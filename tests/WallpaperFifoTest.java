package io.freemytv;

import java.io.File;
import java.nio.file.Files;

public final class WallpaperFifoTest {
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("wallpaper-fifo-test").toFile();
        try {
            for (int i = 1; i <= 7; i++) {
                File f = new File(dir, i + ".jpg"); Files.write(f.toPath(), new byte[]{1});
                if (!f.setLastModified(1000L * i)) throw new AssertionError("Timestamp setup");
            }
            File unrelated = new File(dir, "metadata.json"); Files.write(unrelated.toPath(), new byte[]{1});
            WallpaperFifo.trim(dir, 5);
            if (WallpaperFifo.pictures(dir).length != 5 || new File(dir,"1.jpg").exists()
                    || new File(dir,"2.jpg").exists() || !unrelated.exists()) throw new AssertionError("Migration must remove oldest only");
            Files.readAllBytes(new File(dir,"3.jpg").toPath());
            WallpaperFifo.trim(dir, 4);
            if (new File(dir,"3.jpg").exists()) throw new AssertionError("Reading must not change FIFO order");
            File future = new File(dir,"7.jpg"); future.setLastModified(System.currentTimeMillis()+86400000L);
            if (WallpaperFifo.nextStamp(dir) <= future.lastModified()) throw new AssertionError("Clock rollback must preserve insertion order");
            File next = new File(dir,"8.jpg"); Files.write(next.toPath(),new byte[]{1});next.setLastModified(WallpaperFifo.nextStamp(dir));
            WallpaperFifo.trim(dir,5);
            if(WallpaperFifo.pictures(dir).length != 5 || !next.exists())throw new AssertionError("Five-photo cap");
            System.out.println("Wallpaper FIFO: migration, read stability, clock rollback and five-photo cap passed");
        } finally {
            for(File f : dir.listFiles()) Files.delete(f.toPath());
            Files.delete(dir.toPath());
        }
    }
}
