import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { webcrypto } from 'node:crypto';

const source = readFileSync(new URL('../src/wasmJsMain/resources/bridge.js', import.meta.url), 'utf8');
const endpointSource = readFileSync(new URL('../src/wasmJsMain/resources/api-endpoints.js', import.meta.url), 'utf8');
const oauthSource = readFileSync(new URL('../src/wasmJsMain/resources/oauth.js', import.meta.url), 'utf8');
const authData = () => ({tokens:{accessToken:'ani-test',refreshToken:'refresh-test',bangumiAccessToken:'bgm-test',expiresAtMillis:Date.now()+3600000},user:{bangumiUsername:'reader',nickname:'读者'}});
const storage = () => { const map = new Map(); return { getItem: k => map.get(k) ?? null, setItem: (k, v) => map.set(k, v), removeItem: k => map.delete(k), map }; };
function harness(fetch) {
  const localStorage = storage(), sessionStorage = storage();
  const popup = {closed:false,document:{body:{}},location:{replace(url){this.url=url;}},close(){this.closed=true;},focus(){}};
  const context = { localStorage, sessionStorage, fetch, AbortSignal, URL, URLSearchParams, crypto:webcrypto, navigator:{userAgent:'Windows NT 10.0'}, open:()=>popup, setTimeout, clearTimeout, Date, Map, console, popup }; 
  context.window = context;
  vm.createContext(context); vm.runInContext(endpointSource, context); vm.runInContext(oauthSource, context); vm.runInContext(source, context);
  // Transport contract stub. Actual policy is covered by Kotlin BooksApiRoutingTest.
  context.aniInstallMangaApi((method, url, body) => {
    const path = new URL(url).pathname;
    const publicRead = path.includes('/subjects/') || path === '/v0/search/subjects';
    const fields = body ? Object.keys(JSON.parse(body)) : [];
    const relayed = true;
    const stateOnly = !path.includes('/collections/') || fields.every(k => ['type','ep_status','vol_status','rate','comment','private'].includes(k));
    return JSON.stringify({url: relayed && stateOnly ? 'https://books.example/api/books' + path : url, sendAuthorization: !publicRead, timeoutMillis:20000});
  }, (keyword, sort) => {
    if (sort === 'date') throw new Error('Bangumi book search does not support date sorting');
    return JSON.stringify({keyword:keyword.trim(),sort:sort === 'collect' ? 'heat' : sort,filter:{type:[1]}});
  }, 20);
  return { ...context, call: (action, payload = {}) => new Promise(resolve => context.aniRequest(action, JSON.stringify(payload), raw => resolve(JSON.parse(raw)))) };
}
const response = data => ({ ok: true, json: async () => data });

test('search and collections pass page offsets and normalize reading progress', async () => {
  const calls=[];
  const h=harness(async (url,options)=>{calls.push({url,options}); return response({total:64,data:url.includes('/users/')?[{subject_id:7,ep_status:9,subject:{id:7,name:'作品'}}]:[{id:7,name:'作品'}]});});
  h.localStorage.setItem('animecomic.web.session',JSON.stringify(authData()));
  assert.equal((await h.call('search',{keyword:'作品',type:2,offset:24,sort:'date'})).ok,true);
  assert.match(calls[0].url,/offset=24/);assert.equal(JSON.parse(calls[0].options.body).sort,'date');
  const result=await h.call('collection',{username:'reader',type:1,offset:30});
  assert.equal(result.items[0].progress,9);assert.match(calls[1].url,/offset=30/);
});

test('collection edits send only requested fields without caching private data; 404 alone means uncollected', async () => {
  let status=404, last;
  const h=harness(async(url,options)=>{
    last={url,options};
    if(url.endsWith('/me'))return response({username:'reader'});
    if(options.method==='PATCH')return {ok:true,status:204};
    return {ok:false,status,json:async()=>({error:'upstream'})};
  });
  h.localStorage.setItem('animecomic.web.session',JSON.stringify(authData()));
  assert.equal((await h.call('libraryRead',{id:355555,type:1})).raw,'null');
  status=401;assert.equal((await h.call('libraryRead',{id:355555,type:1})).ok,false);
  assert.equal((await h.call('libraryWrite',{id:355555,type:1,fields:{ep_status:12}})).ok,true);
  assert.deepEqual(JSON.parse(last.options.body),{ep_status:12});
  assert.equal(last.options.headers.Authorization,'Bearer bgm-test');
  assert.equal([...h.localStorage.map.keys()].filter(k=>k!=='animecomic.web.session').length,0);
});

