// SPDX-License-Identifier: AGPL-3.0-only
import http from 'node:http';
import https from 'node:https';
import { isIP } from 'node:net';
import { pathToFileURL } from 'node:url';

const PREFIX = '/api/books';
const MAX_RESPONSE = 2 * 1024 * 1024;
const directAgent = new https.Agent({ keepAlive: true, maxSockets: 8, proxyEnv: {} });

class ApiError extends Error {
  constructor(status, code) { super(code); this.status = status; this.code = code; }
}

/** Fixed direct upstream. Bearer credentials are passed only by the authenticated book routes. */
export function fetchBangumi(path, { method, body, authorization, signal }) {
  return new Promise((resolve, reject) => {
    const bytes = body === undefined ? undefined : Buffer.from(JSON.stringify(body));
    const request = https.request({
      hostname: 'api.bgm.tv', port: 443, path, method, agent: directAgent, signal,
      headers: { Accept: 'application/json', 'User-Agent': 'AnimeComic-Books/1.0 (public book metadata)',
        ...(authorization ? { Authorization: authorization } : {}),
        ...(bytes ? { 'Content-Type': 'application/json', 'Content-Length': bytes.length } : {}) },
    }, response => {
      const chunks = []; let size = 0;
      response.on('error', reject);
      response.on('data', chunk => {
        size += chunk.length;
        if (size > MAX_RESPONSE) response.destroy(new ApiError(502, 'upstream_response_too_large'));
        else chunks.push(chunk);
      });
      response.on('end', () => {
        try {
          const status = response.statusCode;
          if (status !== 200) { resolve({ status }); return; }
          if (!response.headers['content-type']?.startsWith('application/json')) throw new Error('content type');
          resolve({ status, data: JSON.parse(Buffer.concat(chunks).toString('utf8')) });
        } catch { reject(new ApiError(502, 'invalid_upstream_response')); }
      });
    });
    const timer = setTimeout(() => request.destroy(new ApiError(504, 'upstream_timeout')), 7000);
    request.on('close', () => clearTimeout(timer));
    request.on('error', reject);
    request.end(bytes);
  });
}

function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function fields(value, allowed) {
  if (!object(value) || Object.keys(value).some(key => !allowed.includes(key))) throw new ApiError(400, 'invalid_fields');
}
function integer(value, fallback, min, max) {
  if (value === null) return fallback;
  if (!/^(0|[1-9]\d*)$/.test(value) || !Number.isSafeInteger(Number(value)) || Number(value) < min || Number(value) > max) {
    throw new ApiError(400, 'invalid_pagination');
  }
  return Number(value);
}
async function readJson(request) {
  if (!/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'] ?? '')) throw new ApiError(415, 'json_required');
  if (request.headers['content-encoding']) throw new ApiError(415, 'encoded_body_not_supported');
  if (Number(request.headers['content-length'] ?? 0) > 4096) throw new ApiError(413, 'body_too_large');
  const chunks = []; let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 4096) throw new ApiError(413, 'body_too_large');
    chunks.push(chunk);
  }
  let body;
  try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new ApiError(400, 'invalid_json'); }
  return body;
}
async function readSearch(request) {
  const body = await readJson(request);
  fields(body, ['keyword', 'sort', 'filter']);
  if (typeof body.keyword !== 'string' || !body.keyword.trim() || body.keyword.trim().length > 100 || /[\x00-\x1f\x7f]/.test(body.keyword)) {
    throw new ApiError(400, 'keyword_required_max_100');
  }
  const sort = body.sort ?? 'match';
  if (!['match', 'rank', 'heat', 'score'].includes(sort)) throw new ApiError(400, 'invalid_sort');
  const filter = body.filter ?? {};
  fields(filter, ['type', 'nsfw']);
  if (filter.type !== undefined && (!Array.isArray(filter.type) || filter.type.length !== 1 || filter.type[0] !== 1)) {
    throw new ApiError(400, 'books_only');
  }
  if (filter.nsfw !== undefined && typeof filter.nsfw !== 'boolean') throw new ApiError(400, 'invalid_nsfw');
  return { keyword: body.keyword.trim(), sort, filter: { type: [1], ...(filter.nsfw === undefined ? {} : { nsfw: filter.nsfw }) } };
}

