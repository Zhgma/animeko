// SPDX-License-Identifier: AGPL-3.0-only
import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createBookImages } from './images.mjs';
import { createBooksServer } from './server.mjs';

const cover = 'https://lain.bgm.tv/pic/cover/l/ad/0c/118167_mghJX.jpg';
const avatar = 'https://lain.bgm.tv/r/200/pic/user/l/icon.jpg';
const secret = Buffer.alloc(32, 7);
const image = { type: 'image/jpeg', body: Buffer.from([255, 216, 255, 217]) };
const parts = url => new URL(url).pathname.split('/').slice(-2);

test('only fixed BGM image fields receive signed URLs, never user comments or arbitrary origins', () => {
  const images = createBookImages({ secret });
  const result = images.rewrite({ images: { large: cover }, image: cover, comment: cover, user: { avatar: { medium: avatar } } });
  assert.match(result.images.large, /^https:\/\/43\.131\.255\.74:8443\/api\/books\/images\//);
  assert.equal(result.image, result.images.large);
  assert.equal(result.comment, cover);
  assert.match(result.user.avatar.medium, /\/images\//);
  for (const bad of ['http://127.0.0.1/private', 'https://evil.example/pic/cover/l/a.jpg',
    'https://lain.bgm.tv.evil.example/pic/cover/l/a.jpg', 'https://lain.bgm.tv@evil.example/a.jpg',
    'https://lain.bgm.tv:444/pic/cover/l/a.jpg', 'https://lain.bgm.tv/pic/cover/l/a.svg',
    'https://lain.bgm.tv/admin', cover + '?url=http://localhost', cover + '?hd=1&hd=1']) {
    assert.equal(images.rewrite({ images: { large: bad } }).images.large, bad);
  }
});

test('signed images survive restart with the same key and reject changed paths or signatures', async () => {
  const first = createBookImages({ secret });
  const url = first.rewrite({ image: cover }).image;
  const calls = [];
  const second = createBookImages({ secret, upstream: async path => { calls.push(path); return image; } });
  assert.deepEqual((await second.load(...parts(url))).body, image.body);
  assert.deepEqual(calls, ['/pic/cover/l/ad/0c/118167_mghJX.jpg']);
  const [payload, signature] = parts(url);
  await assert.rejects(second.load(Buffer.from('/pic/cover/l/another.jpg').toString('base64url'), signature), { status: 404 });
  await assert.rejects(second.load(payload, '_'.repeat(43)), { status: 404 });
  await assert.rejects(createBookImages({ secret: Buffer.alloc(32, 8) }).load(payload, signature), { status: 404 });
  assert.equal(calls.length, 1);
});

test('image cache expires, coalesces requests, and rejects invalid content without caching it', async () => {
  let now = 0, calls = 0;
  const images = createBookImages({ secret, now: () => now, upstream: async () => { calls++; return image; } });
  const p = parts(images.rewrite({ image: cover }).image);
  await Promise.all([images.load(...p), images.load(...p)]);
  assert.equal(calls, 1);
  assert.equal((await images.load(...p)).cache, 'HIT');
  now = 3600001;
  assert.equal((await images.load(...p)).cache, 'MISS');
  assert.equal(calls, 2);
  for (const result of [{ type: 'text/html', body: image.body }, { type: 'image/jpeg', body: Buffer.alloc(0) },
    { type: 'image/jpeg', body: Buffer.alloc(8 * 1024 * 1024 + 1) }]) {
    const invalid = createBookImages({ secret, upstream: async () => result });
    await assert.rejects(invalid.load(...p), { status: 502 });
  }
});

test('book search, detail, relations, comments and own library issue usable image URLs', async t => {
  const calls = [];
  const server = createBooksServer({ imageSecret: secret, imageUpstream: async path => { calls.push(path); return image; },
    upstream: async path => ({ status: 200, data:
      path === '/v0/me' ? { id: 1, username: 'reader' } :
      path.includes('/collections?') ? { data: [{ subject_type: 1, subject: { type: 1, images: { common: cover } } }] } :
      path.endsWith('/subjects/118167/subjects') ? [{ id: 118165, type: 1, relation: '全集', images: { common: cover } }] :
      path.includes('/search/') ? { data: [{ id: 118167, type: 1, images: { common: cover } }] } :
      { id: 118167, type: 1, images: { large: cover } } }),
    commentsUpstream: async () => ({ status: 200, data: { total: 1, data: [{ id: 1, comment: '单行本评论', user: { avatar: { medium: avatar } } }] } }) });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
  const base = `http://127.0.0.1:${server.address().port}/api/books`;
  const detail = await (await fetch(base + '/v0/subjects/118167')).json();
  const publicImage = new URL(detail.images.large).pathname;
  const origin = new URL(base).origin;
  const response = await fetch(origin + publicImage);
  assert.equal(response.status, 200); assert.equal(response.headers.get('content-type'), 'image/jpeg');
  assert.deepEqual(Buffer.from(await response.arrayBuffer()), image.body);
  assert.equal((await fetch(origin + publicImage)).headers.get('x-cache'), 'HIT');
  const search = await (await fetch(base + '/v0/search/subjects', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"keyword":"book"}' })).json();
  assert.equal(search.data[0].images.common, detail.images.large);
  const relations = await (await fetch(base + '/v0/subjects/118167/subjects')).json();
  assert.equal(relations[0].images.common, detail.images.large);
  const comments = await (await fetch(base + '/p1/subjects/118167/comments')).json();
  assert.match(comments.data[0].user.avatar.medium, /\/images\//);
  const collection = await fetch(base + '/v0/users/-/collections', { headers: { Authorization: 'Bearer test' } });
  assert.equal(collection.headers.get('cache-control'), 'no-store');
  assert.equal((await collection.json()).data[0].subject.images.common, detail.images.large);
  assert.equal((await fetch(origin + publicImage + '?url=http://localhost')).status, 400);
  assert.equal((await fetch(origin + publicImage, { headers: { Authorization: 'Bearer test' } })).status, 400);
  assert.equal((await fetch(origin + publicImage, { headers: { Cookie: 'x=1' } })).status, 400);
  assert.equal((await fetch(origin + publicImage, { method: 'POST' })).status, 405);
  assert.equal(calls.length, 1);
});

test('image requests have a separate bounded budget and do not exhaust book metadata quota', async t => {
  const server = createBooksServer({ imageSecret: secret, imageIpBurst: 1, imageIpPerMinute: 0,
    ipBurst: 1, ipPerMinute: 0, imageUpstream: async () => image });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const path = new URL(createBookImages({ secret }).rewrite({ image: cover }).image).pathname;
  assert.equal((await fetch(origin + path)).status, 200);
  assert.equal((await fetch(origin + path)).status, 429);
  assert.equal((await fetch(origin + '/api/books/health')).status, 200);
});