test('unknown chapter totals remain unknown instead of using volume totals as chapters', async () => {
  const h=harness(async()=>response({id:1,type:1,name:'书籍',volumes:24,total_episodes:0,eps:0}));
  const result=await h.call('detail',{id:1});
  assert.equal(result.subject.count,0);assert.equal(result.subject.volumes,24);
});

test('hash routes allow internal subject details, exploration and settings categories', () => {
  const h=harness(async()=>response({}));
  h.window.location={hash:'#subject/355555'};
  assert.equal(h.aniCurrentRoute(),'subject/355555');
  for(const route of ['explore','search','anime','settings/theme']){h.window.location.hash='#'+route;assert.equal(h.aniCurrentRoute(),route);}
  for(const route of ['subject/-1','subject/9999999999999999999','subject/1?token=bad','https://other.example']){h.window.location.hash='#'+route;assert.equal(h.aniCurrentRoute(),'collection');}
});

test('direct hash changes and browser back share the route listener and dispose cleanly', () => {
  const h=harness(async()=>response({}));const events=new Map(),observed=[];
  h.window.location={hash:'#collection'};
  h.window.addEventListener=(name,listener)=>events.set(name,listener);
  h.window.removeEventListener=(name,listener)=>{assert.equal(events.get(name),listener);events.delete(name);};
  h.aniWatchRoute(route=>observed.push(route));
  h.window.location.hash='#subject/355555';events.get('hashchange')();
  h.window.location.hash='#collection';events.get('popstate')();
  assert.deepEqual(observed,['subject/355555','collection']);
  h.aniUnwatchRoute();assert.equal(events.size,0);
});

test('OAuth opens Bangumi and stores returned session without manual token input', async () => {
  const requests = []; let pending = true;
  const h = harness(async (url, options) => {
    requests.push({url,options});
    if (url.includes('/auth/animeko/start?')) return response({url:'https://bgm.tv/oauth/authorize?state=test'});
    if (pending) return {status:425};
    return response(authData());
  });
  assert.equal(h.aniOAuthPrepare(),true);
  assert.equal((await h.call('oauthStart')).pending,true);
  assert.match(h.popup.location.url,/^https:\/\/bgm.tv\/oauth\/authorize/);
  assert.equal((await h.call('oauthResult')).pending,true);
  pending=false;
  const result=await h.call('oauthResult');
  assert.equal(result.username,'reader'); assert.equal(h.popup.closed,true);
  assert.equal([...h.localStorage.map.keys()].filter(k=>k!=='animecomic.web.session').length,0);
  assert.equal(JSON.parse(h.localStorage.getItem('animecomic.web.session')).tokens.bangumiAccessToken,'bgm-test');
  assert.ok(requests.every(x => x.url.startsWith('https://43.131.255.74:8443/api/web/auth/animeko/') && !x.options.headers.Authorization));
  h.aniLogout(); assert.equal(h.sessionStorage.map.size,0);
});

test('cancelled OAuth cannot save a late authorization result', async () => {
  let resolveResult;
  const h=harness(async url => url.includes('/auth/animeko/start?') ? response({url:'https://bgm.tv/oauth/authorize'}) : new Promise(resolve=>{resolveResult=resolve;}));
  h.aniOAuthPrepare(); await h.call('oauthStart');
  const result=h.call('oauthResult'); await Promise.resolve();
  h.aniOAuthCancel(); resolveResult(response(authData()));
  assert.equal((await result).ok,false); assert.equal(h.sessionStorage.map.size,0);
});

