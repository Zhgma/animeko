// SPDX-License-Identifier: AGPL-3.0-only
import https from 'node:https';
import {lookup} from 'node:dns/promises';
import {createHmac, randomBytes, timingSafeEqual} from 'node:crypto';
import {publicAddress} from './online-relay.mjs';

export function rewritePlaylist(text, base, sign) {
  const asset = value => {
    const url = new URL(value, base);
    if (url.protocol !== 'https:' || url.username || url.password) throw new Error('视频地址必须使用公共 HTTPS');
    return sign(url.href);
  };
  return text.split(/\r?\n/).map(line => {
    if (!line.trim()) return line;
    if (!line.startsWith('#')) return asset(line.trim());
    return line.replace(/URI="([^"]+)"/g, (_,uri)=>'URI="'+asset(uri)+'"');
  }).join('\n');
}

export function createVideoTickets({secret=randomBytes(32), now=Date.now}={}) {
  const mac = value => createHmac('sha256',secret).update(value).digest('base64url');
  return {
    issue(url, headers={}, expires=now()+6*3600000) {
      const data=Buffer.from(JSON.stringify({url,headers,expires})).toString('base64url');
      return data+'.'+mac(data);
    },
    read(ticket) {
      if (typeof ticket !== 'string' || ticket.length>16000) throw new Error('播放凭据无效');
      const [data, signature, extra]=ticket.split('.'); const expected=mac(data||'');
      if(extra || signature?.length!==expected.length || !timingSafeEqual(Buffer.from(signature),Buffer.from(expected))) throw new Error('播放凭据无效');
      const value=JSON.parse(Buffer.from(data,'base64url').toString());
      if(!Number.isFinite(value.expires)||value.expires<now())throw new Error('播放地址已过期，请重新选择线路');
      return value;
    },
  };
}

// Only signed, extractor-discovered media reaches this transport. DNS is pinned per redirect.
export async function openVideo(url, headers, range, redirects=0) {
  const target=new URL(url);
  if(target.protocol!=='https:'||target.username||target.password||redirects>4)throw new Error('视频跳转地址无效');
  const addresses=await lookup(target.hostname,{all:true,family:4});
  if(!addresses.length||addresses.some(v=>!publicAddress(v.address)))throw new Error('不允许读取本地网络');
  return new Promise((resolve,reject)=>{
    const request=https.get(target,{headers:{'User-Agent':headers['User-Agent']||'Mozilla/5.0',
      ...(headers.Referer?{Referer:headers.Referer}:{}),...(range?{Range:range}:{}),Accept:'*/*'},
      lookup:(_host,options,done)=>options.all?done(null,addresses):done(null,addresses[0].address,4)},response=>{
      if([301,302,303,307,308].includes(response.statusCode)&&response.headers.location){response.resume();openVideo(new URL(response.headers.location,target).href,headers,range,redirects+1).then(resolve,reject);}
      else resolve({response,url:target.href});
    });
    request.setTimeout(20000,()=>request.destroy(new Error('视频读取超时')));
    request.on('error',reject);
  });
}

export async function serveVideo(req,res,ticket,tickets,{open=openVideo,prefix='/api/web/online/media'}={}) {
  let value;
  try{value=tickets.read(ticket);}catch(e){res.writeHead(403,{'Content-Type':'application/json'});res.end(JSON.stringify({error:e.message}));return;}
  const range=req.headers.range;
  if(range&&!/^bytes=\d+-\d*$/.test(range)){res.writeHead(416);res.end();return;}
  const {response,url}=await open(value.url,value.headers,range);
  const status=response.statusCode;
  if(![200,206].includes(status)){response.resume();res.writeHead(status>=400&&status<=599?status:502);res.end();return;}
  const type=response.headers['content-type']||'application/octet-stream';
  if(/mpegurl/i.test(type)||/\.m3u8(?:$|[?#])/i.test(url)) {
    const chunks=[];let size=0;
    for await(const chunk of response){size+=chunk.length;if(size>2*1024*1024){response.destroy();throw new Error('播放列表过大');}chunks.push(chunk);}
    const text=Buffer.concat(chunks).toString('utf8');
    if(!text.trimStart().startsWith('#EXTM3U'))throw new Error('来源没有返回有效播放列表');
    const playlist=rewritePlaylist(text,url,child=>prefix+'?ticket='+tickets.issue(child,value.headers,value.expires));
    res.writeHead(200,{'Content-Type':'application/vnd.apple.mpegurl','Cache-Control':'no-store'});res.end(playlist);return;
  }
  // Never serve upstream HTML/script as a media response.
  if(/text\/html|javascript|application\/json/i.test(type)){response.resume();res.writeHead(415);res.end();return;}
  const headers={'Content-Type':type,'Cache-Control':'private, max-age=60','Accept-Ranges':'bytes'};
  for(const key of ['content-length','content-range'])if(response.headers[key])headers[key]=response.headers[key];
  res.writeHead(status,headers);
  await new Promise((resolve,reject)=>{
    const close=()=>{response.destroy();resolve();};res.once('close',close);
    response.on('error',reject);response.on('end',()=>{res.off('close',close);resolve();});response.pipe(res);
  });
}
