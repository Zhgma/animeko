import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {createAnimekoRelay} from '../animeko-relay.mjs';

async function withRelay(fetchImpl, run) {
  const handler = createAnimekoRelay({fetchImpl});
  const server = createServer(async (req,res) => { if (!await handler(req,res,new URL(req.url,'http://localhost'))) res.writeHead(404).end(); });
  await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
  try { await run('http://127.0.0.1:' + server.address().port); }
  finally { await new Promise(resolve => server.close(resolve)); }
}
const headers = {Authorization:'Bearer animeko-test', 'Content-Type':'application/json'};

test('public search and trends work anonymously, profile and history require Animeko credentials', async () => {
  const calls=[];
  await withRelay(async (url,options) => {calls.push({url,options});return Response.json({items:[],total:0});}, async base => {
    for (const path of ['/v2/subjects/search?q=Medalist&sortBy=relevance&include_nsfw=exclude&excludeCollectionTypes=DONE%2CDROPPED','/v2/trends/subjects?offset=0&limit=24']) {
      assert.equal((await fetch(base+'/api/animeko'+path)).status,200);
      assert.equal(calls.at(-1).options.headers.Authorization,undefined);
    }
    assert.equal((await fetch(base+'/api/animeko/v1/me',{headers})).status,200);
    assert.equal((await fetch(base+'/api/animeko/v1/me')).status,401);
    assert.equal((await fetch(base+'/api/animeko/v2/playback-histories/sync',{method:'POST',headers,body:'{"ops":[]}'})).status,200);
    assert.equal((await fetch(base+'/api/animeko/v2/playback-histories/sync',{method:'POST',headers,body:'{"ops":[{"type":"DELETE"}]}'})).status,400);
    assert.equal(calls.length,4);
  });
});

test('Animeko status and rating patches use the App endpoint with no Bangumi translation', async () => {
  const calls=[];
  await withRelay(async (url,options) => { calls.push({url,options}); return Response.json({favorite:{},scoreDetails:{}}); }, async base => {
    for (const body of [{collectionType:'WISH'}, {selfRating:{score:7,tags:['保留'],isPrivate:true,comment:'短评'}}]) {
      const result = await fetch(base+'/api/animeko/v2/subjects/430699',{method:'PATCH',headers:{...headers,Cookie:'private'},body:JSON.stringify(body)});
      assert.equal(result.status,200); assert.equal(result.headers.get('cache-control'),'no-store');
      const call=calls.at(-1);
      assert.equal(call.url,'https://api.animeko.org/v2/subjects/430699');
      assert.deepEqual(JSON.parse(call.options.body),body);
      assert.equal(call.options.headers.Authorization,'Bearer animeko-test');
      assert.equal(call.options.headers.Cookie,undefined);
    }
  });
});
test('list, authenticated detail, episode batches and deletion retain App routes', async () => {
  const calls=[];
  await withRelay(async (url,options) => { calls.push({url,options}); return options.method === 'GET' ? Response.json({items:[],total:0}) : new Response(null,{status:204}); }, async base => {
    for (const path of ['/v2/subjects/list?type=DOING&offset=30&limit=30','/v2/subjects/430699']) assert.equal((await fetch(base+'/api/animeko'+path,{headers})).status,200);
    assert.equal((await fetch(base+'/api/animeko/v2/subjects/430699/episodes/batchUpdate',{method:'PATCH',headers,body:JSON.stringify({episodeIds:[1,2],episodeCollectionType:'DONE'})})).status,204);
    assert.equal((await fetch(base+'/api/animeko/v2/subjects/430699',{method:'DELETE',headers})).status,204);
    assert.match(calls[0].url,/type=DOING&offset=30&limit=30$/);
    assert.equal(calls.length,4);
  });
});
test('relay rejects foreign origins, missing credentials and arbitrary destinations', async () => {
  let count=0;
  await withRelay(async () => {count++; throw new Error('unexpected');}, async base => {
    assert.equal((await fetch(base+'/api/animeko/v2/subjects/list')).status,401);
    assert.equal((await fetch(base+'/api/animeko/v2/subjects/1',{headers:{...headers,Origin:'https://other.example'}})).status,403);
    assert.equal((await fetch(base+'/api/animeko/v2/subjects/1?url=https://other.example',{headers})).status,400);
    assert.equal((await fetch(base+'/api/animeko/v2/users/me',{headers})).status,404);
    assert.equal(count,0);
  });
});
test('failed writes preserve HTTP status, hide raw upstream details and never retry', async () => {
  let count=0;
  await withRelay(async () => {count++; return Response.json({error:'secret upstream data'},{status:400});}, async base => {
    const result=await fetch(base+'/api/animeko/v2/subjects/1',{method:'PATCH',headers,body:'{"collectionType":"DOING"}'});
    assert.equal(result.status,400);assert.match((await result.json()).error,/Animeko.*400/);assert.equal(count,1);
  });
});

