package sensusmmc.news.forge;

import java.io.File;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;

import sensusmmc.news.NewsConfig;
import sensusmmc.news.NewsTabletState;
import sensusmmc.news.SensusNews;

@Mod(
    modid = SensusNews.MOD_ID,
    name = "SensusMMC In-Game News",
    version = "0.1.0",
    acceptedMinecraftVersions = "[1.7.10]"
)
public final class SensusNewsForge {
    @SidedProxy(
        clientSide = "sensusmmc.news.forge.ClientProxy",
        serverSide = "sensusmmc.news.forge.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        NewsConfig.load(event.getSuggestedConfigurationFile().toPath());
        NewsTabletState.load(new File(event.getModConfigurationDirectory(), "sensusmmc-news-state.json").toPath());
        GameRegistry.registerItem(ForgeItems.NEWS_TABLET, "news_tablet");
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
