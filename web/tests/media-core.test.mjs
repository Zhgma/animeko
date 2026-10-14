import { test } from 'node:test';
import assert from 'node:assert/strict';
import {checkedSettings, webUrl, torrentIdentifier, parseSubscriptionJson, boundedFetch} from '../src/wasmJsMain/resources/media-core.mjs';
const base='http://127.0.0.1:4173/';
test('settings reject unsupported trackers and invalid speeds',()=>{
 assert.throws(()=>checkedSettings({trackers:['udp://tracker.test:80']}));
 assert.throws(()=>checkedSettings({trackers:['wss://user:pass@test/']}));
 assert.throws(()=>checkedSettings({maxConns:0}));
 assert.throws(()=>checkedSettings({downloadLimit:NaN}));
 assert.equal(checkedSettings({uploadLimit:0}).uploadLimit,0);
 assert.equal(checkedSettings().downloadLimit,-1);
});
test('only web URLs and valid v1 magnet links accepted',()=>{
 for(const value of ['javascript:alert(1)','file:///a','https://user:pass@example.com/x','http://external.test/a']) assert.throws(()=>webUrl(value,base));
 assert.equal(webUrl('/samples/a',base),base+'samples/a');
 assert.throws(()=>torrentIdentifier('magnet:?xt=urn:btih:no',base));
 assert.equal(torrentIdentifier('a'.repeat(40),base),'magnet:?xt=urn:btih:'+'a'.repeat(40));
 assert.throws(()=>torrentIdentifier('magnet:?xt=urn:btih:'+'a'.repeat(40)+'&ws=file:///x',base));
});
test('Animeko RSS v1 format supports search templates and preserves unknown source formats',()=>{
 const formats=[{factoryId:'rss',version:1,arguments:{name:'RSS',searchConfig:{searchUrl:'https://example.com/?q={keyword}&page={page}'}}},{factoryId:'selector',version:2,arguments:{name:'Native'}}];
 const parsed=parseSubscriptionJson(JSON.stringify({mediaSources:formats}),base);
 assert.equal(parsed[0].supported,true);assert.equal(parsed[1].supported,false);assert.deepEqual(parsed[1].format,formats[1]);
 assert.throws(()=>parseSubscriptionJson('[]',base));
 assert.equal(parseSubscriptionJson(JSON.stringify({mediaSources:[{...formats[0],version:2}]}),base)[0].supported,false);
});
test('fetch omits credentials/referrer and rejects oversized streams and HTTP failures',async()=>{
 const content=await boundedFetch('https://example.com',async(url,options)=>{assert.equal(url,'https://43.131.255.74:8443/api/web/feeds?url=https%3A%2F%2Fexample.com');assert.equal(options.credentials,'omit');assert.equal(options.referrerPolicy,'no-referrer');return new Response('ok');});
 assert.equal(new TextDecoder().decode(content),'ok');
 await assert.rejects(boundedFetch('https://example.com',async()=>new Response('too large'),3));
 await assert.rejects(boundedFetch('https://example.com',async()=>new Response('',{status:403})),/403/);
 await assert.rejects(boundedFetch('https://example.com',async()=>{throw new TypeError('NetworkError')}),/远程订阅 API/);
});
