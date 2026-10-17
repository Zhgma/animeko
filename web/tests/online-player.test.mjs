import test from 'node:test';
import assert from 'node:assert/strict';
import {createOnlineVideo} from '../src/wasmJsMain/resources/online-media.mjs';

class Element extends EventTarget {
  style={};children=[];attributes=new Map();paused=true;currentTime=0;duration=100;muted=false;playbackRate=1;
  append(child){this.children.push(child);}
  setAttribute(k,v){this.attributes.set(k,v);}
  getAttribute(k){return this.attributes.get(k);}
  removeAttribute(k){this.attributes.delete(k);}
  load(){this.playbackRate=this.defaultPlaybackRate||1;}
  pause(){this.paused=true;this.dispatchEvent(new Event('pause'));}
  play(){this.paused=false;this.onplaying?.();return Promise.resolve();}
  canPlayType(){return 'probably';}
}
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function setup(t,fetcher,playerConfig={}){
 const originals=Object.fromEntries(['document','localStorage','fetch','aniApi'].map(k=>[k,globalThis[k]]));
 const doc=new EventTarget();doc.createElement=()=>new Element();doc.fullscreenElement=null;
 globalThis.document=doc;globalThis.localStorage={getItem:()=>null};globalThis.fetch=fetcher;
 globalThis.aniApi={base:'https://api.example/api/web'};
 const states=[];const host=createOnlineVideo({playerConfig,source:'https://s.example/search',title:'show',episodes:[{url:'https://s.example/1',name:'1'},{url:'https://s.example/2',name:'2'}]},s=>states.push(JSON.parse(s)));
 t.after(()=>{host.aniDispose();Object.assign(globalThis,originals);});return {host,video:host.children[0],states};
}
const response=()=>({ok:true,json:async()=>({url:'/api/web/online/media?ticket=test',type:'video'})});

test('Compose video commands operate the same surface and dispose playback',async t=>{
 const {host,video,states}=setup(t,async()=>response());await tick();
 assert.equal(video.paused,false);host.aniCommand('seek',35);assert.equal(video.currentTime,35);
 host.aniCommand('rate',1.5);assert.equal(video.playbackRate,1.5);host.aniCommand('mute',0);assert.equal(video.muted,true);
 host.aniCommand('toggle',0);assert.equal(video.paused,true);
 host.aniCommand('episode',1);await tick();assert.equal(states.at(-1).position,1);assert.equal(video.playbackRate,1.5);
 const n=states.length;host.aniDispose();assert.equal(video.paused,true);host.aniCommand('toggle',0);assert.equal(states.length,n);
});
test('switching episode cancels stale extraction and closing stops a pending request',async t=>{
 const requests=[];const {host,video}=setup(t,(_url,opts)=>new Promise(resolve=>requests.push({opts,resolve})));
 await tick();host.aniCommand('episode',1);await tick();assert.equal(requests[0].opts.signal.aborted,true);
 requests[0].resolve(response());await tick();assert.equal(video.src,undefined);
 host.aniDispose();assert.equal(requests[1].opts.signal.aborted,true);requests[1].resolve(response());await tick();assert.equal(video.src,undefined);
});
test('API errors are visible and can be retried without reconstructing the player',async t=>{
 let count=0;const {host,video,states}=setup(t,async()=>++count===1?{ok:false,json:async()=>({error:'source unavailable'})}:response());
 await tick();assert.equal(states.at(-1).failed,true);assert.equal(states.at(-1).message,'source unavailable');
 host.aniCommand('retry',0);await tick();assert.equal(states.at(-1).failed,false);assert.equal(video.paused,false);
});

test('App speed range and remembered speed apply across episodes',async t=>{
 const {host,video,states}=setup(t,async()=>response(),{playbackSpeed:2.25,minPlaybackSpeed:1,maxPlaybackSpeed:4,rememberPlaybackSpeed:true});await tick();
 assert.equal(video.playbackRate,2.25);host.aniCommand('rate',3.75);
 assert.equal(states.at(-1).saveRate,3.75);host.aniCommand('rate',.5);assert.equal(video.playbackRate,3.75);
 host.aniCommand('episode',1);await tick();assert.equal(video.playbackRate,3.75);
});
test('non-remembered rate is session-only and does not request persistence',async t=>{
 const {host,video,states}=setup(t,async()=>response(),{playbackSpeed:1.25,rememberPlaybackSpeed:false});await tick();
 host.aniCommand('rate',2);assert.equal(video.playbackRate,2);assert.equal(states.at(-1).saveRate,null);
});
test('App auto-play-next option controls the ended event',async t=>{
 const {video,states}=setup(t,async()=>response(),{autoPlayNext:false});await tick();video.onended();await tick();assert.equal(states.at(-1).position,0);
});
test('enabled auto-play advances to the next episode',async t=>{
 const {video,states}=setup(t,async()=>response(),{autoPlayNext:true});await tick();video.onended();await tick();assert.equal(states.at(-1).position,1);
 video.onended();await tick();assert.equal(states.at(-1).position,1);
});
test('long press restores prior speed without saving temporary speed',async t=>{
 const {video,states}=setup(t,async()=>response(),{playbackSpeed:1.25,fastForwardSpeed:2.5});await tick();
 t.mock.timers.enable({apis:['setTimeout']});video.onpointerdown({button:0});t.mock.timers.tick(350);
 assert.equal(video.playbackRate,2.5);assert.equal(states.at(-1).saveRate,null);
 video.onpointerup();assert.equal(video.playbackRate,1.25);video.onclick();assert.equal(video.paused,false);
});
test('floating fullscreen follows App visibility modes',async t=>{
 const {host}=setup(t,async()=>response(),{fullscreenSwitchMode:'ONLY_IN_CONTROLLER'});await tick();assert.equal(host.children[1].hidden,true);
});

test('long press defaults and caps at three independently of normal speed range',async t=>{
 const {video}=setup(t,async()=>response(),{playbackSpeed:1.25,maxPlaybackSpeed:1.5});await tick();
 t.mock.timers.enable({apis:['setTimeout']});video.onpointerdown({button:0});t.mock.timers.tick(350);
 assert.equal(video.playbackRate,3);video.onpointerup();assert.equal(video.playbackRate,1.25);
});
test('imported excessive hold speed is capped at three',async t=>{
 const {video}=setup(t,async()=>response(),{fastForwardSpeed:4});await tick();
 t.mock.timers.enable({apis:['setTimeout']});video.onpointerdown({button:0});t.mock.timers.tick(350);
 assert.equal(video.playbackRate,3);video.onpointercancel();assert.equal(video.playbackRate,1);
});
