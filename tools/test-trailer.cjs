const fs=require('fs'),vm=require('vm'),assert=require('assert');
const src=fs.readFileSync('app/assets/trailer-player.js','utf8');
function fixture(){
 const logs=[];let options;const player={time:0,duration:120,state:1,captions:0,stops:0,getCurrentTime(){return this.time},getDuration(){return this.duration},getPlayerState(){return this.state},setOption(){this.captions++},unloadModule(){},stopVideo(){this.stops++},unMute(){},playVideo(){}};
 const c={console:{log:x=>logs.push(x)},trailerId:'12345678901',trailerOrigin:'https://io.freemytv',setInterval:()=>1,clearInterval:()=>{},YT:{Player:function(id,o){options=o;return player}}};
 vm.createContext(c);vm.runInContext(src,c);c.onYouTubeIframeAPIReady();options.events.onReady({target:player});return {c,logs,player,options};
}
let f=fixture();f.options.events.onStateChange({data:1});f.c.samplePlayback();assert(!f.logs.includes('CINEMA_TRAILER_PLAYING'),'state alone must not reveal blank video');
f.player.time=.75;f.c.samplePlayback();assert(f.logs.includes('CINEMA_TRAILER_PLAYING'));let n=f.logs.length;f.c.samplePlayback();assert.equal(f.logs.length,n,'stalled clock must not keep watchdog alive');
f.player.time=90;f.c.samplePlayback();f.c.samplePlayback();assert.equal(f.logs.filter(x=>x==='CINEMA_TRAILER_ENDED').length,1);assert.equal(f.player.stops,1);
f=fixture();f.player.duration=30;f.player.time=28;f.c.samplePlayback();assert(f.logs.includes('CINEMA_TRAILER_ENDED'),'short teaser ends before recommendations');
f=fixture();f.player.duration=60;f.player.time=40;f.c.samplePlayback();assert(f.logs.includes('CINEMA_TRAILER_ENDED'),'skip final 20 seconds for longer trailers');
f=fixture();f.options.events.onApiChange();assert(f.player.captions>=2);f.options.events.onError({data:150});assert(f.logs.includes('CINEMA_TRAILER_ERROR 150'));
f=fixture();f.options.events.onStateChange({data:0});assert.equal(f.player.stops,1);
console.log('Trailer lifecycle: progress gating, stalls, captions, short/long endings, errors and idempotence passed');
