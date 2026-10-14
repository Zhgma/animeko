import { test } from 'node:test';
import assert from 'node:assert/strict';
import { defaultSubjectSubscriptions, subjectSources, subjectQuery, filterSubjectItems, torrentKey, searchSubjectSource } from '../src/wasmJsMain/resources/subject-media.mjs';
const base = 'http://127.0.0.1:4173/';
const garden = {url:'https://api.animes.garden/feed.xml?page=1&pageSize=3'};
const magnet = 'magnet:?xt=urn:btih:' + 'a'.repeat(40);

test('earlier subscriptions are reused, including disabled subscriptions without re-enabling them', () => {
  const existing=[{id:'old',enabled:false,kind:'rss',url:garden.url,items:[]}];
  const defaults=defaultSubjectSubscriptions(existing,base,()=> 'new');
  assert.equal(defaults.length,1); assert.match(defaults[0].title,/Nyaa/);
  assert.equal(subjectSources([...existing,...defaults]).length,1);
  assert.equal(existing[0].enabled,false);
});
test('native RSS source toggles and unsupported extension sources control available searches', () => {
  const list=subjectSources([{id:'s',title:'App',enabled:true,kind:'animeko',sources:[
    {id:'a',title:'a',supported:true,enabled:false},{id:'b',title:'b',supported:false},
    {id:'c',title:'c',supported:true,url:garden.url}]}]);
  assert.equal(list.length,1); assert.equal(list[0].id,'s/c');
});
test('Garden searches the actual resource library with complete names, type and pagination', () => {
  const anime=new URL(subjectQuery(garden,'总之就是非常可爱 第二季','anime',2,base).url);
  assert.equal(anime.searchParams.get('search'),'总之就是非常可爱 第二季');
  assert.equal(anime.searchParams.get('type'),'动画'); assert.equal(anime.searchParams.get('page'),'2');
  assert.equal(anime.searchParams.get('pageSize'),'50');
  const book=new URL(subjectQuery(garden,'天国大魔境','book',1,base).url);
  assert.equal(book.searchParams.get('type'),'漫画');
});
test('legacy Garden template is adapted before redirects discard search terms',()=>{
  const source={url:'https://garden.breadio.wiki/feed.xml?filter=[{"type":"动画","search":["{keyword}"]}]'};
  const url=new URL(subjectQuery(source,'book','book',1,base).url);
  assert.equal(url.hostname,'api.animes.garden'); assert.equal(url.searchParams.get('type'),'漫画');
  assert.equal(url.searchParams.has('filter'),false);
});
test('Nyaa uses correct anime and literature RSS categories and never pretends to page',()=>{
  const nyaa={url:'https://nyaa.si/?page=rss&c=3_0&f=0'};
  const q=subjectQuery(nyaa,'A & B','book',1,base); const url=new URL(q.url);
  assert.equal(url.searchParams.get('q'),'A & B'); assert.equal(url.searchParams.get('c'),'3_0');
  assert.equal(q.paged,false); assert.equal(new URL(subjectQuery(nyaa,'A','anime',1,base).url).searchParams.get('c'),'1_0');
});
test('custom templates encode keywords; plain and file feeds only filter local titles',()=>{
  const q=subjectQuery({url:'https://rss.test/?q={keyword}&page={page}'},'A&B','anime',3,base);
  assert.equal(new URL(q.url).searchParams.get('q'),'A&B'); assert.equal(q.remote,true);
  assert.equal(subjectQuery({url:''},'ABC','book',1,base).remote,false);
  assert.throws(()=>subjectQuery(garden,'','anime',1,base));
  assert.throws(()=>subjectQuery(garden,'abc','anime',0,base));
});
test('release deduplication recognizes base32 and hex hashes despite different tracker parameters',()=>{
  const hex='magnet:?xt=urn:btih:'+'0'.repeat(40);
  const b32='magnet:?xt=urn:btih:'+'A'.repeat(32)+'&tr=wss://tracker.test';
  assert.equal(torrentKey(hex),torrentKey(b32));
  const items=[{title:'ＡＢＣ test',supported:true,url:hex},{title:'ABC test',supported:true,url:b32},{title:'other',supported:true,url:magnet},{title:'ABC chapter',supported:false,url:''}];
  assert.equal(filterSubjectItems(items,'abc',false).length,1);
  assert.equal(filterSubjectItems(items,'abc',true).length,2);
});
test('search returns only real torrent enclosures and pagination based on upstream rows',async()=>{
  let requested='';
  const result=await searchSubjectSource(garden,{keyword:'ABC',kind:'anime',page:1},base,
    async url=>{requested=url;return new TextEncoder().encode('fixture');},
    ()=>({items:Array.from({length:50},(_,i)=>({title:'ABC '+i,url:'https://test/'+i+'.torrent',supported:true}))}));
  assert.match(requested,/search=ABC/); assert.equal(result.items.length,50); assert.equal(result.hasNext,true);
});
test('network errors remain visible; local RSS files work without external requests',async()=>{
  await assert.rejects(searchSubjectSource(garden,{keyword:'ABC',kind:'anime',page:1},base,async()=>{throw Error('CORS');}),/CORS/);
  const result=await searchSubjectSource({url:'',items:[{title:'ABC',url:magnet,supported:true}]},{keyword:'ABC',kind:'book',page:1},base,async()=>{throw Error('unexpected request');});
  assert.equal(result.items.length,1); assert.equal(result.hasNext,false);
});
