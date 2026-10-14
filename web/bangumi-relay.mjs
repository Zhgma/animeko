// SPDX-License-Identifier: AGPL-3.0-only
// Same-origin transport for the shared Bangumi collection loader and Compose images.
const prefix = '/api/bangumi';
const apiOrigin = 'https://api.bgm.tv';
const imageOrigin = 'https://lain.bgm.tv';
const imageTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/avif']);

export function createBangumiRelay({ fetchImpl = fetch } = {}) {
  function reply(response, status, error) {
    response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
    response.end(JSON.stringify({ error }));
  }
  async function readLimited(stream, limit) {
    const chunks = []; let length = 0;
    for await (const chunk of stream) {
      length += chunk.length;
      if (length > limit) throw new Error('too large');
      chunks.push(Buffer.from(chunk));
    }
    return Buffer.concat(chunks);
  }
  return async function handleBangumi(request, response, url) {
    if (!url.pathname.startsWith(prefix + '/')) return false;
    try {
      const origin = request.headers.origin;
      if (request.headers['sec-fetch-site'] === 'cross-site' || (origin && new URL(origin).host !== request.headers.host)) {
        reply(response, 403, '只允许本站发起请求。'); return true;
      }
      const path = url.pathname.slice(prefix.length);
      const isImage = path === '/image';
      const isSearch = path === '/v0/search/subjects';
      const isComments = /^\/p1\/subjects\/[1-9]\d*\/comments$/.test(path);
      const isCollection = /^\/v0\/users\/-\/collections\/[1-9]\d*$/.test(path);
      const isWrite = isCollection && ['POST', 'PATCH'].includes(request.method);
      if (!isImage && !isSearch && !isComments && !/^\/v0\/(me|episodes|subjects(?:\/\d+(?:\/subjects)?)?|users\/[\w.-]+\/collections(?:\/\d+)?)$/.test(path)) {
        reply(response, 404, '接口不存在。'); return true;
      }
      if (!isWrite && request.method !== (isSearch ? 'POST' : 'GET')) { reply(response, 405, '请求方式无效。'); return true; }
      if (isWrite && !request.headers.authorization) { reply(response, 401, '请先登录 Bangumi。'); return true; }
      let target;
      const headers = { Accept: isImage ? 'image/*' : 'application/json', 'User-Agent': 'AnimeComic-Web/preview' };
      if (isImage) {
        try { target = new URL(url.searchParams.get('url')); } catch { reply(response, 400, '封面地址无效。'); return true; }
        const bangumiImage = target.origin === imageOrigin && /^\/(?:r\/\d+(?:x\d+)?\/)?pic\//.test(target.pathname);
        const animekoImage = target.origin === 'https://static.myani.org' &&
          (/^\/(subjects|users|avatars)\//.test(target.pathname) || /^\/episodes\/[1-9]\d*\/still\/(thumb|medium|large)$/.test(target.pathname));
        if ((!bangumiImage && !animekoImage) || target.username || target.password || target.search || target.hash) {
          reply(response, 400, '封面地址无效。'); return true;
        }
      } else {
        const allowed = new Set(['subject_type', 'subject_id', 'type', 'offset', 'limit', 'sort', 'cat', 'series']);
        if ([...url.searchParams.keys()].some(key => !allowed.has(key)) || url.search.length > 512) {
          reply(response, 400, '请求参数无效。'); return true;
        }
        target = new URL(path + url.search, isComments ? 'https://next.bgm.tv' : apiOrigin);
        const authorization = request.headers.authorization;
        if (authorization && !isComments) {
          if (!/^Bearer [\w.~-]+$/.test(authorization) || authorization.length > 8192) { reply(response, 400, '账号凭据无效。'); return true; }
          headers.Authorization = authorization;
        }
      }
      let body;
      if (isSearch || isWrite) {
        if (!request.headers['content-type']?.startsWith('application/json')) { reply(response, 415, '需要 JSON 请求。'); return true; }
        try {
          body = await readLimited(request, 16384);
          const data = JSON.parse(body.toString('utf8'));
          if (isWrite) {
            const keys = Object.keys(data);
            if (!keys.length || keys.some(k => !['type','ep_status','vol_status','rate','comment','private'].includes(k))) throw new Error('invalid fields');
            if ('type' in data && (!Number.isInteger(data.type) || data.type < 1 || data.type > 5)) throw new Error('invalid type');
            for (const k of ['ep_status','vol_status','rate']) if (k in data && (!Number.isInteger(data[k]) || data[k] < 0 || data[k] > (k === 'rate' ? 10 : 1000000))) throw new Error('invalid number');
            if ('comment' in data && (typeof data.comment !== 'string' || data.comment.length > 10000)) throw new Error('invalid comment');
            if ('private' in data && typeof data.private !== 'boolean') throw new Error('invalid privacy');
            if (request.method === 'POST' && (!('type' in data) || keys.length !== 1)) throw new Error('create requires only type');
          }
        }
        catch { reply(response, 400, '搜索参数无效或过大。'); return true; }
        headers['Content-Type'] = 'application/json';
      }
      const result = await fetchImpl(target.href, { method: request.method, headers, body, redirect: 'error', signal: AbortSignal.timeout(15000) });
      if (!result.ok) {
        await result.body?.cancel();
        reply(response, result.status, result.status === 401 ? 'Bangumi 会话已过期，请重新登录。' : `Bangumi ${isImage ? '图片' : '接口'}返回 HTTP ${result.status}`);
        return true;
      }
      if (result.status === 204) { response.writeHead(204, {'Cache-Control':'no-store'}); response.end(); return true; }
      const contentType = result.headers.get('content-type')?.split(';')[0].trim().toLowerCase();
      if (isImage ? !imageTypes.has(contentType) : contentType !== 'application/json') {
        await result.body?.cancel(); throw new Error('invalid content type');
      }
      const bytes = await readLimited(result.body, isImage ? 8 * 1024 * 1024 : 4 * 1024 * 1024);
      response.writeHead(200, { 'Content-Type': contentType, 'Cache-Control': isImage ? 'public, max-age=86400' : 'no-store', 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer' });
      response.end(bytes);
    } catch {
      reply(response, 502, '本站无法连接 Bangumi，请检查网页服务的网络或代理设置。');
    }
    return true;
  };
}
