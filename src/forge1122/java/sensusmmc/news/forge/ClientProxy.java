package sensusmmc.news.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemMeshDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;

import sensusmmc.news.NewsConfig;
import sensusmmc.news.NewsFeedClient;
import sensusmmc.news.NewsTabletState;
import sensusmmc.news.SensusNews;
import sensusmmc.news.client.NewsTabletContent;
import sensusmmc.news.client.NewsTabletScreen;
import sensusmmc.news.client.TabletClientHooks;
import sensusmmc.news.feed.NewsFeed;
import sensusmmc.news.feed.NewsPost;

public final class ClientProxy extends CommonProxy {
    private static final KeyBinding OPEN_TABLET =
        new KeyBinding("key.sensusmmc.open_tablet", Keyboard.KEY_N, "key.categories.sensusmmc");
    private static boolean polling;
    private static long nextPoll;

    @Override
    public void preInit() {
        if (!NewsConfig.requireTablet()) ClientRegistry.registerKeyBinding(OPEN_TABLET);
        MinecraftForge.EVENT_BUS.register(this);
        TabletClientHooks.setOpener(() -> Minecraft.getMinecraft().displayGuiScreen(new NewsTabletScreen()));
        ModelResourceLocation readModel =
            new ModelResourceLocation(SensusNews.MOD_ID + ":news_tablet", "inventory");
        ModelResourceLocation unreadModel =
            new ModelResourceLocation(SensusNews.MOD_ID + ":news_tablet_unread", "inventory");
        ModelLoader.registerItemVariants(ForgeItems.NEWS_TABLET, readModel, unreadModel);
        ModelLoader.setCustomMeshDefinition(
            ForgeItems.NEWS_TABLET,
            stack -> NewsTabletState.isUnread() ? unreadModel : readModel
        );
    }

    @Override
    public void init() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen == null && !NewsConfig.requireTablet() && OPEN_TABLET.isPressed()) {
            TabletClientHooks.open();
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
                minecraft.addScheduledTask(() -> {
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
                minecraft.addScheduledTask(() -> polling = false);
            }
        });
    }

    private static void notifyPost(NewsPost post) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) return;
        String url = NewsFeedClient.absoluteUrl(post.url());
        TextComponentString link = new TextComponentString(url);
        link.getStyle()
            .setColor(TextFormatting.AQUA)
            .setUnderlined(true)
            .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        TextComponentString message = new TextComponentString("[New Sensus article was posted! ");
        message.appendSibling(link);
        message.appendSibling(new TextComponentString("]"));
        minecraft.player.sendMessage(message);
    }
}