test('OAuth rejects unexpected authorization hosts and missing tokens', async () => {
  const h=harness(async ()=>response({url:'https://attacker.example/oauth/authorize'}));
  h.aniOAuthPrepare(); assert.equal((await h.call('oauthStart')).ok,false);
  assert.equal(h.popup.location.url,undefined);
  const invalid=harness(async url=>response(url.includes('/auth/animeko/start?')?{url:'https://bgm.tv/oauth/authorize'}:{tokens:{}}));
  invalid.aniOAuthPrepare(); await invalid.call('oauthStart');
  assert.equal((await invalid.call('oauthResult')).ok,false); assert.equal(invalid.sessionStorage.map.size,0);
});

test('OAuth reports invalid API responses, popup blocking and closed authorization window', async () => {
  const h=harness(async()=>({ok:false,status:404,json:async()=>{throw new Error('HTML');}}));
  h.aniOAuthPrepare(); assert.match((await h.call('oauthStart')).error,/有效 JSON/);
  const closed=harness(async url=>url.includes('/auth/animeko/start?')?response({url:'https://bgm.tv/oauth/authorize'}):{status:425});
  closed.aniOAuthPrepare(); await closed.call('oauthStart'); closed.popup.closed=true;
  assert.match((await closed.call('oauthResult')).error,/窗口已关闭/);
  const blocked=harness(async()=>{}); blocked.window.open=()=>null;
  assert.equal(blocked.aniOAuthPrepare(),false);
});

test('expired OAuth sessions refresh once and logout prevents late refresh persistence', async () => {
  let requests=0,finish;
  const h=harness(async(url,options)=>{requests++;assert.equal(url,'https://43.131.255.74:8443/api/web/auth/animeko/refresh');assert.equal(JSON.parse(options.body).refreshToken,'refresh-test');return new Promise(resolve=>{finish=resolve;});});
  const expired=authData();expired.tokens.expiresAtMillis=0;h.localStorage.setItem('animecomic.web.session',JSON.stringify(expired));
  const a=h.call('session'),b=h.call('session');await Promise.resolve();
  assert.equal(requests,1);h.aniLogout();finish(response(authData()));
  assert.equal((await a).ok,false);assert.equal((await b).ok,false);assert.equal(h.sessionStorage.map.size,0);
});

test('refreshed credentials are saved and only the Bangumi token reaches its API', async () => {
  const h=harness(async(url,options)=>{
    if(url==='https://43.131.255.74:8443/api/web/auth/animeko/refresh')return response(authData());
    assert.equal(url,'https://books.example/api/books/v0/me');
    assert.equal(options.headers.Authorization,'Bearer bgm-test');
    return response({username:'reader'});
  });
  const expired=authData();expired.tokens.expiresAtMillis=0;
  h.localStorage.setItem('animecomic.web.session',JSON.stringify(expired));
  assert.equal((await h.call('session')).username,'reader');
  assert.ok(JSON.parse(h.localStorage.getItem('animecomic.web.session')).tokens.expiresAtMillis>Date.now());
  assert.equal((await h.call('mangaRaw',{path:'me'})).ok,true);
  assert.equal([...h.localStorage.map.keys()].filter(k=>k!=='animecomic.web.session').length,0);
});

test('public browsing does not transmit the logged-in token and can fall back to labelled cache', async () => {
  let offline = false;
  const h = harness(async (url, options) => {
    assert.ok(url.startsWith('https://43.131.255.74:8443/api/web/bangumi/v0/'));
    assert.equal(options.mode, 'cors');
    assert.equal(options.headers.Authorization, undefined);
    if (offline) throw new TypeError('offline');
    return response({ data: [{ id: 1, type: 2, name_cn: '作品', images: {}, rating: { score: 8.1 } }], total: 1 });
  });
  h.localStorage.setItem('animecomic.web.session', JSON.stringify(authData()));
  assert.equal((await h.call('browse', { type: 2 })).items[0].title, '作品');
  offline = true;
  const fallback = await h.call('browse', { type: 2 });
  assert.equal(fallback.cached, true); assert.equal(fallback.items.length, 1);
});

