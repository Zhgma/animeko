// SPDX-License-Identifier: AGPL-3.0-only
import './api-endpoints.js?v=44c78ea8887c23c5';
import {videoCandidates} from './video-extraction.mjs?v=44c78ea8887c23c5';
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
  const reader=response.body.getReader(), parts=[];let length=0;
  try { while(true){const {done,value}=await reader.read();if(done)break;length+=value.byteLength;if(length>12*1024*1024)throw new Error('来源响应超过 12 MiB');parts.push(value);} }
  finally { await reader.cancel();reader.releaseLock(); }
  const bytes=new Uint8Array(length);let offset=0;for(const part of parts){bytes.set(part,offset);offset+=part.length;}
  const page=JSON.parse(new TextDecoder().decode(bytes));
  if (typeof page.text !== 'string' || typeof page.url !== 'string') throw new Error('网站读取 API 返回无效内容');
  return page;
}
export function staticVideoUrl(text, pageUrl) { return videoCandidates(text,pageUrl).videos[0] || ''; }
let hlsScript;
async function loadHls() {
  if(!hlsScript)hlsScript=new Promise((resolve,reject)=>{
    const script=document.createElement('script');script.src='./vendor/hls.min.js?v=44c78ea8887c23c5';script.onload=resolve;
    script.onerror=()=>{script.remove();reject(new Error('播放器加载失败'));};document.head.append(script);
  }).catch(e=>{hlsScript=null;throw e;});
  await hlsScript;
  if(!window.Hls?.isSupported())throw new Error('当前浏览器不支持 HLS');
}

