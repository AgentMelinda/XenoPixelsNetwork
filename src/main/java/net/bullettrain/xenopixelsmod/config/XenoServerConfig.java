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
    /**
     * Resolved on demand rather than in a static field.
     *
     * <p>{@code FMLPaths.CONFIGDIR} is only populated by a running FML, so resolving it at class
     * load made merely <em>reading</em> a tuning value from this class impossible outside the game.
     * Combat maths that reads config — {@code BeamSurgeState} — is unit-tested directly, and would
     * otherwise die in the static initialiser before a single assertion ran.
     */
    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-server.json");
    }
    /**
     * Bumped to 4 for the beam-surge curve keys.
     *
     * <p>New fields deserialize to their {@code Data} defaults in an older file, so behaviour is
     * safe without a bump — but the file is never rewritten, so the keys stay invisible and nobody
     * can discover or tune them. A bump is what gets them written out.
     */
    private static final int CURRENT_CONFIG_VERSION = 14;

    // --- HUD / DMZ ---
    /** When false, clients block DMZ vanilla HUD overlays. */
    public static boolean dmzHudEnabled = false;
    /** Install/patch DMZ form JSON + skill offerings on boot. */
    public static boolean dmzContentBootstrap = true;
    /** Recover quest-spawned DMZ saga enemies when the stock spawn silently fails. */
    public static boolean dmzSagaSpawnCompat = true;
    /**
     * CustomNPCs {@code npc.say()} / {@code saySurrounding}. Off mutes chat bubbles.
     * Also silences {@code executeCommand} admin/OP feedback from NPC scripts.
     */
    public static boolean npcSayEnabled = true;

    /**
     * Lets NPC-run commands work on a server that has command blocks switched off.
     *
     * <p>My NPCs and CustomNPCs both refuse to run any NPC command at all when
     * {@code enable-command-block=false} — {@code EspiUtilServer.runCommand} returns
     * "Cant run commands if CommandBlocks are disabled" before it has even substituted {@code @dp},
     * so a quest reward that awards points simply does nothing. This makes that one check pass for
     * the NPC command path, and only for it: real command blocks stay exactly as disabled as the
     * server owner set them.
     *
     * <p><b>Off by default, deliberately.</b> It overrides a setting somebody chose, and My NPCs
     * runs NPC commands at permission level 2 — or level 4 when its own {@code NpcUseOpCommands} is
     * enabled. An NPC running op-level commands on a server that turned command blocks off is the
     * operator's call, so they have to make it.
     *
     * <p>The alternative, with no mod involved, is {@code enable-command-block=true} in
     * {@code server.properties}.
     */
    public static boolean npcCommandsIgnoreCommandBlockSetting = false;

    // --- Combat master switches ---
    public static boolean bt3CombatEnabled = true;
    public static boolean bt3ComboEnabled = true;
    public static boolean bt3CinematicRushEnabled = true;
    public static boolean bt3VanishEnabled = true;
    public static boolean bt3ChaseDashEnabled = true;
    /** When true, chase dash flies the player to the target over several ticks instead of teleporting. */
    public static boolean chaseFlightEnabled = true;
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
     * DMZ Z-lock acquire + persist ignore block occlusion. Synced to clients.
     * Each client still has {@code XenoClientConfig.lockOnThroughBlocks} as a local opt-out.
     */
    public static boolean lockOnThroughBlocks = true;

    /**
     * A ki disk vanishes once it has landed its full hit budget.
     *
     * <p>DragonMineZ splits a disk's damage across {@code getMaxHits()} pulses and never removes
     * it on contact, so a Kienzan that has already delivered everything it had keeps sitting in
     * the target, dealing nothing and looking like it failed to despawn. Once the budget is
     * spent the attack is finished by DMZ's own accounting, so this simply ends it there.
     */
    public static boolean kiDiskDespawnOnHitBudget = true;
    /**
     * Combo string uses punch/uppercut anims only (no mixed DMZ kicks).
     * Still triggered by normal attack mash.
     */
    public static boolean bt3ComboPunchesOnly = true;
    /** Prevent players from damaging / knocking DMZ master NPCs. */
    public static boolean protectDmzMasters = true;

    /**
     * Stops XenoPixels combat from launching DragonMineZ masters.
     *
     * <p>Separate from {@link #protectDmzMasters} on purpose: that one governs whether masters can
     * be attacked and damaged at all, while this governs only the impulses our own moves apply
     * directly. A server that wants masters hittable but not launched out of their training spot
     * needs both switches, not one.
     */
    public static boolean protectMastersFromCombatKnockback = true;

    /**
     * Copy a world's CustomNPCs data across to My NPCs when the server starts.
     *
     * <p>On by default because the alternative is silent loss: without CustomNPCs installed, vanilla
     * cannot resolve an in-world NPC's entity id and discards it. Only ever reads the CustomNPCs
     * folder and never overwrites a file My NPCs already has, so it is safe to leave on.
     */
    public static boolean migrateCustomNpcsWorldData = true;
    /** Make profiled NPCs use only their DMZ/Xeno combat profile, not stacked native NPC stats. */
    public static boolean npcDmzStatsAuthoritative = true;
    /** Script tick cadence for both supported NPC mods. One runs scripted tick hooks every tick. */
    public static int npcScriptTickInterval = 1;
    /** Air chase / rush chain after knockup. */
    public static boolean bt3RushChainEnabled = true;
    /** Sonic sway side-step with brief i-frames. */
    public static boolean bt3SonicSwayEnabled = true;
    /** Ultimate skill slot (big KI smash). */
    public static boolean bt3UltimateEnabled = true;
    /** Sparking / limit-style meter. */
    public static boolean bt3SparkingEnabled = true;
    /** Hakai erasure technique (Ctrl + left click). */
    public static boolean hakaiEnabled = true;
    /**
     * Outline the Hakai target for the length of the channel. Vanilla entity glow, so it reads
     * through walls; the colour is the target's scoreboard team colour, or white when it has none.
     */
    public static boolean hakaiTargetGlow = true;
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
     * <p>Wing panels used to share this cap, back when a control surface rotated the hull through a
     * fabricated impulse. They no longer apply impulses at all: a deflected panel now changes the
     * normal Sable's own lift pass reads, and the resulting force is bounded by the aerodynamic
     * model itself (see {@code WingPanelBlock.sable$contributeLiftAndDrag}).
     *
     * <p>Normal full power is {@code maxForce × 1.0 × step} — with the current 3,000 default
     * force (corrected down from a previous 1,200,000 that was producing ship-destroying
     * impulses against this project's real block masses — see
     * {@link net.bullettrain.xenopixelsmod.block.entity.ShipThrusterBlockEntity#DEFAULT_MAX_FORCE}'s
     * own javadoc) and a 60 Hz step that is about 50, so this default leaves headroom for tuning
     * while still catching a genuine runaway, in the same proportion the old 50,000 cap left over
     * the old force default.
     */
    public static double thrusterMaxImpulse = 6_000.0;

    // --- Manual-flight velocity ceiling ---
    /**
     * Keyboard-mode flight (see {@code AeroFlightCore.tick}'s own comment on why
     * {@code AeroStabilizerSystem} is deliberately disabled there) has no steering assist and,
     * before this cap existed, no ceiling at all on the ship's cumulative speed — aerodynamic
     * forces bound what any single tick contributes, not what holding a key for a long time adds up
     * to. Enforced by {@code AeroControlSurfaceTorque}. Matches {@code AeroFlightDirector.MAX_SPEED},
     * the autopilot's own equivalent ceiling, so manual and autopilot flight agree on "too fast."
     */
    public static double maxFlightSpeed = 28.0;
    /** Same idea as {@link #maxFlightSpeed} but for spin rate, in rad/s — keyboard-mode yaw in
     * particular has no natural aerodynamic damping ({@code AeroAeroModel} models no yaw/sideslip
     * term), so nothing else stops an undamped spin from climbing without bound while a role
     * panel stays deflected. */
    public static double maxFlightAngularVelocity = 2.0;

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
    /** Ki a Xeno rush strike costs. DMZ derives strike cost from the caster's own damage,
     * which reached thousands of ki at high power and made rush unusable. */
    public static double rushKiCost = 25.0;
    /** Cooldown in ticks for a Xeno rush strike, replacing DMZ's per-id config lookup. */
    public static int rushCooldownTicks = 40;
    /**
     * Grant the four Xeno rush strikes to every player on login.
     *
     * <p>On by default, and it has to be: these strikes are injected into DMZ's predefined strike
     * registry rather than declared by it, so no DMZ progression path can ever unlock them. Turn
     * this off and they cannot be equipped at all, which removes the moves rather than gating
     * them. Players who already have them keep them - the unlock was written to their saved stats.
     */
    public static boolean rushAutoUnlock = true;
    /**
     * Legacy serialized setting retained for config compatibility. Rush techniques are never
     * auto-equipped because an empty slot may represent an explicit player unbind.
     */
    public static boolean rushAutoEquipSlots = false;
    /** Zanzoken: the afterimage dodge. */
    public static boolean zanzokenEnabled = true;
    /** Ki spent on the press, whether or not the dodge lands. */
    public static float zanzokenKiCost = 20.0f;
    /** How long a press stays live. Short on purpose: this is a read, not a stance. */
    public static int zanzokenWindowTicks = 8;
    /** Invulnerability granted after a successful dodge, so a combo cannot re-hit. */
    public static int zanzokenIFramesTicks = 10;
    public static int zanzokenCooldownTicks = 40;
    /** True renders a copy of the fighter with their DMZ appearance; false uses the dust silhouette. */
    public static boolean zanzokenGhostAfterimage = true;
    /** 1=semi-transparent fade, 2=solid fade, 3=constant semi-transparent then vanish. */
    public static int zanzokenGhostFadeMode = 2;
    /** Starting alpha for fade mode 1 and constant alpha for mode 3. */
    public static float zanzokenGhostAlpha = 0.55f;
    /** Copies in the ring Zanzoken throws around the attacker. */
    public static int zanzokenRingClones = 6;
    public static double zanzokenRingRadius = 3.0;
    /** How long a Zanzoken ring stands. Long enough that an opponent must guess and commit;
     * striking any image disperses the rest early. */
    public static int zanzokenRingTicks = 200;
    /** Shi Shin No Ken: bodies the fighter divides into, counting their own. */
    public static boolean multiFormEnabled = true;
    public static int multiFormBodies = 4;
    public static float multiFormKiCost = 60.0f;
    public static float sonicSwayStaminaCost = 6.0f;
    public static int sonicSwayIFramesTicks = 8;
    public static int sonicSwayCooldownTicks = 18;
    public static float ultimateKiCost = 35.0f;
    public static float ultimateDamageScale = 2.4f;
    public static int ultimateCooldownTicks = 200;
    /** Ki cost to start a Hakai channel. Above Ultimate's, being the more extreme move. */
    public static float hakaiKiCost = 60.0f;
    /** Max distance from caster to target to start (and keep) a Hakai channel. */
    public static double hakaiMaxRange = 15.0;
    /** Hakai cooldown, in ticks, after a channel starts (success or cancel). */
    public static int hakaiCooldownTicks = 600;
    /** Ticks the Hakai channel takes to complete once started (~2s at 20 TPS). */
    public static int hakaiChannelTicks = 40;
    /**
     * Damage a caster may absorb across one Hakai channel, as a fraction of their max health,
     * before it breaks. Any hit at all used to cancel it, which meant Hakai could never be
     * landed on anything that fights back -- a mob's first chip hit ended the channel. 0
     * restores that strict behaviour; 1 makes the channel uninterruptible by damage.
     */
    public static float hakaiPoiseFraction = 0.35f;
    /**
     * How far the caster may drift from where they started before the channel breaks, in blocks.
     * Knockback from being hit mid-channel counts against this too, so it has to be wider than
     * a single knockback impulse or the damage allowance above is meaningless.
     */
    public static double hakaiMoveInterruptDistance = 3.0;
    public static float sparkingBuildPerHit = 6.0f;
    public static float sparkingBuildOnHurt = 3.0f;
    /**
     * How long a full ki bar of Sparking lasts. This also sets the drain speed: the bar is emptied
     * over exactly this many ticks, so spending ki on techniques shortens Sparking too.
     */
    public static int sparkingDurationTicks = 200;
    /** Full-ki charge time before Sparking activates. Default 100 ticks = five seconds. */
    public static int sparkingChargeTicks = 100;
    public static float sparkingDamageMult = 1.35f;
    /**
     * Sparking is entered by charging the ki bar to full rather than by filling a hit-built meter,
     * the way Budokai Tenkaichi 3 does it. Switch off to go back to the meter.
     */
    public static boolean sparkingFromKiCharge = true;
    /**
     * Release limit while Sparking is up, in percent.
     *
     * <p>DragonMineZ normally caps a player at {@code 50 + potentialunlock_level * 5}, which tops
     * out at 115. Sparking lifts the ceiling; DMZ's own tick handler then ramps power release
     * toward it, and the player's real limit is restored when Sparking ends.
     */
    /**
     * Register Hakai, Zanzoken and Shi Shin No Ken as DragonMineZ technique slot entries.
     *
     * <p>Off by default. They are reached from their own keys and gamepad chords, which work; the
     * slot route is kept for a later look rather than removed, but it does not clutter DMZ's
     * technique list or intercept strikes unless it is deliberately switched on.
     */
    /**
     * Zanzoken's afterimages also fool AI, not just other players.
     *
     * <p>Blocks a mob from acquiring the fighter while their images are up; anything already
     * fighting them keeps its target, so this cannot be used to shed a fight.
     */
    public static boolean zanzokenConfusesAi = true;
    /** How long the afterimages stand in for the fighter, for both onlookers and AI. */
    public static int zanzokenAfterimageTicks = 40;

    /**
     * XenoPixels' own ceiling on every DragonMineZ stat, or {@link XenoStatCeiling#OFF} to leave
     * DragonMineZ's configured maximum alone.
     *
     * <p>Off by default: a server that has not asked for this keeps exactly the caps DragonMineZ
     * gives it. {@code /xenostats limit} is what turns it on.
     *
     * @see net.bullettrain.xenopixelsmod.combat.XenoStatCeiling
     */
    public static int statMaxOverride = net.bullettrain.xenopixelsmod.combat.XenoStatCeiling.OFF;

    public static boolean xenoSlotTechniquesEnabled = false;

    public static int sparkingReleaseLimit = 225;
    /** Ticks after Sparking ends before it can be entered again. */
    public static int sparkingCooldownTicks = 200;
    /** Movement speed multiplier while Sparking is up. */
    public static float sparkingMoveSpeedMult = 1.25f;
    /** Attack speed multiplier while Sparking is up. */
    public static float sparkingAttackSpeedMult = 1.35f;
    /** Sparking ends once energy falls to this fraction of the maximum. */
    public static float sparkingEndEnergyFraction = 0.02f;
    public static float transformImpactRadius = 3.5f;
    public static float transformImpactKnock = 0.45f;
    /**
     * Chance (0..1) that chase dash / dragon-dash chase phase succeeds.
     * Default 1.0 (reliable). Explicit lower probabilities retain pay-on-attempt behavior.
     */
    public static float chaseSuccessChance = 1.0f;

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
    /** Repeat basic ki blasts while the secondary-function + Use chord remains held. */
    public static boolean kiBlastHoldToFire = true;
    /** Server-authoritative interval and cooldown for basic ki blasts. */
    public static int kiBlastCooldownTicks = 32;
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
    /** Absolute synchronized limits used by DMZ technique validation and live projectiles. */
    public static float kiProjectileMaxSize = 320.0f;
    public static float kiProjectileMaxSpeed = 32.0f;
    /** Runtime speed growth per release point above the overcharge threshold. */
    public static float kiOverchargeSpeedPerPercent = 0.004f;
    /** Opt-in: let damage/explosion growth follow visual scale instead of the legacy scale cap. */
    public static boolean kiFullGameplayScaling = false;
    /** Independent caps for DMZ's synchronous cubic block scans. */
    public static float kiDestructionMaxRadius = 32.0f;
    public static int kiDestructionBlocksPerTick = 4096;

    // --- Balance ---
    public static double vanishMaxRange = 7.0;
    /** 0 = unlimited (BT3 chase after a launch). */
    public static double chaseMaxRange = 0.0;
    /** Chase-flight step distance per tick, in blocks. */
    public static double chaseFlightSpeed = 1.2;
    /** Chase-flight give-up window if the target is never reached. */
    public static int chaseFlightTimeoutTicks = 2400;
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
    /** Horizontal kick launch multiplier (mash + charged). */
    public static float kickKnockbackScale = 1.0f;
    /**
     * Every Nth hold-R mash beat is a charged-kick knockback. {@code 0} disables.
     * W-tap launcher still works.
     */
    public static int comboLaunchKickEvery = 5;
    /** Launcher Y — steep diagonal off the floor (mash W-tap and charged kick + W). */
    public static float comboLauncherUp = 1.85f;
    /** Launcher away — enough to read as diagonal, not a 90° pop-up. */
    public static float comboLauncherHoriz = 0.55f;
    /**
     * Ticks between beats of a held mash. Also the divisor every mash clip's playback speed is
     * derived from, so raising it slows the string and lets each swing read; lowering it speeds
     * the clips up to match. Must stay above the server's four-tick anti-spam floor, or beats land
     * on the rejection boundary and the client's prediction desyncs from the confirmation.
     */
    public static int comboMashIntervalTicks = 6;
    /**
     * Which authored generation of the combat clips to play: 1 original, 2 yaw-scaled twins,
     * 3 the first BT3 pass, and 4 the forward-centred BT3 rush set. All generations ship.
     */
    public static int comboAnimGeneration =
            net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog.GEN_DEFAULT;
    /** Blocks past the target for vanish (behind them). */
    public static double vanishGap = 1.35;
    /** Left/right vanish offset. */
    public static double vanishSide = 1.05;
    /**
     * Below this horizontal separation, a vanish measures "behind" from the target's own body
     * facing instead of from the line you approached on. That line is only a few centimetres long
     * at point-blank, so its direction is noise: it flips frame to frame, and the client and the
     * server - a hundred milliseconds apart on where both bodies are - can pick opposite sides.
     *
     * <p><b>Off by default.</b> It played worse than the plain approach line, so the original
     * behaviour is what ships; the near-field code stays here behind this number rather than being
     * torn out, so the two can be compared again without a rebuild. {@code 0} is the old
     * behaviour at every range, byte for byte - {@code Bt3VanishGeometryTest} pins that.
     */
    public static double vanishNearField = 0.0;
    /**
     * Move a vanish landing off anything solid instead of teleporting into it. Also off by
     * default: the search can decide a legal spot is blocked - {@code noCollision} counts entities,
     * and a vanish lands right next to a body - and then shuffle the fighter sideways or leave
     * them standing, which reads as the vanish misfiring. Kept switchable for the same reason.
     */
    public static boolean vanishOpenSpotSearch = false;
    /** Blocks short of the target where chase flight stops. 0 = on the target. */
    public static double chaseStopGap = 0.0;
    /**
     * Absolute Y for {@code /xenostructure place}. {@code 0} means use terrain height
     * plus {@link #dmzStructureYOffset}.
     */
    public static int dmzStructureY = 0;
    /** Added on top of terrain (or {@link #dmzStructureY}) when placing a DMZ structure. */
    public static int dmzStructureYOffset = 0;
    /** Summon the matching master NPC after {@code /xenostructure place}. */
    public static boolean dmzStructureMaster = true;
    public static int maxComboSteps = 5;
    /** Ticks to reach full charge (20 = 1s). */
    public static int chargeMaxTicks = 28;

    // --- YAWP (Yet Another World Protector) region protection ---
    /**
     * Let YAWP regions veto DMZ ki griefing. Only has an effect when YAWP is installed;
     * DMZ's allowKiGriefing* gamerules still apply first, this can only deny further.
     */
    public static boolean yawpKiGriefingEnabled = true;

    // --- quality-of-life overrides for DragonMineZ restrictions ---

    /**
     * Only pick items up while sneaking.
     *
     * <p>A DMZ fight against a mob group leaves far more drops than vanilla combat does, and
     * walking through them fills the hotbar with junk mid-fight. Requiring a deliberate crouch
     * makes pickup something the player asks for. Items are left on the ground untouched, so
     * their normal despawn timer still applies and nothing is destroyed by this.
     */
    public static boolean sneakToPickup = true;

    /**
     * Let techniques and ki attacks fire with something in the main hand.
     *
     * <p>DMZ gates every technique on an empty main hand, in five separate places across client
     * and server. That is a defensible design — it keeps ki and weapons as separate stances —
     * but on a server where players carry tools constantly it mostly reads as the ki button
     * silently doing nothing. Turning this off restores DMZ's stock behaviour exactly.
     */
    public static boolean allowKiWithItemInHand = true;

    /**
     * Punching an incoming ki blast sends it back. Answers the ranged-spam problem that guard
     * alone cannot: guard costs stamina and still takes chip damage, so a defender with no way
     * to return fire loses by attrition.
     */
    public static boolean kiDeflectEnabled = true;
    /** Reach in blocks. Punchable ki hitbox is this far from the player. */
    public static float kiDeflectReach = 3.0f;
    /**
     * How closely the player must be facing the blast, as a dot product. 0.2 is a wide cone
     * so a blast 2–3 blocks away is still punchable without a pixel-perfect aim.
     */
    public static float kiDeflectAimDot = 0.2f;
    /** Returned speed as a multiple of the incoming speed. Above 1 rewards the read. */
    public static float kiDeflectSpeedScale = 1.15f;
    /** Floor on returned speed, so a nearly-stalled blast still travels somewhere. */
    public static float kiDeflectMinSpeed = 0.8f;
    /**
     * Returned damage as a multiple of the incoming blast's. Paired with the stamina cost: you
     * spend something real to turn a shot around, and the shot you send back hits harder for it.
     */
    public static float kiDeflectDamageScale = 1.25f;
    /**
     * Stamina spent per deflect. Zero makes deflection free.
     *
     * <p>A cost rather than a pure cooldown, because a cooldown would make a rapid volley
     * undeflectable by construction — with a cost you can answer every shot in a burst for as long
     * as your stamina holds, which is the read worth rewarding.
     */
    public static float kiDeflectStaminaCost = 4.0f;
    /**
     * Minimum ticks between deflects. Small on purpose: this only exists so mashing attack cannot
     * auto-clear everything in reach, not to gate the volley itself. Zero disables it.
     */
    public static int kiDeflectCooldownTicks = 4;

    // --- sustained ki wave ("beam surge") ---

    /**
     * Hold the fire key to keep a ki wave alive and growing.
     *
     * <p>Off restores DMZ's stock behaviour, where a wave lives 80 ticks times its charge
     * multiplier and nothing can extend it.
     */
    public static boolean beamSurgeEnabled = true;
    /** Ki drained per tick of sustain, before the growth surcharge. */
    public static float beamSurgeKiPerTick = 1.6f;
    /** Stamina drained per tick of sustain, before the growth surcharge. */
    public static float beamSurgeStaminaPerTick = 0.5f;
    /** Extra cost at full surge, as a multiple. 1.0 means a maxed beam costs double. */
    public static float beamSurgeCostGrowth = 1.0f;
    /**
     * Beam thickness at full surge, as a fraction added to its baseline size.
     *
     * <p>This is the whole of what bounds a surged beam's thickness — {@link #kiProjectileMaxSize}
     * sits far above it and never binds. Final size is
     * {@code baseline * (1 + surge * beamSurgeSizeGain)}, and surge tops out at 1.0, so the largest
     * a beam can ever get is {@code baseline * (1 + beamSurgeSizeGain)}. At the old 1.2 a typical
     * baseline of 1.0 could not exceed 2.2 no matter how long it was held, which read as a hard
     * lock rather than a tuning value. To pick a value: {@code gain = target / baseline - 1}.
     */
    public static float beamSurgeSizeGain = 9.0f;
    /** Damage at full surge, as a fraction added to its baseline. */
    public static float beamSurgeDamageGain = 1.5f;
    /** Length growth per tick at full surge, as a fraction added to its baseline. */
    public static float beamSurgeReachGain = 0.8f;
    /** How far from the player to look for their own wave. A wave is anchored at its origin. */
    public static float beamSurgeSearchRadius = 30.0f;

    /**
     * The surge curve: how much growth a beam can reach, and how fast it gets there.
     *
     * <p>The ramp is asymptotic, so these are the ceiling it approaches rather than a hard stop.
     * A beam at zero mastery only receives {@link #beamSurgeCeiling} of the configured maxima no
     * matter how long it is held — raise it to let unpractised players reach full growth.
     */
    public static float beamSurgeCeiling = 0.45f;
    /** Extra ceiling per beam-mastery level. The total is still clamped to 1.0. */
    public static float beamSurgeCeilingPerMastery = 0.183f;
    /** Fraction of the remaining gap to the ceiling closed per tick at zero mastery. */
    public static float beamSurgeRampPerTick = 0.020f;
    /** Extra gap-closing per tick per beam-mastery level. */
    public static float beamSurgeRampPerMastery = 0.010f;
    /**
     * Hard ceiling on how long any ki wave or laser may grow, in blocks. Zero or less is uncapped.
     *
     * <p>Neither DragonMineZ nor the surge system caps total beam length — a wave adds its speed to
     * its length every tick for as long as it lives, so a sustained beam reaches as far as its
     * owner can pay for. This is the server's stop.
     */
    public static float beamSurgeMaxLength = 192.0f;

    /**
     * Extra fire ticks on a type-9 ki volley emitter at Volley Mastery 1 / 2 / 3.
     * Child pellets keep their own life. 0 disables that level's bonus.
     */
    public static int barrageExtraTicks1 = 20;
    public static int barrageExtraTicks2 = 45;
    public static int barrageExtraTicks3 = 80;
    /**
     * Base fire ticks for a barrage emitter. 0 keeps DMZ's {@code 50 × charge}.
     * Volley Mastery then adds {@link #barrageExtraTicks1}/{@code 2}/{@code 3}.
     */
    public static int barrageDurationTicks = 0;
    /**
     * Fire window for every ki type. 0 keeps {@code TechniqueDispatcher}'s
     * {@code base × charge}. Per-type values in {@link #kiDurationByType} win.
     */
    public static int kiDurationTicks = 0;
    public static final java.util.Map<String, Integer> kiDurationByType = new java.util.LinkedHashMap<>();
    /**
     * Base cooldown ticks after a barrage. 0 keeps the technique's own cooldown.
     * Volley Mastery then subtracts {@link #barrageCooldownReduce1}/{@code 2}/{@code 3}.
     */
    public static int barrageCooldownTicks = 0;
    public static int barrageCooldownReduce1 = 20;
    public static int barrageCooldownReduce2 = 40;
    public static int barrageCooldownReduce3 = 60;
    /**
     * Ki drained each tick while a lengthened volley is firing. 0 = no extra cost.
     * Running out discards the emitter the same way a beam that cannot pay stops surging.
     */
    public static float barrageKiPerTick = 0.4f;

    /**
     * How far your own ki may be and still accept Guidance. 0 uses
     * {@code KiGuidanceMath} defaults (192 + 48 × level).
     */
    public static int guidanceControlRange = 0;
    /**
     * Camera-steer turn override, 0..1. 0 keeps the skill curve
     * ({@code BASE_TURN + TURN_PER_LEVEL × level}).
     */
    public static float guidanceTurnRate = 0.0f;
    /** Look-ray velocity blend per tick. 0 keeps {@code CAMERA_VEL_RATE} (0.38). */
    public static float guidanceCameraRate = 0.38f;
    /** Extra ticks Guidance stays armed after the hold packet. 0 keeps 8. */
    public static int guidanceHoldGraceTicks = 8;
    /** Minimum look-ray travel in blocks. 0 keeps 8. */
    public static float guidanceLookRayMin = 8.0f;

    /**
     * The sparking aura: rising ki shell, ground debris and lightning arcs while sparking.
     *
     * <p>Sparking already changed how a fight works - damage multiplier, i-frames - with almost
     * no presence. A state that matters that much should be visible from across the arena.
     */
    public static boolean sparkingAuraEnabled = true;
    /** Aura particle density, 0 to 3. Sustained every tick, so this is the main cost knob. */
    public static float sparkingAuraDensity = 1.0f;
    /**
     * YAWP flags consulted for ki griefing caused by a player. Griefing is denied when any
     * listed flag is DENIED at the target block, evaluated with the player's region
     * permissions, so region owners/members are unaffected.
     * Names are YAWP flag ids as used by {@code /wp flag add}; unknown names are ignored
     * (with a warning) so a YAWP update that renames a flag cannot break ki combat.
     *
     * <p>Default is empty: claimed regions no longer silently starve non-owner ki just because
     * vanilla {@code break-blocks} is denied. Operators who want that mapping can put
     * {@code break-blocks} / {@code explosions-blocks} back in the JSON. An already-saved
     * config that listed those flags is left unchanged.
     */
    public static java.util.List<String> yawpPlayerKiFlags = new java.util.ArrayList<>();
    /** YAWP flags consulted for ki griefing caused by a mob or an unowned projectile. */
    public static java.util.List<String> yawpMobKiFlags = new java.util.ArrayList<>();
    /**
     * Radius in blocks around a DMZ master treated as protected by the
     * {@code ki-griefing-masters} region flag. 0 disables master-aware ki protection.
     *
     * <p>DMZ gates master-structure griefing behind its own gamerule but exposes no "is this
     * block part of a master's site" query, so proximity to the master entity is the available
     * approximation. Only scanned when that flag is actually set on the region.
     */
    public static double masterKiGriefRadius = 24.0;

    // --- charge overcharge (hold past DMZ's 175% cap) ---

    /**
     * Let eligible players keep charging a ki technique past DragonMineZ's 175% cap.
     *
     * <p>This is not {@link #kiOverchargeEnabled} (power-release scaling). Instant casts
     * stay at 100% — DMZ fires them itself.
     */
    public static boolean chargeOverchargeEnabled = true;
    /** Gameplay ceiling in percent points. Hard-clamped 200–2000. */
    public static float chargeOverchargeMaxPercent = 1000.0f;
    /** {@code StatsData.getLevel()} gate. 0 = everyone. */
    public static int chargeOverchargeMinLevel = 0;
    /** Size / life / explosion growth per percent above 175. */
    public static float chargeOverchargeSizePerPercent = 0.004f;
    /** Speed growth per percent above 175. */
    public static float chargeOverchargeSpeedPerPercent = 0.001f;
    /**
     * Soft cap on the extra damage factor applied on top of DMZ's 1.75×.
     * Ignored when {@link #kiFullGameplayScaling} is on.
     */
    public static float chargeOverchargeMaxDamageScale = 4.0f;
    /** Dampened form-size factor. Combined with {@link #chargeFormSizeLogCap}. */
    public static float chargeFormSizeFactor = 0.25f;
    /** {@code ln(formMult)} is capped here so a 1000× form cannot blow the projectile up. */
    public static float chargeFormSizeLogCap = 4.0f;
    /**
     * Optional extra size factor per form. Keys: {@code group.form} or short form id.
     * Missing = 1.0.
     */
    public static final Map<String, Float> chargeFormSizeByForm = new ConcurrentHashMap<>();
    /**
     * Master switch for charge craters, flying rocks, and disk slices.
     *
     * <p>Off by default — overcharge visuals and the 1000% cap work without breaking the world.
     */
    public static boolean chargeOverchargeGriefEnabled = false;
    public static float chargeOverchargeCraterMinPercent = 250.0f;
    public static int chargeOverchargeCraterMaxRadius = 4;
    public static int chargeOverchargeCraterIntervalTicks = 10;
    public static int chargeOverchargeMaxRocks = 4;
    public static boolean chargeOverchargeDiskSliceEnabled = true;
    public static boolean chargeOverchargeCameraEnabled = true;
    public static boolean chargeOverchargeVoicesEnabled = false;

    private XenoServerConfig() {}

    public static void load() {
        if (!Files.exists(path())) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path())) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            boolean migrated = data.configVersion < CURRENT_CONFIG_VERSION;
            apply(data);
            if (migrated) {
                // Guard stays enabled; the client now owns it on a dedicated non-Use key.
                // Scoped to files older than 3: this fired once, for the release that moved Guard
                // off right-click. Every later bump would otherwise re-enable it again and undo a
                // server owner's deliberate choice for a reason that no longer applies.
                boolean wasDisabled = data.configVersion < 3 && !bt3GuardEnabled;
                if (wasDisabled) bt3GuardEnabled = true;
                if (wasDisabled) {
                    XenoPixelsMod.LOGGER.info(
                            "Config migration re-enabled bt3GuardEnabled: Guard no longer takes"
                                    + " right-click, so the reason to disable it is gone. Set it"
                                    + " back to false in xenopixelsmod-server.json if you still"
                                    + " want it off.");
                }
                // A file written before 4 pins the old beamSurgeSizeGain of 1.2, which capped a
                // surged beam at 2.2x its baseline however long it was held — a hard stop rather
                // than a tuning value. Raising only a file still sitting on exactly that old
                // default leaves a deliberately chosen number alone.
                if (data.configVersion < 4 && beamSurgeSizeGain == 1.2f) {
                    beamSurgeSizeGain = 9.0f;
                    XenoPixelsMod.LOGGER.info(
                            "Config migration raised beamSurgeSizeGain 1.2 -> 9.0: a fully surged"
                                    + " beam can now reach 10x its baseline thickness instead of"
                                    + " 2.2x. Lower it in xenopixelsmod-server.json to taste.");
                }
                if (data.configVersion < 8) {
                    if (data.chaseMaxRange == 14.0) {
                        chaseMaxRange = 0.0;
                    }
                    if (!data.chaseFlightEnabled) {
                        chaseFlightEnabled = true;
                    }
                    if (data.chaseFlightSpeed == 0.9) {
                        chaseFlightSpeed = 1.2;
                    }
                    if (data.chaseFlightTimeoutTicks == 60) {
                        chaseFlightTimeoutTicks = 400;
                    }
                    XenoPixelsMod.LOGGER.info(
                            "Config migration: BT3 chase is fly-to-target, unlimited range,"
                                    + " speed 1.2, timeout 400. Set chaseMaxRange in"
                                    + " xenopixelsmod-server.json to cap it.");
                }
                if (data.configVersion < 9 && data.kiDeflectAimDot == 0.55f) {
                    kiDeflectAimDot = 0.2f;
                    XenoPixelsMod.LOGGER.info(
                            "Config migration: kiDeflectAimDot 0.55 -> 0.2 so punches at 2-3"
                                    + " blocks land on ki blasts.");
                }
                // The version-8 migration above set this to 3.5 (~70 blocks/sec), which resolves
                // a normal chase gap in 1-3 ticks -- imperceptible as travel, so it looked like
                // the old instant teleport it was meant to replace. Only reset installs still
                // sitting on that value; a deliberate admin override is left alone.
                if (data.configVersion < 10 && data.chaseFlightSpeed == 3.5) {
                    chaseFlightSpeed = 1.2;
                    XenoPixelsMod.LOGGER.info(
                            "Config migration: chaseFlightSpeed 3.5 -> 1.2 so chase-dash reads as"
                                    + " a glide instead of a teleport.");
                }
                if (data.configVersion < 11 && data.hakaiChannelTicks == 80) {
                    hakaiChannelTicks = 40;
                    XenoPixelsMod.LOGGER.info(
                            "Config migration: hakaiChannelTicks 80 -> 40 (DBS-length channel).");
                }
                save();
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load server config", e);
        }
    }

    /** Multipliers are bounded so a stray config cannot make a player untouchable. */
    private static float clampMult(float value, float fallback) {
        if (!(value > 0f)) return fallback;
        return Math.max(1f, Math.min(4f, value));
    }

    public static void save() {
        // Every form-scale writer routes through here, so this is the one place the memo has to
        // be dropped. Invalidating per-mutation instead would be one missed call away from
        // silently serving stale multipliers.
        invalidateFormScaleCache();
        try {
            Files.createDirectories(path().getParent());
            try (Writer writer = Files.newBufferedWriter(path())) {
                GSON.toJson(snapshot(), writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save server config", e);
        }
    }

    public static Data snapshot() {
        Data d = new Data();
        d.configVersion = CURRENT_CONFIG_VERSION;
        d.dmzHudEnabled = dmzHudEnabled;
        d.dmzContentBootstrap = dmzContentBootstrap;
        d.dmzSagaSpawnCompat = dmzSagaSpawnCompat;
        d.npcSayEnabled = npcSayEnabled;
        d.npcCommandsIgnoreCommandBlockSetting = npcCommandsIgnoreCommandBlockSetting;
        d.bt3CombatEnabled = bt3CombatEnabled;
        d.bt3ComboEnabled = bt3ComboEnabled;
        d.bt3CinematicRushEnabled = bt3CinematicRushEnabled;
        d.bt3VanishEnabled = bt3VanishEnabled;
        d.bt3ChaseDashEnabled = bt3ChaseDashEnabled;
        d.chaseFlightEnabled = chaseFlightEnabled;
        d.bt3BackstepEnabled = bt3BackstepEnabled;
        d.bt3FinisherEnabled = bt3FinisherEnabled;
        d.bt3ChargeAttackEnabled = bt3ChargeAttackEnabled;
        d.bt3DragonDashEnabled = bt3DragonDashEnabled;
        d.bt3GuardEnabled = bt3GuardEnabled;
        d.bt3SuperCounterEnabled = bt3SuperCounterEnabled;
        d.bt3KiBlastCancelEnabled = bt3KiBlastCancelEnabled;
        d.bt3ZBurstEnabled = bt3ZBurstEnabled;
        d.bt3LockCycleEnabled = bt3LockCycleEnabled;
        d.lockOnThroughBlocks = lockOnThroughBlocks;
        d.kiDiskDespawnOnHitBudget = kiDiskDespawnOnHitBudget;
        d.bt3ComboPunchesOnly = bt3ComboPunchesOnly;
        d.protectDmzMasters = protectDmzMasters;
        d.protectMastersFromCombatKnockback = protectMastersFromCombatKnockback;
        d.migrateCustomNpcsWorldData = migrateCustomNpcsWorldData;
        d.npcDmzStatsAuthoritative = npcDmzStatsAuthoritative;
        d.npcScriptTickInterval = npcScriptTickInterval;
        d.bt3RushChainEnabled = bt3RushChainEnabled;
        d.bt3SonicSwayEnabled = bt3SonicSwayEnabled;
        d.bt3UltimateEnabled = bt3UltimateEnabled;
        d.bt3SparkingEnabled = bt3SparkingEnabled;
        d.hakaiEnabled = hakaiEnabled;
        d.hakaiTargetGlow = hakaiTargetGlow;
        d.hakaiKiCost = hakaiKiCost;
        d.hakaiMaxRange = hakaiMaxRange;
        d.hakaiCooldownTicks = hakaiCooldownTicks;
        d.hakaiChannelTicks = hakaiChannelTicks;
        d.hakaiPoiseFraction = hakaiPoiseFraction;
        d.hakaiMoveInterruptDistance = hakaiMoveInterruptDistance;
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
        d.maxFlightSpeed = maxFlightSpeed;
        d.maxFlightAngularVelocity = maxFlightAngularVelocity;
        d.vanishShadeEnabled = vanishShadeEnabled;
        d.vanishShadeDensity = vanishShadeDensity;
        d.vanishThunderVolume = vanishThunderVolume;
        d.vanishSoundOut = vanishSoundOut;
        d.vanishSoundIn = vanishSoundIn;
        d.rushChainKiCost = rushChainKiCost;
        d.rushChainDamageScale = rushChainDamageScale;
        d.rushChainRange = rushChainRange;
        d.rushKiCost = rushKiCost;
        d.rushCooldownTicks = rushCooldownTicks;
        d.rushAutoUnlock = rushAutoUnlock;
        d.rushAutoEquipSlots = rushAutoEquipSlots;
        d.zanzokenEnabled = zanzokenEnabled;
        d.zanzokenKiCost = zanzokenKiCost;
        d.zanzokenWindowTicks = zanzokenWindowTicks;
        d.zanzokenIFramesTicks = zanzokenIFramesTicks;
        d.zanzokenCooldownTicks = zanzokenCooldownTicks;
        d.zanzokenGhostAfterimage = zanzokenGhostAfterimage;
        d.zanzokenGhostFadeMode = zanzokenGhostFadeMode;
        d.zanzokenGhostAlpha = zanzokenGhostAlpha;
        d.zanzokenRingClones = zanzokenRingClones;
        d.zanzokenRingRadius = zanzokenRingRadius;
        d.zanzokenRingTicks = zanzokenRingTicks;
        d.multiFormEnabled = multiFormEnabled;
        d.multiFormBodies = multiFormBodies;
        d.multiFormKiCost = multiFormKiCost;
        d.sonicSwayStaminaCost = sonicSwayStaminaCost;
        d.sonicSwayIFramesTicks = sonicSwayIFramesTicks;
        d.sonicSwayCooldownTicks = sonicSwayCooldownTicks;
        d.ultimateKiCost = ultimateKiCost;
        d.ultimateDamageScale = ultimateDamageScale;
        d.ultimateCooldownTicks = ultimateCooldownTicks;
        d.sparkingBuildPerHit = sparkingBuildPerHit;
        d.sparkingBuildOnHurt = sparkingBuildOnHurt;
        d.sparkingDurationTicks = sparkingDurationTicks;
        d.sparkingChargeTicks = sparkingChargeTicks;
        d.sparkingDamageMult = sparkingDamageMult;
        d.sparkingFromKiCharge = sparkingFromKiCharge;
        d.zanzokenConfusesAi = zanzokenConfusesAi;
        d.zanzokenAfterimageTicks = zanzokenAfterimageTicks;
        d.statMaxOverride = statMaxOverride;
        d.xenoSlotTechniquesEnabled = xenoSlotTechniquesEnabled;
        d.sparkingReleaseLimit = sparkingReleaseLimit;
        d.sparkingCooldownTicks = sparkingCooldownTicks;
        d.sparkingMoveSpeedMult = sparkingMoveSpeedMult;
        d.sparkingAttackSpeedMult = sparkingAttackSpeedMult;
        d.sparkingEndEnergyFraction = sparkingEndEnergyFraction;
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
        d.kiBlastHoldToFire = kiBlastHoldToFire;
        d.kiBlastCooldownTicks = kiBlastCooldownTicks;
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
        d.kiProjectileMaxSize = kiProjectileMaxSize;
        d.kiProjectileMaxSpeed = kiProjectileMaxSpeed;
        d.kiOverchargeSpeedPerPercent = kiOverchargeSpeedPerPercent;
        d.kiFullGameplayScaling = kiFullGameplayScaling;
        d.kiDestructionMaxRadius = kiDestructionMaxRadius;
        d.kiDestructionBlocksPerTick = kiDestructionBlocksPerTick;
        d.vanishMaxRange = vanishMaxRange;
        d.chaseMaxRange = chaseMaxRange;
        d.chaseFlightSpeed = chaseFlightSpeed;
        d.chaseFlightTimeoutTicks = chaseFlightTimeoutTicks;
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
        d.kickKnockbackScale = kickKnockbackScale;
        d.comboLaunchKickEvery = comboLaunchKickEvery;
        d.comboMashIntervalTicks = comboMashIntervalTicks;
        d.comboAnimGeneration = comboAnimGeneration;
        d.vanishNearField = vanishNearField;
        d.vanishOpenSpotSearch = vanishOpenSpotSearch;
        d.comboLauncherUp = comboLauncherUp;
        d.comboLauncherHoriz = comboLauncherHoriz;
        d.vanishGap = vanishGap;
        d.vanishSide = vanishSide;
        d.chaseStopGap = chaseStopGap;
        d.dmzStructureY = dmzStructureY;
        d.dmzStructureYOffset = dmzStructureYOffset;
        d.dmzStructureMaster = dmzStructureMaster;
        d.maxComboSteps = maxComboSteps;
        d.chargeMaxTicks = chargeMaxTicks;
        d.yawpKiGriefingEnabled = yawpKiGriefingEnabled;
        d.sneakToPickup = sneakToPickup;
        d.allowKiWithItemInHand = allowKiWithItemInHand;
        d.kiDeflectEnabled = kiDeflectEnabled;
        d.kiDeflectReach = kiDeflectReach;
        d.kiDeflectAimDot = kiDeflectAimDot;
        d.kiDeflectSpeedScale = kiDeflectSpeedScale;
        d.kiDeflectMinSpeed = kiDeflectMinSpeed;
        d.kiDeflectDamageScale = kiDeflectDamageScale;
        d.kiDeflectStaminaCost = kiDeflectStaminaCost;
        d.kiDeflectCooldownTicks = kiDeflectCooldownTicks;
        d.beamSurgeEnabled = beamSurgeEnabled;
        d.beamSurgeKiPerTick = beamSurgeKiPerTick;
        d.beamSurgeStaminaPerTick = beamSurgeStaminaPerTick;
        d.beamSurgeCostGrowth = beamSurgeCostGrowth;
        d.beamSurgeSizeGain = beamSurgeSizeGain;
        d.beamSurgeDamageGain = beamSurgeDamageGain;
        d.beamSurgeReachGain = beamSurgeReachGain;
        d.beamSurgeSearchRadius = beamSurgeSearchRadius;
        d.beamSurgeCeiling = beamSurgeCeiling;
        d.beamSurgeCeilingPerMastery = beamSurgeCeilingPerMastery;
        d.beamSurgeRampPerTick = beamSurgeRampPerTick;
        d.beamSurgeRampPerMastery = beamSurgeRampPerMastery;
        d.beamSurgeMaxLength = beamSurgeMaxLength;
        d.barrageExtraTicks1 = barrageExtraTicks1;
        d.barrageExtraTicks2 = barrageExtraTicks2;
        d.barrageExtraTicks3 = barrageExtraTicks3;
        d.barrageDurationTicks = barrageDurationTicks;
        d.kiDurationTicks = kiDurationTicks;
        d.kiDurationByType = new java.util.LinkedHashMap<>(kiDurationByType);
        d.barrageCooldownTicks = barrageCooldownTicks;
        d.barrageCooldownReduce1 = barrageCooldownReduce1;
        d.barrageCooldownReduce2 = barrageCooldownReduce2;
        d.barrageCooldownReduce3 = barrageCooldownReduce3;
        d.barrageKiPerTick = barrageKiPerTick;
        d.guidanceControlRange = guidanceControlRange;
        d.guidanceTurnRate = guidanceTurnRate;
        d.guidanceCameraRate = guidanceCameraRate;
        d.guidanceHoldGraceTicks = guidanceHoldGraceTicks;
        d.guidanceLookRayMin = guidanceLookRayMin;
        d.sparkingAuraEnabled = sparkingAuraEnabled;
        d.sparkingAuraDensity = sparkingAuraDensity;
        d.yawpPlayerKiFlags = new java.util.ArrayList<>(yawpPlayerKiFlags);
        d.yawpMobKiFlags = new java.util.ArrayList<>(yawpMobKiFlags);
        d.masterKiGriefRadius = masterKiGriefRadius;
        d.chargeOverchargeEnabled = chargeOverchargeEnabled;
        d.chargeOverchargeMaxPercent = chargeOverchargeMaxPercent;
        d.chargeOverchargeMinLevel = chargeOverchargeMinLevel;
        d.chargeOverchargeSizePerPercent = chargeOverchargeSizePerPercent;
        d.chargeOverchargeSpeedPerPercent = chargeOverchargeSpeedPerPercent;
        d.chargeOverchargeMaxDamageScale = chargeOverchargeMaxDamageScale;
        d.chargeFormSizeFactor = chargeFormSizeFactor;
        d.chargeFormSizeLogCap = chargeFormSizeLogCap;
        d.chargeFormSizeByForm = new LinkedHashMap<>(chargeFormSizeByForm);
        d.chargeOverchargeGriefEnabled = chargeOverchargeGriefEnabled;
        d.chargeOverchargeCraterMinPercent = chargeOverchargeCraterMinPercent;
        d.chargeOverchargeCraterMaxRadius = chargeOverchargeCraterMaxRadius;
        d.chargeOverchargeCraterIntervalTicks = chargeOverchargeCraterIntervalTicks;
        d.chargeOverchargeMaxRocks = chargeOverchargeMaxRocks;
        d.chargeOverchargeDiskSliceEnabled = chargeOverchargeDiskSliceEnabled;
        d.chargeOverchargeCameraEnabled = chargeOverchargeCameraEnabled;
        d.chargeOverchargeVoicesEnabled = chargeOverchargeVoicesEnabled;
        return d;
    }

    public static void apply(Data d) {
        if (d == null) return;
        dmzHudEnabled = d.dmzHudEnabled;
        dmzContentBootstrap = d.dmzContentBootstrap;
        dmzSagaSpawnCompat = d.dmzSagaSpawnCompat;
        npcSayEnabled = d.npcSayEnabled;
        npcCommandsIgnoreCommandBlockSetting = d.npcCommandsIgnoreCommandBlockSetting;
        bt3CombatEnabled = d.bt3CombatEnabled;
        bt3ComboEnabled = d.bt3ComboEnabled;
        bt3CinematicRushEnabled = d.bt3CinematicRushEnabled;
        bt3VanishEnabled = d.bt3VanishEnabled;
        bt3ChaseDashEnabled = d.bt3ChaseDashEnabled;
        chaseFlightEnabled = d.chaseFlightEnabled;
        bt3BackstepEnabled = d.bt3BackstepEnabled;
        bt3FinisherEnabled = d.bt3FinisherEnabled;
        bt3ChargeAttackEnabled = d.bt3ChargeAttackEnabled;
        bt3DragonDashEnabled = d.bt3DragonDashEnabled;
        bt3GuardEnabled = d.bt3GuardEnabled;
        bt3SuperCounterEnabled = d.bt3SuperCounterEnabled;
        bt3KiBlastCancelEnabled = d.bt3KiBlastCancelEnabled;
        bt3ZBurstEnabled = d.bt3ZBurstEnabled;
        bt3LockCycleEnabled = d.bt3LockCycleEnabled;
        lockOnThroughBlocks = d.lockOnThroughBlocks == null || d.lockOnThroughBlocks;
        kiDiskDespawnOnHitBudget = d.kiDiskDespawnOnHitBudget == null || d.kiDiskDespawnOnHitBudget;
        bt3ComboPunchesOnly = d.bt3ComboPunchesOnly;
        protectDmzMasters = d.protectDmzMasters;
        protectMastersFromCombatKnockback = d.protectMastersFromCombatKnockback;
        migrateCustomNpcsWorldData = d.migrateCustomNpcsWorldData;
        npcDmzStatsAuthoritative = d.npcDmzStatsAuthoritative == null || d.npcDmzStatsAuthoritative;
        npcScriptTickInterval = d.npcScriptTickInterval == null ? 1
                : Math.max(1, Math.min(20, d.npcScriptTickInterval));
        bt3RushChainEnabled = d.bt3RushChainEnabled;
        bt3SonicSwayEnabled = d.bt3SonicSwayEnabled;
        bt3UltimateEnabled = d.bt3UltimateEnabled;
        bt3SparkingEnabled = d.bt3SparkingEnabled;
        hakaiEnabled = d.hakaiEnabled;
        hakaiTargetGlow = d.hakaiTargetGlow;
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
        thrusterMaxImpulse = d.thrusterMaxImpulse > 0.0 ? d.thrusterMaxImpulse : 6_000.0;
        // Zero/negative would clamp every ship to a standstill; treat as "unset" the same way.
        maxFlightSpeed = d.maxFlightSpeed > 0.0 ? d.maxFlightSpeed : 28.0;
        maxFlightAngularVelocity = d.maxFlightAngularVelocity > 0.0 ? d.maxFlightAngularVelocity : 2.0;
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
        rushKiCost = d.rushKiCost >= 0 ? d.rushKiCost : 25.0;
        rushCooldownTicks = Math.max(1, d.rushCooldownTicks);
        rushAutoUnlock = d.rushAutoUnlock;
        rushAutoEquipSlots = d.rushAutoEquipSlots;
        zanzokenEnabled = d.zanzokenEnabled;
        zanzokenKiCost = Math.max(0f, d.zanzokenKiCost);
        zanzokenWindowTicks = Math.max(1, d.zanzokenWindowTicks);
        zanzokenIFramesTicks = Math.max(0, d.zanzokenIFramesTicks);
        zanzokenCooldownTicks = Math.max(0, d.zanzokenCooldownTicks);
        zanzokenGhostAfterimage = d.zanzokenGhostAfterimage;
        zanzokenGhostFadeMode = d.zanzokenGhostFadeMode == null ? 2
                : Math.max(1, Math.min(3, d.zanzokenGhostFadeMode));
        zanzokenGhostAlpha = d.zanzokenGhostAlpha == null ? 0.55f
                : Math.max(0.05f, Math.min(1.0f, d.zanzokenGhostAlpha));
        zanzokenRingClones = Math.max(1, Math.min(16, d.zanzokenRingClones));
        zanzokenRingRadius = d.zanzokenRingRadius > 0 ? Math.min(12.0, d.zanzokenRingRadius) : 3.0;
        zanzokenRingTicks = Math.max(20, Math.min(1200, d.zanzokenRingTicks));
        multiFormEnabled = d.multiFormEnabled;
        multiFormBodies = Math.max(2, Math.min(8, d.multiFormBodies));
        multiFormKiCost = Math.max(0f, d.multiFormKiCost);
        sonicSwayStaminaCost = Math.max(0f, d.sonicSwayStaminaCost);
        sonicSwayIFramesTicks = Math.max(2, Math.min(40, d.sonicSwayIFramesTicks <= 0 ? 8 : d.sonicSwayIFramesTicks));
        sonicSwayCooldownTicks = Math.max(5, Math.min(80, d.sonicSwayCooldownTicks <= 0 ? 18 : d.sonicSwayCooldownTicks));
        ultimateKiCost = Math.max(0f, d.ultimateKiCost);
        ultimateDamageScale = d.ultimateDamageScale > 0f ? d.ultimateDamageScale : 2.4f;
        ultimateCooldownTicks = Math.max(40, Math.min(600, d.ultimateCooldownTicks <= 0 ? 200 : d.ultimateCooldownTicks));
        hakaiKiCost = Math.max(0f, d.hakaiKiCost > 0f ? d.hakaiKiCost : 60.0f);
        hakaiMaxRange = d.hakaiMaxRange > 0 ? d.hakaiMaxRange : 15.0;
        hakaiCooldownTicks = Math.max(40, Math.min(2400, d.hakaiCooldownTicks <= 0 ? 600 : d.hakaiCooldownTicks));
        hakaiChannelTicks = Math.max(10, Math.min(400, d.hakaiChannelTicks <= 0 ? 40 : d.hakaiChannelTicks));
        hakaiPoiseFraction = d.hakaiPoiseFraction == null ? 0.35f
                : Math.max(0f, Math.min(1f, d.hakaiPoiseFraction));
        hakaiMoveInterruptDistance = d.hakaiMoveInterruptDistance == null ? 3.0
                : Math.max(0.5, Math.min(32.0, d.hakaiMoveInterruptDistance));
        sparkingBuildPerHit = Math.max(0f, d.sparkingBuildPerHit);
        sparkingBuildOnHurt = Math.max(0f, d.sparkingBuildOnHurt);
        sparkingDurationTicks = Math.max(20, Math.min(1200, d.sparkingDurationTicks <= 0 ? 200 : d.sparkingDurationTicks));
        sparkingChargeTicks = Math.max(20, Math.min(1200, d.sparkingChargeTicks <= 0 ? 100 : d.sparkingChargeTicks));
        sparkingDamageMult = d.sparkingDamageMult > 1f ? d.sparkingDamageMult : 1.35f;
        sparkingFromKiCharge = d.sparkingFromKiCharge == null || d.sparkingFromKiCharge;
        zanzokenConfusesAi = d.zanzokenConfusesAi == null || d.zanzokenConfusesAi;
        zanzokenAfterimageTicks = Math.max(1, Math.min(200, d.zanzokenAfterimageTicks <= 0 ? 40 : d.zanzokenAfterimageTicks));
        statMaxOverride = net.bullettrain.xenopixelsmod.combat.XenoStatCeiling.store(d.statMaxOverride);
        xenoSlotTechniquesEnabled = d.xenoSlotTechniquesEnabled != null && d.xenoSlotTechniquesEnabled;
        sparkingReleaseLimit = d.sparkingReleaseLimit > 0 ? Math.min(1000, d.sparkingReleaseLimit) : 225;
        sparkingCooldownTicks = Math.max(0, Math.min(12000, d.sparkingCooldownTicks));
        sparkingMoveSpeedMult = clampMult(d.sparkingMoveSpeedMult, 1.25f);
        sparkingAttackSpeedMult = clampMult(d.sparkingAttackSpeedMult, 1.35f);
        sparkingEndEnergyFraction = Math.max(0f, Math.min(0.5f, d.sparkingEndEnergyFraction));
        transformImpactRadius = d.transformImpactRadius > 0f ? d.transformImpactRadius : 3.5f;
        transformImpactKnock = Math.max(0f, d.transformImpactKnock);
        chaseSuccessChance = !Float.isFinite(d.chaseSuccessChance) || d.chaseSuccessChance < 0f
                ? 1.0f : Math.max(0f, Math.min(1f, d.chaseSuccessChance));
        guardDamageReduction = d.guardDamageReduction > 0f ? Math.min(0.95f, d.guardDamageReduction) : 0.55f;
        guardStaminaPerHit = Math.max(0f, d.guardStaminaPerHit);
        guardStaminaPerSec = Math.max(0f, d.guardStaminaPerSec);
        guardBreakStunTicks = Math.max(5, Math.min(80, d.guardBreakStunTicks <= 0 ? 25 : d.guardBreakStunTicks));
        superCounterWindowTicks = Math.max(4, Math.min(40, d.superCounterWindowTicks <= 0 ? 12 : d.superCounterWindowTicks));
        superCounterKiCost = Math.max(0f, d.superCounterKiCost);
        superCounterDamageScale = d.superCounterDamageScale > 0f ? d.superCounterDamageScale : 1.35f;
        kiBlastCancelKiCost = Math.max(0f, d.kiBlastCancelKiCost);
        kiBlastCancelDamageScale = d.kiBlastCancelDamageScale > 0f ? d.kiBlastCancelDamageScale : 0.85f;
        kiBlastHoldToFire = d.kiBlastHoldToFire;
        kiBlastCooldownTicks = Math.max(1, d.kiBlastCooldownTicks);
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
        kiProjectileMaxSize = positiveFinite(d.kiProjectileMaxSize, 320f);
        kiProjectileMaxSpeed = positiveFinite(d.kiProjectileMaxSpeed, 32f);
        kiOverchargeSpeedPerPercent = nonNegativeFinite(d.kiOverchargeSpeedPerPercent, 0.004f);
        kiFullGameplayScaling = d.kiFullGameplayScaling;
        kiDestructionMaxRadius = positiveFinite(d.kiDestructionMaxRadius, 32f);
        kiDestructionBlocksPerTick = Math.max(64, d.kiDestructionBlocksPerTick);
        vanishMaxRange = d.vanishMaxRange > 0 ? d.vanishMaxRange : 7.0;
        chaseMaxRange = d.chaseMaxRange < 0 ? 0.0 : d.chaseMaxRange;
        chaseFlightSpeed = d.chaseFlightSpeed > 0 ? d.chaseFlightSpeed : 3.5;
        chaseFlightTimeoutTicks = d.chaseFlightTimeoutTicks > 0 ? d.chaseFlightTimeoutTicks : 2400;
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
        kickKnockbackScale = d.kickKnockbackScale > 0f
                ? Math.max(0.1f, Math.min(8f, d.kickKnockbackScale)) : 1.0f;
        comboLaunchKickEvery = Math.max(0, Math.min(32, d.comboLaunchKickEvery));
        comboMashIntervalTicks = Math.max(2, Math.min(20,
                d.comboMashIntervalTicks <= 0 ? 6 : d.comboMashIntervalTicks));
        comboAnimGeneration =
                net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog.clampGeneration(
                        d.comboAnimGeneration);
        vanishNearField = d.vanishNearField >= 0 ? Math.min(8.0, d.vanishNearField) : 0.0;
        vanishOpenSpotSearch = d.vanishOpenSpotSearch;
        comboLauncherUp = d.comboLauncherUp > 0f
                ? Math.max(0.2f, Math.min(6f, d.comboLauncherUp)) : 1.85f;
        comboLauncherHoriz = d.comboLauncherHoriz > 0f
                ? Math.max(0.05f, Math.min(4f, d.comboLauncherHoriz)) : 0.55f;
        vanishGap = d.vanishGap >= 0 ? Math.min(16.0, d.vanishGap) : 1.35;
        vanishSide = d.vanishSide >= 0 ? Math.min(8.0, d.vanishSide) : 1.05;
        chaseStopGap = d.chaseStopGap >= 0 ? Math.min(8.0, d.chaseStopGap) : 0.0;
        dmzStructureY = d.dmzStructureY;
        dmzStructureYOffset = d.dmzStructureYOffset;
        dmzStructureMaster = d.dmzStructureMaster;
        maxComboSteps = Math.max(1, Math.min(8, d.maxComboSteps <= 0 ? 5 : d.maxComboSteps));
        chargeMaxTicks = Math.max(10, Math.min(80, d.chargeMaxTicks <= 0 ? 28 : d.chargeMaxTicks));

        yawpKiGriefingEnabled = d.yawpKiGriefingEnabled;
        sneakToPickup = d.sneakToPickup;
        allowKiWithItemInHand = d.allowKiWithItemInHand;
        kiDeflectEnabled = d.kiDeflectEnabled;
        kiDeflectReach = Math.max(0.5f, d.kiDeflectReach);
        kiDeflectAimDot = Math.max(-1f, Math.min(1f, d.kiDeflectAimDot));
        kiDeflectSpeedScale = Math.max(0.1f, d.kiDeflectSpeedScale);
        kiDeflectMinSpeed = Math.max(0.05f, d.kiDeflectMinSpeed);
        kiDeflectDamageScale = Math.max(0.1f, d.kiDeflectDamageScale);
        kiDeflectStaminaCost = Math.max(0f, d.kiDeflectStaminaCost);
        kiDeflectCooldownTicks = Math.max(0, Math.min(100, d.kiDeflectCooldownTicks));
        beamSurgeEnabled = d.beamSurgeEnabled;
        beamSurgeKiPerTick = Math.max(0f, d.beamSurgeKiPerTick);
        beamSurgeStaminaPerTick = Math.max(0f, d.beamSurgeStaminaPerTick);
        beamSurgeCostGrowth = Math.max(0f, d.beamSurgeCostGrowth);
        beamSurgeSizeGain = Math.max(0f, d.beamSurgeSizeGain);
        beamSurgeDamageGain = Math.max(0f, d.beamSurgeDamageGain);
        beamSurgeReachGain = Math.max(0f, d.beamSurgeReachGain);
        beamSurgeSearchRadius = Math.max(2f, d.beamSurgeSearchRadius);
        // Clamped to 0..1: the manager scales every other configured maximum against this surge,
        // so a ceiling above 1 would silently multiply size, damage and reach past their own caps.
        beamSurgeCeiling = clamp01(d.beamSurgeCeiling, 0.45f);
        beamSurgeCeilingPerMastery = clamp01(d.beamSurgeCeilingPerMastery, 0.183f);
        // A ramp of 0 would freeze surge at zero forever, so the floor is a slow but real climb.
        beamSurgeRampPerTick = Math.min(1f, Math.max(0.001f,
                nonNegativeFinite(d.beamSurgeRampPerTick, 0.020f)));
        beamSurgeRampPerMastery = Math.min(1f, nonNegativeFinite(d.beamSurgeRampPerMastery, 0.010f));
        // Negative or zero means uncapped, so this only rejects a NaN.
        beamSurgeMaxLength = Float.isFinite(d.beamSurgeMaxLength) ? d.beamSurgeMaxLength : 192f;
        barrageExtraTicks1 = Math.max(0, d.barrageExtraTicks1);
        barrageExtraTicks2 = Math.max(0, d.barrageExtraTicks2);
        barrageExtraTicks3 = Math.max(0, d.barrageExtraTicks3);
        barrageDurationTicks = Math.max(0, d.barrageDurationTicks);
        kiDurationTicks = Math.max(0, d.kiDurationTicks);
        kiDurationByType.clear();
        if (d.kiDurationByType != null) {
            for (java.util.Map.Entry<String, Integer> e : d.kiDurationByType.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                kiDurationByType.put(e.getKey().toLowerCase(), clampBarrageTicks(e.getValue()));
            }
        }
        barrageCooldownTicks = Math.max(0, d.barrageCooldownTicks);
        barrageCooldownReduce1 = Math.max(0, d.barrageCooldownReduce1);
        barrageCooldownReduce2 = Math.max(0, d.barrageCooldownReduce2);
        barrageCooldownReduce3 = Math.max(0, d.barrageCooldownReduce3);
        barrageKiPerTick = Math.max(0f, d.barrageKiPerTick);
        guidanceControlRange = Math.max(0, d.guidanceControlRange);
        guidanceTurnRate = clamp01(d.guidanceTurnRate, 0.0f);
        guidanceCameraRate = nonNegativeFinite(d.guidanceCameraRate, 0.38f);
        guidanceHoldGraceTicks = Math.max(0, Math.min(40, d.guidanceHoldGraceTicks));
        guidanceLookRayMin = nonNegativeFinite(d.guidanceLookRayMin, 8.0f);
        applyGuidanceOverrides();
        sparkingAuraEnabled = d.sparkingAuraEnabled;
        sparkingAuraDensity = Math.max(0f, Math.min(3f, d.sparkingAuraDensity));
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
        chargeOverchargeEnabled = d.chargeOverchargeEnabled;
        chargeOverchargeMaxPercent = clampChargeCap(d.chargeOverchargeMaxPercent);
        chargeOverchargeMinLevel = Math.max(0, d.chargeOverchargeMinLevel);
        chargeOverchargeSizePerPercent = nonNegativeFinite(d.chargeOverchargeSizePerPercent, 0.004f);
        chargeOverchargeSpeedPerPercent = nonNegativeFinite(d.chargeOverchargeSpeedPerPercent, 0.001f);
        chargeOverchargeMaxDamageScale = d.chargeOverchargeMaxDamageScale > 1.0f
                ? d.chargeOverchargeMaxDamageScale : 4.0f;
        chargeFormSizeFactor = nonNegativeFinite(d.chargeFormSizeFactor, 0.25f);
        chargeFormSizeLogCap = d.chargeFormSizeLogCap > 0.0f ? d.chargeFormSizeLogCap : 4.0f;
        chargeFormSizeByForm.clear();
        if (d.chargeFormSizeByForm != null) {
            for (Map.Entry<String, Float> e : d.chargeFormSizeByForm.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) continue;
                if (!Float.isFinite(e.getValue()) || e.getValue() <= 0.0f) continue;
                chargeFormSizeByForm.put(e.getKey(), e.getValue());
            }
        }
        chargeOverchargeGriefEnabled = d.chargeOverchargeGriefEnabled;
        chargeOverchargeCraterMinPercent = Math.max(175.0f,
                nonNegativeFinite(d.chargeOverchargeCraterMinPercent, 250.0f));
        chargeOverchargeCraterMaxRadius = Math.max(1, Math.min(16,
                d.chargeOverchargeCraterMaxRadius <= 0 ? 4 : d.chargeOverchargeCraterMaxRadius));
        chargeOverchargeCraterIntervalTicks = Math.max(2, Math.min(40,
                d.chargeOverchargeCraterIntervalTicks <= 0 ? 10 : d.chargeOverchargeCraterIntervalTicks));
        chargeOverchargeMaxRocks = Math.max(0, Math.min(16, d.chargeOverchargeMaxRocks));
        chargeOverchargeDiskSliceEnabled = d.chargeOverchargeDiskSliceEnabled;
        chargeOverchargeCameraEnabled = d.chargeOverchargeCameraEnabled;
        chargeOverchargeVoicesEnabled = d.chargeOverchargeVoicesEnabled;
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
        return 1f + excess * kiOverchargeSizePerPercent * kiOverchargeMultiplier;
    }

    public static float kiOverchargeSpeedScale(int powerRelease) {
        float excess = kiOverchargeExcessPercent(powerRelease);
        return excess <= 0f ? 1f : 1f + excess * kiOverchargeSpeedPerPercent * kiOverchargeMultiplier;
    }

    public static float kiOverchargeDamageScale(int powerRelease) {
        float excess = kiOverchargeExcessPercent(powerRelease);
        if (excess <= 0f) return 1f;
        float scale = 1f + excess * kiOverchargeDamagePerPercent * kiOverchargeMultiplier;
        return kiFullGameplayScaling ? scale : Math.min(kiOverchargeMaxScale, scale);
    }

    public static float kiOverchargeExplosionScale(int powerRelease) {
        float excess = kiOverchargeExcessPercent(powerRelease);
        if (excess <= 0f) return 1f;
        float scale = 1f + excess * kiOverchargeExplosionPerPercent * kiOverchargeMultiplier;
        return kiFullGameplayScaling ? scale : Math.min(kiOverchargeMaxScale, scale);
    }

    public static float clampKiSize(float value) {
        return Float.isFinite(value) ? Math.max(0.1f, Math.min(value, kiProjectileMaxSize)) : 0.1f;
    }

    public static float clampKiSpeed(float value) {
        return Float.isFinite(value) ? Math.max(0.1f, Math.min(value, kiProjectileMaxSpeed)) : 0.1f;
    }

    /**
     * Hold a ki wave or laser to its configured reach.
     *
     * <p>A non-positive {@link #beamSurgeMaxLength} means uncapped, which is the stock behaviour:
     * neither DMZ nor the surge system bounds total beam length on its own.
     */
    public static float clampBeamLength(float value) {
        if (!Float.isFinite(value)) return 0f;
        if (beamSurgeMaxLength <= 0f) return value;
        return Math.min(value, beamSurgeMaxLength);
    }

    /**
     * Hard clamp written into {@code Techniques.setTechniqueChargePercent}.
     * Disabled feature keeps DMZ's stock 200.
     */
    public static float chargeOverchargeClamp() {
        if (!chargeOverchargeEnabled) return 200.0f;
        return clampChargeCap(chargeOverchargeMaxPercent);
    }

    public static float clampChargeCap(float value) {
        if (!Float.isFinite(value) || value <= 200.0f) return 1000.0f;
        return Math.min(2000.0f, value);
    }

    public static float kiDestructionRadiusLimit() {
        // DMZ scans a (2r+1)^3 cube synchronously. Convert the configured check budget back to
        // the largest safe radius, then apply the explicit radius ceiling too.
        float budgetRadius = (float) (Math.cbrt(kiDestructionBlocksPerTick) - 1.0) * 0.5f;
        return Math.max(0.5f, Math.min(kiDestructionMaxRadius, budgetRadius));
    }

    private static float positiveFinite(float value, float fallback) {
        return Float.isFinite(value) && value > 0f ? value : fallback;
    }

    private static float nonNegativeFinite(float value, float fallback) {
        return Float.isFinite(value) && value >= 0f ? value : fallback;
    }

    private static float clamp01(float value, float fallback) {
        if (!Float.isFinite(value)) return fallback;
        return Math.max(0f, Math.min(1f, value));
    }

    /** Wire legacy DMZ HUD config field for older code paths. */
    public static boolean isDmzHudEnabled() {
        return dmzHudEnabled;
    }

    public static void setDmzHudEnabled(boolean enabled) {
        dmzHudEnabled = enabled;
        save();
    }

    public static void setChaseFlightEnabled(boolean enabled) {
        chaseFlightEnabled = enabled;
        save();
    }

    public static void setLockOnThroughBlocks(boolean enabled) {
        lockOnThroughBlocks = enabled;
        save();
    }

    public static void setChaseMaxRange(double range) {
        chaseMaxRange = range < 0 ? 0.0 : range;
        save();
    }

    public static void setChaseFlightSpeed(double speed) {
        chaseFlightSpeed = Math.max(0.1, speed);
        save();
    }

    public static void setHakaiEnabled(boolean enabled) {
        hakaiEnabled = enabled;
        save();
    }

    public static void setHakaiKiCost(float cost) {
        hakaiKiCost = Math.max(0f, cost);
        save();
    }

    public static void setHakaiMaxRange(double range) {
        hakaiMaxRange = Math.max(1.0, range);
        save();
    }

    public static void setHakaiCooldownTicks(int ticks) {
        hakaiCooldownTicks = Math.max(40, Math.min(2400, ticks));
        save();
    }

    /** {@code chaseMaxRange <= 0} means no distance cap. */
    public static boolean chaseRangeUnlimited() {
        return chaseMaxRange <= 0.0;
    }

    public static float clampFormStatMultiplier(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 1.0f;
        return Math.max(FORM_STAT_MULT_MIN, Math.min(FORM_STAT_MULT_MAX, value));
    }

    /** Applies global + per-form + per-stat scale maps from a config/sync payload. */
    public static void applyFormScaleMaps(Data d) {
        if (d == null) return;
        invalidateFormScaleCache();
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

    public static final int BARRAGE_TICKS_MAX = 1200;

    public static int clampBarrageTicks(int ticks) {
        return Math.max(0, Math.min(BARRAGE_TICKS_MAX, ticks));
    }

    public static void setBarrageDurationTicks(int ticks) {
        barrageDurationTicks = clampBarrageTicks(ticks);
        save();
    }

    public static void setKiDurationTicks(int ticks) {
        kiDurationTicks = clampBarrageTicks(ticks);
        save();
    }

    public static void setKiDurationForType(String type, int ticks) {
        String key = type == null ? "" : type.trim().toLowerCase();
        if (key.isEmpty()) return;
        int value = clampBarrageTicks(ticks);
        if (value <= 0) kiDurationByType.remove(key);
        else kiDurationByType.put(key, value);
        if ("barrage".equals(key)) barrageDurationTicks = value;
        save();
    }

    public static void setBarrageCooldownTicks(int ticks) {
        barrageCooldownTicks = clampBarrageTicks(ticks);
        save();
    }

    public static final int GUIDANCE_RANGE_MAX = 1024;

    public static void setGuidanceControlRange(int blocks) {
        guidanceControlRange = Math.max(0, Math.min(GUIDANCE_RANGE_MAX, blocks));
        applyGuidanceOverrides();
        save();
    }

    public static void setGuidanceTurnRate(float rate) {
        guidanceTurnRate = clamp01(rate, 0.0f);
        applyGuidanceOverrides();
        save();
    }

    public static void setGuidanceCameraRate(float rate) {
        guidanceCameraRate = Math.max(0.0f, Math.min(1.0f, nonNegativeFinite(rate, 0.38f)));
        applyGuidanceOverrides();
        save();
    }

    public static void setGuidanceHoldGraceTicks(int ticks) {
        guidanceHoldGraceTicks = Math.max(0, Math.min(40, ticks));
        save();
    }

    public static void setGuidanceLookRayMin(float blocks) {
        guidanceLookRayMin = Math.max(0.0f, Math.min(64.0f, nonNegativeFinite(blocks, 8.0f)));
        applyGuidanceOverrides();
        save();
    }

    public static void setChargeOverchargeEnabled(boolean enabled) {
        chargeOverchargeEnabled = enabled;
        save();
    }

    public static void setChargeOverchargeMaxPercent(float percent) {
        chargeOverchargeMaxPercent = clampChargeCap(percent);
        save();
    }

    public static void setChargeOverchargeGriefEnabled(boolean enabled) {
        chargeOverchargeGriefEnabled = enabled;
        save();
    }

    public static void setKiProjectileMaxSize(float size) {
        kiProjectileMaxSize = positiveFinite(size, 320.0f);
        save();
    }

    public static void setKiProjectileMaxSpeed(float speed) {
        kiProjectileMaxSpeed = positiveFinite(speed, 32.0f);
        save();
    }

    public static void setKiDestructionMaxRadius(float radius) {
        kiDestructionMaxRadius = positiveFinite(radius, 32.0f);
        save();
    }

    public static void setKiFullGameplayScaling(boolean enabled) {
        kiFullGameplayScaling = enabled;
        save();
    }

    public static void setBeamSurgeEnabled(boolean enabled) {
        beamSurgeEnabled = enabled;
        save();
    }

    public static void setBarrageKiPerTick(float value) {
        barrageKiPerTick = Math.max(0.0f, value);
        save();
    }

    public static void setBarrageExtraTicks(int level, int ticks) {
        int value = Math.max(0, ticks);
        switch (level) {
            case 1 -> barrageExtraTicks1 = value;
            case 2 -> barrageExtraTicks2 = value;
            case 3 -> barrageExtraTicks3 = value;
            default -> {
                return;
            }
        }
        save();
    }

    public static void setBarrageCooldownReduce(int level, int ticks) {
        int value = Math.max(0, ticks);
        switch (level) {
            case 1 -> barrageCooldownReduce1 = value;
            case 2 -> barrageCooldownReduce2 = value;
            case 3 -> barrageCooldownReduce3 = value;
            default -> {
                return;
            }
        }
        save();
    }

    static void applyGuidanceOverrides() {
        net.bullettrain.xenopixelsmod.combat.technique.KiGuidanceMath.controlRangeOverride =
                guidanceControlRange;
        net.bullettrain.xenopixelsmod.combat.technique.KiGuidanceMath.turnRateOverride =
                guidanceTurnRate;
        net.bullettrain.xenopixelsmod.combat.technique.KiGuidanceMath.cameraRateOverride =
                guidanceCameraRate;
        net.bullettrain.xenopixelsmod.combat.technique.KiGuidanceMath.lookRayMinOverride =
                guidanceLookRayMin;
    }

    /**
     * Apply last-session combat fields from a sync payload without rewriting
     * the whole config (packet Data is incomplete for missiles, YAWP, etc.).
     */
    public static void applySyncedKiCombat(Data d) {
        if (d == null) return;
        chargeOverchargeEnabled = d.chargeOverchargeEnabled;
        chargeOverchargeMaxPercent = clampChargeCap(d.chargeOverchargeMaxPercent);
        chargeOverchargeMinLevel = Math.max(0, d.chargeOverchargeMinLevel);
        chargeOverchargeSizePerPercent = nonNegativeFinite(d.chargeOverchargeSizePerPercent, 0.004f);
        chargeOverchargeSpeedPerPercent = nonNegativeFinite(d.chargeOverchargeSpeedPerPercent, 0.001f);
        chargeOverchargeMaxDamageScale = d.chargeOverchargeMaxDamageScale > 1.0f
                ? d.chargeOverchargeMaxDamageScale : 4.0f;
        chargeFormSizeFactor = nonNegativeFinite(d.chargeFormSizeFactor, 0.25f);
        chargeFormSizeLogCap = d.chargeFormSizeLogCap > 0.0f ? d.chargeFormSizeLogCap : 4.0f;
        chargeOverchargeGriefEnabled = d.chargeOverchargeGriefEnabled;
        chargeOverchargeCraterMinPercent = Math.max(175.0f,
                nonNegativeFinite(d.chargeOverchargeCraterMinPercent, 250.0f));
        chargeOverchargeCraterMaxRadius = Math.max(1, Math.min(16,
                d.chargeOverchargeCraterMaxRadius <= 0 ? 4 : d.chargeOverchargeCraterMaxRadius));
        chargeOverchargeCraterIntervalTicks = Math.max(2, Math.min(40,
                d.chargeOverchargeCraterIntervalTicks <= 0 ? 10 : d.chargeOverchargeCraterIntervalTicks));
        chargeOverchargeMaxRocks = Math.max(0, Math.min(16, d.chargeOverchargeMaxRocks));
        chargeOverchargeDiskSliceEnabled = d.chargeOverchargeDiskSliceEnabled;
        chargeOverchargeCameraEnabled = d.chargeOverchargeCameraEnabled;
        chargeOverchargeVoicesEnabled = d.chargeOverchargeVoicesEnabled;
        barrageDurationTicks = Math.max(0, d.barrageDurationTicks);
        barrageCooldownTicks = Math.max(0, d.barrageCooldownTicks);
        barrageExtraTicks1 = Math.max(0, d.barrageExtraTicks1);
        barrageExtraTicks2 = Math.max(0, d.barrageExtraTicks2);
        barrageExtraTicks3 = Math.max(0, d.barrageExtraTicks3);
        barrageCooldownReduce1 = Math.max(0, d.barrageCooldownReduce1);
        barrageCooldownReduce2 = Math.max(0, d.barrageCooldownReduce2);
        barrageCooldownReduce3 = Math.max(0, d.barrageCooldownReduce3);
        barrageKiPerTick = Math.max(0f, d.barrageKiPerTick);
        kiDurationTicks = Math.max(0, d.kiDurationTicks);
        kiDurationByType.clear();
        if (d.kiDurationByType != null) {
            for (java.util.Map.Entry<String, Integer> e : d.kiDurationByType.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                kiDurationByType.put(e.getKey().toLowerCase(), clampBarrageTicks(e.getValue()));
            }
        }
        kiProjectileMaxSize = positiveFinite(d.kiProjectileMaxSize, 320f);
        kiProjectileMaxSpeed = positiveFinite(d.kiProjectileMaxSpeed, 32f);
        kiFullGameplayScaling = d.kiFullGameplayScaling;
        kiDestructionMaxRadius = positiveFinite(d.kiDestructionMaxRadius, 32f);
        kiDestructionBlocksPerTick = Math.max(64, d.kiDestructionBlocksPerTick);
        beamSurgeEnabled = d.beamSurgeEnabled;
        beamSurgeKiPerTick = Math.max(0f, d.beamSurgeKiPerTick);
        beamSurgeStaminaPerTick = Math.max(0f, d.beamSurgeStaminaPerTick);
        beamSurgeCostGrowth = Math.max(0f, d.beamSurgeCostGrowth);
        beamSurgeSizeGain = Math.max(0f, d.beamSurgeSizeGain);
        beamSurgeDamageGain = Math.max(0f, d.beamSurgeDamageGain);
        beamSurgeReachGain = Math.max(0f, d.beamSurgeReachGain);
        beamSurgeSearchRadius = Math.max(2f, d.beamSurgeSearchRadius);
        beamSurgeCeiling = clamp01(d.beamSurgeCeiling, 0.45f);
        beamSurgeCeilingPerMastery = clamp01(d.beamSurgeCeilingPerMastery, 0.183f);
        beamSurgeRampPerTick = Math.min(1f, Math.max(0.001f,
                nonNegativeFinite(d.beamSurgeRampPerTick, 0.020f)));
        beamSurgeRampPerMastery = Math.min(1f, nonNegativeFinite(d.beamSurgeRampPerMastery, 0.010f));
        beamSurgeMaxLength = Float.isFinite(d.beamSurgeMaxLength) ? d.beamSurgeMaxLength : 192f;
        lockOnThroughBlocks = d.lockOnThroughBlocks == null || d.lockOnThroughBlocks;
        vanishGap = d.vanishGap >= 0 ? Math.min(16.0, d.vanishGap) : 1.35;
        vanishSide = d.vanishSide >= 0 ? Math.min(8.0, d.vanishSide) : 1.05;
        // The attacking client predicts its own vanish landing from these three, so an operator's
        // tuning has to reach the client statics too or the prediction disagrees with the server
        // on every vanish rather than only the close ones.
        vanishNearField = d.vanishNearField >= 0 ? Math.min(8.0, d.vanishNearField) : 0.0;
        vanishOpenSpotSearch = d.vanishOpenSpotSearch;
        comboAnimGeneration =
                net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog.clampGeneration(
                        d.comboAnimGeneration);
        chaseStopGap = d.chaseStopGap >= 0 ? Math.min(8.0, d.chaseStopGap) : 0.0;
        guidanceControlRange = Math.max(0, d.guidanceControlRange);
        guidanceTurnRate = clamp01(d.guidanceTurnRate, 0.0f);
        guidanceCameraRate = nonNegativeFinite(d.guidanceCameraRate, 0.38f);
        guidanceHoldGraceTicks = Math.max(0, Math.min(40, d.guidanceHoldGraceTicks));
        guidanceLookRayMin = nonNegativeFinite(d.guidanceLookRayMin, 8.0f);
        applyGuidanceOverrides();
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
    /**
     * Whether any form scaling is configured at all.
     *
     * <p>Callers on hot paths check this <em>before</em> doing any work to resolve a form key.
     * {@link #formScaleFor(String, String)} is reached from DMZ's stat maths, which reads
     * multipliers live on every stat access, and resolving a key allocates several strings. On a
     * server that has not configured any scaling — the common case — this bail makes the whole
     * thing two field reads and no allocation.
     */
    public static boolean formScalingActive() {
        return Math.abs(formStatMultiplier - 1.0f) >= 1.0e-6f
                || !formPerFormMultipliers.isEmpty()
                || !formPerStatMultipliers.isEmpty()
                || !formPerFormStatMultipliers.isEmpty();
    }

    /**
     * Memo of resolved {@code (formKey, stat)} scales.
     *
     * <p>Resolution is not cheap: it normalises both keys, and both per-form lookups fall back to a
     * linear scan of their map that concatenates a string per entry. Doing that on every stat read
     * was the single largest source of garbage in the mod. The answer only changes when the config
     * does, so it is computed once per pair.
     *
     * <p>Invalidated from {@link #save()} and {@link #applyData}, which between them are reached by
     * every writer — the setters, the clear commands, config load, and the server-to-client sync.
     * Keying invalidation off those rather than off each individual map mutation is what makes a
     * stale entry impossible; a missed invalidation here would silently report wrong stats.
     */
    private static final Map<String, Float> FORM_SCALE_CACHE = new ConcurrentHashMap<>();

    static void invalidateFormScaleCache() {
        FORM_SCALE_CACHE.clear();
    }

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
        String cacheKey = (formKey == null ? "" : formKey) + ' ' + (stat == null ? "" : stat);
        Float memo = FORM_SCALE_CACHE.get(cacheKey);
        if (memo != null) return memo;

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
        float result = overall * statScale;
        FORM_SCALE_CACHE.put(cacheKey, result);
        return result;
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
        public int configVersion;
        public boolean dmzHudEnabled = false;
        public boolean dmzContentBootstrap = true;
        public boolean dmzSagaSpawnCompat = true;
        public boolean npcSayEnabled = true;
        public boolean npcCommandsIgnoreCommandBlockSetting = false;
        public boolean bt3CombatEnabled = true;
        public boolean bt3ComboEnabled = true;
        public boolean bt3CinematicRushEnabled = true;
        public boolean bt3VanishEnabled = true;
        public boolean bt3ChaseDashEnabled = true;
        public boolean chaseFlightEnabled = true;
        public boolean bt3BackstepEnabled = true;
        public boolean bt3FinisherEnabled = true;
        public boolean bt3ChargeAttackEnabled = true;
        public boolean bt3DragonDashEnabled = true;
        public boolean bt3GuardEnabled = true;
        public boolean bt3SuperCounterEnabled = true;
        public boolean bt3KiBlastCancelEnabled = true;
        public boolean bt3ZBurstEnabled = true;
        public boolean bt3LockCycleEnabled = true;
        public Boolean lockOnThroughBlocks;
        public Boolean kiDiskDespawnOnHitBudget;
        public boolean bt3ComboPunchesOnly = true;
        public boolean protectDmzMasters = true;
        public boolean protectMastersFromCombatKnockback = true;
        public boolean migrateCustomNpcsWorldData = true;
        public Boolean npcDmzStatsAuthoritative;
        public Integer npcScriptTickInterval;
        public boolean bt3RushChainEnabled = true;
        public boolean bt3SonicSwayEnabled = true;
        public boolean bt3UltimateEnabled = true;
        public boolean bt3SparkingEnabled = true;
        public boolean hakaiEnabled = true;
        public boolean hakaiTargetGlow = true;
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
        public double thrusterMaxImpulse = 6_000.0;
        public double maxFlightSpeed = 28.0;
        public double maxFlightAngularVelocity = 2.0;
        public boolean vanishShadeEnabled = true;
        public double vanishShadeDensity = 1.0;
        public double vanishThunderVolume = 0.35;
        public String vanishSoundOut = "xenopixelsmod:vanish";
        public String vanishSoundIn = "xenopixelsmod:vanish";
        public float rushChainKiCost = 10.0f;
        public float rushChainDamageScale = 0.9f;
        public double rushChainRange = 16.0;
        public double rushKiCost = 25.0;
        public int rushCooldownTicks = 40;
        public boolean rushAutoUnlock = true;
        public boolean rushAutoEquipSlots = false;
        public boolean zanzokenEnabled = true;
        public float zanzokenKiCost = 20.0f;
        public int zanzokenWindowTicks = 8;
        public int zanzokenIFramesTicks = 10;
        public int zanzokenCooldownTicks = 40;
        public boolean zanzokenGhostAfterimage = true;
        public Integer zanzokenGhostFadeMode;
        public Float zanzokenGhostAlpha;
        public int zanzokenRingClones = 6;
        public double zanzokenRingRadius = 3.0;
        public int zanzokenRingTicks = 200;
        public boolean multiFormEnabled = true;
        public int multiFormBodies = 4;
        public float multiFormKiCost = 60.0f;
        public float sonicSwayStaminaCost = 6.0f;
        public int sonicSwayIFramesTicks = 8;
        public int sonicSwayCooldownTicks = 18;
        public float ultimateKiCost = 35.0f;
        public float ultimateDamageScale = 2.4f;
        public int ultimateCooldownTicks = 200;
        public float hakaiKiCost = 60.0f;
        public double hakaiMaxRange = 15.0;
        public int hakaiCooldownTicks = 600;
        public int hakaiChannelTicks = 40;
        public Float hakaiPoiseFraction;
        public Double hakaiMoveInterruptDistance;
        public float sparkingBuildPerHit = 6.0f;
        public float sparkingBuildOnHurt = 3.0f;
        public int sparkingDurationTicks = 200;
        public int sparkingChargeTicks = 100;
        public float sparkingDamageMult = 1.35f;
        public Boolean sparkingFromKiCharge;
        public Boolean zanzokenConfusesAi;
        public int zanzokenAfterimageTicks = 40;
        public int statMaxOverride;
        public Boolean xenoSlotTechniquesEnabled;
        public int sparkingReleaseLimit = 225;
        public int sparkingCooldownTicks = 200;
        public float sparkingMoveSpeedMult = 1.25f;
        public float sparkingAttackSpeedMult = 1.35f;
        public float sparkingEndEnergyFraction = 0.02f;
        public float transformImpactRadius = 3.5f;
        public float transformImpactKnock = 0.45f;
        public float chaseSuccessChance = 1.0f;
        public float guardDamageReduction = 0.55f;
        public float guardStaminaPerHit = 8.0f;
        public float guardStaminaPerSec = 3.0f;
        public int guardBreakStunTicks = 25;
        public int superCounterWindowTicks = 12;
        public float superCounterKiCost = 10.0f;
        public float superCounterDamageScale = 1.35f;
        public float kiBlastCancelKiCost = 6.0f;
        public float kiBlastCancelDamageScale = 0.85f;
        public boolean kiBlastHoldToFire = true;
        public int kiBlastCooldownTicks = 32;
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
        public float kiProjectileMaxSize = 320.0f;
        public float kiProjectileMaxSpeed = 32.0f;
        public float kiOverchargeSpeedPerPercent = 0.004f;
        public boolean kiFullGameplayScaling = false;
        public float kiDestructionMaxRadius = 32.0f;
        public int kiDestructionBlocksPerTick = 4096;
        public double vanishMaxRange = 7.0;
        public double chaseMaxRange = 0.0;
        public double chaseFlightSpeed = 1.2;
        public int chaseFlightTimeoutTicks = 400;
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
        public float kickKnockbackScale = 1.0f;
        public int comboLaunchKickEvery = 5;
        public int comboMashIntervalTicks = 6;
        public int comboAnimGeneration = 4;
        public double vanishNearField = 0.0;
        public boolean vanishOpenSpotSearch = false;
        public float comboLauncherUp = 1.85f;
        public float comboLauncherHoriz = 0.55f;
        public double vanishGap = 1.35;
        public double vanishSide = 1.05;
        public double chaseStopGap = 0.0;
        public int dmzStructureY = 0;
        public int dmzStructureYOffset = 0;
        public boolean dmzStructureMaster = true;
        public int maxComboSteps = 5;
        public int chargeMaxTicks = 28;
        public boolean yawpKiGriefingEnabled = true;
        public boolean sneakToPickup = true;
        public boolean allowKiWithItemInHand = true;
        public boolean kiDeflectEnabled = true;
        public float kiDeflectReach = 3.0f;
        public float kiDeflectAimDot = 0.2f;
        public float kiDeflectSpeedScale = 1.15f;
        public float kiDeflectMinSpeed = 0.8f;
        public float kiDeflectDamageScale = 1.25f;
        public float kiDeflectStaminaCost = 4.0f;
        public int kiDeflectCooldownTicks = 4;
        public boolean beamSurgeEnabled = true;
        public float beamSurgeKiPerTick = 1.6f;
        public float beamSurgeStaminaPerTick = 0.5f;
        public float beamSurgeCostGrowth = 1.0f;
        public float beamSurgeSizeGain = 9.0f;
        public float beamSurgeDamageGain = 1.5f;
        public float beamSurgeReachGain = 0.8f;
        public float beamSurgeSearchRadius = 30.0f;
        public float beamSurgeCeiling = 0.45f;
        public float beamSurgeCeilingPerMastery = 0.183f;
        public float beamSurgeRampPerTick = 0.020f;
        public float beamSurgeRampPerMastery = 0.010f;
        public float beamSurgeMaxLength = 192.0f;
        public int barrageExtraTicks1 = 20;
        public int barrageExtraTicks2 = 45;
        public int barrageExtraTicks3 = 80;
        public int barrageDurationTicks = 0;
        public int kiDurationTicks = 0;
        public java.util.Map<String, Integer> kiDurationByType = new java.util.LinkedHashMap<>();
        public int barrageCooldownTicks = 0;
        public int guidanceControlRange = 0;
        public float guidanceTurnRate = 0.0f;
        public float guidanceCameraRate = 0.38f;
        public int guidanceHoldGraceTicks = 8;
        public float guidanceLookRayMin = 8.0f;
        public int barrageCooldownReduce1 = 20;
        public int barrageCooldownReduce2 = 40;
        public int barrageCooldownReduce3 = 60;
        public float barrageKiPerTick = 0.4f;
        public boolean sparkingAuraEnabled = true;
        public float sparkingAuraDensity = 1.0f;
        // Null (absent from an older config file) means "use the defaults"; see apply().
        public java.util.List<String> yawpPlayerKiFlags = null;
        public java.util.List<String> yawpMobKiFlags = null;
        /** Boxed so an absent key keeps the default instead of resetting to 0. */
        public Double masterKiGriefRadius = null;
        public boolean chargeOverchargeEnabled = true;
        public float chargeOverchargeMaxPercent = 1000.0f;
        public int chargeOverchargeMinLevel = 0;
        public float chargeOverchargeSizePerPercent = 0.004f;
        public float chargeOverchargeSpeedPerPercent = 0.001f;
        public float chargeOverchargeMaxDamageScale = 4.0f;
        public float chargeFormSizeFactor = 0.25f;
        public float chargeFormSizeLogCap = 4.0f;
        public Map<String, Float> chargeFormSizeByForm = new LinkedHashMap<>();
        public boolean chargeOverchargeGriefEnabled = false;
        public float chargeOverchargeCraterMinPercent = 250.0f;
        public int chargeOverchargeCraterMaxRadius = 4;
        public int chargeOverchargeCraterIntervalTicks = 10;
        public int chargeOverchargeMaxRocks = 4;
        public boolean chargeOverchargeDiskSliceEnabled = true;
        public boolean chargeOverchargeCameraEnabled = true;
        public boolean chargeOverchargeVoicesEnabled = false;
    }
}
