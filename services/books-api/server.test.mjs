// SPDX-License-Identifier: AGPL-3.0-only
import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createBooksServer } from './server.mjs';

async function fixture(t, options = {}) {
  const calls = [];
  const server = createBooksServer({ upstream: async (path, options) => {
    calls.push({ path, ...options });
    return { status: 200, data: path.includes('search')
      ? { total: 1, limit: 20, offset: 0, data: [{ id: 118165, type: 1, name_cn: '碧蓝之海' }] }
      : { id: 118165, type: 1, name_cn: '碧蓝之海' } };
  }, ...options });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const get = (path, init) => fetch(origin + '/api/books' + path, init);
  const search = (body, query = '', headers = {}) => get('/v0/search/subjects' + query, {
    method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body),
  });
  return { get, search, calls, origin };
}

test('search pins type 1, preserves pagination and caches normalized requests', async t => {
  const f = await fixture(t);
  const first = await f.search({ keyword: ' 碧蓝之海 ', sort: 'rank' }, '?limit=10&offset=20');
  assert.equal(first.status, 200);
  assert.equal((await first.json()).data[0].type, 1);
  assert.deepEqual(f.calls[0], { path: '/v0/search/subjects?limit=10&offset=20', method: 'POST',
    body: { keyword: '碧蓝之海', sort: 'rank', filter: { type: [1] } } });
  const second = await f.search({ keyword: '碧蓝之海', sort: 'rank', filter: { type: [1] } }, '?offset=20&limit=10');
  assert.equal(second.headers.get('x-cache'), 'HIT'); assert.equal(f.calls.length, 1);
});

test('all book platforms are supported; non-books are denied without metadata leakage and cached', async t => {
  let calls = 0;
  const f = await fixture(t, { upstream: async path => {
    calls++; const id = Number(path.split('/').pop());
    return { status: 200, data: { id, type: id === 2 ? 2 : 1, platform: id === 3 ? '小说' : '漫画', name: 'private-title' } };
  } });
  for (const id of [1, 3]) assert.equal((await f.get('/v0/subjects/' + id)).status, 200);
  for (let i = 0; i < 2; i++) {
    const r = await f.get('/v0/subjects/2'); assert.equal(r.status, 404);
    assert.deepEqual(await r.json(), { error: 'book_not_found' });
  }
  assert.equal(calls, 3);
});

test('public routes keep strict verbs and bounded non-duplicate query parameters', async t => {
  const f = await fixture(t, { ipBurst: 100 });
  for (const path of ['/v0/episodes', '/v0/subjects', '/v0/subjects/1/subjects',
    '/image?url=http://localhost/', '/v0/subjects/0', '/v0/subjects/-1', '/v0/subjects/1/', '/v0/subjects/1?url=http://localhost']) {
    assert.ok([400, 404].includes((await f.get(path)).status), path);
  }
  assert.equal((await f.get('/v0/subjects/1', { method: 'PATCH' })).status, 405);
  assert.equal((await f.get('/v0/search/subjects')).status, 405);
  for (const query of ['?limit=21', '?offset=1001', '?offset=-1', '?limit=1&limit=2', '?type=2', '?offset=1e2']) {
    assert.equal((await f.search({ keyword: 'book' }, query)).status, 400, query);
  }
  assert.equal(f.calls.length, 0);
});

test('search rejects non-books, unsupported filters, empty keywords, oversized bodies and credentials', async t => {
  const f = await fixture(t);
  for (const body of [{ keyword: '' }, { keyword: 'x', filter: { type: [2] } }, { keyword: 'x', filter: { type: [1, 2] } },
    { keyword: 'x', url: 'http://127.0.0.1' }, { keyword: 'x', filter: { tag: ['anime'] } }, { keyword: 'x', filter: { nsfw: 'true' } }]) {
    assert.equal((await f.search(body)).status, 400);
  }
  assert.equal((await f.search({ keyword: 'x'.repeat(5000) })).status, 413);
  assert.equal((await f.search({ keyword: 'x' }, '', { Authorization: 'Bearer secret' })).status, 400);
  assert.equal((await f.get('/v0/subjects/1', { headers: { Cookie: 'secret=1' } })).status, 400);
  assert.equal((await f.search({ keyword: 'x' }, '', { 'Content-Type': 'text/plain' })).status, 415);
  assert.equal(f.calls.length, 0);
});

