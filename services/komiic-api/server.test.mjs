// SPDX-License-Identifier: AGPL-3.0-only
import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createKomiicServer } from './server.mjs';

const kid = '5a4b77e0-bf9b-407a-9d18-6ab223c08c20';
const jpeg = Buffer.from([255, 216, 255, 217]);
const json = data => ({ status: 200, type: 'application/json', body: Buffer.from(JSON.stringify({ data })) });
async function fixture(t, options = {}) {
  const calls = [];
  const server = createKomiicServer({ secret: Buffer.alloc(32, 7), upstream: async args => {
    calls.push(args);
    if (!args.body) return { status: 200, type: 'image/jpeg', body: jpeg };
    if (args.body.query.startsWith('query Search')) return json({ searchComicsAndAuthors: { comics: [{ id: '115', title: 'Book', imageUrl: 'https://public.komiic.com/comics/85859ad8241bcbe8c823330faa856026/cover.jpg' }] } });
    if (args.body.query.startsWith('query Chapters')) return json({ chaptersByComicId: [{ id: '1490', serial: '1', type: 'book', size: 1 }] });
    return json({ imagesByChapterId: [{ kid, width: 704, height: 1008 }] });
  }, ...options });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const get = (path, init) => fetch(origin + '/api/komiic' + path, init);
  const post = value => get('/query', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) });
  return { get, post, calls, origin };
}

test('search, chapters, pages, signed cover and page bytes use fixed anonymous upstream', async t => {
  const f = await fixture(t);
  const search = await (await f.post({ operation: 'Search', variables: { keyword: ' book ' } })).json();
  assert.equal(f.calls[0].body.variables.keyword, 'book');
  assert.ok(f.calls[0].body.query.startsWith('query Search'));
  const cover = new URL(search.data.searchComicsAndAuthors.comics[0].imageUrl);
  assert.equal(cover.hostname, '119.28.149.19');
  assert.equal((await fetch(f.origin + cover.pathname)).status, 200);
  assert.equal(f.calls[1].host, 'public.komiic.com');
  assert.equal((await f.post({ operation: 'Chapters', variables: { comicId: '115' } })).status, 200);
  const pages = await (await f.post({ operation: 'Images', variables: { comicId: '115', chapterId: '1490' } })).json();
  assert.deepEqual(f.calls.at(-1).body.variables, { chapterId: '1490' });
  const path = new URL(pages.data.imagesByChapterId[0].url).pathname;
  const image = await fetch(f.origin + path);
  assert.equal(image.headers.get('content-type'), 'image/jpeg');
  assert.deepEqual(Buffer.from(await image.arrayBuffer()), jpeg);
  assert.deepEqual(f.calls.at(-1), { host: 'komiic.com', path: '/api/image/' + kid, referer: 'https://komiic.com/comic/115/chapter/1490/images/all' });
  assert.equal((await fetch(f.origin + path)).headers.get('x-cache'), 'HIT');
  assert.equal((await f.get('/icon')).status, 200);
  assert.ok(f.calls.every(call => !call.authorization && !call.cookie));
});

test('arbitrary GraphQL, mutations, extra variables, URLs, nonnumeric IDs and credentials cannot reach upstream', async t => {
  const f = await fixture(t, { jsonBurst: 100 });
  for (const value of [{ query: 'mutation { deleteAccount }' }, { operation: 'constructor', variables: {} },
    { operation: 'Search', variables: { keyword: 'ok' }, query: 'query { me }' },
    { operation: 'Search', variables: { keyword: 'x', url: 'http://localhost' } },
    { operation: 'Search', variables: { keyword: '' } }, { operation: 'Search', variables: { keyword: 'a'.repeat(101) } },
    { operation: 'Chapters', variables: { comicId: '../admin' } }, { operation: 'Chapters', variables: { comicId: 1 } },
    { operation: 'Images', variables: { chapterId: '2' } }]) assert.equal((await f.post(value)).status, 400);
  assert.equal((await f.get('/query?url=http://localhost')).status, 404);
  assert.equal((await f.get('/api/image/' + kid)).status, 404);
  assert.equal((await f.get('/query')).status, 405);
  assert.equal((await f.get('/health', { headers: { Authorization: 'Bearer secret' } })).status, 400);
  assert.equal((await f.get('/health', { headers: { Cookie: 'session=secret' } })).status, 400);
  assert.equal((await f.get('/query', { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: '{}' })).status, 415);
  assert.equal((await f.post({ operation: 'Search', variables: { keyword: 'x'.repeat(5000) } })).status, 413);
  assert.equal(f.calls.length, 0);
});

