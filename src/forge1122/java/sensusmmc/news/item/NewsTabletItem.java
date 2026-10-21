package sensusmmc.news.item;

import java.util.List;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import sensusmmc.news.NewsTabletState;
import sensusmmc.news.client.TabletClientHooks;

public final class NewsTabletItem extends Item {
    public NewsTabletItem() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            TabletClientHooks.open();
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        int unread = NewsTabletState.unreadCount();
        if (unread > 0) {
            String suffix = unread == 1 ? "" : "s";
            tooltip.add(TextFormatting.AQUA.toString() + "[" + unread + " unread article" + suffix + "!]");
        }
    }
}
