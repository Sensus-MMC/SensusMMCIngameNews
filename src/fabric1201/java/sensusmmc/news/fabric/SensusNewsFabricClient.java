package sensusmmc.news.fabric;

import sensusmmc.news.ResourceLocations;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import sensusmmc.news.NewsConfig;
import sensusmmc.news.NewsFeedClient;
import sensusmmc.news.NewsTabletState;
import sensusmmc.news.SensusNews;
import sensusmmc.news.client.NewsTabletContent;
import sensusmmc.news.client.NewsTabletScreen;
import sensusmmc.news.client.TabletClientHooks;
import sensusmmc.sensusnews.NewsFeed;
import sensusmmc.sensusnews.NewsPost;

public final class SensusNewsFabricClient implements ClientModInitializer {
    private static KeyMapping openTablet;
    private static boolean polling;
    private static long nextPoll;

    @Override
    public void onInitializeClient() {
        NewsConfig.load(FabricLoader.getInstance().getConfigDir().resolve("sensusmmc-news.properties"));        NewsTabletState.load(FabricLoader.getInstance().getConfigDir().resolve("sensusmmc-news-state.json"));
        TabletClientHooks.setOpener(() -> Minecraft.getInstance().setScreen(new NewsTabletScreen()));
        if (!NewsConfig.requireTablet()) {
            openTablet = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.sensusmmc.open_tablet",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_N,
                "key.categories.sensusmmc"
            ));
        }
        ItemProperties.register(
            SensusNewsFabric.NEWS_TABLET,
            ResourceLocations.of(SensusNews.MOD_ID, "unread"),
            (ClampedItemPropertyFunction) (stack, level, entity, seed) -> NewsTabletState.isUnread() ? 1.0f : 0.0f
        );
        ClientTickEvents.END_CLIENT_TICK.register(SensusNewsFabricClient::tick);
    }

    private static void tick(Minecraft minecraft) {
        if (openTablet != null) {
            while (openTablet.consumeClick()) {
                TabletClientHooks.open();
            }
        }
        if (minecraft.player == null) return;
        NewsTabletContent.ensureBundledFeed();
        sensusmmc.news.RemotePagePack.refreshIfStale();
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
            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        MutableComponent link = Component.literal(url).withStyle(style);
        MutableComponent message = Component.literal("[New Sensus article was posted! ")
            .append(link)
            .append(Component.literal("]"));
        minecraft.player.sendSystemMessage(message);
    }
}
