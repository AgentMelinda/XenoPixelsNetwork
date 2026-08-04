package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import org.valkyrienskies.core.api.ships.LoadedServerShip;

/**
 * Opens the XYZ target GUI on the client only.
 * <p>
 * Must not reference {@code net.minecraft.client.*} directly — that crashes dedicated
 * servers at item registration ({@code NoClassDefFoundError: Screen}).
 */
public class TargetToolItem extends Item {
    private static final String TAG_HAS_TARGET = "HasTarget";
    private static final String TAG_TARGET = "Target";
    private static final String TAG_TARGET_SHIP = "TargetShipId";

    public TargetToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            ClientScreens.openTargetTool.run();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof ShipVlsGuidanceBlockEntity guidance)) {
            if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
            LoadedServerShip ship = VsShipHelper.getLoadedShipAtFast(server, context.getClickedPos());
            if (ship == null) ship = VsShipHelper.getLoadedShipAt(server, context.getClickedPos());
            if (ship == null) return InteractionResult.PASS;
            var position = ship.getTransform().getPositionInWorld();
            setStoredMovingTarget(context.getItemInHand(), ship.getId(),
                    BlockPos.containing(position.x(), position.y(), position.z()));
            if (player != null) player.displayClientMessage(Component.literal(
                    "§bMoving VS2 target designated: ship#" + ship.getId()), true);
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos target = getStoredTarget(context.getItemInHand());
        if (target == null) {
            if (player != null) player.displayClientMessage(Component.translatable(
                    "chat.xenopixelsmod.target_tool_empty"), true);
            return InteractionResult.CONSUME;
        }

        long movingShipId = getStoredMovingTarget(context.getItemInHand());
        if (movingShipId >= 0) guidance.setMovingTarget(movingShipId, target);
        else guidance.setTargetWorld(target.getX(), target.getY(), target.getZ());
        guidance.recomputeSolution();
        int fleetSynced = guidance.broadcastFleetTarget();
        if (player != null) {
            String rangeError = guidance.rangeLimitMessage();
            if (rangeError != null) {
                player.displayClientMessage(Component.literal("§c" + rangeError), true);
            } else {
                player.displayClientMessage(Component.translatable(
                        "chat.xenopixelsmod.target_applied", target.getX(), target.getY(), target.getZ())
                        .append(fleetSynced > 0 ? Component.literal(" §7| fleet synced §f" + fleetSynced)
                                : Component.empty()), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    public static void setStoredTarget(ItemStack stack, BlockPos target) {
        if (stack == null || stack.isEmpty() || target == null) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_HAS_TARGET, true);
        tag.putLong(TAG_TARGET, target.asLong());
        tag.remove(TAG_TARGET_SHIP);
    }

    public static void setStoredMovingTarget(ItemStack stack, long shipId, BlockPos lastKnown) {
        if (stack == null || stack.isEmpty() || shipId < 0 || lastKnown == null) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_HAS_TARGET, true);
        tag.putLong(TAG_TARGET, lastKnown.asLong());
        tag.putLong(TAG_TARGET_SHIP, shipId);
    }

    public static long getStoredMovingTarget(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag != null && tag.contains(TAG_TARGET_SHIP) ? tag.getLong(TAG_TARGET_SHIP) : -1L;
    }

    public static BlockPos getStoredTarget(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(TAG_HAS_TARGET) || !tag.contains(TAG_TARGET)) return null;
        return BlockPos.of(tag.getLong(TAG_TARGET));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        BlockPos target = getStoredTarget(stack);
        if (target == null) {
            tooltip.add(Component.translatable("tooltip.xenopixelsmod.target_tool_empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            long moving = getStoredMovingTarget(stack);
            if (moving >= 0) tooltip.add(Component.literal("Moving VS2 ship #" + moving)
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.xenopixelsmod.target_tool_target",
                            target.getX(), target.getY(), target.getZ())
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.xenopixelsmod.target_tool_apply")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
