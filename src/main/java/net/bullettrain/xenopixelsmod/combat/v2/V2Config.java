package net.bullettrain.xenopixelsmod.combat.v2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server tuning for XenoCombat v2, written to {@code config/xenopixelsmod-combat-v2.json}.
 *
 * <p>Nothing here is sent to clients as configuration. Whatever a client needs to draw (which
 * branches are open, how long is left) arrives as live state in {@code CombatV2StatePacket}, so a
 * client cannot disagree with the server about a window.
 *
 * <p>Whether v2 runs at all is not here: that is {@code combatControllerMode}, so the existing
 * switch, sweep and sync stay the single owner of the mode.
 *
 * <p>One {@link Values} object is the whole configuration. It is the file shape, the defaults and
 * the thing the code reads, so a setting is declared once and cannot be saved under one name and
 * read under another. Every value is clamped when a file is loaded.
 *
 * <p>Damage scales multiply the fighter's DragonMineZ melee damage. A file written before that
 * was true (version below {@link #VERSION}) has those values put back to their defaults on load,
 * because the same number meant something else in it.
 */
public final class V2Config {
    /** Bumped when the meaning of an existing setting changes. */
    static final int VERSION = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Values values = new Values();

    private V2Config() {
    }

    /** The configuration in force. Never null. */
    public static Values get() {
        return values;
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-combat-v2.json");
    }

    public static void load() {
        Path path = path();
        if (!Files.exists(path)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            Values loaded = GSON.fromJson(reader, Values.class);
            if (loaded != null) {
                if (loaded.version < VERSION) {
                    loaded.rebase();
                    XenoPixelsMod.LOGGER.info("Combat v2 config was written by an older build: "
                            + "damage scales, strike range and input buffer were reset to defaults");
                }
                values = loaded.clamped();
            }
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Failed to load combat v2 config; using what is in force", e);
        }
        // Rewritten so a file from an older build gains the settings added since.
        save();
    }

    public static void save() {
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            values.version = VERSION;
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(values, writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save combat v2 config", e);
        }
    }

    /** Replaces the configuration in force. For tests and reloads; the values are clamped. */
    static void use(Values next) {
        values = next == null ? new Values() : next.clamped();
    }

    /** Every v2 setting. Field defaults are what a missing key reads as. */
    public static final class Values {

        /** Which build's meaning of the settings this file uses. Absent in the first files. */
        public int version;

        // ---- input ----
        /** Ticks an early input is held waiting for the current beat to open. */
        public int inputBufferTicks = 8;
        /** Minimum ticks between two accepted attack inputs from one player. */
        public int minInputIntervalTicks = 2;

        // ---- strikes ----
        /** How far a strike reaches, from the attacker's eyes to the target's hitbox. */
        public double strikeRange = 3.75;
        /**
         * How squarely the attacker must face the target: 1 dead ahead, 0 anywhere in the front
         * half, -1 no facing requirement at all.
         */
        public double strikeFacingDot = 0.0;
        /** A strike does not land through a wall. */
        public boolean strikeNeedsLineOfSight = true;
        /**
         * Whether v2 strikes pay DragonMineZ's own per-hit stamina cost. DragonMineZ charges the
         * attacker stamina for every melee hit and zeroes it when it runs short; in v2 stamina is
         * the defensive resource (grab, guard), so strikes leave it alone unless this is on.
         */
        public boolean strikesDrainStamina = false;
        /** Extra damage of a fully charged attack, as a share of the beat's own damage. */
        public double chargeDamageBonus = 0.35;
        /** Multiplier on the horizontal part of every hit reaction. */
        public double reactionScale = 1.0;

        // ---- chase / dragon homing ----
        public boolean chaseEnabled = true;
        public double chaseSpeed = 1.6;
        public double chaseMaxRange = 64.0;
        public double chaseArriveDistance = 1.6;
        public float chaseKiCost = 4.0f;
        /** Ticks after a launching hit in which chase is free of the range check and ki cost. */
        public int homingWindowTicks = 40;
        /** Turn the fighter's aura on for the length of a chase or dash, as v1's chase does. */
        public boolean travelAura = true;
        /** Put a fighter who has learned Fly into DragonMineZ's flight pose while travelling. */
        public boolean travelFlightPose = true;

        // ---- z-burst ----
        public boolean zBurstEnabled = true;
        public double zBurstRange = 14.0;
        public double zBurstSpeed = 2.2;
        public float zBurstKiCost = 3.0f;

        // ---- dragon dash ----
        public boolean dragonDashEnabled = true;
        public double dragonDashRange = 24.0;
        public double dragonDashSpeed = 2.6;
        public float dragonDashKiCost = 8.0f;
        public float dragonDashStaminaCost = 6.0f;
        public float dragonDashDamageScale = 2.0f;
        public int dragonDashCooldownTicks = 60;
        /** N may cross the same target once during travel or within this window after an accepted hit. */
        public int dragonDashFollowupTicks = 24;

        // ---- rush ----
        public boolean rushEnabled = true;
        public double rushRange = 6.0;
        public float rushKiCostPerImpact = 2.0f;
        /** Each impact but the last. A rush is several of these, so it is below a single jab. */
        public float rushDamageScale = 0.9f;
        public float rushFinisherDamageScale = 1.6f;
        /** After a Xeno rush breaker or finisher strike, chase the launched target automatically. */
        public boolean rushStrikeAutoChase = true;

        // ---- super counter ----
        public boolean counterEnabled = true;
        public int counterWindowTicks = 10;
        public int counterLockoutTicks = 40;
        public float counterKiCost = 6.0f;
        public float counterDamageScale = 1.65f;
        /** The attacker must be within this many blocks to be countered. */
        public double counterRange = 16.0;

        // ---- vanish ----
        public boolean vanishEnabled = true;
        /** Follow the validated DMZ lock range, including targets above or below the fighter. */
        public boolean vanishUsesLockRange = true;
        /** Optional shorter reach when vanishUsesLockRange is false. */
        public double vanishRange = 12.0;
        public float vanishKiCost = 8.0f;
        public float vanishStaminaCost = 0.0f;
        /** Ticks after a vanish in which attacks pass through the fighter. */
        public int vanishIFrameTicks = 8;
        /**
         * Ticks before the next vanish. Kept well above the i-frames: ki costs are small beside a
         * trained fighter's pool, so it is this gap, not the cost, that stops a fighter chaining
         * vanishes into never being hittable.
         */
        public int vanishCooldownTicks = 24;
        /** How far a vanish with no target in range carries the fighter. */
        public double vanishBlinkDistance = 5.0;

        // ---- grab ----
        public boolean grabEnabled = true;
        /**
         * Whether the grab and throw also work under the {@code legacy} and {@code bt3_manual}
         * controllers. They are the one part of v2 those controllers run; nothing else of v2 does.
         */
        public boolean grabOutsideV2 = true;
        public double grabRange = 2.6;
        /** Cosine of the half-angle the victim must be inside, in front of the grabber. */
        public double grabFacingDot = 0.5;
        public int grabStartupTicks = 6;
        public int grabHoldTicks = 14;
        /** Ticks after a grab connects in which a player victim may break it; 0 turns it off. */
        public int grabTechWindowTicks = 6;
        public int grabCooldownTicks = 50;
        public float grabStaminaCost = 8.0f;
        public float grabDamageScale = 1.6f;
        /** Whether mobs and NPCs can be grabbed at all, or only players. */
        public boolean grabNonPlayers = true;
        /** Entities taller than this cannot be picked up. */
        public double grabMaxVictimHeight = 3.2;

        /** Puts back the defaults of every setting whose meaning changed since this file. */
        void rebase() {
            Values d = new Values();
            inputBufferTicks = d.inputBufferTicks;
            strikeRange = d.strikeRange;
            dragonDashDamageScale = d.dragonDashDamageScale;
            rushDamageScale = d.rushDamageScale;
            rushFinisherDamageScale = d.rushFinisherDamageScale;
            counterDamageScale = d.counterDamageScale;
            grabDamageScale = d.grabDamageScale;
            version = VERSION;
        }

        /** Clamps every value into a range the code can run with, in place. */
        Values clamped() {
            inputBufferTicks = clamp(inputBufferTicks, 0, 20);
            minInputIntervalTicks = clamp(minInputIntervalTicks, 1, 20);

            strikeRange = clamp(strikeRange, 1.0, 12.0);
            strikeFacingDot = clamp(strikeFacingDot, -1.0, 1.0);
            chargeDamageBonus = clamp(chargeDamageBonus, 0.0, 5.0);
            reactionScale = clamp(reactionScale, 0.0, 5.0);

            chaseSpeed = clamp(chaseSpeed, 0.1, 6.0);
            chaseMaxRange = clamp(chaseMaxRange, 2.0, 512.0);
            chaseArriveDistance = clamp(chaseArriveDistance, 0.3, 6.0);
            chaseKiCost = cost(chaseKiCost);
            homingWindowTicks = clamp(homingWindowTicks, 0, 200);

            zBurstRange = clamp(zBurstRange, 2.0, 64.0);
            zBurstSpeed = clamp(zBurstSpeed, 0.1, 6.0);
            zBurstKiCost = cost(zBurstKiCost);

            dragonDashRange = clamp(dragonDashRange, 2.0, 128.0);
            dragonDashSpeed = clamp(dragonDashSpeed, 0.1, 6.0);
            dragonDashKiCost = cost(dragonDashKiCost);
            dragonDashStaminaCost = cost(dragonDashStaminaCost);
            dragonDashDamageScale = scale(dragonDashDamageScale);
            dragonDashCooldownTicks = clamp(dragonDashCooldownTicks, 0, 1200);
            dragonDashFollowupTicks = clamp(dragonDashFollowupTicks, 0, 100);

            rushRange = clamp(rushRange, 1.0, 32.0);
            rushKiCostPerImpact = cost(rushKiCostPerImpact);
            rushDamageScale = scale(rushDamageScale);
            rushFinisherDamageScale = scale(rushFinisherDamageScale);

            counterWindowTicks = clamp(counterWindowTicks, 1, 60);
            counterLockoutTicks = clamp(counterLockoutTicks, 0, 600);
            counterKiCost = cost(counterKiCost);
            counterDamageScale = scale(counterDamageScale);
            counterRange = clamp(counterRange, 1.0, 128.0);

            vanishRange = clamp(vanishRange, 1.0, 64.0);
            vanishKiCost = cost(vanishKiCost);
            vanishStaminaCost = cost(vanishStaminaCost);
            vanishIFrameTicks = clamp(vanishIFrameTicks, 0, 60);
            vanishCooldownTicks = clamp(vanishCooldownTicks, 0, 600);
            vanishBlinkDistance = clamp(vanishBlinkDistance, 0.5, 16.0);

            grabRange = clamp(grabRange, 1.0, 8.0);
            grabFacingDot = clamp(grabFacingDot, -1.0, 1.0);
            grabStartupTicks = clamp(grabStartupTicks, 1, 40);
            grabHoldTicks = clamp(grabHoldTicks, 1, 100);
            grabTechWindowTicks = clamp(grabTechWindowTicks, 0, 100);
            grabCooldownTicks = clamp(grabCooldownTicks, 0, 1200);
            grabStaminaCost = cost(grabStaminaCost);
            grabDamageScale = scale(grabDamageScale);
            grabMaxVictimHeight = clamp(grabMaxVictimHeight, 0.5, 32.0);
            return this;
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static double clamp(double value, double min, double max) {
            if (!Double.isFinite(value)) return min;
            return Math.max(min, Math.min(max, value));
        }

        private static float cost(float value) {
            return (float) clamp(value, 0.0, 100000.0);
        }

        private static float scale(float value) {
            return (float) clamp(value, 0.0, 20.0);
        }
    }
}
