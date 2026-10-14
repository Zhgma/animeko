import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,readdirSync} from 'node:fs';
import {onlineRead} from '../src/wasmJsMain/resources/online-media.mjs';

test('browser sources have no host-relative API or loopback transport dependency',()=>{
  const root=new URL('../src/wasmJsMain/',import.meta.url);
  function inspect(dir) { for(const entry of readdirSync(dir,{withFileTypes:true})) {
    if(['vendor','samples'].includes(entry.name))continue;
    const file=new URL(entry.name+(entry.isDirectory()?'/':''),dir);
    if(entry.isDirectory()) inspect(file);
    else if(/\.(kt|js|mjs)$/.test(entry.name)) {
      const source=readFileSync(file,'utf8');
      assert.doesNotMatch(source,/['"]\/api\//,file.pathname);
      assert.doesNotMatch(source,/https?:\/\/(?:localhost|127\.0\.0\.1)(?::\d+)?\/api/,file.pathname);
    }
  }}
  inspect(root);
  assert.doesNotMatch(readFileSync(new URL('../serve.mjs',import.meta.url),'utf8'),/create(?:OAuth|Bangumi|Animeko|Online)Relay/);
});

test('website source is fetched through the HTTPS gateway anonymously',async(t)=>{
  const calls=[];
  t.mock.method(globalThis,'fetch',async(url,init)=>{calls.push({url,init});return new Response(JSON.stringify({text:'<a>episode</a>',url:'https://source.example/episodes'}),{status:200});});
  assert.deepEqual(await onlineRead('https://source.example/episodes'),{text:'<a>episode</a>',url:'https://source.example/episodes'});
  assert.equal(calls[0].url,'https://43.131.255.74:8443/api/web/online/page?url=https%3A%2F%2Fsource.example%2Fepisodes');
  assert.equal(calls[0].init.mode,'cors'); assert.equal(calls[0].init.credentials,'omit');
  assert.equal(calls[0].init.headers,undefined);
  await assert.rejects(()=>onlineRead('https://user:password@source.example/'),/HTTPS/);
  await assert.rejects(()=>onlineRead('http://source.example/'),/HTTPS/);
  assert.equal(calls.length,1);
});

test('gateway responses are bounded and failed reads do not silently change transport',async(t)=>{
  let calls=0;
  t.mock.method(globalThis,'fetch',async()=>{calls++;throw new TypeError('Failed to fetch');});
  await assert.rejects(()=>onlineRead('https://source.example/'),/远程网站读取 API/);
  assert.equal(calls,1);
  t.mock.method(globalThis,'fetch',async()=>new Response('x'.repeat(12582913)));
  await assert.rejects(()=>onlineRead('https://source.example/'),/12 MiB/);
});
