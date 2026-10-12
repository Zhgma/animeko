// SPDX-License-Identifier: AGPL-3.0-only
// Explicit live verification: node services/books-api/smoke.mjs
import assert from 'node:assert/strict';
const base = 'https://119.28.149.19:8443/api/books';
const results = [];
async function call(path, options = {}) {
  const start = Date.now();
  const response = await fetch(base + path, { ...options, signal: AbortSignal.timeout(20000) });
  const data = await response.json();
  results.push({ path, status: response.status, cache: response.headers.get('x-cache'), ms: Date.now() - start });
  return { response, data };
}
const search = (keyword, type = 1) => call('/v0/search/subjects?limit=10&offset=0', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ keyword, filter: { type: [type] } }),
});
assert.equal((await call('/health')).response.status, 200);
for (const keyword of ['碧蓝之海', '三体']) {
  const { response, data } = await search(keyword);
  assert.equal(response.status, 200, JSON.stringify(data));
  assert.ok(data.data.length > 0); assert.ok(data.data.every(book => book.type === 1));
  results.push({ keyword, books: data.data.map(({ id, name_cn, name, platform }) => ({ id, name: name_cn || name, platform })) });
  const book = data.data.find(book => book.platform === '小说') ?? data.data[0];
  const detail = await call('/v0/subjects/' + book.id);
  assert.equal(detail.response.status, 200); assert.equal(detail.data.type, 1);
  assert.equal((await call('/v0/subjects/' + book.id)).response.headers.get('x-cache'), 'HIT');
}
assert.equal((await call('/v0/subjects/253')).response.status, 404);
assert.equal((await search('test', 2)).response.status, 400);
assert.equal((await call('/v0/me')).response.status, 401);
assert.equal((await call('/v0/subjects/118165/subjects')).response.status, 404);
assert.equal((await call('/v0/subjects/118165?url=http://127.0.0.1')).response.status, 400);
assert.equal((await call('/v0/subjects/118165', { method: 'PATCH' })).response.status, 405);
assert.equal((await call('/v0/subjects/118165', { headers: { Authorization: 'Bearer test-not-a-real-token' } })).response.status, 400);
assert.equal((await call('/v0/users/-/collections/118165')).response.status, 401);
assert.equal((await call('/v0/users/-/collections/118165', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"type":3}' })).response.status, 401);
assert.equal((await call('/v0/users/-/collections/118165', { method: 'PATCH', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer test-not-a-real-token' }, body: '{"ep_status":12}' })).response.status, 401);
const comments = await call('/p1/subjects/311122/comments?limit=2&offset=0');
assert.equal(comments.response.status, 200); assert.ok(comments.data.total > 2); assert.equal(comments.data.data.length, 2);
const nextComments = await call('/p1/subjects/311122/comments?limit=2&offset=2');
assert.equal(nextComments.response.status, 200); assert.notEqual(comments.data.data[0].id, nextComments.data.data[0].id);
assert.equal((await call('/p1/subjects/253/comments')).response.status, 404);
assert.equal((await call('/p1/subjects/311122/comments?limit=21')).response.status, 400);
// Different client-supplied forwarding headers must still share the actual caller's IP budget.
const limited = await Promise.all(Array.from({ length: 35 }, (_, i) => call('/health', { headers: { 'X-Real-IP': `192.0.2.${i + 1}` } })));
assert.ok(limited.some(({ response }) => response.status === 429));
assert.ok(limited.every(({ response }) => [200, 429].includes(response.status)));
console.log(JSON.stringify({ passed: true, time: new Date().toISOString(), results }, null, 2));
