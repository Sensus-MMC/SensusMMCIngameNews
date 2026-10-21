package sensusmmc.news;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import sensusmmc.sensusnews.NewsFeed;

public final class NewsFeedClient {
    public interface Callback {
        void onSuccess(NewsFeed feed);

        void onFailure(Throwable error);
    }

    private NewsFeedClient() {
    }

    public static void fetchAsync(String url, Callback callback) {
        Thread thread = new Thread(() -> {
            try {
                callback.onSuccess(fetch(url));
            } catch (Throwable error) {
                callback.onFailure(error);
            }
        }, "SensusNews-Feed");
        thread.setDaemon(true);
        thread.start();
    }

    public static String absoluteUrl(String url) {
        if (url == null || url.isEmpty()) return SensusNews.SITE_URL;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("/")) return SensusNews.SITE_URL + url;
        return SensusNews.SITE_URL + "/" + url;
    }

    private static NewsFeed fetch(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "SensusMMC-Ingame-News/0.1");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Feed returned HTTP " + status);
            }
            try (InputStream input = connection.getInputStream()) {
                return NewsFeed.fromJson(read(input));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
