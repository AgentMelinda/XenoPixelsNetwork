package net.bullettrain.xenopixelsmod.combat.overcharge;

import com.dragonminez.common.init.MainGameRules;
import com.dragonminez.common.init.block.custom.DragonBallBlock;
import com.dragonminez.common.init.entities.ki.KiDiskEntity;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

/**
 * Budgeted block slice along an overcharged destructo-disk's plane.
 *
 * <p>Only the disks registered here are visited — never every entity in the level.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DiskSliceTracker {

    private static final float INDESTRUCTIBLE = 1000.0f;
    private static final Map<ResourceKeyed, Tracked> LIVE = new HashMap<>();

    private DiskSliceTracker() {
    }

    private record ResourceKeyed(String level, UUID disk) {
    }

    private static final class Tracked {
        private double lastX = Double.NaN;
        private double lastY = Double.NaN;
        private double lastZ = Double.NaN;
    }

    public static void track(ServerLevel level, KiDiskEntity disk) {
        if (level == null || disk == null) return;
        if (!XenoServerConfig.chargeOverchargeGriefEnabled) return;
        if (!XenoServerConfig.chargeOverchargeDiskSliceEnabled) return;
        LIVE.putIfAbsent(new ResourceKeyed(level.dimension().location().toString(), disk.getUUID()),
                new Tracked());
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (LIVE.isEmpty()) return;
        if (!XenoServerConfig.chargeOverchargeEnabled
                || !XenoServerConfig.chargeOverchargeGriefEnabled
                || !XenoServerConfig.chargeOverchargeDiskSliceEnabled) {
            LIVE.clear();
            return;
        }

        String levelId = level.dimension().location().toString();
        Iterator<Map.Entry<ResourceKeyed, Tracked>> it = LIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceKeyed, Tracked> entry = it.next();
            if (!entry.getKey().level().equals(levelId)) continue;
            Entity entity = level.getEntity(entry.getKey().disk());
            if (!(entity instanceof KiDiskEntity disk) || !disk.isAlive() || !disk.isFiring()) {
                it.remove();
                continue;
            }
            slice(level, disk, entry.getValue());
        }
    }

    private static void slice(ServerLevel level, KiDiskEntity disk, Tracked tracked) {
        Vec3 pos = disk.position();
        if (Double.isFinite(tracked.lastX)
                && pos.distanceToSqr(tracked.lastX, tracked.lastY, tracked.lastZ) < 0.04) {
            return;
        }
        tracked.lastX = pos.x;
        tracked.lastY = pos.y;
        tracked.lastZ = pos.z;

        Vec3 dir = disk.getDeltaMovement();
        if (dir.lengthSqr() < 1.0e-6) dir = disk.getLookAngle();
        if (dir.lengthSqr() < 1.0e-6) return;
        dir = dir.normalize();

        Vec3 reference = Math.abs(dir.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 right = dir.cross(reference);
        if (right.lengthSqr() < 1.0e-6) return;
        right = right.normalize();
        Vec3 up = dir.cross(right).normalize();

        float radius = Math.min(disk.getSize(), XenoServerConfig.kiDestructionRadiusLimit());
        int steps = Math.max(1, Math.min(16, (int) Math.ceil(radius)));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Entity source = disk.getOwner() != null ? disk.getOwner() : disk;

        for (int i = -steps; i <= steps; i++) {
            for (int j = -steps; j <= steps; j++) {
                if (i * i + j * j > steps * steps) continue;
                double x = pos.x + right.x * i + up.x * j;
                double y = pos.y + right.y * i + up.y * j;
                double z = pos.z + right.z * i + up.z * j;
                cursor.set(x, y, z);
                if (!tryBreak(level, cursor, source)) return;
            }
        }
    }

    private static boolean tryBreak(ServerLevel level, BlockPos pos, Entity source) {
        if (!level.isLoaded(pos)) return true;
        if (!MainGameRules.canKiGrief(level, pos, source)) return true;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (state.getDestroySpeed(level, pos) < 0.0f) return true;
        if (state.getBlock() instanceof DragonBallBlock) return true;
        if (state.getExplosionResistance(level, pos, null) >= INDESTRUCTIBLE) return true;
        if (!KiDestructionBudget.tryConsume(level, 1)) return false;
        level.destroyBlock(pos, false);
        return true;
    }

    public static void forgetLevel(String levelId) {
        if (levelId == null) return;
        LIVE.keySet().removeIf(key -> key.level().equals(levelId));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clear();
    }

    public static void clear() {
        LIVE.clear();
        KiDestructionBudget.clear();
    }
}
