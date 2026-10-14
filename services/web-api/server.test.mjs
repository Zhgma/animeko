import test from 'node:test';
import assert from 'node:assert/strict';
import {once} from 'node:events';
import {createWebServer, readFeed} from './server.mjs';
import {publicAddress, readOnlinePage} from '../../web/online-relay.mjs';
const origin='https://zhgma.github.io';
async function start(t,options={}) {
  const server=createWebServer(options); server.listen(0,'127.0.0.1'); await once(server,'listening');
  t.after(()=>new Promise(resolve=>{server.closeAllConnections();server.close(resolve);}));
  return async(path,init={})=>fetch(`http://127.0.0.1:${server.address().port}/api/web${path}`,{...init,headers:{Origin:origin,...init.headers}});
}
const ok=data=>new Response(JSON.stringify(data),{headers:{'Content-Type':'application/json'}});
test('Pages preflight, errors and health have exact CORS; hostile origins and headers are rejected',async t=>{
  let calls=0; const request=await start(t,{fetchImpl:async()=>{calls++;return ok({});}});
  let res=await request('/health'); assert.equal(res.status,200);assert.equal(res.headers.get('access-control-allow-origin'),origin);
  assert.equal(res.headers.get('access-control-allow-credentials'),null);
  res=await request('/animeko/v2/subjects/1',{method:'OPTIONS',headers:{'Access-Control-Request-Method':'PATCH','Access-Control-Request-Headers':'Authorization, Content-Type'}});
  assert.equal(res.status,204);
  assert.equal((await request('/health',{headers:{Origin:'https://evil.example'}})).status,403);
  assert.equal((await request('/health',{headers:{Origin:'null'}})).status,403);
  assert.equal((await request('/health',{method:'OPTIONS',headers:{'Access-Control-Request-Method':'GET','Access-Control-Request-Headers':'X-Target'}})).status,403);
  res=await request('/unknown');assert.equal(res.status,404);assert.equal(res.headers.get('access-control-allow-origin'),origin);assert.equal(calls,0);
});
test('public subject and login providers work anonymously; mutation uses only supplied Ani Bearer',async t=>{
  const calls=[];const request=await start(t,{fetchImpl:async(url,init)=>{calls.push({url,init});return ok(url.endsWith('providers')?{providers:['bangumi','github']}:{});}});
  assert.equal((await request('/animeko/v2/subjects/622288')).status,200);
  assert.deepEqual(await (await request('/auth/animeko/providers')).json(),{providers:['bangumi','github']});
  assert.equal((await request('/animeko/v2/subjects/622288',{method:'PATCH',headers:{'Content-Type':'application/json'},body:'{}'})).status,401);
  const res=await request('/animeko/v2/subjects/622288',{method:'PATCH',headers:{Authorization:'Bearer ani-test',Cookie:'not-forwarded=1','Content-Type':'application/json','Sec-Fetch-Site':'cross-site'},body:'{"collectionType":"DOING"}'});
  assert.equal(res.status,200);assert.equal(calls.at(-1).init.headers.Authorization,'Bearer ani-test');
  assert.equal(calls.at(-1).init.headers.Cookie,undefined);assert.equal(calls.at(-1).init.headers.Origin,undefined);
  assert.ok(calls.every(c=>c.url.startsWith('https://api.animeko.org/')));
});
test('Bangumi reads and collection updates retain token kind and JSON shape, and writes are not retried',async t=>{
  const calls=[];const request=await start(t,{fetchImpl:async(url,init)=>{calls.push({url,init});return new Response(null,{status:503});}});
  const res=await request('/bangumi/v0/users/-/collections/1',{method:'PATCH',headers:{Authorization:'Bearer bgm-test','Content-Type':'application/json'},body:'{"type":3}'});
  assert.equal(res.status,503);assert.equal(calls.length,1);assert.equal(calls[0].init.headers.Authorization,'Bearer bgm-test');
  assert.deepEqual(JSON.parse(calls[0].init.body),{type:3});
});
test('feed forwarding accepts JSON/RSS, forbids credentials and unsafe URLs and never sends auth to source',async t=>{
  const calls=[];const read=async url=>{calls.push(url);return {status:200,type:'application/json',text:'{"mediaSources":[]}'}};
  const request=await start(t,{read});
  const route='/feeds?url='+encodeURIComponent('https://feed.example/list.json');
  assert.deepEqual(await(await request(route)).json(),{mediaSources:[]});
  assert.equal((await request(route,{headers:{Authorization:'Bearer secret'}})).status,400);assert.equal(calls.length,1);
  await assert.rejects(()=>readFeed('https://user:password@feed.example',read));
  await assert.rejects(()=>readFeed('http://feed.example',read));
  await assert.rejects(()=>readFeed('https://feed.example',async()=>({status:200,type:'text/html',text:'<html>blocked</html>'})));
  assert.equal((await readFeed('https://feed.example',async()=>({status:200,type:'application/rss+xml',text:'<?xml version="1.0"?><rss version="2.0"></rss>'}))).status,200);
  for(const address of ['127.0.0.1','10.0.0.1','169.254.169.254','192.168.1.1','100.64.0.1','192.0.0.1']) assert.equal(publicAddress(address),false,address);
  await assert.rejects(()=>readOnlinePage('https://127.0.0.1/'),/本地网络/);
});
test('online pages require catalog origin; no arbitrary HTML proxy',async t=>{
  const read=async url=>({status:200,type:'application/json',text:JSON.stringify({exportedMediaSourceDataList:{mediaSources:[]}})});
  const request=await start(t,{read});
  assert.equal((await request('/online/page?url='+encodeURIComponent('https://evil.example/'))).status,403);
});
test('per-IP rate and OTP limits are enforced with Retry-After',async t=>{
  const request=await start(t,{requestLimit:2,fetchImpl:async()=>ok({providers:[]})});
  assert.equal((await request('/auth/animeko/providers')).status,200);
  assert.equal((await request('/auth/animeko/providers')).status,200);
  const res=await request('/auth/animeko/providers');assert.equal(res.status,429);assert.equal(res.headers.get('retry-after'),'60');
  const otp=await start(t,{fetchImpl:async()=>ok({otpId:'fake'})});
  for(let i=0;i<3;i++)assert.equal((await otp('/auth/animeko/email-otp',{method:'POST',headers:{'Content-Type':'application/json'},body:'{"email":"test@example.com"}'})).status,200);
  assert.equal((await otp('/auth/animeko/email-otp',{method:'POST',headers:{'Content-Type':'application/json'},body:'{"email":"test@example.com"}'})).status,429);
});

