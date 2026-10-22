package sensusmmc.news.feed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sensusmmc.news.page.json.Json;

public final class NewsFeed {
    public static final int SCHEMA = 1;

    private final int schema;
    private final String generated;
    private final List<NewsPost> posts;

    public NewsFeed(int schema, String generated, List<NewsPost> posts) {
        if (schema != SCHEMA) throw new IllegalArgumentException("unsupported feed schema");
        if (generated == null) throw new IllegalArgumentException("generated");
        if (posts == null) throw new IllegalArgumentException("posts");
        List<String> ids = new ArrayList<String>(posts.size());
        for (NewsPost post : posts) {
            if (ids.contains(post.id())) throw new IllegalArgumentException("duplicate post id " + post.id());
            ids.add(post.id());
        }
        this.schema = schema;
        this.generated = generated;
        this.posts = Collections.unmodifiableList(new ArrayList<NewsPost>(posts));
    }

    public static NewsFeed fromJson(String source) {
        Map<String, Object> object = Json.object(Json.parse(source), "feed");
        List<Object> postValues = Json.array(object.get("posts"), "feed.posts");
        List<NewsPost> posts = new ArrayList<NewsPost>(postValues.size());
        for (Object post : postValues) posts.add(NewsPost.fromJson(post));
        return new NewsFeed(
            Json.integer(object.get("schema"), "feed.schema"),
            Json.requiredString(object, "generated"),
            posts
        );
    }

    public String toJson() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("schema", schema);
        result.put("generated", generated);
        List<Object> postValues = new ArrayList<Object>(posts.size());
        for (NewsPost post : posts) postValues.add(post.toJson());
        result.put("posts", postValues);
        return Json.write(result);
    }

    public NewsPost post(String id) {
        for (NewsPost post : posts) {
            if (post.id().equals(id)) return post;
        }
        return null;
    }

    public int schema() {
        return schema;
    }

    public String generated() {
        return generated;
    }

    public List<NewsPost> posts() {
        return posts;
    }
}
