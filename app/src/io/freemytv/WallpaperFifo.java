package io.freemytv;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/** Download order is stored as file timestamps; reading an image never changes its position. */
final class WallpaperFifo {
    static final int MAX = 5;
    private WallpaperFifo() {}

    static File[] pictures(File dir) {
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".jpg"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        return files;
    }

    static void trim(File dir, int keep) throws IOException {
        File[] files = pictures(dir);
        for (int i = 0; i < files.length - keep; i++) {
            if (!files[i].delete()) throw new IOException("Cannot evict old wallpaper");
        }
    }

    static long nextStamp(File dir) {
        long stamp = System.currentTimeMillis();
        for (File f : pictures(dir)) stamp = Math.max(stamp, f.lastModified() + 1);
        return stamp;
    }
}
