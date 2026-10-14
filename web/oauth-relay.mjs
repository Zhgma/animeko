// SPDX-License-Identifier: AGPL-3.0-only
// Same endpoints as BangumiOAuthClient and UserAuthenticationAniApi; no OAuth client secret.
const servers = ['https://api.animeko.org', 'https://danmaku-global.myani.org', 'https://danmaku-cn.myani.org', 'https://s1.animeko.openani.org'];
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const platforms = new Set(['windows', 'macos', 'android', 'ios', 'linux', 'debian', 'ubuntu', 'redhat']);
const architectures = new Set(['aarch64', 'x86', 'x86_64']);

export function createOAuthRelay({ fetchImpl = fetch, now = Date.now, upstreams = servers } = {}) {
  const pending = new Map();
  const reply = (response, status, body) => {
    response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff' });
    response.end(JSON.stringify(body));
  };
  async function upstream(path, init = {}, preferred) {
    for (const base of preferred ? [preferred] : upstreams) {
      try {
        const result = await fetchImpl(base + path, { ...init, redirect: 'error', signal: AbortSignal.timeout(15000), headers: { Accept: 'application/json', 'User-Agent': 'AnimeComic-Web/preview', ...(init.headers || {}) } });
        if (init.method !== 'POST' && (result.status >= 500 || result.status === 403)) { await result.body?.cancel(); continue; }
        return { result, base };
      } catch { if (init.method === 'POST') break; /* Only read requests may fail over. */ }
    }
    throw new Error('授权服务暂时无法连接，请稍后重试。');
  }
  function loginResponse(data) {
    const t = data?.tokens;
    if (!t || !t.accessToken || !t.refreshToken || typeof t.accessToken !== 'string' || typeof t.refreshToken !== 'string' || !Number.isFinite(t.expiresAtMillis) || (t.bangumiAccessToken != null && typeof t.bangumiAccessToken !== 'string')) throw new Error('授权结果缺少 Animeko 会话，请重新登录。');
    return { tokens: { accessToken: t.accessToken, refreshToken: t.refreshToken, expiresAtMillis: t.expiresAtMillis, bangumiAccessToken: t.bangumiAccessToken || null }, user: { id: data.user?.id || '', bangumiUsername: data.user?.bangumiUsername || '', nickname: data.user?.nickname || '', email: data.user?.email || '', avatar: data.user?.mediumAvatar || data.user?.smallAvatar || '' } };
  }
  return async function handleOAuth(request, response, url) {
    if (!url.pathname.startsWith('/api/auth/bangumi/') && !url.pathname.startsWith('/api/auth/animeko/')) return false;
    try {
      const origin = request.headers.origin;
      if (request.headers['sec-fetch-site'] === 'cross-site' || (origin && new URL(origin).host !== request.headers.host)) { reply(response, 403, { error: '只允许本站发起授权请求。' }); return true; }
      const action = url.pathname.split('/').at(-1);
      if (action === 'providers') {
        if (request.method !== 'GET') { reply(response, 405, { error: '请求方式无效。' }); return true; }
        const { result } = await upstream('/v2/users/oauth/providers');
        if (!result.ok) { reply(response, result.status, { error: '无法读取登录方式。' }); return true; }
        const data = await result.json();
        reply(response, 200, { providers: (data.providers || []).filter(p => ['bangumi', 'github'].includes(p)) }); return true;
      }
      if (['email-otp', 'email-login'].includes(action)) {
        if (request.method !== 'POST' || !request.headers['content-type']?.startsWith('application/json')) { reply(response, 405, { error: '请求方式无效。' }); return true; }
        let raw = '';
        for await (const chunk of request) { raw += chunk; if (raw.length > 4096) { reply(response, 413, { error: '请求过大。' }); return true; } }
        const data = JSON.parse(raw);
        const send = action === 'email-otp';
        if (send ? typeof data.email !== 'string' || data.email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email) : typeof data.otpId !== 'string' || data.otpId.length > 256 || !data.otpId || !/^\d{6}$/.test(data.otpValue)) {
          reply(response, 400, { error: send ? '邮箱格式不正确。' : '请输入六位验证码。' }); return true;
        }
        const body = send ? { email: data.email } : { otpId: data.otpId, otpValue: data.otpValue };
        const { result } = await upstream('/v2/users/auth/email' + (send ? '/otp' : ''), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        if (!result.ok) { reply(response, result.status, { error: result.status === 429 ? '请求过于频繁，请稍后重试。' : send ? '无法发送验证码，请检查邮箱后重试。' : '验证码无效或已过期，请重试。' }); return true; }
        const payload = await result.json();
        if (send) {
          if (typeof payload.otpId !== 'string' || !payload.otpId) throw new Error('验证码请求结果无效。');
          reply(response, 200, { otpId: payload.otpId, hasExistingUser: payload.hasExistingUser === true });
        } else reply(response, 200, loginResponse(payload));
        return true;
      }
      if (action === 'refresh') {
        if (request.method !== 'POST' || !request.headers['content-type']?.startsWith('application/json')) { reply(response, 405, { error: '请求方式无效。' }); return true; }
        let body = '';
        for await (const chunk of request) { body += chunk; if (body.length > 16384) { reply(response, 413, { error: '请求过大。' }); return true; } }
        const token = JSON.parse(body).refreshToken;
        if (typeof token !== 'string' || !token || token.length > 8192) { reply(response, 400, { error: '刷新会话无效。' }); return true; }
        const { result } = await upstream('/v2/users/auth/refresh', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: token }) });
        if (!result.ok) { reply(response, result.status, { error: '会话已过期，请重新登录。' }); return true; }
        reply(response, 200, loginResponse(await result.json())); return true;
      }
      if (!['start', 'result'].includes(action)) { reply(response, 404, { error: '授权接口不存在。' }); return true; }
      if (request.method !== 'GET') { reply(response, 405, { error: '请求方式无效。' }); return true; }
      const id = url.searchParams.get('requestId') || '';
      if (!uuid.test(id)) { reply(response, 400, { error: '授权请求标识无效。' }); return true; }
      for (const [key, value] of pending) if (value.expires <= now()) pending.delete(key);
      if (action === 'start') {
        const provider = url.searchParams.get('provider') || 'bangumi';
        const bind = url.searchParams.get('mode') === 'bind';
        if (!['bangumi', 'github'].includes(provider)) { reply(response, 400, { error: '登录方式无效。' }); return true; }
        const authorization = request.headers.authorization;
        if (bind && (!authorization || !/^Bearer [\w.~-]+$/.test(authorization) || authorization.length > 8192)) { reply(response, 401, { error: '请先登录 Animeko。' }); return true; }
        const os = url.searchParams.get('os'), arch = url.searchParams.get('arch');
        if (!platforms.has(os) || !architectures.has(arch)) { reply(response, 400, { error: '客户端系统信息无效。' }); return true; }
        if (pending.size >= 256) { reply(response, 429, { error: '授权请求过多，请稍后重试。' }); return true; }
        const path = provider === 'bangumi' ? '/v2/users/bangumi/' + (bind ? 'bind' : 'oauth') : '/v2/users/oauth/github/' + (bind ? 'bind' : 'login');
        const { result, base } = await upstream(path + '?' + new URLSearchParams({ requestId: id, os, arch }), bind ? { headers: { Authorization: authorization } } : {});
        if (!result.ok) { reply(response, result.status, { error: '无法开始 Bangumi 授权，请重试。' }); return true; }
        const data = await result.json(), target = new URL(data.url);
        if (target.origin !== (provider === 'bangumi' ? 'https://bgm.tv' : 'https://github.com') || target.pathname !== (provider === 'bangumi' ? '/oauth/authorize' : '/login/oauth/authorize') || target.username || target.password) throw new Error('授权地址无效。');
        pending.set(id, { base, provider, expires: now() + 10 * 60 * 1000 });
        reply(response, 200, { url: target.href }); return true;
      }
      const attempt = pending.get(id);
      if (!attempt) { reply(response, 410, { error: '授权请求已过期，请重新登录。' }); return true; }
      const { result } = await upstream((attempt.provider === 'github' ? '/v2/users/oauth/result?' : '/v2/users/bangumi/result?') + new URLSearchParams({ requestId: id }), {}, attempt.base);
      if (result.status === 425) { reply(response, 425, { pending: true }); return true; }
      if (!result.ok) { pending.delete(id); reply(response, result.status, { error: result.status === 409 ? '账号绑定冲突，请在 Animeko 中检查账号关联。' : '授权失败，请重新登录。' }); return true; }
      const data = loginResponse(await result.json());
      pending.delete(id); reply(response, 200, data);
    } catch (error) {
      reply(response, 502, { error: error instanceof SyntaxError ? '授权服务返回了无效数据。' : error.message || '授权服务暂时不可用。' });
    }
    return true;
  };
}
