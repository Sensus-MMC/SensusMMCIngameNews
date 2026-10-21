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
            "\"cssWidth\":400,\"cssHeight\":478,\"dpr\":2,\"sourceWidth\":800,\"sourceHeight\":956,\"scale\":0.25}," +
            "\"pages\":[{\"id\":\"home\",\"url\":\"/\",\"title\":\"Home\",\"sourceWidth\":800,\"sourceHeight\":1348," +
            "\"tiles\":[" +
            "{\"file\":\"home/0000.png\",\"x\":0,\"y\":0,\"width\":1024,\"height\":1024,\"contentWidth\":800,\"contentHeight\":1024}," +
            "{\"file\":\"home/0001.png\",\"x\":0,\"y\":1024,\"width\":1024,\"height\":1024,\"contentWidth\":800,\"contentHeight\":324}]," +
            "\"links\":[{\"x\":40,\"y\":166,\"width\":142,\"height\":71,\"href\":\"/about\",\"external\":false,\"text\":\"About\"}]}]}";
        PageManifest manifest = PageManifest.fromJson(source);
        check(manifest.pages().size() == 1, "manifest page count");
        check(manifest.page("home") != null, "manifest page lookup");
        Page page = manifest.page("home");
        check(page.gameHeight(0.25) == 337, "page game height");
        check(page.scrollMax(VIEWPORT) == 98, "scroll max");
        PageLink link = page.links().get(0);
        check(link.contains(10.0, 41.5, 0.0, 0.25), "link hit");
        check(!link.contains(10.0, 60.0, 0.0, 0.25), "link miss");
        PageManifest roundTrip = PageManifest.fromJson(manifest.toJson());
        check(roundTrip.page("home").sourceHeight() == 1348, "manifest round trip");
        check(Tile.isSafePath("home/0000.png"), "safe path");
        check(!Tile.isSafePath("../home/0000.png"), "unsafe parent path");
        check(!Tile.isSafePath("home\\0000.png"), "unsafe backslash path");
    }

    private static void geometry() {
        check(VIEWPORT.gameWidth() == 200, "game width");
        check(VIEWPORT.gameHeight() == 239, "game height");
        check(VIEWPORT.sourceY(0.0) == 0.0, "source top");
        check(VIEWPORT.sourceY(10.0) == 40.0, "source scroll");
        check(VIEWPORT.gameX(800.0) == 200.0, "game x");
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
        check(manifest.viewport().sourceWidth() == 800, "fixture viewport");
        for (Page page : manifest.pages()) {
            check(page.tiles().size() == Math.max(1, (page.sourceHeight() + 1023) / 1024), "fixture tile count");
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
