import test from 'node:test';
import assert from 'node:assert/strict';
import {videoCandidates,extractOnlineVideo} from '../src/wasmJsMain/resources/video-extraction.mjs';
import {rewritePlaylist,createVideoTickets} from '../online-video.mjs';
test('nested player JSON and escaped/encrypted public media addresses',()=>{
 const root='https://site.example/watch/1';
 const body='var player_aaaa='+JSON.stringify({vod_data:{name:'x'},url:'https://cdn.example:8088/first.mp4',url_next:'https://cdn.example/next.mp4'})+';';
 assert.equal(videoCandidates(body,root).videos[0],'https://cdn.example:8088/first.mp4');
 for(const encrypt of [1,2]){
  const raw=encodeURIComponent('https://cdn.example/a.m3u8?k=x&v=1');
  assert.equal(videoCandidates('let player_b = '+JSON.stringify({encrypt,url:encrypt===2?btoa(raw):raw})+';',root).videos[0],'https://cdn.example/a.m3u8?k=x&v=1');
 }
 assert.deepEqual(videoCandidates('<video src="javascript:bad()"></video>',root).videos,[]);
});
test('video/source tags and encoded parser URLs are discovered without script execution',()=>{
 assert.equal(videoCandidates('<video><source src="/v.mp4?a=1&amp;b=2"></video>','https://site.example').videos[0],'https://site.example/v.mp4?a=1&b=2');
 assert.equal(videoCandidates('<iframe src="https://parser.example/?url=https%3A%2F%2Fcdn.example%2Fa.m3u8"></iframe>','https://site.example').videos[0],'https://cdn.example/a.m3u8');
});
test('bounded nested extraction does not loop and propagates cancellation',async()=>{
 const pages={'https://s.example/':'<iframe src="/p"></iframe>','https://s.example/p':'<video src="https://c.example/1.mp4"></video>'};
 const result=await extractOnlineVideo('https://s.example/',async url=>({url,text:pages[url]}));assert.equal(result.url,'https://c.example/1.mp4');
 let calls=0;await assert.rejects(extractOnlineVideo('https://s.example/',async url=>{calls++;return {url,text:'<iframe src="/"></iframe>'};}));assert.equal(calls,1);
 await assert.rejects(extractOnlineVideo('https://s.example/',()=>{throw Error('must not run');},{signal:AbortSignal.abort()}));
});
test('HLS variants, segments, encryption keys and initialization fragments use signed child URLs',()=>{
 const output=rewritePlaylist('#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\n#EXT-X-MAP:URI="init.mp4"\nlow/list.m3u8\nhttps://cdn.example/segment.ts','https://site.example/root/master.m3u8',url=>'signed:'+url);
 assert.match(output,/URI="signed:https:\/\/site.example\/root\/key.bin"/);assert.match(output,/signed:https:\/\/site.example\/root\/low\/list.m3u8/);
 assert.throws(()=>rewritePlaylist('#EXTM3U\nfile:///secret','https://site.example',x=>x));
});
test('media tickets reject tampering and expiry; child tickets retain original lifetime',()=>{
 let time=100;const tickets=createVideoTickets({secret:Buffer.alloc(32,5),now:()=>time});
 const ticket=tickets.issue('https://cdn.example/a.mp4',{Referer:'https://site.example'},200);
 assert.equal(tickets.read(ticket).headers.Referer,'https://site.example');
 assert.throws(()=>tickets.read(ticket.replace(/^./,'x')));time=201;assert.throws(()=>tickets.read(ticket));
});

import {createServer} from 'node:http';
import {Readable} from 'node:stream';
import {serveVideo} from '../online-video.mjs';
test('media relay streams Range responses and rewrites HLS with expiring tickets',async t=>{
 const tickets=createVideoTickets();let lastRange;
 const open=async(url,headers,range)=>{
  lastRange=range;const hls=url.endsWith('m3u8');
  const response=Readable.from([Buffer.from(hls?'#EXTM3U\n#EXTINF:3,\none.ts':'video-bytes')]);
  response.statusCode=hls?200:206;response.headers={'content-type':hls?'application/vnd.apple.mpegurl':'video/mp4',...(hls?{}:{'content-range':'bytes 0-10/100','content-length':'11'})};return {response,url};
 };
 const server=createServer((req,res)=>serveVideo(req,res,new URL(req.url,'http://local').searchParams.get('ticket'),tickets,{open}));
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));
 const root='http://127.0.0.1:'+server.address().port;
 let response=await fetch(root+'/?ticket='+tickets.issue('https://cdn.example/a.mp4'),{headers:{Range:'bytes=0-10'}});
 assert.equal(response.status,206);assert.equal(response.headers.get('content-range'),'bytes 0-10/100');assert.equal(await response.text(),'video-bytes');assert.equal(lastRange,'bytes=0-10');
 response=await fetch(root+'/?ticket='+tickets.issue('https://cdn.example/master.m3u8'));
 const playlist=await response.text();const child=playlist.split('\n').at(-1);assert.equal(tickets.read(new URL(child,root).searchParams.get('ticket')).url,'https://cdn.example/one.ts');
});

test('a following episode or unrelated advertisement is never mistaken for the current video',()=>{
 const data={url:'not-a-video',url_next:'https://cdn.example/episode2.mp4'};
 const found=videoCandidates('var player_aaaa='+JSON.stringify(data)+'; const ad="https://cdn.example/ad.mp4";','https://site.example');
 assert.equal(found.videos.length,0);
 assert.equal(videoCandidates('new DPlayer({video:{url:"https://cdn.example/current.m3u8"}})','https://site.example').videos[0],'https://cdn.example/current.m3u8');
});