test('online extraction validates the selected catalog source and returns only a signed media route',async t=>{
 const catalog={exportedMediaSourceDataList:{mediaSources:[{factoryId:'web-selector',version:2,arguments:{searchConfig:{searchUrl:'https://site.example/search?q={keyword}',matchVideo:{addHeadersToVideo:{referer:'https://site.example/'}}}}}]}};
 const calls=[];const request=await start(t,{read:async url=>{calls.push(url);return url.includes('creamycake')?{status:200,text:JSON.stringify(catalog)}:{status:200,type:'text/html',text:'<video src="https://cdn.example:8088/a.mp4"></video>'};}});
 const query=new URLSearchParams({url:'https://site.example/watch/1',source:'https://site.example/search?q={keyword}'});
 const response=await request('/online/resolve?'+query);assert.equal(response.status,200);
 const result=await response.json();assert.ok(result.url.startsWith('/api/web/online/media?ticket='));assert.equal(result.type,'video');
 query.set('url','https://evil.example/');assert.equal((await request('/online/resolve?'+query)).status,403);
 assert.equal((await request('/online/media?ticket=forged')).status,403);
 const preflight=await request('/online/media?ticket=x',{method:'OPTIONS',headers:{'Access-Control-Request-Method':'GET','Access-Control-Request-Headers':'Range'}});assert.equal(preflight.status,204);
});

test('episode comments preserve App query, optional Bearer and response, without accepting writes',async t=>{
 const calls=[]; const payload={total:1,items:[{id:'bangumi:7',episodeId:12345}],bangumiUnavailable:false,nextCursor:'next+/='};
 const request=await start(t,{fetchImpl:async(url,init)=>{calls.push({url,init});return ok(payload)}});
 const path='/animeko/v2/episodes/12345/comments?offset=20&limit=20&includeBangumi=true&after=next%2B%2F%3D';
 let response=await request(path);assert.equal(response.status,200);assert.deepEqual(await response.json(),payload);
 assert.equal(response.headers.get('access-control-allow-origin'),origin);
 assert.equal(calls[0].url,'https://api.animeko.org/v2/episodes/12345/comments?offset=20&limit=20&includeBangumi=true&after=next%2B%2F%3D');
 assert.equal(calls[0].init.headers.Authorization,undefined);
 response=await request(path,{headers:{Authorization:'Bearer ani-test',Cookie:'not-forwarded=1'}});
 assert.equal(response.status,200);assert.equal(calls[1].init.headers.Authorization,'Bearer ani-test');assert.equal(calls[1].init.headers.Cookie,undefined);
 for(const method of ['POST','PATCH','DELETE']) assert.equal((await request('/animeko/v2/episodes/12345/comments',{method})).status,404);
 assert.equal((await request('/animeko/v2/episodes/12345/comments?url=https://evil.example')).status,400);
 assert.equal((await request('/animeko/v2/subjects/12345?includeBangumi=true')).status,400);
 assert.equal(calls.length,2);
});
test('episode comments forward upstream failures without misreporting them as missing gateway routes',async t=>{
 let calls=0;const request=await start(t,{fetchImpl:async()=>{calls++;return new Response(null,{status:401})}});
 const response=await request('/animeko/v2/episodes/12345/comments');assert.equal(response.status,401);
 assert.equal(calls,1);assert.match((await response.json()).error,/会话已过期/);
});
