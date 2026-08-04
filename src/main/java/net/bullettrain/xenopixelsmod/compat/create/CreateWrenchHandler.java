package net.bullettrain.xenopixelsmod.compat.create;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.custom.MissileChunkLoaderBlock;
import net.bullettrain.xenopixelsmod.block.custom.MissileTubeBlock;
import net.bullettrain.xenopixelsmod.block.custom.ShipThrusterBlock;
import net.bullettrain.xenopixelsmod.block.custom.ShipVlsGuidanceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Soft Create wrench support for VS2 ship blocks (no hard Create compile dep).
 * <p>
 * When the player uses {@code create:wrench} on thruster / tube / guidance / chunk loader,
 * rotates {@code FACING} around the clicked face axis (same idea as Create's IWrenchable).
 * Sneak-wrench picks the block up like Create does for most machines.
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class CreateWrenchHandler {
    private static final ResourceLocation CREATE_WRENCH = new ResourceLocation("create", "wrench");

    private CreateWrenchHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !CREATE_WRENCH.equals(id)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
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
}
