package net.bullettrain.xenopixelsmod.block.custom.copycat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The right-click-to-apply / wrench-to-unbind flow shared by every copycat block in this mod.
 * Adapted from {@code CopycatGlowstoneBlock.useItemOn} so the wing variants get identical
 * behaviour without duplicating it. Uses the generic {@code message.xenopixelsmod.copycat_*}
 * translation keys.
 */
public final class CopycatMaterialSupport {

    private static final ResourceLocation CREATE_WRENCH =
            ResourceLocation.fromNamespaceAndPath("create", "wrench");

    private CopycatMaterialSupport() {
    }

    /**
     * Handle a right-click on a copycat block. {@code selfBlock} is the copycat block itself, used
     * to reject stacking copycats and to detect the "unbound" state.
     */
    public static ItemInteractionResult useItemOn(ItemStack stack, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand,
                                                  BlockHitResult hit, CopycatMaterial holder,
                                                  net.minecraft.world.level.block.Block selfBlock) {
        if (isCreateWrench(stack) && holder.hasCustomMaterial()) {
            if (!level.isClientSide && holder.resetMaterial()) {
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                        SoundSource.BLOCKS, 0.8F, 1.1F);
                player.displayClientMessage(
                        Component.translatable("message.xenopixelsmod.copycat_unbound"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!(stack.getItem() instanceof BlockItem blockItem)
                || blockItem.getBlock() == selfBlock
                || blockItem.getBlock() instanceof CopycatWing) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (holder.hasCustomMaterial()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.xenopixelsmod.copycat_locked"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockState material = orientMaterial(blockItem.getBlock().defaultBlockState(), hit.getDirection());
        if (material.isAir() || material.getRenderShape() == RenderShape.INVISIBLE) {
            return ItemInteractionResult.FAIL;
        }

        if (!level.isClientSide && holder.applyMaterial(material)) {
            level.playSound(null, pos, material.getSoundType(level, pos, player).getPlaceSound(),
                    SoundSource.BLOCKS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable(
                    "message.xenopixelsmod.copycat_applied", material.getBlock().getName()), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** NeoForge connected-texture appearance hook: report the copied material when bound. */
    public static BlockState appearance(BlockState shell, BlockAndTintGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CopycatMaterial holder && holder.hasCustomMaterial()) {
            return holder.getMaterial();
        }
        return shell;
    }

    /** Copy directional state from the clicked face onto the material (matches Create). */
    public static BlockState orientMaterial(BlockState material, Direction face) {
        Direction.Axis axis = face.getAxis();
        if (material.hasProperty(BlockStateProperties.FACING)) {
            material = material.setValue(BlockStateProperties.FACING, face);
        }
        if (material.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && axis != Direction.Axis.Y) {
            material = material.setValue(BlockStateProperties.HORIZONTAL_FACING, face);
        }
        if (material.hasProperty(BlockStateProperties.AXIS)) {
            material = material.setValue(BlockStateProperties.AXIS, axis);
        }
        if (material.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) && axis != Direction.Axis.Y) {
            material = material.setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
        }
        return material;
    }

    public static boolean isCreateWrench(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && CREATE_WRENCH.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
