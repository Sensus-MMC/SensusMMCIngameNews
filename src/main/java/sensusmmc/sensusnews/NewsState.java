package sensusmmc.sensusnews;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sensusmmc.sensuspages.json.Json;

public final class NewsState {
    public static final int SCHEMA = 1;

    private final Set<String> known;
    private final Set<String> read;
    private String lastCheck;

    public NewsState(Set<String> known, Set<String> read, String lastCheck) {
        if (known == null || read == null) throw new IllegalArgumentException("state sets");
        this.known = new LinkedHashSet<String>(known);
        this.read = new LinkedHashSet<String>(read);
        this.lastCheck = lastCheck == null ? "" : lastCheck;
    }

    public static NewsState empty() {
        return new NewsState(Collections.<String>emptySet(), Collections.<String>emptySet(), "");
    }

    public static NewsState fromJson(String source) {
        Map<String, Object> object = Json.object(Json.parse(source), "newsState");
        int schema = Json.integer(object.get("schema"), "newsState.schema");
        if (schema != SCHEMA) throw new IllegalArgumentException("unsupported news state schema");
        Set<String> known = new LinkedHashSet<String>();
        Object knownValue = object.get("known");
        if (knownValue != null) known.addAll(Json.strings(knownValue, "newsState.known"));
        Set<String> read = new LinkedHashSet<String>();
        Object readValue = object.get("read");
        if (readValue != null) read.addAll(Json.strings(readValue, "newsState.read"));
        return new NewsState(known, read, Json.string(object, "lastCheck", ""));
    }

    public String toJson() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("schema", SCHEMA);
        result.put("known", new ArrayList<String>(known));
        result.put("read", new ArrayList<String>(read));
        result.put("lastCheck", lastCheck);
        return Json.write(result);
    }

    public List<NewsPost> update(NewsFeed feed, String timestamp) {
        if (feed == null) throw new IllegalArgumentException("feed");
        List<NewsPost> fresh = new ArrayList<NewsPost>();
        for (NewsPost post : feed.posts()) {
            if (known.add(post.id())) fresh.add(post);
        }
        if (timestamp != null) lastCheck = timestamp;
        return fresh;
    }

    public boolean markRead(String id) {
        if (id == null || id.isEmpty()) return false;
        known.add(id);
        return read.add(id);
    }

    public boolean isRead(String id) {
        return read.contains(id);
    }

    public boolean isKnown(String id) {
        return known.contains(id);
    }

    public int unreadCount(NewsFeed feed) {
        int count = 0;
        for (NewsPost post : feed.posts()) {
            if (!read.contains(post.id())) count += 1;
        }
        return count;
    }

    public List<NewsPost> unread(NewsFeed feed) {
        List<NewsPost> result = new ArrayList<NewsPost>();
        for (NewsPost post : feed.posts()) {
            if (!read.contains(post.id())) result.add(post);
        }
        return result;
    }

    public Set<String> known() {
        return Collections.unmodifiableSet(known);
    }

    public Set<String> read() {
        return Collections.unmodifiableSet(read);
    }

    public String lastCheck() {
        return lastCheck;
    }
}
