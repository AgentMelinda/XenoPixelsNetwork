package net.bullettrain.xenopixelsmod.compat.npc.brain.v2;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.TechniqueDispatcher;
import net.bullettrain.xenopixelsmod.compat.npc.NpcBrainKiRotation;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatRanges;
import net.bullettrain.xenopixelsmod.compat.npc.NpcKiCooldowns;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Snapshot for {@link NpcSagaCombatBrain#decide}, matching DMZ {@code CombatContext}.
 *
 * <p>Fields are public so unit tests can build a context without a live entity.
 */
public final class NpcSagaCombatContext {
    // Aliases for NpcCombatRanges, which is now the single definition of these numbers. They are
    // DragonMineZ's own (SagasCombatBrain.MELEE_RANGE / MID_RANGE / OUT_RANGE and
    // CombatContext.APPROACH_THRESHOLD) and were duplicated here while the v1 brain used bands of
    // its own. Kept as names so existing call sites and tests read unchanged.
    public static final double MELEE = NpcCombatRanges.MELEE;
    public static final double MID = NpcCombatRanges.MID;
    public static final double OUT = NpcCombatRanges.OUT;
    public static final double APPROACH_THRESHOLD = NpcCombatRanges.APPROACH_THRESHOLD;

    public double dist3D;
    public boolean targetApproaching;
    public boolean targetBlocking;
    public boolean targetHelpless;
    public boolean targetTransforming;
    public boolean targetCasting;
    public boolean targetFiring;
    public float selfHpPct = 1.0f;
    public boolean comboReady = true;
    public boolean vanishReady = true;
    public boolean dashReady = true;
    public boolean advanced = true;
    public boolean hasHitscan;
    public boolean hasGuardBreak;
    public boolean hasTravel;
    public boolean hasZoning;
    public boolean hasMidKi;
    public boolean allowRecovery = true;
    public boolean allowCombo = true;
    public boolean allowMelee = true;

    public boolean targetApproaching() {
        return targetApproaching;
    }

    public static NpcSagaCombatContext snapshot(LivingEntity npc, LivingEntity target,
                                               NpcCombatProfile profile) {
        NpcSagaCombatContext c = new NpcSagaCombatContext();
        if (npc == null || target == null) {
            return c;
        }
        c.dist3D = npc.distanceTo(target);
        Vec3 toSelf = npc.position().subtract(target.position());
        Vec3 toSelfNorm = toSelf.lengthSqr() > 1.0E-6 ? toSelf.normalize() : Vec3.ZERO;
        c.targetApproaching = target.getDeltaMovement().dot(toSelfNorm) > APPROACH_THRESHOLD;
        c.selfHpPct = npc.getMaxHealth() > 0.0f ? npc.getHealth() / npc.getMaxHealth() : 0.0f;
        c.comboReady = profile != null && (profile.brainFlyingFist || profile.brainHeavyHit
                || profile.brainBoneCrusher || profile.brainStrike || profile.brainDisengage);
        c.allowCombo = c.comboReady;
        c.allowMelee = profile == null || profile.brainStrike || profile.brainChance("strike") > 0;
        c.allowRecovery = profile != null && profile.brainDisengage;
        c.vanishReady = profile != null && profile.brainVanish && profile.brainChance("vanish") > 0;
        c.dashReady = profile != null && profile.brainChase && profile.brainChance("chase") > 0;
        c.hasHitscan = readyBand(npc, profile, NpcBrainKiRotation.Band.BLAST)
                || readyBand(npc, profile, NpcBrainKiRotation.Band.NAMED);
        c.hasTravel = readyBand(npc, profile, NpcBrainKiRotation.Band.WAVE);
        c.hasZoning = readyBand(npc, profile, NpcBrainKiRotation.Band.DISK);
        c.hasMidKi = c.hasHitscan;
        c.hasGuardBreak = c.hasTravel || c.hasHitscan;
        if (target instanceof ServerPlayer player) {
            fillPlayerState(c, player);
        }
        return c;
    }

    private static boolean readyBand(LivingEntity npc, NpcCombatProfile profile,
                                     NpcBrainKiRotation.Band band) {
        if (profile == null || !NpcBrainKiRotation.allows(profile, band)) {
            return false;
        }
        String id = NpcBrainKiRotation.pick(profile, 0, technique ->
                NpcBrainKiRotation.bandOf(technique) == band
                        && (npc == null || NpcKiCooldowns.ready(npc, technique)));
        return id != null;
    }

    private static void fillPlayerState(NpcSagaCombatContext c, ServerPlayer player) {
        try {
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).resolve().orElse(null);
            if (data == null) {
                return;
            }
            c.targetBlocking = data.getStatus().isBlocking();
            c.targetHelpless = data.getStatus().isStunned() || data.getStatus().isKnockedDown();
            c.targetTransforming = data.getStatus().isActionCharging();
            c.targetCasting = data.getTechniques().isTechniqueCharging();
            c.targetFiring = TechniqueDispatcher.isFiringKiAttack(player);
        } catch (Throwable ignored) {
            // Stats API absent or a resolve miss — treat as a normal mob target.
        }
    }
}
