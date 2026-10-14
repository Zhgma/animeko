import { test } from 'node:test';
import assert from 'node:assert/strict';
import { btAttributes } from '../src/wasmJsMain/resources/bt-attributes.mjs';

test('RSS release attributes populate compact labels and actual filter values', () => {
  const a=btAttributes('[喵萌奶茶屋&LoliHouse] 测试 - 04 [WebRip 1080p HEVC-10bit AAC][简繁内封字幕]', '2024-09-10T12:00:00Z');
  assert.equal(a.alliance,'喵萌奶茶屋&LoliHouse'); assert.equal(a.resolution,'1080P');
  assert.deepEqual(a.subtitles,['简中','繁中']); assert.equal(a.subtitleKind,'内封');
  assert.equal(a.episodeStart,4); assert.equal(a.episodeEnd,4); assert.equal(a.published,'09-10');
});
test('single, fractional, versioned and pack numbers avoid confusing resolution with episode',()=>{
  assert.equal(btAttributes('[Group] Test [01v2][1080P]').episodeStart,1);
  assert.equal(btAttributes('Test 第 12.5 话 [720p]').episodeStart,12.5);
  assert.equal(btAttributes('Test S02E03 [1080p]').episodeStart,3);
  const pack=btAttributes('[Group] Test [01-12][CHT]');
  assert.equal(pack.episodeStart,1);assert.equal(pack.episodeEnd,12);
  assert.equal(btAttributes('Test [1080p][2024]').episodeStart,-1);
});
test('unavailable metadata stays unknown instead of inventing release attributes',()=>{
  const a=btAttributes('漫画合集.zip','invalid date');
  assert.equal(a.episodeStart,-1); assert.equal(a.alliance,''); assert.equal(a.resolution,'');
  assert.deepEqual(a.subtitles,[]); assert.equal(a.published,'');
});
