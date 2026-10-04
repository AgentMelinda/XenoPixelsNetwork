package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.BaseEntityBlock;

/** (fork) seated control chair. Same model as the stock chair. */
public class PilotSeatForkBlock extends PilotSeatBlock {
    public static final MapCodec<PilotSeatForkBlock> CODEC = simpleCodec(PilotSeatForkBlock::new);

    public PilotSeatForkBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected EntityType<XenoPilotSeatEntity> seatType() {
        return ModEntities.PILOT_SEAT_FORK.get();
    }
}
