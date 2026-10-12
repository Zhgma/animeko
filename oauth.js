// SPDX-License-Identifier: AGPL-3.0-only
// Browser window/session adapter for Animeko's Bangumi OAuth protocol.
(() => {
  const key = 'animecomic.web.session';
  let attempt = null, epoch = 0, refreshing = null, emailGeneration = 0;
  const read = () => { try { return JSON.parse(sessionStorage.getItem(key) || 'null'); } catch { return null; } };
  function save(data) {
    const tokens = data?.tokens;
    if (!tokens || typeof tokens.refreshToken !== 'string' || !tokens.refreshToken || typeof tokens.accessToken !== 'string' || !tokens.accessToken || !Number.isFinite(tokens.expiresAtMillis) || (tokens.bangumiAccessToken != null && typeof tokens.bangumiAccessToken !== 'string')) throw new Error('授权结果无效，请重新登录。');
    try { sessionStorage.setItem(key, JSON.stringify(data)); } catch { throw new Error('浏览器禁用了会话存储，无法保存登录状态。'); }
  }
  async function api(action, query = {}, body, authorization = '') {
    if (!['providers','email-otp','email-login','refresh','start','result'].includes(action)) throw new Error('不支持的账号操作');
    const params = new URLSearchParams(query);
    let response;
    try { response = await fetch(window.aniApi.auth + '/' + action + (params.size ? '?' + params : ''), {
      method: body ? 'POST' : 'GET', mode:'cors', credentials: 'omit', cache: 'no-store', redirect:'error', signal: AbortSignal.timeout(25000),
      headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}), ...(authorization ? { Authorization: 'Bearer ' + authorization } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    }); } catch (error) {
      if (error instanceof TypeError || error?.name === 'TypeError') throw new Error('无法连接远程账号 API，请检查网络或稍后重试。');
      throw error;
    }
    if (response.status === 425) return { pending: true };
    let data;
    try { data = await response.json(); } catch { throw new Error('Animeko API 返回 HTTP ' + response.status + '，未返回有效 JSON；请检查服务端跨域策略和接口状态。'); }
    if (!response.ok) throw new Error(data.error || 'Animeko API 返回 HTTP ' + response.status);
    if (data.tokens && data.user) data.user = {...data.user, avatar:data.user.mediumAvatar || data.user.smallAvatar || data.user.avatar || ''};
    return data;
  }
  function platform() {
    const ua = navigator.userAgent;
    const os = /Android/i.test(ua) ? 'android' : /iPhone|iPad|iPod/i.test(ua) || (/Macintosh/i.test(ua) && navigator.maxTouchPoints > 1) ? 'ios' : /Windows/i.test(ua) ? 'windows' : /Mac/i.test(ua) ? 'macos' : 'linux';
    return { os, arch: /arm|aarch64/i.test(ua) || ['android', 'ios'].includes(os) ? 'aarch64' : 'x86_64' };
  }
  window.aniOAuthCancel = () => {
    const old = attempt; attempt = null;
    try { old?.popup?.close(); } catch { /* The authorization tab may already be closed. */ }
  };
  window.aniOAuthPrepare = (provider = 'bangumi', bind = false) => {
    if (!['bangumi', 'github'].includes(provider)) return false;
    window.aniOAuthCancel();
    const popup = window.open('about:blank', '_blank');
    if (!popup) return false;
    popup.opener = null;
    popup.document.title = '正在连接 Animeko';
    popup.document.body.textContent = '正在连接 Animeko 授权服务…';
    attempt = { id: crypto.randomUUID(), popup, epoch, url: null, provider, bind };
    return true;
  };
  window.aniOAuthReopen = () => {
    if (!attempt?.url) return false;
    if (!attempt.popup.closed) { attempt.popup.focus(); return true; }
    const popup = window.open(attempt.url, '_blank');
    if (!popup) return false;
    popup.opener = null; attempt.popup = popup; return true;
  };
  window.aniOAuthRequest = async action => {
    const active = attempt;
    if (!active) throw new Error('请重新发起 Bangumi 授权。');
    const token = action === 'oauthStart' && active.bind ? (await window.aniAuthSession())?.tokens.accessToken : '';
    if (active.bind && action === 'oauthStart' && !token) throw new Error('请先登录 Animeko。');
    const data = await api(action === 'oauthStart' ? 'start' : 'result', { requestId: active.id, provider:active.provider, ...(action === 'oauthStart' ? { ...platform(), mode: active.bind ? 'bind' : 'login' } : {}) }, undefined, token);
    if (attempt !== active || epoch !== active.epoch) throw new Error('授权已取消。');
    if (action === 'oauthStart') {
      const url = new URL(data.url);
      if (url.origin !== (active.provider === 'bangumi' ? 'https://bgm.tv' : 'https://github.com') || url.pathname !== (active.provider === 'bangumi' ? '/oauth/authorize' : '/login/oauth/authorize') || url.username || url.password) throw new Error('授权地址无效。');
      active.url = url.href; active.popup.location.replace(url.href);
      return { pending: true };
    }
    if (data.pending) {
      if (active.popup.closed) throw new Error('授权窗口已关闭，请重新登录。');
      return data;
    }
    save(data);
    window.aniOAuthCancel();
    return window.aniAuthIdentity(data);
  };
  window.aniAuthIdentity = async data => {
    if (!data) return {};
    let username = data.user?.bangumiUsername || '', nickname = data.user?.nickname || '';
    let avatar = data.user?.avatar || '';
    let bangumiError = '';
    if (!username && data.tokens.bangumiAccessToken) {
      try {
        const user = await window.aniMangaIdentity(data.tokens.bangumiAccessToken);
        username = user.username || '';
        // The account being displayed is Animeko; Bangumi is only a linked identity.
      } catch (error) {
        bangumiError = error.message || 'Bangumi 账号资料暂时不可用';
      }
    }
    return { username, nickname: nickname || 'Animeko', avatar, userId: String(data.user?.id || ''),
      email: data.user?.email || '', signedIn: true, hasBangumi: !!data.tokens.bangumiAccessToken, bangumiError };

  };
  window.aniAccountCancel = () => { emailGeneration++; window.aniOAuthCancel(); };
  window.aniAccountRequest = async (action, payload) => {
    const generation = emailGeneration, sessionEpoch = epoch;
    if (action === 'providers') return api('providers');
    const data = await api(action === 'emailOtp' ? 'email-otp' : 'email-login', {}, payload);
    if (emailGeneration !== generation || epoch !== sessionEpoch) throw new Error('登录已取消。');
    if (action === 'emailOtp') return data;
    save(data); return window.aniAuthIdentity(data);
  };
  window.aniAuthSession = async () => {
    const session = read();
    if (!session) return null;
    if (session.tokens.expiresAtMillis > Date.now() + 30000) return session;
    if (!refreshing) {
      const generation = epoch;
      const task = api('refresh', {}, { refreshToken: session.tokens.refreshToken }).then(data => {
        if (epoch !== generation) throw new Error('登录状态已改变。');
        save(data); return data;
      });
      refreshing = task;
      task.finally(() => { if (refreshing === task) refreshing = null; }).catch(() => {});
    }
    return refreshing;
  };
  window.aniAuthClear = () => {
    epoch++; refreshing = null; window.aniOAuthCancel();
    try { sessionStorage.removeItem(key); sessionStorage.removeItem('animecomic.web.token'); } catch { /* No writable storage means there is no persisted browser session. */ }
  };
})();
