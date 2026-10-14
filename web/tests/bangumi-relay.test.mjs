import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createBangumiRelay } from '../bangumi-relay.mjs';

test('episode stills use the bounded anonymous Animeko image transport', async () => {
  let calls = 0;
  await withServer(async (url, options) => {
    calls++;
    assert.equal(url, 'https://static.myani.org/episodes/1738523/still/thumb');
    assert.equal(options.headers.Authorization, undefined);
    assert.equal(options.headers.Cookie, undefined);
    return new Response(new Uint8Array([1]), {headers: {'Content-Type': 'image/webp'}});
  }, async base => {
    const image = path => base + '/api/bangumi/image?url=' + encodeURIComponent('https://static.myani.org' + path);
    assert.equal((await fetch(image('/episodes/1738523/still/thumb'), {headers: {Authorization: 'Bearer ignored', Cookie: 'ignored'}})).status, 200);
    for (const path of ['/episodes/1738523/private', '/episodes/0/still/thumb', '/episodes/1/still/other']) {
      assert.equal((await fetch(image(path))).status, 400);
    }
    assert.equal(calls, 1);
  });
});

test('Animeko CDN covers share bounded anonymous image transport', async () => {
  await withServer(async (url, options) => {
    assert.equal(url,'https://static.myani.org/subjects/430699/cover/large');
    assert.equal(options.headers.Authorization,undefined);
    return new Response(new Uint8Array([1]),{headers:{'Content-Type':'image/webp'}});
  }, async base => {
    const r = await fetch(base+'/api/bangumi/image?url='+encodeURIComponent('https://static.myani.org/subjects/430699/cover/large'),{headers:{Authorization:'Bearer ignored'}});
    assert.equal(r.status,200);assert.equal(r.headers.get('content-type'),'image/webp');
  });
});

test('collection writes preserve minimal fields, require a token and accept empty 204 responses', async () => {
  const calls = [];
  await withServer(async (url, options) => {
    calls.push({url,options});
    return new Response(null, {status:204});
  }, async base => {
    const path = base + '/api/bangumi/v0/users/-/collections/355555';
    assert.equal((await fetch(path, {method:'POST',headers:{'Content-Type':'application/json'},body:'{"type":3}'})).status,401);
    for (const fields of [{type:0},{rate:11},{ep_status:-1},{private:'yes'},{url:'https://other.example'},{comment:123},{}]) {
      assert.equal((await fetch(path, {method:'PATCH',headers:{Authorization:'Bearer test-bgm','Content-Type':'application/json'},body:JSON.stringify(fields)})).status,400);
    }
    const result = await fetch(path, {method:'PATCH',headers:{Authorization:'Bearer test-bgm','Content-Type':'application/json',Cookie:'not-forwarded'},body:'{"ep_status":12}'});
    assert.equal(result.status,204); assert.equal(calls.length,1);
    assert.equal(calls[0].url,'https://api.bgm.tv/v0/users/-/collections/355555');
    assert.deepEqual(JSON.parse(calls[0].options.body),{ep_status:12});
    assert.equal(calls[0].options.headers.Cookie,undefined);
    assert.equal((await fetch(base+'/api/bangumi/v0/users/another/collections/1',{method:'PATCH',headers:{Authorization:'Bearer test-bgm','Content-Type':'application/json'},body:'{"type":3}'})).status,405);
  });
});

test('detail relations and episode pagination stay on the fixed Bangumi API origin', async () => {
  await withServer(async url => {
    assert.ok(['https://api.bgm.tv/v0/subjects/355555/subjects','https://api.bgm.tv/v0/episodes?subject_id=1&limit=100&offset=100'].includes(url));
    return Response.json({data:[]});
  },async base => {
    assert.equal((await fetch(base+'/api/bangumi/v0/subjects/355555/subjects')).status,200);
    assert.equal((await fetch(base+'/api/bangumi/v0/episodes?subject_id=1&limit=100&offset=100')).status,200);
  });
});

test('public comments use only the fixed next.bgm.tv route and never receive credentials', async () => {
  await withServer(async (url,options) => {
    assert.equal(url,'https://next.bgm.tv/p1/subjects/355555/comments?limit=20&offset=20');
    assert.equal(options.headers.Authorization,undefined);
    assert.equal(options.headers.Cookie,undefined);
    return Response.json({total:21,data:[]});
  },async base => {
    assert.equal((await fetch(base+'/api/bangumi/p1/subjects/355555/comments?limit=20&offset=20',{headers:{Authorization:'Bearer private-token',Cookie:'private-cookie'}})).status,200);
    assert.equal((await fetch(base+'/api/bangumi/p1/me')).status,404);
  });
});

async function withServer(fetchImpl, run) {
  const handler = createBangumiRelay({ fetchImpl });
  const server = createServer(async (req, res) => { if (!await handler(req, res, new URL(req.url, 'http://localhost'))) res.writeHead(404).end(); });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await run('http://127.0.0.1:' + server.address().port); }
  finally { await new Promise(resolve => server.close(resolve)); }
}

