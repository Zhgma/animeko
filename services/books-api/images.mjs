// SPDX-License-Identifier: AGPL-3.0-only
import https from 'node:https';
import { createHmac, randomBytes, timingSafeEqual } from 'node:crypto';

const MAX_IMAGE = 8 * 1024 * 1024;
const agent = new https.Agent({ keepAlive: true, maxSockets: 12, proxyEnv: {} });
export class ImageError extends Error {
  constructor(status, code) { super(code); this.status = status; this.code = code; }
}

function imagePath(value) {
  if (typeof value !== 'string' || value.length > 400) return null;
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' || url.hostname !== 'lain.bgm.tv' || url.port || url.username || url.password || url.hash) return null;
    if (!/^\/(?:r\/(?:100|200|400|800)\/)?pic\/(?:cover|user)\/(?:s|m|l)\/[A-Za-z0-9_/-]+\.(?:jpg|jpeg|png|webp|gif)$/i.test(url.pathname)) return null;
    if ([...url.searchParams].some(([key, val]) => !((key === 'r' && /^\d{1,12}$/.test(val)) || (key === 'hd' && val === '1')))) return null;
    if (new Set(url.searchParams.keys()).size !== [...url.searchParams.keys()].length) return null;
    return url.pathname + url.search;
  } catch { return null; }
}

/** Direct, fixed-host image fetch; credentials and redirects are never forwarded. */
export function fetchBookImage(path) {
  return new Promise((resolve, reject) => {
    const request = https.request({ hostname: 'lain.bgm.tv', port: 443, path, method: 'GET', agent,
      signal: AbortSignal.timeout(12000), headers: { Accept: 'image/*', 'User-Agent': 'AnimeComic-Books/1.0' } }, response => {
      const type = response.headers['content-type']?.split(';')[0].trim().toLowerCase();
      if (response.statusCode !== 200 || !['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(type)) {
        response.destroy(); reject(new ImageError(response.statusCode === 404 ? 404 : 502, 'image_upstream_failed')); return;
      }
      const chunks = []; let size = 0;
      response.on('error', reject);
      response.on('data', chunk => {
        size += chunk.length;
        if (size > MAX_IMAGE) response.destroy(new ImageError(502, 'image_too_large'));
        else chunks.push(chunk);
      });
      response.on('end', () => resolve({ type, body: Buffer.concat(chunks) }));
    });
    request.on('error', reject); request.end();
  });
}

/** Only image fields in validated book responses can issue a signed image URL. */
export function createBookImages({ secret = randomBytes(32), baseUrl = 'https://43.131.255.74:8443/api/books',
  upstream = fetchBookImage, now = Date.now } = {}) {
  if (!Buffer.isBuffer(secret) || secret.length < 32) throw new Error('Image signing key must contain at least 32 bytes');
  const cache = new Map(), pending = new Map(); let cacheBytes = 0;
  const sign = value => createHmac('sha256', secret).update(value).digest('base64url');
  function rewrite(value, imageField = false) {
    if (typeof value === 'string') {
      const path = imageField && imagePath(value);
      if (!path) return value;
      const payload = Buffer.from(path).toString('base64url');
      return `${baseUrl}/images/${payload}/${sign(payload)}`;
    }
    if (Array.isArray(value)) return value.map(item => rewrite(item));
    if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) =>
      [key, rewrite(item, imageField || ['images', 'image', 'avatar'].includes(key))]));
    return value;
  }
  async function load(payload, signature) {
    const expected = Buffer.from(sign(payload));
    const actual = Buffer.from(signature);
    if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) throw new ImageError(404, 'image_not_found');
    const path = Buffer.from(payload, 'base64url').toString('utf8');
    if (Buffer.from(path).toString('base64url') !== payload || imagePath('https://lain.bgm.tv' + path) !== path) throw new ImageError(404, 'image_not_found');
    const old = cache.get(path);
    if (old && old.expires > now()) return { ...old, cache: 'HIT' };
    if (pending.has(path)) return pending.get(path);
    const promise = (async () => {
      const result = await upstream(path);
      if (!Buffer.isBuffer(result.body) || !result.body.length || result.body.length > MAX_IMAGE ||
          !['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(result.type)) throw new ImageError(502, 'invalid_image');
      if (cache.has(path)) { cacheBytes -= cache.get(path).body.length; cache.delete(path); }
      while (cache.size >= 256 || cacheBytes + result.body.length > 32 * 1024 * 1024) {
        const key = cache.keys().next().value;
        if (key === undefined) break;
        cacheBytes -= cache.get(key).body.length; cache.delete(key);
      }
      const entry = { ...result, expires: now() + 3600000 };
      cache.set(path, entry); cacheBytes += result.body.length;
      return { ...entry, cache: 'MISS' };
    })();
    pending.set(path, promise);
    try { return await promise; } finally { pending.delete(path); }
  }
  return { rewrite, load };
}
