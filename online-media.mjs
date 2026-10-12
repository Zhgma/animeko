// SPDX-License-Identifier: AGPL-3.0-only
import './api-endpoints.js?v=f2d23ecc1bbfac74';
export async function onlineRead(url) {
  const target = new URL(url);
  if (target.protocol !== 'https:' || target.username || target.password) throw new Error('在线来源必须使用 HTTPS');
  let response;
  try { response = await fetch(globalThis.aniApi.page(target.href), {mode:'cors', credentials:'omit', referrerPolicy:'no-referrer', signal:AbortSignal.timeout(20000)}); }
  catch (error) {
    if (error instanceof TypeError) throw new Error('无法连接远程网站读取 API，请检查网络或稍后重试。');
    throw error;
  }
  if (!response.ok) { const data = await response.json().catch(()=>({})); throw new Error(data.error || '网站读取 API 返回 HTTP ' + response.status); }
  const reader = response.body.getReader(), parts = []; let length = 0;
  try {
    while (true) {
      const {done,value} = await reader.read(); if (done) break;
      length += value.byteLength;
      if (length > 12 * 1024 * 1024) throw new Error('来源响应超过 12 MiB');
      parts.push(value);
    }
  } finally { await reader.cancel(); reader.releaseLock(); }
  const bytes = new Uint8Array(length); let offset = 0;
  for (const part of parts) { bytes.set(part, offset); offset += part.length; }
  const page = JSON.parse(new TextDecoder().decode(bytes));
  if (typeof page.text !== 'string' || typeof page.url !== 'string') throw new Error('网站读取 API 返回无效内容');
  return page;
}
export function staticVideoUrl(text, pageUrl) {
  const match=text.match(/(?:var|let|const)\s+player_\w+\s*=\s*(\{[^\n]+?\})\s*(?:;|<)/);
  if(!match)return '';
  try {
    const player=JSON.parse(match[1]);let value=player.url;
    if(player.encrypt===2||player.encrypt==='2')value=decodeURIComponent(atob(value));
    else if(player.encrypt===1||player.encrypt==='1')value=decodeURIComponent(value);
    const url=new URL(value,pageUrl);
    return url.protocol==='https:'&&!url.username&&!url.password&&/\.(mp4|webm|m3u8)(?:$|[?#])/i.test(url.href)?url.href:'';
  }catch{return '';}
}
let hlsScript;
export async function onlinePlay({url,title},playbackRate=1) {
  const page=await onlineRead(url);
  const direct=staticVideoUrl(page.text,page.url);
  document.getElementById('web-media-player')?.close();
  const dialog=document.createElement('dialog');dialog.id='web-media-player';
  const label=document.createElement('h3');label.textContent=title||'在线播放';
  const status=document.createElement('p');
  const original=document.createElement('a');original.textContent='在来源站点打开';original.href=page.url;original.target='_blank';original.rel='noopener noreferrer';
  const close=document.createElement('button');close.textContent='关闭';close.onclick=()=>dialog.close();
  let dispose=()=>{};
  dialog.onclose=()=>{dispose();dialog.remove();};
  dialog.append(label,status,original,close);document.body.append(dialog);dialog.showModal();
  if(direct) {
    const video=document.createElement('video');video.controls=true;video.playsInline=true;video.playbackRate=playbackRate;dialog.insertBefore(video,status);
    status.textContent='在线资源；无需 BT 节点。';
    video.onerror=()=>{status.textContent='来源视频无法读取或浏览器不支持编码；可到来源站点播放。';};
    if(/\.m3u8(?:$|[?#])/i.test(direct)&&!video.canPlayType('application/vnd.apple.mpegurl')) {
      try {
        if(!hlsScript)hlsScript=new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='./vendor/hls.min.js?v=f2d23ecc1bbfac74';script.onload=resolve;script.onerror=()=>reject(new Error('播放器加载失败'));document.head.append(script);}).catch(e=>{hlsScript=null;throw e;});
        await hlsScript;if(!dialog.open)return;
        if(!window.Hls?.isSupported())throw new Error('当前浏览器不支持 HLS');
        const hls=new window.Hls();hls.on(window.Hls.Events.ERROR,(_event,data)=>{if(data.fatal){status.textContent='HLS 来源拒绝跨域或无法读取；可到来源站点播放。';hls.stopLoad();}});
        hls.loadSource(direct);hls.attachMedia(video);dispose=()=>hls.destroy();
      }catch(error){status.textContent=error.message;}
    }else video.src=direct;
    const cleanup=dispose;dispose=()=>{cleanup();video.pause();video.removeAttribute('src');video.load();};
  }else {
    const frame=document.createElement('iframe');frame.src=page.url;frame.title=title||'来源播放器';frame.allow='fullscreen';frame.setAttribute('sandbox','allow-scripts allow-same-origin allow-presentation');
    frame.style.cssText='width:100%;height:60vh;border:0;background:#000';dialog.insertBefore(frame,status);
    status.textContent='此来源需要站点播放器，当前嵌入原站页面；若站点禁止嵌入，请使用下方入口。';dispose=()=>frame.remove();
  }
}
