// SPDX-License-Identifier: AGPL-3.0-only
import http from 'node:http';
import https from 'node:https';
import { isIP } from 'node:net';
import { createHmac, randomBytes, timingSafeEqual } from 'node:crypto';

const PREFIX = '/api/komiic';
const QUERIES = {
  Search: 'query Search($keyword: String!) { searchComicsAndAuthors(keyword: $keyword) { comics { id title imageUrl } } }',
  Chapters: 'query Chapters($comicId: ID!) { chaptersByComicId(comicId: $comicId) { id serial type size } }',
  Images: 'query Images($chapterId: ID!) { imagesByChapterId(chapterId: $chapterId) { kid width height } }',
};
const agent = new https.Agent({ keepAlive: true, maxSockets: 8, proxyEnv: {} });
class ApiError extends Error {
  constructor(status, code) { super(code); this.status = status; this.code = code; }
}
function object(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function id(value) { return typeof value === 'string' && /^[1-9]\d{0,9}$/.test(value); }
function assetTarget(value) {
  if (!object(value) || Object.keys(value).some(key => !['host', 'path', 'referer'].includes(key))) return false;
  return (value.host === 'komiic.com' && (/^\/api\/image\/[a-f0-9-]{36}$/.test(value.path) || value.path === '/favicon.ico') ||
    value.host === 'public.komiic.com' && /^\/comics\/[a-f0-9]{32}\/[A-Za-z0-9_-]+\.(?:jpg|jpeg|png|webp)$/.test(value.path)) &&
    /^https:\/\/komiic\.com\/(?:comic\/[1-9]\d{0,9}\/chapter\/[1-9]\d{0,9}\/images\/all)?$/.test(value.referer);
}

/** Fixed hosts and paths, direct HTTPS, no caller credentials or redirect following. */
export function upstreamRequest({ host = 'komiic.com', path = '/api/query', body, referer = 'https://komiic.com/' }) {
  if (body === undefined && !assetTarget({ host, path, referer })) throw new ApiError(400, 'invalid_asset');
  if (body !== undefined && (host !== 'komiic.com' || path !== '/api/query')) throw new ApiError(400, 'invalid_upstream');
  return new Promise((resolve, reject) => {
    const bytes = body === undefined ? undefined : Buffer.from(JSON.stringify(body));
    const maxBytes = bytes ? 2 * 1024 * 1024 : 16 * 1024 * 1024;
    const request = https.request({ hostname: host, port: 443, path, method: bytes ? 'POST' : 'GET', agent,
      signal: AbortSignal.timeout(15000), headers: { 'User-Agent': 'Mozilla/5.0 AnimeComic-Komiic/1.0', Referer: referer,
        Accept: bytes ? 'application/json' : 'image/*', ...(bytes ? { 'Content-Type': 'application/json', 'Content-Length': bytes.length } : {}) } }, response => {
      const chunks = []; let size = 0;
      response.on('error', reject);
      response.on('data', chunk => {
        size += chunk.length;
        if (size > maxBytes) response.destroy(new ApiError(502, 'upstream_response_too_large')); else chunks.push(chunk);
      });
      response.on('end', () => resolve({ status: response.statusCode, type: response.headers['content-type']?.split(';')[0], body: Buffer.concat(chunks) }));
    });
    request.on('error', reject); request.end(bytes);
  });
}

export function createKomiicServer({ upstream = upstreamRequest, secret = randomBytes(32), now = Date.now,
  baseUrl = 'https://43.131.255.74:8443/api/komiic', trustLoopbackProxy = false,
  jsonBurst = 30, jsonPerMinute = 60, imageBurst = 90, imagePerMinute = 240,
  globalJsonBurst = 60, globalImageBurst = 180 } = {}) {
  if (!Buffer.isBuffer(secret) || secret.length < 32) throw new Error('Komiic signing key must contain 32 bytes');
  const clients = new Map(), cache = new Map(), pending = new Map();
  let cacheBytes = 0, activeJson = 0, activeImages = 0, cooldownUntil = 0;
  const bucket = tokens => ({ tokens, at: now() });
  const globalJson = bucket(globalJsonBurst), globalImages = bucket(globalImageBurst);
  function spend(b, burst, rate) {
    b.tokens = Math.min(burst, b.tokens + Math.max(0, now() - b.at) * rate / 60000); b.at = now();
    if (b.tokens < 1) return false; b.tokens--; return true;
  }
  function client(request) {
    const peer = request.socket.remoteAddress, forwarded = request.headers['x-real-ip'];
    const ip = trustLoopbackProxy && ['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(peer) && typeof forwarded === 'string' && isIP(forwarded) ? forwarded : peer;
    if (!clients.has(ip)) {
      if (clients.size >= 10000) {
        for (const [key, c] of clients) if (!c.activeJson && !c.activeImages && now() - Math.max(c.json.at, c.images.at) > 120000) clients.delete(key);
        if (clients.size >= 10000) throw new ApiError(503, 'server_busy');
      }
      clients.set(ip, { json: bucket(jsonBurst), images: bucket(imageBurst), activeJson: 0, activeImages: 0 });
    }
    return clients.get(ip);
  }
  const sign = payload => createHmac('sha256', secret).update(payload).digest('base64url');
  function asset(value) {
    if (!assetTarget(value)) throw new ApiError(502, 'invalid_upstream_asset');
    const payload = Buffer.from(JSON.stringify(value)).toString('base64url');
    return `${baseUrl}/images/${payload}/${sign(payload)}`;
  }
  function target(payload, signature) {
    const expected = Buffer.from(sign(payload)), actual = Buffer.from(signature);
    if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) throw new ApiError(404, 'image_not_found');
    let value;
    try { value = JSON.parse(Buffer.from(payload, 'base64url').toString()); } catch { throw new ApiError(404, 'image_not_found'); }
    if (!assetTarget(value)) throw new ApiError(404, 'image_not_found');
    return value;
  }
  async function cached(key, ttl, fetcher) {
    const old = cache.get(key);
    if (old && old.expires > now()) return { ...old, cache: 'HIT' };
    if (pending.has(key)) return pending.get(key);
    if (now() < cooldownUntil) throw new ApiError(429, 'komiic_rate_limited');
    const promise = (async () => {
      const result = await fetcher();
      if (cache.has(key)) { cacheBytes -= cache.get(key).body.length; cache.delete(key); }
      while (cache.size >= 1000 || cacheBytes + result.body.length > 64 * 1024 * 1024) {
        const first = cache.keys().next().value; if (first === undefined) break;
        cacheBytes -= cache.get(first).body.length; cache.delete(first);
      }
      cache.set(key, { ...result, expires: now() + ttl }); cacheBytes += result.body.length;
      return { ...result, cache: 'MISS' };
    })();
    pending.set(key, promise); try { return await promise; } finally { pending.delete(key); }
  }
  function upstreamStatus(status) {
    if (status === 402) { cooldownUntil = now() + 30000; throw new ApiError(429, 'komiic_reading_limit'); }
    if (status === 429) { cooldownUntil = now() + 30000; throw new ApiError(429, 'komiic_rate_limited'); }
    if ([401, 403].includes(status)) throw new ApiError(status, 'komiic_access_restricted');
    if (status === 404) throw new ApiError(404, 'komiic_not_found');
    if (status !== 200) throw new ApiError(502, 'komiic_upstream_failed');
  }
  async function query(operation, variables) {
    const response = await upstream({ body: { query: QUERIES[operation], variables } });
    upstreamStatus(response.status);
    if (response.type !== 'application/json' || response.body.length > 2 * 1024 * 1024) throw new ApiError(502, 'invalid_upstream_json');
    let result;
    try { result = JSON.parse(response.body); } catch { throw new ApiError(502, 'invalid_upstream_json'); }
    if (Array.isArray(result.errors) && result.errors.length) throw new ApiError(403, 'komiic_access_restricted');
    if (!object(result.data)) throw new ApiError(502, 'invalid_upstream_json');
    return result;
  }
  async function readOperation(request) {
    if (!/^application\/json(?:\s*;|$)/i.test(request.headers['content-type'] ?? '') || request.headers['content-encoding']) throw new ApiError(415, 'json_required');
    if (Number(request.headers['content-length']) > 4096) throw new ApiError(413, 'body_too_large');
    const chunks = []; let size = 0;
    for await (const chunk of request) { size += chunk.length; if (size > 4096) throw new ApiError(413, 'body_too_large'); chunks.push(chunk); }
    let value;
    try { value = JSON.parse(Buffer.concat(chunks)); } catch { throw new ApiError(400, 'invalid_json'); }
    if (!object(value) || Object.keys(value).some(key => !['operation', 'variables'].includes(key)) ||
      !Object.hasOwn(QUERIES, value.operation) || !object(value.variables)) throw new ApiError(400, 'invalid_operation');
    const fields = value.operation === 'Search' ? ['keyword'] : value.operation === 'Chapters' ? ['comicId'] : ['comicId', 'chapterId'];
    if (Object.keys(value.variables).length !== fields.length || fields.some(key => !(key in value.variables))) throw new ApiError(400, 'invalid_variables');
    if (value.operation === 'Search') {
      const k = value.variables.keyword;
      if (typeof k !== 'string' || !k.trim() || k.trim().length > 100 || /[\x00-\x1f\x7f]/.test(k)) throw new ApiError(400, 'invalid_keyword');
      value.variables.keyword = k.trim();
    } else if (fields.some(key => !id(value.variables[key]))) throw new ApiError(400, 'invalid_id');
    return value;
  }
  async function operationResult(operation, variables) {
    const result = await query(operation, operation === 'Images' ? { chapterId: variables.chapterId } : variables);
    if (operation === 'Search') {
      const books = result.data.searchComicsAndAuthors?.comics;
      if (!Array.isArray(books) || books.length > 500 || books.some(b => !object(b) || !id(b.id) || typeof b.title !== 'string')) throw new ApiError(502, 'invalid_search');
      for (const book of books) {
        try {
          const url = new URL(book.imageUrl);
          book.imageUrl = url.protocol === 'https:' && !url.port && !url.username && !url.password && !url.search && !url.hash
            ? asset({ host: url.hostname, path: url.pathname, referer: 'https://komiic.com/' }) : '';
        } catch { book.imageUrl = ''; }
      }
    } else if (operation === 'Chapters') {
      const rows = result.data.chaptersByComicId;
      if (!Array.isArray(rows) || rows.length > 10000 || rows.some(c => !object(c) || !id(c.id) || !['book', 'chapter'].includes(c.type))) throw new ApiError(502, 'invalid_chapters');
    } else {
      const rows = result.data.imagesByChapterId;
      if (!Array.isArray(rows) || !rows.length || rows.length > 2000 || rows.some(p => !object(p) || !/^[a-f0-9-]{36}$/.test(p.kid) || !Number.isInteger(p.width) || !Number.isInteger(p.height))) throw new ApiError(502, 'invalid_pages');
      for (const page of rows) page.url = asset({ host: 'komiic.com', path: '/api/image/' + page.kid,
        referer: `https://komiic.com/comic/${variables.comicId}/chapter/${variables.chapterId}/images/all` });
    }
    return { type: 'application/json; charset=utf-8', body: Buffer.from(JSON.stringify(result)) };
  }
  function reply(response, status, body, type = 'application/json; charset=utf-8', extra = {}) {
    if (response.destroyed) return;
    const bytes = Buffer.isBuffer(body) ? body : Buffer.from(JSON.stringify(body));
    response.writeHead(status, { 'Content-Type': type, 'Content-Length': bytes.length, 'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff', 'Access-Control-Allow-Origin': '*', ...extra }); response.end(bytes);
  }
  const server = http.createServer({ maxHeaderSize: 8192 }, async (request, response) => {
    let c, admitted = false, binary = false;
    try {
      if (!/^\/api\/komiic\/[A-Za-z0-9_/-]+$/.test(request.url) || request.url.length > 1000) throw new ApiError(404, 'not_found');
      const image = /^\/api\/komiic\/images\/([A-Za-z0-9_-]{1,800})\/([A-Za-z0-9_-]{43})$/.exec(request.url);
      const icon = request.url === PREFIX + '/icon', health = request.url === PREFIX + '/health', api = request.url === PREFIX + '/query';
      if (!image && !icon && !health && !api) throw new ApiError(404, 'not_found');
      binary = Boolean(image || icon); c = client(request);
      if (binary ? !spend(c.images, imageBurst, imagePerMinute) || !spend(globalImages, globalImageBurst, 900)
        : !spend(c.json, jsonBurst, jsonPerMinute) || !spend(globalJson, globalJsonBurst, 300)) throw new ApiError(429, 'rate_limited');
      if (request.headers.authorization || request.headers.cookie) throw new ApiError(400, 'no_credentials');
      if (request.method === 'OPTIONS') { reply(response, 204, Buffer.alloc(0), undefined, { 'Access-Control-Allow-Methods': api ? 'POST, OPTIONS' : 'GET, OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type', 'Access-Control-Max-Age': '600' }); return; }
      if (request.method !== (api ? 'POST' : 'GET')) throw new ApiError(405, 'method_not_allowed');
      if (!api && (Number(request.headers['content-length']) || request.headers['transfer-encoding'])) throw new ApiError(400, 'body_not_allowed');
      if (health) { reply(response, 200, { status: 'ok', service: 'animecomic-komiic', version: 1 }); return; }
      if (binary ? activeImages >= 8 || c.activeImages >= 6 : activeJson >= 6 || c.activeJson >= 4) throw new ApiError(429, 'too_many_concurrent_requests');
      if (binary) { activeImages++; c.activeImages++; } else { activeJson++; c.activeJson++; } admitted = true;
      let result;
      if (binary) {
        const value = icon ? { host: 'komiic.com', path: '/favicon.ico', referer: 'https://komiic.com/' } : target(image[1], image[2]);
        result = await cached(JSON.stringify(value), 3600000, async () => {
          const res = await upstream(value); upstreamStatus(res.status);
          if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif', ...(icon ? ['image/x-icon', 'image/vnd.microsoft.icon'] : [])].includes(res.type) ||
            !res.body.length || res.body.length > 16 * 1024 * 1024) throw new ApiError(502, 'invalid_image');
          return res;
        });
      } else {
        const { operation, variables } = await readOperation(request);
        result = await cached(operation + JSON.stringify(variables), operation === 'Search' ? 60000 : 300000, () => operationResult(operation, variables));
      }
      reply(response, 200, result.body, result.type, { 'X-Cache': result.cache, ...(binary ? { 'Cache-Control': 'public, max-age=3600' } : {}) });
    } catch (e) {
      const status = e instanceof ApiError ? e.status : e.name === 'AbortError' ? 504 : 502;
      reply(response, status, { error: e instanceof ApiError ? e.code : 'komiic_upstream_unavailable' }, undefined,
        { ...([429, 503].includes(status) ? { 'Retry-After': '30' } : {}), ...(status === 413 ? { Connection: 'close' } : {}) });
    } finally {
      if (admitted) { if (binary) { activeImages--; c.activeImages--; } else { activeJson--; c.activeJson--; } }
    }
  });
  server.headersTimeout = 5000; server.requestTimeout = 10000; server.timeout = 20000;
  server.keepAliveTimeout = 5000; server.maxConnections = 128; server.maxRequestsPerSocket = 100;
  return server;
}
