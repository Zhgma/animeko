import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';
import { createOAuthRelay } from '../oauth-relay.mjs';

const authorized = () => ({tokens:{accessToken:'test-ani',refreshToken:'test-refresh',bangumiAccessToken:'test-bgm',expiresAtMillis:Date.now()+3600000},user:{bangumiUsername:'reader',nickname:'Reader',email:'omit@example.test'}});
async function withServer(fetchImpl, run, extra = {}) {
  const handler=createOAuthRelay({fetchImpl,...extra});
  const server=createServer(async(req,res)=>{if(!await handler(req,res,new URL(req.url,'http://localhost')))res.writeHead(404).end();});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try { await run('http://127.0.0.1:'+server.address().port); }
  finally { await new Promise(resolve=>server.close(resolve)); }
}
const start = id => '/api/auth/bangumi/start?'+new URLSearchParams({requestId:id,os:'windows',arch:'x86_64'});
const result = id => '/api/auth/bangumi/result?requestId='+id;

test('Animeko email endpoints sanitize bodies and permit unbound Bangumi sessions', async () => {
  const calls = [];
  await withServer(async (url, options) => {
    calls.push({ url, options });
    if (url.endsWith('/email/otp')) {
      assert.deepEqual(JSON.parse(options.body), { email: 'reader@example.test' });
      return Response.json({ otpId: 'otp-test', hasExistingUser: true });
    }
    assert.equal(url, 'https://api.animeko.org/v2/users/auth/email');
    assert.deepEqual(JSON.parse(options.body), { otpId: 'otp-test', otpValue: '123456' });
    const data = authorized(); data.tokens.bangumiAccessToken = null;
    return Response.json(data);
  }, async base => {
    const post = (action, body) => fetch(base + '/api/auth/animeko/' + action, { method: 'POST', headers: { 'Content-Type': 'application/json', Origin: base }, body: JSON.stringify(body) });
    assert.equal((await post('email-otp', { email: 'invalid' })).status, 400);
    assert.equal((await post('email-otp', { email: 'reader@example.test', ignored: 'secret' })).status, 200);
    assert.equal((await post('email-login', { otpId: 'otp-test', otpValue: 'abc' })).status, 400);
    const login = await post('email-login', { otpId: 'otp-test', otpValue: '123456' });
    assert.equal(login.status, 200); assert.equal((await login.json()).tokens.bangumiAccessToken, null);
    assert.equal(calls.length, 2);
  });
});

test('email delivery failures do not retry and send duplicate verification emails', async () => {
  let calls = 0;
  await withServer(async () => { calls++; throw new Error('timeout'); }, async base => {
    const result = await fetch(base + '/api/auth/animeko/email-otp', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: 'reader@example.test' }) });
    assert.equal(result.status, 502); assert.equal(calls, 1);
  });
});

test('provider discovery filters unknown providers and binding requires Animeko authentication', async () => {
  await withServer(async (url, options) => {
    if (url.endsWith('/providers')) return Response.json({ providers: ['bangumi', 'github', 'unknown'] });
    assert.match(url, /\/v2\/users\/bangumi\/bind\?/);
    assert.equal(options.headers.Authorization, 'Bearer test-ani');
    return Response.json({ url: 'https://bgm.tv/oauth/authorize' });
  }, async base => {
    assert.deepEqual((await (await fetch(base + '/api/auth/animeko/providers')).json()).providers, ['bangumi', 'github']);
    assert.equal((await fetch(base + start(randomUUID()) + '&mode=bind')).status, 401);
    assert.equal((await fetch(base + start(randomUUID()) + '&mode=bind', { headers: { Authorization: 'Bearer test-ani' } })).status, 200);
  });
});

test('relay uses App OAuth endpoints and handles waiting, success and consumed requests',async()=>{
  const requests=[];let ready=false;
  await withServer(async(url,options)=>{
    requests.push({url,options});
    if(url.includes('/oauth?'))return Response.json({url:'https://bgm.tv/oauth/authorize?state=test'});
    return ready?Response.json(authorized()):new Response('',{status:425});
  },async base=>{
    const id=randomUUID();
    assert.equal((await fetch(base+start(id),{headers:{Origin:base,Cookie:'private=ignored'}})).status,200);
    assert.equal((await fetch(base+result(id))).status,425); ready=true;
    const reply=await fetch(base+result(id)),data=await reply.json();
    assert.equal(reply.headers.get('cache-control'),'no-store');
    assert.equal(data.tokens.bangumiAccessToken,'test-bgm');assert.equal(data.user.email,'omit@example.test');
    assert.equal((await fetch(base+result(id))).status,410);
    assert.match(requests[0].url,/^https:\/\/api.animeko.org\/v2\/users\/bangumi\/oauth\?/);
    assert.match(requests[1].url,/\/v2\/users\/bangumi\/result\?/);
    assert.ok(requests.every(x=>!x.options.headers.Origin&&!x.options.headers.Cookie&&!x.options.headers.Authorization));
  });
});

test('relay rejects foreign origins, unknown IDs and invalid platforms before forwarding',async()=>{
  let calls=0;
  await withServer(async()=>{calls++;throw new Error('unexpected');},async base=>{
    assert.equal((await fetch(base+start(randomUUID()),{headers:{Origin:'https://other.example'}})).status,403);
    assert.equal((await fetch(base+start(randomUUID()),{headers:{'Sec-Fetch-Site':'cross-site'}})).status,403);
    assert.equal((await fetch(base+result(randomUUID()))).status,410);
    assert.equal((await fetch(base+start(randomUUID()).replace('os=windows','os=web'))).status,400);
    assert.equal((await fetch(base+result('../me'))).status,400);
    assert.equal(calls,0);
  });
});

test('relay validates authorization destinations, retries known servers and expires requests',async()=>{
  let clock=0,calls=0;
  await withServer(async url=>{
    calls++;
    if(url.startsWith('https://api.animeko.org'))return new Response('',{status:503});
    return Response.json({url:'https://bgm.tv/oauth/authorize'});
  },async base=>{
    const id=randomUUID();assert.equal((await fetch(base+start(id))).status,200);assert.equal(calls,2);
    clock=600001;assert.equal((await fetch(base+result(id))).status,410);
  },{now:()=>clock});
  await withServer(async()=>Response.json({url:'https://other.example/oauth/authorize'}),async base=>{
    assert.equal((await fetch(base+start(randomUUID()))).status,502);
  });
});

test('relay refreshes through Animeko only and rejects invalid refresh bodies',async()=>{
  let calls=0;
  await withServer(async(url,options)=>{
    calls++;assert.equal(url,'https://api.animeko.org/v2/users/auth/refresh');
    assert.equal(options.method,'POST');assert.deepEqual(JSON.parse(options.body),{refreshToken:'test-refresh'});
    return Response.json(authorized());
  },async base=>{
    const path=base+'/api/auth/bangumi/refresh';
    assert.equal((await fetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:'test-refresh'})})).status,200);
    assert.equal((await fetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'})).status,400);
    assert.equal((await fetch(path)).status,405);assert.equal(calls,1);
  });
});