test('read failures switch to a healthy fixed Animeko node and remember it', async () => {
  const calls=[];
  await withRelay(async (url) => {
    calls.push(url);
    if (url.startsWith('https://api.animeko.org')) throw new Error('ECONNRESET');
    return Response.json({items:[],total:0});
  }, async base => {
    assert.equal((await fetch(base+'/api/animeko/v2/subjects/list?type=DOING',{headers})).status,200);
    assert.equal(calls.length,2);
    assert.match(calls[1],/^https:\/\/danmaku-global.myani.org\//);
    assert.equal((await fetch(base+'/api/animeko/v1/me',{headers})).status,200);
    assert.equal(calls.length,3);
    assert.match(calls[2],/^https:\/\/danmaku-global.myani.org\//);
  });
});
test('read failover covers invalid JSON and upstream 502 but never hides auth errors', async () => {
  for (const failure of [() => new Response('broken',{headers:{'Content-Type':'application/json'}}), () => new Response(null,{status:502})]) {
    let count=0;
    await withRelay(async () => ++count === 1 ? failure() : Response.json({items:[]}), async base => {
      assert.equal((await fetch(base+'/api/animeko/v2/subjects/list',{headers})).status,200);
      assert.equal(count,2);
    });
  }
  let count=0;
  await withRelay(async () => {count++;return new Response(null,{status:401});}, async base => {
    assert.equal((await fetch(base+'/api/animeko/v1/me',{headers})).status,401);
    assert.equal(count,1);
  });
});
test('mutation connection loss sends exactly once; empty-op history may fail over', async () => {
  let count=0;
  await withRelay(async () => {count++;throw new Error('connection lost');}, async base => {
    assert.equal((await fetch(base+'/api/animeko/v2/subjects/1',{method:'PATCH',headers,body:'{"collectionType":"DOING"}'})).status,502);
    assert.equal(count,1);
  });
  count=0;
  await withRelay(async (_,options) => {assert.deepEqual(JSON.parse(options.body),{ops:[]}); if(++count===1) throw new Error('connection lost'); return Response.json({upserts:[],deletes:[]});}, async base => {
    assert.equal((await fetch(base+'/api/animeko/v2/playback-histories/sync',{method:'POST',headers,body:'{"ops":[]}'})).status,200);
    assert.equal(count,2);
  });
});
test('exploration uses authenticated home recommendations and public airing schedule', async () => {
  const calls=[];
  await withRelay(async (url,options) => {calls.push({url,options});return Response.json({items:[],total:0});}, async base => {
    assert.equal((await fetch(base+'/api/animeko/v2/home/recommendations?offset=30&limit=30',{headers})).status,200);
    assert.match(calls.at(-1).url,/\/v2\/home\/recommendations\?offset=30&limit=30$/);
    assert.equal((await fetch(base+'/api/animeko/v2/home/recommendations')).status,401);
    assert.equal((await fetch(base+'/api/animeko/v1/schedule/airing?today=2026-10-12&timeZone=Asia%2FShanghai')).status,200);
    assert.equal(calls.at(-1).options.headers.Authorization,undefined);
    assert.equal((await fetch(base+'/api/animeko/v1/schedule/airing',{method:'POST',headers})).status,404);
    assert.equal(calls.length,2);
  });
});