test('mixed upstream search results never expose anime', async t => {
  const f = await fixture(t, { upstream: async () => ({ status: 200, data: { data: [{ type: 1 }, { type: 2, name: 'anime' }] } }) });
  const r = await f.search({ keyword: 'x' }); assert.equal(r.status, 502);
  assert.deepEqual(await r.json(), { error: 'invalid_upstream_response' });
});

test('IP rate limit ignores spoofed forwarding headers and recovers with time', async t => {
  let time = 0;
  const f = await fixture(t, { now: () => time, ipBurst: 2 });
  for (let i = 0; i < 2; i++) assert.equal((await f.get('/health', { headers: { 'X-Real-IP': '1.2.3.' + i } })).status, 200);
  const blocked = await f.get('/health', { headers: { 'X-Forwarded-For': '8.8.8.8', 'X-Real-IP': '8.8.8.8' } });
  assert.equal(blocked.status, 429); assert.equal(blocked.headers.get('retry-after'), '15');
  time = 60000; assert.equal((await f.get('/health')).status, 200);
});

test('trusted loopback proxy uses per-IP budgets and a global budget', async t => {
  const f = await fixture(t, { trustLoopbackProxy: true, ipBurst: 1, globalBurst: 2 });
  const call = ip => f.get('/health', { headers: { 'X-Real-IP': ip } });
  assert.equal((await call('1.2.3.4')).status, 200);
  assert.equal((await call('1.2.3.5')).status, 200);
  assert.equal((await call('1.2.3.6')).status, 429);
});

test('concurrent duplicate reads share upstream work and busy clients are bounded', async t => {
  let release, started; const begun = new Promise(resolve => { started = resolve; }); let calls = 0;
  const f = await fixture(t, { maxPerIpConcurrent: 2, upstream: async () => { calls++; started(); await new Promise(resolve => { release = resolve; });
    return { status: 200, data: { id: 118165, type: 1 } }; } });
  const a = f.get('/v0/subjects/118165'); await begun;
  const b = f.get('/v0/subjects/118165');
  // Wait for the second request to occupy its slot without releasing the upstream.
  await new Promise(resolve => setTimeout(resolve, 30));
  assert.equal((await f.get('/v0/subjects/118166')).status, 429);
  release(); assert.equal((await a).status, 200); assert.equal((await b).status, 200); assert.equal(calls, 1);
});

test('upstream 429 applies a cooldown, failures do not enter the success cache', async t => {
  let time = 0, calls = 0;
  const f = await fixture(t, { now: () => time, upstream: async () => {
    calls++; return calls === 1 ? { status: 429 } : { status: 200, data: { id: 118165, type: 1 } };
  } });
  assert.equal((await f.get('/v0/subjects/118165')).status, 503);
  assert.equal((await f.get('/v0/subjects/118165')).status, 503); assert.equal(calls, 1);
  time = 16000; assert.equal((await f.get('/v0/subjects/118165')).status, 200); assert.equal(calls, 2);
});

test('CORS preflight is public and never enables credential transport', async t => {
  const f = await fixture(t);
  const r = await f.get('/v0/search/subjects', { method: 'OPTIONS' });
  assert.equal(r.status, 204); assert.equal(r.headers.get('access-control-allow-origin'), '*');
  assert.equal(r.headers.get('access-control-allow-credentials'), null); assert.equal(f.calls.length, 0);
});

const authHeaders = { Authorization: 'Bearer alice-token', 'Content-Type': 'application/json' };
const ownBook = '/v0/users/-/collections/118165';
function accountUpstream(calls, { nonBook = false, writeStatus = 204 } = {}) {
  const entries = new Map();
  return async (path, options) => {
    calls.push({ path, ...options });
    const username = options.authorization === 'Bearer bob-token' ? 'bob' : 'alice';
    if (!['Bearer alice-token', 'Bearer bob-token'].includes(options.authorization)) return { status: 401 };
    if (path === '/v0/me') return { status: 200, data: { id: username === 'alice' ? 1 : 2, username, nickname: 'private-nickname' } };
    if (path === '/v0/subjects/118165') return { status: 200, data: { id: 118165, type: nonBook ? 2 : 1 } };
    const entry = entries.get(username) ?? { subject_id: 118165, subject_type: 1, type: 3,
      ep_status: username === 'alice' ? 12 : 99, vol_status: 2, private: true, comment: username + '-comment', rate: 8, tags: ['tag'] };
    if (options.method !== 'GET') {
      if ([202, 204].includes(writeStatus)) entries.set(username, { ...entry, ...options.body });
      return { status: writeStatus };
    }
    if (path.includes('?')) return { status: 200, data: { total: 1, limit: 30, offset: 0, data: [entry] } };
    return { status: 200, data: entry };
  };
}

