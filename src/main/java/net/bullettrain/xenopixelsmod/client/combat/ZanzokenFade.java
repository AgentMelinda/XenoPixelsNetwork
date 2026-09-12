package net.bullettrain.xenopixelsmod.client.combat;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fades a fighter's real body while their Zanzoken images stand around them.
 *
 * <p>The ring only works if no body in it is identifiable, and the fighter's own was: every image
 * drew through {@link AfterimageFade} and dimmed as it aged, while the real player went on
 * rendering fully solid in one of the slots. That is the one body an attacker had to pick, and it
 * announced itself.
 *
 * <p>This gives the real body the <b>same</b> alpha curve the images use, from the same config, so
 * all of them dim together and none of them stands out. It reads as the fighter blurring rather
 * than vanishing, which is what the technique is, and the fighter can still see themselves.
 *
 * <p>No packet and no new state: the ring images are entities the client already has, and they
 * carry their owner's entity id and their own lifetime. Tracking them as they arrive and leave is
 * enough to know, per frame, whether a given player's ring is standing and how far through it is.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class ZanzokenFade {

    /**
     * Owner entity id -> one image currently standing for them.
     *
     * <p>One is enough. Every image in a ring is created in the same tick with the same lifetime,
     * so any of them reports the same age, and the ring is dropped whole.
     */
    private static final Map<Integer, XenoCloneEntity> RINGS = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> LAST_ALPHA = new ConcurrentHashMap<>();
    private static final Map<Integer, Restore> RESTORE = new ConcurrentHashMap<>();
    private static final int RESTORE_TICKS = 10;

    private static final class Restore {
        final float from;
        int remaining;

        Restore(float from) {
            this.from = from;
            this.remaining = RESTORE_TICKS;
        }
    }

    private ZanzokenFade() {
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof XenoCloneEntity clone)) return;
        if (!clone.level().isClientSide) return;
        // Only the standing images disguise anyone. A Shi Shin No Ken body fights on its own and
        // its owner is meant to be visible.
        if (clone.slot() != XenoCloneEntity.SLOT_STATIONARY) return;
        RINGS.put(clone.ownerId(), clone);
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof XenoCloneEntity clone)) return;
        if (!clone.level().isClientSide) return;
        RINGS.remove(clone.ownerId(), clone);
    }

    /** Dropped wholesale on disconnect; ids from one session mean nothing in the next. */
    @SubscribeEvent
    public static void onLoggingOut(
            net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        RINGS.clear();
        LAST_ALPHA.clear();
        RESTORE.clear();
    }

    /**
     * The alpha this player's body should render at, or 1 when no ring of theirs is standing.
     *
     * <p>Deliberately the same call the image renderer makes, with the same configuration, so the
     * two cannot drift apart into "the images faded and the fighter did not".
     */
    public static float alpha(Entity entity, float partialTick) {
        if (entity == null || !XenoServerConfig.zanzokenEnabled
                || !XenoServerConfig.zanzokenGhostAfterimage) {
            return 1.0f;
        }
        int id = entity.getId();
        XenoCloneEntity image = standingImage(entity);
        if (image != null) {
            RESTORE.remove(id);
            float alpha = AfterimageFade.alpha(1, XenoServerConfig.zanzokenGhostAlpha,
                    image.tickCount + partialTick, image.lifetimeTicks());
            LAST_ALPHA.put(id, alpha);
            return alpha;
        }
        Float last = LAST_ALPHA.remove(id);
        if (last != null && last < 0.999f) {
            RESTORE.put(id, new Restore(last));
        }
        Restore restore = RESTORE.get(id);
        if (restore != null) {
            restore.remaining--;
            float t = 1.0f - restore.remaining / (float) RESTORE_TICKS;
            float alpha = restore.from + (1.0f - restore.from) * Math.max(0.0f, Math.min(1.0f, t));
            if (restore.remaining <= 0) RESTORE.remove(id);
            return alpha;
        }
        return 1.0f;
    }

    /**
     * {@code buffers}, wrapped to draw at {@code alpha}, or {@code buffers} itself when there is
     * nothing to fade.
     */
    public static MultiBufferSource wrap(MultiBufferSource buffers, float alpha) {
        return CombatBodyFade.wrap(buffers, alpha);
    }

    /**
     * The join map is the fast path. Synched {@code ownerId} can still be the default when
     * {@code EntityJoinLevelEvent} fires, so a miss falls back to a scan of living ring copies.
     */
    private static XenoCloneEntity standingImage(Entity entity) {
        int id = entity.getId();
        XenoCloneEntity image = RINGS.get(id);
        if (isStanding(image, id)) return image;
        if (image != null) RINGS.remove(id, image);
        if (!(entity.level() instanceof ClientLevel level)) return null;
        for (Entity candidate : level.entitiesForRendering()) {
            if (candidate instanceof XenoCloneEntity clone && isStanding(clone, id)) {
                RINGS.put(id, clone);
                return clone;
            }
        }
        return null;
    }

    private static boolean isStanding(XenoCloneEntity clone, int ownerId) {
        return clone != null && clone.isAlive() && !clone.isRemoved()
                && clone.slot() == XenoCloneEntity.SLOT_STATIONARY
                && clone.ownerId() == ownerId;
    }
}