test('account identity enrichment uses shared manga route and never the Animeko token', async () => {
  const h = harness(async (url, options) => {
    assert.equal(url, 'https://books.example/api/books/v0/me');
    assert.equal(options.mode, 'cors');
    assert.equal(options.headers.Authorization, 'Bearer bgm-test');
    return response({ username: 'reader', nickname: '读者' });
  });
  const session = authData(); session.user = {};
  h.localStorage.setItem('animecomic.web.session', JSON.stringify(session));
  assert.equal((await h.call('session')).username, 'reader');
});

test('upstream JSON failures preserve the API explanation', async () => {
  const h = harness(async () => ({ ok: false, status: 502, json: async () => ({ error: 'API 上游暂时不可用。' }) }));
  assert.match((await h.call('detail', { id: 1 })).error, /API 上游暂时不可用/);
});
test('private collections are never cached and require a session', async () => {
  const h = harness(async () => response({ data: [{ subject: { id: 5, name: '书' } }] }));
  assert.equal((await h.call('collection', { username: 'reader' })).ok, false);
  h.localStorage.setItem('animecomic.web.session', JSON.stringify(authData()));
  assert.equal((await h.call('collection', { username: 'reader', type: 1 })).items[0].type, 1);
  assert.equal([...h.localStorage.map.keys()].filter(k=>k!=='animecomic.web.session').length, 0);
});
test('empty search and invalid IDs do not issue network requests', async () => {
  let calls = 0;
  const h = harness(async () => { calls++; throw new Error('unexpected request'); });
  assert.deepEqual((await h.call('search', { keyword: ' ' })).items, []);
  assert.equal((await h.call('detail', { id: '../me' })).ok, false);
  assert.equal(calls, 0);
});
test('shared manga transport rejects paths outside the Bangumi model', async () => {
  let calls = 0; const h = harness(async () => { calls++; });
  h.localStorage.setItem('animecomic.web.session', JSON.stringify(authData()));
  assert.equal((await h.call('mangaRaw', { path: 'https://attacker.example/me' })).ok, false);
  assert.equal((await h.call('mangaRaw', { path: '../me' })).ok, false);
  assert.equal(calls, 0);
});
test('explicit sample preview loads only bundled metadata without credentials', async () => {
  const h = harness(async (url, options) => {
    assert.equal(url, './samples/1.json'); assert.equal(options.credentials, 'omit');
    assert.equal(options.headers, undefined);
    return response({ items: [{ id: 1, sample: true }] });
  });
  h.localStorage.setItem('animecomic.web.session', JSON.stringify(authData()));
  assert.equal((await h.call('sample', { type: 1 })).items[0].sample, true);
  assert.equal([...h.localStorage.map.keys()].filter(k=>k!=='animecomic.web.session').length, 0);
});

test('Animeko email login accepts an account without Bangumi and never calls the Bangumi API', async () => {
  const h = harness(async (url, options) => {
    if (url.endsWith('/email-otp')) { assert.equal(JSON.parse(options.body).email, 'reader@example.test'); return response({ otpId: 'otp-test', hasExistingUser: true }); }
    assert.ok(url.endsWith('/email-login'));
    assert.deepEqual(JSON.parse(options.body), { otpId: 'otp-test', otpValue: '123456' });
    const data = authData(); data.tokens.bangumiAccessToken = null; data.user = { id: 'ani-user', nickname: 'Reader' };
    return response(data);
  });
  assert.equal((await h.call('emailOtp', { email: 'reader@example.test' })).otpId, 'otp-test');
  const account = await h.call('emailLogin', { otpId: 'otp-test', otpValue: '123456' });
  assert.equal(account.signedIn, true); assert.equal(account.hasBangumi, false); assert.equal(account.username, '');
  assert.equal((await h.call('session')).signedIn, true);
  assert.equal((await h.call('mangaRaw', { path: 'me' })).ok, false);
});

test('cancelled email login cannot restore a session from a late response', async () => {
  let finish;
  const h = harness(async () => new Promise(resolve => { finish = resolve; }));
  const pending = h.call('emailLogin', { otpId: 'otp-test', otpValue: '123456' });
  h.aniAccountCancel(); finish(response(authData()));
  assert.equal((await pending).ok, false); assert.equal(h.sessionStorage.map.size, 0);
});

