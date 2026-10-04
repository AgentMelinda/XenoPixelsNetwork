package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.combat.fx.HakaiFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The moment a Hakai lands on one living thing. Shared by the single-target channel
 * ({@link HakaiChannelSystem}) and the area one ({@link HakaiAreaSystem}) so both erase the same way;
 * moved here unchanged from {@code HakaiChannelSystem.finish}.
 */
final class HakaiErase {
    private HakaiErase() {
    }

    static void eraseTarget(ServerLevel level, LivingEntity target) {
        Vec3 pos = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        CombatFx.cue(level, pos, CombatFxKind.HAKAI_ERASE, 1.0f);

        HakaiFx.burst(level, target, true);
        HakaiFx.reveal(target);
        net.bullettrain.xenopixelsmod.compat.npc.NpcDissolve.clear(target);
        // A native Xeno NPC dies rather than being erased from existence. discard() skips die(),
        // which is what schedules the respawn, so an NPC caught here vanished permanently - the
        // same removal /kill is already guarded against. This is the player-cast path; NpcHakai
        // has the NPC-cast one, and both have to agree.
        if (target instanceof Player
                || target instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity
                || net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync.isCustomNpc(target)) {
            eraseLivingTarget(target);
        } else {
            target.discard();
        }
    }

    private static void eraseLivingTarget(LivingEntity target) {
        var source = target.level().damageSources().genericKill();
        target.hurt(source, Float.MAX_VALUE);
        if (target.isAlive()) {
            target.setHealth(0.0f);
            target.die(source);
        }
    }
}
