package net.bullettrain.xenopixelsmod.block.entity;

import net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat;
import net.bullettrain.xenopixelsmod.compat.ballistix.MissileChunkLoadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.WeakHashMap;

/**
 * Stores a world-space ballistic aim point and pushes it into nearby Ballistix VLS tiles
 * when Ballistix is installed. Works as a coordinate computer even without Ballistix.
 * 
 * Performance optimizations:
 * - Cached VLS tile references to avoid repeated block lookups
 * - Reduced search frequency with configurable tick interval
 * - WeakHashMap for cache to allow GC cleanup
 * - Early exits for null/invalid states
 */
public class ShipVlsGuidanceBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos target;
    private boolean wasPowered;
    private int searchRadius = 6;
    private int tickCounter = 0;
    private static final int PUSH_TICK_INTERVAL = 40; // Only push target every 40 ticks (2 seconds)
    
    // Cache of nearby VLS tiles to avoid repeated block entity lookups
    private final WeakHashMap<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> vlsCache = new WeakHashMap<>();
    private long lastCacheRefresh = 0;
    private static final long CACHE_DURATION_TICKS = 100; // Refresh cache every 5 seconds

    public ShipVlsGuidanceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIP_VLS_GUIDANCE.get(), pos, state);
    }

    @Nullable
    public BlockPos getTarget() {
        return target;
    }

    public void clearTarget() {
        target = null;
        vlsCache.clear();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setTargetFromLook(Player player) {
        if (player == null || level == null) return;
        HitResult hit = player.pick(128.0, 0f, false);
        if (hit.getType() == HitResult.Type.BLOCK || hit.getType() == HitResult.Type.ENTITY) {
            Vec3 loc = hit.getLocation();
            BlockPos raw = BlockPos.containing(loc);
            target = BallistixVs2Compat.toWorldBlockPos(level, raw);
            setChanged();
            if (!level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (powered && !wasPowered) {
            onRedstonePulse(sl);
        }
        wasPowered = powered;

        // Periodic re-push so VLS keeps world target while ship moves
        tickCounter++;
        if (target != null && tickCounter % PUSH_TICK_INTERVAL == 0) {
            refreshVlsCache(sl);
            pushTargetToNearbyVls();
        }
    }

    private void onRedstonePulse(ServerLevel level) {
        refreshVlsCache(level);
        pushTargetToNearbyVls();
        if (target != null) {
            MissileChunkLoadManager.forceNear(level, worldPosition, 2, 20 * 30);
            MissileChunkLoadManager.forceNear(level, target, 2, 20 * 90);
        }
        tryLaunchNearbyVls();
    }

    private void refreshVlsCache(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime - lastCacheRefresh < CACHE_DURATION_TICKS && !vlsCache.isEmpty()) {
            return; // Cache still valid
        }
        
        vlsCache.clear();
        if (!ModList.get().isLoaded("ballistix")) {
            lastCacheRefresh = gameTime;
            return;
        }
        
        int r = searchRadius;
        for (BlockPos p : BlockPos.betweenClosed(
                worldPosition.offset(-r, -2, -r),
                worldPosition.offset(r, 4, r))) {
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) continue;
            String cn = be.getClass().getName();
            if (cn.contains("TileVerticalLaunchSilo") || cn.contains("TileLauncherControlPanel")) {
                vlsCache.put(p.immutable(), be);
            }
        }
        lastCacheRefresh = gameTime;
    }

    private void pushTargetToNearbyVls() {
        if (target == null || level == null || vlsCache.isEmpty()) return;
        
        BlockPos worldTarget = BallistixVs2Compat.toWorldBlockPos(level, target);
        if (worldTarget == null) worldTarget = target;
        target = worldTarget;
        
        for (BlockEntity be : vlsCache.values()) {
            if (be == null || be.isRemoved()) continue;
            try {
                var m = be.getClass().getMethod("setTarget", BlockPos.class);
                m.invoke(be, worldTarget);
            } catch (Throwable ignored) {
                // Method not found or invocation failed - remove from cache
                vlsCache.remove(be.getBlockPos());
            }
        }
        setChanged();
    }

    private void tryLaunchNearbyVls() {
        if (level == null || vlsCache.isEmpty()) return;
        
        for (BlockEntity be : vlsCache.values()) {
            if (be == null || be.isRemoved()) continue;
            try {
                be.getClass().getMethod("launch").invoke(be);
                BallistixVs2Compat.onLauncherFired(level, be.getBlockPos());
            } catch (Throwable ignored) {
                // Method not found - remove from cache
                vlsCache.remove(be.getBlockPos());
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (target != null) {
            tag.putLong("Target", target.asLong());
        }
        tag.putBoolean("WasPowered", wasPowered);
        tag.putInt("TickCounter", tickCounter);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Target")) {
            target = BlockPos.of(tag.getLong("Target"));
        } else {
            target = null;
        }
        wasPowered = tag.getBoolean("WasPowered");
        tickCounter = tag.getInt("TickCounter");
    }
}
