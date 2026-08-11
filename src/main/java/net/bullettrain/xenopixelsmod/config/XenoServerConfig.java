package net.bullettrain.xenopixelsmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative feature flags and combat balance.
 * Written to {@code config/xenopixelsmod-server.json}.
 */
public final class XenoServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-server.json");

    // --- HUD / DMZ ---
    /** When false, clients block DMZ vanilla HUD overlays. */
    public static boolean dmzHudEnabled = false;
    /** Install/patch DMZ form JSON + skill offerings on boot. */
    public static boolean dmzContentBootstrap = true;

    // --- Combat master switches ---
    public static boolean bt3CombatEnabled = true;
    public static boolean bt3ComboEnabled = true;
    public static boolean bt3VanishEnabled = true;
    public static boolean bt3ChaseDashEnabled = true;
    public static boolean bt3BackstepEnabled = true;
    public static boolean bt3FinisherEnabled = true;
    public static boolean bt3ChargeAttackEnabled = true;
    public static boolean bt3DragonDashEnabled = true;
    /** Hold-to-block; drains STM and reduces damage (BT3 / XV2 guard). */
    public static boolean bt3GuardEnabled = true;
    /** After taking a hit, vanish becomes a super-counter for a short window. */
    public static boolean bt3SuperCounterEnabled = true;
    /** Mid-combo ki blast that cancels the string. */
    public static boolean bt3KiBlastCancelEnabled = true;
    /** Mid-combo Z-Burst step-in toward lock-on target. */
    public static boolean bt3ZBurstEnabled = true;
    /** Client lock-on cycle next/prev (server flag allows the feature). */
    public static boolean bt3LockCycleEnabled = true;
    /**
     * Combo string uses punch/uppercut anims only (no mixed DMZ kicks).
     * Still triggered by normal attack mash.
     */
    public static boolean bt3ComboPunchesOnly = true;
    /** Prevent players from damaging / knocking DMZ master NPCs. */
    public static boolean protectDmzMasters = true;
    /** Air chase / rush chain after knockup. */
    public static boolean bt3RushChainEnabled = true;
    /** Sonic sway side-step with brief i-frames. */
    public static boolean bt3SonicSwayEnabled = true;
    /** Ultimate skill slot (big KI smash). */
    public static boolean bt3UltimateEnabled = true;
    /** Sparking / limit-style meter. */
    public static boolean bt3SparkingEnabled = true;
    /** Transform impact ring when form changes. */
    public static boolean bt3TransformImpactEnabled = true;

    // --- Phase 3 progression ---
    /** Spawnable training dummy + damage meter ({@code /xenotrain}). */
    public static boolean trainingDummyEnabled = true;
    /** Parallel quest lite ({@code /xenoquest}). */
    public static boolean parallelQuestEnabled = true;
    /** Mentor pairing assist ({@code /xenomentor}). */
    public static boolean mentorEnabled = true;

    // --- Copycat glowstone power ---
    /** Require Forge Energy for copycat glowstone light. False keeps the block always lit. */
    public static boolean copycatForgeEnergyEnabled = false;
    public static int copycatEnergyCapacity = 100_000;
    public static int copycatMaxReceiveFePerTick = 1_000;
    public static int copycatEnergyUseFePerTick = 10;

    /**
     * Hard ceiling on a ship-as-missile's loft and cruise altitude. 0 disables the clamp.
     *
     * <p>Northstar teleports anything crossing {@code atmosphereTeleportHeight} (default 1000)
     * into a space dimension. A Sable hull is a sub-level, not an entity, so Northstar's
     * {@code ignore_world_bounds_teleport} entity tag cannot exempt it — the only reliable
     * defence is to keep the arc below that altitude. Default 950 leaves margin under the
     * stock 1000; raise it if you have raised Northstar's, lower it if a hull still transits.
     */
    public static double missileMaxApexY = 950.0;

    // --- Missile terminal guidance ---
    /**
     * Aim the terminal phase at a gravity-compensated point instead of straight at the target.
     *
     * <p><b>Off by default — the plain pure-pursuit path is the shipped behaviour.</b>
     * Compensation was tried against a reported consistent short-and-low bias, but in practice
     * it was worse than the uncompensated path, which lands within a few blocks. It is kept
     * behind this flag rather than deleted so the idea can be re-tested with better constants
     * (the {@code 0.5·g·t²} term almost certainly over-corrects at short time-to-go, since the
     * missile is already diving).
     *
     * <p>With this false the terminal phase is arithmetically identical to the original code.
     */
    public static boolean missileTerminalGravityCompensation = false;

    // --- Thruster impulse guard ---
    /**
     * Refuse to hand Sable a thruster impulse that is not finite, or that exceeds
     * {@link #thrusterMaxImpulse}.
     *
     * <p>A single bad impulse does not just move a ship — Rapier integrates it into the body's
     * velocity and inertia, and the sub-level tears itself apart in a way that looks like an
     * explosion and cannot be undone by fixing the input on the next tick. This clamps instead,
     * and logs once per ship so a runaway shows up in the log rather than only in the wreckage.
     *
     * <p>Disable to get the raw pre-guard behaviour back when diagnosing.
     */
    public static boolean thrusterImpulseGuardEnabled = true;
    /**
     * Largest per-tick impulse magnitude a single thruster may apply.
     *
     * <p>Normal full power is {@code maxForce × 1.0 × step} — with the 1,200,000 default force
     * and a 60 Hz step that is about 20,000, so the 50,000 default leaves ample headroom for
     * tuning while still catching a genuine runaway.
     */
    public static double thrusterMaxImpulse = 50_000.0;

    // --- Vanish shade (the black afterimage left behind on a vanish) ---
    /** Stamp a black humanoid silhouette with electric arcs where a vanish started. */
    public static boolean vanishShadeEnabled = true;
    /** Silhouette density, 0..3. 1.0 is roughly fifty particles; 0 disables the silhouette. */
    public static double vanishShadeDensity = 1.0;
    /** Thunder crack volume on vanish, 0..1. 0 silences it without touching the visual. */
    public static double vanishThunderVolume = 0.35;
    /**
     * Sound ids played when a fighter leaves and arrives during a vanish, e.g.
     * {@code "xenopixelsmod:vanish_out"}.
     *
     * <p>Empty means "keep the built-in behaviour", which is DragonMineZ's {@code evasion1} /
     * {@code evasion2} with a vanilla enderman-teleport fallback. This is a configured id rather
     * than a registered-and-shipped sound event on purpose: declaring a sound in
     * {@code sounds.json} whose {@code .ogg} is not present makes every client log a missing-asset
     * error on resource load, so the mod must not ship a dangling entry. Point these at your own
     * sound once you have added the file, or at any sound from an installed mod.
     */
    public static String vanishSoundOut = "xenopixelsmod:vanish";
    public static String vanishSoundIn = "xenopixelsmod:vanish";

    public static float rushChainKiCost = 10.0f;
    public static float rushChainDamageScale = 0.9f;
    public static double rushChainRange = 16.0;
    public static float sonicSwayStaminaCost = 6.0f;
    public static int sonicSwayIFramesTicks = 8;
    public static int sonicSwayCooldownTicks = 18;
    public static float ultimateKiCost = 35.0f;
    public static float ultimateDamageScale = 2.4f;
    public static int ultimateCooldownTicks = 200;
    public static float sparkingBuildPerHit = 6.0f;
    public static float sparkingBuildOnHurt = 3.0f;
    public static int sparkingDurationTicks = 100;
    public static float sparkingDamageMult = 1.35f;
    public static float transformImpactRadius = 3.5f;
    public static float transformImpactKnock = 0.45f;
    /**
     * Chance (0..1) that chase dash / dragon-dash chase phase succeeds.
     * Default 0.50 (50%). Set 0.45 for 45%.
     */
    public static float chaseSuccessChance = 0.50f;

    // --- Phase-1 combat balance ---
    public static float guardDamageReduction = 0.55f;
    public static float guardStaminaPerHit = 8.0f;
    public static float guardStaminaPerSec = 3.0f;
    public static int guardBreakStunTicks = 25;
    public static int superCounterWindowTicks = 12;
    public static float superCounterKiCost = 10.0f;
    public static float superCounterDamageScale = 1.35f;
    public static float kiBlastCancelKiCost = 6.0f;
    public static float kiBlastCancelDamageScale = 0.85f;
    public static float zBurstKiCost = 8.0f;
    public static float zBurstDamageScale = 0.75f;
    public static double zBurstRange = 8.0;

    // --- Form multipliers (public server balance: scales DMZ form / stack-form stat mults) ---
    /**
     * Global form power scale for this server. Affects form bonuses only (base form stays 1.0).
     * Formula: {@code 1 + (formMult - 1) * formStatMultiplier}.
     * <ul>
     *   <li>{@code 1.0} — stock DMZ form power</li>
     *   <li>{@code 2.0} — double form bonuses</li>
     *   <li>{@code 0.0} — forms give no stat bonus</li>
     * </ul>
     * Tunable live with {@code /xenoform set} or {@code config/xenopixelsmod-server.json}.
     */
    public static float formStatMultiplier = 1.0f;
    /**
     * Per-form overall power scales. Keys: {@code group.form} (e.g. {@code xenopixels_gods_forms.ssb})
     * or short form id ({@code ssb}). When missing, {@link #formStatMultiplier} is used.
     */
    public static final Map<String, Float> formPerFormMultipliers = new ConcurrentHashMap<>();
    /**
     * Global per-stat scales (all forms). Keys: {@code str}, {@code pwr}, {@code def}, {@code skp},
     * {@code stm}, {@code vit}, {@code ene}, {@code speed}. Default when missing: 1.0.
     */
    public static final Map<String, Float> formPerStatMultipliers = new ConcurrentHashMap<>();
    /**
     * Per-form per-stat scales. Outer key = form id, inner key = stat (str/pwr/…).
     * Example: {@code ssb.str = 5} multiplies only Super Saiyan Blue strength bonus.
     */
    public static final Map<String, Map<String, Float>> formPerFormStatMultipliers = new ConcurrentHashMap<>();
    /** DMZ form combat stats you can scale. */
    public static final String[] FORM_STAT_KEYS = {
            "str", "skp", "stm", "def", "vit", "pwr", "ene", "speed"
    };
    /** Minimum allowed form scale (global, per-form, or per-stat). */
    public static final float FORM_STAT_MULT_MIN = 0.0f;
    /** Maximum allowed form scale (global, per-form, or per-stat). */
    public static final float FORM_STAT_MULT_MAX = 1_000_000.0f;

    // --- KI overcharge (power release %) ---
    /** Enable bigger/harder KI attacks when release is above the threshold. */
    public static boolean kiOverchargeEnabled = true;
    /** Power-release % where overcharge scaling starts (default 175). */
    public static int kiOverchargeThreshold = 175;
    /** Size growth per 1% release above threshold (default 0.012 = +1.2%/pt). */
    public static float kiOverchargeSizePerPercent = 0.012f;
    /** Damage growth per 1% release above threshold. */
    public static float kiOverchargeDamagePerPercent = 0.015f;
    /** Explosion radius growth per 1% release above threshold. */
    public static float kiOverchargeExplosionPerPercent = 0.014f;
    /** Master multiplier applied to all overcharge growth. */
    public static float kiOverchargeMultiplier = 1.0f;
    /** Cap on overcharge scale factor (1 + growth), e.g. 3.0 = triple max. */
    public static float kiOverchargeMaxScale = 3.0f;

    // --- Balance ---
    public static double vanishMaxRange = 7.0;
    public static double chaseMaxRange = 14.0;
    public static double backstepMaxRange = 10.0;
    public static double chargeAttackRange = 5.0;
    public static double dragonDashRange = 16.0;
    // --- KI costs ---
    public static float vanishKiCost = 8.0f;
    public static float chaseKiCost = 12.0f;
    public static float backstepKiCost = 6.0f;
    public static float comboKiCost = 1.5f;
    public static float finisherKiCost = 5.0f;
    public static float dragonDashKiCost = 10.0f;

    // --- Stamina costs ---
    /** Base stamina on fist charge release (scales with charge %). */
    public static float fistChargeStaminaCost = 18.0f;
    /** Base stamina on kick charge release (scales with charge %). */
    public static float kickChargeStaminaCost = 18.0f;
    /** Legacy alias; prefer fistChargeStaminaCost / kickChargeStaminaCost. */
    @Deprecated
    public static float chargeStaminaCost = 18.0f;
    /** Stamina drained per second while holding any charge (client warn + release scale). */
    public static float chargeHoldStaminaPerSec = 4.0f;
    public static float dragonDashStaminaCost = 22.0f;
    /** Extra stamina when releasing kick with W/S vertical bias. */
    public static float kickVerticalExtraStamina = 4.0f;

    // --- Damage / launch ---
    public static float comboDamageScale = 1.0f;
    public static float finisherDamageScale = 1.35f;
    public static float chargeDamageScale = 1.8f;
    public static float kickDamageScale = 1.15f;
    /** Upward launch multiplier when holding W during charged kick. */
    public static float kickUpLaunch = 1.35f;
    /** Downward launch multiplier when holding S during charged kick. */
    public static float kickDownLaunch = 1.15f;
    /** Extra reach (blocks) for charged kick while holding S. */
    public static float kickDownRangeBonus = 4.0f;
    public static int maxComboSteps = 5;
    /** Ticks to reach full charge (20 = 1s). */
    public static int chargeMaxTicks = 28;

    // --- YAWP (Yet Another World Protector) region protection ---
    /**
     * Let YAWP regions veto DMZ ki griefing. Only has an effect when YAWP is installed;
     * DMZ's allowKiGriefing* gamerules still apply first, this can only deny further.
     */
    public static boolean yawpKiGriefingEnabled = true;
    /**
     * YAWP flags consulted for ki griefing caused by a player. Griefing is denied when any
     * listed flag is DENIED at the target block, evaluated with the player's region
     * permissions, so region owners/members are unaffected.
     * Names are YAWP flag ids as used by {@code /wp flag add}; unknown names are ignored
     * (with a warning) so a YAWP update that renames a flag cannot break ki combat.
     */
    public static java.util.List<String> yawpPlayerKiFlags =
            new java.util.ArrayList<>(java.util.List.of("break-blocks", "explosions-blocks"));
    /** YAWP flags consulted for ki griefing caused by a mob or an unowned projectile. */
    public static java.util.List<String> yawpMobKiFlags =
            new java.util.ArrayList<>(java.util.List.of("mob-griefing", "explosions-blocks"));
    /**
     * Radius in blocks around a DMZ master treated as protected by the
     * {@code ki-griefing-masters} region flag. 0 disables master-aware ki protection.
     *
     * <p>DMZ gates master-structure griefing behind its own gamerule but exposes no "is this
     * block part of a master's site" query, so proximity to the master entity is the available
     * approximation. Only scanned when that flag is actually set on the region.
     */
    public static double masterKiGriefRadius = 24.0;

    private XenoServerConfig() {}

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            apply(data);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load server config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(snapshot(), writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save server config", e);
        }
    }

    public static Data snapshot() {
        Data d = new Data();
        d.dmzHudEnabled = dmzHudEnabled;
        d.dmzContentBootstrap = dmzContentBootstrap;
        d.bt3CombatEnabled = bt3CombatEnabled;
        d.bt3ComboEnabled = bt3ComboEnabled;
        d.bt3VanishEnabled = bt3VanishEnabled;
        d.bt3ChaseDashEnabled = bt3ChaseDashEnabled;
        d.bt3BackstepEnabled = bt3BackstepEnabled;
        d.bt3FinisherEnabled = bt3FinisherEnabled;
        d.bt3ChargeAttackEnabled = bt3ChargeAttackEnabled;
        d.bt3DragonDashEnabled = bt3DragonDashEnabled;
        d.bt3GuardEnabled = bt3GuardEnabled;
        d.bt3SuperCounterEnabled = bt3SuperCounterEnabled;
        d.bt3KiBlastCancelEnabled = bt3KiBlastCancelEnabled;
        d.bt3ZBurstEnabled = bt3ZBurstEnabled;
        d.bt3LockCycleEnabled = bt3LockCycleEnabled;
        d.bt3ComboPunchesOnly = bt3ComboPunchesOnly;
        d.protectDmzMasters = protectDmzMasters;
        d.bt3RushChainEnabled = bt3RushChainEnabled;
        d.bt3SonicSwayEnabled = bt3SonicSwayEnabled;
        d.bt3UltimateEnabled = bt3UltimateEnabled;
        d.bt3SparkingEnabled = bt3SparkingEnabled;
        d.bt3TransformImpactEnabled = bt3TransformImpactEnabled;
        d.trainingDummyEnabled = trainingDummyEnabled;
        d.parallelQuestEnabled = parallelQuestEnabled;
        d.mentorEnabled = mentorEnabled;
        d.copycatForgeEnergyEnabled = copycatForgeEnergyEnabled;
        d.copycatEnergyCapacity = copycatEnergyCapacity;
        d.copycatMaxReceiveFePerTick = copycatMaxReceiveFePerTick;
        d.copycatEnergyUseFePerTick = copycatEnergyUseFePerTick;
        d.missileMaxApexY = missileMaxApexY;
        d.missileTerminalGravityCompensation = missileTerminalGravityCompensation;
        d.thrusterImpulseGuardEnabled = thrusterImpulseGuardEnabled;
        d.thrusterMaxImpulse = thrusterMaxImpulse;
        d.vanishShadeEnabled = vanishShadeEnabled;
        d.vanishShadeDensity = vanishShadeDensity;
        d.vanishThunderVolume = vanishThunderVolume;
        d.vanishSoundOut = vanishSoundOut;
        d.vanishSoundIn = vanishSoundIn;
        d.rushChainKiCost = rushChainKiCost;
        d.rushChainDamageScale = rushChainDamageScale;
        d.rushChainRange = rushChainRange;
        d.sonicSwayStaminaCost = sonicSwayStaminaCost;
        d.sonicSwayIFramesTicks = sonicSwayIFramesTicks;
        d.sonicSwayCooldownTicks = sonicSwayCooldownTicks;
        d.ultimateKiCost = ultimateKiCost;
        d.ultimateDamageScale = ultimateDamageScale;
        d.ultimateCooldownTicks = ultimateCooldownTicks;
        d.sparkingBuildPerHit = sparkingBuildPerHit;
        d.sparkingBuildOnHurt = sparkingBuildOnHurt;
        d.sparkingDurationTicks = sparkingDurationTicks;
        d.sparkingDamageMult = sparkingDamageMult;
        d.transformImpactRadius = transformImpactRadius;
        d.transformImpactKnock = transformImpactKnock;
        d.chaseSuccessChance = chaseSuccessChance;
        d.guardDamageReduction = guardDamageReduction;
        d.guardStaminaPerHit = guardStaminaPerHit;
        d.guardStaminaPerSec = guardStaminaPerSec;
        d.guardBreakStunTicks = guardBreakStunTicks;
        d.superCounterWindowTicks = superCounterWindowTicks;
        d.superCounterKiCost = superCounterKiCost;
        d.superCounterDamageScale = superCounterDamageScale;
        d.kiBlastCancelKiCost = kiBlastCancelKiCost;
        d.kiBlastCancelDamageScale = kiBlastCancelDamageScale;
        d.zBurstKiCost = zBurstKiCost;
        d.zBurstDamageScale = zBurstDamageScale;
        d.zBurstRange = zBurstRange;
        d.formStatMultiplier = formStatMultiplier; // boxed Float in Data
        d.formPerFormMultipliers = new LinkedHashMap<>(formPerFormMultipliers);
        d.formPerStatMultipliers = new LinkedHashMap<>(formPerStatMultipliers);
        d.formPerFormStatMultipliers = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Float>> e : formPerFormStatMultipliers.entrySet()) {
            d.formPerFormStatMultipliers.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        d.kiOverchargeEnabled = kiOverchargeEnabled;
        d.kiOverchargeThreshold = kiOverchargeThreshold;
        d.kiOverchargeSizePerPercent = kiOverchargeSizePerPercent;
        d.kiOverchargeDamagePerPercent = kiOverchargeDamagePerPercent;
        d.kiOverchargeExplosionPerPercent = kiOverchargeExplosionPerPercent;
        d.kiOverchargeMultiplier = kiOverchargeMultiplier;
        d.kiOverchargeMaxScale = kiOverchargeMaxScale;
        d.vanishMaxRange = vanishMaxRange;
        d.chaseMaxRange = chaseMaxRange;
        d.backstepMaxRange = backstepMaxRange;
        d.chargeAttackRange = chargeAttackRange;
        d.dragonDashRange = dragonDashRange;
        d.vanishKiCost = vanishKiCost;
        d.chaseKiCost = chaseKiCost;
        d.backstepKiCost = backstepKiCost;
        d.comboKiCost = comboKiCost;
        d.finisherKiCost = finisherKiCost;
        d.dragonDashKiCost = dragonDashKiCost;
        d.fistChargeStaminaCost = fistChargeStaminaCost;
        d.kickChargeStaminaCost = kickChargeStaminaCost;
        d.chargeStaminaCost = chargeStaminaCost;
        d.chargeHoldStaminaPerSec = chargeHoldStaminaPerSec;
        d.dragonDashStaminaCost = dragonDashStaminaCost;
        d.kickVerticalExtraStamina = kickVerticalExtraStamina;
        d.comboDamageScale = comboDamageScale;
        d.finisherDamageScale = finisherDamageScale;
        d.chargeDamageScale = chargeDamageScale;
        d.kickDamageScale = kickDamageScale;
        d.kickUpLaunch = kickUpLaunch;
        d.kickDownLaunch = kickDownLaunch;
        d.kickDownRangeBonus = kickDownRangeBonus;
        d.maxComboSteps = maxComboSteps;
        d.chargeMaxTicks = chargeMaxTicks;
        d.yawpKiGriefingEnabled = yawpKiGriefingEnabled;
        d.yawpPlayerKiFlags = new java.util.ArrayList<>(yawpPlayerKiFlags);
        d.yawpMobKiFlags = new java.util.ArrayList<>(yawpMobKiFlags);
        d.masterKiGriefRadius = masterKiGriefRadius;
        return d;
    }

    public static void apply(Data d) {
        if (d == null) return;
        dmzHudEnabled = d.dmzHudEnabled;
        dmzContentBootstrap = d.dmzContentBootstrap;
        bt3CombatEnabled = d.bt3CombatEnabled;
        bt3ComboEnabled = d.bt3ComboEnabled;
        bt3VanishEnabled = d.bt3VanishEnabled;
        bt3ChaseDashEnabled = d.bt3ChaseDashEnabled;
        bt3BackstepEnabled = d.bt3BackstepEnabled;
        bt3FinisherEnabled = d.bt3FinisherEnabled;
        bt3ChargeAttackEnabled = d.bt3ChargeAttackEnabled;
        bt3DragonDashEnabled = d.bt3DragonDashEnabled;
        bt3GuardEnabled = d.bt3GuardEnabled;
        bt3SuperCounterEnabled = d.bt3SuperCounterEnabled;
        bt3KiBlastCancelEnabled = d.bt3KiBlastCancelEnabled;
        bt3ZBurstEnabled = d.bt3ZBurstEnabled;
        bt3LockCycleEnabled = d.bt3LockCycleEnabled;
        bt3ComboPunchesOnly = d.bt3ComboPunchesOnly;
        protectDmzMasters = d.protectDmzMasters;
        bt3RushChainEnabled = d.bt3RushChainEnabled;
        bt3SonicSwayEnabled = d.bt3SonicSwayEnabled;
        bt3UltimateEnabled = d.bt3UltimateEnabled;
        bt3SparkingEnabled = d.bt3SparkingEnabled;
        bt3TransformImpactEnabled = d.bt3TransformImpactEnabled;
        trainingDummyEnabled = d.trainingDummyEnabled;
        parallelQuestEnabled = d.parallelQuestEnabled;
        mentorEnabled = d.mentorEnabled;
        copycatForgeEnergyEnabled = d.copycatForgeEnergyEnabled;
        copycatEnergyCapacity = Math.max(1_000,
                d.copycatEnergyCapacity <= 0 ? 100_000 : d.copycatEnergyCapacity);
        copycatMaxReceiveFePerTick = Math.max(1,
                d.copycatMaxReceiveFePerTick <= 0 ? 1_000 : d.copycatMaxReceiveFePerTick);
        copycatEnergyUseFePerTick = Math.max(0,
                d.copycatEnergyUseFePerTick < 0 ? 10 : d.copycatEnergyUseFePerTick);
        missileMaxApexY = Math.max(0.0, d.missileMaxApexY);
        missileTerminalGravityCompensation = d.missileTerminalGravityCompensation;
        thrusterImpulseGuardEnabled = d.thrusterImpulseGuardEnabled;
        // A zero or negative cap would clamp every thruster to nothing; treat it as "unset".
        thrusterMaxImpulse = d.thrusterMaxImpulse > 0.0 ? d.thrusterMaxImpulse : 50_000.0;
        vanishShadeEnabled = d.vanishShadeEnabled;
        vanishShadeDensity = Math.max(0.0, Math.min(3.0, d.vanishShadeDensity));
        vanishThunderVolume = Math.max(0.0, Math.min(1.0, d.vanishThunderVolume));
        // Blank stays blank; a whitespace-only id would otherwise become a failed lookup
        // on every single vanish.
        vanishSoundOut = d.vanishSoundOut == null ? "" : d.vanishSoundOut.trim();
        vanishSoundIn = d.vanishSoundIn == null ? "" : d.vanishSoundIn.trim();
        rushChainKiCost = Math.max(0f, d.rushChainKiCost);
        rushChainDamageScale = d.rushChainDamageScale > 0f ? d.rushChainDamageScale : 0.9f;
        rushChainRange = d.rushChainRange > 0 ? d.rushChainRange : 16.0;
        sonicSwayStaminaCost = Math.max(0f, d.sonicSwayStaminaCost);
        sonicSwayIFramesTicks = Math.max(2, Math.min(40, d.sonicSwayIFramesTicks <= 0 ? 8 : d.sonicSwayIFramesTicks));
        sonicSwayCooldownTicks = Math.max(5, Math.min(80, d.sonicSwayCooldownTicks <= 0 ? 18 : d.sonicSwayCooldownTicks));
        ultimateKiCost = Math.max(0f, d.ultimateKiCost);
        ultimateDamageScale = d.ultimateDamageScale > 0f ? d.ultimateDamageScale : 2.4f;
        ultimateCooldownTicks = Math.max(40, Math.min(600, d.ultimateCooldownTicks <= 0 ? 200 : d.ultimateCooldownTicks));
        sparkingBuildPerHit = Math.max(0f, d.sparkingBuildPerHit);
        sparkingBuildOnHurt = Math.max(0f, d.sparkingBuildOnHurt);
        sparkingDurationTicks = Math.max(20, Math.min(400, d.sparkingDurationTicks <= 0 ? 100 : d.sparkingDurationTicks));
        sparkingDamageMult = d.sparkingDamageMult > 1f ? d.sparkingDamageMult : 1.35f;
        transformImpactRadius = d.transformImpactRadius > 0f ? d.transformImpactRadius : 3.5f;
        transformImpactKnock = Math.max(0f, d.transformImpactKnock);
        chaseSuccessChance = d.chaseSuccessChance < 0f ? 0.50f : Math.max(0f, Math.min(1f, d.chaseSuccessChance));
        guardDamageReduction = d.guardDamageReduction > 0f ? Math.min(0.95f, d.guardDamageReduction) : 0.55f;
        guardStaminaPerHit = Math.max(0f, d.guardStaminaPerHit);
        guardStaminaPerSec = Math.max(0f, d.guardStaminaPerSec);
        guardBreakStunTicks = Math.max(5, Math.min(80, d.guardBreakStunTicks <= 0 ? 25 : d.guardBreakStunTicks));
        superCounterWindowTicks = Math.max(4, Math.min(40, d.superCounterWindowTicks <= 0 ? 12 : d.superCounterWindowTicks));
        superCounterKiCost = Math.max(0f, d.superCounterKiCost);
        superCounterDamageScale = d.superCounterDamageScale > 0f ? d.superCounterDamageScale : 1.35f;
        kiBlastCancelKiCost = Math.max(0f, d.kiBlastCancelKiCost);
        kiBlastCancelDamageScale = d.kiBlastCancelDamageScale > 0f ? d.kiBlastCancelDamageScale : 0.85f;
        zBurstKiCost = Math.max(0f, d.zBurstKiCost);
        zBurstDamageScale = d.zBurstDamageScale > 0f ? d.zBurstDamageScale : 0.75f;
        zBurstRange = d.zBurstRange > 0 ? d.zBurstRange : 8.0;
        // Boxed Float: null when key missing from older configs → keep default 1.0
        if (d.formStatMultiplier != null) {
            formStatMultiplier = clampFormStatMultiplier(d.formStatMultiplier);
        }
        applyFormScaleMaps(d);
        kiOverchargeEnabled = d.kiOverchargeEnabled;
        kiOverchargeThreshold = Math.max(100, Math.min(500, d.kiOverchargeThreshold <= 0 ? 175 : d.kiOverchargeThreshold));
        kiOverchargeSizePerPercent = Math.max(0f, d.kiOverchargeSizePerPercent);
        kiOverchargeDamagePerPercent = Math.max(0f, d.kiOverchargeDamagePerPercent);
        kiOverchargeExplosionPerPercent = Math.max(0f, d.kiOverchargeExplosionPerPercent);
        kiOverchargeMultiplier = d.kiOverchargeMultiplier > 0f ? d.kiOverchargeMultiplier : 1f;
        kiOverchargeMaxScale = d.kiOverchargeMaxScale > 1f ? d.kiOverchargeMaxScale : 3f;
        vanishMaxRange = d.vanishMaxRange > 0 ? d.vanishMaxRange : 7.0;
        chaseMaxRange = d.chaseMaxRange > 0 ? d.chaseMaxRange : 14.0;
        backstepMaxRange = d.backstepMaxRange > 0 ? d.backstepMaxRange : 10.0;
        chargeAttackRange = d.chargeAttackRange > 0 ? d.chargeAttackRange : 5.0;
        dragonDashRange = d.dragonDashRange > 0 ? d.dragonDashRange : 16.0;
        vanishKiCost = Math.max(0f, d.vanishKiCost);
        chaseKiCost = Math.max(0f, d.chaseKiCost);
        backstepKiCost = Math.max(0f, d.backstepKiCost);
        comboKiCost = Math.max(0f, d.comboKiCost);
        finisherKiCost = Math.max(0f, d.finisherKiCost);
        dragonDashKiCost = Math.max(0f, d.dragonDashKiCost);

        // Prefer new split stamina fields; fall back to legacy chargeStaminaCost
        float legacyCharge = d.chargeStaminaCost > 0f ? d.chargeStaminaCost : 18f;
        fistChargeStaminaCost = Math.max(0f, d.fistChargeStaminaCost > 0f ? d.fistChargeStaminaCost : legacyCharge);
        kickChargeStaminaCost = Math.max(0f, d.kickChargeStaminaCost > 0f ? d.kickChargeStaminaCost : legacyCharge);
        chargeStaminaCost = Math.max(0f, legacyCharge);
        chargeHoldStaminaPerSec = Math.max(0f, d.chargeHoldStaminaPerSec);
        dragonDashStaminaCost = Math.max(0f, d.dragonDashStaminaCost);
        kickVerticalExtraStamina = Math.max(0f, d.kickVerticalExtraStamina);

        comboDamageScale = d.comboDamageScale > 0 ? d.comboDamageScale : 1f;
        finisherDamageScale = d.finisherDamageScale > 0 ? d.finisherDamageScale : 1.35f;
        chargeDamageScale = d.chargeDamageScale > 0 ? d.chargeDamageScale : 1.8f;
        kickDamageScale = d.kickDamageScale > 0 ? d.kickDamageScale : 1.15f;
        kickUpLaunch = d.kickUpLaunch > 0 ? d.kickUpLaunch : 1.35f;
        kickDownLaunch = d.kickDownLaunch > 0 ? d.kickDownLaunch : 1.15f;
        kickDownRangeBonus = Math.max(0f, d.kickDownRangeBonus);
        maxComboSteps = Math.max(1, Math.min(8, d.maxComboSteps <= 0 ? 5 : d.maxComboSteps));
        chargeMaxTicks = Math.max(10, Math.min(80, d.chargeMaxTicks <= 0 ? 28 : d.chargeMaxTicks));

        yawpKiGriefingEnabled = d.yawpKiGriefingEnabled;
        // A missing list means "config written before this option existed" - keep the defaults.
        // An explicitly empty list is honoured: it disables that half of the check.
        if (d.yawpPlayerKiFlags != null) {
            yawpPlayerKiFlags = new java.util.ArrayList<>(d.yawpPlayerKiFlags);
        }
        if (d.yawpMobKiFlags != null) {
            yawpMobKiFlags = new java.util.ArrayList<>(d.yawpMobKiFlags);
        }
        if (d.masterKiGriefRadius != null) {
            masterKiGriefRadius = Math.max(0.0, Math.min(256.0, d.masterKiGriefRadius));
        }
    }

    public static float fistReleaseStamina(float charge01) {
        float c = Math.max(0f, Math.min(1f, charge01));
        return fistChargeStaminaCost * (0.45f + 0.55f * c);
    }

    public static float kickReleaseStamina(float charge01, int vertical) {
        float c = Math.max(0f, Math.min(1f, charge01));
        float base = kickChargeStaminaCost * (0.45f + 0.55f * c);
        if (vertical != 0) base += kickVerticalExtraStamina * (0.5f + 0.5f * c);
        return base;
    }

    /**
     * Overcharge scale for a given power-release %. Returns 1.0 at/below threshold.
     * Uses {@code size} growth curve as the base excess factor; callers apply their own per-% rates.
     */
    public static float kiOverchargeExcessPercent(int powerRelease) {
        if (!kiOverchargeEnabled) return 0f;
        int excess = powerRelease - kiOverchargeThreshold;
        return Math.max(0f, excess);
    }

    public static float kiOverchargeSizeScale(int powerRelease) {
        float excess = kiOverchargeExcessPercent(powerRelease);
        if (excess <= 0f) return 1f;
        float scale = 1f + excess * kiOverchargeSizePerPercent * kiOverchargeMultiplier;
        return Math.min(kiOverchargeMaxScale, scale);
    }

    public static float kiOverchargeDamageScale(int powerRelease) {
        float excess = kiOverchargeExcessPercent(powerRelease);
        if (excess <= 0f) return 1f;
        float scale = 1f + excess * kiOverchargeDamagePerPercent * kiOverchargeMultiplier;
        return Math.min(kiOverchargeMaxScale, scale);
    }

    public static float kiOverchargeExplosionScale(int powerRelease) {
        float excess = kiOverchargeExcessPercent(powerRelease);
        if (excess <= 0f) return 1f;
        float scale = 1f + excess * kiOverchargeExplosionPerPercent * kiOverchargeMultiplier;
        return Math.min(kiOverchargeMaxScale, scale);
    }

    /** Wire legacy DMZ HUD config field for older code paths. */
    public static boolean isDmzHudEnabled() {
        return dmzHudEnabled;
    }

    public static void setDmzHudEnabled(boolean enabled) {
        dmzHudEnabled = enabled;
        save();
    }

    public static float clampFormStatMultiplier(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 1.0f;
        return Math.max(FORM_STAT_MULT_MIN, Math.min(FORM_STAT_MULT_MAX, value));
    }

    /** Applies global + per-form + per-stat scale maps from a config/sync payload. */
    public static void applyFormScaleMaps(Data d) {
        if (d == null) return;
        if (d.formStatMultiplier != null) {
            formStatMultiplier = clampFormStatMultiplier(d.formStatMultiplier);
        }
        formPerFormMultipliers.clear();
        if (d.formPerFormMultipliers != null) {
            for (Map.Entry<String, Float> e : d.formPerFormMultipliers.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) continue;
                formPerFormMultipliers.put(normalizeFormKey(e.getKey()), clampFormStatMultiplier(e.getValue()));
            }
        }
        formPerStatMultipliers.clear();
        if (d.formPerStatMultipliers != null) {
            for (Map.Entry<String, Float> e : d.formPerStatMultipliers.entrySet()) {
                String stat = normalizeStatKey(e.getKey());
                if (stat.isEmpty() || e.getValue() == null) continue;
                formPerStatMultipliers.put(stat, clampFormStatMultiplier(e.getValue()));
            }
        }
        formPerFormStatMultipliers.clear();
        if (d.formPerFormStatMultipliers != null) {
            for (Map.Entry<String, Map<String, Float>> e : d.formPerFormStatMultipliers.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) continue;
                String form = normalizeFormKey(e.getKey());
                Map<String, Float> inner = new ConcurrentHashMap<>();
                for (Map.Entry<String, Float> s : e.getValue().entrySet()) {
                    String stat = normalizeStatKey(s.getKey());
                    if (stat.isEmpty() || s.getValue() == null) continue;
                    inner.put(stat, clampFormStatMultiplier(s.getValue()));
                }
                if (!inner.isEmpty()) {
                    formPerFormStatMultipliers.put(form, inner);
                }
            }
        }
    }

    public static String normalizeFormKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    /** Normalizes DMZ stat tokens: STR/pwr/power/strength → str, pwr, def, … */
    public static String normalizeStatKey(String stat) {
        if (stat == null) return "";
        String s = stat.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "strength", "melee" -> "str";
            case "power", "ki", "energy_pwr" -> "pwr";
            case "defense", "defence" -> "def";
            case "stamina", "stam" -> "stm";
            case "vitality", "hp", "health" -> "vit";
            case "energy", "ki_pool" -> "ene";
            case "strike", "skill" -> "skp";
            case "spd", "move", "flight" -> "speed";
            default -> s;
        };
    }

    public static boolean isKnownFormStat(String stat) {
        String s = normalizeStatKey(stat);
        for (String k : FORM_STAT_KEYS) {
            if (k.equals(s)) return true;
        }
        return false;
    }

    public static void setFormStatMultiplier(float value) {
        formStatMultiplier = clampFormStatMultiplier(value);
        save();
    }

    /** Set or replace per-form overall scale. Key should be {@code group.form} or short form id. */
    public static void setPerFormMultiplier(String formKey, float value) {
        String k = normalizeFormKey(formKey);
        if (k.isEmpty() || "global".equals(k) || "*".equals(k)) {
            setFormStatMultiplier(value);
            return;
        }
        formPerFormMultipliers.put(k, clampFormStatMultiplier(value));
        save();
    }

    /** Global scale for one combat stat (all forms). */
    public static void setPerStatMultiplier(String stat, float value) {
        String s = normalizeStatKey(stat);
        if (!isKnownFormStat(s)) return;
        formPerStatMultipliers.put(s, clampFormStatMultiplier(value));
        save();
    }

    /** Scale one stat for one form only. */
    public static void setPerFormStatMultiplier(String formKey, String stat, float value) {
        String f = normalizeFormKey(formKey);
        String s = normalizeStatKey(stat);
        if (f.isEmpty() || !isKnownFormStat(s)) return;
        if ("global".equals(f) || "*".equals(f)) {
            setPerStatMultiplier(s, value);
            return;
        }
        formPerFormStatMultipliers
                .computeIfAbsent(f, k -> new ConcurrentHashMap<>())
                .put(s, clampFormStatMultiplier(value));
        save();
    }

    /** Remove a per-form overall override (falls back to global). */
    public static boolean clearPerFormMultiplier(String formKey) {
        String k = normalizeFormKey(formKey);
        if (k.isEmpty()) return false;
        boolean removed = formPerFormMultipliers.remove(k) != null;
        if (removed) save();
        return removed;
    }

    public static boolean clearPerStatMultiplier(String stat) {
        String s = normalizeStatKey(stat);
        boolean removed = formPerStatMultipliers.remove(s) != null;
        if (removed) save();
        return removed;
    }

    public static boolean clearPerFormStatMultiplier(String formKey, String stat) {
        String f = normalizeFormKey(formKey);
        String s = normalizeStatKey(stat);
        if ("global".equals(f) || "*".equals(f)) {
            return clearPerStatMultiplier(s);
        }
        Map<String, Float> inner = formPerFormStatMultipliers.get(f);
        if (inner == null) return false;
        boolean removed = inner.remove(s) != null;
        if (inner.isEmpty()) formPerFormStatMultipliers.remove(f);
        if (removed) save();
        return removed;
    }

    public static void clearAllPerFormMultipliers() {
        boolean any = !formPerFormMultipliers.isEmpty()
                || !formPerStatMultipliers.isEmpty()
                || !formPerFormStatMultipliers.isEmpty();
        formPerFormMultipliers.clear();
        formPerStatMultipliers.clear();
        formPerFormStatMultipliers.clear();
        if (any) save();
    }

    private static Float lookupPerFormMap(Map<String, Float> map, String formKey) {
        if (map == null || map.isEmpty() || formKey == null || formKey.isBlank()) return null;
        String k = normalizeFormKey(formKey);
        Float exact = map.get(k);
        if (exact != null) return exact;
        int dot = k.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < k.length()) {
            Float shortMatch = map.get(k.substring(dot + 1));
            if (shortMatch != null) return shortMatch;
        }
        for (Map.Entry<String, Float> e : map.entrySet()) {
            String stored = e.getKey();
            if (stored.equals(k) || stored.endsWith("." + k)) return e.getValue();
        }
        return null;
    }

    private static Map<String, Float> lookupPerFormStatMap(String formKey) {
        if (formKey == null || formKey.isBlank() || formPerFormStatMultipliers.isEmpty()) return null;
        String k = normalizeFormKey(formKey);
        Map<String, Float> exact = formPerFormStatMultipliers.get(k);
        if (exact != null) return exact;
        int dot = k.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < k.length()) {
            Map<String, Float> shortMatch = formPerFormStatMultipliers.get(k.substring(dot + 1));
            if (shortMatch != null) return shortMatch;
        }
        for (Map.Entry<String, Map<String, Float>> e : formPerFormStatMultipliers.entrySet()) {
            String stored = e.getKey();
            if (stored.equals(k) || stored.endsWith("." + k)) return e.getValue();
        }
        return null;
    }

    /**
     * Overall form scale (no per-stat): exact per-form → short-id → global.
     */
    public static float formScaleFor(String formKey) {
        if (formKey == null || formKey.isBlank()) {
            return formStatMultiplier;
        }
        Float override = lookupPerFormMap(formPerFormMultipliers, formKey);
        return override != null ? override : formStatMultiplier;
    }

    /**
     * Combined scale for form + combat stat.
     * {@code overall × globalStat × formStat} (missing pieces default to 1.0 for the last two;
     * overall falls back to {@link #formStatMultiplier}).
     */
    public static float formScaleFor(String formKey, String stat) {
        float overall = formScaleFor(formKey);
        float statScale = 1.0f;
        String s = normalizeStatKey(stat);
        if (!s.isEmpty()) {
            Float globalStat = formPerStatMultipliers.get(s);
            if (globalStat != null) statScale *= globalStat;
            Map<String, Float> formStats = lookupPerFormStatMap(formKey);
            if (formStats != null) {
                Float formStat = formStats.get(s);
                if (formStat != null) statScale *= formStat;
            }
        }
        return overall * statScale;
    }

    public static Map<String, Float> perFormMultipliersView() {
        return Collections.unmodifiableMap(formPerFormMultipliers);
    }

    public static Map<String, Float> perStatMultipliersView() {
        return Collections.unmodifiableMap(formPerStatMultipliers);
    }

    public static Map<String, Map<String, Float>> perFormStatMultipliersView() {
        return Collections.unmodifiableMap(formPerFormStatMultipliers);
    }

    /**
     * Applies server form scale to a DMZ form (or stack-form) stat mult.
     * Base form ({@code 1.0}) is unchanged; only the bonus above 1 is scaled.
     */
    public static double scaleFormMultiplier(double formMult) {
        return scaleFormMultiplier(formMult, null, null);
    }

    public static double scaleFormMultiplier(double formMult, String formKey) {
        return scaleFormMultiplier(formMult, formKey, null);
    }

    /**
     * @param formKey active form id ({@code group.form}) or null
     * @param stat    DMZ stat token (STR/PWR/…) or null for overall form scale only
     */
    public static double scaleFormMultiplier(double formMult, String formKey, String stat) {
        float m = formScaleFor(formKey, stat);
        if (Math.abs(m - 1.0f) < 1.0e-6f) return formMult;
        return 1.0 + (formMult - 1.0) * (double) m;
    }

    public static class Data {
        public boolean dmzHudEnabled = false;
        public boolean dmzContentBootstrap = true;
        public boolean bt3CombatEnabled = true;
        public boolean bt3ComboEnabled = true;
        public boolean bt3VanishEnabled = true;
        public boolean bt3ChaseDashEnabled = true;
        public boolean bt3BackstepEnabled = true;
        public boolean bt3FinisherEnabled = true;
        public boolean bt3ChargeAttackEnabled = true;
        public boolean bt3DragonDashEnabled = true;
        public boolean bt3GuardEnabled = true;
        public boolean bt3SuperCounterEnabled = true;
        public boolean bt3KiBlastCancelEnabled = true;
        public boolean bt3ZBurstEnabled = true;
        public boolean bt3LockCycleEnabled = true;
        public boolean bt3ComboPunchesOnly = true;
        public boolean protectDmzMasters = true;
        public boolean bt3RushChainEnabled = true;
        public boolean bt3SonicSwayEnabled = true;
        public boolean bt3UltimateEnabled = true;
        public boolean bt3SparkingEnabled = true;
        public boolean bt3TransformImpactEnabled = true;
        public boolean trainingDummyEnabled = true;
        public boolean parallelQuestEnabled = true;
        public boolean mentorEnabled = true;
        public boolean copycatForgeEnergyEnabled = false;
        public int copycatEnergyCapacity = 100_000;
        public int copycatMaxReceiveFePerTick = 1_000;
        public int copycatEnergyUseFePerTick = 10;
        public double missileMaxApexY = 950.0;
        public boolean missileTerminalGravityCompensation = false;
        public boolean thrusterImpulseGuardEnabled = true;
        public double thrusterMaxImpulse = 50_000.0;
        public boolean vanishShadeEnabled = true;
        public double vanishShadeDensity = 1.0;
        public double vanishThunderVolume = 0.35;
        public String vanishSoundOut = "xenopixelsmod:vanish";
        public String vanishSoundIn = "xenopixelsmod:vanish";
        public float rushChainKiCost = 10.0f;
        public float rushChainDamageScale = 0.9f;
        public double rushChainRange = 16.0;
        public float sonicSwayStaminaCost = 6.0f;
        public int sonicSwayIFramesTicks = 8;
        public int sonicSwayCooldownTicks = 18;
        public float ultimateKiCost = 35.0f;
        public float ultimateDamageScale = 2.4f;
        public int ultimateCooldownTicks = 200;
        public float sparkingBuildPerHit = 6.0f;
        public float sparkingBuildOnHurt = 3.0f;
        public int sparkingDurationTicks = 100;
        public float sparkingDamageMult = 1.35f;
        public float transformImpactRadius = 3.5f;
        public float transformImpactKnock = 0.45f;
        public float chaseSuccessChance = 0.50f;
        public float guardDamageReduction = 0.55f;
        public float guardStaminaPerHit = 8.0f;
        public float guardStaminaPerSec = 3.0f;
        public int guardBreakStunTicks = 25;
        public int superCounterWindowTicks = 12;
        public float superCounterKiCost = 10.0f;
        public float superCounterDamageScale = 1.35f;
        public float kiBlastCancelKiCost = 6.0f;
        public float kiBlastCancelDamageScale = 0.85f;
        public float zBurstKiCost = 8.0f;
        public float zBurstDamageScale = 0.75f;
        public double zBurstRange = 8.0;
        /**
         * Global form bonus scale (see {@link XenoServerConfig#formStatMultiplier}).
         * Boxed so older JSON without the key stays {@code null} (use default 1.0).
         */
        public Float formStatMultiplier = 1.0f;
        /** Per-form overall overrides (key → scale). Null/empty = none. */
        public Map<String, Float> formPerFormMultipliers = new LinkedHashMap<>();
        /** Global per-stat overrides (str/pwr/def/…). */
        public Map<String, Float> formPerStatMultipliers = new LinkedHashMap<>();
        /** Per-form per-stat overrides. */
        public Map<String, Map<String, Float>> formPerFormStatMultipliers = new LinkedHashMap<>();
        public boolean kiOverchargeEnabled = true;
        public int kiOverchargeThreshold = 175;
        public float kiOverchargeSizePerPercent = 0.012f;
        public float kiOverchargeDamagePerPercent = 0.015f;
        public float kiOverchargeExplosionPerPercent = 0.014f;
        public float kiOverchargeMultiplier = 1.0f;
        public float kiOverchargeMaxScale = 3.0f;
        public double vanishMaxRange = 7.0;
        public double chaseMaxRange = 14.0;
        public double backstepMaxRange = 10.0;
        public double chargeAttackRange = 5.0;
        public double dragonDashRange = 16.0;
        public float vanishKiCost = 8.0f;
        public float chaseKiCost = 12.0f;
        public float backstepKiCost = 6.0f;
        public float comboKiCost = 1.5f;
        public float finisherKiCost = 5.0f;
        public float dragonDashKiCost = 10.0f;
        public float fistChargeStaminaCost = 18.0f;
        public float kickChargeStaminaCost = 18.0f;
        public float chargeStaminaCost = 18.0f;
        public float chargeHoldStaminaPerSec = 4.0f;
        public float dragonDashStaminaCost = 22.0f;
        public float kickVerticalExtraStamina = 4.0f;
        public float comboDamageScale = 1.0f;
        public float finisherDamageScale = 1.35f;
        public float chargeDamageScale = 1.8f;
        public float kickDamageScale = 1.15f;
        public float kickUpLaunch = 1.35f;
        public float kickDownLaunch = 1.15f;
        public float kickDownRangeBonus = 4.0f;
        public int maxComboSteps = 5;
        public int chargeMaxTicks = 28;
        public boolean yawpKiGriefingEnabled = true;
        // Null (absent from an older config file) means "use the defaults"; see apply().
        public java.util.List<String> yawpPlayerKiFlags = null;
        public java.util.List<String> yawpMobKiFlags = null;
        /** Boxed so an absent key keeps the default instead of resetting to 0. */
        public Double masterKiGriefRadius = null;
    }
}
