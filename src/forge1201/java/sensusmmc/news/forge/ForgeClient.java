package sensusmmc.news.forge;

import sensusmmc.news.ResourceLocations;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import sensusmmc.news.NewsConfig;
import sensusmmc.news.NewsFeedClient;
import sensusmmc.news.NewsTabletState;
import sensusmmc.news.SensusNews;
import sensusmmc.news.client.NewsTabletContent;
import sensusmmc.news.client.NewsTabletScreen;
import sensusmmc.news.client.TabletClientHooks;
import sensusmmc.news.feed.NewsFeed;
import sensusmmc.news.feed.NewsPost;

@Mod.EventBusSubscriber(modid = SensusNews.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeClient {
    private static final KeyMapping OPEN_TABLET = new KeyMapping(
        "key.sensusmmc.open_tablet",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_N,
        "key.categories.sensusmmc"
    );

    private static boolean polling;
    private static long nextPoll;

    private ForgeClient() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        if (NewsConfig.requireTablet()) return;
        event.register(OPEN_TABLET);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        NewsTabletState.load(FMLPaths.CONFIGDIR.get().resolve("sensusmmc-news-state.json"));
        event.enqueueWork(() -> {
            TabletClientHooks.setOpener(() -> Minecraft.getInstance().setScreen(new NewsTabletScreen()));
            ItemProperties.register(
                ForgeItems.NEWS_TABLET.get(),
                ResourceLocations.of(SensusNews.MOD_ID, "unread"),
                (stack, level, entity, seed) -> NewsTabletState.isUnread() ? 1.0f : 0.0f
            );
        });
        MinecraftForge.EVENT_BUS.addListener(ForgeClient::clientTick);
    }

    private static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!NewsConfig.requireTablet()) {
            while (OPEN_TABLET.consumeClick()) {
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
