package sensusmmc.news.neoforge;

import com.mojang.blaze3d.platform.InputConstants;

import java.net.URI;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

import sensusmmc.news.NewsConfig;
import sensusmmc.news.NewsFeedClient;
import sensusmmc.news.NewsTabletState;
import sensusmmc.news.SensusNews;
import sensusmmc.news.client.NewsTabletContent;
import sensusmmc.news.client.NewsTabletScreen;
import sensusmmc.news.client.TabletClientHooks;
import sensusmmc.sensusnews.NewsFeed;
import sensusmmc.sensusnews.NewsPost;

public final class NeoForgeClient {
    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SensusNews.MOD_ID, "news"));
    private static final KeyMapping OPEN_TABLET = new KeyMapping(
        "key.sensusmmc.open_tablet",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_N,
        CATEGORY
    );

    private static boolean polling;
    private static long nextPoll;

    private NeoForgeClient() {
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        if (NewsConfig.requireTablet()) return;
        event.register(OPEN_TABLET);
    }

    public static void setup(FMLClientSetupEvent event) {
        NewsTabletState.load(FMLPaths.CONFIGDIR.get().resolve("sensusmmc-news-state.json"));
        event.enqueueWork(() ->
            TabletClientHooks.setOpener(() -> Minecraft.getInstance().setScreenAndShow(new NewsTabletScreen()))
        );
        NeoForge.EVENT_BUS.addListener(NeoForgeClient::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!NewsConfig.requireTablet()) {
            while (OPEN_TABLET.consumeClick()) {
                TabletClientHooks.open();
            }
        }
        if (minecraft.player == null) return;
        NewsTabletContent.ensureBundledFeed();
        long now = System.currentTimeMillis();
        if (polling || now < nextPoll) return;
        polling = true;
        nextPoll = now + NewsConfig.pollSeconds() * 1000L;
        NewsFeedClient.fetchAsync(NewsConfig.feedUrl(), new NewsFeedClient.Callback() {
            @Override
            public void onSuccess(NewsFeed feed) {
                minecraft.execute(() -> {
                    boolean first = NewsTabletState.isKnownEmpty();
                    NewsTabletState.FeedUpdate update = NewsTabletState.applyFeed(feed);
                    if (!first && !update.fresh().isEmpty() && NewsConfig.chatNotifications()) {
                        for (NewsPost post : update.fresh()) notifyPost(post);
                    }
                    polling = false;
                });
            }

            @Override
            public void onFailure(Throwable error) {
                minecraft.execute(() -> polling = false);
            }
        });
    }

    private static void notifyPost(NewsPost post) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        String url = NewsFeedClient.absoluteUrl(post.url());
        Style style = Style.EMPTY
            .withColor(ChatFormatting.AQUA)
            .withUnderlined(true)
            .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)));
        MutableComponent link = Component.literal(url).withStyle(style);
        MutableComponent message = Component.literal("[New Sensus article was posted! ")
            .append(link)
            .append(Component.literal("]"));
        minecraft.player.sendSystemMessage(message);
    }
}
