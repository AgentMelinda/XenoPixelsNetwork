package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/**
 * Fighting shadow: player-shaped DMZ copy (hair included) that uses ki and melee on the trainer.
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

        net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity dummy =
                net.bullettrain.xenopixelsmod.missile.ModEntities.CLONE.get().create(level);
        if (dummy == null) return "Failed to create shadow clone";

        double x = player.getX() + player.getLookAngle().x * 3.0;
        double y = player.getY();
        double z = player.getZ() + player.getLookAngle().z * 3.0;
        dummy.moveTo(x, y, z, player.getYRot() + 180f, 0);
        dummy.configure(player, net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity.SLOT_SHADOW_FIGHT,
                Integer.MAX_VALUE, Math.max(20f, player.getMaxHealth() * pct / 100f));
        CompoundTag data = dummy.getPersistentData();
        data.putBoolean(TAG_XENO_TRAINING, true);
        data.putBoolean(ProgressionEvents.DUMMY_TAG, true);
        data.putUUID(TAG_OWNER, player.getUUID());
        data.putInt("xenopixelsmod_shadow_pct", pct);
        dummy.setCustomName(Component.literal("Shadow Dummy (" + pct + "%)"));
        dummy.setCustomNameVisible(true);
        if (!level.addFreshEntity(dummy)) {
            return "Could not spawn shadow (area blocked?)";
        }

        XenoCapabilities.get(player).ifPresent(d -> d.resetDummySession());
        player.displayClientMessage(Component.literal(
                "§dShadow Dummy §f" + pct + "% §7spawned — it will attack you. /xenotrain dismiss"), false);
        return null;
    }

    public static int dismissNearby(ServerPlayer player, double range) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        int n = 0;
        AABB search = player.getBoundingBox().inflate(range);
        for (net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity dummy :
                level.getEntitiesOfClass(net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity.class, search)) {
            if (dummy.slot() != net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity.SLOT_SHADOW_FIGHT
                    && !dummy.getPersistentData().getBoolean(TAG_XENO_TRAINING)) continue;
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

    /** Saga-style pressure: ki at range, melee in close. Looks like the trainer (clone renderer). */
    public static void tickFight(net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity dummy,
                                 ServerPlayer trainer) {
        if (dummy == null || trainer == null || !trainer.isAlive()) return;
        CompoundTag data = dummy.getPersistentData();
        int cd = data.getInt("xenopixelsmod_shadow_cd");
        if (cd > 0) {
            data.putInt("xenopixelsmod_shadow_cd", cd - 1);
            return;
        }
        double dist = dummy.distanceTo(trainer);
        int pct = Math.max(25, data.getInt("xenopixelsmod_shadow_pct"));
        if (dist > 6.5) {
            net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile profile =
                    net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.read(dummy);
            profile.kiPower = Math.max(10, (int) (trainer.getMaxHealth() * pct / 20f));
            profile.energy = 200;
            net.bullettrain.xenopixelsmod.compat.npc.NpcKiAttackDispatcher.fireKiBlast(
                    dummy, profile,
                    net.bullettrain.xenopixelsmod.compat.npc.NpcKiAttackDispatcher.NO_DURATION_OVERRIDE,
                    trainer, 0);
            data.putInt("xenopixelsmod_shadow_cd", 28);
        } else {
            float dmg = Math.max(1f, trainer.getMaxHealth() * pct / 400f);
            trainer.hurt(dummy.damageSources().mobAttack(dummy), dmg);
            dummy.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            data.putInt("xenopixelsmod_shadow_cd", 16);
        }
    }
}