// HtmlElementView owns this surface. All visible controls are Compose components.
export function createOnlineVideo(payload, emit) {
  const {url,title,source,episodes=[],index=0,playerConfig={}}=payload;
  const playlist=episodes.length?episodes:[{url,name:title}];
  const host=document.createElement('div'),video=document.createElement('video');
  host.style.cssText='position:relative;width:100%;height:100%;background:#000;overflow:hidden';
  video.style.cssText='display:block;width:100%;height:100%;object-fit:contain';
  video.playsInline=true;video.preload='metadata';video.setAttribute('aria-label',title||'视频');host.append(video);
  let position=Math.max(0,Math.min(index,playlist.length-1)),abort,dispose=()=>{},generation=0,closed=false;
  let message='正在提取视频…',failed=false,loading=true;
  const minRate=playerConfig.minPlaybackSpeed??.5,maxRate=playerConfig.maxPlaybackSpeed??2.5;
  const clampRate=value=>Math.max(minRate,Math.min(maxRate,value));
  let initialRate=clampRate(playerConfig.playbackSpeed??1);
  video.defaultPlaybackRate=initialRate;video.playbackRate=initialRate;
  function notify(saveRate=null){if(!closed)emit(JSON.stringify({position,time:video.currentTime||0,duration:Number.isFinite(video.duration)?video.duration:0,
    paused:video.paused,muted:video.muted,loading,message,failed,rate:video.playbackRate,fullscreen:!!document.fullscreenElement,
    saveRate:typeof saveRate==='number'?saveRate:null}));}
  function status(text,error=false){message=text;failed=error;notify();}
  function cleanup(){abort?.abort();dispose();dispose=()=>{};video.pause();video.removeAttribute('src');video.load();}
  async function play(){
    cleanup();const id=++generation,controller=new AbortController();abort=controller;loading=true;status('正在提取视频…');
    try {
      const response=await fetch(globalThis.aniApi.base+'/online/resolve?'+new URLSearchParams({url:playlist[position].url,source}),{
        credentials:'omit',signal:AbortSignal.any([controller.signal,AbortSignal.timeout(60000)])});
      const result=await response.json();if(!response.ok)throw new Error(response.status===404?'视频提取接口未部署（HTTP 404），请检查媒体 API。':(result.error||'视频提取失败'));
      if(id!==generation||closed)return;
      const resolved=new URL(result.url,globalThis.aniApi.base);
      if(resolved.origin!==new URL(globalThis.aniApi.base).origin)throw new Error('视频接口返回了无效地址');
      status('视频已提取，正在加载…');
      const start=()=>video.play().catch(()=>{if(id===generation&&!closed)status('点击播放开始观看');});
      if(result.type==='hls'&&!video.canPlayType('application/vnd.apple.mpegurl')) {
        await loadHls();if(id!==generation||closed)return;
        const hls=new window.Hls();let recovered=false;
        hls.on(window.Hls.Events.ERROR,(_event,data)=>{if(data.fatal&&id===generation&&!closed){
          if(data.type===window.Hls.ErrorTypes.MEDIA_ERROR&&!recovered){recovered=true;hls.recoverMediaError();}
          else {loading=false;status('来源暂时无法读取，请重试或切换数据源。',true);hls.stopLoad();}
        }});
        hls.on(window.Hls.Events.MANIFEST_PARSED,start);
        hls.loadSource(resolved.href);hls.attachMedia(video);dispose=()=>hls.destroy();
      }else{video.src=resolved.href;void start();}
    }catch(e){if(!controller.signal.aborted&&id===generation&&!closed){loading=false;status(e.message||'视频提取失败，请切换线路',true);}}
  }
  video.onplaying=()=>{loading=false;status('');};
  video.onwaiting=()=>{loading=true;notify();};
  video.onerror=()=>{if(!closed&&video.getAttribute('src')){loading=false;status('视频无法播放，请重试或切换数据源。',true);}};
  for(const name of ['timeupdate','durationchange','pause','play','volumechange','ratechange','loadedmetadata'])video.addEventListener(name,notify);
  let holding=false,holdTimer,restoreRate,ignoreClick=false;
  const endHold=()=>{clearTimeout(holdTimer);if(holding){video.playbackRate=restoreRate;holding=false;ignoreClick=true;notify();}};
  video.onpointerdown=event=>{if(event.button!==0)return;holdTimer=setTimeout(()=>{
    if(closed||video.paused)return;holding=true;restoreRate=video.playbackRate;video.playbackRate=clampRate(playerConfig.fastForwardSpeed??2.5);notify();
  },350);};
  video.onpointerup=endHold;video.onpointercancel=endHold;video.onpointerleave=endHold;
  video.onclick=()=>{if(ignoreClick){ignoreClick=false;return;}host.aniCommand('toggle',0);};
  video.onended=()=>{if(playerConfig.autoPlayNext!==false&&position+1<playlist.length){position++;void play();}};
  const toggleFullscreen=async()=>{
    try {if(document.fullscreenElement)await document.exitFullscreen();else await document.documentElement.requestFullscreen();}
    catch{status('浏览器未允许全屏，可使用系统全屏按钮。');}
  };
  video.ondblclick=toggleFullscreen;
  const floating=document.createElement('button');floating.textContent='⛶';floating.setAttribute('aria-label','全屏');
  floating.style.cssText='position:absolute;right:12px;bottom:12px;border:0;border-radius:50%;width:40px;height:40px;color:white;background:#0009;font-size:26px;cursor:pointer';
  floating.onclick=toggleFullscreen;
  let fullscreenTimer;
  const showFloating=()=>{
    const mode=playerConfig.fullscreenSwitchMode??'ALWAYS_SHOW_FLOATING';
    floating.hidden=mode==='ONLY_IN_CONTROLLER';clearTimeout(fullscreenTimer);
    if(mode==='AUTO_HIDE_FLOATING')fullscreenTimer=setTimeout(()=>{floating.hidden=true;},5000);
  };
  host.append(floating);host.onpointermove=showFloating;showFloating();
  host.aniCommand=(action,value)=>{
    if(closed)return;
    if(action==='toggle'){if(video.paused)video.play().catch(()=>status('当前视频尚未就绪'));else video.pause();}
    if(action==='seek'&&Number.isFinite(video.duration))video.currentTime=Math.max(0,Math.min(value,video.duration));
    if(action==='mute')video.muted=!video.muted;
    if(action==='rate'&&Number.isFinite(value)&&value>=minRate&&value<=maxRate){
      endHold();video.defaultPlaybackRate=value;video.playbackRate=value;
      notify(playerConfig.rememberPlaybackSpeed===false?null:value);return;
    }
    if(action==='episode'&&Number.isInteger(value)&&value>=0&&value<playlist.length&&value!==position){endHold();position=value;void play();}
    if(action==='retry')void play();
    if(action==='fullscreen')void toggleFullscreen();
    notify();
  };
  const keyboard=event=>{
    if(event.defaultPrevented||event.ctrlKey||event.altKey||event.metaKey||/INPUT|TEXTAREA|SELECT/.test(event.target?.tagName)||event.target?.isContentEditable)return;
    if(event.code==='Space'){event.preventDefault();host.aniCommand('toggle',0);}
    if(event.code==='ArrowRight'||event.code==='ArrowLeft'){event.preventDefault();host.aniCommand('seek',video.currentTime+(event.code==='ArrowRight'?5:-5));}
  };
  document.addEventListener('fullscreenchange',notify);document.addEventListener('keydown',keyboard);
  host.aniDispose=()=>{if(closed)return;endHold();clearTimeout(fullscreenTimer);closed=true;generation++;cleanup();document.removeEventListener('fullscreenchange',notify);document.removeEventListener('keydown',keyboard);if(document.fullscreenElement)document.exitFullscreen().catch(()=>{});};
  // Allow Compose to attach the element before state changes trigger recomposition.
  queueMicrotask(()=>{if(!closed)void play();});
  return host;
}
globalThis.aniCreateVideo=(json,callback)=>createOnlineVideo(JSON.parse(json),callback);
