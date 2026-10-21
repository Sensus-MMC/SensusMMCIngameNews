package sensusmmc.news.fabric;

import sensusmmc.news.ResourceLocations;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import sensusmmc.news.NewsConfig;
import sensusmmc.news.SensusNews;
import sensusmmc.news.item.NewsTabletItem;

public final class SensusNewsFabric implements ModInitializer {
    public static final Item NEWS_TABLET = new NewsTabletItem(new Item.Properties().stacksTo(1));

    @Override
    public void onInitialize() {
        NewsConfig.load(FabricLoader.getInstance().getConfigDir().resolve("sensusmmc-news.properties"));
        Registry.register(BuiltInRegistries.ITEM, ResourceLocations.of(SensusNews.MOD_ID, "news_tablet"), NEWS_TABLET);
        ResourceKey<CreativeModeTab> tools = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocations.of("minecraft", "tools_and_utilities")
        );
        ItemGroupEvents.modifyEntriesEvent(tools).register(entries -> entries.accept(NEWS_TABLET));
    }
}