test('collections preserve pagination and bearer but never browser cookies, origins or private caches', async () => {
  await withServer(async (url, options) => {
    assert.equal(url, 'https://api.bgm.tv/v0/users/reader/collections?subject_type=1&type=3&offset=30&limit=30');
    assert.equal(options.headers.Authorization, 'Bearer test-bgm');
    assert.equal(options.headers.Cookie, undefined); assert.equal(options.headers.Origin, undefined);
    assert.equal(options.redirect, 'error');
    return Response.json({ data: [{ subject_id: 1 }], total: 31 });
  }, async base => {
    const result = await fetch(base + '/api/bangumi/v0/users/reader/collections?subject_type=1&type=3&offset=30&limit=30', { headers: { Authorization: 'Bearer test-bgm', Origin: base, Cookie: 'ignored=secret' } });
    assert.equal(result.status, 200); assert.equal(result.headers.get('cache-control'), 'no-store');
    assert.equal((await result.json()).total, 31);
  });
});

test('relay rejects foreign origins, arbitrary routes, query destinations and write methods', async () => {
  let calls = 0;
  await withServer(async () => { calls++; throw new Error('unexpected'); }, async base => {
    assert.equal((await fetch(base + '/api/bangumi/v0/me', { headers: { Origin: 'https://other.example' } })).status, 403);
    assert.equal((await fetch(base + '/api/bangumi/v0/me', { headers: { 'Sec-Fetch-Site': 'cross-site' } })).status, 403);
    assert.equal((await fetch(base + '/api/bangumi/v0/me', { method: 'POST' })).status, 405);
    assert.equal((await fetch(base + '/api/bangumi/https://other.example')).status, 404);
    assert.equal((await fetch(base + '/api/bangumi/v0/me?url=https://other.example')).status, 400);
    for (const url of ['https://other.example/pic/a.jpg', 'http://lain.bgm.tv/pic/a.jpg', 'https://user:pass@lain.bgm.tv/pic/a.jpg', 'https://lain.bgm.tv:8080/pic/a.jpg', 'https://lain.bgm.tv/private', 'https://lain.bgm.tv/pic/a.jpg?url=https://other.example']) {
      assert.equal((await fetch(base + '/api/bangumi/image?url=' + encodeURIComponent(url))).status, 400);
    }
    assert.equal(calls, 0);
  });
});

test('covers return bounded image bytes with no authentication forwarded', async () => {
  await withServer(async (url, options) => {
    assert.equal(url, 'https://lain.bgm.tv/pic/cover/l/a.jpg');
    assert.equal(options.headers.Authorization, undefined); assert.equal(options.headers.Cookie, undefined);
    assert.equal(options.redirect, 'error');
    return new Response(new Uint8Array([1, 2, 3]), { headers: { 'Content-Type': 'image/jpeg' } });
  }, async base => {
    const result = await fetch(base + '/api/bangumi/image?url=https%3A%2F%2Flain.bgm.tv%2Fpic%2Fcover%2Fl%2Fa.jpg', { headers: { Authorization: 'Bearer test-bgm', Cookie: 'ignored=secret' } });
    assert.equal(result.status, 200); assert.equal(result.headers.get('content-type'), 'image/jpeg');
    assert.deepEqual([...new Uint8Array(await result.arrayBuffer())], [1, 2, 3]);
  });
});

test('Bangumi resized cover paths use the same fixed image host', async () => {
  await withServer(async url => {
    assert.equal(url, 'https://lain.bgm.tv/r/400/pic/cover/l/a.jpg');
    return new Response(new Uint8Array([1]), { headers: { 'Content-Type': 'image/jpeg' } });
  }, async base => {
    assert.equal((await fetch(base + '/api/bangumi/image?url=' + encodeURIComponent('https://lain.bgm.tv/r/400/pic/cover/l/a.jpg'))).status, 200);
  });
});

test('search relays JSON, while HTTP auth errors and network failures remain distinguishable', async () => {
  await withServer(async (url, options) => {
    if (url.endsWith('/me')) return new Response('', { status: 401 });
    if (url.endsWith('/subjects/1')) throw new Error('private upstream diagnostics');
    assert.equal(url, 'https://api.bgm.tv/v0/search/subjects?limit=24');
    assert.equal(options.method, 'POST'); assert.equal(JSON.parse(options.body).keyword, '漫画');
    return Response.json({ data: [] });
  }, async base => {
    assert.equal((await fetch(base + '/api/bangumi/v0/search/subjects?limit=24', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ keyword: '漫画' }) })).status, 200);
    assert.equal((await fetch(base + '/api/bangumi/v0/me')).status, 401);
    const failed = await fetch(base + '/api/bangumi/v0/subjects/1');
    assert.equal(failed.status, 502); assert.match((await failed.json()).error, /网络或代理/);
  });
});

test('relay rejects HTML masquerading as images and oversized image bodies', async () => {
  for (const response of [new Response('<html>blocked</html>', { headers: { 'Content-Type': 'text/html' } }), new Response(new Uint8Array(8 * 1024 * 1024 + 1), { headers: { 'Content-Type': 'image/png' } })]) {
    await withServer(async () => response, async base => {
      assert.equal((await fetch(base + '/api/bangumi/image?url=https%3A%2F%2Flain.bgm.tv%2Fpic%2Fa.png')).status, 502);
    });
  }
});
