package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Daily scenic wallpaper feed. All work runs on CinemaView's dedicated background worker. */
final class DailyScenery {
    private static final String HOST = "https://www.bing.com";
    private static final String FEED = HOST + "/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-IN";
    private static final int MAX_IMAGE = 12 * 1024 * 1024;
    private final SharedPreferences prefs;
    private final File dir;
    private final int targetWidth;
    private List<JSONObject> entries = new ArrayList<>();
    private int index = -1;
    private long retryAfter;

    static final class Scene {
        final Bitmap bitmap;
        final String title, credit;
        Scene(Bitmap bitmap, String title, String credit) {
            this.bitmap = bitmap; this.title = title; this.credit = credit;
        }
    }

    DailyScenery(Context context) {
        prefs = context.getSharedPreferences("cinema_daily_scenery", Context.MODE_PRIVATE);
        dir = new File(context.getCacheDir(), "cinema-daily");
        targetWidth = Math.min(3840, context.getResources().getDisplayMetrics().widthPixels);
    }

    /** Date-based refresh also works after sleep or a timezone change. A failed fetch retries after one hour. */
    private void refresh() {
        if (entries.isEmpty()) entries = parse(prefs.getString("feed", "[]"));
        long now = System.currentTimeMillis();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(now));
        if (today.equals(prefs.getString("day", "")) || now < retryAfter) return;
        retryAfter = now + 3600_000;
        try {
            JSONObject response = new JSONObject(new String(fetch(FEED, 256 * 1024), StandardCharsets.UTF_8));
            List<JSONObject> fresh = parse(response.getJSONArray("images").toString());
            if (fresh.isEmpty()) throw new IOException("No scenic wallpapers available");
            // Keep a few previous entries and their credits for offline playback while new images download.
            Set<String> ids = new HashSet<>(); for (JSONObject e : fresh) ids.add(e.optString("urlbase"));
            for (JSONObject old : entries) if (fresh.size() < 12 && ids.add(old.optString("urlbase"))) fresh.add(old);
            entries = fresh; index = -1;
            JSONArray saved = new JSONArray(); for (JSONObject e : entries) saved.put(e);
            prefs.edit().putString("feed", saved.toString()).putString("day", today).apply();
            prune();
            AppLog.w("Daily scenery: refreshed " + entries.size() + " scenic wallpapers");
        } catch (Exception e) { AppLog.w("Daily scenery: keeping offline gallery; refresh will retry"); }
    }

    /** Only wallpaper-enabled photographs with scenic descriptions, never promotions or arbitrary feed URLs. */
    private static List<JSONObject> parse(String json) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < array.length() && out.size() < 12; i++) {
                JSONObject e = array.getJSONObject(i);
                String base = e.optString("urlbase"), description = e.optString("copyright").toLowerCase(Locale.ROOT);
                if (!e.optBoolean("wp") || !base.matches("/th\\?id=OHR\\.[A-Za-z0-9_-]+") || !seen.add(base)) continue;
                if (!description.matches("(?s).*\\b(forest|mountains?|lakes?|parks?|rivers?|valleys?|desert|dunes?|canyon|coast|beach|aurora|waterfalls?|glacier|islands?|ocean|sea|cliffs?|landscape|peaks?|highlands|hills|meadow|fjord|reef)\\b.*")) continue;
                if (description.matches("(?s).*\\b(promotion|sponsored|advertisement|movie|film|xbox|disney)\\b.*")) continue;
                out.add(e);
            }
        } catch (Exception e) { out.clear(); }
        return out;
    }

    Scene next(boolean advance) {
        try { WallpaperFifo.trim(dir, WallpaperFifo.MAX); }
        catch (IOException e) { AppLog.w("Daily scenery: cache cleanup unavailable"); }
        refresh();
        if (entries.isEmpty()) return null;
        if (index < 0) index = 0;
        else if (advance) index = (index + 1) % entries.size();
        JSONObject entry = entries.get(index);
        try {
            File file = file(entry);
            if (!file.isFile()) {
                byte[] bytes = fetch(HOST + entry.getString("urlbase") + "_UHD.jpg", MAX_IMAGE);
                Bitmap bitmap = decode(bytes);
                if (bitmap == null) throw new IOException("Invalid wallpaper");
                try { save(file, bytes); } catch (IOException e) { AppLog.w("Daily scenery: cache unavailable"); }
                AppLog.w("Daily scenery: downloaded wallpaper, displayed at " + bitmap.getWidth() + "x" + bitmap.getHeight());
                return scene(entry, bitmap);
            }
            Bitmap bitmap = decode(read(file, MAX_IMAGE));
            if (bitmap != null) { AppLog.w("Daily scenery: loaded cached wallpaper"); return scene(entry, bitmap); }
            file.delete(); // A corrupt private cache entry is fetched again on its next turn.
        } catch (Exception | OutOfMemoryError e) { AppLog.w("Daily scenery: using cached or bundled wallpaper"); }
        for (int offset = 1; offset < entries.size(); offset++) {
            int candidate = (index + offset) % entries.size();
            JSONObject cached = entries.get(candidate);
            try {
                File f = file(cached); if (!f.isFile()) continue;
                Bitmap bitmap = decode(read(f, MAX_IMAGE));
                if (bitmap != null) { index = candidate; return scene(cached, bitmap); }
            } catch (Exception | OutOfMemoryError ignored) { }
        }
        return null;
    }

    private Scene scene(JSONObject e, Bitmap bitmap) {
        String date = e.optString("enddate").replaceFirst("^(\\d{4})(\\d{2})(\\d{2})$", "$1-$2-$3");
        return new Scene(bitmap, e.optString("title", "Daily landscape"), e.optString("copyright") + "\nBing daily wallpaper · " + date);
    }
    private File file(JSONObject e) {
        // Validated provider IDs contain only safe filename characters and cannot escape this private directory.
        return new File(dir, e.optString("urlbase").substring("/th?id=OHR.".length()) + ".jpg");
    }
    private void prune() {
        try { WallpaperFifo.trim(dir, WallpaperFifo.MAX); }
        catch (IOException e) { AppLog.w("Daily scenery: cache cleanup unavailable"); }
    }
    private Bitmap decode(byte[] bytes) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth < 1920 || options.outHeight < 1080 || options.outWidth > 10000
                || options.outHeight > 10000 || (long) options.outWidth * options.outHeight > 32_000_000) throw new IOException("Wallpaper dimensions");
        options.inJustDecodeBounds = false; options.inSampleSize = 1;
        while (options.outWidth / (options.inSampleSize * 2) >= targetWidth) options.inSampleSize *= 2;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
    private void save(File file, byte[] bytes) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cache directory");
        long stamp = WallpaperFifo.nextStamp(dir);
        // Download and validation already succeeded in memory. Make room before persisting a sixth photo.
        WallpaperFifo.trim(dir, WallpaperFifo.MAX - 1);
        AtomicFile atomic = new AtomicFile(file); FileOutputStream stream = null;
        try { stream = atomic.startWrite(); stream.write(bytes); atomic.finishWrite(stream); }
        catch (IOException e) { if (stream != null) atomic.failWrite(stream); throw e; }
        if (!file.setLastModified(stamp)) { file.delete(); throw new IOException("Cannot record wallpaper download order"); }
    }
    private static byte[] read(File file, int limit) throws IOException {
        try (InputStream in = new FileInputStream(file)) { return read(in, limit); }
    }
    private static byte[] read(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] chunk = new byte[16384]; int n;
        while ((n = in.read(chunk)) != -1) {
            if (Thread.currentThread().isInterrupted() || out.size() + n > limit) throw new IOException("Download cancelled or too large");
            out.write(chunk, 0, n);
        }
        return out.toByteArray();
    }
    private static byte[] fetch(String address, int limit) throws IOException {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol()) || !"www.bing.com".equals(url.getHost())) throw new IOException("Wallpaper host");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(10000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "OpenTheatre/1.0 (TV wallpaper)");
        try {
            if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > limit) throw new IOException("Wallpaper response");
            try (InputStream in = connection.getInputStream()) { return read(in, limit); }
        } finally { connection.disconnect(); }
    }
}
