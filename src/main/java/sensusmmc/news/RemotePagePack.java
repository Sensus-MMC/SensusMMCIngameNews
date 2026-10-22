package sensusmmc.news;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import sensusmmc.news.page.Page;
import sensusmmc.news.page.PageManifest;
import sensusmmc.news.page.Tile;

public final class RemotePagePack {
    private static final int MAX_MANIFEST_BYTES = 8 * 1024 * 1024;
    private static final int MAX_TILE_BYTES = 8 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 64L * 1024L * 1024L;
    private static final long REFRESH_MILLIS = 300000L;

    private static final Object LOCK = new Object();
    private static final AtomicBoolean FETCHING = new AtomicBoolean();
    private static final Map<String, byte[]> TILES = new HashMap<String, byte[]>();
    private static volatile PageManifest manifest;
    private static volatile long nextRefresh;

    private RemotePagePack() {
    }

    public static PageManifest manifest() {
        return manifest;
    }

    public static byte[] tile(String file) {
        synchronized (LOCK) {
            return TILES.get(file);
        }
    }

    public static void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now < nextRefresh) return;
        nextRefresh = now + REFRESH_MILLIS;
        if (!FETCHING.compareAndSet(false, true)) return;
        Thread thread = new Thread(() -> {
            try {
                PageManifest loaded = null;
                String base = null;
                String[] bases = {
                    SensusNews.SITE_URL + "/tablet",
                    SensusNews.RAW_BASE + "/tablet"
                };
                for (String candidate : bases) {
                    try {
                        loaded = fetchManifest(candidate);
                        base = candidate;
                        break;
                    } catch (IOException error) {
                    }
                }
                if (loaded == null) throw new IOException("Tablet page pack unavailable");
                PageManifest current;
                synchronized (LOCK) {
                    current = manifest;
                }
                if (current != null && current.generated().equals(loaded.generated()) && !TILES.isEmpty()) {
                    return;
                }
                Map<String, byte[]> tiles = fetchTiles(loaded, base);
                synchronized (LOCK) {
                    manifest = loaded;
                    TILES.clear();
                    TILES.putAll(tiles);
                }
            } catch (Throwable error) {
                error.printStackTrace();
            } finally {
                FETCHING.set(false);
            }
        }, "SensusNews-Tablet");
        thread.setDaemon(true);
        thread.start();
    }

    private static PageManifest fetchManifest(String base) throws IOException {
        byte[] bytes = fetch(base + "/manifest.json", MAX_MANIFEST_BYTES);
        return PageManifest.fromJson(new String(bytes, StandardCharsets.UTF_8));
    }

    private static Map<String, byte[]> fetchTiles(PageManifest value, String base) throws IOException {
        Map<String, byte[]> result = new HashMap<String, byte[]>();
        long total = 0L;
        for (Page page : value.pages()) {
            for (Tile tile : page.tiles()) {
                String file = tile.file();
                if (!Tile.isSafePath(file)) continue;
                if (result.containsKey(file)) continue;
                byte[] bytes = fetch(base + "/pages/" + file, MAX_TILE_BYTES);
                total += bytes.length;
                if (total > MAX_TOTAL_BYTES) throw new IOException("Tablet page pack is too large");
                result.put(file, bytes);
            }
        }
        return result;
    }

    private static byte[] fetch(String value, int limit) throws IOException {
        if (!SensusNews.isAllowedUrl(value)) throw new IOException("Tablet page pack must use HTTPS");
        HttpURLConnection connection = (HttpURLConnection) new URL(value).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json, image/png");
            connection.setRequestProperty("User-Agent", "SensusMMC-Ingame-News/0.1");
            int status = connection.getResponseCode();
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                throw new IOException("Tablet page pack must use HTTPS");
            }
            if (status < 200 || status >= 300) throw new IOException("Tablet page pack returned HTTP " + status);
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (output.size() + count > limit) throw new IOException("Tablet page pack response is too large");
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }
}
