package sensusmmc.news.forge;

import java.io.File;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import sensusmmc.news.NewsConfig;
import sensusmmc.news.NewsTabletState;
import sensusmmc.news.SensusNews;

@Mod(
    modid = SensusNews.MOD_ID,
    name = "SensusMMC In-Game News",
    version = "0.1.0",
    acceptedMinecraftVersions = "[1.12,1.13)"
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
        ForgeItems.register();
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
