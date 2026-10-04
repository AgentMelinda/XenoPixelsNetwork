package net.bullettrain.xenopixelsmod.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Same air-shift modes as the stock configurator; facing and lit apply on (fork) / copycat flaps. */
public class PanelConfiguratorForkItem extends PanelConfiguratorItem {
    public PanelConfiguratorForkItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("Fork flaps: use Flap facing / Lit mode after shift-clicking air")
                .withStyle(ChatFormatting.AQUA));
    }
}