test('account routes require bearer credentials, prohibit cookies and do not expose other users', async t => {
  const calls = []; const f = await fixture(t, { upstream: accountUpstream(calls) });
  for (const path of ['/v0/me', ownBook, '/v0/users/alice/collections']) {
    assert.equal((await f.get(path)).status, 401);
    assert.equal((await f.get(path, { headers: { Authorization: 'Basic abc' } })).status, 401);
  }
  assert.equal(calls.length, 0);
  assert.equal((await f.get('/v0/me', { headers: { ...authHeaders, Cookie: 'login=secret' } })).status, 400);
  assert.equal((await f.get('/v0/users/bob/collections', { headers: authHeaders })).status, 403);
  assert.deepEqual(calls.map(x => x.path), ['/v0/me']);
  const me = await f.get('/v0/me', { headers: authHeaders });
  assert.deepEqual(await me.json(), { id: 1, username: 'alice' });
  assert.equal(me.headers.get('cache-control'), 'no-store');
});

test('all five states and reading progress preserve rating, tags, privacy and other fields', async t => {
  const calls = []; const f = await fixture(t, { upstream: accountUpstream(calls) });
  for (const type of [1, 3, 2, 4, 5]) {
    const r = await f.get(ownBook, { method: 'POST', headers: authHeaders, body: JSON.stringify({ type }) });
    assert.equal(r.status, 204); assert.equal(await r.text(), '');
    const updated = await (await f.get(ownBook, { headers: authHeaders })).json();
    assert.equal(updated.type, type); assert.equal(updated.ep_status, 12); assert.equal(updated.private, true);
  }
  assert.equal((await f.get(ownBook, { method: 'PATCH', headers: authHeaders, body: '{"ep_status":24,"vol_status":3}' })).status, 204);
  const final = await (await f.get(ownBook, { headers: authHeaders })).json();
  assert.equal(final.ep_status, 24); assert.equal(final.vol_status, 3); assert.equal(final.rate, 8);
  assert.equal(final.comment, 'alice-comment'); assert.deepEqual(final.tags, ['tag']); assert.equal(final.type, 5);
  const writes = calls.filter(x => x.method !== 'GET');
  assert.ok(writes.every(x => x.path === '/v0/users/-/collections/118165'));
  assert.deepEqual(writes.at(-1).body, { ep_status: 24, vol_status: 3 });
  assert.equal(writes.length, 6);
});

test('authentication and book type are checked before every mutation', async t => {
  const calls = []; const f = await fixture(t, { upstream: accountUpstream(calls, { nonBook: true }) });
  assert.equal((await f.get(ownBook, { method: 'POST', headers: authHeaders, body: '{"type":3}' })).status, 404);
  assert.equal((await f.get(ownBook, { method: 'PATCH', headers: authHeaders, body: '{"ep_status":1}' })).status, 404);
  assert.equal(calls.filter(x => x.method !== 'GET').length, 0);
  const denied = await f.get(ownBook, { method: 'PATCH', headers: { ...authHeaders, Authorization: 'Bearer expired-token' }, body: '{"ep_status":1}' });
  assert.equal(denied.status, 401);
  assert.equal(calls.at(-1).path, '/v0/me');
});

