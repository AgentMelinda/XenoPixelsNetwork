package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;

/** (fork) horizontal flap. Placement and facing match {@link WingPanelForkBlock}. */
public class WingFlapHorizontalForkBlock extends WingPanelForkBlock {
    public static final MapCodec<WingFlapHorizontalForkBlock> CODEC = simpleCodec(WingFlapHorizontalForkBlock::new);

    public WingFlapHorizontalForkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
