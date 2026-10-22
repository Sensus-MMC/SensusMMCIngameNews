package sensusmmc.news.page;

import java.util.LinkedHashMap;
import java.util.Map;

import sensusmmc.news.page.json.Json;

public final class Tile {
    private final String file;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int contentWidth;
    private final int contentHeight;

    public Tile(String file, int x, int y, int width, int height, int contentWidth, int contentHeight) {
        if (!isSafePath(file)) throw new IllegalArgumentException("unsafe tile path");
        if (x < 0 || y < 0 || width <= 0 || height <= 0) throw new IllegalArgumentException("tile bounds");
        if (contentWidth <= 0 || contentHeight <= 0 || contentWidth > width || contentHeight > height) {
            throw new IllegalArgumentException("tile content bounds");
        }
        this.file = file;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
    }

    public static Tile fromJson(Object value) {
        Map<String, Object> object = Json.object(value, "tile");
        return new Tile(
            Json.requiredString(object, "file"),
            Json.integer(object.get("x"), "tile.x"),
            Json.integer(object.get("y"), "tile.y"),
            Json.integer(object.get("width"), "tile.width"),
            Json.integer(object.get("height"), "tile.height"),
            Json.integer(object.get("contentWidth"), "tile.contentWidth"),
            Json.integer(object.get("contentHeight"), "tile.contentHeight")
        );
    }

    public Map<String, Object> toJson() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("file", file);
        result.put("x", x);
        result.put("y", y);
        result.put("width", width);
        result.put("height", height);
        result.put("contentWidth", contentWidth);
        result.put("contentHeight", contentHeight);
        return result;
    }

    public static boolean isSafePath(String path) {
        if (path == null || path.isEmpty() || path.length() > 256) return false;
        if (path.indexOf('\\') >= 0 || path.indexOf(':') >= 0 || path.startsWith("/")) return false;
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return false;
        }
        return true;
    }

    public String file() {
        return file;
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

    public int contentWidth() {
        return contentWidth;
    }

    public int contentHeight() {
        return contentHeight;
    }
}
