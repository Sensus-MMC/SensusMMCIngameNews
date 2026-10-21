package sensusmmc.news.neoforge;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import sensusmmc.news.SensusNews;
import sensusmmc.news.item.NewsTabletItem;

public final class NeoForgeItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SensusNews.MOD_ID);
    public static final DeferredItem<Item> NEWS_TABLET =
        ITEMS.registerItem("news_tablet", NewsTabletItem::new, new Item.Properties().stacksTo(1));

    private NeoForgeItems() {
    }
}
