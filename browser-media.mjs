// SPDX-License-Identifier: AGPL-3.0-only
import {addDefaultCatalogs, refreshedSubscription, sourceConfigurations} from './subscription-state.mjs?v=a567236a4426cb2b';
import { defaults, checkedSettings, webUrl, torrentIdentifier, parseSubscriptionJson, parseFeed, boundedFetch } from './media-core.mjs?v=a567236a4426cb2b';
import { subjectSources, searchSubjectSource, torrentKey } from './subject-media.mjs?v=a567236a4426cb2b';
import { onlineRead } from './online-media.mjs?v=a567236a4426cb2b';
const prefix = 'animecomic.web.';
const load = (key, fallback) => { try { return JSON.parse(localStorage.getItem(prefix + key)) ?? fallback; } catch { return fallback; } };
const save = (key, value) => localStorage.setItem(prefix + key, JSON.stringify(value));
const base = new URL('.', location.href).href;
let settings; try { settings = checkedSettings(load('mediaSettings', defaults)); } catch { settings = {...defaults}; }
let subscriptions = load('subscriptions', []); if (!Array.isArray(subscriptions)) subscriptions=[];
subscriptions=subscriptions.map(sub=>sub.kind!=='animeko'?sub:{...sub,sources:(sub.sources||[]).map(source=>{
  try {return {...source,...parseSubscriptionJson(JSON.stringify({mediaSources:[source.format]}),base)[0],id:source.id,enabled:source.enabled!==false};}catch{return source;}
})});
const savedTasks = load('torrentTasks', []);
let journal = (Array.isArray(savedTasks) ? savedTasks : []).map(t => ({...t, state:'stopped', peers:0, speed:0, uploadSpeed:0, error:''}));
const active = new Map(), starting = new Set();
let clientPromise, streamingClient, refreshing = false;
const persistTasks = () => save('torrentTasks', journal);
const bytes64 = bytes => { let s = ''; for (const byte of bytes) s += String.fromCharCode(byte); return btoa(s); };
const from64 = text => Uint8Array.from(atob(text), c => c.charCodeAt(0));
async function client() {
  if (!clientPromise) clientPromise = import('./vendor/webtorrent.min.js?v=a567236a4426cb2b').then(({default: WebTorrent}) => {
    if (!WebTorrent.WEBRTC_SUPPORT) throw new Error('当前浏览器不支持 WebRTC');
    const c = new WebTorrent({ maxConns:settings.maxConns, downloadLimit:settings.downloadLimit, uploadLimit:settings.uploadLimit, webSeeds:settings.webSeeds, dht:false, lsd:false, natUpnp:false, natPmp:false });
    c.on('error', e => { for (const task of journal) if (active.has(task.id)) { task.state='error'; task.error=e.message; } active.clear(); clientPromise=null; persistTasks(); });
    return c;
  }).catch(e => {clientPromise=null; throw e;});
  return clientPromise;
}
function taskSnapshot(task) {
  const t = active.get(task.id);
  if (t && !t.destroyed) {
    task.name=t.name || task.name; task.hash=t.infoHash || task.hash; task.progress=t.progress || 0; task.downloaded=t.downloaded || 0; task.length=t.length || 0;
    task.speed=t.downloadSpeed || 0; task.uploadSpeed=t.uploadSpeed || 0; task.peers=t.numPeers || 0;
    task.files=(t.files || []).map((f,i)=>({index:i,name:f.path,length:f.length,progress:f.progress || 0,selected: !task.excluded?.includes(i)}));
    if (t.ready) task.state=t.done?'done':'downloading';
  }
  const {metadata, identifier, ...view} = task; return view;
}
async function start(task) {
  if (active.has(task.id) || starting.has(task.id)) return;
  starting.add(task.id); task.state='metadata'; task.error=''; task.warning='';
  try {
    const c = await client();
    let input = task.metadata ? from64(task.metadata) : task.identifier;
    if (typeof input === 'string' && /^https?:/.test(input)) input = await boundedFetch(input, fetch, 2097152, 'torrent');
    const existing = await c.get(input); if (existing) throw new Error('此种子已经在任务列表中');
    const t=c.add(input, {announce:settings.trackers, destroyStoreOnDestroy:false, deselect:true});
    active.set(task.id,t);
    t.on('error', e => { task.error=e.message; task.state='error'; active.delete(task.id); persistTasks(); });
    t.on('warning', e => { task.warning=e.message; });
    t.on('noPeers', () => { if (!t.done && !t.numPeers) task.warning='没有可连接的浏览器节点；需要 WebRTC 做种节点或支持 CORS 的 Web Seed。'; });
    t.on('ready', () => {
      task.metadata=bytes64(t.torrentFile); task.identifier=t.magnetURI;
      t.files.forEach((f,i)=>{if(!task.excluded?.includes(i))f.select();});
      taskSnapshot(task); persistTasks();
    });
    t.on('done', () => { taskSnapshot(task); task.state='done'; persistTasks(); });
    t.on('metadata', () => {
      task.name=t.name; task.length=t.length;
      if (t.length > 20 * 1024 ** 3) { task.warning='任务超过 20 GiB，请检查浏览器存储空间。'; }
    });
  } catch(e) { task.state='error'; task.error=e.message; persistTasks(); throw e; }
  finally { starting.delete(task.id); }
}
async function stop(task, remove = false) {
  if (starting.has(task.id)) throw new Error('正在加载种子，请稍后再操作');
  const t = active.get(task.id);
  if (t && !t.destroyed) {
    taskSnapshot(task); if (t.torrentFile) task.metadata=bytes64(t.torrentFile);
    const c=await client(); await new Promise((resolve,reject)=>{c.remove(t,{destroyStore:remove},err=>err?reject(err):resolve()).catch(reject);});
  }
  active.delete(task.id); task.state='stopped'; task.peers=0; task.speed=0; task.uploadSpeed=0;
  if (remove) journal=journal.filter(t=>t.id!==task.id);
  persistTasks();
}
function downloadBlob(blob, name) {
  const url=URL.createObjectURL(blob), a=document.createElement('a'); a.href=url; a.download=name.replace(/[\\/]/g,'_'); a.click(); setTimeout(()=>URL.revokeObjectURL(url),60000);
}
function pick(accept, max=2097152) {
  return new Promise((resolve,reject)=>{
    const input=document.createElement('input'); input.type='file'; input.accept=accept;
    input.oncancel=()=>reject(new Error('已取消文件选择'));
    input.onchange=async()=>{const f=input.files?.[0]; if(!f)return reject(new Error('未选择文件')); if(f.size>max)return reject(new Error('文件超过大小限制')); try{resolve({name:f.name,bytes:new Uint8Array(await f.arrayBuffer())});}catch(e){reject(e);}};
    input.click();
  });
}
function parseSubscription(text, url) {
  text = text.trim().replace(/^\uFEFF/, '');
  if(text.startsWith('{')) {
    const sources = parseSubscriptionJson(text,url || base);
    return {kind:sources.length && sources.every(s=>s.kind==='manga')?'manga':'animeko',sources,items:[],configurationText:text};
  }
  const feed=parseFeed(text,url || base); return {kind:'rss',sources:[],...feed};
}
async function refreshSubscription(sub) {
  if(!sub.url) throw new Error('本地导入的订阅请重新导入文件更新');
  try {
    const parsed=parseSubscription(new TextDecoder().decode(await boundedFetch(sub.url)),sub.url);
    Object.assign(sub, refreshedSubscription(sub, parsed));
  } catch(e) { sub.error=e.message; throw e; }
  finally { save('subscriptions',subscriptions); }
}
async function refreshAll() {
  if(refreshing)return;
  refreshing=true;
  try { for(const sub of subscriptions.filter(s=>s.enabled && s.url)) await refreshSubscription(sub).catch(()=>{}); }
  finally { refreshing=false; }
}
setInterval(()=>{if(settings.autoRefresh) for(const sub of subscriptions.filter(s=>s.enabled && s.url && Date.now()-(s.updated||0)>settings.refreshMinutes*60000)) { if(!refreshing){refreshAll();break;} }},60000);
function snapshot() { return {settings, tasks:journal.map(taskSnapshot), subscriptions, subjectSources:subjectSources(subscriptions), refreshing, secure:window.isSecureContext, webrtc:typeof RTCPeerConnection!=='undefined'}; }
async function requireFile(p) {
  const task=journal.find(t=>t.id===p.id); if(!task)throw new Error('任务不存在');
  const t=active.get(task.id); if(!t?.ready)throw new Error('请先继续任务并等待种子加载完成');
  const file=t.files[p.index]; if(!file)throw new Error('文件不存在'); return {task,t,file};
}
export async function mediaAction(action,p={}) {
  if(action==='mediaInitialize' || action==='mediaRestoreDefaults') {
    if(action==='mediaRestoreDefaults' || !load('appCatalogsInstalled', false)) {
      const catalogs=(p.catalogs || []).map(c=>({...c,url:webUrl(c.url,base)}));
      subscriptions=addDefaultCatalogs(subscriptions,catalogs);
      save('subscriptions',subscriptions);save('appCatalogsInstalled',true);
      void refreshAll();
    }
    return snapshot();
  }
  if(action==='mangaSubscriptionSave') {
    const url=webUrl(p.url,base), name=String(p.name || '').trim();
    if(!name)throw new Error('请输入名称');
    if(subscriptions.some(s=>s.url===url && s.id!==p.id))throw new Error('订阅已存在');
    const parsed=parseSubscription(new TextDecoder().decode(await boundedFetch(url)),url);
    if(parsed.kind!=='manga')throw new Error('需要漫画 manga-native sub 订阅；Venera 脚本执行器尚未接入网页');
    const previous=p.id?subscriptions.find(s=>s.id===p.id):null;
    if(p.id && !previous)throw new Error('订阅不存在');
    if(!previous && subscriptions.length>=30)throw new Error('最多添加 30 个订阅');
    const next={...parsed,id:previous?.id || crypto.randomUUID(),url,title:name,enabled:previous?.enabled!==false,updated:Date.now(),error:''};
    if(previous)Object.assign(previous,next);else subscriptions.push(next);
    save('subscriptions',subscriptions);return snapshot();
  }
  if(action==='subscriptionBatch') {
    const keys=new Set(p.sources || []);
    for(const sub of subscriptions) {
      if(sub.kind==='rss') { if(keys.has(sub.id+'/'+sub.id) && p.operation!=='delete')sub.enabled=!!p.enabled; }
      else if(p.operation==='delete')sub.sources=sub.sources.filter(s=>!keys.has(sub.id+'/'+s.id));
      else sub.sources.forEach(s=>{if(keys.has(sub.id+'/'+s.id))s.enabled=!!p.enabled;});
    }
    if(p.operation==='delete')subscriptions=subscriptions.filter(s=>s.kind!=='rss' || !keys.has(s.id+'/'+s.id));
    save('subscriptions',subscriptions);return snapshot();
  }
  if(action==='mediaOnlineRead')return onlineRead(p.url);
  if(action==='mediaState')return snapshot();
  if(action==='mediaSubjectSources') {
    return snapshot();
  }
  if(action==='mediaSubjectSearch') {
    const source=subjectSources(subscriptions).find(s=>s.id===p.source);
    if(!source)throw new Error('来源已停用或删除，请重新选择');
    return {...snapshot(), subjectSearch:await searchSubjectSource(source,p,base)};
  }
  if(action==='torrentCopy') { await navigator.clipboard.writeText(torrentIdentifier(p.url,base)); return snapshot(); }
  if(action==='mediaSettingsValidate') { checkedSettings(p.settings); return snapshot(); }
  if(action==='mediaSettings') {
    const next=checkedSettings(p.settings); save('mediaSettings',next); settings=next;
    if(clientPromise){const c=await client(); c.maxConns=settings.maxConns;c.throttleDownload(settings.downloadLimit);c.throttleUpload(settings.uploadLimit);c.enableWebSeeds=settings.webSeeds;}
    return snapshot();
  }
  if(action==='torrentAdd' || action==='torrentFile') {
    const picked=action==='torrentFile'?await pick('.torrent'):null;
    const identifier=picked?'':torrentIdentifier(p.url,base);
    if(identifier && journal.some(t=>[t.identifier,t.sourceIdentifier].some(url=>url && torrentKey(url)===torrentKey(identifier))))return snapshot();
    if(journal.length>=30)throw new Error('最多保留 30 个任务，请先移除旧任务');
    const task={id:crypto.randomUUID(),identifier,sourceIdentifier:identifier,metadata:picked?bytes64(picked.bytes):'',name:picked?.name || String(p.title || '正在读取种子').slice(0,500),subjectTitle:String(p.subjectTitle || '').slice(0,200),episode:String(p.episode || '').slice(0,20),state:'metadata',progress:0,length:0,downloaded:0,files:[],excluded:[],error:'',warning:''};
    journal.push(task);persistTasks(); await start(task); return snapshot();
  }
  if(['torrentStop','torrentResume','torrentRemove'].includes(action)) {
    const task=journal.find(t=>t.id===p.id); if(!task)throw new Error('任务不存在');
    if(action==='torrentResume')await start(task);else await stop(task,action==='torrentRemove');return snapshot();
  }
  if(action==='torrentSelect') {
    const {task,file}=await requireFile(p); task.excluded=task.excluded.filter(i=>i!==p.index);
    if(p.selected)file.select();else {file.deselect();task.excluded.push(p.index);}persistTasks();return snapshot();
  }
  if(action==='torrentSave') {
    const {file}=await requireFile(p); if(file.progress<1)throw new Error('请等待该文件下载完成');
    if(file.length > 256*1024*1024 && window.showSaveFilePicker){const h=await window.showSaveFilePicker({suggestedName:file.name});const writable=await h.createWritable();try{for await(const chunk of file)await writable.write(chunk);await writable.close();}catch(e){await writable.abort();throw e;}}
    else {if(file.length>256*1024*1024)throw new Error('此浏览器的大文件保存需支持文件系统访问接口；请使用桌面 Chrome/Edge。');downloadBlob(await file.blob(),file.name);}return snapshot();
  }
  if(action==='torrentPlay') {
    const {file}=await requireFile(p);if(!/\.(mp4|webm|ogg|ogv|mp3|m4a|wav)$/i.test(file.name))throw new Error('此文件类型不能直接在浏览器播放');
    const controller=await navigator.serviceWorker.register(new URL('webtorrent-sw.js',base)); await navigator.serviceWorker.ready;
    const c=await client();if(streamingClient!==c){c.createServer({controller});streamingClient=c;}
    document.getElementById('web-media-player')?.remove();
    const dialog=document.createElement('dialog');dialog.id='web-media-player';
    const label=document.createElement('h3');label.textContent=file.name;
    const video=document.createElement('video');video.controls=true;video.playsInline=true;video.playbackRate=settings.playbackRate;
    const message=document.createElement('p');message.textContent='支持程度取决于浏览器的音视频解码器。';
    video.onerror=()=>{message.textContent='浏览器无法解码此文件，或资源读取失败。可下载完成后保存到本机播放。';};
    const close=document.createElement('button');close.textContent='关闭';close.onclick=()=>dialog.close();
    dialog.onclose=()=>{video.pause();video.removeAttribute('src');video.load();dialog.remove();};
    dialog.append(label,video,message,close);document.body.append(dialog);dialog.showModal();file.streamTo(video);return snapshot();
  }
  if(action==='subscriptionAdd' || action==='subscriptionImport' || action==='subscriptionPaste') {
    if(subscriptions.length>=30)throw new Error('最多添加 30 个订阅');
    const picked=action==='subscriptionImport'?await pick('.json,.xml,.rss,.atom'):null;
    const url=picked || action==='subscriptionPaste'?'':webUrl(p.url,base);
    if(url && subscriptions.some(s=>s.url===url))throw new Error('订阅已存在');
    const text=picked?new TextDecoder().decode(picked.bytes):action==='subscriptionPaste'?String(p.content || ''):new TextDecoder().decode(await boundedFetch(url));
    if (action==='subscriptionPaste') parseSubscriptionJson(text.trim().replace(/^\uFEFF/, ''),base);
    const parsed=parseSubscription(text,url);const sub={...parsed,id:crypto.randomUUID(),url,title:p.name?.trim() || parsed.title || picked?.name || (url?new URL(url).hostname:'导入的数据源配置'),enabled:true,updated:Date.now(),error:''};
    subscriptions.push(sub);save('subscriptions',subscriptions);return snapshot();
  }
  if(action==='subscriptionRefreshAll'){await refreshAll();return snapshot();}
  if(action==='subscriptionConfiguration') {
    return {...snapshot(), configurations:sourceConfigurations(subscriptions,p.id,p.source)};
  }
  if(action==='subscriptionSearch') {
    const sub=subscriptions.find(s=>s.id===p.id);if(!sub?.enabled)throw new Error('订阅已停用或不存在');
    const source=sub.sources?.find(s=>s.id===p.source);if(!source?.supported || source.enabled===false)throw new Error('数据源未启用或不支持');
    const url=webUrl(source.url.replaceAll('{keyword}',encodeURIComponent(p.keyword||'')).replaceAll('{page}',String(Math.max(1,Math.floor(p.page||1)))),base);
    const feed=parseFeed(new TextDecoder().decode(await boundedFetch(url)),url);return {...snapshot(),feedItems:feed.items};
  }
  if(action.startsWith('subscription')) {
    const sub=subscriptions.find(s=>s.id===p.id);if(!sub)throw new Error('订阅不存在');
    if(action==='subscriptionRefresh')await refreshSubscription(sub);
    else if(action==='subscriptionToggle')sub.enabled=!!p.enabled;
    else if(action==='subscriptionSourceToggle'){const source=sub.sources.find(s=>s.id===p.source);if(!source)throw new Error('来源不存在');source.enabled=!!p.enabled;}
    else if(action==='subscriptionRemove')subscriptions=subscriptions.filter(s=>s.id!==p.id);
    else throw new Error('未知订阅操作');
    save('subscriptions',subscriptions);return snapshot();
  }
  if(action==='mediaStorage'){const estimate=await navigator.storage?.estimate();return {...snapshot(),usage:estimate?.usage||0,quota:estimate?.quota||0};}
  throw new Error('未知媒体操作');
}