test('tampered image signatures and query injection are rejected before image fetch', async t => {
  const f = await fixture(t);
  const pages = await (await f.post({ operation: 'Images', variables: { comicId: '115', chapterId: '1490' } })).json();
  const path = new URL(pages.data.imagesByChapterId[0].url).pathname;
  const prefix = path.slice(0, path.lastIndexOf('/') + 1);
  assert.equal((await fetch(f.origin + prefix + '_'.repeat(43))).status, 404);
  assert.equal((await fetch(f.origin + path + '?url=http://localhost')).status, 404);
  assert.equal((await fetch(f.origin + path, { headers: { Authorization: 'Bearer secret' } })).status, 400);
  assert.equal(f.calls.length, 1);
});

test('login, forbidden, reading limit, redirect and upstream throttling propagate without automatic retries', async t => {
  for (const status of [401, 403, 429, 302, 500]) {
    let count = 0;
    const f = await fixture(t, { upstream: async () => { count++; return { status, type: 'text/html', body: Buffer.from('blocked') }; } });
    const result = await f.post({ operation: 'Search', variables: { keyword: 'book' } });
    assert.equal(result.status, [401, 403, 429].includes(status) ? status : 502);
    assert.equal(count, 1);
    if (status === 429) {
      assert.equal((await f.post({ operation: 'Search', variables: { keyword: 'other' } })).status, 429);
      assert.equal(count, 1);
    }
  }
  const f = await fixture(t, { upstream: async () => ({ status: 200, type: 'application/json', body: Buffer.from('{"errors":[{"message":"reading limit reached"}]}') }) });
  assert.equal((await f.post({ operation: 'Images', variables: { comicId: '115', chapterId: '1490' } })).status, 403);
});

test('malformed upstream payloads cannot become empty success or signed external assets', async t => {
  const f = await fixture(t, { upstream: async () => json({ searchComicsAndAuthors: { comics: [{ id: '1', title: 'B', imageUrl: 'https://evil.example/a.jpg' }] } }) });
  const data = await (await f.post({ operation: 'Search', variables: { keyword: 'book' } })).json();
  assert.equal(data.data.searchComicsAndAuthors.comics[0].imageUrl, '');
  const malformed = await fixture(t, { upstream: async () => json({ imagesByChapterId: [{ kid: '../private', width: 1, height: 1 }] }) });
  assert.equal((await malformed.post({ operation: 'Images', variables: { comicId: '1', chapterId: '2' } })).status, 502);
  const html = await fixture(t, { upstream: async () => ({ status: 200, type: 'text/html', body: Buffer.from('<html>Captcha</html>') }) });
  assert.equal((await html.get('/icon')).status, 502);
});

test('cache coalesces identical searches and expires after a minute', async t => {
  let now = 0;
  const f = await fixture(t, { now: () => now });
  const body = { operation: 'Search', variables: { keyword: 'book' } };
  const responses = await Promise.all([f.post(body), f.post(body)]);
  assert.ok(responses.every(r => r.status === 200)); assert.equal(f.calls.length, 1);
  assert.equal((await f.post(body)).headers.get('x-cache'), 'HIT');
  now = 60001; assert.equal((await f.post(body)).headers.get('x-cache'), 'MISS'); assert.equal(f.calls.length, 2);
});

test('image quota is separate, forwarding headers are untrusted, and CORS excludes credentials', async t => {
  const f = await fixture(t, { imageBurst: 1, imagePerMinute: 0, jsonBurst: 1, jsonPerMinute: 0 });
  assert.equal((await f.get('/icon')).status, 200);
  assert.equal((await f.get('/icon', { headers: { 'X-Real-IP': '1.2.3.4' } })).status, 429);
  const preflight = await f.get('/query', { method: 'OPTIONS' });
  assert.equal(preflight.status, 204); assert.equal(preflight.headers.get('access-control-allow-credentials'), null);
  assert.equal((await f.get('/health')).status, 429);
});
