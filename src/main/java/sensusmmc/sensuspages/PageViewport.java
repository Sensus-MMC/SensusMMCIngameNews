package sensusmmc.sensuspages;

import java.util.LinkedHashMap;
import java.util.Map;

import sensusmmc.sensuspages.json.Json;

public final class PageViewport {
    public static final int CONTENT_WIDTH = 305;
    public static final int CONTENT_HEIGHT = 308;
    public static final int CAPTURE_CSS_WIDTH = 610;
    public static final int CAPTURE_CSS_HEIGHT = 616;
    public static final int CAPTURE_DPR = 3;

    private final int cssWidth;
    private final int cssHeight;
    private final int dpr;
    private final int sourceWidth;
    private final int sourceHeight;
    private final double scale;

    public PageViewport(int cssWidth, int cssHeight, int dpr, int sourceWidth, int sourceHeight, double scale) {
        this.cssWidth = cssWidth;
        this.cssHeight = cssHeight;
        this.dpr = dpr;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.scale = scale;
        validate();
    }

    public static PageViewport standard() {
        return new PageViewport(
            CAPTURE_CSS_WIDTH,
            CAPTURE_CSS_HEIGHT,
            CAPTURE_DPR,
            CAPTURE_CSS_WIDTH * CAPTURE_DPR,
            CAPTURE_CSS_HEIGHT * CAPTURE_DPR,
            (double) CONTENT_WIDTH / (CAPTURE_CSS_WIDTH * CAPTURE_DPR)
        );
    }

    public static PageViewport fromJson(Object value) {
        Map<String, Object> object = Json.object(value, "viewport");
        return new PageViewport(
            Json.integer(object.get("cssWidth"), "viewport.cssWidth"),
            Json.integer(object.get("cssHeight"), "viewport.cssHeight"),
            Json.integer(object.get("dpr"), "viewport.dpr"),
            Json.integer(object.get("sourceWidth"), "viewport.sourceWidth"),
            Json.integer(object.get("sourceHeight"), "viewport.sourceHeight"),
            Json.decimal(object.get("scale"), "viewport.scale")
        );
    }

    public Map<String, Object> toJson() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("cssWidth", cssWidth);
        result.put("cssHeight", cssHeight);
        result.put("dpr", dpr);
        result.put("sourceWidth", sourceWidth);
        result.put("sourceHeight", sourceHeight);
        result.put("scale", scale);
        return result;
    }

    public int gameWidth() {
        return (int) Math.round(sourceWidth * scale);
    }

    public int gameHeight() {
        return (int) Math.round(sourceHeight * scale);
    }

    public double sourceY(double scrollGame) {
        return scrollGame / scale;
    }

    public double gameY(double source) {
        return source * scale;
    }

    public double gameX(double source) {
        return source * scale;
    }

    private void validate() {
        if (cssWidth <= 0 || cssHeight <= 0 || dpr <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("viewport dimensions must be positive");
        }
        if (sourceWidth != cssWidth * dpr || sourceHeight != cssHeight * dpr) {
            throw new IllegalArgumentException("source dimensions must match css dimensions at dpr");
        }
        if (scale <= 0.0) throw new IllegalArgumentException("scale must be positive");
        if (Math.abs(scale - (double) CONTENT_WIDTH / sourceWidth) > 1.0e-9) {
            throw new IllegalArgumentException("scale must map source width to " + CONTENT_WIDTH);
        }
        if (gameWidth() != CONTENT_WIDTH || gameHeight() != CONTENT_HEIGHT) {
            throw new IllegalArgumentException("viewport must map to " + CONTENT_WIDTH + "x" + CONTENT_HEIGHT);
        }
        if (cssWidth != CAPTURE_CSS_WIDTH || cssHeight != CAPTURE_CSS_HEIGHT || dpr != CAPTURE_DPR) {
            throw new IllegalArgumentException("viewport must be " + CAPTURE_CSS_WIDTH + "x" + CAPTURE_CSS_HEIGHT + " at dpr " + CAPTURE_DPR);
        }
    }

    public int cssWidth() {
        return cssWidth;
    }

    public int cssHeight() {
        return cssHeight;
    }

    public int dpr() {
        return dpr;
    }

    public int sourceWidth() {
        return sourceWidth;
    }

    public int sourceHeight() {
        return sourceHeight;
    }

    public double scale() {
        return scale;
    }
}
