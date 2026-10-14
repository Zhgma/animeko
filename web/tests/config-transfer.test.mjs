import {test} from 'node:test';
import assert from 'node:assert/strict';
import {writeConfiguration,configAction} from '../src/wasmJsMain/resources/config-io.mjs';
import {parseSubscriptionJson} from '../src/wasmJsMain/resources/media-core.mjs';
const storage = () => {const data = new Map();return {data,getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v),removeItem:k=>data.delete(k)}};
test('configuration transport preserves 64-bit JSON as opaque text and cannot write account keys',()=>{
 const s=storage(), raw='{"seedColorValue":18446744069414584320}';
 writeConfiguration(s,'p.',{themeSettings:raw});assert.equal(s.getItem('p.themeSettings'),raw);
 assert.throws(()=>writeConfiguration(s,'p.',{tokenStore:'secret'}));
});
test('storage failure rolls back partial configuration updates',()=>{
 const s=storage();s.setItem('p.themeSettings','old');const set=s.setItem;let fail=true;
 s.setItem=(k,v)=>{if(k==='p.uiSettings'&&fail){fail=false;throw Error('quota')}return set(k,v)};
 assert.throws(()=>writeConfiguration(s,'p.',{themeSettings:'new',uiSettings:'{}'}));
 assert.equal(s.getItem('p.themeSettings'),'old');assert.equal(s.getItem('p.uiSettings'),null);
});
test('media apply failure rolls back general settings',async()=>{
 const s=storage();s.setItem('p.themeSettings','old');
 await assert.rejects(configAction('configApply',{values:{themeSettings:'new'},mediaSettings:{}},s,'p.',async()=>{throw Error('failed')}));
 assert.equal(s.getItem('p.themeSettings'),'old');
});
test('App subscription wrapper accepted; malformed imports rejected before persistence',()=>{
 const source={factoryId:'rss',version:1,arguments:{name:'RSS',description:'',iconUrl:'',searchConfig:{searchUrl:'https://example.com/?q={keyword}'}}};
 assert.equal(parseSubscriptionJson(JSON.stringify({exportedMediaSourceDataList:{mediaSources:[source]}}),'http://localhost/')[0].supported,true);
 for (const value of [null,{}, {mediaSources:[null]},{mediaSources:[{factoryId:'rss',version:'1',arguments:{}}]}]) assert.throws(()=>parseSubscriptionJson(JSON.stringify(value),'http://localhost/'));
});
