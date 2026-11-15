package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in title lookup. Never sends playback progress, provider links, or account details. Memory cache only. */
final class ContinueArtwork {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable changed;
    private volatile boolean closed;
    private final Set<String> pending = new HashSet<>();
    private final Map<String,Long> failed = new HashMap<>();
    private final Map<String,Bitmap> cache = new LinkedHashMap<String,Bitmap>(16,.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<String,Bitmap> e) { return size()>12; }
    };
    ContinueArtwork(Runnable changed) { this.changed=changed; }
    Bitmap get(WatchNext.Item item) {
        if(closed||Recs.key().isEmpty())return null;
        String title=item.title;boolean series=item.episode!=null;
        String key=title+"|"+series;
        synchronized(cache) {
            Bitmap found=cache.get(key);if(found!=null)return found;
            Long last=failed.get(key);
            if(pending.contains(key)||(last!=null&&System.currentTimeMillis()-last<600_000))return null;
            pending.add(key);
        }
        IO.execute(()->{
            Bitmap bitmap=null;
            try { if(!closed)bitmap=lookup(title,series); }
            catch(Exception | OutOfMemoryError e){AppLog.w("Continue artwork: no matching image available");}
            synchronized(cache) {
                pending.remove(key);if(closed)return;
                if(bitmap!=null)cache.put(key,bitmap);else failed.put(key,System.currentTimeMillis());
            }
            main.post(()->{if(!closed)changed.run();});
        });
        return null;
    }
    void close(){closed=true;synchronized(cache){cache.clear();failed.clear();}}
    private static String normalized(String s) {
        return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");
    }
    private Bitmap lookup(String title,boolean series) throws Exception {
        String key=Recs.key();boolean bearer=key.startsWith("eyJ");
        String url="https://api.tmdb.org/3/search/multi?include_adult=false&language=en-US&query="+URLEncoder.encode(title,"UTF-8");
        if(!bearer)url+="&api_key="+URLEncoder.encode(key,"UTF-8");
        JSONArray results=new JSONObject(new String(fetch(url,bearer?key:null,1024*1024),"UTF-8")).getJSONArray("results");
        String wanted=normalized(title);JSONObject chosen=null;double best=-1;
        for(int i=0;i<results.length();i++) {
            JSONObject r=results.getJSONObject(i);String type=r.optString("media_type");
            if(!type.equals("tv")&&!type.equals("movie"))continue;
            if(series&&!type.equals("tv"))continue;
            if(r.optBoolean("adult"))continue;
            if(!wanted.equals(normalized(r.optString("title",r.optString("name"))))
                    &&!wanted.equals(normalized(r.optString("original_title",r.optString("original_name")))))continue;
            // Exact title matches only. For remakes, prefer the established title with the most audience votes.
            double score=r.optDouble("vote_count",0);
            if(score>best){chosen=r;best=score;}
        }
        if(chosen==null||closed)return null;
        String path=chosen.optString("backdrop_path");
        String size="w780";
        if(!path.matches("/[A-Za-z0-9]+\\.(jpg|png)")){path=chosen.optString("poster_path");size="w500";}
        if(!path.matches("/[A-Za-z0-9]+\\.(jpg|png)"))return null;
        byte[] image=fetch("https://image.tmdb.org/t/p/"+size+path,null,4*1024*1024);
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(image,0,image.length,opts);
        if(opts.outWidth<1||opts.outHeight<1||(long)opts.outWidth*opts.outHeight>4_000_000)return null;
        opts.inJustDecodeBounds=false;
        return BitmapFactory.decodeByteArray(image,0,image.length,opts);
    }
    private static byte[] fetch(String address,String token,int limit) throws IOException {
        URL url=new URL(address);String host=url.getHost();
        if(!url.getProtocol().equals("https")||!(host.equals("api.tmdb.org")||host.equals("image.tmdb.org")))throw new IOException("Artwork host");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
        if(token!=null&&host.equals("api.tmdb.org"))c.setRequestProperty("Authorization","Bearer "+token);
        try {
            if(c.getResponseCode()!=200||c.getContentLengthLong()>limit)throw new IOException("Artwork response");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] bytes=new byte[16384];int n;
                while((n=in.read(bytes))!=-1){if(out.size()+n>limit)throw new IOException("Artwork size");out.write(bytes,0,n);}
                return out.toByteArray();
            }
        } finally {c.disconnect();}
    }
}