async function readCollectionWrite(request) {
  const body = await readJson(request);
  fields(body, request.method === 'POST' ? ['type'] : ['type', 'ep_status', 'vol_status']);
  if (!Object.keys(body).length || (request.method === 'POST' && body.type === undefined)) throw new ApiError(400, 'empty_update');
  if (body.type !== undefined && (!Number.isInteger(body.type) || body.type < 1 || body.type > 5)) throw new ApiError(400, 'invalid_collection_type');
  for (const key of ['ep_status', 'vol_status']) {
    if (body[key] !== undefined && (!Number.isInteger(body[key]) || body[key] < 0 || body[key] > 1000000)) throw new ApiError(400, 'invalid_progress');
  }
  return body;
}

export function createBooksServer({ upstream = fetchBangumi, now = Date.now,
  ipBurst = 30, ipPerMinute = 60, globalBurst = 60, globalPerMinute = 300,
  maxConcurrent = 8, maxPerIpConcurrent = 6, trustLoopbackProxy = false } = {}) {
  const clients = new Map(), cache = new Map(), pending = new Map();
  const global = { tokens: globalBurst, at: now() };
  let active = 0, cacheBytes = 0, cooldownUntil = 0;
  function spend(bucket, burst, rate) {
    const time = now();
    bucket.tokens = Math.min(burst, bucket.tokens + Math.max(0, time - bucket.at) * rate / 60000);
    bucket.at = time;
    if (bucket.tokens < 1) return false;
    bucket.tokens--; return true;
  }
  function clientFor(request) {
    const peer = request.socket.remoteAddress;
    const forwarded = request.headers['x-real-ip'];
    const ip = trustLoopbackProxy && ['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(peer) && typeof forwarded === 'string' && isIP(forwarded)
      ? forwarded : peer;
    let client = clients.get(ip);
    if (!client) {
      if (clients.size >= 10000) {
        for (const [key, entry] of clients) if (!entry.active && now() - entry.at > 120000) clients.delete(key);
        if (clients.size >= 10000) throw new ApiError(503, 'server_busy');
      }
      client = { tokens: ipBurst, at: now(), active: 0, writes: { tokens: 10, at: now() } }; clients.set(ip, client);
    }
    return client;
  }
  function reply(response, status, value, extra = {}) {
    if (response.destroyed) return;
    const bytes = typeof value === 'string' ? value : JSON.stringify(value);
    response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(bytes),
      'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Access-Control-Allow-Origin': '*', ...extra });
    response.end(bytes);
  }
  function remember(key, result, ttl) {
    const size = Buffer.byteLength(result.body);
    if (cache.has(key)) { cacheBytes -= cache.get(key).size; cache.delete(key); }
    while (cache.size >= 1000 || cacheBytes + size > 16 * 1024 * 1024) {
      const oldest = cache.keys().next().value;
      if (oldest === undefined) break;
      cacheBytes -= cache.get(oldest).size; cache.delete(oldest);
    }
    cache.set(key, { ...result, size, expires: now() + ttl }); cacheBytes += size;
  }
  async function load(key, path, options, isSearch, id) {
    const cached = cache.get(key);
    if (cached && cached.expires > now()) return { ...cached, cache: 'HIT' };
    if (pending.has(key)) return pending.get(key);
    if (now() < cooldownUntil) throw new ApiError(503, 'upstream_busy');
    const promise = (async () => {
      const result = await upstream(path, options);
      if (result.status === 429) { cooldownUntil = now() + 15000; throw new ApiError(503, 'upstream_busy'); }
      if (result.status === 404) {
        const missing = { status: 404, body: JSON.stringify({ error: 'book_not_found' }), cache: 'MISS' };
        remember(key, missing, 60000); return missing;
      }
      if (result.status !== 200) throw new ApiError(502, 'upstream_failed');
      if (isSearch) {
        if (!object(result.data) || !Array.isArray(result.data.data) || result.data.data.length > 20 ||
            !result.data.data.every(book => object(book) && book.type === 1)) throw new ApiError(502, 'invalid_upstream_response');
      } else if (!object(result.data) || result.data.id !== id) {
        throw new ApiError(502, 'invalid_upstream_response');
      } else if (result.data.type !== 1) {
        const denied = { status: 404, body: JSON.stringify({ error: 'book_not_found' }), cache: 'MISS' };
        remember(key, denied, 300000); return denied;
      }
      const success = { status: 200, body: JSON.stringify(result.data), cache: 'MISS' };
      remember(key, success, isSearch ? 60000 : 3600000); return success;
    })();
    pending.set(key, promise);
    try { return await promise; } finally { pending.delete(key); }
  }
  // Account responses are never shared, persisted, cached or coalesced between requests.
  async function privateRequest(path, options) {
    if (now() < cooldownUntil) throw new ApiError(503, 'upstream_busy');
    const result = await upstream(path, options);
    if (result.status === 429) { cooldownUntil = now() + 15000; throw new ApiError(503, 'upstream_busy'); }
    if (result.status === 401) throw new ApiError(401, 'bangumi_authorization_required');
    if (result.status === 403) throw new ApiError(403, 'bangumi_permission_denied');
    if (result.status === 404) throw new ApiError(404, 'collection_or_book_not_found');
    if (result.status === 400) throw new ApiError(400, 'upstream_rejected_update');
    if (![200, 202, 204].includes(result.status)) throw new ApiError(502, 'upstream_failed');
    return result;
  }
  async function handleAccount(request, response, url, collection, isMe, isWrite, client) {
    const authorization = request.headers.authorization;
    if (typeof authorization !== 'string' || !/^Bearer [A-Za-z0-9._~+/=-]{1,2048}$/.test(authorization)) {
      throw new ApiError(401, 'bangumi_authorization_required');
    }
    const body = isWrite ? await readCollectionWrite(request) : undefined;
    if (isWrite && !spend(client.writes, 10, 30)) throw new ApiError(429, 'write_rate_limited');
    const signal = AbortSignal.timeout(15000);
    const getOptions = { method: 'GET', authorization, signal };
    const list = collection && !collection[2];
    const limit = list ? integer(url.searchParams.get('limit'), 30, 1, 30) : null;
    const offset = list ? integer(url.searchParams.get('offset'), 0, 0, 10000) : null;
    const type = list ? integer(url.searchParams.get('type'), null, 1, 5) : null;
    if (list && url.searchParams.has('subject_type') && url.searchParams.get('subject_type') !== '1') throw new ApiError(400, 'books_only');
    const identity = await privateRequest('/v0/me', getOptions);
    if (!object(identity.data) || !/^[A-Za-z0-9_-]{1,64}$/.test(identity.data.username) || !Number.isSafeInteger(identity.data.id)) {
      throw new ApiError(502, 'invalid_upstream_identity');
    }
    if (isMe) { reply(response, 200, { id: identity.data.id, username: identity.data.username }); return; }
    const username = identity.data.username;
    if (collection[1] !== '-' && collection[1] !== username) throw new ApiError(403, 'own_collection_only');
    const id = collection[2] ? Number(collection[2]) : null;
    if (id) {
      const subject = await privateRequest(`/v0/subjects/${id}`, getOptions);
      if (!object(subject.data) || subject.data.id !== id) throw new ApiError(502, 'invalid_upstream_response');
      if (subject.data.type !== 1) throw new ApiError(404, 'book_not_found');
    }
    if (isWrite) {
      const result = await privateRequest(`/v0/users/-/collections/${id}`, { method: request.method, body, authorization, signal });
      if (![202, 204].includes(result.status)) throw new ApiError(502, 'unexpected_write_response');
      // Public detail statistics must not stay cached after a collection mutation.
      const key = `/v0/subjects/${id}null`;
      if (cache.has(key)) { cacheBytes -= cache.get(key).size; cache.delete(key); }
      reply(response, result.status, ''); return;
    }
    const path = `/v0/users/${username}/collections` + (id ? `/${id}`
      : `?subject_type=1&limit=${limit}&offset=${offset}${type === null ? '' : '&type=' + type}`);
    const result = await privateRequest(path, getOptions);
    if (list) {
      if (!object(result.data) || !Array.isArray(result.data.data) || result.data.data.length > limit ||
          !result.data.data.every(row => object(row) && row.subject_type === 1 && (!row.subject || row.subject.type === 1))) {
        throw new ApiError(502, 'invalid_upstream_collection');
      }
    } else if (!object(result.data) || result.data.subject_id !== id || result.data.subject_type !== 1) {
      throw new ApiError(502, 'invalid_upstream_collection');
    }
    reply(response, 200, result.data);
  }
  const server = http.createServer({ maxHeaderSize: 8192 }, async (request, response) => {
    let client, admitted = false;
    try {
      // Reject encoded/traversal/absolute paths before URL normalization.
      if (!/^\/api\/books\/[A-Za-z0-9_/-]+(?:\?[^#]*)?$/.test(request.url) || request.url.length > 512) throw new ApiError(404, 'not_found');
      const url = new URL(request.url, 'http://localhost');
      const isSearch = url.pathname === PREFIX + '/v0/search/subjects';
      const detail = new RegExp('^' + PREFIX + '/v0/subjects/([1-9]\\d{0,7})$').exec(url.pathname);
      const health = url.pathname === PREFIX + '/health';
      const isMe = url.pathname === PREFIX + '/v0/me';
      const collection = new RegExp('^' + PREFIX + '/v0/users/([A-Za-z0-9_-]{1,64})/collections(?:/([1-9]\\d{0,7}))?$').exec(url.pathname);
      const account = isMe || Boolean(collection);
      const canWrite = collection && collection[1] === '-' && collection[2];
      const isWrite = canWrite && ['POST', 'PATCH'].includes(request.method);
      if (!isSearch && !detail && !health && !account) throw new ApiError(404, 'not_found');
      client = clientFor(request);
      if (!spend(global, globalBurst, globalPerMinute) || !spend(client, ipBurst, ipPerMinute)) throw new ApiError(429, 'rate_limited');
      if (request.method === 'OPTIONS') {
        reply(response, 204, '', { 'Access-Control-Allow-Methods': isSearch ? 'POST, OPTIONS' : canWrite ? 'GET, POST, PATCH, OPTIONS' : 'GET, OPTIONS', 'Access-Control-Allow-Headers': account ? 'Content-Type, Authorization' : 'Content-Type', 'Access-Control-Max-Age': '600' }); return;
      }
      if (!isWrite && request.method !== (isSearch ? 'POST' : 'GET')) throw new ApiError(405, 'method_not_allowed');
      if (request.headers.cookie || (!account && request.headers.authorization)) throw new ApiError(400, 'public_api_no_credentials');
      const allowed = isSearch ? ['limit', 'offset'] : collection && !collection[2] ? ['limit', 'offset', 'type', 'subject_type'] : [];
      const keys = [...url.searchParams.keys()];
      if (keys.some(key => !allowed.includes(key)) || new Set(keys).size !== keys.length) throw new ApiError(400, 'invalid_query');
      if (health) { reply(response, 200, { status: 'ok', service: 'animecomic-books', version: 2 }); return; }
      if (active >= maxConcurrent || client.active >= maxPerIpConcurrent) throw new ApiError(429, 'too_many_concurrent_requests');
      active++; client.active++; admitted = true;
      if (request.method === 'GET' && (Number(request.headers['content-length'] ?? 0) || request.headers['transfer-encoding'])) throw new ApiError(400, 'body_not_allowed');
      if (account) { await handleAccount(request, response, url, collection, isMe, isWrite, client); return; }
      let path, options;
      if (isSearch) {
        const limit = integer(url.searchParams.get('limit'), 20, 1, 20);
        const offset = integer(url.searchParams.get('offset'), 0, 0, 1000);
        options = { method: 'POST', body: await readSearch(request) };
        path = `/v0/search/subjects?limit=${limit}&offset=${offset}`;
      } else {
        if (Number(request.headers['content-length'] ?? 0) || request.headers['transfer-encoding']) throw new ApiError(400, 'body_not_allowed');
        path = `/v0/subjects/${detail[1]}`; options = { method: 'GET' };
      }
      const key = path + JSON.stringify(options.body ?? null);
      const result = await load(key, path, options, isSearch, detail ? Number(detail[1]) : null);
      reply(response, result.status, result.body, { 'X-Cache': result.cache });
    } catch (error) {
      const status = error instanceof ApiError ? error.status : error.name === 'AbortError' ? 504 : 502;
      reply(response, status, { error: error instanceof ApiError ? error.code : status === 504 ? 'upstream_timeout' : 'upstream_unavailable' },
        { ...(status === 429 || status === 503 ? { 'Retry-After': '15' } : {}),
          ...(status === 413 ? { Connection: 'close' } : {}) });
    } finally { if (admitted) { active--; client.active--; } }
  });
  server.headersTimeout = 5000;
  server.requestTimeout = 10000;
  server.timeout = 20000;
  server.keepAliveTimeout = 5000;
  server.maxConnections = 128;
  server.maxRequestsPerSocket = 100;
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.env.BOOKS_API_PORT ?? 4180);
  const server = createBooksServer({ trustLoopbackProxy: true });
  server.listen(port, '127.0.0.1', () => console.log(JSON.stringify({ event: 'listening', port, service: 'animecomic-books' })));
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => process.exit(0)));
}
