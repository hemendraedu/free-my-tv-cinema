/* Preview lifecycle. Only the official iframe API is used; no iframe DOM access. */
var p, finished = false, announced = false, previousTime = 0, timer;
function captionsOff() {
    try {
        // Availability differs between player versions. Burned-in text is part of the video.
        if (p.setOption) p.setOption('captions', 'track', {});
        if (p.unloadModule) p.unloadModule('captions');
    } catch (e) {}
}
function finishPreview() {
    if (finished) return;
    finished = true;
    clearInterval(timer);
    console.log('CINEMA_TRAILER_ENDED');
    if (p && p.stopVideo) p.stopVideo();
}
function samplePlayback() {
    if (finished || !p || !p.getCurrentTime) return;
    var time = p.getCurrentTime(), duration = p.getDuration();
    // Finish before the closing section, where creators commonly add end cards.
    var end = duration > 45 ? Math.min(90, duration - 20) : duration - 2;
    if (duration > 3 && time >= end) { finishPreview(); return; }
    if (p.getPlayerState() === 1 && time > previousTime + 0.05) {
        console.log('CINEMA_TRAILER_PROGRESS');
        if (!announced && time >= 0.5) {
            announced = true;
            console.log('CINEMA_TRAILER_PLAYING');
        }
    }
    previousTime = time;
}
function onYouTubeIframeAPIReady() {
    p = new YT.Player('player', {
        videoId: trailerId,
        playerVars: {autoplay:1, playsinline:1, controls:0, fs:0, rel:0, cc_load_policy:0, origin:trailerOrigin},
        events: {
            onReady:function(e) { captionsOff(); e.target.unMute(); e.target.playVideo(); timer=setInterval(samplePlayback,250); },
            onApiChange:captionsOff,
            onStateChange:function(e) { if(e.data === 1) captionsOff(); if(e.data === 0) finishPreview(); },
            onError:function(e) { console.log('CINEMA_TRAILER_ERROR ' + e.data); },
            onAutoplayBlocked:function() { console.log('CINEMA_TRAILER_ERROR autoplay'); }
        }
    });
}
