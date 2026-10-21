package sensusmmc.news;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class NewsConfig {
    public static final String DEFAULT_FEED_URL = "https://sensusmmc.news/feed.json";

    private static boolean requireTablet = false;
    private static boolean chatNotifications = true;
    private static int pollSeconds = 300;
    private static String feedUrl = DEFAULT_FEED_URL;
    private static Path path;

    private NewsConfig() {
    }

    public static synchronized void load(Path file) {
        path = file;
        Properties properties = new Properties();
        if (file != null && Files.exists(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            } catch (IOException error) {
                error.printStackTrace();
            }
        }
        requireTablet = Boolean.parseBoolean(properties.getProperty("requireTablet", "false"));
        chatNotifications = Boolean.parseBoolean(properties.getProperty("chatNotifications", "true"));
        pollSeconds = parsePollSeconds(properties.getProperty("pollSeconds", "300"));
        feedUrl = parseFeedUrl(properties.getProperty("feedUrl", DEFAULT_FEED_URL));
        save();
    }

    public static synchronized void save() {
        if (path == null) return;
        Properties properties = new Properties();
        properties.setProperty("requireTablet", Boolean.toString(requireTablet));
        properties.setProperty("chatNotifications", Boolean.toString(chatNotifications));
        properties.setProperty("pollSeconds", Integer.toString(pollSeconds));
        properties.setProperty("feedUrl", feedUrl);
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "SensusMMC In-Game News");
            }
        } catch (IOException error) {
            error.printStackTrace();
        }
    }

    private static int parsePollSeconds(String value) {
        try {
            return Math.max(30, Integer.parseInt(value.trim()));
        } catch (NumberFormatException error) {
            return 300;
        }
    }

    private static String parseFeedUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        return DEFAULT_FEED_URL;
    }

    public static boolean requireTablet() {
        return requireTablet;
    }

    public static boolean chatNotifications() {
        return chatNotifications;
    }

    public static int pollSeconds() {
        return pollSeconds;
    }

    public static String feedUrl() {
        return feedUrl;
    }
}
