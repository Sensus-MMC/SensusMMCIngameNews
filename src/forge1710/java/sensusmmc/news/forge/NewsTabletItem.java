package sensusmmc.news.forge;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import sensusmmc.news.NewsTabletState;
import sensusmmc.news.client.TabletClientHooks;

public final class NewsTabletItem extends Item {
    private IIcon readIcon;
    private IIcon unreadIcon;

    public NewsTabletItem() {
        setUnlocalizedName("sensusmmc.news_tablet");
        setTextureName("sensusmmc:newstablet_off");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabMisc);
    }

    @Override
    public void registerIcons(IIconRegister register) {
        readIcon = register.registerIcon("sensusmmc:newstablet_off");
        unreadIcon = register.registerIcon("sensusmmc:newstablet");
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return NewsTabletState.isUnread() ? unreadIcon : readIcon;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            TabletClientHooks.open();
        }
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        int unread = NewsTabletState.unreadCount();
        if (unread > 0) {
            String suffix = unread == 1 ? "" : "s";
            tooltip.add(EnumChatFormatting.AQUA.toString() + "[" + unread + " unread article" + suffix + "!]");
        }
    }
}
