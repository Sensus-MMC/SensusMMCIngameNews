package sensusmmc.sensuspages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sensusmmc.sensuspages.json.Json;

public final class Page {
    private final String id;
    private final String url;
    private final String title;
    private final int sourceWidth;
    private final int sourceHeight;
    private final List<Tile> tiles;
    private final List<PageLink> links;

    public Page(String id, String url, String title, int sourceWidth, int sourceHeight, List<Tile> tiles, List<PageLink> links) {
        if (id == null || !id.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("page id");
        if (url == null || url.isEmpty()) throw new IllegalArgumentException("page url");
        if (sourceWidth <= 0 || sourceHeight <= 0) throw new IllegalArgumentException("page size");
        if (tiles == null || tiles.isEmpty()) throw new IllegalArgumentException("page tiles");
        int expectedY = 0;
        for (int index = 0; index < tiles.size(); index += 1) {
            Tile tile = tiles.get(index);
            if (tile.x() != 0) throw new IllegalArgumentException("tile x");
            if (tile.y() != expectedY) throw new IllegalArgumentException("tile y");
            if (tile.contentWidth() != sourceWidth) throw new IllegalArgumentException("tile content width");
            if (tile.contentHeight() != Math.min(tile.height(), sourceHeight - tile.y())) {
                throw new IllegalArgumentException("tile content height");
            }
            expectedY += tile.height();
        }
        if (expectedY < sourceHeight) throw new IllegalArgumentException("page tiles do not cover height");
        this.id = id;
        this.url = url;
        this.title = title == null ? "" : title;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.tiles = Collections.unmodifiableList(new ArrayList<Tile>(tiles));
        this.links = Collections.unmodifiableList(links == null ? new ArrayList<PageLink>() : new ArrayList<PageLink>(links));
    }

    public static Page fromJson(Object value, PageViewport viewport) {
        Map<String, Object> object = Json.object(value, "page");
        List<Object> tileValues = Json.array(object.get("tiles"), "page.tiles");
        List<Tile> tiles = new ArrayList<Tile>(tileValues.size());
        for (Object tile : tileValues) tiles.add(Tile.fromJson(tile));
        List<PageLink> links = new ArrayList<PageLink>();
        Object linkValue = object.get("links");
        if (linkValue != null) {
            for (Object link : Json.array(linkValue, "page.links")) links.add(PageLink.fromJson(link));
        }
        Page page = new Page(
            Json.requiredString(object, "id"),
            Json.requiredString(object, "url"),
            Json.string(object, "title", ""),
            Json.integer(object.get("sourceWidth"), "page.sourceWidth"),
            Json.integer(object.get("sourceHeight"), "page.sourceHeight"),
            tiles,
            links
        );
        if (page.sourceWidth != viewport.sourceWidth()) throw new IllegalArgumentException("page width does not match viewport");
        return page;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("url", url);
        result.put("title", title);
        result.put("sourceWidth", sourceWidth);
        result.put("sourceHeight", sourceHeight);
        List<Object> tileValues = new ArrayList<Object>(tiles.size());
        for (Tile tile : tiles) tileValues.add(tile.toJson());
        result.put("tiles", tileValues);
        List<Object> linkValues = new ArrayList<Object>(links.size());
        for (PageLink link : links) linkValues.add(link.toJson());
        result.put("links", linkValues);
        return result;
    }

    public int gameHeight(double scale) {
        return (int) Math.round(sourceHeight * scale);
    }

    public int scrollMax(PageViewport viewport) {
        return Math.max(0, gameHeight(viewport.scale()) - PageViewport.CONTENT_HEIGHT);
    }

    public PageLink linkAt(double gameX, double gameY, double scrollGame, double scale) {
        for (PageLink link : links) {
            if (link.contains(gameX, gameY, scrollGame, scale)) return link;
        }
        return null;
    }

    public String id() {
        return id;
    }

    public String url() {
        return url;
    }

    public String title() {
        return title;
    }

    public int sourceWidth() {
        return sourceWidth;
    }

    public int sourceHeight() {
        return sourceHeight;
    }

    public List<Tile> tiles() {
        return tiles;
    }

    public List<PageLink> links() {
        return links;
    }
}
