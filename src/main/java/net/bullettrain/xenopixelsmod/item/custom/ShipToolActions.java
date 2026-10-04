package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.block.custom.PanelRole;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelBlock;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelForkBlock;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelPose;
import net.bullettrain.xenopixelsmod.block.entity.WingPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Applies the selected {@link ShipToolMode} to a clicked block. */
public final class ShipToolActions {
    private ShipToolActions() {}

    public static InteractionResult apply(UseOnContext context, ShipToolMode mode) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        return switch (mode) {
            case LINKER -> InteractionResult.PASS;
            case ROLE -> applyRole(level, player, pos, state);
            case DEFLECT -> applyDeflect(level, player, pos, state);
            case FACING -> applyFacing(level, player, pos, state);
            case ORIENT -> applyOrient(level, player, pos, state);
            case AXIS -> applyAxis(level, player, pos, state);
            case LIT -> applyLit(level, player, pos, state);
        };
    }

    public static InteractionResult applyRole(Level level, @Nullable Player player, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof WingPanelBlock) || !state.hasProperty(WingPanelBlock.ROLE)) {
            return fail(player, "That is not a wing panel");
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        PanelRole next = nextRole(state.getValue(WingPanelBlock.ROLE));
        level.setBlock(pos, state.setValue(WingPanelBlock.ROLE, next), Block.UPDATE_CLIENTS);
        resetDeflect(level, pos);
        tell(player, "§bPanel role: §f" + next.getSerializedName().toUpperCase(Locale.ROOT));
        return InteractionResult.CONSUME;
    }

    public static InteractionResult applyDeflect(Level level, @Nullable Player player, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof WingPanelBlock) || !state.hasProperty(WingPanelBlock.INVERT)) {
            return fail(player, "That is not a wing panel");
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        boolean next = !state.getValue(WingPanelBlock.INVERT);
        level.setBlock(pos, state.setValue(WingPanelBlock.INVERT, next), Block.UPDATE_CLIENTS);
        resetDeflect(level, pos);
        tell(player, "§bDeflect: §f" + (next ? "INVERT −" : "NORMAL +"));
        return InteractionResult.CONSUME;
    }

    public static InteractionResult applyFacing(Level level, @Nullable Player player, BlockPos pos, BlockState state) {
        if (state.hasProperty(WingPanelForkBlock.FACING)) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            Direction next = nextFacing(state.getValue(WingPanelForkBlock.FACING));
            level.setBlock(pos, WingPanelForkBlock.withFacing(state, next), Block.UPDATE_CLIENTS);
            tell(player, "§bFlap facing: §f" + next.getSerializedName());
            return InteractionResult.CONSUME;
        }
        if (!(state.getBlock() instanceof WingPanelBlock) || !state.hasProperty(WingPanelBlock.AXIS)) {
            return fail(player, "That block has no fork facing — use a (fork) flap");
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Direction.Axis next = nextAxis(state.getValue(WingPanelBlock.AXIS));
        level.setBlock(pos, state.setValue(WingPanelBlock.AXIS, next), Block.UPDATE_CLIENTS);
        tell(player, "§bMount axis: §f" + next.getName().toUpperCase(Locale.ROOT)
                + " §7(stock — no six-way facing)");
        return InteractionResult.CONSUME;
    }

    public static InteractionResult applyOrient(Level level, @Nullable Player player, BlockPos pos, BlockState state) {
        if (!state.hasProperty(WingPanelForkBlock.FACING)) {
            return fail(player, "That block has no hinge spin — use a (fork) flap");
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof WingPanelBlockEntity panel)) {
            return fail(player, "That flap has no hinge data");
        }
        int next = WingPanelBlockEntity.normalizeHinge(panel.getHingeRotation() + 90);
        panel.setHingeRotation(next);
        Direction stub = WingPanelPose.stubWorldDir(state.getValue(WingPanelForkBlock.FACING), next);
        tell(player, "§bFlap orient: §f" + next + "° §7stub " + stub.getSerializedName());
        return InteractionResult.CONSUME;
    }

    public static InteractionResult applyFacingOnAxis(Level level, @Nullable Player player, BlockPos pos,
                                                       BlockState state, Direction.Axis axis) {
        if (axis == null) {
            return fail(player, "Missing flap axis");
        }
        if (state.hasProperty(WingPanelForkBlock.FACING)) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            Direction next = nextFacingOnAxis(state.getValue(WingPanelForkBlock.FACING), axis);
            level.setBlock(pos, WingPanelForkBlock.withFacing(state, next), Block.UPDATE_CLIENTS);
            tell(player, "§bFlap dir " + axis.getName().toUpperCase(Locale.ROOT)
                    + ": §f" + next.getSerializedName());
            return InteractionResult.CONSUME;
        }
        if (!(state.getBlock() instanceof WingPanelBlock) || !state.hasProperty(WingPanelBlock.AXIS)) {
            return fail(player, "That block has no fork facing — use a (fork) flap");
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        level.setBlock(pos, state.setValue(WingPanelBlock.AXIS, axis), Block.UPDATE_CLIENTS);
        tell(player, "§bMount axis: §f" + axis.getName().toUpperCase(Locale.ROOT)
                + " §7(stock — no per-axis facing)");
        return InteractionResult.CONSUME;
    }

    public static InteractionResult applyAxis(Level level, @Nullable Player player, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof WingPanelBlock) || !state.hasProperty(WingPanelBlock.AXIS)) {
            return fail(player, "That is not a wing panel");
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Direction.Axis next = nextAxis(state.getValue(WingPanelBlock.AXIS));
        BlockState updated = state.setValue(WingPanelBlock.AXIS, next);
        if (updated.hasProperty(WingPanelForkBlock.FACING)) {
            Direction current = updated.getValue(WingPanelForkBlock.FACING);
            if (current.getAxis() != next) {
                updated = WingPanelForkBlock.withFacing(updated,
                        Direction.get(current.getAxisDirection(), next));
            }
        }
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        tell(player, "§bMount axis: §f" + next.getSerializedName().toUpperCase(Locale.ROOT));
        return InteractionResult.CONSUME;
    }

    public static InteractionResult applyLit(Level level, @Nullable Player player, BlockPos pos, BlockState state) {
        Block next = litCounterpart(state.getBlock());
        if (next == null) {
            return fail(player, "That is not a copycat wing — lit mode only swaps copycat panels");
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockState copied = copyShared(state, next.defaultBlockState());
        boolean nowLit = isLitCopycat(next);
        CompoundTagBackup backup = CompoundTagBackup.capture(level, pos);
        level.setBlock(pos, copied, Block.UPDATE_CLIENTS);
        backup.restore(level, pos);
        tell(player, nowLit ? "§eLit mode: §aON" : "§eLit mode: §7OFF");
        return InteractionResult.CONSUME;
    }

    public static PanelRole nextRole(PanelRole role) {
        PanelRole[] values = PanelRole.values();
        return values[(role.ordinal() + 1) % values.length];
    }

    public static Direction nextFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.UP;
            case UP -> Direction.DOWN;
            case DOWN -> Direction.NORTH;
        };
    }

    /** Flip +/− on {@code axis}. If the current facing is on another axis, start at +axis. */
    public static Direction nextFacingOnAxis(Direction facing, Direction.Axis axis) {
        if (facing != null && facing.getAxis() == axis) {
            return facing.getOpposite();
        }
        return Direction.get(Direction.AxisDirection.POSITIVE, axis);
    }

    public static Direction.Axis nextAxis(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.Axis.Y;
            case Y -> Direction.Axis.Z;
            case Z -> Direction.Axis.X;
        };
    }

    public static @Nullable Block litCounterpart(Block block) {
        if (block == ModBlocks.COPYCAT_WING_PANEL.get()) return ModBlocks.COPYCAT_WING_PANEL_LIT.get();
        if (block == ModBlocks.COPYCAT_WING_PANEL_LIT.get()) return ModBlocks.COPYCAT_WING_PANEL.get();
        if (block == ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL.get()) return ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL_LIT.get();
        if (block == ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL_LIT.get()) return ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL.get();
        if (block == ModBlocks.COPYCAT_WING_FLAP_VERTICAL.get()) return ModBlocks.COPYCAT_WING_FLAP_VERTICAL_LIT.get();
        if (block == ModBlocks.COPYCAT_WING_FLAP_VERTICAL_LIT.get()) return ModBlocks.COPYCAT_WING_FLAP_VERTICAL.get();
        if (block == ModBlocks.COPYCAT_WING_PANEL_FORK.get()) return ModBlocks.COPYCAT_WING_PANEL_LIT_FORK.get();
        if (block == ModBlocks.COPYCAT_WING_PANEL_LIT_FORK.get()) return ModBlocks.COPYCAT_WING_PANEL_FORK.get();
        if (block == ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL_FORK.get()) {
            return ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL_LIT_FORK.get();
        }
        if (block == ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL_LIT_FORK.get()) {
            return ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL_FORK.get();
        }
        if (block == ModBlocks.COPYCAT_WING_FLAP_VERTICAL_FORK.get()) {
            return ModBlocks.COPYCAT_WING_FLAP_VERTICAL_LIT_FORK.get();
        }
        if (block == ModBlocks.COPYCAT_WING_FLAP_VERTICAL_LIT_FORK.get()) {
            return ModBlocks.COPYCAT_WING_FLAP_VERTICAL_FORK.get();
        }
        return null;
    }

    public static boolean isLitCopycat(Block block) {
        return block == ModBlocks.COPYCAT_WING_PANEL_LIT.get()
                || block == ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL_LIT.get()
                || block == ModBlocks.COPYCAT_WING_FLAP_VERTICAL_LIT.get()
                || block == ModBlocks.COPYCAT_WING_PANEL_LIT_FORK.get()
                || block == ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL_LIT_FORK.get()
                || block == ModBlocks.COPYCAT_WING_FLAP_VERTICAL_LIT_FORK.get();
    }

    private static BlockState copyShared(BlockState from, BlockState to) {
        BlockState next = to;
        if (from.hasProperty(WingPanelBlock.AXIS) && next.hasProperty(WingPanelBlock.AXIS)) {
            next = next.setValue(WingPanelBlock.AXIS, from.getValue(WingPanelBlock.AXIS));
        }
        if (from.hasProperty(WingPanelBlock.ROLE) && next.hasProperty(WingPanelBlock.ROLE)) {
            next = next.setValue(WingPanelBlock.ROLE, from.getValue(WingPanelBlock.ROLE));
        }
        if (from.hasProperty(WingPanelBlock.INVERT) && next.hasProperty(WingPanelBlock.INVERT)) {
            next = next.setValue(WingPanelBlock.INVERT, from.getValue(WingPanelBlock.INVERT));
        }
        if (from.hasProperty(WingPanelForkBlock.FACING) && next.hasProperty(WingPanelForkBlock.FACING)) {
            next = next.setValue(WingPanelForkBlock.FACING, from.getValue(WingPanelForkBlock.FACING));
        }
        return next;
    }

    private static void resetDeflect(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WingPanelBlockEntity panel) {
            panel.setTargetDeflectDeg(0.0);
        }
    }

    private static InteractionResult fail(@Nullable Player player, String message) {
        tell(player, "§c" + message);
        return InteractionResult.FAIL;
    }

    private static void tell(@Nullable Player player, String message) {
        if (player != null) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }

    /** Saves copycat skin + deflection across a block-id swap. */
    private record CompoundTagBackup(@Nullable BlockState material, boolean custom, double deflect, int hinge) {
        static CompoundTagBackup capture(Level level, BlockPos pos) {
            if (level.getBlockEntity(pos) instanceof WingPanelBlockEntity panel) {
                return new CompoundTagBackup(panel.getMaterial(), panel.hasCustomMaterial(),
                        panel.getTargetDeflectDeg(), panel.getHingeRotation());
            }
            return new CompoundTagBackup(null, false, 0.0, 0);
        }

        void restore(Level level, BlockPos pos) {
            if (!(level.getBlockEntity(pos) instanceof WingPanelBlockEntity panel)) {
                return;
            }
            if (custom && material != null) {
                panel.applyMaterial(material);
            }
            panel.setTargetDeflectDeg(deflect);
            panel.setHingeRotation(hinge);
        }
    }
}
