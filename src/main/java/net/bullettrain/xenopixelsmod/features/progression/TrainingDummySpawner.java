package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/**
 * Punching dummy: a slightly transparent copy of the trainer, not an armor stand.
 */
public final class TrainingDummySpawner {
    public static final float GHOST_ALPHA = 0.45f;

    private TrainingDummySpawner() {}

    public static String spawn(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return "Players only";
        dismissOwn(player, 48.0);
        XenoCloneEntity dummy = ModEntities.CLONE.get().create(level);
        if (dummy == null) return "Failed to create dummy";
        dummy.moveTo(player.getX() + player.getLookAngle().x * 2.5,
                player.getY(),
                player.getZ() + player.getLookAngle().z * 2.5,
                player.getYRot() + 180f, 0);
        dummy.configure(player, XenoCloneEntity.SLOT_TRAINING, Integer.MAX_VALUE, 40f);
        dummy.getPersistentData().putUUID("xenopixelsmod_training_owner", player.getUUID());
        ProgressionEvents.tagAsDummy(dummy);
        dummy.setCustomName(Component.literal("Training Dummy").withStyle(ChatFormatting.GOLD));
        dummy.setCustomNameVisible(true);
        if (!level.addFreshEntity(dummy)) return "Could not spawn dummy (area blocked?)";
        XenoCapabilities.get(player).ifPresent(d -> d.resetDummySession());
        return null;
    }

    /** Removes this player's own training dummies in range, of every kind. */
    public static int dismissOwn(ServerPlayer player, double range) {
        return dismiss(player, range, false);
    }

    /**
     * @param everyone true also removes other players' dummies (the operator's
     *                 {@code /xenotrain dismiss all}). It used to be implied by being an
     *                 operator, so an operator spawning or dismissing a dummy removed everyone's
     *                 (2026-10-02 owner: "xenodismsis per player not all").
     */
    public static int dismiss(ServerPlayer player, double range, boolean everyone) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        int n = 0;
        AABB box = player.getBoundingBox().inflate(range);
        for (XenoCloneEntity clone : level.getEntitiesOfClass(XenoCloneEntity.class, box)) {
            if (clone.slot() != XenoCloneEntity.SLOT_TRAINING) continue;
            if (clone.getPersistentData().hasUUID("xenopixelsmod_training_owner")
                    && !clone.getPersistentData().getUUID("xenopixelsmod_training_owner")
                    .equals(player.getUUID())
                    && !everyone) {
                continue;
            }
            clone.discard();
            n++;
        }
        n += ShadowDummyTraining.dismissNearby(player, range, everyone);
        n += TrainingNpc.dismissNearby(player, range, everyone);
        return n;
    }
}
