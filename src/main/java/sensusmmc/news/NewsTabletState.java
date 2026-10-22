package sensusmmc.news;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sensusmmc.news.feed.NewsFeed;
import sensusmmc.news.feed.NewsPost;
import sensusmmc.news.feed.NewsState;

public final class NewsTabletState {
    private static NewsState state = NewsState.empty();
    private static NewsFeed feed;
    private static Path storage;
    private static int unreadCount;

    private NewsTabletState() {
    }

    public static synchronized void load(Path file) {
        storage = file;
        if (file != null && Files.exists(file)) {
            try {
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                state = NewsState.fromJson(source);
            } catch (Exception error) {
                error.printStackTrace();
                state = NewsState.empty();
            }
        }
    }

    public static synchronized void save() {
        if (storage == null) return;
        try {
            Path parent = storage.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(storage, state.toJson().getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            error.printStackTrace();
        }
    }

    public static synchronized void setFeed(NewsFeed value) {
        if (value == null) return;
        feed = value;
        state.update(value, value.generated());
        unreadCount = state.unreadCount(value);
        save();
    }

    public static synchronized FeedUpdate applyFeed(NewsFeed value) {
        if (value == null) return new FeedUpdate(Collections.<NewsPost>emptyList(), true);
        boolean first = state.known().isEmpty();
        feed = value;
        List<NewsPost> fresh = state.update(value, value.generated());
        unreadCount = state.unreadCount(value);
        save();
        return new FeedUpdate(fresh, first);
    }

    public static synchronized void markAllRead() {
        if (feed != null) {
            for (NewsPost post : feed.posts()) state.markRead(post.id());
        }
        unreadCount = 0;
        save();
    }

    public static synchronized int unreadCount() {
        return unreadCount;
    }

    public static synchronized boolean isUnread() {
        return unreadCount > 0;
    }

    public static synchronized boolean hasFeed() {
        return feed != null;
    }

    public static synchronized boolean isKnownEmpty() {
        return state.known().isEmpty();
    }

    public static final class FeedUpdate {
        private final List<NewsPost> fresh;
        private final boolean first;

        private FeedUpdate(List<NewsPost> fresh, boolean first) {
            this.fresh = Collections.unmodifiableList(new ArrayList<NewsPost>(fresh));
            this.first = first;
        }

        public List<NewsPost> fresh() {
            return fresh;
        }

        public boolean first() {
            return first;
        }
    }
}
