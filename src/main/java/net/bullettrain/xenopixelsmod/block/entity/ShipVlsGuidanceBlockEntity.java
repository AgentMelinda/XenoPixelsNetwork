package net.bullettrain.xenopixelsmod.block.entity;

import net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat;
import net.bullettrain.xenopixelsmod.compat.ballistix.MissileChunkLoadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/**
 * Stores a world-space ballistic aim point and pushes it into nearby Ballistix VLS tiles
 * when Ballistix is installed. Works as a coordinate computer even without Ballistix.
 */
public class ShipVlsGuidanceBlockEntity extends BlockEntity {
    private BlockPos target;
    private boolean wasPowered;
    private int searchRadius = 6;

    public ShipVlsGuidanceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIP_VLS_GUIDANCE.get(), pos, state);
    }

    public BlockPos getTarget() {
        return target;
    }

    public void clearTarget() {
        target = null;
        setChanged();
    }

    public void setTargetFromLook(Player player) {
        if (player == null) return;
        HitResult hit = player.pick(128.0, 0f, false);
        if (hit.getType() == HitResult.Type.BLOCK || hit.getType() == HitResult.Type.ENTITY) {
            Vec3 loc = hit.getLocation();
            BlockPos raw = BlockPos.containing(loc);
            if (level != null) {
                target = BallistixVs2Compat.toWorldBlockPos(level, raw);
            } else {
                target = raw;
            }
            setChanged();
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (powered && !wasPowered) {
            pushTargetToNearbyVls();
            if (target != null) {
                MissileChunkLoadManager.forceNear(sl, worldPosition, 2, 20 * 30);
                MissileChunkLoadManager.forceNear(sl, target, 2, 20 * 90);
            }
            tryLaunchNearbyVls();
        }
        wasPowered = powered;

        // Periodic re-push so VLS keeps world target while ship moves
        if (target != null && sl.getGameTime() % 40 == 0) {
            pushTargetToNearbyVls();
        }
    }

    private void pushTargetToNearbyVls() {
        if (target == null || level == null) return;
        if (!ModList.get().isLoaded("ballistix")) return;
        BlockPos worldTarget = BallistixVs2Compat.toWorldBlockPos(level, target);
        target = worldTarget;
        int r = searchRadius;
        for (BlockPos p : BlockPos.betweenClosed(
                worldPosition.offset(-r, -2, -r),
                worldPosition.offset(r, 4, r))) {
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) continue;
            String cn = be.getClass().getName();
            if (!cn.contains("TileVerticalLaunchSilo") && !cn.contains("TileLauncherControlPanel")) continue;
            try {
                // setTarget(BlockPos) on Ballistix control panels / VLS
                var m = be.getClass().getMethod("setTarget", BlockPos.class);
                m.invoke(be, worldTarget);
            } catch (Throwable ignored) {
            }
        }
        setChanged();
    }

    private void tryLaunchNearbyVls() {
        if (level == null || !ModList.get().isLoaded("ballistix")) return;
        int r = searchRadius;
        for (BlockPos p : BlockPos.betweenClosed(
                worldPosition.offset(-r, -2, -r),
                worldPosition.offset(r, 4, r))) {
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) continue;
            if (!be.getClass().getName().contains("TileVerticalLaunchSilo")
                    && !be.getClass().getName().contains("TileLauncherControlPanel")) continue;
            try {
                be.getClass().getMethod("launch").invoke(be);
                BallistixVs2Compat.onLauncherFired(level, p);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (target != null) {
            tag.putLong("Target", target.asLong());
        }
        tag.putBoolean("WasPowered", wasPowered);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Target")) {
            target = BlockPos.of(tag.getLong("Target"));
        }
        wasPowered = tag.getBoolean("WasPowered");
    }
}
