package sensusmmc.news.forge;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import sensusmmc.news.NewsConfig;
import sensusmmc.news.SensusNews;

@Mod(SensusNews.MOD_ID)
public final class SensusNewsForge {
    public SensusNewsForge() {
        NewsConfig.load(FMLPaths.CONFIGDIR.get().resolve("sensusmmc-news.properties"));
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ForgeItems.ITEMS.register(modBus);
        modBus.addListener(SensusNewsForge::addCreative);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(ForgeItems.NEWS_TABLET);
        }
    }
}
