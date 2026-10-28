import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';

const script=readFileSync(new URL('../src/wasmJsMain/resources/api-endpoints.js',import.meta.url),'utf8');
function harness(saved=new Map()) {
  let config={version:1,origin:'https://remote.example:8443'};
  const calls=[];
  const context=vm.createContext({URL,AbortSignal,localStorage:{getItem:k=>saved.get(k),setItem:(k,v)=>saved.set(k,v)},
    fetch:async(url,options)=>{calls.push({url,options});return {ok:true,text:async()=>JSON.stringify(config)};}});
  vm.runInContext(script,context);
  return {c:context,calls,setConfig:value=>{config=value;},saved,refresh:()=>new Promise(resolve=>context.aniRelayRefresh(resolve))};
}
test('manual and subscription changes update every endpoint and survive reload',async()=>{
  const h=harness();
  assert.equal(await h.refresh(),'');
  assert.equal(h.c.aniApi.bangumi,'https://remote.example:8443/api/web/bangumi');
  assert.equal(h.calls[0].options.credentials,'omit');
  assert.equal(h.calls[0].options.headers,undefined);
  assert.equal(h.c.aniRelaySave('https://manual.example','https://config.example/api.json'),'');
  assert.equal(await h.refresh(),'');
  assert.equal(h.c.aniApi.auth,'https://manual.example/api/web/auth/animeko');
  assert.equal(h.c.aniApi.image('https://43.131.255.74:8443/api/books/images/a/sig?q=1'),'https://manual.example/api/books/images/a/sig?q=1');
  assert.equal(h.c.aniApi.image('https://remote.example:8443/api/komiic/images/a/sig'),'https://manual.example/api/komiic/images/a/sig');
  assert.equal(harness(h.saved).c.aniApi.base,'https://manual.example/api/web');
  assert.equal(h.c.aniRelaySave('','https://config.example/api.json'),'');
  assert.equal(h.c.aniApi.base,'https://remote.example:8443/api/web');
});
test('invalid settings and failed refreshes preserve the address',async()=>{
  const h=harness(); const original=h.c.aniApi.base;
  for(const origin of ['http://example.com','https://u:p@example.com','https://example.com/api','https://example.com?q=x']) {
    assert.notEqual(h.c.aniRelaySave(origin,'https://example.com/api.json'),'');
    assert.equal(h.c.aniApi.base,original);
  }
  for(const config of [{version:2,origin:'https://example.com'},{version:1,origin:'http://example.com'}]) {
    h.setConfig(config); assert.notEqual(await h.refresh(),''); assert.equal(h.c.aniApi.base,original);
  }
});
test('late subscription responses cannot replace user edits',async()=>{
  const h=harness(); let finish;
  h.c.fetch=()=>new Promise(resolve=>{finish=resolve;});
  const pending=h.refresh();
  h.c.aniRelaySave('https://manual.example','https://new.example/config.json');
  finish({ok:true,text:async()=>JSON.stringify({version:1,origin:'https://stale.example'})});
  assert.notEqual(await pending,''); assert.equal(h.c.aniApi.base,'https://manual.example/api/web');
});
