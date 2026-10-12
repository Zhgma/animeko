// SPDX-License-Identifier: AGPL-3.0-only
import assert from 'node:assert/strict';
const base = 'https://119.28.149.19:8443/api/komiic';
const results = [];
async function operation(name, variables) {
  const response = await fetch(base + '/query', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ operation: name, variables }), signal: AbortSignal.timeout(20000) });
  const data = await response.json(); assert.equal(response.status, 200, JSON.stringify(data));
  return data.data;
}
const health = await (await fetch(base + '/health')).json(); assert.equal(health.service, 'animecomic-komiic');
for (const keyword of ['碧藍', '花薰']) {
  const books = (await operation('Search', { keyword })).searchComicsAndAuthors.comics;
  assert.ok(books.length); const book = books[0];
  const chapters = (await operation('Chapters', { comicId: book.id })).chaptersByComicId;
  assert.ok(chapters.length); const chapter = chapters[0];
  const pages = (await operation('Images', { comicId: book.id, chapterId: chapter.id })).imagesByChapterId;
  assert.ok(pages.length);
  const images = [];
  for (const url of [book.imageUrl, pages[0].url]) {
    assert.ok(url.startsWith(base + '/images/'));
    const response = await fetch(url, { signal: AbortSignal.timeout(20000) }); assert.equal(response.status, 200);
    assert.match(response.headers.get('content-type'), /^image\//);
    const bytes = new Uint8Array(await response.arrayBuffer()); assert.ok(bytes.length > 100);
    images.push({ bytes: bytes.length, type: response.headers.get('content-type'), header: Buffer.from(bytes.slice(0, 12)).toString('hex') });
  }
  results.push({ keyword, book: book.id, title: book.title, chapters: chapters.length, chapter: chapter.id, pages: pages.length, images });
}
const icon = await fetch(base + '/icon'); assert.equal(icon.status, 200); await icon.arrayBuffer();
const invalid = await fetch(base + '/query', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"query":"mutation { invalid }"}' });
assert.equal(invalid.status, 400);
console.log(JSON.stringify({ passed: true, time: new Date().toISOString(), results }, null, 2));
