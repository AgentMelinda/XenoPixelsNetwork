package net.bullettrain.xenopixelsmod.features.progression;

import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.ShadowDummyEntity;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

/**
 * Spawns DMZ {@link ShadowDummyEntity} as an attacking player clone for training.
 */
public final class ShadowDummyTraining {
    public static final String TAG_XENO_TRAINING = "xenopixelsmod_training_shadow";
    public static final String TAG_OWNER = "xenopixelsmod_training_owner";

    private ShadowDummyTraining() {}

    /**
     * @param powerPercent 1–100 stats copy strength (DMZ copyStatsFromPlayerWithPercent)
     * @return null on success, error message otherwise
     */
    public static String spawnAttackingClone(ServerPlayer player, int powerPercent) {
        if (player == null) return "Players only";
        if (!(player.level() instanceof ServerLevel level)) return "Server only";

        int pct = Math.max(25, Math.min(100, powerPercent));

        // Dismiss previous xeno training shadow near player
        dismissNearby(player, 48.0);

        EntityType<ShadowDummyEntity> type = MainEntities.SHADOW_DUMMY.get();
        ShadowDummyEntity dummy = type.create(level);
        if (dummy == null) return "Failed to create Shadow Dummy entity";

        double x = player.getX() + player.getLookAngle().x * 3.0;
        double y = player.getY();
        double z = player.getZ() + player.getLookAngle().z * 3.0;
        dummy.moveTo(x, y, z, player.getYRot() + 180f, 0);

        // Copy combat stats — this is the real DMZ “shadow clone”
        try {
            dummy.copyStatsFromPlayerWithPercent(player, pct);
        } catch (Throwable t) {
            try {
                dummy.copyStatsFromPlayer(player);
            } catch (Throwable t2) {
                XenoPixelsMod.LOGGER.warn("Shadow dummy stat copy failed: {}", t2.toString());
            }
        }

        // Do NOT setOwner — owner link may prevent attacking the trainer.
        // Tag for cleanup + damage meter.
        CompoundTag data = dummy.getPersistentData();
        data.putBoolean(TAG_XENO_TRAINING, true);
        data.putBoolean(ProgressionEvents.DUMMY_TAG, true);
        data.putUUID(TAG_OWNER, player.getUUID());
        data.putInt("xenopixelsmod_shadow_pct", pct);

        dummy.setCustomName(Component.literal("Shadow Dummy (" + pct + "%)"));
        dummy.setCustomNameVisible(true);
        dummy.setPersistenceRequired();

        if (!level.addFreshEntity(dummy)) {
            return "Could not spawn shadow (area blocked?)";
        }

        // Force aggro on trainer (ShadowDummyEntity extends Monster / Mob)
        dummy.setTarget(player);
        dummy.setLastHurtByMob(player);

        XenoCapabilities.get(player).ifPresent(d -> d.resetDummySession());
        player.displayClientMessage(Component.literal(
                "§dShadow Dummy §f" + pct + "% §7spawned — it will attack you. /xenotrain dismiss"), false);
        return null;
    }

    public static int dismissNearby(ServerPlayer player, double range) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        int n = 0;
        AABB search = player.getBoundingBox().inflate(range);
        for (ShadowDummyEntity dummy : level.getEntitiesOfClass(ShadowDummyEntity.class, search)) {
            if (!dummy.getPersistentData().getBoolean(TAG_XENO_TRAINING)) continue;
            if (dummy.distanceToSqr(player) > range * range) continue;
            // Only dismiss own if owner set
            if (dummy.getPersistentData().hasUUID(TAG_OWNER)
                    && !dummy.getPersistentData().getUUID(TAG_OWNER).equals(player.getUUID())
                    && !player.hasPermissions(2)) {
                continue;
            }
            dummy.discard();
            n++;
        }
        return n;
    }
}
