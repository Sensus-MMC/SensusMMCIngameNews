package sensusmmc.news.page;

import java.util.LinkedHashMap;
import java.util.Map;

import sensusmmc.news.page.json.Json;

public final class PageLink {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final String href;
    private final boolean external;
    private final String text;

    public PageLink(int x, int y, int width, int height, String href, boolean external, String text) {
        if (x < 0 || y < 0 || width <= 0 || height <= 0) throw new IllegalArgumentException("link bounds");
        if (href == null || href.isEmpty()) throw new IllegalArgumentException("link href");
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.href = href;
        this.external = external;
        this.text = text == null ? "" : text;
    }

    public static PageLink fromJson(Object value) {
        Map<String, Object> object = Json.object(value, "link");
        return new PageLink(
            Json.integer(object.get("x"), "link.x"),
            Json.integer(object.get("y"), "link.y"),
            Json.integer(object.get("width"), "link.width"),
            Json.integer(object.get("height"), "link.height"),
            Json.requiredString(object, "href"),
            Json.bool(object, "external", false),
            Json.string(object, "text", "")
        );
    }

    public Map<String, Object> toJson() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("x", x);
        result.put("y", y);
        result.put("width", width);
        result.put("height", height);
        result.put("href", href);
        result.put("external", external);
        result.put("text", text);
        return result;
    }

    public boolean contains(double gameX, double gameY, double scrollGame, double scale) {
        double left = x * scale;
        double top = y * scale;
        double right = left + width * scale;
        double bottom = top + height * scale;
        double documentY = scrollGame + gameY;
        return gameX >= left && gameX < right && documentY >= top && documentY < bottom;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String href() {
        return href;
    }

    public boolean external() {
        return external;
    }

    public String text() {
        return text;
    }
}
