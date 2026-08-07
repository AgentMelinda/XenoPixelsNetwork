package net.bullettrain.xenopixelsmod.block.entity;

import net.bullettrain.xenopixelsmod.missile.MissileChunkLoadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Force-load tickets for missile corridors. Sparse tick; redstone cached.
 */
public class MissileChunkLoaderBlockEntity extends BlockEntity {
    private boolean alwaysOn = true;
    private int radius = 1; // default 1 (was 2) — cheaper forced chunks
    private boolean cachedRedstone;
    private static final int TICK_INTERVAL = 80; // 4s

    public MissileChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MISSILE_CHUNK_LOADER.get(), pos, state);
    }

    public void onRedstoneChanged(boolean powered) {
        cachedRedstone = powered;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        // Stagger by position so many loaders don't all fire same tick
        long t = sl.getGameTime();
        int stagger = (worldPosition.getX() * 17 + worldPosition.getZ()) & 15;
        if (((t + stagger) % TICK_INTERVAL) != 0) return;

        boolean powered = alwaysOn || cachedRedstone;
        if (!powered) return;
        // Short TTL; re-applied every interval — keeps corridor without permanent tickets
        // Block acts as a local loader around itself (treated as pad; skipped if target-only)
        MissileChunkLoadManager.forceNear(sl, worldPosition, Math.min(2, radius), 20 * 6,
                MissileChunkLoadManager.Role.PAD);
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
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("AlwaysOn", alwaysOn);
        tag.putInt("Radius", radius);
    }

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        alwaysOn = !tag.contains("AlwaysOn") || tag.getBoolean("AlwaysOn");
        radius = Math.max(1, Math.min(3, tag.contains("Radius") ? tag.getInt("Radius") : 1));
    }
}
