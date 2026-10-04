package net.bullettrain.xenopixelsmod.compat.npc.brain.v2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NpcSagaCombatBrainTest {
    private static final NpcSagaCombatBrain.Chance ALWAYS = p -> true;
    private static final NpcSagaCombatBrain.Chance NEVER = p -> false;

    @Test
    void nullContextHolds() {
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(null, ALWAYS);
        assertEquals(NpcSagaCombatBrain.Type.HOLD, intent.type());
    }

    @Test
    void lowHpWithoutApproachUsesRecovery() {
        NpcSagaCombatContext ctx = melee();
        ctx.selfHpPct = 0.2f;
        ctx.comboReady = true;
        ctx.targetApproaching = false;
        ctx.allowRecovery = true;
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(ctx, NEVER);
        assertEquals(NpcSagaCombatBrain.Type.COMBO, intent.type());
        assertEquals(NpcSagaCombatBrain.ComboRole.RECOVERY, intent.combo());
    }

    @Test
    void blockWithGuardBreakCastsWave() {
        NpcSagaCombatContext ctx = mid();
        ctx.targetBlocking = true;
        ctx.hasGuardBreak = true;
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(ctx, NEVER);
        assertEquals(NpcSagaCombatBrain.Type.CAST, intent.type());
        assertEquals(net.bullettrain.xenopixelsmod.compat.npc.NpcBrainKiRotation.Band.WAVE,
                intent.kiBand());
    }

    @Test
    void outOfRangeTeleportWhenVanishRolls() {
        NpcSagaCombatContext ctx = out();
        ctx.vanishReady = true;
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(ctx, ALWAYS);
        assertEquals(NpcSagaCombatBrain.Type.TELEPORT, intent.type());
    }

    @Test
    void outOfRangeDashesWhenVanishMisses() {
        NpcSagaCombatContext ctx = out();
        ctx.vanishReady = false;
        ctx.dashReady = true;
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(ctx, ALWAYS);
        assertEquals(NpcSagaCombatBrain.Type.APPROACH, intent.type());
        assertEquals(NpcSagaCombatBrain.Locomotion.DASH, intent.locomotion());
    }

    @Test
    void outOfRangeRunsWhenRepositionRollsFail() {
        NpcSagaCombatContext ctx = out();
        ctx.vanishReady = true;
        ctx.dashReady = true;
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(ctx, NEVER);
        assertEquals(NpcSagaCombatBrain.Type.APPROACH, intent.type());
        assertEquals(NpcSagaCombatBrain.Locomotion.RUN, intent.locomotion());
    }

    @Test
    void farApproachingWithoutHitscanMayCastTravel() {
        NpcSagaCombatContext ctx = far();
        ctx.targetApproaching = true;
        ctx.hasHitscan = false;
        ctx.hasTravel = true;
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(ctx, ALWAYS);
        assertEquals(NpcSagaCombatBrain.Type.CAST, intent.type());
        assertEquals(net.bullettrain.xenopixelsmod.compat.npc.NpcBrainKiRotation.Band.WAVE,
                intent.kiBand());
    }

    @Test
    void farApproachingWithoutTravelRuns() {
        NpcSagaCombatContext ctx = far();
        ctx.targetApproaching = true;
        ctx.hasHitscan = false;
        ctx.hasTravel = false;
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(ctx, ALWAYS);
        assertEquals(NpcSagaCombatBrain.Type.APPROACH, intent.type());
        assertEquals(NpcSagaCombatBrain.Locomotion.RUN, intent.locomotion());
    }

    @Test
    void midRangeCastsBlastWhenKiReady() {
        NpcSagaCombatContext ctx = mid();
        ctx.hasMidKi = true;
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(ctx, ALWAYS);
        assertEquals(NpcSagaCombatBrain.Type.CAST, intent.type());
        assertEquals(net.bullettrain.xenopixelsmod.compat.npc.NpcBrainKiRotation.Band.BLAST,
                intent.kiBand());
    }

    @Test
    void midRangeApproachesWhenCastMisses() {
        NpcSagaCombatContext ctx = mid();
        ctx.hasMidKi = true;
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(ctx, NEVER);
        assertEquals(NpcSagaCombatBrain.Type.APPROACH, intent.type());
        assertEquals(NpcSagaCombatBrain.Locomotion.RUN, intent.locomotion());
    }

    @Test
    void meleePressureWhenComboRolls() {
        NpcSagaCombatContext ctx = melee();
        ctx.comboReady = true;
        ctx.allowCombo = true;
        ctx.allowMelee = true;
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(ctx, ALWAYS);
        assertEquals(NpcSagaCombatBrain.Type.COMBO, intent.type());
        assertEquals(NpcSagaCombatBrain.ComboRole.PRESSURE, intent.combo());
    }

    @Test
    void meleeJabWhenComboMisses() {
        NpcSagaCombatContext ctx = melee();
        ctx.comboReady = true;
        ctx.allowCombo = true;
        ctx.allowMelee = true;
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(ctx, NEVER);
        assertEquals(NpcSagaCombatBrain.Type.MELEE, intent.type());
        assertNull(intent.combo());
    }

    @Test
    void meleeHoldsWhenStrikeIsOff() {
        NpcSagaCombatContext ctx = melee();
        ctx.allowMelee = false;
        NpcSagaCombatBrain.Intent intent = NpcSagaCombatBrain.decide(ctx, ALWAYS);
        assertEquals(NpcSagaCombatBrain.Type.HOLD, intent.type());
    }

    private static NpcSagaCombatContext melee() {
        NpcSagaCombatContext ctx = new NpcSagaCombatContext();
        ctx.dist3D = 3.0;
        return ctx;
    }

    private static NpcSagaCombatContext mid() {
        NpcSagaCombatContext ctx = new NpcSagaCombatContext();
        ctx.dist3D = 8.0;
        return ctx;
    }

    private static NpcSagaCombatContext far() {
        NpcSagaCombatContext ctx = new NpcSagaCombatContext();
        ctx.dist3D = 20.0;
        return ctx;
    }

    private static NpcSagaCombatContext out() {
        NpcSagaCombatContext ctx = new NpcSagaCombatContext();
        ctx.dist3D = 40.0;
        return ctx;
    }
}
