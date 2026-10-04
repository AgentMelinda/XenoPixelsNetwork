package net.bullettrain.xenopixelsmod.compat.npc.brain.v2;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatMoves;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAnim;
import net.bullettrain.xenopixelsmod.compat.npc.NpcGeckoAnim;
import net.bullettrain.xenopixelsmod.compat.npc.NpcMeleeDamage;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short authored strings for v2 combo roles. Not a port of DMZ gum/absorb/sleep kits.
 */
final class NpcSagaCombos {
    private static final int PRESSURE_HITS = 4;
    private static final int PRESSURE_GAP = 5;

    private record Combo(UUID victim, NpcSagaCombatBrain.ComboRole role, int remaining,
                         int nextTick, int step) {}

    private static final Map<UUID, Combo> COMBOS = new ConcurrentHashMap<>();

    private NpcSagaCombos() {}

    static boolean active(UUID npcId) {
        return npcId != null && COMBOS.containsKey(npcId);
    }

    static void forget(UUID npcId) {
        if (npcId != null) {
            COMBOS.remove(npcId);
        }
    }

    static boolean start(LivingEntity npc, NpcCombatProfile profile, LivingEntity victim,
                         NpcSagaCombatBrain.ComboRole role, int serverTick) {
        if (npc == null || victim == null || role == null) {
            return false;
        }
        if (role == NpcSagaCombatBrain.ComboRole.RECOVERY) {
            NpcCombatMoves.guard(npc, true);
            NpcCombatMoves.backstep(npc, victim);
            return true;
        }
        if (role == NpcSagaCombatBrain.ComboRole.STUN || role == NpcSagaCombatBrain.ComboRole.HEAVY) {
            NpcCombatMoves.vanish(npc, victim, 0);
            // The vanish can fail (cooldown, no landing spot); a finisher thrown from where it
            // stands would be a punch at nothing.
            if (!net.bullettrain.xenopixelsmod.compat.npc.NpcCombatRanges.withinMelee(npc, victim)) {
                return false;
            }
            float scale = role == NpcSagaCombatBrain.ComboRole.HEAVY ? 1.75f : 1.25f;
            play(npc, role == NpcSagaCombatBrain.ComboRole.HEAVY
                    ? Bt3AnimationIntent.HEAVY_FINISH : Bt3AnimationIntent.UPPERCUT_RIGHT);
            NpcMeleeDamage.hit(npc, victim, scale);
            return true;
        }
        COMBOS.put(npc.getUUID(), new Combo(victim.getUUID(), role, PRESSURE_HITS, serverTick, 0));
        advance(npc, profile, serverTick);
        return true;
    }

    static void advance(LivingEntity npc, NpcCombatProfile profile, int serverTick) {
        Combo combo = npc == null ? null : COMBOS.get(npc.getUUID());
        if (combo == null || serverTick < combo.nextTick()) {
            return;
        }
        LivingEntity victim = npc.getServer() == null ? null
                : net.bullettrain.xenopixelsmod.compat.npc.NpcEntityLookup
                .findAlive(npc.getServer(), combo.victim());
        if (victim == null || !victim.isAlive()
                || npc.distanceTo(victim) > NpcSagaCombatContext.MELEE + 2.0) {
            COMBOS.remove(npc.getUUID());
            return;
        }
        // Only animate reachable strikes. If the target escapes, end the pressure string
        // so steer() and the next decision can chase rather than hold the NPC in place.
        if (!net.bullettrain.xenopixelsmod.compat.npc.NpcCombatRanges.withinMelee(npc, victim)) {
            // A paused combo still owns movement in steer(). Release it so an escaped
            // target is approached instead of holding the NPC in place indefinitely.
            COMBOS.remove(npc.getUUID());
            return;
        }
        play(npc, combo.step() % 2 == 0
                ? Bt3AnimationIntent.JAB_LEFT : Bt3AnimationIntent.JAB_RIGHT);
        NpcMeleeDamage.hit(npc, victim, profile == null ? 1.0f : profile.brainModifier("strike"));
        int left = combo.remaining() - 1;
        if (left <= 0) {
            COMBOS.remove(npc.getUUID());
            return;
        }
        COMBOS.put(npc.getUUID(), new Combo(combo.victim(), combo.role(), left,
                serverTick + PRESSURE_GAP, combo.step() + 1));
    }

    private static void play(LivingEntity npc, Bt3AnimationIntent intent) {
        if (!NpcDmzAnim.play(npc, intent)) swingWithoutClip(npc);
    }

    /**
     * The vanilla arm swing for an NPC that cannot show Xeno clips (a new NPC starts with its DMZ
     * appearance off), as the older brain's {@code swingBeforeHit} does. GeckoLib NPCs are left
     * out: {@code NpcMeleeDamage.hit} plays their own attack clip.
     */
    static void swingWithoutClip(LivingEntity npc) {
        if (!NpcDmzAnim.canAnimate(npc) && !NpcGeckoAnim.canAnimate(npc)) {
            npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        }
    }
}