test('Bangumi binding sends only the existing Animeko token to the remote Animeko authorization API', async () => {
  const h = harness(async (url, options) => {
    assert.ok(url.startsWith('https://43.131.255.74:8443/api/web/auth/animeko/start?')); assert.ok(url.includes('provider=bangumi')); assert.ok(url.includes('mode=bind'));
    assert.equal(options.headers.Authorization, 'Bearer ani-test');
    return response({ url: 'https://bgm.tv/oauth/authorize?state=test' });
  });
  h.localStorage.setItem('animecomic.web.session', JSON.stringify(authData()));
  assert.equal(h.aniOAuthPrepare('bangumi', true), true);
  assert.equal((await h.call('oauthStart')).pending, true);
});

test('GitHub login accepts only the GitHub OAuth URL for that selected provider', async () => {
  const h = harness(async () => response({ url: 'https://github.com/login/oauth/authorize?state=test' }));
  assert.equal(h.aniOAuthPrepare('github', false), true);
  assert.equal((await h.call('oauthStart')).pending, true);
  assert.match(h.popup.location.url, /^https:\/\/github.com\/login\/oauth\/authorize/);
});

test('initial-page preference applies only when opening without a deep link', () => {
  const h=harness(async()=>response({})); h.window.location={hash:''};
  assert.equal(h.aniCurrentRoute(),'explore');
  h.aniWrite('uiSettings',JSON.stringify({mainSceneInitialPage:'CacheManagement'}));
  assert.equal(h.aniCurrentRoute(),'downloads');
  h.window.location.hash='#settings/sources';assert.equal(h.aniCurrentRoute(),'settings/sources');
  h.window.location.hash='';h.aniWrite('uiSettings','invalid JSON');assert.equal(h.aniCurrentRoute(),'explore');
});


test('book search uses shared body, 20-item pages and no credentials', async () => {
  const h = harness(async (url, options) => {
    assert.equal(url, 'https://books.example/api/books/v0/search/subjects?limit=20&offset=20');
    assert.equal(options.headers.Authorization, undefined);
    assert.equal(options.credentials, 'omit');
    assert.deepEqual(JSON.parse(options.body), {keyword:'书',sort:'heat',filter:{type:[1]}});
    return response({data:[],total:40});
  });
  h.localStorage.setItem('animecomic.web.session', JSON.stringify(authData()));
  assert.equal((await h.call('search',{keyword:' 书 ',sort:'collect',type:1,offset:20})).ok,true);
  assert.equal((await h.call('search',{keyword:'书',sort:'date',type:1})).ok,false);
});

test('Animeko session survives unavailable Bangumi enrichment and preserves Animeko profile', async () => {
  const h = harness(async () => {throw new TypeError('offline');});
  const session = authData(); session.user = {id:'ani-42',nickname:'Animeko昵称',avatar:'ani-avatar'};
  h.localStorage.setItem('animecomic.web.session', JSON.stringify(session));
  const result = await h.call('session');
  assert.equal(result.signedIn,true); assert.equal(result.hasBangumi,true);
  assert.equal(result.userId,'ani-42'); assert.equal(result.nickname,'Animeko昵称');
  assert.equal(result.avatar,'ani-avatar'); assert.ok(result.bangumiError);
});

test('known books use the shared proxy policy for details, comments, state and rating writes', async () => {
  const calls=[];
  const h = harness(async (url, options) => {
    calls.push({url,options});
    if (options.method === 'PATCH') return {ok:true,status:204};
    return response(url.endsWith('/v0/subjects/7') ? {id:7,type:1} : {data:[]});
  });
  h.localStorage.setItem('animecomic.web.session',JSON.stringify(authData()));
  await h.call('detail',{id:7}); await h.call('comments',{id:7});
  await h.call('libraryWrite',{id:7,type:1,fields:{ep_status:2}});
  await h.call('libraryWrite',{id:7,type:1,fields:{rate:8}});
  assert.ok(calls.slice(0,3).every(c=>c.url.startsWith('https://books.example/api/books/')));
  assert.equal(calls[0].options.headers.Authorization,undefined);
  assert.equal(calls[1].options.headers.Authorization,undefined);
  assert.equal(calls[2].options.headers.Authorization,'Bearer bgm-test');
  assert.equal(calls[3].url,'https://books.example/api/books/v0/users/-/collections/7');
});

