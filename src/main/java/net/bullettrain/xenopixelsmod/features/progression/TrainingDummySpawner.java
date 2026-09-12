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

    public static int dismissOwn(ServerPlayer player, double range) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return 0;
        int n = 0;
        AABB box = player.getBoundingBox().inflate(range);
        for (XenoCloneEntity clone : level.getEntitiesOfClass(XenoCloneEntity.class, box)) {
            if (clone.slot() != XenoCloneEntity.SLOT_TRAINING) continue;
            if (clone.getPersistentData().hasUUID("xenopixelsmod_training_owner")
                    && !clone.getPersistentData().getUUID("xenopixelsmod_training_owner")
                    .equals(player.getUUID())
                    && !player.hasPermissions(2)) {
                continue;
            }
            clone.discard();
            n++;
        }
        n += ShadowDummyTraining.dismissNearby(player, range);
        return n;
    }
}
