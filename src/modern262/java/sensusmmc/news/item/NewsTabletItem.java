package sensusmmc.news.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import sensusmmc.news.NewsTabletState;
import sensusmmc.news.client.TabletClientHooks;

public final class NewsTabletItem extends Item {
    public NewsTabletItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            TabletClientHooks.open();
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        Item.TooltipContext context,
        TooltipDisplay display,
        Consumer<Component> tooltip,
        TooltipFlag flag
    ) {
        int unread = NewsTabletState.unreadCount();
        if (unread > 0) {
            String suffix = unread == 1 ? "" : "s";
            tooltip.accept(Component.literal("[" + unread + " unread article" + suffix + "!]")
                .withStyle(ChatFormatting.AQUA));
        }
    }
}