test('authorization and missing responses never fall back to stale public cache', async () => {
  let status=200;
  const h=harness(async()=>status===200 ? response({id:7,type:1}) : {ok:false,status,json:async()=>({error:'denied'})});
  await h.call('detail',{id:7});
  for (status of [401,403,404]) { const r=await h.call('detail',{id:7}); assert.equal(r.ok,false); assert.equal(r.status,status); }
});


test('static Pages deployment calls public HTTPS APIs without an origin-local backend', async () => {
  const calls=[];
  const h=harness(async(url,options)=>{calls.push({url,options}); return response({providers:['bangumi']});});
  h.location={origin:'https://zhgma.github.io',pathname:'/animeko/',hash:''};
  assert.equal(h.aniAnimekoBaseUrl(),'https://43.131.255.74:8443/api/web/animeko');
  await h.call('providers');
  await h.call('episodes',{id:622288});
  await h.call('comments',{id:622288,type:2});
  assert.deepEqual(calls.map(c=>new URL(c.url).origin),Array(3).fill('https://43.131.255.74:8443'));
  assert.ok(calls.every(c=>c.options.credentials==='omit' && c.options.mode==='cors' && !c.options.headers.Authorization));
});

test('failed remote login is explicit and never retries a mutation', async () => {
  const calls=[];
  const h=harness(async(url)=>{calls.push(url);throw new TypeError('Failed to fetch');});
  const result=await h.call('emailOtp',{email:'reader@example.test'});
  assert.equal(result.ok,false); assert.match(result.error,/远程账号 API/);
  assert.deepEqual(calls,['https://43.131.255.74:8443/api/web/auth/animeko/email-otp']);
  assert.equal(h.sessionStorage.map.size,0);
});


test('book relations use the shared App API even when opened before detail finishes',async()=>{
  const calls=[];
  const h=harness(async(url,options)=>{calls.push({url,options});return response(url.endsWith('/subjects/7')?{id:7,type:1,name:'Book'}:[{id:8,type:1,name:'Related'}]);});
  const result=await h.call('related',{id:7});
  assert.equal(result.items[0].id,8);
  assert.ok(calls.every(c=>c.url.startsWith('https://books.example/api/books/')));
  assert.equal(calls.at(-1).url,'https://books.example/api/books/v0/subjects/7/subjects');
  assert.ok(calls.every(c=>!c.options.headers.Authorization));
});

test('login persists across page contexts and logout removes the persistent session',async()=>{
 const h=harness(async()=>response(authData()));
 await h.call('emailLogin',{otpId:'test',otpValue:'123456'});
 const saved=h.localStorage.getItem('animecomic.web.session');assert.ok(saved);
 const reopened=harness(async()=>response({username:'reader'}));reopened.localStorage.setItem('animecomic.web.session',saved);
 assert.equal((await reopened.call('session')).signedIn,true);
 reopened.aniLogout();assert.equal(reopened.localStorage.getItem('animecomic.web.session'),null);
});
test('existing tab session migrates into persistent storage',async()=>{
 const h=harness(async()=>response({username:'reader'}));h.sessionStorage.setItem('animecomic.web.session',JSON.stringify(authData()));
 assert.equal((await h.call('session')).signedIn,true);
 assert.ok(h.localStorage.getItem('animecomic.web.session'));assert.equal(h.sessionStorage.getItem('animecomic.web.session'),null);
});


test('migrated image links preserve signatures and only rewrite known API image paths', () => {
  const context = vm.createContext({});
  vm.runInContext(endpointSource, context);
  const old = 'https://119.28.149.19:8443/api/books/images/path/signature?size=400';
  assert.equal(context.aniApi.image(old), 'https://43.131.255.74:8443/api/books/images/path/signature?size=400');
  for (const other of ['https://119.28.149.19:8443/other', 'https://119.28.149.19:8443.evil.example/api/books/images/path']) {
    assert.equal(context.aniApi.image(other), other);
  }
});
