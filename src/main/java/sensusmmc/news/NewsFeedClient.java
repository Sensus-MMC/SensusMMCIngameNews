package sensusmmc.news;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sensusmmc.sensusnews.NewsFeed;
import sensusmmc.sensusnews.NewsPost;

public final class NewsFeedClient {
    public interface Callback {
        void onSuccess(NewsFeed feed);

        void onFailure(Throwable error);
    }

    private static final Pattern ENTRY = Pattern.compile("<div class=\"article-entry\">");
    private static final Pattern ARTICLE_LINK = Pattern.compile("<a[^>]+href=\"([^\"]*/articles/[^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern TIME = Pattern.compile("<time[^>]+datetime=\"([^\"]+)\"");
    private static final Pattern AUTHOR = Pattern.compile("<span class=\"meta-author\">(.*?)</span>", Pattern.DOTALL);
    private static final Pattern IMAGE = Pattern.compile("<img[^>]+src=\"([^\"]+)\"");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

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
            connection.setRequestProperty("Accept", "application/json, text/html");
            connection.setRequestProperty("User-Agent", "SensusMMC-Ingame-News/0.1");
            int status = connection.getResponseCode();
            if (status == 404) {
                return fetchFromSite();
            }
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

    private static NewsFeed fetchFromSite() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(SensusNews.SITE_URL + "/").openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "text/html");
            connection.setRequestProperty("User-Agent", "SensusMMC-Ingame-News/0.1");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Site returned HTTP " + status);
            }
            try (InputStream input = connection.getInputStream()) {
                return parseHtml(read(input));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static NewsFeed parseHtml(String html) throws IOException {
        List<NewsPost> posts = new ArrayList<NewsPost>();
        Matcher entry = ENTRY.matcher(html);
        int start = -1;
        while (entry.find()) {
            if (start >= 0) addEntry(html.substring(start, entry.start()), posts);
            start = entry.end();
        }
        if (start >= 0) addEntry(html.substring(start), posts);
        if (posts.isEmpty()) throw new IOException("No articles found on site");
        return new NewsFeed(1, Instant.now().toString(), posts);
    }

    private static void addEntry(String block, List<NewsPost> posts) {
        Matcher link = ARTICLE_LINK.matcher(block);
        if (!link.find()) return;
        String url = link.group(1);
        String title = clean(link.group(2));
        String date = first(TIME, block);
        String author = clean(first(AUTHOR, block));
        String image = first(IMAGE, block);
        String id = url;
        if (id.contains("://")) {
            id = id.substring(id.indexOf("://") + 3);
            int slash = id.indexOf('/');
            id = slash >= 0 ? id.substring(slash) : "";
        }
        id = id.replaceAll("^/+|/+$", "").replaceAll("[^A-Za-z0-9._-]+", "-");
        if (id.isEmpty()) return;
        posts.add(new NewsPost(id, title, url, date, author, "", image, id));
    }

    private static String first(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String clean(String value) {
        String withoutTags = TAGS.matcher(value == null ? "" : value).replaceAll("");
        return withoutTags
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim();
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
