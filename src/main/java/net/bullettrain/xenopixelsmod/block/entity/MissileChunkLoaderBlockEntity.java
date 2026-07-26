package net.bullettrain.xenopixelsmod.block.entity;

import net.bullettrain.xenopixelsmod.compat.ballistix.MissileChunkLoadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Periodic force-load tickets for ship VLS / missile corridors.
 */
public class MissileChunkLoaderBlockEntity extends BlockEntity {
    private boolean alwaysOn = true;
    private int radius = 2;

    public MissileChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MISSILE_CHUNK_LOADER.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (sl.getGameTime() % 40 != 0) return;
        boolean powered = alwaysOn || level.hasNeighborSignal(worldPosition);
        if (!powered) return;
        MissileChunkLoadManager.forceNear(sl, worldPosition, radius, 20 * 8);
    }

    public void toggleAlwaysOn() {
        alwaysOn = !alwaysOn;
        setChanged();
    }

    public boolean isAlwaysOn() {
        return alwaysOn;
    }

    public int getRadius() {
        return radius;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("AlwaysOn", alwaysOn);
        tag.putInt("Radius", radius);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        alwaysOn = !tag.contains("AlwaysOn") || tag.getBoolean("AlwaysOn");
        radius = Math.max(1, Math.min(4, tag.contains("Radius") ? tag.getInt("Radius") : 2));
    }
}
