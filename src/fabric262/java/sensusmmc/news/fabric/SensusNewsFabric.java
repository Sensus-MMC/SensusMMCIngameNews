package sensusmmc.news.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
        Registry.register(
            BuiltInRegistries.ITEM,
            Identifier.fromNamespaceAndPath(SensusNews.MOD_ID, "news_tablet"),
            NEWS_TABLET
        );
        ResourceKey<CreativeModeTab> tools = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.withDefaultNamespace("tools_and_utilities")
        );
        CreativeModeTabEvents.modifyOutputEvent(tools).register(output -> output.accept(NEWS_TABLET));
    }
}
