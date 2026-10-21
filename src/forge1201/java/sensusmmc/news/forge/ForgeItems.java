package sensusmmc.news.forge;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import sensusmmc.news.SensusNews;
import sensusmmc.news.item.NewsTabletItem;

public final class ForgeItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, SensusNews.MOD_ID);
    public static final RegistryObject<Item> NEWS_TABLET =
        ITEMS.register("news_tablet", () -> new NewsTabletItem(new Item.Properties().stacksTo(1)));

    private ForgeItems() {
    }
}
