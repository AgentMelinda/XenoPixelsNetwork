package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

import java.util.List;

/**
 * Makes AI lose a fighter while their Zanzoken images are standing in for them.
 *
 * <p>The technique used to fool players and nothing else — an onlooker could not tell which body
 * was real, but anything with AI went on tracking the fighter through it, which made the whole trick
 * decorative against everything but another player.
 *
 * <p>Two halves, because a fight contains both kinds of attacker:
 *
 * <ul>
 *   <li>{@link #onChangeTarget} stops anything <b>acquiring</b> the fighter while the images stand.
 *       Hooked on {@link LivingChangeTargetEvent} rather than on any mod's own AI: every attacker in
 *       play reaches {@code Mob#setTarget} in the end — vanilla mobs directly, DragonMineZ saga
 *       enemies through {@code PathfinderMob}, and CustomNPCs' {@code EntityNPCInterface#setTarget}
 *       and My NPCs' NPCs by calling {@code super.setTarget} as their last statement — so one
 *       handler covers all of them and needs no reference to any of those mods. Anything that
 *       acquires targets by some private route of its own is simply unaffected rather than broken.
 *   <li>{@link #scatter} moves the attackers that <b>already had</b> the fighter onto one of the
 *       images, at the moment the ring goes up.
 * </ul>
 *
 * <p><b>Why scatter redirects rather than clears.</b> Dropping every target would read as a stun and
 * let a fighter shake off a whole fight with one key. Pointing each attacker at a different image
 * costs them nothing they had — they are still fighting, still swinging — but they are swinging at
 * the wrong body, which is what the images are for. It also resolves itself: striking any image
 * disperses the ring, and an attacker whose image is gone re-acquires normally.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ZanzokenConfusion {

    /**
     * How far out attackers are scattered, in blocks.
     *
     * <p>Wide enough to cover everything that could plausibly be swinging at the fighter, and no
     * wider: something across the map that happens to have this player as its target has not seen
     * the images and has no business being fooled by them.
     */
    private static final double SCATTER_RADIUS = 32.0;

    private ZanzokenConfusion() {
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!XenoServerConfig.zanzokenEnabled || !XenoServerConfig.zanzokenConfusesAi) return;
        if (event.getEntity() == null || event.getEntity().level().isClientSide) return;
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)) return;
        // Something already locked on is not re-acquiring; leave it alone. Scatter is what moves
        // those, once, when the ring goes up.
        if (event.getOriginalAboutToBeSetTarget() == player) return;
        if (!Bt3CombatEvents.afterimagesActive(player)) return;
        event.setCanceled(true);
    }

    /**
     * Points everything currently hunting this fighter at one of their images instead.
     *
     * <p>Called once, when the ring is created. Each attacker draws its own image, so a crowd does
     * not converge on one body and give the trick away, and the fighter's own slot is never chosen
     * because no image stands in it.
     *
     * <p>Every failure here is swallowed per attacker: an NPC whose AI refuses to be retargeted
     * costs the disguise against that one attacker, never the dodge and never the tick.
     */
    /**
     * Moves whoever was hunting a lost image onto one of the images still standing.
     *
     * <p>Without this, an attacker whose image dies drops its target and re-acquires — and the
     * nearest thing to re-acquire is the real fighter, so losing one body would hand the whole ring
     * away. Re-homing keeps the attacker misled for as long as any image is left.
     *
     * @param lost      the image that has just been removed
     * @param survivors the images still standing; nothing is done when this is empty, because the
     *                  ring is over anyway
     */
    public static void rehome(XenoCloneEntity lost, List<XenoCloneEntity> survivors) {
        if (!XenoServerConfig.zanzokenEnabled || !XenoServerConfig.zanzokenConfusesAi) return;
        if (lost == null || survivors == null || survivors.isEmpty()) return;
        if (!(lost.level() instanceof ServerLevel level)) return;

        AABB range = lost.getBoundingBox().inflate(SCATTER_RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, range, mob -> mob.getTarget() == lost)) {
            try {
                mob.setTarget(survivors.get(lost.getRandom().nextInt(survivors.size())));
            } catch (Throwable ignored) {
                // An NPC that will not be moved re-acquires on its own. A worse disguise, not a
                // broken tick.
            }
        }
    }

    public static void scatter(ServerPlayer player, List<XenoCloneEntity> images) {
        if (!XenoServerConfig.zanzokenEnabled || !XenoServerConfig.zanzokenConfusesAi) return;
        if (player == null || images == null || images.isEmpty()) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        AABB range = player.getBoundingBox().inflate(SCATTER_RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, range, mob -> mob.getTarget() == player)) {
            try {
                XenoCloneEntity image = images.get(player.getRandom().nextInt(images.size()));
                // Fired as a normal retarget so any mod watching target changes still sees it. Our
                // own handler above does not block this one: the new target is an image, not the
                // player, so it never reaches the ServerPlayer check.
                mob.setTarget(image);
            } catch (Throwable ignored) {
                // An NPC that will not be moved keeps hunting the real body. That is a worse dodge,
                // not a broken one.
            }
        }
    }
}
