package sensusmmc.news.item;

import sensusmmc.news.ResourceLocations;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import sensusmmc.news.NewsTabletState;
import sensusmmc.news.client.TabletClientHooks;

public final class NewsTabletItem extends Item {
    public NewsTabletItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            TabletClientHooks.open();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        appendTooltip(tooltip);
    }

    private static void appendTooltip(List<Component> tooltip) {
        int unread = NewsTabletState.unreadCount();
        if (unread > 0) {
            String suffix = unread == 1 ? "" : "s";
            tooltip.add(Component.literal("[" + unread + " unread article" + suffix + "!]")
                .withStyle(ChatFormatting.AQUA));
        }
    }
}
