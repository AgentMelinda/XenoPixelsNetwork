package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link WingPanelBlock} that always mounts vertically ({@code AXIS=Z}), matching Warium's own
 * separate vertical control-surface block. A vertical surface is already full-height, so it can
 * only ever swing sideways or rock fore/aft, never tilt up/down — the same reason a real rudder
 * cannot be reshaped into an elevator by turning it (see {@link PanelRole#needsHorizontalMount()}
 * for the fuller version of this). This is the block to reach for a rudder (YAW role).
 *
 * <p>Everything else is inherited unchanged from {@link WingPanelBlock}; only where it is allowed
 * to point differs.
 */
public class WingFlapVerticalBlock extends WingPanelBlock {

    public static final MapCodec<WingFlapVerticalBlock> CODEC = simpleCodec(WingFlapVerticalBlock::new);

    public WingFlapVerticalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, Direction.Axis.Z);
    }
}
