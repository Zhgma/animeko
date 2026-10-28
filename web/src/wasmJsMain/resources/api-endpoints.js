// SPDX-License-Identifier: AGPL-3.0-only
(() => {
  const fallback = 'https://43.131.252.236:8443';
  const defaultSubscription = 'https://raw.githubusercontent.com/Zhgma/animeko/main/subs/api.json';
  const key = 'animecomic.relay.v1';
  const validate = value => {
    const text = String(value).trim().replace(/\/$/, '');
    const u = new URL(text);
    if (u.protocol !== 'https:' || u.username || u.password || u.pathname !== '/' || u.search || u.hash || /\s/.test(text)) throw new Error('请输入 HTTPS 服务器地址，不含 /api 路径');
    return u.origin;
  };
  const subscriptionUrl = value => {
    const u = new URL(value);
    if (u.protocol !== 'https:' || u.username || u.password || u.hash) throw new Error('请输入有效的 HTTPS 配置订阅链接');
    return u.href;
  };
  let state = {manualOrigin:'', subscriptionUrl:defaultSubscription, subscribedOrigin:fallback, previousOrigins:[]};
  try {
    const saved = JSON.parse(globalThis.localStorage?.getItem(key) || 'null');
    if (saved) state = {...state, ...saved, manualOrigin:saved.manualOrigin ? validate(saved.manualOrigin) : '',
      subscribedOrigin:validate(saved.subscribedOrigin), subscriptionUrl:subscriptionUrl(saved.subscriptionUrl),
      previousOrigins:(saved.previousOrigins || []).map(validate)};
  } catch {}
  const origin = () => state.manualOrigin || state.subscribedOrigin;
  const base = () => origin() + '/api/web';
  let listener;
  const commit = next => {
    const previousOrigins = [...new Set([...state.previousOrigins, origin()])].slice(-20);
    const updated = {...next, previousOrigins};
    globalThis.localStorage?.setItem(key, JSON.stringify(updated));
    state = updated;
    listener?.(origin(), JSON.stringify(previousOrigins));
  };
  const refresh = async () => {
    const before = state;
    const response = await fetch(before.subscriptionUrl, {credentials:'omit', referrerPolicy:'no-referrer', signal:AbortSignal.timeout(15000)});
    if (!response.ok) throw new Error('配置订阅下载失败：HTTP ' + response.status);
    const text = await response.text();
    if (text.length > 16384) throw new Error('配置订阅过大');
    const config = JSON.parse(text);
    if (config.version !== 1) throw new Error('不支持此配置订阅版本');
    const next = validate(config.origin);
    if (state !== before) throw new Error('设置已变更，请重新刷新');
    commit({...state, subscribedOrigin:next});
  };
  const image = url => {
    let migrated = url;
    for (const old of [...state.previousOrigins, fallback, 'https://119.28.149.19:8443', 'https://43.131.255.74:8443']) {
      if (['books','komiic'].some(kind=>url.startsWith(old + '/api/' + kind + '/images/'))) { migrated = origin() + url.slice(old.length); break; }
    }
    return /^https:\/\/(?:lain\.bgm\.tv|static\.myani\.org)\//.test(migrated)
      ? base() + '/bangumi/image?url=' + encodeURIComponent(migrated) : migrated;
  };
  globalThis.aniApi = Object.freeze({
    get base(){return base();}, get animeko(){return base()+'/animeko';}, get bangumi(){return base()+'/bangumi';}, get auth(){return base()+'/auth/animeko';},
    page:url=>base()+'/online/page?url='+encodeURIComponent(url),
    torrent:url=>base()+'/torrent?url='+encodeURIComponent(url),
    feed:url=>base()+'/feeds?url='+encodeURIComponent(url), image,
  });
  globalThis.aniAnimekoBaseUrl = () => globalThis.aniApi.animeko;
  globalThis.aniRelayRead = () => JSON.stringify({...state, effectiveOrigin:origin()});
  globalThis.aniRelaySave = (manual, subscription) => {
    try { commit({...state, manualOrigin:manual.trim() ? validate(manual) : '', subscriptionUrl:subscriptionUrl(subscription)}); return ''; }
    catch (e) { return e.message; }
  };
  globalThis.aniRelayRefresh = callback => refresh().then(()=>callback(''), e=>callback(e.message));
  globalThis.aniWatchRelay = callback => { listener=callback; callback(origin(),JSON.stringify(state.previousOrigins)); };
  globalThis.aniRelayStart = () => {
    const update = () => { if (!state.manualOrigin) refresh().catch(()=>{}); };
    update(); setInterval(update, 3600000);
  };
})();
