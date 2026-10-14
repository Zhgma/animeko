import {test} from 'node:test';
import assert from 'node:assert/strict';
import {tolerateResourceCacheWriteFailure} from '../src/wasmJsMain/resources/resource-cache.mjs';
test('failed Compose resource cache writes are optional without changing torrent caches',async()=>{
 const storage={open:async()=>({put:async()=>{throw Error('Cache.put network error')},match:async()=> 'cached'})};
 tolerateResourceCacheWriteFailure(storage,{fetch:async()=>new Response('font'),location:new URL('http://localhost/')});
 const resources=await storage.open('compose_web_resources_cache');
 await resources.put('font',{});assert.equal(await resources.match('font'),'cached');
 await assert.rejects((await storage.open('webtorrent')).put('file',{}));
});
test('Compose resource response is buffered independently; other requests are untouched',async()=>{
 const api=new Response('api');
 const host={fetch:async url=>url.includes('composeResources')?new Response('resource',{headers:{'Content-Type':'font/ttf'}}):api,location:new URL('http://localhost/')};
 tolerateResourceCacheWriteFailure(null,host);
 const resource=await host.fetch('/composeResources/font.ttf');
 assert.equal(await resource.clone().text(),'resource');assert.equal(await resource.text(),'resource');
 assert.equal(resource.headers.get('Content-Type'),'font/ttf');assert.equal(await host.fetch('/api/status'),api);
});
