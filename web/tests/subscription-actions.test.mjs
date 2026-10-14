import {test} from 'node:test';
import assert from 'node:assert/strict';
const store=new Map();globalThis.localStorage={getItem:k=>store.get(k)??null,setItem:(k,v)=>store.set(k,v)};
globalThis.location={href:'https://zhgma.github.io/animeko/'};globalThis.window={isSecureContext:true};
const interval=globalThis.setInterval;globalThis.setInterval=(...args)=>{const timer=interval(...args);timer.unref();return timer;};
let fail=false;
const format={factoryId:'rss',version:1,arguments:{name:'Source',searchConfig:{searchUrl:'https://example.org/search?q={keyword}'}}};
globalThis.fetch=async()=>fail?new Response(JSON.stringify({error:'test offline'}),{status:502}):new Response(JSON.stringify({mediaSources:[format]}));
const {mediaAction}=await import('../src/wasmJsMain/resources/browser-media.mjs');
test('initial install, refresh failure, source toggle/export and deletion use persistent media actions',async()=>{
 let state=await mediaAction('mediaInitialize',{catalogs:[{url:'https://example.org/sub.json',title:'App catalog',kind:'animeko'}]});
 for(let i=0;i<20 && state.refreshing;i++){await new Promise(r=>setTimeout(r,5));state=await mediaAction('mediaState');}
 assert.equal(state.subscriptions.length,1);assert.equal(state.subscriptions[0].sources.length,1);
 const id=state.subscriptions[0].id, sourceId=state.subscriptions[0].sources[0].id;
 await mediaAction('subscriptionSourceToggle',{id,source:sourceId,enabled:false});
 state=await mediaAction('subscriptionRefresh',{id});assert.equal(state.subscriptions[0].sources[0].enabled,false);
 fail=true;await assert.rejects(mediaAction('subscriptionRefresh',{id}),/offline/);
 state=await mediaAction('mediaState');assert.equal(state.subscriptions[0].sources.length,1);assert.match(state.subscriptions[0].error,/offline/);
 assert.deepEqual(JSON.parse((await mediaAction('subscriptionConfiguration',{id,source:sourceId})).configurations[0]).mediaSources,[format]);
 await mediaAction('subscriptionBatch',{sources:[id+'/'+sourceId],operation:'enabled',enabled:true});
 state=await mediaAction('mediaState');assert.equal(state.subscriptions[0].sources[0].enabled,true);
 await mediaAction('subscriptionRemove',{id});state=await mediaAction('mediaInitialize',{catalogs:[{url:'https://example.org/sub.json'}]});assert.equal(state.subscriptions.length,0);
 assert.equal(JSON.parse(store.get('animecomic.web.subscriptions')).length,0);
});
