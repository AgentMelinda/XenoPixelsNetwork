package net.bullettrain.xenopixelsmod.compat.npc.brain.v4;

public final class NpcCombatBrainV4 {
    private NpcCombatBrainV4() {}

    public static NpcCombatIntentV4 decide(NpcCombatContextV4 context) {
        if (context == null) return NpcCombatIntentV4.HOLD;
        if (context.canRecover && (context.healthFraction <= 0.3 || context.energyFraction <= 0.15)) {
            return NpcCombatIntentV4.RECOVER;
        }
        if (context.distance <= NpcCombatContextV4.MELEE_RANGE) {
            if (context.targetHelpless && context.canCombo) return NpcCombatIntentV4.COMBO;
            if (context.targetBlocking && context.canGuardBreak) return NpcCombatIntentV4.GUARD_BREAK;
            if ((context.targetCasting || context.targetFiring) && context.canVanish) return NpcCombatIntentV4.VANISH;
            if (context.canCombo) return NpcCombatIntentV4.COMBO;
            return context.canMelee ? NpcCombatIntentV4.MELEE : NpcCombatIntentV4.REPOSITION;
        }
        if (context.distance <= NpcCombatContextV4.MID_RANGE) {
            if (context.targetApproaching && context.canDash) return NpcCombatIntentV4.DASH;
            if (context.canHitscan) return NpcCombatIntentV4.HITSCAN;
            if (context.canTravel) return NpcCombatIntentV4.TRAVEL;
            return context.canDash ? NpcCombatIntentV4.DASH : NpcCombatIntentV4.APPROACH;
        }
        if (context.distance <= NpcCombatContextV4.OUT_RANGE) {
            if (context.canHitscan) return NpcCombatIntentV4.HITSCAN;
            if (context.canZone) return NpcCombatIntentV4.ZONING;
            if (context.canTravel) return NpcCombatIntentV4.TRAVEL;
        }
        return NpcCombatIntentV4.APPROACH;
    }
}
