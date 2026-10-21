package sensusmmc.news.neoforge;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import sensusmmc.news.NewsConfig;
import sensusmmc.news.SensusNews;

@Mod(SensusNews.MOD_ID)
public final class SensusNewsNeoForge {
    public SensusNewsNeoForge(IEventBus modBus) {
        NewsConfig.load(FMLPaths.CONFIGDIR.get().resolve("sensusmmc-news.properties"));
        NeoForgeItems.ITEMS.register(modBus);
        modBus.addListener(SensusNewsNeoForge::addCreative);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(NeoForgeItems.NEWS_TABLET);
        }
    }
}
