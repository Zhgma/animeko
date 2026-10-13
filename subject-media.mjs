// SPDX-License-Identifier: AGPL-3.0-only
import { webUrl, parseFeed, boundedFetch, parseSubscriptionJson } from './media-core.mjs?v=41351d16ee4c92fd';
import { btAttributes } from './bt-attributes.mjs?v=41351d16ee4c92fd';

const gardenHosts = ['api.animes.garden', 'animes.garden', 'garden.breadio.wiki'];
const host = url => { try { return new URL(url).hostname; } catch { return ''; } };
export function defaultSubjectSubscriptions(subscriptions, base, id = () => crypto.randomUUID()) {
  const urls = subscriptions.flatMap(s => [s.url, ...(s.sources || []).map(s => s.url)]);
  const presets = [
    ['AnimeGarden', 'https://api.animes.garden/feed.xml?search={keyword}&page={page}&pageSize=50', gardenHosts],
    ['Nyaa 文学 / 漫画', 'https://nyaa.si/?page=rss&c=3_0&f=0&q={keyword}', ['nyaa.si', 'nyaa.land']],
  ];
  return presets.filter(([, , hosts]) => !urls.some(url => hosts.includes(host(url)))).map(([title, url]) => ({
    id: id(), title, url: '', kind: 'animeko', enabled: true, updated: 0, error: '', items: [],
    sources: parseSubscriptionJson(JSON.stringify({ mediaSources: [{ factoryId: 'rss', version: 1,
      arguments: { name: title, searchConfig: { searchUrl: url, filterByEpisodeSort: false } } }] }), base),
  }));
}
export function subjectSources(subscriptions) {
  return subscriptions.filter(s => s.enabled).flatMap(s => s.kind === 'rss'
    ? [{ id: s.id, title: s.title, url: s.url, items: s.items || [] }]
    : (s.sources || []).filter(v => v.enabled !== false && v.supported && v.kind !== 'web' && v.format?.factoryId !== 'web-selector')
      .map(v => ({ id: s.id + '/' + v.id, title: s.title === v.title ? v.title : s.title + ' · ' + v.title, url: v.url })));
}
export function subjectQuery(source, keyword, kind, page, base) {
  if (typeof keyword !== 'string' || !keyword.trim() || keyword.length > 200) throw new Error('请输入 1–200 字的作品名称');
  if (!Number.isInteger(page) || page < 1 || page > 1000) throw new Error('页码超出范围');
  const template = source.url || '';
  const u = template ? new URL(template, base) : null;
  if (u && gardenHosts.includes(u.hostname) && u.pathname === '/feed.xml') {
    // The legacy Garden RSS redirects without retaining the keyword. Use its public CORS endpoint.
    const url = new URL('https://api.animes.garden/feed.xml');
    for (const [key, value] of u.searchParams) if (!['filter', 'search', 'type', 'types', 'page', 'pageSize'].includes(key)) url.searchParams.append(key, value);
    url.searchParams.set('search', keyword.trim()); url.searchParams.set('type', kind === 'book' ? '漫画' : '动画');
    url.searchParams.set('page', page); url.searchParams.set('pageSize', 50);
    return { url: url.href, remote: true, paged: true, note: 'AnimeGarden · 每页 50 条；分类由发布站提供，请核对发布标题。' };
  }
  if (u && ['nyaa.si', 'nyaa.land'].includes(u.hostname) && u.searchParams.get('page') === 'rss') {
    u.searchParams.set('q', keyword.trim()); u.searchParams.set('c', kind === 'book' ? '3_0' : '1_0');
    return { url: webUrl(u.href, base), remote: true, paged: false, note: 'Nyaa RSS 返回最新匹配条目，不支持翻页；文学分类也包含小说。' };
  }
  return { url: template ? webUrl(template.replaceAll('{keyword}', encodeURIComponent(keyword.trim())).replaceAll('{page}', String(page)), base) : '',
    remote: template.includes('{keyword}'), paged: template.includes('{page}'),
    note: template.includes('{keyword}') ? '使用订阅中的搜索模板。' : '此订阅没有搜索接口：仅在当前 RSS 条目中筛选，不代表完整资源库。' };
}
export function torrentKey(url) {
  if (!url.startsWith('magnet:?')) return url;
  const xt = new URLSearchParams(url.slice(8)).getAll('xt').find(x => /^urn:btih:/i.test(x));
  if (!xt) return url;
  let hash = xt.slice(9).toLowerCase();
  if (/^[a-z2-7]{32}$/.test(hash)) {
    let buffer = 0, bits = 0, hex = '';
    for (const char of hash) { buffer = (buffer << 5) | 'abcdefghijklmnopqrstuvwxyz234567'.indexOf(char); bits += 5;
      if (bits >= 8) { bits -= 8; hex += ((buffer >>> bits) & 255).toString(16).padStart(2, '0'); } }
    hash = hex;
  }
  return 'btih:' + hash;
}
export function filterSubjectItems(items, keyword, remote) {
  const normalize = s => s.normalize('NFKC').toLocaleLowerCase().replace(/\s+/g, ' ').trim();
  const seen = new Set();
  return items.filter(item => {
    if (!item.supported || !item.url || (!remote && !normalize(item.title).includes(normalize(keyword)))) return false;
    const key = torrentKey(item.url); if (seen.has(key)) return false; seen.add(key); return true;
  });
}
export async function searchSubjectSource(source, p, base, read = boundedFetch, parse = parseFeed) {
  const query = subjectQuery(source, p.keyword, p.kind, p.page, base);
  const items = query.url ? parse(new TextDecoder().decode(await read(query.url)), query.url).items : source.items || [];
  return { items: filterSubjectItems(items, p.keyword, query.remote).map(item => ({...item,...btAttributes(item.title,item.date)})), note: query.note, hasNext: query.paged && items.length >= 50 };
}
