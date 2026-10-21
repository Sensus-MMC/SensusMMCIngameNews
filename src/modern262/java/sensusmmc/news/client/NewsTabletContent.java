package sensusmmc.news.client;

import sensusmmc.news.ResourceLocations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import sensusmmc.news.NewsTabletState;
import sensusmmc.news.SensusNews;
import sensusmmc.sensusnews.NewsFeed;

public final class NewsTabletContent {
    private static final Identifier BUNDLED_FEED =
        ResourceLocations.of(SensusNews.MOD_ID, "pages/feed.json");
    private static boolean loaded;

    private NewsTabletContent() {
    }

    public static void ensureBundledFeed() {
        if (loaded || NewsTabletState.hasFeed()) {
            loaded = true;
            return;
        }
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(BUNDLED_FEED).orElse(null);
            if (resource == null) return;
            try (InputStream input = resource.open()) {
                NewsTabletState.setFeed(NewsFeed.fromJson(read(input)));
                loaded = true;
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
