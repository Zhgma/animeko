// SPDX-License-Identifier: AGPL-3.0-only
// Browser transport for shared Kotlin policies, local storage and media adapters.
(() => {
  'use strict';
  const API = window.aniApi.bangumi;
  window.aniAnimekoToken = callback => window.aniAuthSession().then(session => {
    const token = session?.tokens.accessToken;
    callback(token || '', '');
  }).catch(() => callback('', 'Animeko 会话不可用，请重新登录。'));
  const prefix = 'animecomic.web.';
  let mangaRoute, bookSearch, bookPageSize;
  const subjectTypes = new Map(), pendingDetails = new Map();
  window.aniInstallMangaApi = (route, search, pageSize) => { mangaRoute = route; bookSearch = search; bookPageSize = pageSize; };
  const isBook = p => p.type === 1 || subjectTypes.get(p.id) === 1;
  let currentDark = false;
  let routeListener = null;
  const validRoute = route => {
    if (!/^(collection|anime|explore|schedule|trending|search|account|history|downloads|settings(?:\/[a-z]+)?|subject\/[1-9]\d*)$/.test(route)) return 'collection';
    if (route.startsWith('subject/') && Number(route.slice(8)) > 2147483647) return 'collection';
    return route;
  };
  window.aniCurrentRoute = () => { if (location.hash.length > 1) return validRoute(location.hash.slice(1)); let initial; try { initial=JSON.parse(aniRead('uiSettings') || '{}').mainSceneInitialPage; } catch {} return ({Exploration:'explore',Collection:'collection',CacheManagement:'downloads'})[initial] || 'explore'; };
  window.aniNavigate = route => { const next = validRoute(route); if (location.hash !== '#' + next) history.pushState(null, '', '#' + next); };
  window.aniWatchRoute = callback => { window.aniUnwatchRoute(); routeListener = () => callback(window.aniCurrentRoute()); window.addEventListener('popstate', routeListener); window.addEventListener('hashchange', routeListener); };
  window.aniUnwatchRoute = () => { if (routeListener) { window.removeEventListener('popstate', routeListener); window.removeEventListener('hashchange', routeListener); } routeListener = null; };
  window.aniRead = key => { try { return localStorage.getItem(prefix + key) || ''; } catch { return ''; } };
  window.aniWrite = (key, value) => { try { localStorage.setItem(prefix + key, value); return true; } catch { return false; } };
  window.aniDark = () => currentDark;
  window.aniOpen = url => { if (url === './source.zip') { window.open(new URL(url, location.href), '_blank', 'noopener,noreferrer'); return; } if (/^https:\/\/(bgm\.tv|next\.bgm\.tv)\//.test(url)) window.open(url, '_blank', 'noopener,noreferrer'); };
  window.aniTheme = dark => { currentDark = dark; document.documentElement.dataset.theme = dark ? 'dark' : 'light'; document.querySelector('meta[name="theme-color"]').content = dark ? '#211e25' : '#f2ecf4'; };
  window.aniReady = () => document.getElementById('boot')?.remove();
  window.aniLogout = () => window.aniAuthClear();
  const text = value => typeof value === 'string' ? value : '';
  const normalize = s => { if (s.type) subjectTypes.set(s.id, s.type); return ({ id: s.id, type: s.type || 2, title: s.name_cn || s.name || '未命名作品', original: s.name || '',
    cover: s.images?.large || s.images?.common || s.images?.medium || '', score: String(s.rating?.score || s.score || ''), rank: s.rating?.rank || s.rank || 0,
    date: s.date || s.air_date || '', summary: s.summary || '', count: s.total_episodes || s.eps || 0, tags: (s.tags || []).map(t => t.name).filter(Boolean),
    platform: s.platform || '', votes: s.rating?.total || 0, volumes: s.volumes || 0,
    info: (s.infobox || []).map(row => ({label: row.key, value: typeof row.value === 'string' ? row.value : Array.isArray(row.value) ? row.value.map(v => v.v).join(' / ') : String(row.value ?? '')})),
    collection: s.collection || {}, histogram: s.rating?.count || {}, relation: s.relation || '',
    progress: s.progress || 0, volumeProgress: s.volumeProgress || 0, nsfw: Boolean(s.nsfw) }); };
  async function fetchJson(path, options = {}, token = '', manga = false) {
    let target = API + path, timeout = 20000;
    if (manga) {
      if (!mangaRoute) throw new Error('共享漫画接口尚未初始化');
      const [pathname, query] = path.split('?');
      const original = (pathname.startsWith('/p1/') ? 'https://next.bgm.tv' : 'https://api.bgm.tv') + pathname;
      const route = JSON.parse(mangaRoute(options.method || 'GET', original, options.body || ''));
      // The App's book API routing and credential policy also apply in a static browser.
      if (route.url !== original) target = route.url + (query ? '?' + query : '');
      if (!route.sendAuthorization) token = '';
      timeout = route.timeoutMillis;
    }
    const response = await fetch(target, { ...options, mode: target.startsWith('/') ? 'same-origin' : 'cors', credentials: 'omit', cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(timeout),
      headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: 'Bearer ' + token } : {}) } });
    if (!response.ok) {
      const failure = await response.json().catch(() => ({}));
      const error = new Error(failure.error || (response.status === 401 ? '令牌无效或已过期，请重新连接。' : response.status === 429 ? '请求过于频繁，请稍后重试。' : '接口返回 HTTP ' + response.status));
      error.status = response.status; throw error;
    }
    if (response.status === 202 || response.status === 204) return {};
    return response.json();
  }
  async function detail(id) {
    if (!pendingDetails.has(id)) {
      const task = (async () => {
        let data;
        if (subjectTypes.get(id) === 2) data = await fetchJson('/v0/subjects/' + id);
        else {
          try { data = await fetchJson('/v0/subjects/' + id, {}, '', true); }
          catch (error) {
            if (error.status !== 404 || subjectTypes.get(id) === 1) throw error;
            data = await fetchJson('/v0/subjects/' + id);
          }
        }
        subjectTypes.set(id, data.type);
        return data;
      })();
      pendingDetails.set(id, task);
      task.finally(() => pendingDetails.delete(id)).catch(() => {});
    }
    return pendingDetails.get(id);
  }
  window.aniMangaIdentity = token => fetchJson('/v0/me', {}, token, true);
  async function api(action, p) {
    if (action.startsWith('config')) return (await import('./config-io.mjs')).configAction(action, p, localStorage, prefix,
      async settings => (await import('./browser-media.mjs')).mediaAction('mediaSettings', {settings}));
    if (/^(media|mangaSubscription|torrent|subscription)/.test(action)) return {raw:JSON.stringify(await (await import('./browser-media.mjs')).mediaAction(action,p))};
    if (action === 'networkCheck') {
      await fetchJson('/v0/subjects/355555', {}, '', true);
      return {raw:'App / 网页共享书籍 API 连接正常。此检查不代表浏览器授权页面的网络路径正常。'};
    }
    if (action === 'clearCache') {
      Object.keys(localStorage).filter(k => k.startsWith(prefix + 'cache.')).forEach(k => localStorage.removeItem(k));
      return {};
    }
    const type = p.type === 1 ? 1 : 2;
    const offset = Number.isSafeInteger(p.offset) && p.offset >= 0 ? p.offset : 0;
    if (action === 'sample') {
      const response = await fetch(`./samples/${type}.json`, { credentials: 'omit' });
      if (!response.ok) throw new Error('示例数据无法加载');
      return response.json();
    }
    if (action === 'browse') {
      const data = await fetchJson(`/v0/subjects?type=${type}&sort=${p.sort === 'date' ? 'date' : 'rank'}&limit=24&offset=${offset}${type === 1 ? '&cat=1001&series=true' : ''}`);
      return { items: data.data.map(normalize), total: data.total || 0 };
    }
    if (action === 'search') {
      if (!text(p.keyword).trim()) return { items: [] };
      if (type === 1 && !bookSearch) throw new Error('共享书籍搜索尚未初始化');
      const body = type === 1 ? bookSearch(p.keyword, p.sort || 'match') : JSON.stringify({ keyword: p.keyword, sort: ['match','rank','date','collect'].includes(p.sort) ? p.sort : 'match', filter: { type: [type] } });
      const data = await fetchJson(`/v0/search/subjects?limit=${type === 1 ? bookPageSize : 24}&offset=${offset}`, { method: 'POST', body }, '', type === 1);
      return { items: data.data.map(normalize), total: data.total || 0 };
    }
    if (action === 'detail') {
      if (!Number.isSafeInteger(p.id) || p.id < 1) throw new Error('作品编号无效');
      return { subject: normalize(await detail(p.id)) };
    }
    if (action === 'related') {
      if (!Number.isSafeInteger(p.id) || p.id < 1) throw new Error('作品编号无效');
      if (!subjectTypes.has(p.id)) await detail(p.id);
      return {items: (await fetchJson(`/v0/subjects/${p.id}/subjects`, {}, '', isBook(p))).map(normalize)};
    }
    if (action === 'episodes') {
      if (!Number.isSafeInteger(p.id) || p.id < 1) throw new Error('作品编号无效');
      return {raw: JSON.stringify(await fetchJson(`/v0/episodes?subject_id=${p.id}&limit=100&offset=${offset}`))};
    }
    if (action === 'comments') {
      if (!Number.isSafeInteger(p.id) || p.id < 1) throw new Error('作品编号无效');
      return {raw:JSON.stringify(await fetchJson(`/p1/subjects/${p.id}/comments?limit=20&offset=${offset}`, {}, '', isBook(p)))};
    }
    if (action === 'libraryRead' || action === 'libraryWrite') {
      if (!isBook(p)) throw new Error('番剧收藏由共享 Animeko 客户端处理');
      if (!Number.isSafeInteger(p.id) || p.id < 1) throw new Error('作品编号无效');
      const session = await window.aniAuthSession();
      const token = session?.tokens.bangumiAccessToken;
      if (!token) throw new Error('请先连接 Bangumi 账号');
      if (action === 'libraryRead') {
        const identity = await window.aniMangaIdentity(token);
        if (!identity.username) throw new Error('Bangumi 用户信息为空，请重新关联账号');
        try { return { raw: JSON.stringify(await fetchJson(`/v0/users/${encodeURIComponent(identity.username)}/collections/${p.id}`, {}, token, isBook(p))) }; }
        catch (error) { if (error.status === 404) return {raw:'null'}; throw error; }
      }
      const fields = p.fields;
      if (!fields || typeof fields !== 'object' || Array.isArray(fields)) throw new Error('收藏参数无效');
      await fetchJson(`/v0/users/-/collections/${p.id}`, {method: p.create ? 'POST' : 'PATCH', body: JSON.stringify(fields)}, token, isBook(p));
      return {};
    }
    if (action === 'oauthStart' || action === 'oauthResult') return window.aniOAuthRequest(action);
    if (['providers', 'emailOtp', 'emailLogin'].includes(action)) return window.aniAccountRequest(action, p);
    if (action === 'session') return window.aniAuthIdentity(await window.aniAuthSession());
    if (action === 'mangaRaw') {
      const token = (await window.aniAuthSession())?.tokens.bangumiAccessToken;
      if (!token) throw new Error('请先连接 Bangumi 账号');
      if (!/^(me|subjects\/\d+|users\/[\w.-]+\/collections)$/.test(p.path)) throw new Error('请求路径无效');
      const query = p.status == null ? '' : `?subject_type=1&type=${Number(p.status)}&offset=${Number(p.offset) || 0}&limit=30`;
      return { raw: JSON.stringify(await fetchJson('/v0/' + p.path + query, {}, token, true)) };
    }
    if (action === 'collection') {
      if (type !== 1) throw new Error('追番列表由共享 Animeko 客户端处理');
      const token = (await window.aniAuthSession())?.tokens.bangumiAccessToken;
      if (!token) throw new Error('请先连接 Bangumi 账号');
      const data = await fetchJson(`/v0/users/${encodeURIComponent(p.username)}/collections?subject_type=${type}&type=${Number(p.status) || 3}&limit=30&offset=${offset}`, {}, token, type === 1);
      return { items: data.data.map(entry => normalize({ ...entry.subject, id:entry.subject_id || entry.subject.id, type, progress:entry.ep_status, volumeProgress:entry.vol_status })), total: data.total || 0 };
    }
    throw new Error('不支持的操作');
  }
  window.aniRequest = (action, payload, callback) => {
    let p; try { p = JSON.parse(payload); } catch { callback(JSON.stringify({ ok: false, error: '请求参数无效' })); return; }
    const cacheable = ['browse', 'search', 'detail'].includes(action);
    const key = 'cache.' + action + '.' + JSON.stringify(p);
    api(action, p).then(result => {
      if (cacheable) { aniWrite(key, JSON.stringify({ at: Date.now(), result })); }
      callback(JSON.stringify({ ok: true, ...result }));
    }).catch(error => {
      if (cacheable && (!error.status || error.status === 429 || error.status >= 500)) {
        try { const old = JSON.parse(aniRead(key)); if (Date.now() - old.at < 7 * 86400000) { callback(JSON.stringify({ ok: true, ...old.result, cached: true })); return; } } catch {}
      }
      callback(JSON.stringify({ ok: false, status: error.status || 0, error: error.name === 'TimeoutError' ? '连接超时，请检查网络后重试。' : error instanceof TypeError ? '无法连接数据接口，请检查网络、证书或跨域设置。' : error.message }));
    });
  };

})();
