// SPDX-License-Identifier: AGPL-3.0-only
export const sourceKey = source => JSON.stringify([source.format?.factoryId, source.title, source.url, source.format?.arguments?.key]);
export function addDefaultCatalogs(subscriptions, catalogs, id = () => crypto.randomUUID()) {
  const seen = new Set(subscriptions.map(s => s.url));
  const additions = [];
  for (const catalog of catalogs) {
    if (seen.has(catalog.url)) continue;
    if (subscriptions.length + additions.length >= 30) break;
    seen.add(catalog.url);
    additions.push({...catalog, id:id(), enabled:true, sources:[], items:[], updated:0, error:''});
  }
  return [...subscriptions, ...additions];
}
export function refreshedSubscription(sub, parsed, now = Date.now()) {
  const previous = new Map((sub.sources || []).map(s => [sourceKey(s), s]));
  const sources = parsed.sources.map(s => ({...s, id: previous.get(sourceKey(s))?.id ?? crypto.randomUUID(), enabled: previous.get(sourceKey(s))?.enabled !== false}));
  // Local ordering belongs to the user, independently of the catalog's order.
  const order = (sub.sources || []).map(sourceKey);
  sources.sort((a,b) => (order.includes(sourceKey(a)) ? order.indexOf(sourceKey(a)) : 9999) - (order.includes(sourceKey(b)) ? order.indexOf(sourceKey(b)) : 9999));
  return {...sub, ...parsed, title:sub.title, kind:sub.kind === 'manga' ? 'manga' : parsed.kind, sources, updated:now, error:''};
}
export function sourceConfigurations(subscriptions, subscriptionId, sourceId) {
  return subscriptions.filter(s => !subscriptionId || s.id === subscriptionId).flatMap(s => {
    if (s.kind === 'rss') return s.url ? [JSON.stringify({mediaSources:[{factoryId:'rss',version:1,arguments:{name:s.title,description:'',iconUrl:'',searchConfig:{searchUrl:s.url,filterByEpisodeSort:false}}}]})] : [];
    return [JSON.stringify({mediaSources:(s.sources || []).filter(v => !sourceId || v.id === sourceId).map(v => v.format)})];
  });
}
