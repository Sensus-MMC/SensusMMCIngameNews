package sensusmmc.news.page;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sensusmmc.news.page.json.Json;

public final class PageManifest {
    public static final int SCHEMA = 1;

    private final int schema;
    private final String generated;
    private final PageViewport viewport;
    private final List<Page> pages;

    public PageManifest(int schema, String generated, PageViewport viewport, List<Page> pages) {
        if (schema != SCHEMA) throw new IllegalArgumentException("unsupported schema");
        if (generated == null) throw new IllegalArgumentException("generated");
        if (viewport == null) throw new IllegalArgumentException("viewport");
        if (pages == null || pages.isEmpty()) throw new IllegalArgumentException("pages");
        List<String> ids = new ArrayList<String>(pages.size());
        for (Page page : pages) {
            if (ids.contains(page.id())) throw new IllegalArgumentException("duplicate page id " + page.id());
            if (page.sourceWidth() != viewport.sourceWidth()) throw new IllegalArgumentException("page width");
            ids.add(page.id());
        }
        this.schema = schema;
        this.generated = generated;
        this.viewport = viewport;
        this.pages = Collections.unmodifiableList(new ArrayList<Page>(pages));
    }

    public static PageManifest fromJson(String source) {
        Map<String, Object> object = Json.object(Json.parse(source), "manifest");
        PageViewport viewport = PageViewport.fromJson(object.get("viewport"));
        List<Object> pageValues = Json.array(object.get("pages"), "manifest.pages");
        List<Page> pages = new ArrayList<Page>(pageValues.size());
        for (Object page : pageValues) pages.add(Page.fromJson(page, viewport));
        return new PageManifest(
            Json.integer(object.get("schema"), "manifest.schema"),
            Json.requiredString(object, "generated"),
            viewport,
            pages
        );
    }

    public String toJson() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("schema", schema);
        result.put("generated", generated);
        result.put("viewport", viewport.toJson());
        List<Object> pageValues = new ArrayList<Object>(pages.size());
        for (Page page : pages) pageValues.add(page.toJson());
        result.put("pages", pageValues);
        return Json.write(result);
    }

    public Page page(String id) {
        for (Page page : pages) {
            if (page.id().equals(id)) return page;
        }
        return null;
    }

    public int schema() {
        return schema;
    }

    public String generated() {
        return generated;
    }

    public PageViewport viewport() {
        return viewport;
    }

    public List<Page> pages() {
        return pages;
    }
}
