// SPDX-License-Identifier: AGPL-3.0-only
// Same-origin transport for the App's generated subject and episode API clients.
const servers = ['https://api.animeko.org', 'https://danmaku-global.myani.org', 'https://danmaku-cn.myani.org', 'https://s1.animeko.openani.org'];
export function createAnimekoRelay({ fetchImpl = fetch, upstream, upstreams = upstream ? [upstream] : servers } = {}) {
  let preferred = 0;
  const reply = (res, status, error) => {
    res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
    res.end(JSON.stringify({error}));
  };
  return async (req, res, url) => {
    if (!url.pathname.startsWith('/api/animeko/')) return false;
    try {
      if (req.headers['sec-fetch-site'] === 'cross-site' || (req.headers.origin && new URL(req.headers.origin).host !== req.headers.host)) {
        reply(res, 403, '只允许本站发起请求。'); return true;
      }
      const path = url.pathname.slice('/api/animeko'.length);
      const subject = /^\/v2\/subjects\/[1-9]\d*$/.test(path);
      const batch = /^\/v2\/subjects\/[1-9]\d*\/episodes\/batchUpdate$/.test(path);
      const list = path === '/v2/subjects/list' || path === '/v2/subjects/count';
      const profile = path === '/v1/me' && req.method === 'GET' || path === '/v2/users/me' && req.method === 'PATCH';
      const search = path === '/v2/subjects/search' && req.method === 'GET';
      const trends = path === '/v2/trends/subjects' && req.method === 'GET';
      const recommendations = path === '/v2/home/recommendations' && req.method === 'GET';
      const schedule = path === '/v1/schedule/airing' && req.method === 'GET';
      const episodeComments = /^\/v2\/episodes\/[1-9]\d*\/comments$/.test(path) && req.method === 'GET';
      const history = path === '/v2/playback-histories/sync' && req.method === 'POST';
      if (!(subject && ['GET','PATCH','DELETE'].includes(req.method) || batch && req.method === 'PATCH' || list && req.method === 'GET' || profile || search || trends || recommendations || schedule || history || episodeComments)) {
        reply(res, 404, '接口不存在。'); return true;
      }
      const queryKeys = episodeComments ? ['offset','limit','includeBangumi','after'] : ['type','offset','limit','q','sortBy','include_nsfw','excludeCollectionTypes','tags','airDates','ratings','ranks','fields','mode','today','timeZone'];
      if ([...url.searchParams.keys()].some(k => !queryKeys.includes(k)) || url.search.length > 2048) {
        reply(res, 400, '请求参数无效。'); return true;
      }
      const auth = req.headers.authorization;
      if ((!auth && !search && !trends && !schedule && !episodeComments && !(subject && req.method === 'GET')) || (auth && (!/^Bearer [\w.~-]+$/.test(auth) || auth.length > 8192))) { reply(res, 401, '请登录 Animeko。'); return true; }
      let body;
      if (req.method === 'PATCH' || history) {
        if (!req.headers['content-type']?.startsWith('application/json')) { reply(res, 415, '需要 JSON 请求。'); return true; }
        const chunks = []; let length = 0;
        for await (const chunk of req) {
          length += chunk.length;
          if (length > 65536) { reply(res, 413, '请求过大。'); return true; }
          chunks.push(chunk);
        }
        body = Buffer.concat(chunks);
        try {
          const data = JSON.parse(body.toString('utf8'));
          if (history && (!Array.isArray(data.ops) || data.ops.length || Object.keys(data).some(k => !['ops','lastSyncAt'].includes(k)))) throw new Error('read-only history');
        } catch { reply(res, 400, '请求参数无效。'); return true; }
      }
      // Only reads (including empty-op history sync) can fail over. Never replay a mutation.
      const canRetry = req.method === 'GET' || history;
      const start = preferred;
      let result, bytes;
      for (let attempt = 0; attempt < (canRetry ? upstreams.length : 1); attempt++) {
        const index = (start + attempt) % upstreams.length;
        try {
          result = await fetchImpl(upstreams[index] + path + url.search, {method:req.method, body,
            headers:{Accept:'application/json', ...(auth ? {Authorization:auth} : {}), 'Content-Type':'application/json', 'User-Agent':'AnimeComic-Web/preview'},
            redirect:'error', signal:AbortSignal.timeout(canRetry ? 5000 : 20000)});
          if (!result.ok) {
            await result.body?.cancel();
            if (canRetry && result.status >= 500 && attempt + 1 < upstreams.length) continue;
            break;
          }
          if (result.status !== 204) {
            if (!result.headers.get('content-type')?.toLowerCase().startsWith('application/json')) throw new Error('invalid response');
            const chunks = []; let length = 0;
            for await (const chunk of result.body) {
              length += chunk.length; if (length > 8 * 1024 * 1024) throw new Error('response too large'); chunks.push(Buffer.from(chunk));
            }
            bytes = Buffer.concat(chunks);
            JSON.parse(bytes.toString('utf8'));
          }
          preferred = index;
          break;
        } catch (error) {
          await result?.body?.cancel().catch(() => {});
          result = undefined;
          if (!canRetry || attempt + 1 === upstreams.length) throw error;
        }
      }
      if (!result) throw new Error('unavailable');
      if (!result.ok) {
        await result.body?.cancel();
        reply(res, result.status, result.status === 401 ? 'Animeko 会话已过期，请重新登录。' : `Animeko 接口返回 HTTP ${result.status}`); return true;
      }
      if (result.status === 204) { res.writeHead(204, {'Cache-Control':'no-store'}); res.end(); return true; }
      res.writeHead(result.status, {'Content-Type':'application/json', 'Cache-Control':'no-store', 'X-Content-Type-Options':'nosniff'});
      res.end(bytes);
    } catch { reply(res, 502, '无法连接 Animeko；请刷新确认收藏状态后再重试。'); }
    return true;
  };
}
