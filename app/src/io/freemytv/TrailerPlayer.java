package io.freemytv;

import android.app.Activity;
import android.webkit.*;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Official YouTube embed; no extraction or downloads. Only one visible, audible player at a time. */
final class TrailerPlayer {
    private final Activity activity;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Map<String,String> ids=new HashMap<>();
    private WebView web;
    private int generation;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable watchdog;
    TrailerPlayer(Activity a){activity=a;}
    void play(Recs.Pick pick,FrameLayout host,Runnable playing,Runnable ended,Runnable failed){
        stop();final int ticket=generation;
        worker.execute(()->{
            String id=null;try{id=find(pick);}catch(Exception e){AppLog.w("Trailer lookup unavailable");}
            final String video=id;
            activity.runOnUiThread(()->{if(ticket!=generation)return;if(video==null){AppLog.w("Trailer unavailable: no candidate");failed.run();return;}AppLog.w("Trailer candidate "+pick.kind+":"+pick.id+" video="+video);embed(video,host,playing,ended,failed,ticket);});
        });
    }
    private String find(Recs.Pick p)throws Exception{
        if(p.id<=0||!("movie".equals(p.kind)||"tv".equals(p.kind)))return null;
        String lookup=p.kind+":"+p.id;if(ids.containsKey(lookup))return ids.get(lookup);
        String key=Recs.key();if(key.isEmpty())return null;boolean bearer=key.startsWith("eyJ");
        String url="https://api.tmdb.org/3/"+p.kind+"/"+p.id+"/videos?language=en-US";
        if(!bearer)url+="&api_key="+URLEncoder.encode(key,"UTF-8");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
        if(bearer)c.setRequestProperty("Authorization","Bearer "+key);
        try{
            if(c.getResponseCode()!=200)return null;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>1024*1024)throw new IOException("Size");bytes.write(b,0,n);}}
            JSONArray list=new JSONObject(bytes.toString("UTF-8")).getJSONArray("results");String best=null;int score=-1;
            for(int i=0;i<list.length();i++){JSONObject v=list.getJSONObject(i);String id=v.optString("key"),type=v.optString("type");
                // Reproduced on the Sony Android TV: this Derry Short reports advancing playback
                // but renders a black picture. Keep artwork until TMDB offers another candidate.
                if("iOjzJsHS0M0".equals(id))continue;
                if(!"YouTube".equals(v.optString("site"))||!id.matches("[A-Za-z0-9_-]{11}")||!("Trailer".equals(type)||"Teaser".equals(type)))continue;
                int rank=(v.optBoolean("official")?10:0)+("Trailer".equals(type)?2:0);
                if(rank>score){score=rank;best=id;}}
            if(ids.size()>24)ids.clear();ids.put(lookup,best);return best;
        }finally{c.disconnect();}
    }
    private void embed(String id,FrameLayout host,Runnable playing,Runnable ended,Runnable failed,int ticket){
        try{
            // Chromium owns audio focus; a second native request would interrupt our own player.
            web=new WebView(activity);web.setBackgroundColor(android.graphics.Color.BLACK);
            web.setFocusable(false);web.setFocusableInTouchMode(false);web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            web.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return r.isForMainFrame();}
                @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){if(ticket==generation){host.setAlpha(0);stop();failed.run();}return true;}
            });
            watchdog=()->{if(ticket==generation){AppLog.w("Trailer unavailable: no playback progress");host.setAlpha(0);stop();failed.run();}};
            handler.postDelayed(watchdog,15000);
            web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){
                if(ticket!=generation)return true;String s=m.message();
                if("CINEMA_TRAILER_PROGRESS".equals(s)){if(watchdog!=null){handler.removeCallbacks(watchdog);handler.postDelayed(watchdog,12000);}}
                else if("CINEMA_TRAILER_PLAYING".equals(s)){AppLog.w("Trailer playing with audio and advancing time");playing.run();}
                else if("CINEMA_TRAILER_ENDED".equals(s)){AppLog.w("Trailer preview complete");host.setAlpha(0);stop();ended.run();}
                else if(s.startsWith("CINEMA_TRAILER_ERROR")){AppLog.w("Trailer embed unavailable: "+s.replaceAll("[^A-Za-z0-9 _-]", ""));host.setAlpha(0);stop();failed.run();}
                return true;
            }});
            host.addView(web,new FrameLayout.LayoutParams(-1,-1));
            String origin="https://"+activity.getPackageName();
            String script;
            try(InputStream in=activity.getAssets().open("trailer-player.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);script=out.toString("UTF-8");
            }
            String html="<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'><meta name='referrer' content='strict-origin-when-cross-origin'>"
                +"<style>html,body,#player{margin:0;width:100%;height:100%;background:black;overflow:hidden}</style></head><body><div id='player'></div>"
                +"<script>var trailerId='"+id+"', trailerOrigin='"+origin+"';"+script+"</script><script src='https://www.youtube.com/iframe_api'></script></body></html>";
            web.loadDataWithBaseURL(origin+"/",html,"text/html","UTF-8",null);
        }catch(Exception e){AppLog.w("Trailer player unavailable on this TV");stop();failed.run();}
    }
    void stop(){generation++;if(watchdog!=null){handler.removeCallbacks(watchdog);watchdog=null;}if(web!=null){WebView old=web;web=null;old.stopLoading();old.loadUrl("about:blank");old.onPause();if(old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);old.destroy();}}
    void destroy(){stop();worker.shutdownNow();}
}
