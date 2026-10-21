package sensusmmc.news.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
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
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modBus.addListener(NeoForgeClient::registerKeys);
            modBus.addListener(NeoForgeClient::setup);
        }
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        ResourceKey<CreativeModeTab> tools = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.withDefaultNamespace("tools_and_utilities")
        );
        if (event.getTabKey().equals(tools)) {
            event.accept(NeoForgeItems.NEWS_TABLET);
        }
    }
}
