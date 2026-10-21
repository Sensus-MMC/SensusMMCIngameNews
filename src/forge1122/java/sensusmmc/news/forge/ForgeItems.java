package sensusmmc.news.forge;

import net.minecraft.item.Item;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import sensusmmc.news.SensusNews;
import sensusmmc.news.item.NewsTabletItem;

public final class ForgeItems {
    public static final Item NEWS_TABLET = new NewsTabletItem();

    private ForgeItems() {
    }

    public static void register() {
        NEWS_TABLET.setRegistryName(SensusNews.MOD_ID, "news_tablet");
        NEWS_TABLET.setTranslationKey(SensusNews.MOD_ID + ".news_tablet");
        ForgeRegistries.ITEMS.register(NEWS_TABLET);
    }
}
