import {test} from 'node:test';
import assert from 'node:assert/strict';
import {addDefaultCatalogs, refreshedSubscription, sourceConfigurations} from '../src/wasmJsMain/resources/subscription-state.mjs';
import {parseSubscriptionJson} from '../src/wasmJsMain/resources/media-core.mjs';
import {readFileSync} from 'node:fs';
const base='https://zhgma.github.io/animeko/';
const formats=[{factoryId:'rss',version:1,arguments:{name:'A',searchConfig:{searchUrl:'https://example.org/?q={keyword}'}}},{factoryId:'web-selector',version:2,arguments:{name:'B',searchConfig:{searchUrl:'https://example.org/b?q={keyword}'}}}];
const sources=()=>parseSubscriptionJson(JSON.stringify({mediaSources:formats}),base);
test('default catalog insertion is idempotent and preserves user state',()=>{
 const existing=[{id:'old',url:'https://test/a',enabled:false,sources:[{title:'custom'}]}];
 const catalogs=[{url:'https://test/a'},{url:'https://test/b',kind:'manga'}];
 const result=addDefaultCatalogs(existing,catalogs,()=> 'new');
 assert.equal(result.length,2);assert.equal(result[0],existing[0]);assert.equal(result[1].kind,'manga');
 assert.deepEqual(addDefaultCatalogs(result,catalogs),result);
});
test('refresh retains disabled sources, local order and user subscription title',()=>{
 const old=sources().reverse();old[0].enabled=false;
 const sub={id:'x',title:'Custom',kind:'animeko',enabled:false,sources:old};
 const result=refreshedSubscription(sub,{kind:'animeko',title:'Remote',sources:sources()},123);
 assert.equal(result.title,'Custom');assert.equal(result.enabled,false);assert.equal(result.sources[0].title,'B');assert.equal(result.sources[0].enabled,false);assert.equal(result.updated,123);
});
test('export one source excludes other sources and deleted stale configuration',()=>{
 const sub={id:'x',kind:'animeko',sources:sources(),configurationText:JSON.stringify({mediaSources:formats})};
 assert.deepEqual(JSON.parse(sourceConfigurations([sub],'x','source-1')[0]).mediaSources,[formats[1]]);
 sub.sources.pop();assert.deepEqual(JSON.parse(sourceConfigurations([sub])[0]).mediaSources,[formats[0]]);
});
test('current App manga catalog preserves seven adapters without classifying them as BT',()=>{
 const text=readFileSync(new URL('../../subs/manga.json',import.meta.url),'utf8');
 const result=parseSubscriptionJson(text,base);
 assert.equal(result.length,7);assert.ok(result.every(s=>s.kind==='manga' && !s.supported));
 assert.deepEqual(JSON.parse(sourceConfigurations([{id:'m',kind:'manga',sources:result}])[0]).mediaSources,JSON.parse(text).exportedMediaSourceDataList.mediaSources);
});

test('catalog insertion and reordering cannot redirect a selected source ID',()=>{
 const old=sources();
 const inserted={...old[0],id:'source-0',title:'New',url:'https://new.example/search'};
 const result=refreshedSubscription({title:'Catalog',kind:'animeko',sources:old},{kind:'animeko',sources:[inserted,...sources().reverse()]});
 assert.equal(result.sources.find(s=>s.title==='A').id,old[0].id);
 assert.equal(result.sources.find(s=>s.title==='B').id,old[1].id);
 assert.equal(new Set(result.sources.map(s=>s.id)).size,3);
 assert.ok(!old.some(s=>s.id===result.sources.find(s=>s.title==='New').id));
});
