package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * What an NPC's configured stats actually come out as in a fight.
 *
 * <p>Exists because the question "how much damage does this NPC do?" had no answer in game. The
 * CustomNPCs editor shows a field called "Damage", but that is its own native melee strength, which
 * XenoPixels pins to 1 while DragonMineZ stats are authoritative — so the number on screen was the
 * one that did nothing, and the number that applied was never shown anywhere. A wrong figure and a
 * wrong formula looked identical from the outside.
 *
 * <p>Everything here is derived from the same helpers the combat path uses, never re-implemented, so
 * a reading that disagrees with what a player takes is a real bug rather than a stale copy of the
 * maths.
 */
public final class NpcDamageReport {

    private NpcDamageReport() {
    }

    /**
     * One NPC's offensive figures.
     *
     * @param melee          damage a fully rested punch lands
     * @param strike         damage a strike technique lands before its own multiplier
     * @param ki             damage a ki attack lands before the technique's multiplier
     * @param staminaCost    stamina one punch asks for
     * @param maxStamina     the largest that pool can ever be
     * @param sustainable    whether stamina regenerates fast enough to keep paying full price
     * @param strScaling     the race/class factor applied to strength
     */
    public record Report(double melee, double strike, double ki,
                         double staminaCost, double maxStamina, boolean sustainable,
                         double strScaling) {

        /** The share of its melee damage this NPC lands once its pool is empty. */
        public double tiredMelee() {
            return melee * NpcStatMath.MIN_TIRED_FRACTION;
        }

        /** Punches a full pool pays for outright. */
        public int punchesFromFull() {
            if (staminaCost <= 0.0) return Integer.MAX_VALUE;
            return (int) Math.max(0, Math.floor(maxStamina / staminaCost));
        }
    }

    public static Report of(NpcCombatProfile profile) {
        return of(null, profile);
    }

    /** Uses the attached DragonMineZ calculation when reporting a live NPC. */
    public static Report of(Entity entity, NpcCombatProfile profile) {
        if (profile == null) {
            return new Report(0, 0, 0, 0, 0, false, 1.0);
        }
        double melee = entity instanceof LivingEntity living
                ? NpcDmzStats.meleeDamage(living, profile) : profile.meleeDamage();
        double strike = entity instanceof LivingEntity living
                ? NpcDmzStats.strikeDamage(living, profile) : profile.strikeDamage();
        double ki = entity instanceof LivingEntity living
                ? NpcKiAttackDispatcher.kiDamage(living, profile) : profile.kiDamage();
        double cost = Math.max(1.0, Math.ceil(melee
                * com.dragonminez.common.config.ConfigManager.getCombatConfig()
                        .getStaminaConsumptionRatio()));
        double maxStamina = NpcStatMath.maxStamina(profile.resistance,
                NpcFormLookup.multiplier(profile, "STM"));
        // The brains punch about once a second, and stamina regenerates every tick from VIT, so a
        // second's worth of regeneration is what has to cover one punch for the NPC to keep hitting
        // at full strength indefinitely.
        double regenPerSecond = NpcStatMath.resourceRecoveryPerTick(profile.vitality,
                NpcFormLookup.multiplier(profile, "VIT")) * 20.0;
        return new Report(melee, strike, ki,
                cost, maxStamina, regenPerSecond >= cost,
                NpcStatScaling.of(profile, "STR"));
    }

    /**
     * A figure in the same abbreviated form the health readout uses.
     *
     * <p>Public because callers outside this package need it and {@code NpcVitalityMath} is not
     * theirs to reach into; sharing the formatter is also what keeps a damage figure and a health
     * figure looking like they belong to the same NPC.
     */
    public static String format(double value) {
        return NpcVitalityMath.displayedMaxHealth(value);
    }

    /** Short label for the NPC editor, beside the HP figure. */
    public static String displayedMeleeText(NpcCombatProfile profile) {
        return NpcVitalityMath.displayedMaxHealth(of(profile).melee());
    }
}
