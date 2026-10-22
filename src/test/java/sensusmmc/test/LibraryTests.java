package sensusmmc.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import sensusmmc.sensusnews.NewsFeed;
import sensusmmc.sensusnews.NewsPost;
import sensusmmc.sensusnews.NewsState;
import sensusmmc.sensuspages.Page;
import sensusmmc.sensuspages.PageLink;
import sensusmmc.sensuspages.PageManifest;
import sensusmmc.sensuspages.PageViewport;
import sensusmmc.sensuspages.Tile;

public final class LibraryTests {
    private static final PageViewport VIEWPORT = PageViewport.standard();

    public static void main(String[] args) throws Exception {
        json();
        manifest();
        geometry();
        news();
        if (args.length > 0) fixtures(Paths.get(args[0]));
        System.out.println("library tests passed");
    }

    private static void json() {
        String source = "{\"text\":\"a\\\"b\\n\",\"count\":3,\"ratio\":0.25,\"flag\":true,\"none\":null,\"items\":[1,2]}";
        Object parsed = sensusmmc.sensuspages.json.Json.parse(source);
        String written = sensusmmc.sensuspages.json.Json.write(parsed);
        check(written.contains("\"text\":\"a\\\"b\\n\""), "json string round trip");
        check(written.contains("\"ratio\":0.25"), "json decimal");
        check("missing".equals(sensusmmc.sensuspages.json.Json.string(
            sensusmmc.sensuspages.json.Json.object(parsed, "root"), "missing", "missing")), "json fallback");
    }

    private static void manifest() {
        String source =
            "{\"schema\":1,\"generated\":\"2026-10-22T00:00:00Z\",\"viewport\":{" +
            "\"cssWidth\":610,\"cssHeight\":616,\"dpr\":3,\"sourceWidth\":1830,\"sourceHeight\":1848,\"scale\":0.16666666666666666}," +
            "\"pages\":[{\"id\":\"home\",\"url\":\"/\",\"title\":\"Home\",\"sourceWidth\":1830,\"sourceHeight\":3696," +
            "\"tiles\":[" +
            "{\"file\":\"home/0000.png\",\"x\":0,\"y\":0,\"width\":2048,\"height\":2048,\"contentWidth\":1830,\"contentHeight\":2048}," +
            "{\"file\":\"home/0001.png\",\"x\":0,\"y\":2048,\"width\":2048,\"height\":2048,\"contentWidth\":1830,\"contentHeight\":1648}]," +
            "\"links\":[{\"x\":0,\"y\":0,\"width\":600,\"height\":300,\"href\":\"/about\",\"external\":false,\"text\":\"About\"}]}]}";
        PageManifest manifest = PageManifest.fromJson(source);
        check(manifest.pages().size() == 1, "manifest page count");
        check(manifest.page("home") != null, "manifest page lookup");
        Page page = manifest.page("home");
        check(page.gameHeight(VIEWPORT.scale()) == 616, "page game height");
        check(page.scrollMax(VIEWPORT) == 308, "scroll max");
        PageLink link = page.links().get(0);
        check(link.contains(10.0, 20.0, 0.0, VIEWPORT.scale()), "link hit");
        check(!link.contains(10.0, 60.0, 0.0, VIEWPORT.scale()), "link miss");
        PageManifest roundTrip = PageManifest.fromJson(manifest.toJson());
        check(roundTrip.page("home").sourceHeight() == 3696, "manifest round trip");
        check(Tile.isSafePath("home/0000.png"), "safe path");
        check(!Tile.isSafePath("../home/0000.png"), "unsafe parent path");
        check(!Tile.isSafePath("home\\0000.png"), "unsafe backslash path");
    }

    private static void geometry() {
        check(VIEWPORT.gameWidth() == 305, "game width");
        check(VIEWPORT.gameHeight() == 308, "game height");
        check(VIEWPORT.sourceY(0.0) == 0.0, "source top");
        check(VIEWPORT.sourceY(10.0) == 60.0, "source scroll");
        check(VIEWPORT.gameX(1830.0) == 305.0, "game x");
    }

    private static void news() {
        NewsPost first = new NewsPost("first", "First", "/first", "2026-10-01T00:00:00Z", "Sensus", "A", "", "first");
        NewsPost second = new NewsPost("second", "Second", "/second", "2026-10-15T00:00:00Z", "Sensus", "B", "", "second");
        NewsFeed feed = new NewsFeed(1, "2026-10-22T00:00:00Z", java.util.Arrays.asList(first, second));
        NewsState state = NewsState.empty();
        check(state.update(feed, "2026-10-22T00:00:00Z").size() == 2, "initial news");
        check(state.unreadCount(feed) == 2, "unread count");
        check(state.markRead("first"), "mark read");
        check(!state.markRead("first"), "mark read idempotent");
        check(state.unreadCount(feed) == 1, "unread after read");
        check(state.update(feed, "2026-10-22T00:01:00Z").isEmpty(), "no duplicate news");
        NewsState loaded = NewsState.fromJson(state.toJson());
        check(loaded.isRead("first"), "news state round trip");
        NewsPost third = new NewsPost("third", "Third", "/third", "2026-10-20T00:00:00Z", "Sensus", "C", "", "third");
        NewsFeed extended = new NewsFeed(1, "2026-10-22T00:02:00Z", java.util.Arrays.asList(first, second, third));
        check(loaded.update(extended, "2026-10-22T00:02:00Z").size() == 1, "new post");
        check(loaded.unread(extended).size() == 2, "unread posts");
        check(loaded.lastCheck().equals("2026-10-22T00:02:00Z"), "last check");
    }

    private static void fixtures(Path directory) throws Exception {
        Path manifestPath = directory.resolve("manifest.json");
        Path feedPath = directory.resolve("feed.json");
        if (!Files.exists(manifestPath)) return;
        String manifestText = new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8);
        PageManifest manifest = PageManifest.fromJson(manifestText);
        check(manifest.viewport().sourceWidth() == 1830, "fixture viewport");
        for (Page page : manifest.pages()) {
            int tileSize = page.tiles().get(0).width();
            check(page.tiles().size() == Math.max(1, (page.sourceHeight() + tileSize - 1) / tileSize), "fixture tile count");
            Path tilePath = directory.resolve("pages").resolve(page.tiles().get(0).file());
            check(Files.exists(tilePath), "fixture tile exists " + tilePath);
            System.out.println("page " + page.id() + " " + page.sourceWidth() + "x" + page.sourceHeight()
                + " tiles=" + page.tiles().size() + " links=" + page.links().size()
                + " scroll=" + page.scrollMax(manifest.viewport()));
        }
        if (Files.exists(feedPath)) {
            String feedText = new String(Files.readAllBytes(feedPath), StandardCharsets.UTF_8);
            NewsFeed feed = NewsFeed.fromJson(feedText);
            NewsState state = NewsState.empty();
            state.update(feed, feed.generated());
            System.out.println("feed posts=" + feed.posts().size() + " unread=" + state.unreadCount(feed));
        }
        Page about = manifest.page("about");
        if (about != null) {
            boolean aboutLink = false;
            for (PageLink link : manifest.page("home").links()) {
                if ("/about".equals(link.href())) aboutLink = true;
            }
            check(aboutLink, "about link");
            System.out.println("about page " + about.sourceHeight() + " scroll=" + about.scrollMax(manifest.viewport()));
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
