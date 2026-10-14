// SPDX-License-Identifier: AGPL-3.0-only
import https from 'node:https';
import { lookup } from 'node:dns/promises';
import {extractOnlineVideo} from './src/wasmJsMain/resources/video-extraction.mjs';
import {createVideoTickets, serveVideo} from './online-video.mjs';

export const onlineSubscription = 'https://sub.creamycake.org/v1/css1.json';
// Only public search pages used by the shared manga adapters; no arbitrary source URL proxy.
export function isMangaSearchUrl(url) {
  if(url.protocol!=='https:'||url.port||url.username||url.password||url.hash) return false;
  const paths={
    'www.manhuagui.com': /^\/s\/[^/]+_p1\.html$/,
    'www.manhuaren.com': /^\/search$/,
    'www.ykmh.net': /^\/search\/$/,
    'cn.bzmgcn.com': /^\/search$/,
    'godamh.com': /^\/s\/[^/]+$/,
    'api.creative-comic.tw': /^\/book$/,
    'comick.art': /^\/search$/,
  };
  return paths[url.hostname]?.test(url.pathname) === true;
}
export function publicAddress(address) {
  const p = address.split('.').map(Number);
  return p.length === 4 && p.every(v => Number.isInteger(v) && v >= 0 && v <= 255) &&
    ![0,10,127].includes(p[0]) && !(p[0] === 169 && p[1] === 254) &&
    !(p[0] === 172 && p[1] >= 16 && p[1] <= 31) && !(p[0] === 192 && [0,168].includes(p[1])) &&
    !(p[0] === 100 && p[1] >= 64 && p[1] <= 127) && p[0] < 224 && p[0] !== 198;
}
export async function readOnlinePage(value) {
  const url = new URL(value);
  if (url.protocol !== 'https:' || url.port || url.username || url.password || url.hash) throw new Error('需要 HTTPS 公共来源地址');
  const addresses = await lookup(url.hostname,{all:true,family:4});
  if (!addresses.length || addresses.some(v => !publicAddress(v.address))) throw new Error('不允许访问本地网络');
  return new Promise((resolve,reject) => {
    const request = https.get(url, {lookup: (_host,options,done) => options.all ? done(null,addresses) : done(null,addresses[0].address,4),
      headers:{'User-Agent':'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36',Accept:'text/html,application/json;q=0.9,*/*;q=0.8',
        ...(isMangaSearchUrl(url)?{Referer:url.origin+'/'}:{}),
        ...(url.hostname==='api.creative-comic.tw'?{device:'web_desktop',uuid:'null',Referer:'https://www.creative-comic.tw/'}:{})},
      timeout:15000}, response => {
      const chunks=[];let size=0;
      response.on('data',chunk=>{size+=chunk.length;if(size>2*1024*1024)request.destroy(new Error('来源页面超过 2 MiB'));else chunks.push(chunk);});
      response.on('end',()=>resolve({status:response.statusCode,type:response.headers['content-type']||'',location:response.headers.location||'',bytes:Buffer.concat(chunks),text:Buffer.concat(chunks).toString('utf8')}));
      response.on('error',reject);
    });
    const deadline=setTimeout(()=>request.destroy(new Error('来源读取超时')),15000);
    request.on('close',()=>clearTimeout(deadline));
    request.on('timeout',()=>request.destroy(new Error('来源读取超时')));
    request.on('error',reject);
  });
}
export function createOnlineRelay({read = readOnlinePage}={}) {
  const tickets=createVideoTickets();
  let catalog, expires=0, pending;
  const redirectedOrigins=new Set();
  async function getCatalog() {
    if (catalog && Date.now()<expires) return catalog;
    if (!pending) pending=(async()=>{
      const response=await read(onlineSubscription);
      if(response.status!==200)throw new Error('在线订阅返回 HTTP '+response.status);
      const data=JSON.parse(response.text);
      const sources=data.exportedMediaSourceDataList?.mediaSources;
      if(!Array.isArray(sources)||sources.length>300)throw new Error('在线订阅格式无效');
      catalog=data;redirectedOrigins.clear();expires=Date.now()+3600000;return catalog;
    })().finally(()=>{pending=null;});
    return pending;
  }
  return async(req,res,url)=>{
    if(!url.pathname.startsWith('/api/online/'))return false;
    const reply=(status,data)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(data));};
    try {
      if(req.method!=='GET'){reply(405,{error:'只允许读取'});return true;}
      if(req.headers['sec-fetch-site']==='cross-site'||req.headers.origin&&new URL(req.headers.origin).host!==req.headers.host){reply(403,{error:'只允许本站请求'});return true;}
      if(url.pathname==='/api/online/media'){
        await serveVideo(req,res,url.searchParams.get('ticket'),tickets);return true;
      }
      if(url.pathname==='/api/online/subscription'){reply(200,await getCatalog());return true;}
      if(!['/api/online/page','/api/online/resolve'].includes(url.pathname)){reply(404,{error:'接口不存在'});return true;}
      const value=url.searchParams.get('url');
      if(!value||value.length>4096){reply(400,{error:'来源地址无效'});return true;}
      let target=new URL(value);
      if(url.pathname==='/api/online/page' && isMangaSearchUrl(target)) {
        const response=await read(target.href);
        if(response.status!==200){reply(response.status>=400&&response.status<=599?response.status:502,{error:'漫画来源返回 HTTP '+response.status+'，请稍后重试或切换来源'});return true;}
        if(!/^(text\/|application\/json)/i.test(response.type)){reply(415,{error:'漫画来源没有返回网页或 JSON'});return true;}
        reply(200,{url:target.href,text:response.text});return true;
      }
      const sources=(await getCatalog()).exportedMediaSourceDataList.mediaSources;
      const origins=new Set(sources.filter(s=>s.factoryId==='web-selector'&&s.version===2).flatMap(s=>{
        try {const u=new URL(s.arguments.searchConfig.searchUrl);return u.protocol==='https:'&&!u.username&&!u.password&&!u.port?[u.origin]:[];}catch{return [];}
      }));
      if(url.pathname==='/api/online/resolve'){
        if(!origins.has(target.origin)&&!redirectedOrigins.has(target.origin)){reply(403,{error:'此站点不在 App 在线订阅中'});return true;}
        const source=sources.find(s=>s.arguments?.searchConfig?.searchUrl===url.searchParams.get('source'));
        if(!source){reply(400,{error:'请选择有效的数据源'});return true;}
        const sourceOrigin=new URL(source.arguments.searchConfig.searchUrl).origin;
        if(sourceOrigin!==target.origin&&!redirectedOrigins.has(target.origin)){reply(403,{error:'播放页与数据源不一致'});return true;}
        const deadline=AbortSignal.timeout(45000);
        const video=await extractOnlineVideo(target.href,async value=>{
          let location=new URL(value);
          for(let i=0;i<4;i++){
            deadline.throwIfAborted();
            const response=await read(location.href);
            if([301,302,303,307,308].includes(response.status)&&response.location){location=new URL(response.location,location);continue;}
            if(response.status!==200)throw new Error('来源返回 HTTP '+response.status);
            if(!/^(text\/|application\/(?:json|javascript))/i.test(response.type))throw new Error('来源内容无法解析');
            return {url:location.href,text:response.text};
          }
          throw new Error('播放页重定向过多');
        },{signal:deadline});
        const configured=source.arguments.searchConfig.matchVideo?.addHeadersToVideo||{};
        const headers={'User-Agent':configured.userAgent||'Mozilla/5.0',Referer:configured.referer??video.pageUrl};
        const ticket=tickets.issue(video.url,headers);
        reply(200,{url:'/api/web/online/media?ticket='+ticket,type:/\.m3u8(?:$|[?#])/i.test(video.url)?'hls':'video',pageUrl:target.href});return true;
      }
      for(let attempt=0;attempt<4;attempt++) {
        if((!origins.has(target.origin)&&!redirectedOrigins.has(target.origin))||target.protocol!=='https:'||target.port||target.username||target.password){reply(403,{error:'此站点不在 App 在线订阅中'});return true;}
        target.hash='';
        const response=await read(target.href);
        if([301,302,303,307,308].includes(response.status)&&response.location){
          target=new URL(response.location,target);
          if(target.protocol!=='https:'||target.port||target.username||target.password)throw new Error('来源跳转地址无效');
          redirectedOrigins.add(target.origin);continue;
        }
        if(response.status!==200){reply(response.status,{error:'来源返回 HTTP '+response.status+'；可能需要登录、验证或稍后重试'});return true;}
        if(!/^(text\/|application\/json)/i.test(response.type)){reply(415,{error:'来源没有返回网页或 JSON'});return true;}
        reply(200,{url:target.href,text:response.text});return true;
      }
      reply(502,{error:'来源重定向过多'});
    }catch(error){reply(502,{error:error.message||'在线来源暂时不可用'});}
    return true;
  };
}
