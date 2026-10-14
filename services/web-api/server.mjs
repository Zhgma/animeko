// SPDX-License-Identifier: AGPL-3.0-only
import http from 'node:http';
import { isIP } from 'node:net';
import { createAnimekoRelay } from '../../web/animeko-relay.mjs';
import { createBangumiRelay } from '../../web/bangumi-relay.mjs';
import { createOAuthRelay } from '../../web/oauth-relay.mjs';
import { createOnlineRelay, readOnlinePage } from '../../web/online-relay.mjs';

export const prefix = '/api/web';
export const allowedOrigins = ['https://zhgma.github.io', 'https://43.131.255.74:8443', 'https://119.28.149.19:8443',
  'http://127.0.0.1:4173', 'http://127.0.0.1:4180', 'http://localhost:4173', 'http://localhost:4180'];
const methods = ['GET', 'POST', 'PATCH', 'DELETE'];
const allowedHeaders = ['authorization', 'content-type', 'accept', 'range'];
const json = (res, status, data) => {
  res.writeHead(status, {'Content-Type':'application/json; charset=utf-8'});
  res.end(JSON.stringify(data));
};

// Subscription reads carry no user credentials and only return bounded JSON/XML/torrent documents.
export async function readFeed(value, read = readOnlinePage, torrent = false) {
  let target = new URL(value);
  for (let attempt = 0; attempt < 4; attempt++) {
    if (target.protocol !== 'https:' || target.port || target.username || target.password || target.hash || target.href.length > 4096) throw new Error('订阅需要公共 HTTPS 地址');
    const result = await read(target.href);
    if ([301,302,303,307,308].includes(result.status) && result.location) { target = new URL(result.location,target); continue; }
    if (result.status !== 200) return {status:result.status, error:'订阅来源返回 HTTP ' + result.status};
    if (torrent) {
      if (!result.bytes || result.bytes[0] !== 100 || result.bytes.at(-1) !== 101 || !/^application\/(?:x-bittorrent|octet-stream)/i.test(result.type)) throw new Error('来源没有返回种子文件');
      return {status:200,type:'application/x-bittorrent',text:result.bytes};
    }
    const text = result.text.trim().replace(/^\uFEFF/,'');
    if (/^(application\/(?:json|[^;]+\+json)|text\/plain)/i.test(result.type) && /^[{[]/.test(text)) {
      JSON.parse(text); return {status:200, type:'application/json; charset=utf-8', text:result.text};
    }
    if (/^(?:<\?xml[^>]*>\s*)?<(?:rss\b|feed\b|rdf:RDF\b)/i.test(text)) return {status:200,type:'application/xml; charset=utf-8',text:result.text};
    throw new Error('订阅没有返回有效的 JSON、RSS 或 Atom 文档');
  }
  throw new Error('订阅重定向过多');
}

export function createWebServer({origins = allowedOrigins, fetchImpl = fetch, read = readOnlinePage,
  trustLoopbackProxy = false, now = Date.now, requestLimit = 300, maxConcurrent = 48} = {}) {
  const allowed = new Set(origins), buckets = new Map();
  const handlers = [createOAuthRelay({fetchImpl,now}), createAnimekoRelay({fetchImpl}),
    createBangumiRelay({fetchImpl}), createOnlineRelay({read})];
  let active = 0;
  const server = http.createServer(async (req,res) => {
    res.setHeader('Cache-Control','no-store'); res.setHeader('X-Content-Type-Options','nosniff');
    res.setHeader('Referrer-Policy','no-referrer'); res.setHeader('Vary','Origin');
    try {
      const origin = req.headers.origin;
      if (origin && !allowed.has(origin)) return json(res,403,{error:'网页域名未获准访问此 API'});
      if (origin) res.setHeader('Access-Control-Allow-Origin',origin);
      res.setHeader('Access-Control-Expose-Headers','Content-Range, Accept-Ranges, Content-Length');
      const url = new URL(req.url,'http://gateway.invalid');
      if (!url.pathname.startsWith(prefix + '/') || req.url.length > 20000) return json(res,404,{error:'接口不存在'});
      if (req.method === 'OPTIONS') {
        const headers = (req.headers['access-control-request-headers'] || '').toLowerCase().split(',').map(s=>s.trim()).filter(Boolean);
        if (!origin || !methods.includes(req.headers['access-control-request-method']) || headers.some(s=>!allowedHeaders.includes(s))) return json(res,403,{error:'跨域请求参数不支持'});
        res.writeHead(204,{'Access-Control-Allow-Methods':methods.join(', '), 'Access-Control-Allow-Headers':allowedHeaders.join(', '),'Access-Control-Max-Age':'600'}); res.end(); return;
      }
      if (!methods.includes(req.method)) return json(res,405,{error:'请求方式不支持'});
      if (Number(req.headers['content-length'] || 0) > 65536) return json(res,413,{error:'请求过大'});
      if (url.pathname === prefix + '/health' && req.method === 'GET') return json(res,200,{service:'animecomic-web-api',version:1});
      let ip = req.socket.remoteAddress;
      if (trustLoopbackProxy && ['127.0.0.1','::1','::ffff:127.0.0.1'].includes(ip) && isIP(req.headers['x-real-ip'] || '')) ip = req.headers['x-real-ip'];
      const time = now();
      for (const [key,bucket] of buckets) if (time-bucket.start >= 60000) buckets.delete(key);
      const bucket = buckets.get(ip) || {start:time,count:0,otp:0,active:0};
      if (!buckets.has(ip) && buckets.size >= 4096) { res.setHeader('Retry-After','60'); return json(res,503,{error:'服务繁忙'}); }
      buckets.set(ip,bucket);
      if (++bucket.count > requestLimit || (url.pathname.endsWith('/email-otp') && ++bucket.otp > 3)) { res.setHeader('Retry-After','60'); return json(res,429,{error:'请求过于频繁，请稍后重试'}); }
      if (active >= maxConcurrent || bucket.active >= 16) { res.setHeader('Retry-After','5'); return json(res,503,{error:'服务繁忙，请稍后重试'}); }
      active++; bucket.active++;
      try {
        // The external origin is checked above. Legacy transport adapters receive a trusted internal request.
        for (const key of ['origin','sec-fetch-site','cookie','referer']) delete req.headers[key];
        if ([prefix + '/feeds',prefix + '/torrent'].includes(url.pathname) && req.method === 'GET') {
          if (req.headers.authorization || [...url.searchParams.keys()].some(k=>k!=='url') || url.searchParams.getAll('url').length !== 1) return json(res,400,{error:'订阅读取不接受账号凭据或额外参数'});
          const result = await readFeed(url.searchParams.get('url'),read,url.pathname.endsWith('/torrent'));
          if (result.error) return json(res,result.status,{error:result.error});
          res.writeHead(200,{'Content-Type':result.type}); res.end(result.text); return;
        }
        url.pathname = '/api' + url.pathname.slice(prefix.length);
        for (const handle of handlers) if (await handle(req,res,url)) return;
        json(res,404,{error:'接口不存在'});
      } finally { active--; bucket.active--; }
    } catch { if (!res.headersSent) json(res,502,{error:'远程 API 读取失败，请检查来源地址或稍后重试'}); else res.destroy(); }
  });
  server.requestTimeout=30000; server.headersTimeout=15000; server.timeout=90000; server.maxHeadersCount=50;
  return server;
}
