package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;

/** (fork) vertical flap. Placement and facing match {@link WingPanelForkBlock}. */
public class WingFlapVerticalForkBlock extends WingPanelForkBlock {
    public static final MapCodec<WingFlapVerticalForkBlock> CODEC = simpleCodec(WingFlapVerticalForkBlock::new);

    public WingFlapVerticalForkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(AXIS, Direction.Axis.Z)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
