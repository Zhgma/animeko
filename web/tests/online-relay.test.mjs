import test from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {createOnlineRelay,publicAddress,onlineSubscription,isMangaSearchUrl} from '../online-relay.mjs';
import {staticVideoUrl} from '../src/wasmJsMain/resources/online-media.mjs';
import {parseSubscriptionJson} from '../src/wasmJsMain/resources/media-core.mjs';
import {subjectSources} from '../src/wasmJsMain/resources/subject-media.mjs';
const format={factoryId:'web-selector',version:2,arguments:{name:'Online',searchConfig:{searchUrl:'https://example.com/search?q={keyword}',subjectFormatId:'a',channelFormatId:'no-channel'}}};
test('manga search allowlist excludes credentials, unrelated paths and redirects',async()=>{
  for(const value of ['https://www.manhuaren.com/search?title=blue','https://api.creative-comic.tw/book?keyword=blue','https://www.manhuagui.com/s/blue_p1.html'])assert.equal(isMangaSearchUrl(new URL(value)),true);
  for(const value of ['https://www.manhuaren.com/account','https://u:p@www.manhuaren.com/search','https://www.manhuaren.com:444/search','http://www.manhuaren.com/search','https://evil.example/search'])assert.equal(isMangaSearchUrl(new URL(value)),false);
  await withRelay(async()=>({status:302,location:'https://untrusted.example/'}),async base=>{
    assert.equal((await fetch(base+'/api/online/page?'+new URLSearchParams({url:'https://www.manhuaren.com/search?title=blue'}))).status,502);
  });
});
test('public manga search works without the anime catalog and preserves quota errors',async()=>{
  const relay=createOnlineRelay({read:async url=>{assert.notEqual(url,onlineSubscription);return url.includes('quota')?{status:429}:{status:200,type:'text/html',text:'manga result'};}});
  const server=createServer((req,res)=>relay(req,res,new URL(req.url,'http://localhost')));
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try {
    const base='http://127.0.0.1:'+server.address().port+'/api/online/page?';
    const result=await fetch(base+new URLSearchParams({url:'https://www.manhuaren.com/search?title=blue'}));
    assert.equal(result.status,200);assert.equal((await result.json()).text,'manga result');
    assert.equal((await fetch(base+new URLSearchParams({url:'https://www.manhuaren.com/search?title=quota'}))).status,429);
  } finally {await new Promise(resolve=>server.close(resolve));}
});
async function withRelay(read,run){
  const relay=createOnlineRelay({read:async url=>url===onlineSubscription?{status:200,text:JSON.stringify({exportedMediaSourceDataList:{mediaSources:[format]}})}:read(url)});
  const server=createServer(async(req,res)=>{await relay(req,res,new URL(req.url,'http://localhost'));});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try{await run('http://127.0.0.1:'+server.address().port);}finally{await new Promise(resolve=>server.close(resolve));}
}
test('online relay restricts origins, methods and cross-site callers',async()=>{
  const calls=[];
  await withRelay(async url=>{calls.push(url);return {status:200,type:'text/html',text:'<a>source</a>'};},async base=>{
    for(const url of ['https://evil.example/test','https://example.com:8080/','https://u:p@example.com/'])assert.equal((await fetch(base+'/api/online/page?'+new URLSearchParams({url}))).status,403);
    assert.equal((await fetch(base+'/api/online/subscription',{method:'POST'})).status,405);
    assert.equal((await fetch(base+'/api/online/subscription',{headers:{origin:'https://other.example'}})).status,403);
    const response=await fetch(base+'/api/online/page?url=https%3A%2F%2Fexample.com%2Fsearch',{headers:{Authorization:'Bearer ignored',Cookie:'ignored'}});
    assert.equal(response.status,200);assert.equal(calls.length,1);assert.equal((await response.json()).text,'<a>source</a>');
  });
});
test('private, loopback, metadata and non-IPv4 addresses are rejected',()=>{
  for(const ip of ['127.0.0.1','10.0.0.1','169.254.169.254','172.20.0.1','192.168.1.1','100.64.1.1','0.0.0.0','::1','::ffff:127.0.0.1'])assert.equal(publicAddress(ip),false,ip);
  assert.equal(publicAddress('104.21.1.1'),true);
});
test('source redirects remain HTTPS and errors remain errors',async()=>{
  await withRelay(async url=>url.includes('example.com')?{status:301,location:'https://new.example/page'}:{status:403,type:'text/html',text:'blocked'},async base=>{
    assert.equal((await fetch(base+'/api/online/page?url=https%3A%2F%2Fexample.com%2Fsearch')).status,403);
  });
});
test('online source import is supported but never enters the BT source list',()=>{
  const sources=parseSubscriptionJson(JSON.stringify({mediaSources:[format]}),'https://example.com');
  assert.equal(sources[0].supported,true);assert.equal(sources[0].kind,'web');
  assert.deepEqual(subjectSources([{id:'s',kind:'animeko',enabled:true,sources}]),[]);
});
test('static player data supports public video URLs without executing scripts',()=>{
  assert.equal(staticVideoUrl('var player_aaaa = {"url":"https://cdn.example/a.m3u8","encrypt":0};','https://example.com'),'https://cdn.example/a.m3u8');
  assert.equal(staticVideoUrl('var player_aaaa = {"url":"javascript:alert(1)"};','https://example.com'),'');
  assert.equal(staticVideoUrl('var player_aaaa = {url:danger()};','https://example.com'),'');
});
