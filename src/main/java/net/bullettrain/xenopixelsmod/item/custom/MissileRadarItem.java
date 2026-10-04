package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.bullettrain.xenopixelsmod.missile.MissileLookTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Look-ray coordinate pad for ballistic missiles. Right-click stores the block you are looking
 * at (200 blocks). Right-click a guidance computer to load those coords. Shift-right-click air
 * clears. Does not open the target GUI or link flaps.
 */
public class MissileRadarItem extends Item {
    private static final String TAG_HAS_TARGET = "HasTarget";
    private static final String TAG_TARGET = "Target";

    public MissileRadarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                clearStoredTarget(stack);
                player.displayClientMessage(Component.translatable("chat.xenopixelsmod.missile_radar_cleared"),
                        true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!level.isClientSide) {
            captureLook(player, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        if (be instanceof ShipVlsGuidanceBlockEntity guidance) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            return applyToComputer(player, stack, guidance);
        }
        if (player != null && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                clearStoredTarget(stack);
                player.displayClientMessage(Component.translatable("chat.xenopixelsmod.missile_radar_cleared"),
                        true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player != null) {
            captureLook(player, stack);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void captureLook(Player player, ItemStack stack) {
        BlockPos pos = MissileLookTarget.fromPlayer(player);
        if (pos == null) {
            player.displayClientMessage(Component.translatable("chat.xenopixelsmod.missile_radar_miss"), true);
            return;
        }
        setStoredTarget(stack, pos);
        player.displayClientMessage(Component.translatable(
                "chat.xenopixelsmod.missile_radar_stored", pos.getX(), pos.getY(), pos.getZ()), true);
    }

    private static InteractionResult applyToComputer(@Nullable Player player, ItemStack stack,
                                                     ShipVlsGuidanceBlockEntity guidance) {
        BlockPos target = getStoredTarget(stack);
        if (target == null) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("chat.xenopixelsmod.missile_radar_empty"),
                        true);
            }
            return InteractionResult.CONSUME;
        }
        guidance.setTargetWorld(target.getX(), target.getY(), target.getZ());
        guidance.recomputeSolution();
        int fleetSynced = guidance.broadcastFleetTarget();
        if (player != null) {
            String rangeError = guidance.rangeLimitMessage();
            if (rangeError != null) {
                player.displayClientMessage(Component.literal("§c" + rangeError), true);
            } else {
                player.displayClientMessage(Component.translatable(
                                "chat.xenopixelsmod.target_applied",
                                target.getX(), target.getY(), target.getZ())
                        .append(fleetSynced > 0
                                ? Component.literal(" §7| fleet synced §f" + fleetSynced)
                                : Component.empty()), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    public static void setStoredTarget(ItemStack stack, BlockPos target) {
        if (stack == null || stack.isEmpty() || target == null) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean(TAG_HAS_TARGET, true);
            tag.putLong(TAG_TARGET, target.asLong());
        });
    }

    public static void clearStoredTarget(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(TAG_HAS_TARGET);
            tag.remove(TAG_TARGET);
        });
    }

    public static @Nullable BlockPos getStoredTarget(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.getBoolean(TAG_HAS_TARGET) || !tag.contains(TAG_TARGET)) return null;
        return BlockPos.of(tag.getLong(TAG_TARGET));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        BlockPos target = getStoredTarget(stack);
        if (target == null) {
            tooltip.add(Component.translatable("tooltip.xenopixelsmod.missile_radar_empty")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.xenopixelsmod.missile_radar_target",
                            target.getX(), target.getY(), target.getZ())
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.xenopixelsmod.missile_radar_look")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.xenopixelsmod.missile_radar_apply")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
