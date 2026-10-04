package net.bullettrain.xenopixelsmod.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Panel configurator. Shift-right-click in the air to choose a mode, then right-click a
 * panel, flap, or engine to apply it (role, deflect, facing, orient, axis, lit, or linker).
 */
public class PanelConfiguratorItem extends Item {

    public PanelConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ShipToolModes.announce(player, ShipToolModes.cycle(stack, ShipToolMode.ROLE));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!level.isClientSide) {
            ShipToolModes.announce(player, ShipToolModes.get(stack, ShipToolMode.ROLE));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ShipToolMode mode = ShipToolModes.get(context.getItemInHand(), ShipToolMode.ROLE);
        if (mode == ShipToolMode.LINKER) {
            return TargetToolItem.applyLinker(context);
        }
        return ShipToolActions.apply(context, mode);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ShipToolMode mode = ShipToolModes.get(stack, ShipToolMode.ROLE);
        tooltip.add(Component.literal("Mode: " + mode.title()).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Shift-right-click air to change mode")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal(mode.hint()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click a panel or engine to apply the selected mode")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
