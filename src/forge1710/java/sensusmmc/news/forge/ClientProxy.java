package sensusmmc.news.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.event.ClickEvent;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import org.lwjgl.input.Keyboard;

import sensusmmc.news.NewsConfig;
import sensusmmc.news.NewsFeedClient;
import sensusmmc.news.NewsTabletState;
import sensusmmc.news.client.NewsTabletContent;
import sensusmmc.news.client.NewsTabletScreen;
import sensusmmc.news.client.TabletClientHooks;
import sensusmmc.sensusnews.NewsFeed;
import sensusmmc.sensusnews.NewsPost;

public final class ClientProxy extends CommonProxy {
    private static final KeyBinding OPEN_TABLET =
        new KeyBinding("key.sensusmmc.open_tablet", Keyboard.KEY_N, "key.categories.sensusmmc");
    private static boolean polling;
    private static long nextPoll;

    @Override
    public void preInit() {
        if (!NewsConfig.requireTablet()) ClientRegistry.registerKeyBinding(OPEN_TABLET);
        FMLCommonHandler.instance().bus().register(this);
        TabletClientHooks.setOpener(() -> Minecraft.getMinecraft().displayGuiScreen(new NewsTabletScreen()));
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
        if (minecraft.thePlayer == null) return;
        NewsTabletContent.ensureBundledFeed();
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
        if (minecraft.thePlayer == null) return;
        String url = NewsFeedClient.absoluteUrl(post.url());
        ChatComponentText link = new ChatComponentText(url);
        ChatStyle style = new ChatStyle();
        style.setColor(EnumChatFormatting.AQUA);
        style.setUnderlined(true);
        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        link.setChatStyle(style);
        ChatComponentText message = new ChatComponentText("[New Sensus article was posted! ");
        message.appendSibling(link);
        message.appendSibling(new ChatComponentText("]"));
        minecraft.thePlayer.addChatComponentMessage(message);
    }
}