test('list is pinned to books and private responses are never cached or shared across tokens', async t => {
  const calls = []; const f = await fixture(t, { upstream: accountUpstream(calls) });
  const a = await f.get('/v0/users/alice/collections?type=3&limit=30&offset=0', { headers: authHeaders });
  assert.equal(a.status, 200); assert.equal(a.headers.get('x-cache'), null);
  assert.equal((await a.json()).data[0].ep_status, 12);
  assert.equal(calls.at(-1).path, '/v0/users/alice/collections?subject_type=1&limit=30&offset=0&type=3');
  for (const [token, progress] of [['alice-token', 12], ['bob-token', 99], ['alice-token', 12]]) {
    const r = await f.get(ownBook, { headers: { Authorization: 'Bearer ' + token } });
    assert.equal(r.status, 200); assert.equal((await r.json()).ep_status, progress);
  }
  assert.equal(calls.filter(x => x.path === '/v0/me').length, 4);
  const before = calls.length;
  for (const query of ['?subject_type=2', '?type=6', '?limit=31', '?offset=10001', '?url=http://localhost', '?type=1&type=2']) {
    assert.equal((await f.get('/v0/users/-/collections' + query, { headers: authHeaders })).status, 400);
  }
  assert.equal(calls.length, before);
});

test('invalid collection writes and account targets cannot reach upstream', async t => {
  const calls = []; const f = await fixture(t, { upstream: accountUpstream(calls), ipBurst: 100 });
  for (const body of [{}, { type: 0 }, { type: 6 }, { type: '3' }, { ep_status: -1 }, { ep_status: 1.5 },
    { vol_status: 1000001 }, { rate: 10 }, { comment: 'bad' }, { private: false }, { user: 'bob', type: 3 }]) {
    assert.equal((await f.get(ownBook, { method: 'PATCH', headers: authHeaders, body: JSON.stringify(body) })).status, 400);
  }
  assert.equal((await f.get(ownBook, { method: 'POST', headers: authHeaders, body: '{"type":3,"ep_status":2}' })).status, 400);
  assert.equal((await f.get('/v0/users/alice/collections/118165', { method: 'PATCH', headers: authHeaders, body: '{"type":3}' })).status, 405);
  assert.equal((await f.get(ownBook, { method: 'DELETE', headers: authHeaders })).status, 405);
  assert.equal(calls.length, 0);
});

test('upstream write failure is propagated without success or automatic replay', async t => {
  const calls = []; const f = await fixture(t, { upstream: accountUpstream(calls, { writeStatus: 403 }) });
  const r = await f.get(ownBook, { method: 'PATCH', headers: authHeaders, body: '{"ep_status":50}' });
  assert.equal(r.status, 403); assert.equal(calls.filter(x => x.method === 'PATCH').length, 1);
  assert.equal((await (await f.get(ownBook, { headers: authHeaders })).json()).ep_status, 12);
});

test('Bangumi 202 Accepted writes are preserved and can be read back', async t => {
  const calls = []; const f = await fixture(t, { upstream: accountUpstream(calls, { writeStatus: 202 }) });
  for (const [method, body] of [['POST', { type: 2 }], ['PATCH', { ep_status: 3, vol_status: 1 }]]) {
    const response = await f.get(ownBook, { method, headers: authHeaders, body: JSON.stringify(body) });
    assert.equal(response.status, 202); assert.equal(await response.text(), '');
  }
  const value = await (await f.get(ownBook, { headers: authHeaders })).json();
  assert.equal(value.type, 2); assert.equal(value.ep_status, 3); assert.equal(value.vol_status, 1);
  assert.equal(calls.filter(x => x.method !== 'GET').length, 2);
});

test('private list rejects mixed anime content, and write bursts have a separate limit', async t => {
  const calls = []; const base = accountUpstream(calls);
  const f = await fixture(t, { ipBurst: 100, globalBurst: 100, now: () => 0, upstream: async (path, options) => {
    if (path.includes('?')) return { status: 200, data: { data: [{ subject_type: 2 }] } };
    return base(path, options);
  } });
  assert.equal((await f.get('/v0/users/-/collections', { headers: authHeaders })).status, 502);
  for (let i = 0; i < 10; i++) assert.equal((await f.get(ownBook, { method: 'PATCH', headers: authHeaders, body: '{"vol_status":0}' })).status, 204);
  assert.equal((await f.get(ownBook, { method: 'PATCH', headers: authHeaders, body: '{"vol_status":0}' })).status, 429);
  assert.equal(calls.filter(x => x.method === 'PATCH').length, 10);
});
