// SPDX-License-Identifier: AGPL-3.0-only
import './api-endpoints.js?v=babfb6e08d91e8b1';
export const defaults = { maxConns: 20, downloadLimit: -1, uploadLimit: -1, webSeeds: true, trackers: [], refreshMinutes: 60, autoRefresh: true, playbackRate: 1 };
export function checkedSettings(value = {}) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('网页 BT 设置必须是 JSON 对象');
  for (const key of ['webSeeds','autoRefresh']) if (value[key] !== undefined && typeof value[key] !== 'boolean') throw new Error('设置必须是布尔值：' + key);
  const n = (key, min, max) => {
    const v = value[key] ?? defaults[key];
    if (!Number.isFinite(v) || v < min || v > max) throw new Error('设置数值超出范围：' + key);
    return v;
  };
  const trackers = value.trackers ?? [];
  if (!Array.isArray(trackers) || trackers.length > 20 || trackers.some(u => !/^wss:\/\//i.test(u))) throw new Error('Tracker 仅支持 wss:// 地址，最多 20 项');
  trackers.forEach(u => { const url = new URL(u); if (url.username || url.password) throw new Error('Tracker 不能含用户名或密码'); });
  return { maxConns: Math.round(n('maxConns', 1, 100)), downloadLimit: Math.round(n('downloadLimit', -1, 1073741824)), uploadLimit: Math.round(n('uploadLimit', -1, 1073741824)),
    webSeeds: value.webSeeds !== false, trackers: [...new Set(trackers)], refreshMinutes: Math.round(n('refreshMinutes', 5, 1440)), autoRefresh: value.autoRefresh === true, playbackRate: n('playbackRate', 0.25, 4) };
}
export function webUrl(value, base) {
  if (typeof value !== 'string' || !value.trim()) throw new Error('链接不能为空');
  const u = new URL(value, base);
  if (!['https:', 'http:'].includes(u.protocol) || u.username || u.password) throw new Error('仅支持不含用户名/密码的 HTTP(S) 地址');
  if (u.protocol === 'http:' && u.origin !== new URL(base).origin) throw new Error('远程地址必须使用 HTTPS');
  return u.href;
}
export function torrentIdentifier(value, base) {
  const text = String(value || '').trim();
  if (text.length > 16384) throw new Error('种子链接过长');
  if (/^[a-f0-9]{40}$/i.test(text)) return 'magnet:?xt=urn:btih:' + text.toLowerCase();
  if (text.startsWith('magnet:?')) {
    const p = new URLSearchParams(text.slice(8));
    if (!p.getAll('xt').some(v => /^urn:btih:([a-f0-9]{40}|[a-z2-7]{32})$/i.test(v))) throw new Error('需要有效的 BitTorrent v1 磁力链接');
    for (const key of ['ws','xs','as']) for (const url of p.getAll(key)) webUrl(url, base);
    return text;
  }
  return webUrl(text, base);
}
export function parseSubscriptionJson(text, base) {
  if (typeof text !== 'string' || text.length > 2097152) throw new Error('配置超过 2 MiB');
  const json = JSON.parse(text);
  const sources = json?.mediaSources ?? json?.exportedMediaSourceDataList?.mediaSources;
  if (!Array.isArray(sources) || sources.length > 300) throw new Error('需要 Animeko mediaSources 订阅格式；Venera/Mihon 扩展不能直接运行');
  return sources.map((source, i) => {
    if (!source || typeof source.factoryId !== 'string' || !source.factoryId || !Number.isInteger(source.version) || source.version < 1 || !source.arguments || typeof source.arguments !== 'object' || Array.isArray(source.arguments)) throw new Error('数据源缺少有效的 factoryId、version 或 arguments');
    const a = source.arguments || {};
    const url = typeof a.searchConfig?.searchUrl === 'string' ? a.searchConfig.searchUrl : '';
    let supported = source.factoryId === 'rss' && source.version === 1 && !!url;
    const manga = source.factoryId === 'manga-native';
    const online = source.factoryId === 'web-selector' && source.version === 2;
    if (online) supported = !!url && ['a','indexed','json-path-indexed'].includes(a.searchConfig.subjectFormatId || 'a') && ['index-grouped','no-channel'].includes(a.searchConfig.channelFormatId || 'no-channel');
    if (supported) { try { webUrl(url.replaceAll('{keyword}', '').replaceAll('{page}', '1'), base); } catch { supported = false; } }
    return { id: 'source-' + i, title: String(a.name || source.factoryId || '未命名源').slice(0,200), url,
      supported, reason: supported ? '' : manga ? '已导入漫画源配置；网页阅读适配器尚未接入' : '此源的类型或版本尚未接入网页执行器', kind: manga ? 'manga' : online ? 'web' : 'bt', format: source };
  });
}
export function parseFeed(text, base, Parser = DOMParser) {
  if (/<!DOCTYPE|<!ENTITY/i.test(text)) throw new Error('订阅不允许 DTD 或实体声明');
  const doc = new Parser().parseFromString(text, 'application/xml');
  if (doc.querySelector('parsererror') || !['rss','feed','RDF'].includes(doc.documentElement.localName)) throw new Error('不是有效的 RSS/Atom 订阅');
  const value = (el, name) => Array.from(el.children).find(n => n.localName.toLowerCase() === name)?.textContent?.trim() || '';
  const safe = url => { try { return url?.startsWith('magnet:') ? torrentIdentifier(url, base) : webUrl(url, base); } catch { return ''; } };
  const nodes = Array.from(doc.getElementsByTagName('*')).filter(n => ['item','entry'].includes(n.localName));
  const items = nodes.slice(0,300).map((node, i) => {
    const links = Array.from(node.children).filter(n => ['link','enclosure'].includes(n.localName));
    const urls = links.map(n => ({ url: safe(n.getAttribute('url') || n.getAttribute('href') || n.textContent?.trim()), type: n.getAttribute('type') || '', rel: n.getAttribute('rel') || n.localName }));
    const hash = value(node,'infohash');
    const magnet = urls.find(n => n.url.startsWith('magnet:'))?.url || (/^[a-f0-9]{40}$/i.test(hash) ? 'magnet:?xt=urn:btih:' + hash : '');
    const torrent = urls.find(n => n.type.includes('bittorrent') || /\.torrent(?:\?|$)/i.test(n.url))?.url || '';
    const length = Number(links.find(n => n.localName === 'enclosure')?.getAttribute('length') || 0);
    return { id: String(i), title: value(node,'title') || '未命名条目', url: magnet || torrent, size: Number.isSafeInteger(length) && length > 0 ? length : 0,
      page: urls.find(n => !n.url.startsWith('magnet:') && n.rel !== 'enclosure')?.url || '', date: value(node,'pubdate') || value(node,'updated'), supported: !!(magnet || torrent) };
  });
  return { title: doc.querySelector('channel > title, feed > title')?.textContent?.trim() || 'RSS / Atom', items };
}
export async function boundedFetch(url, fetcher = fetch, limit = 2097152, kind = 'feed') {
  let response;
  try { response = await fetcher(kind === 'torrent' ? globalThis.aniApi.torrent(url) : globalThis.aniApi.feed(url), { mode:'cors', credentials: 'omit', referrerPolicy: 'no-referrer', signal: AbortSignal.timeout(20000) }); }
  catch (e) { throw new Error('远程订阅 API 读取失败，请检查网络或稍后重试。'); }
  if (!response.ok) { const data = await response.json().catch(()=>({})); throw new Error(data.error || '订阅返回 HTTP ' + response.status); }
  if (Number(response.headers.get('content-length')) > limit) throw new Error('订阅/种子文件超过 2 MiB');
  const reader = response.body.getReader(), chunks = []; let length = 0;
  try { while (true) { const {value, done} = await reader.read(); if (done) break; length += value.length; if (length > limit) throw new Error('订阅/种子文件超过 2 MiB'); chunks.push(value); } }
  catch (e) { await reader.cancel(); throw e; }
  const bytes = new Uint8Array(length); let offset = 0; for (const b of chunks) { bytes.set(b, offset); offset += b.length; } return bytes;
}
