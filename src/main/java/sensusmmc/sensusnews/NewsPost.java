package sensusmmc.sensusnews;

import java.util.LinkedHashMap;
import java.util.Map;

import sensusmmc.sensuspages.json.Json;

public final class NewsPost {
    private final String id;
    private final String title;
    private final String url;
    private final String date;
    private final String author;
    private final String excerpt;
    private final String image;
    private final String page;

    public NewsPost(String id, String title, String url, String date, String author, String excerpt, String image, String page) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("post id");
        if (url == null || url.isEmpty()) throw new IllegalArgumentException("post url");
        this.id = id;
        this.title = title == null ? "" : title;
        this.url = url;
        this.date = date == null ? "" : date;
        this.author = author == null ? "" : author;
        this.excerpt = excerpt == null ? "" : excerpt;
        this.image = image == null ? "" : image;
        this.page = page == null ? "" : page;
    }

    public static NewsPost fromJson(Object value) {
        Map<String, Object> object = Json.object(value, "post");
        return new NewsPost(
            Json.requiredString(object, "id"),
            Json.string(object, "title", ""),
            Json.requiredString(object, "url"),
            Json.string(object, "date", ""),
            Json.string(object, "author", ""),
            Json.string(object, "excerpt", ""),
            Json.string(object, "image", ""),
            Json.string(object, "page", "")
        );
    }

    public Map<String, Object> toJson() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("title", title);
        result.put("url", url);
        result.put("date", date);
        result.put("author", author);
        result.put("excerpt", excerpt);
        result.put("image", image);
        result.put("page", page);
        return result;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String url() {
        return url;
    }

    public String date() {
        return date;
    }

    public String author() {
        return author;
    }

    public String excerpt() {
        return excerpt;
    }

    public String image() {
        return image;
    }

    public String page() {
        return page;
    }
}
