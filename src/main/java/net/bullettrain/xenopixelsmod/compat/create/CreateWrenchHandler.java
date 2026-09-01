package net.bullettrain.xenopixelsmod.compat.create;

import net.neoforged.fml.common.EventBusSubscriber;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.custom.MissileChunkLoaderBlock;
import net.bullettrain.xenopixelsmod.block.custom.MissileTubeBlock;
import net.bullettrain.xenopixelsmod.block.custom.ShipThrusterBlock;
import net.bullettrain.xenopixelsmod.block.custom.ShipVlsGuidanceBlock;
import net.bullettrain.xenopixelsmod.block.custom.CopycatGlowstoneBlock;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelBlock;
import net.bullettrain.xenopixelsmod.block.entity.CopycatGlowstoneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * Soft Create wrench support for VS2 ship blocks (no hard Create compile dep).
 * <p>
 * When the player uses {@code create:wrench} on thruster / tube / guidance / chunk loader,
 * rotates {@code FACING} around the clicked face axis (same idea as Create's IWrenchable).
 * Sneak-wrench picks the block up like Create does for most machines. On a wing panel, sets its
 * surface {@code AXIS} directly from the player's look direction instead of a facing.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class CreateWrenchHandler {
    private static final ResourceLocation CREATE_WRENCH = ResourceLocation.fromNamespaceAndPath("create", "wrench");

    private CreateWrenchHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !CREATE_WRENCH.equals(id)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        // Copycat materials are intentionally one-shot. The Create wrench is the sole
        // unbinding tool, and this high-priority event makes that reliable even when Create's
        // own wrench handlers would otherwise consume the interaction first.
        if (state.getBlock() instanceof CopycatGlowstoneBlock
                && level.getBlockEntity(pos) instanceof CopycatGlowstoneBlockEntity copycat
                && copycat.hasCustomMaterial()) {
            Player player = event.getEntity();
            if (!level.isClientSide && copycat.resetMaterial()) {
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                        SoundSource.BLOCKS, 0.8f, 1.1f);
                player.displayClientMessage(Component.translatable(
                        "message.xenopixelsmod.copycat_glowstone_unbound"), true);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            event.setCanceled(true);
            return;
        }

        if (state.getBlock() instanceof WingPanelBlock) {
            // Sets AXIS directly from the player's own look direction — the exact same
            // Direction.getNearest(lookAngle) construction WingPanelBlock.getStateForPlacement
            // already uses when the panel is first placed — rather than depending on which thin
            // edge face the raycast happened to hit. A wing panel is a 3px-thick slab; both a
            // click-face-aware design and a click-anywhere cycle were tried first and still
            // depend on the raycast actually registering a hit on that sliver at all. This
            // doesn't fix a raycast miss (nothing running from inside the interaction can, since
            // the event never fires with this block as the target), but it does mean any click
            // that DOES land sets the axis in one step, correctly, from wherever the player is
            // actually looking — no cycling, no face precision needed once you've hit the block.
            Player player = event.getEntity();
            Direction.Axis next = player != null
                    ? Direction.getNearest(player.getLookAngle()).getAxis()
                    : nextAxis(state.getValue(WingPanelBlock.AXIS));
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(WingPanelBlock.AXIS, next), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.7f, 1.2f);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            event.setCanceled(true);
            return;
        }

        DirectionProperty facing = facingProperty(state.getBlock());
        if (facing == null || !state.hasProperty(facing)) return;

        Player player = event.getEntity();
        if (player != null && player.isShiftKeyDown()) {
            // Sneak-wrench: pick up (Create-style)
            if (!level.isClientSide) {
                Block.dropResources(state, level, pos, level.getBlockEntity(pos), player, stack);
                level.removeBlock(pos, false);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 1.1f);
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            event.setCanceled(true);
            return;
        }

        Direction clicked = event.getFace();
        if (clicked == null) clicked = Direction.UP;
        Direction current = state.getValue(facing);
        Direction next = rotateAround(current, clicked.getAxis());
        if (next == current) {
            // Same axis as facing — flip instead so every click still does something
            next = current.getOpposite();
        }
        BlockState rotated = state.setValue(facing, next);
        if (!rotated.canSurvive(level, pos)) {
            event.setCancellationResult(InteractionResult.PASS);
            return;
        }

        if (!level.isClientSide) {
            level.setBlock(pos, rotated, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.7f, 1.2f);
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
    }

    private static DirectionProperty facingProperty(Block block) {
        if (block instanceof ShipThrusterBlock) return ShipThrusterBlock.FACING;
        if (block instanceof MissileTubeBlock) return MissileTubeBlock.FACING;
        if (block instanceof ShipVlsGuidanceBlock) return ShipVlsGuidanceBlock.FACING;
        if (block instanceof MissileChunkLoaderBlock) return MissileChunkLoaderBlock.FACING;
        return null;
    }

    /** Clockwise step of {@code dir} around {@code axis} (Create-style). */
    private static Direction rotateAround(Direction dir, Direction.Axis axis) {
        return dir.getClockWise(axis);
    }

    /** X → Y → Z → X, matching {@code PanelRole.next()}'s simple cycling. */
    private static Direction.Axis nextAxis(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.Axis.Y;
            case Y -> Direction.Axis.Z;
            case Z -> Direction.Axis.X;
        };
    }
}
