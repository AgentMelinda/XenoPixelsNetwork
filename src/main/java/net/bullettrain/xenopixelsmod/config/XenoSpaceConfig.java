package net.bullettrain.xenopixelsmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.space.chart.ChartDefinition;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.math.BodyLod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 * Tuning for the Xenoverse Space groundwork, written to {@code config/xenopixelsmod-space.json}.
 *
 * <p>Follows the convention every other config in this mod uses — {@code public} state plus Gson and a
 * nested {@code Data} DTO, loaded from {@code XenoPixelsMod#commonSetup} — and specifically mirrors
 * {@link XenoPerfConfig}: a {@code version} field so a default can be changed later without silently
 * overwriting what an operator chose, clamping inside {@link #apply} so a typo cannot produce a value
 * that takes the physics tick with it, and a {@link #statusLine()} for command output. Nothing here
 * uses NeoForge's {@code ModConfigSpec}, because nothing else in this repo does and two config systems
 * in one mod is worse than either.
 *
 * <p>§19 of the space design calls for every constant to be a config key. This file holds the
 * <b>performance and survival</b> subset that the groundwork actually reads; per-planet values such as
 * {@code exit_altitude} live on the chart entries and in the {@code .yml} sidecars (§17, §24), which
 * come in a later phase. {@code void_damage} is the one per-planet value that is already live, because
 * the void rule reads it straight off the chart row that binds the dimension.
 *
 * <p>Values are read through accessors rather than exposed as fields, because several of them feed
 * per-tick and per-frame loops where a half-clamped value would be a crash or a hang rather than a
 * cosmetic oddity. The accessor is the single place the clamp is guaranteed to have been applied.
 */
public final class XenoSpaceConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-space.json");
    /** Version 1 is the initial groundwork schema; version 2 adds the live LOD driver interval;
     * version 3 adds the opt-in {@code fieldGravity} ship-force switch; version 4 adds the void-damage
     * survival rule (§19.1) and the dimension set it is allowed to apply to; version 5 adds the
     * altitude handoff (§3, §19.3) and folds the shipped {@code xenopixelsmod:voidsea} host into the
     * version-4 default dimension set; version 9 exposes Sable chunk-bounds batching for body builds; version 10 adds
     * bounded small-planet physicalization; version 11 gates it on the incremental section writer; version 12 adds
     * a shared per-tick generated-body voxel budget; version 14 caps asteroid candidate-scan cell work per pass;
     * version 15 separates deterministic small-planet field visibility from physicalization; version 16 adds
     * an opt-in shared build-step time allowance; version 17 adds a default-off Sable allocation phase probe;
     * version 19 makes the shared generated-body voxel ceiling default-on with an explicit toggle. */
    private static final int CURRENT_VERSION = GeneratedBodyBuildPolicy.CONFIG_VERSION;

    /**
     * Master switch. Passes re-read it on entry. The altitude pass is always registered for live
     * enabling; other physics passes still require space enabled at startup.
     */
    private static boolean enabled = false;

    // --- Physics pass budget ---
    /**
     * Work units one physics tick may spend across all space passes before the rest are deferred to the
     * next tick, round-robin. Zero disables budgeting and lets every due pass run, which is what a
     * single-player world with a handful of bodies wants; a busy server sets a real number.
     */
    private static int workPerPhysicsTick = 0;
    /** Physics ticks between gravity field summations. Twenty matches OrbitalGravitySystem's own cadence. */
    private static int gravityIntervalTicks = 20;
    /** How many bodies actually contribute to a summed field (§5's top-3 rule). */
    private static int gravityContributors = 3;
    /** Distance beyond which a body's field is not evaluated at all (§21's interaction range). */
    private static double fieldRange = 100_000.0;

    // --- Orbital motion ---
    /**
     * §20's {@code space.gravity.real_orbits}. Ships off until the Q11 precision spike passes, because
     * until then a handoff landing on a body that has moved since the client drew it is possible.
     */
    private static boolean realOrbits = false;
    /**
     * §20 time warp: {@code /xenospace orbits warp <seconds>} adds to this persisted epoch shift, which the chart adds to
     * game time before solving Kepler's equation. Every server and client evaluates the same formula, so a warp moves the
     * whole system forward without breaking determinism. Off unless {@link #orbitWarpAllowed}.
     */
    private static boolean orbitWarpAllowed = false;
    /**
     * Owner request 2026-10-16: the Voidsea should not have a limited height. When on (default off until seen working):
     * Sable sub-levels in the Voidsea are only removed beyond +-{@link #VOIDSEA_HEIGHT_LIMIT} instead of Sable's own
     * sub_level_remove_min/max (-10,000 / 100,000; SableVoidseaHeightMixin), and a player below the Voidsea floor is no
     * longer returned to an anchor - fall damage is still cancelled - until they pass the same limit. Blocks keep the
     * dimension's 0..1024 build height (Minecraft caps blocks at 4,064; Sable stores bodies in Voidsea chunks, so the
     * dimension height is not changed). The limit stays far below Sable's +-2.1e9 empty-bounds sentinel, so the
     * extreme-Y guard still removes bodies with broken (empty) bounds.
     */
    private static boolean voidseaUnboundedHeight = false;
    /** Design-doc 15 black holes (BlackHoleSystem): tidal pull, escape meter, horizon flush. Default off. */
    private static boolean blackHolesEnabled = false;
    /** Oxygen is used this many times faster inside a black hole tidal band (new, unvalidated default). */
    private static int blackHoleOxygenFactor = 3;
    /** Design-doc 16 Super Dragon Balls v1 (CosmicDragonBalls): scatter, radar, wish. Default off. */
    private static boolean cosmicDragonBallsEnabled = false;
    /** Dimensions the seven balls scatter across (around each dimension's spawn). */
    private static java.util.List<String> cosmicDragonBallHosts = java.util.List.of("minecraft:overworld");
    /** Scatter radius around the host spawn, in blocks (balls land between 20% and 100% of it). */
    private static int cosmicDragonBallRadius = 3000;
    /** In-game days the balls stay dormant after a wish. */
    private static int cosmicDragonBallDormantDays = 3;
    /**
     * Design-doc 16b/22 planet Hakai (PlanetHakai): an area Hakai fired at a charted body erases it from the chart.
     * {@code off} (default), {@code erasable_only} (only bodies whose chart JSON says {@code "erasable": true}) or
     * {@code all} (every body except {@link #hakaiPlanetProtected}).
     */
    private static String hakaiPlanets = "off";
    /** Bodies planet Hakai never erases. Earth (the handoff anchor) is listed by default; remove it to allow erasing Earth. */
    private static java.util.List<String> hakaiPlanetProtected = java.util.List.of("xenopixelsmod:earth");
    /** Permission level needed to erase a planet with Hakai (0 = anyone who can cast area Hakai). */
    private static int hakaiPlanetPermissionLevel = 2;
    /** How far along the caster's look a planet can be targeted, in blocks (the Voidsea only). */
    private static double hakaiPlanetReach = 20_000;
    /** Ticks the planet burns in Hakai before it is gone. */
    private static int hakaiPlanetEraseTicks = 100;
    /** Minutes until an erased planet returns by itself; 0 = only by admin restore (or a wish, later). */
    private static int hakaiPlanetRestoreMinutes = 0;
    /** Players inside an erased planet's dimension are thrown out into the Voidsea where it was. */
    private static boolean hakaiPlanetEjectOccupants = true;
    public static final double VOIDSEA_HEIGHT_LIMIT = 30_000_000.0;
    /**
     * Growing universe (space/universe, default "off"): "timer" reveals one more star system per game day along the frontier
     * from Sol, "discovery" reveals a system when a player flies into its cell (at most universeDiscoveriesPerDay), "both"
     * does both; growth stops at universeMaxRevealed. Systems are computed from the world seed; only what is revealed is
     * stored. universeWarp lets {@code /xenospace universe warp} move a player to a revealed system (travel by both flight
     * and warp, owner decision 2026-10-16).
     */
    private static String universeGrowth = "off";
    private static int universeMaxRevealed = 512, universeDiscoveriesPerDay = 8;
    private static boolean universeWarp = false;
    private static boolean logicalUniverseEnabled = false;
    private static double orbitEpochOffsetSeconds = 0.0;

    // --- Physical planets: station keeping ---
    /**
     * Spring (1/s²), damping (1/s) and acceleration ceiling (blocks/s²) of the controller that keeps each
     * managed physical planet on its orbit (or pinned station when {@link #realOrbits} is off). The ceiling
     * is what lets ships tow a planet: thrust above {@code ceiling × mass} wins, and once released the
     * spring brings the planet back. Defaults are near critical damping (damping ≈ 2·√spring).
     */
    private static double orbitHoldStiffness = 0.02;
    private static double orbitHoldDamping = 0.28;
    private static double orbitHoldMaxAccel = 0.5;
    /**
     * Release return. While something pushes a planet (a ship, a rope, a physics staff) the hold values
     * above apply. Once no outside acceleration above {@link #orbitHeldThreshold} (blocks/s²) has been seen
     * for {@link #orbitReleaseTicks}, the planet is released and flies home with the stiffer return gains,
     * capped at {@link #orbitReturnMaxSpeed} blocks/s. {@code orbitReturnBoost=false} keeps the single
     * hold controller.
     */
    private static boolean orbitReturnBoost = true;
    private static double orbitReturnStiffness = 0.08;
    private static double orbitReturnDamping = 0.57;
    private static double orbitReturnMaxAccel = 3.0;
    private static double orbitReturnMaxSpeed = 60.0;
    private static double orbitHeldThreshold = 0.35;
    private static int orbitReleaseTicks = 40;
    /** A released planet farther than this from its target is teleported back; 0 disables. */
    private static double orbitRecoverDistance = 4096.0;
    /** Fraction of a released planet's spin removed per second. */
    private static double planetSpinDamping = 0.5;
    /** Remove Simulated physics-staff locks from managed planets, so a lock never pins a planet. */
    private static boolean planetReleaseStaffLock = true;
    /**
     * Which charted bodies are physical Sable shells in the Voidsea: {@code all} (every planet, moon and
     * the Sun), {@code planets} (all but stars; the Sun stays a visual sphere) or {@code earth_moon} (the
     * S9 behaviour). Earth is physical in every mode. {@code /xenospace planets physical <mode>} switches
     * live and deletes the shells a smaller mode drops.
     */
    private static String physicalPlanets = "proximity";
    /**
     * {@code proximity} mode: a charted body becomes physical when a Voidsea player is within
     * {@code activate + radius} of its chart position and is released beyond {@code release + radius}.
     * Checked every {@code proximityIntervalTicks}; at most one body is built per check. Earth stays physical.
     */
    private static double proximityActivateRange = 1500.0;
    private static double proximityReleaseRange = 2200.0;
    private static int proximityIntervalTicks = 20;
    /**
     * On-demand planet builds install at most about this many voxels per server tick (whole chunk columns, so
     * one column may exceed it). 0 builds each body in a single tick. Logged: one-call installs took up to
     * 1.9 s for the Sun's 478k voxels; batched installs measured about 135 voxels per ms (478424 in 3531 ms), so 5000 is roughly 37 ms per tick.
     */
    private static int planetBuildVoxelsPerTick = 5000;
    /**
     * Experimental (S11): before the first install, anchor the plot rotation point at the shell centre, and teleport
     * the finished body onto its requested centre. Needed for batched builds to land in place; under investigation
     * because every run with it on ended in a multi-second native Rapier step. Off = the A1 behaviour.
     */
    private static boolean planetBuildPoseFix = true;
    /**
     * Experiment E5 (S11): after a plot-load build, run the reference mod's mass/pose/bounds finalization plus
     * updateLastPose. Off until a fresh test passes.
     */
    private static boolean planetBuildFinalize = false;
    /** Remove unmanaged Voidsea sub-levels made only of planet_shell blocks (orphaned planet fragments). */
    private static boolean planetOrphanCleanup = true;
    /** Fix Sable 2.0.3's unload ticket lookup key (SableTicketUnloadKeyMixin). Off until proven at runtime. */
    private static boolean sableTicketUnloadKeyFix = false;
    /** Skip Sable's split heat-map for planet shells and planets under a batched build (SablePlanetHeatMapSkipMixin). */
    private static boolean planetSkipSplitHeatMap = false;
    /** Skip Sable's per-tick world chunk sweep for planet bodies only (SablePlanetChunkTicketSkipMixin). */
    private static boolean planetSkipChunkTickets = false;
    /** Batched build installs the centre column and the four extreme columns first, so the body's bounds are final at once. */
    private static boolean planetBuildFrameFirst = false;
    /** Repeated plot.load builds skip Sable's per-load mass/stats finalize except on the last batch; ignored by the incremental-section writer. */
    private static boolean planetBuildDeferFinalize = false;
    /** Coalesce Sable's per-new-chunk plot-bounds scan to the completed body-build batch boundary. Default off until a
     * gated runtime run shows it working (owner rule: new paths stay switchable and off; the proven path stays on). */
    private static boolean planetBuildCoalesceChunkBounds = false;
    /** Before a batched planet build completes, move other bodies out of its shell band (they would be embedded). */
    private static boolean planetBuildEvictIntruders = false;
    /** S11 B1: send the deterministic asteroid field (seed + xenospace/belt belts) to Voidsea clients. */
    private static boolean asteroidBelts = false;
    /** S11 B3: build belt asteroids as shared Sable bodies near Voidsea players (needs asteroidBelts). */
    private static boolean asteroidPhysical = false;
    private static double asteroidActivateRange = 420.0, asteroidReleaseRange = 650.0;
    private static int asteroidMaxPerPlayer = 6, asteroidMaxGlobal = 64, asteroidSpawnIntervalTicks = 10,
            asteroidBuildVoxelsPerTick = 2000, asteroidScanCellsPerPass = 4096;
    /** S11 C2/C3 physicalization gate and hard operator caps. Kept off until runtime/save-load proof passes. */
    private static boolean smallPlanetPhysical = false;
    private static boolean smallPlanetRadialGravity = true;
    /** Bounded deterministic small-planet far field; independent of expensive near-player Sable body construction. */
    private static boolean smallPlanetFieldEnabled = true;
    private static double smallPlanetActivateRange = 420.0, smallPlanetReleaseRange = 650.0;
    private static int smallPlanetMaxPerPlayer = 2, smallPlanetMaxGlobal = 8, smallPlanetSpawnIntervalTicks = 40,
            smallPlanetBuildVoxelsPerTick = 512;
    /** Shared exact voxel-count ceiling across all generated-body owners; turned off only by its explicit operator toggle. */
    private static boolean generatedBodyBuildBudgetEnabled = true;
    private static int generatedBodyBuildVoxelsPerTick = 2_000;
    /** Optional total milliseconds per server tick spent in generated-body install steps; 0 disables this secondary budget. */
    private static double generatedBodyBuildMillisPerTick = 0.0;
    /** Temporary diagnostics for Sable's allocation observer calls; no timings are taken while false. */
    private static boolean sableAllocationPhaseProbe = false;
    private static boolean smallPlanetBuildIncrementalSections = false;
    /** Experimental bounded Sable writer; remains off until fresh runtime/save-load acceptance passes. */
    private static boolean asteroidBuildIncrementalSections = false;
    /**
     * S11 B4 fracture (implementation owned by Codex; keys here). Off until runtime tests pass. Sources: the fragment
     * minimum (8 blocks) and fragment lifetime (60 s) come from the approved S11 plan; every other value is a new,
     * UNVALIDATED operator-tunable default (no existing config or reference-mod number exists) and must be re-tuned once
     * Sable's velocity update timing is verified (Codex C-039).
     */
    private static boolean asteroidFracture = false;
    /**
     * Design-doc §6 oxygen (default off). In vacuum (the Voidsea host, a listed space dimension, a charted body whose JSON
     * says "breathable": false, or above a body's exit altitude) a player first uses Breathing Capsule supply, then holds
     * their breath for oxygenBreathSeconds, then pays ki each second, and only when ki runs out takes damage each second
     * (a soft fail, never a one-hit kill). Inside a ship sub-level counts as pressurized when oxygenShipsPressurized is on.
     * All numbers are new, unvalidated gameplay defaults; the 30 s first phase comes from §6 ("Phase 1 (0-30 s)").
     */
    private static boolean oxygenEnabled = false, oxygenShipsPressurized = true;
    private static int oxygenBreathSeconds = 30, oxygenCapsuleSeconds = 300, oxygenReserveCapSeconds = 900;
    private static double oxygenKiPerSecond = 5.0, oxygenDamagePerSecond = 1.0, oxygenRefillPerSecond = 10.0;
    /** Change of an asteroid's linear speed within one tick that counts as an impact, in blocks/second (new default). */
    private static double asteroidFractureDeltaV = 6.0;
    /**
     * Half-width of the fracture cut slab around its plane, in blocks (Euclidean, unit normal). Sable 2.0.3's split flood fill
     * uses 18 neighbours (SubLevelHeatMapManager.DIRECTION_OFFSETS: 6 faces + 12 edges, javap), so the removed slab must leave
     * no 18-tunnel. A "standard" digital plane (thickness = L1 norm of the normal) separates even 26-connected sets; for a unit
     * normal that is a half-width of |n|1/2 <= sqrt(3)/2, so 0.87 separates for every cut direction. 0.5 (one block) can leave
     * diagonal tunnels on oblique planes and then nothing splits. Reasoning from digital-plane theory, not runtime-tested.
     */
    private static double asteroidFractureSlabHalfWidth = 0.87;
    private static int asteroidFractureCooldownTicks = 40, asteroidMaxFragmentsPerAsteroid = 8, asteroidMaxFragmentsGlobal = 128,
            asteroidFragmentMinBlocks = 8, asteroidFragmentLifetimeTicks = 1200;
    /** On-demand chart planets are never stored by Sable and are rebuilt from the chart (SablePlanetEphemeralSaveMixin). */
    private static boolean planetEphemeral = false;
    /**
     * A managed body recorded in its ledger but not restored by Sable at startup (its stored record was lost, e.g. a
     * crash during a save) is rebuilt instead of waited for forever.
     */
    private static boolean planetRecoverLost = false;
    private static List<String> physicalPlanetExclude = List.of("xenospace_spike:surface");
    /**
     * Dimensions where Sable's physics chunk tickets are switched off (SableIgnoreChunksDimensionMixin), as if
     * their datapack physics record had ignore_chunks. Bodies there stop colliding with ordinary world blocks.
     */
    private static List<String> ignoreChunkPhysicsDimensions = List.of();
    private static int physicalPlanetMaxRadius = 200;
    /**
     * How a planet shell is built: {@code atomic} (in-memory plot installed in one call; safe with players
     * nearby) or {@code blocks} (the S9/S10 path: place every voxel in the world, then assemble).
     */
    private static String planetAssembly = "atomic";

    /**
     * How a descent from the Voidsea enters a body: {@code surface} (touching the body's sphere, within
     * {@link #handoffSurfaceMargin} blocks of it) or {@code altitude} (the earlier rule: inside the
     * body's horizontal column and at or below its exit altitude).
     */
    private static String handoffEntry = "surface";
    private static double handoffSurfaceMargin = 2.0;

    /**
     * §5's live chart-gravity consumer switch. Off by default and deliberately separate from
     * {@code enabled}: a server may run the LOD ladder for measurement without altering ship physics.
     * When on, {@code SpaceGravityConsumer} adds each {@code simulates()} body's pull to ships in the
     * chart-space host as an <b>extra</b> impulse — it never touches the per-ship {@code gravitySi}
     * that {@code OrbitalGravitySystem}/{@code ShipGravityControl} own, so flight degrades to exactly
     * today's behavior when this is off.
     */
    private static boolean fieldGravity = false;

    // --- Survival (§19.1) ---
    /**
     * Whether falling out of a <b>space</b> dimension kills. Off by default, and off means
     * {@code SpaceVoidSafety} cancels the {@code out_of_world} damage and returns the faller to the
     * body's {@code handoff_anchor_y} — a cancelled fall with no floor underneath is an infinite fall,
     * so the two halves of this rule are not optional relative to each other.
     *
     * <p>Only dimensions the chart binds (or one of {@link #spaceDimensions}) are eligible; an
     * unbound dimension keeps vanilla void damage so the overworld, nether and End are untouched.
     */
    private static boolean voidDamage = false;

    /**
     * The mod's own Voidsea host: the one dimension this repo ships as data
     * ({@code data/xenopixelsmod/dimension/voidsea.json}, {@code height 1024}) rather than as an example
     * pack, so the handoff has somewhere to put a player without the operator writing a dimension first.
     */
    public static final ResourceLocation DEFAULT_HANDOFF_HOST =
            ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "voidsea");

    /**
     * What an operator gets for an absent, empty, or wholly malformed list. A hand-edited typo must not
     * leave the server with no space dimensions at all, which would silently switch both the void rule
     * and the planets off in the one dimension built to test them.
     */
    private static final Set<ResourceLocation> DEFAULT_SPACE_DIMENSIONS =
            Set.of(DEFAULT_HANDOFF_HOST, ResourceLocation.fromNamespaceAndPath("xenospace_spike", "orbit"));

    /**
     * The dimensions §19.1 calls <b>space</b>, in addition to every dimension bound to a chart body. Two
     * things read this: the void rule is allowed to protect them, and a player flying one counts as an
     * observer to the LOD ladder, so the planets charted around them get promoted to something drawn.
     *
     * <p>Needed because the shipped spike host {@code xenospace_spike:orbit} has no chart entry of its
     * own, and a rule or a planet list that keys only off chart bindings silently does nothing in the one
     * dimension people actually fly in.
     *
     * <p>Held as resource locations: both consumers run per damage event and per ladder pass, where
     * re-parsing the authored strings would allocate on a path §5 of the perf work made allocation-free.
     */
    private static Set<ResourceLocation> spaceDimensions = DEFAULT_SPACE_DIMENSIONS;

    // --- Altitude handoff (§3, §19.3) ---
    /**
     * §19.3's {@code handoff.enabled}. Off by default and deliberately separate from {@code enabled}, so
     * a server can run the ladder and the void rule — both read-only about where you are — before
     * switching on the one space feature that <em>moves</em> players between dimensions.
     */
    private static boolean handoffEnabled = false;
    private static Map<String, Double> handoffExitOverrides = Map.of();
    /**
     * The Voidsea host — the dimension a player leaving a charted body arrives in, and the one they
     * depart from to come back down. Chart rows name the dimension a body <em>is</em>; this names the
     * dimension space <em>is</em>, which is not a body and so has no chart row of its own.
     */
    private static ResourceLocation handoffHost = DEFAULT_HANDOFF_HOST;
    /**
     * §19.3's {@code band_thickness}: the height of the handoff window. This slice has no dual-host
     * window (§3's ideal is both levels loaded at once), so the band is used for what it can do alone —
     * an outbound arrival is placed half a band above the body's exit altitude, which is the distance a
     * player has to actually descend in the host before the mirror trigger fires them back down. Without
     * it, an arrival exactly on its own trigger altitude oscillates: transfer, arrive, transfer again.
     */
    private static double handoffBandThickness = 128.0;
    /**
     * How close to a body's chart column a descending player has to be, in blocks of horizontal chart
     * distance, for the descent to hand them into that body instead of leaving them in deep space. Used as
     * a floor — a body's own column is {@code max(2 x radius_blocks, this)} — so a gas giant is grabbier
     * than a moon without the operator retuning per body, and a 512 default comfortably covers Earth's
     * authored 100-block radius and Namek's 84.
     */
    private static double handoffReach = 512.0;
    /**
     * Game ticks between altitude sweeps by {@code SpaceHandoffPass}. Four hands a climbing player a
     * half-second boundary, which is below the distance a DMZ flight crosses in that time at cruise, so
     * the trigger is met inside the band rather than several hundred blocks past it; the same four ticks
     * are why an idle server pays one player walk per sweep.
     */
    private static int handoffIntervalTicks = 4;
    /**
     * §19.3's {@code handoff.velocity_carryover}. Vanilla clears {@code DeltaMovement} on a dimension
     * change, so this decides whether the pass re-applies the pre-transfer motion after the teleport.
     * Carrying it is what makes the crossing read as continuous flight instead of a stop; switching it
     * off leaves the player stationary on arrival, which is the safer thing to debug from.
     */
    private static boolean velocityCarryover = true;
    /**
     * Whether a player handed into a floorless space dimension is protected from vanilla's anti-fly
     * watchdog while they are airborne in it. Needed because that watchdog kicks a non-spectator survival
     * player who is {@code clientIsFloating} for more than {@code ceil(80 * 0.08 / gravity)} ticks —
     * exactly 80 at vanilla gravity, about four seconds, which any orbit fall reaches long before the
     * void does. {@code SpaceFlightGuard} exempts them with a Levitation effect at amplifier −1 rather
     * than a {@code mayfly} grant: both clear the watchdog's conjunct, but {@code Abilities} is saved
     * with the player, so a live {@code mayfly} grant still present at logout persists and hands a
     * survival player double-tap-jump flight everywhere, forever. The effect expires, is not persisted,
     * and at amplifier −1 applies no upward force. A player who already has flight is left alone.
     */
    private static boolean flightGrant = true;

    // --- Level-of-detail ladder (§21) ---
    private static int proxyBudget = 32;
    /**
     * Physics ticks between live LOD-ladder resolutions by {@code SpaceLodDriver}. Ten keeps a
     * hovering ship's promotions within half a second while costing one flat array walk per
     * interval; every hysteresis threshold is radius-scaled, so the interval, not the distance,
     * is what bounds the ladder's reaction time.
     */
    private static int lodIntervalTicks = 10;
    private static double lodProxyFactor = 900.0;
    private static double lodFieldFactor = 400.0;
    private static double lodAnchorFactor = 60.0;
    private static double lodChunkFactor = 1.05;
    private static double lodHysteresisMargin = 0.25;

    // --- Physical planet spike (§11) ---
    /**
     * The largest voxel radius {@code /xenospace physics-spike create} may assemble. The voxel count grows
     * as r³ — 123 at 3, ~2.1k at 8, ~17k at 16 — and the pre-flight air scan, the block placement and the
     * assembly all run on the server thread, so this is a ceiling on a hand command rather than a knob to
     * tune up. The default leaves room for a body you can walk around on without a visible stall.
     */
    private static int maxSpikeRadius = 8;

    // --- Measurement ---
    /** Per-pass timings for {@code /xenospace perf}. On by default: it is two long writes per pass. */
    private static boolean perfProbeEnabled = true;
    private static JsonObject bodyDefaults = new JsonObject();

    private XenoSpaceConfig() {}

    public static boolean enabled() { return enabled; }
    public static int workPerPhysicsTick() { return workPerPhysicsTick; }
    public static int gravityIntervalTicks() { return gravityIntervalTicks; }
    public static int gravityContributors() { return gravityContributors; }
    public static double fieldRange() { return fieldRange; }
    public static boolean realOrbits() { return realOrbits; }
    public static boolean orbitWarpAllowed() { return orbitWarpAllowed; }
    public static boolean voidseaUnboundedHeight() { return voidseaUnboundedHeight; }
    public static boolean blackHolesEnabled() { return blackHolesEnabled; }
    public static int blackHoleOxygenFactor() { return blackHoleOxygenFactor; }
    public static boolean cosmicDragonBallsEnabled() { return cosmicDragonBallsEnabled; }
    public static java.util.List<String> cosmicDragonBallHosts() { return cosmicDragonBallHosts; }
    public static int cosmicDragonBallRadius() { return cosmicDragonBallRadius; }
    public static int cosmicDragonBallDormantDays() { return cosmicDragonBallDormantDays; }
    public static String hakaiPlanets() { return hakaiPlanets; }
    public static java.util.List<String> hakaiPlanetProtected() { return hakaiPlanetProtected; }
    public static int hakaiPlanetPermissionLevel() { return hakaiPlanetPermissionLevel; }
    public static double hakaiPlanetReach() { return hakaiPlanetReach; }
    public static int hakaiPlanetEraseTicks() { return hakaiPlanetEraseTicks; }
    public static int hakaiPlanetRestoreMinutes() { return hakaiPlanetRestoreMinutes; }
    public static boolean hakaiPlanetEjectOccupants() { return hakaiPlanetEjectOccupants; }
    /** {@code /xenospace hakai protect|unprotect}; persisted. Returns false when nothing changed. */
    public static boolean setHakaiPlanetProtected(String id, boolean protect) {
        var next = new java.util.ArrayList<>(hakaiPlanetProtected);
        boolean changed = protect ? !next.contains(id) && next.add(id) : next.remove(id);
        if (!changed) return false;
        hakaiPlanetProtected = java.util.List.copyOf(next);
        save();
        return true;
    }
    /** Live switch for {@code /xenospace hakai mode}; persisted. */
    public static void setHakaiPlanets(String mode) {
        hakaiPlanets = net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiRules.Mode.parse(mode).key();
        save();
    }
    public static String universeGrowth() { return universeGrowth; }
    public static int universeMaxRevealed() { return universeMaxRevealed; }
    public static int universeDiscoveriesPerDay() { return universeDiscoveriesPerDay; }
    public static boolean universeWarp() { return universeWarp; }
    public static boolean logicalUniverseEnabled() { return logicalUniverseEnabled; }
    /** Persisted master switch for generating and recording full-width logical star-system coordinates. */
    public static void setLogicalUniverseEnabled(boolean enabled) { logicalUniverseEnabled = enabled; save(); }
    /** Live switch for {@code /xenospace universe growth <mode>}; persisted. */
    public static void setUniverseGrowth(String mode) {
        universeGrowth = net.bullettrain.xenopixelsmod.space.universe.UniverseGrowth.Mode.parse(mode).name().toLowerCase(java.util.Locale.ROOT);
        save();
    }
    public static double orbitEpochOffsetSeconds() { return orbitEpochOffsetSeconds; }

    /** Live switch for {@code /xenospace orbits on|off}; persisted. Callers re-apply it to the live chart. */
    public static void setRealOrbits(boolean on) { realOrbits = on; save(); }

    /** Adds to the persisted §20 epoch shift (seconds may be negative to rewind); returns the new total. */
    public static double addOrbitEpochOffset(double seconds) {
        double next = orbitEpochOffsetSeconds + seconds;
        if (!Double.isFinite(next) || Math.abs(next) > 1e12) throw new IllegalArgumentException("epoch offset out of range");
        orbitEpochOffsetSeconds = next;
        save();
        return next;
    }

    public static void resetOrbitEpochOffset() { orbitEpochOffsetSeconds = 0.0; save(); }
    public static double orbitHoldStiffness() { return orbitHoldStiffness; }
    public static double orbitHoldDamping() { return orbitHoldDamping; }
    public static double orbitHoldMaxAccel() { return orbitHoldMaxAccel; }
    public static boolean orbitReturnBoost() { return orbitReturnBoost; }
    public static double orbitReturnStiffness() { return orbitReturnStiffness; }
    public static double orbitReturnDamping() { return orbitReturnDamping; }
    public static double orbitReturnMaxAccel() { return orbitReturnMaxAccel; }
    public static double orbitReturnMaxSpeed() { return orbitReturnMaxSpeed; }
    public static double orbitHeldThreshold() { return orbitHeldThreshold; }
    public static int orbitReleaseTicks() { return orbitReleaseTicks; }
    public static double orbitRecoverDistance() { return orbitRecoverDistance; }
    public static double planetSpinDamping() { return planetSpinDamping; }
    public static boolean planetReleaseStaffLock() { return planetReleaseStaffLock; }
    public static String physicalPlanets() { return physicalPlanets; }
    public static java.util.Set<String> physicalPlanetExclude() { return java.util.Set.copyOf(physicalPlanetExclude); }
    public static boolean ignoresPhysicsChunks(String dimension) { return ignoreChunkPhysicsDimensions.contains(dimension); }
    public static int physicalPlanetMaxRadius() { return physicalPlanetMaxRadius; }
    public static boolean atomicPlanetAssembly() { return !"blocks".equals(planetAssembly); }
    public static String physicalPlanetsMode(String value) {
        String mode = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return mode.equals("planets") || mode.equals("earth_moon") || mode.equals("all") ? mode : "proximity";
    }
    public static boolean proximityPlanets() { return "proximity".equals(physicalPlanets); }
    public static double proximityActivateRange() { return proximityActivateRange; }
    public static double proximityReleaseRange() { return proximityReleaseRange; }
    public static int proximityIntervalTicks() { return proximityIntervalTicks; }
    public static int planetBuildVoxelsPerTick() { return planetBuildVoxelsPerTick; }
    /** S11 batched-build test (debug only, not saved): changes the per-tick voxel budget for this server run. */
    public static void debugSetPlanetBuildVoxelsPerTick(int voxels) { planetBuildVoxelsPerTick = Math.max(0, Math.min(10_000_000, voxels)); }
    public static boolean planetBuildPoseFix() { return planetBuildPoseFix; }
    public static boolean planetBuildFinalize() { return planetBuildFinalize; }
    public static boolean planetOrphanCleanup() { return planetOrphanCleanup; }
    public static boolean sableTicketUnloadKeyFix() { return sableTicketUnloadKeyFix; }
    public static boolean planetSkipSplitHeatMap() { return planetSkipSplitHeatMap; }
    public static boolean planetSkipChunkTickets() { return planetSkipChunkTickets; }
    public static boolean planetBuildFrameFirst() { return planetBuildFrameFirst; }
    public static boolean planetBuildDeferFinalize() { return planetBuildDeferFinalize; }
    /** S11 diagnostic switch; changing it affects builds started after the change. */
    public static void setPlanetBuildDeferFinalize(boolean active) { planetBuildDeferFinalize = active; save(); }
    public static boolean planetBuildCoalesceChunkBounds() { return planetBuildCoalesceChunkBounds; }
    public static boolean planetBuildEvictIntruders() { return planetBuildEvictIntruders; }
    public static boolean asteroidBelts() { return asteroidBelts; }
    public static boolean asteroidPhysical() { return asteroidPhysical; }
    public static double asteroidActivateRange() { return asteroidActivateRange; }
    public static double asteroidReleaseRange() { return asteroidReleaseRange; }
    public static int asteroidMaxPerPlayer() { return asteroidMaxPerPlayer; }
    public static int asteroidMaxGlobal() { return asteroidMaxGlobal; }
    public static int asteroidSpawnIntervalTicks() { return asteroidSpawnIntervalTicks; }
    public static int asteroidBuildVoxelsPerTick() { return asteroidBuildVoxelsPerTick; }
    public static int asteroidScanCellsPerPass() { return asteroidScanCellsPerPass; }
    public static boolean asteroidBuildIncrementalSections() { return asteroidBuildIncrementalSections; }
    public static boolean smallPlanetPhysical() { return smallPlanetPhysical; }
    public static boolean smallPlanetRadialGravity() { return smallPlanetRadialGravity; }
    public static boolean smallPlanetGravityEnabled() {
        return enabled && smallPlanetPhysical && smallPlanetRadialGravity;
    }
    public static boolean smallPlanetFieldEnabled() { return smallPlanetFieldEnabled; }
    public static double smallPlanetActivateRange() { return smallPlanetActivateRange; }
    public static double smallPlanetReleaseRange() { return smallPlanetReleaseRange; }
    public static int smallPlanetMaxPerPlayer() { return smallPlanetMaxPerPlayer; }
    public static int smallPlanetMaxGlobal() { return smallPlanetMaxGlobal; }
    public static int smallPlanetSpawnIntervalTicks() { return smallPlanetSpawnIntervalTicks; }
    public static int smallPlanetBuildVoxelsPerTick() { return smallPlanetBuildVoxelsPerTick; }
    public static boolean smallPlanetBuildIncrementalSections() { return smallPlanetBuildIncrementalSections; }
    public static boolean generatedBodyBuildBudgetEnabled() { return generatedBodyBuildBudgetEnabled; }
    public static int generatedBodyBuildVoxelsPerTick() {
        return generatedBodyBuildBudgetEnabled ? generatedBodyBuildVoxelsPerTick : 0;
    }
    public static void setGeneratedBodyBuildBudgetEnabled(boolean active) {
        generatedBodyBuildBudgetEnabled = active;
        save();
    }
    public static double generatedBodyBuildMillisPerTick() { return generatedBodyBuildMillisPerTick; }
    public static boolean sableAllocationPhaseProbe() { return sableAllocationPhaseProbe; }
    public static void setSmallPlanetPhysical(boolean active) { smallPlanetPhysical = active; save(); }
    public static void setSmallPlanetRadialGravity(boolean active) { smallPlanetRadialGravity = active; save(); }
    public static void setSmallPlanetFieldEnabled(boolean active) { smallPlanetFieldEnabled = active; save(); }
    public static void setSmallPlanetBuildIncrementalSections(boolean active) { smallPlanetBuildIncrementalSections = active; save(); }
    public static boolean asteroidFracture() { return asteroidFracture; }
    public static boolean oxygenEnabled() { return oxygenEnabled; }
    public static boolean oxygenShipsPressurized() { return oxygenShipsPressurized; }
    public static int oxygenBreathSeconds() { return oxygenBreathSeconds; }
    public static int oxygenCapsuleSeconds() { return oxygenCapsuleSeconds; }
    public static int oxygenReserveCapSeconds() { return oxygenReserveCapSeconds; }
    public static double oxygenKiPerSecond() { return oxygenKiPerSecond; }
    public static double oxygenDamagePerSecond() { return oxygenDamagePerSecond; }
    public static double oxygenRefillPerSecond() { return oxygenRefillPerSecond; }
    public static double asteroidFractureDeltaV() { return asteroidFractureDeltaV; }
    public static double asteroidFractureSlabHalfWidth() { return asteroidFractureSlabHalfWidth; }
    public static int asteroidFractureCooldownTicks() { return asteroidFractureCooldownTicks; }
    public static int asteroidMaxFragmentsPerAsteroid() { return asteroidMaxFragmentsPerAsteroid; }
    public static int asteroidMaxFragmentsGlobal() { return asteroidMaxFragmentsGlobal; }
    /** Fragments with fewer blocks than this are not kept as bodies (S11 plan: "pieces under 8 blocks"). */
    public static int asteroidFragmentMinBlocks() { return asteroidFragmentMinBlocks; }
    /** Small fragments vanish after this many ticks (S11 plan: 60 s). */
    public static int asteroidFragmentLifetimeTicks() { return asteroidFragmentLifetimeTicks; }
    public static boolean planetEphemeral() { return planetEphemeral; }
    public static boolean planetRecoverLost() { return planetRecoverLost; }
    public static void setPhysicalPlanets(String mode) {
        physicalPlanets = physicalPlanetsMode(mode);
        save();
    }
    public static boolean surfaceEntry() { return !"altitude".equals(handoffEntry); }
    public static double handoffSurfaceMargin() { return handoffSurfaceMargin; }
    public static boolean fieldGravity() { return fieldGravity; }
    public static boolean voidDamage() { return voidDamage; }
    public static Set<ResourceLocation> spaceDimensions() { return spaceDimensions; }
    public static boolean handoffEnabled() { return handoffEnabled; }
    public static Map<String, Double> handoffExitOverrides() { return handoffExitOverrides; }

    /**
     * Chart body ids are namespaced ("xenopixelsmod:earth"); a bare key such as "earth" used to parse as a valid
     * ResourceLocation yet never match a body, so the override was silently ignored (Codex CL-123 test). Bare keys now mean
     * this mod's namespace.
     */
    static String bodyKey(String key) {
        String trimmed = key.trim();
        return trimmed.indexOf(':') < 0 ? net.bullettrain.xenopixelsmod.XenoPixelsMod.MOD_ID + ":" + trimmed : trimmed;
    }

    /** Call only after the live host/body bounds have been validated. */
    public static void setHandoffExit(String body, double y) {
        body = bodyKey(body);
        if (ResourceLocation.tryParse(body) == null || !Double.isFinite(y) || Math.abs(y) > 30_000_000)
            throw new IllegalArgumentException("invalid body or exit altitude");
        Map<String, Double> next = new HashMap<>(handoffExitOverrides);
        next.put(body, y);
        handoffExitOverrides = Map.copyOf(next);
        save();
    }
    public static ResourceLocation handoffHost() { return handoffHost; }
    public static double handoffBandThickness() { return handoffBandThickness; }
    public static double handoffReach() { return handoffReach; }
    public static int handoffIntervalTicks() { return handoffIntervalTicks; }
    public static boolean velocityCarryover() { return velocityCarryover; }
    public static boolean flightGrant() { return flightGrant; }
    /** The ceiling on {@code /xenospace physics-spike create}, in voxels. */
    public static int maxSpikeRadius() { return maxSpikeRadius; }

    /**
     * What the spike may actually build for a requested radius, clamped into
     * {@code [1, }{@link #maxSpikeRadius()}{@code ]}. Command input goes through here so the ceiling is
     * enforced in one place rather than in the command's argument parser.
     */
    public static int spikeRadius(int requested) {
        return Math.max(1, Math.min(maxSpikeRadius, requested));
    }
    public static int proxyBudget() { return proxyBudget; }
    public static int lodIntervalTicks() { return lodIntervalTicks; }
    public static boolean perfProbeEnabled() { return perfProbeEnabled; }
    public static JsonObject bodyDefaults() { return bodyDefaults.deepCopy(); }

    /**
     * Live switch for {@code /xenospace voiddamage}. Persisted immediately, because the void rule is
     * re-read per damage event rather than cached, so there is no restart and nothing to invalidate.
     */
    public static void setVoidDamage(boolean damageApplies) {
        voidDamage = damageApplies;
        save();
    }

    /**
     * Live switch for {@code /xenospace handoff}. Persisted immediately, because the pass re-reads it on
     * entry rather than caching it, so switching it off takes effect on the next sweep and on needs only
     * that space.enabled is also on. The altitude pass is always registered at start.
     */
    public static void setHandoffEnabled(boolean active) {
        handoffEnabled = active;
        save();
    }

    /** Live switch for {@code /xenospace handoff velocity}. */
    public static void setVelocityCarryover(boolean carry) {
        velocityCarryover = carry;
        save();
    }

    /** Live switch for {@code /xenospace handoff flight}, the anti-fly-watchdog exemption (§19.1's kick fix). */
    public static void setFlightGrant(boolean grants) {
        flightGrant = grants;
        save();
    }

    /**
     * Live ceiling for {@code /xenospace physics-spike create}, clamped through the same bounds the config
     * load uses so a command cannot install a value a restart would reject.
     */
    public static void setMaxSpikeRadius(int radius) {
        maxSpikeRadius = Math.max(1, Math.min(32, radius));
        save();
    }

    /**
     * The LOD ladder as one value object, clamped. Built on demand rather than cached, because it is
     * read once per chart rebuild and once per LOD resolution pass — not per body — and caching it
     * would need an invalidation hook on every setter for no measurable gain.
     *
     * @return thresholds guaranteed to satisfy {@link BodyLod.Thresholds}' ordering contract, falling
     *         back to the defaults for any key an operator set out of order
     */
    public static BodyLod.Thresholds thresholds() {
        try {
            return new BodyLod.Thresholds(lodProxyFactor, lodFieldFactor, lodAnchorFactor,
                    lodChunkFactor, lodHysteresisMargin);
        } catch (IllegalArgumentException e) {
            // An out-of-order ladder in a hand-edited file must not stop the server booting or leave
            // the chart without a ladder; the defaults are known-consistent.
            XenoPixelsMod.LOGGER.warn("Space LOD thresholds are inconsistent ({}); using defaults", e.getMessage());
            return BodyLod.Thresholds.defaults();
        }
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null) apply(data);
        } catch (IOException | RuntimeException e) {
            // A malformed file must not stop the server booting; keep the defaults, as every other
            // config in this mod does.
            XenoPixelsMod.LOGGER.warn("Failed to load space config; using defaults", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(snapshot(), writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save space config", e);
        }
    }

    public static Data snapshot() {
        Data d = new Data();
        d.version = CURRENT_VERSION;
        d.enabled = enabled;
        d.workPerPhysicsTick = workPerPhysicsTick;
        d.gravityIntervalTicks = gravityIntervalTicks;
        d.gravityContributors = gravityContributors;
        d.fieldRange = fieldRange;
        d.realOrbits = realOrbits;
        d.orbitWarpAllowed = orbitWarpAllowed; d.orbitEpochOffsetSeconds = orbitEpochOffsetSeconds;
        d.voidseaUnboundedHeight = voidseaUnboundedHeight;
        d.blackHolesEnabled = blackHolesEnabled; d.blackHoleOxygenFactor = blackHoleOxygenFactor;
        d.cosmicDragonBallsEnabled = cosmicDragonBallsEnabled; d.cosmicDragonBallHosts = new java.util.ArrayList<>(cosmicDragonBallHosts);
        d.cosmicDragonBallRadius = cosmicDragonBallRadius; d.cosmicDragonBallDormantDays = cosmicDragonBallDormantDays;
        d.hakaiPlanets = hakaiPlanets; d.hakaiPlanetProtected = new java.util.ArrayList<>(hakaiPlanetProtected);
        d.hakaiPlanetPermissionLevel = hakaiPlanetPermissionLevel; d.hakaiPlanetReach = hakaiPlanetReach;
        d.hakaiPlanetEraseTicks = hakaiPlanetEraseTicks; d.hakaiPlanetRestoreMinutes = hakaiPlanetRestoreMinutes;
        d.hakaiPlanetEjectOccupants = hakaiPlanetEjectOccupants;
        d.universeGrowth = universeGrowth; d.universeMaxRevealed = universeMaxRevealed;
        d.universeDiscoveriesPerDay = universeDiscoveriesPerDay; d.universeWarp = universeWarp;
        d.logicalUniverseEnabled = logicalUniverseEnabled;
        d.orbitHoldStiffness = orbitHoldStiffness;
        d.orbitHoldDamping = orbitHoldDamping;
        d.orbitHoldMaxAccel = orbitHoldMaxAccel;
        d.orbitReturnBoost = orbitReturnBoost;
        d.orbitReturnStiffness = orbitReturnStiffness;
        d.orbitReturnDamping = orbitReturnDamping;
        d.orbitReturnMaxAccel = orbitReturnMaxAccel;
        d.orbitReturnMaxSpeed = orbitReturnMaxSpeed;
        d.orbitHeldThreshold = orbitHeldThreshold;
        d.orbitReleaseTicks = orbitReleaseTicks;
        d.orbitRecoverDistance = orbitRecoverDistance;
        d.planetSpinDamping = planetSpinDamping;
        d.planetReleaseStaffLock = planetReleaseStaffLock;
        d.physicalPlanets = physicalPlanets;
        d.proximityActivateRange = proximityActivateRange;
        d.proximityReleaseRange = proximityReleaseRange;
        d.proximityIntervalTicks = proximityIntervalTicks;
        d.planetBuildVoxelsPerTick = planetBuildVoxelsPerTick;
        d.planetBuildPoseFix = planetBuildPoseFix;
        d.planetBuildFinalize = planetBuildFinalize;
        d.planetOrphanCleanup = planetOrphanCleanup;
        d.sableTicketUnloadKeyFix = sableTicketUnloadKeyFix;
        d.planetSkipSplitHeatMap = planetSkipSplitHeatMap;
        d.planetSkipChunkTickets = planetSkipChunkTickets;
        d.planetBuildFrameFirst = planetBuildFrameFirst;
        d.planetBuildDeferFinalize = planetBuildDeferFinalize;
        d.planetBuildCoalesceChunkBounds = planetBuildCoalesceChunkBounds;
        d.planetBuildEvictIntruders = planetBuildEvictIntruders;
        d.asteroidBelts = asteroidBelts;
        d.asteroidPhysical = asteroidPhysical;
        d.asteroidActivateRange = asteroidActivateRange; d.asteroidReleaseRange = asteroidReleaseRange;
        d.asteroidMaxPerPlayer = asteroidMaxPerPlayer; d.asteroidMaxGlobal = asteroidMaxGlobal;
        d.asteroidSpawnIntervalTicks = asteroidSpawnIntervalTicks; d.asteroidBuildVoxelsPerTick = asteroidBuildVoxelsPerTick;
        d.asteroidScanCellsPerPass = asteroidScanCellsPerPass;
        d.asteroidBuildIncrementalSections = asteroidBuildIncrementalSections;
        d.smallPlanetPhysical = smallPlanetPhysical;
        d.smallPlanetRadialGravity = smallPlanetRadialGravity;
        d.smallPlanetFieldEnabled = smallPlanetFieldEnabled;
        d.smallPlanetActivateRange = smallPlanetActivateRange; d.smallPlanetReleaseRange = smallPlanetReleaseRange;
        d.smallPlanetMaxPerPlayer = smallPlanetMaxPerPlayer; d.smallPlanetMaxGlobal = smallPlanetMaxGlobal;
        d.smallPlanetSpawnIntervalTicks = smallPlanetSpawnIntervalTicks;
        d.smallPlanetBuildVoxelsPerTick = smallPlanetBuildVoxelsPerTick;
        d.smallPlanetBuildIncrementalSections = smallPlanetBuildIncrementalSections;
        d.generatedBodyBuildBudgetEnabled = generatedBodyBuildBudgetEnabled;
        d.generatedBodyBuildVoxelsPerTick = generatedBodyBuildVoxelsPerTick;
        d.generatedBodyBuildMillisPerTick = generatedBodyBuildMillisPerTick;
        d.sableAllocationPhaseProbe = sableAllocationPhaseProbe;
        d.asteroidFracture = asteroidFracture; d.asteroidFractureDeltaV = asteroidFractureDeltaV;
        d.oxygenEnabled = oxygenEnabled; d.oxygenShipsPressurized = oxygenShipsPressurized;
        d.oxygenBreathSeconds = oxygenBreathSeconds; d.oxygenCapsuleSeconds = oxygenCapsuleSeconds;
        d.oxygenReserveCapSeconds = oxygenReserveCapSeconds; d.oxygenKiPerSecond = oxygenKiPerSecond;
        d.oxygenDamagePerSecond = oxygenDamagePerSecond; d.oxygenRefillPerSecond = oxygenRefillPerSecond;
        d.asteroidFractureSlabHalfWidth = asteroidFractureSlabHalfWidth;
        d.asteroidFractureCooldownTicks = asteroidFractureCooldownTicks; d.asteroidMaxFragmentsPerAsteroid = asteroidMaxFragmentsPerAsteroid;
        d.asteroidMaxFragmentsGlobal = asteroidMaxFragmentsGlobal; d.asteroidFragmentMinBlocks = asteroidFragmentMinBlocks;
        d.asteroidFragmentLifetimeTicks = asteroidFragmentLifetimeTicks;
        d.planetEphemeral = planetEphemeral;
        d.planetRecoverLost = planetRecoverLost;
        d.physicalPlanetExclude = List.copyOf(physicalPlanetExclude);
        d.ignoreChunkPhysicsDimensions = List.copyOf(ignoreChunkPhysicsDimensions);
        d.physicalPlanetMaxRadius = physicalPlanetMaxRadius;
        d.planetAssembly = planetAssembly;
        d.handoffEntry = handoffEntry;
        d.handoffSurfaceMargin = handoffSurfaceMargin;
        d.fieldGravity = fieldGravity;
        d.voidDamage = voidDamage;
        d.spaceDimensions = spaceDimensions.stream().map(ResourceLocation::toString).sorted().toList();
        d.handoffEnabled = handoffEnabled;
        d.handoffExitOverrides = new HashMap<>(handoffExitOverrides);
        d.handoffHost = handoffHost.toString();
        d.handoffBandThickness = handoffBandThickness;
        d.handoffReach = handoffReach;
        d.handoffIntervalTicks = handoffIntervalTicks;
        d.velocityCarryover = velocityCarryover;
        d.flightGrant = flightGrant;
        d.maxSpikeRadius = maxSpikeRadius;
        d.proxyBudget = proxyBudget;
        d.lodIntervalTicks = lodIntervalTicks;
        d.lodProxyFactor = lodProxyFactor;
        d.lodFieldFactor = lodFieldFactor;
        d.lodAnchorFactor = lodAnchorFactor;
        d.lodChunkFactor = lodChunkFactor;
        d.lodHysteresisMargin = lodHysteresisMargin;
        d.perfProbeEnabled = perfProbeEnabled;
        d.bodyDefaults = bodyDefaults.deepCopy();
        return d;
    }

    /**
     * Applies and clamps a snapshot. Every bound here exists because an unbounded version of that key
     * would hang or crash rather than merely look wrong: a zero interval means a pass that is due every
     * tick forever, a negative contributor count means an empty field, a zero hysteresis margin means a
     * ladder that oscillates on its own boundary.
     */
    public static void apply(Data d) {
        if (d == null) return;
        JsonObject defaults = d.bodyDefaults == null ? new JsonObject() : d.bodyDefaults;
        ChartDefinition.keys(defaults, "kind", "radius_blocks", "gravity", "handoff_anchor_y",
                "exit_altitude", "orbital_motion", "erasable", "danger_rating", "void_damage",
                "sky_style");
        ChartDefinition.parse("xenopixelsmod:defaults", defaults);
        bodyDefaults = defaults.deepCopy();
        enabled = d.enabled;
        // 0 = unbudgeted; otherwise a real ceiling. Capped so a typo cannot make the pass unbounded
        // in the other direction and starve Sable's own work.
        workPerPhysicsTick = d.workPerPhysicsTick <= 0 ? 0 : Math.min(d.workPerPhysicsTick, 1_000_000);
        gravityIntervalTicks = Math.max(1, Math.min(1200, d.gravityIntervalTicks));
        gravityContributors = Math.max(1, Math.min(32, d.gravityContributors));
        fieldRange = !Double.isFinite(d.fieldRange) ? 100_000.0
                : d.fieldRange <= 0.0 ? Double.MAX_VALUE : Math.min(d.fieldRange, 30_000_000.0);
        realOrbits = d.realOrbits;
        orbitWarpAllowed = d.orbitWarpAllowed;
        voidseaUnboundedHeight = d.voidseaUnboundedHeight;
        blackHolesEnabled = d.blackHolesEnabled;
        blackHoleOxygenFactor = Math.max(1, Math.min(100, d.blackHoleOxygenFactor));
        cosmicDragonBallsEnabled = d.cosmicDragonBallsEnabled;
        var hosts = new java.util.ArrayList<String>();
        if (d.cosmicDragonBallHosts != null) for (String host : d.cosmicDragonBallHosts)
            if (host != null && net.minecraft.resources.ResourceLocation.tryParse(host) != null && hosts.size() < 16) hosts.add(host);
        cosmicDragonBallHosts = hosts.isEmpty() ? java.util.List.of("minecraft:overworld") : java.util.List.copyOf(hosts);
        cosmicDragonBallRadius = Math.max(64, Math.min(1_000_000, d.cosmicDragonBallRadius));
        cosmicDragonBallDormantDays = Math.max(0, Math.min(3650, d.cosmicDragonBallDormantDays));
        hakaiPlanets = net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiRules.Mode.parse(d.hakaiPlanets).key();
        var protectedIds = new java.util.ArrayList<String>();
        if (d.hakaiPlanetProtected != null) for (String id : d.hakaiPlanetProtected) {
            String key = id == null ? null : (id.contains(":") ? id : "xenopixelsmod:" + id);
            if (key != null && net.minecraft.resources.ResourceLocation.tryParse(key) != null && !protectedIds.contains(key) && protectedIds.size() < 256)
                protectedIds.add(key);
        }
        hakaiPlanetProtected = java.util.List.copyOf(protectedIds);
        hakaiPlanetPermissionLevel = Math.max(0, Math.min(4, d.hakaiPlanetPermissionLevel));
        hakaiPlanetReach = Double.isFinite(d.hakaiPlanetReach) ? Math.max(16, Math.min(1_000_000, d.hakaiPlanetReach)) : 20_000;
        hakaiPlanetEraseTicks = Math.max(1, Math.min(20 * 60, d.hakaiPlanetEraseTicks));
        hakaiPlanetRestoreMinutes = Math.max(0, Math.min(60 * 24 * 365, d.hakaiPlanetRestoreMinutes));
        hakaiPlanetEjectOccupants = d.hakaiPlanetEjectOccupants;
        universeGrowth = net.bullettrain.xenopixelsmod.space.universe.UniverseGrowth.Mode.parse(d.universeGrowth).name().toLowerCase(java.util.Locale.ROOT);
        universeMaxRevealed = Math.max(1, Math.min(200_000, d.universeMaxRevealed));
        universeDiscoveriesPerDay = Math.max(0, Math.min(1000, d.universeDiscoveriesPerDay));
        universeWarp = d.universeWarp;
        logicalUniverseEnabled = d.logicalUniverseEnabled;
        orbitEpochOffsetSeconds = Double.isFinite(d.orbitEpochOffsetSeconds) && Math.abs(d.orbitEpochOffsetSeconds) <= 1e12
                ? d.orbitEpochOffsetSeconds : 0.0;
        orbitHoldStiffness = clampOrdered(d.orbitHoldStiffness, 0.0, 10.0, 0.02);
        orbitHoldDamping = clampOrdered(d.orbitHoldDamping, 0.0, 20.0, 0.28);
        orbitHoldMaxAccel = clampOrdered(d.orbitHoldMaxAccel, 0.0, 100.0, 0.5);
        orbitReturnBoost = d.orbitReturnBoost;
        orbitReturnStiffness = clampOrdered(d.orbitReturnStiffness, 0.0, 10.0, 0.08);
        orbitReturnDamping = clampOrdered(d.orbitReturnDamping, 0.0, 20.0, 0.57);
        orbitReturnMaxAccel = clampOrdered(d.orbitReturnMaxAccel, 0.0, 100.0, 3.0);
        orbitReturnMaxSpeed = clampOrdered(d.orbitReturnMaxSpeed, 0.1, 1000.0, 60.0);
        orbitHeldThreshold = clampOrdered(d.orbitHeldThreshold, 0.0, 100.0, 0.35);
        orbitReleaseTicks = Math.max(1, Math.min(1200, d.orbitReleaseTicks));
        orbitRecoverDistance = clampOrdered(d.orbitRecoverDistance, 0.0, 30_000_000.0, 4096.0);
        planetSpinDamping = clampOrdered(d.planetSpinDamping, 0.0, 20.0, 0.5);
        planetReleaseStaffLock = d.planetReleaseStaffLock;
        physicalPlanets = physicalPlanetsMode(d.physicalPlanets);
        // Version 7 targets multiplayer: "all" (the v6 default) keeps every shell loaded at all times, so a
        // file still on it moves to proximity. An explicit earth_moon/planets choice is kept.
        if (d.version != null && d.version < 7 && "all".equals(physicalPlanets)) physicalPlanets = "proximity";
        proximityActivateRange = clampOrdered(d.proximityActivateRange, 64.0, 1_000_000.0, 1500.0);
        proximityReleaseRange = Math.max(proximityActivateRange + 64.0,
                clampOrdered(d.proximityReleaseRange, 64.0, 1_000_000.0, 2200.0));
        proximityIntervalTicks = Math.max(1, Math.min(1200, d.proximityIntervalTicks));
        planetBuildVoxelsPerTick = Math.max(0, Math.min(10_000_000, d.planetBuildVoxelsPerTick));
        planetBuildPoseFix = d.planetBuildPoseFix;
        planetBuildFinalize = d.planetBuildFinalize;
        planetOrphanCleanup = d.planetOrphanCleanup;
        sableTicketUnloadKeyFix = d.sableTicketUnloadKeyFix;
        planetSkipSplitHeatMap = d.planetSkipSplitHeatMap;
        planetSkipChunkTickets = d.planetSkipChunkTickets;
        planetBuildFrameFirst = d.planetBuildFrameFirst;
        planetBuildDeferFinalize = d.planetBuildDeferFinalize;
        planetBuildCoalesceChunkBounds = d.planetBuildCoalesceChunkBounds;
        planetBuildEvictIntruders = d.planetBuildEvictIntruders;
        asteroidBelts = d.asteroidBelts;
        asteroidPhysical = d.asteroidPhysical;
        asteroidActivateRange = clampOrdered(d.asteroidActivateRange, 32.0, 4096.0, 420.0);
        asteroidReleaseRange = Math.max(asteroidActivateRange + 32.0, clampOrdered(d.asteroidReleaseRange, 64.0, 8192.0, 650.0));
        asteroidMaxPerPlayer = Math.max(0, Math.min(64, d.asteroidMaxPerPlayer));
        asteroidMaxGlobal = Math.max(0, Math.min(512, d.asteroidMaxGlobal));
        asteroidSpawnIntervalTicks = Math.max(1, Math.min(1200, d.asteroidSpawnIntervalTicks));
        // The incremental writer uses this as an exact voxel cap; keep an operator-specified small slice
        // possible, and prevent a hand-edited config from turning one tick into an unbounded bulk install.
        asteroidBuildVoxelsPerTick = Math.max(1, Math.min(2_000, d.asteroidBuildVoxelsPerTick));
        asteroidScanCellsPerPass = Math.max(1, Math.min(65_536, d.asteroidScanCellsPerPass));
        asteroidBuildIncrementalSections = d.asteroidBuildIncrementalSections;
        smallPlanetPhysical = d.smallPlanetPhysical;
        smallPlanetRadialGravity = d.smallPlanetRadialGravity;
        smallPlanetFieldEnabled = d.smallPlanetFieldEnabled;
        smallPlanetActivateRange = clampOrdered(d.smallPlanetActivateRange, 32.0, 4_096.0, 420.0);
        smallPlanetReleaseRange = Math.max(smallPlanetActivateRange + 32.0,
                clampOrdered(d.smallPlanetReleaseRange, 64.0, 8_192.0, 650.0));
        smallPlanetMaxPerPlayer = Math.max(0, Math.min(16, d.smallPlanetMaxPerPlayer));
        smallPlanetMaxGlobal = Math.max(0, Math.min(32, d.smallPlanetMaxGlobal));
        smallPlanetSpawnIntervalTicks = Math.max(1, Math.min(1_200, d.smallPlanetSpawnIntervalTicks));
        smallPlanetBuildVoxelsPerTick = Math.max(1, Math.min(2_000, d.smallPlanetBuildVoxelsPerTick));
        smallPlanetBuildIncrementalSections = d.smallPlanetBuildIncrementalSections;
        asteroidFracture = d.asteroidFracture;
        oxygenEnabled = d.oxygenEnabled; oxygenShipsPressurized = d.oxygenShipsPressurized;
        oxygenBreathSeconds = Math.max(1, Math.min(3600, d.oxygenBreathSeconds));
        oxygenCapsuleSeconds = Math.max(1, Math.min(36_000, d.oxygenCapsuleSeconds));
        oxygenReserveCapSeconds = Math.max(oxygenCapsuleSeconds, Math.min(360_000, d.oxygenReserveCapSeconds));
        oxygenKiPerSecond = clampOrdered(d.oxygenKiPerSecond, 0.0, 100_000.0, 5.0);
        oxygenDamagePerSecond = clampOrdered(d.oxygenDamagePerSecond, 0.0, 1000.0, 1.0);
        oxygenRefillPerSecond = clampOrdered(d.oxygenRefillPerSecond, 0.1, 1000.0, 10.0);
        asteroidFractureDeltaV = clampOrdered(d.asteroidFractureDeltaV, 0.5, 200.0, 6.0);
        asteroidFractureSlabHalfWidth = clampOrdered(d.asteroidFractureSlabHalfWidth, 0.5, 4.0, 0.87);
        asteroidFractureCooldownTicks = Math.max(1, Math.min(1200, d.asteroidFractureCooldownTicks));
        asteroidMaxFragmentsPerAsteroid = Math.max(0, Math.min(64, d.asteroidMaxFragmentsPerAsteroid));
        asteroidMaxFragmentsGlobal = Math.max(0, Math.min(2048, d.asteroidMaxFragmentsGlobal));
        asteroidFragmentMinBlocks = Math.max(1, Math.min(512, d.asteroidFragmentMinBlocks));
        asteroidFragmentLifetimeTicks = Math.max(20, Math.min(72_000, d.asteroidFragmentLifetimeTicks));
        planetEphemeral = d.planetEphemeral;
        planetRecoverLost = d.planetRecoverLost;
        ignoreChunkPhysicsDimensions = d.ignoreChunkPhysicsDimensions == null ? List.of()
                : d.ignoreChunkPhysicsDimensions.stream().filter(java.util.Objects::nonNull).map(String::trim).toList();
        physicalPlanetExclude = d.physicalPlanetExclude == null ? List.of()
                : d.physicalPlanetExclude.stream().filter(java.util.Objects::nonNull).map(String::trim).toList();
        physicalPlanetMaxRadius = Math.max(4, Math.min(400, d.physicalPlanetMaxRadius));
        // A non-incremental batch completes one whole plot chunk column. Keep enough headroom for the largest
        // configured chart planet plus that one-column completion, or its builder could remain stalled forever.
        // 0 explicitly restores the pre-shared-cap local manager behavior. Positive caps may be as low as 1;
        // indivisible plot.load groups switch to the exact voxel writer when they cannot fit.
        var buildPolicy = GeneratedBodyBuildPolicy.resolve(d.version, d.generatedBodyBuildVoxelsPerTick,
                d.generatedBodyBuildBudgetEnabled);
        generatedBodyBuildBudgetEnabled = buildPolicy.enabled();
        generatedBodyBuildVoxelsPerTick = buildPolicy.voxelsPerTick();
        generatedBodyBuildMillisPerTick = Double.isFinite(d.generatedBodyBuildMillisPerTick)
                ? Math.max(0.0, Math.min(20.0, d.generatedBodyBuildMillisPerTick)) : 0.0;
        sableAllocationPhaseProbe = d.sableAllocationPhaseProbe;
        planetAssembly = "blocks".equalsIgnoreCase(String.valueOf(d.planetAssembly).trim()) ? "blocks" : "atomic";
        handoffEntry = "altitude".equalsIgnoreCase(String.valueOf(d.handoffEntry).trim()) ? "altitude" : "surface";
        handoffSurfaceMargin = clampOrdered(d.handoffSurfaceMargin, 0.0, 64.0, 2.0);
        fieldGravity = d.fieldGravity;
        proxyBudget = Math.max(0, Math.min(256, d.proxyBudget));
        // Version 6 charts the whole solar system and the DMZ worlds (about 15 bodies): a file still on the
        // old default of 12 would silently drop planets from the sky, so it moves to the new default.
        if (d.version != null && d.version < 6 && d.proxyBudget == 12) proxyBudget = 32;
        // A missing key (version-1 file) decodes to 0; both 0 and a negative typo map to the
        // default, because an interval of zero would resolve the ladder every tick and one tick is
        // already the floor every other interval key uses.
        lodIntervalTicks = d.lodIntervalTicks <= 0 ? 10 : Math.min(1200, d.lodIntervalTicks);
        // Clamped independently rather than chained, because the ladder's ordering constraint
        // (proxy > field > anchor > chunk) is a relationship between four keys, not a bound on any one
        // of them. thresholds() is what detects an inconsistent combination and falls back to the
        // defaults with a warning, so a hand-edited file can warn without breaking the chart.
        lodProxyFactor = clampOrdered(d.lodProxyFactor, 1.0, 10_000_000.0, 900.0);
        lodFieldFactor = clampOrdered(d.lodFieldFactor, 1.0, 10_000_000.0, 400.0);
        lodAnchorFactor = clampOrdered(d.lodAnchorFactor, 0.001, 10_000_000.0, 60.0);
        lodChunkFactor = clampOrdered(d.lodChunkFactor, 0.001, 10_000_000.0, 1.05);
        lodHysteresisMargin = !Double.isFinite(d.lodHysteresisMargin) || d.lodHysteresisMargin <= 0.0
                ? 0.25 : Math.min(d.lodHysteresisMargin, 10.0);
        perfProbeEnabled = d.version == null || d.version >= 1 ? d.perfProbeEnabled : true;
        voidDamage = d.voidDamage;
        handoffEnabled = d.handoffEnabled;
        Map<String, Double> exits = new HashMap<>();
        if (d.handoffExitOverrides != null) d.handoffExitOverrides.forEach((raw, y) -> {
            String id = raw == null ? null : bodyKey(raw);
            if (id != null && !id.equals(raw))
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn("handoffExitOverrides key '{}' has no namespace; using '{}'", raw, id);
            if (id != null && ResourceLocation.tryParse(id) != null && y != null
                    && Double.isFinite(y) && Math.abs(y) <= 30_000_000) exits.put(id, y);
        });
        handoffExitOverrides = Map.copyOf(exits);
        handoffHost = safeHost(d.handoffHost);
        // A version-4 file has none of these keys, so Gson leaves each at its own Data default and the
        // guards below only catch explicit typos: a band of zero would put an arrival exactly on its own
        // mirror trigger and oscillate, and a zero interval would sweep every tick.
        handoffBandThickness = !Double.isFinite(d.handoffBandThickness) || d.handoffBandThickness <= 0.0
                ? 128.0 : Math.min(d.handoffBandThickness, 4096.0);
        handoffIntervalTicks = d.handoffIntervalTicks <= 0 ? 4 : Math.min(1200, d.handoffIntervalTicks);
        handoffReach = !Double.isFinite(d.handoffReach) || d.handoffReach <= 0.0
                ? 512.0 : Math.min(d.handoffReach, 100_000.0);
        velocityCarryover = d.velocityCarryover;
        flightGrant = d.flightGrant;
        spaceDimensions = withHost(safeDimensions(d.spaceDimensions));
        // 1..32 voxels: the r³ cost is the reason this is clamped rather than trusted.
        maxSpikeRadius = Math.max(1, Math.min(32, d.maxSpikeRadius));
    }

    /**
     * The handoff host as an id, falling back to {@link #DEFAULT_HANDOFF_HOST} for a typo or a blank.
     * The dimension itself is never validated here — an unresolvable host makes the handoff refuse to
     * transfer and say so in {@code /xenospace handoff status}, which is cheaper at config time than
     * holding a server reference just to look a level up.
     */
    private static ResourceLocation safeHost(String id) {
        if (id == null || id.isBlank()) return DEFAULT_HANDOFF_HOST;
        try {
            ResourceLocation parsed = ResourceLocation.tryParse(ChartDefinition.id(id.trim()));
            return parsed == null ? DEFAULT_HANDOFF_HOST : parsed;
        } catch (IllegalArgumentException e) {
            XenoPixelsMod.LOGGER.warn("Ignoring malformed space handoff host '{}'", id);
            return DEFAULT_HANDOFF_HOST;
        }
    }

    /**
     * Makes sure the handoff host is called space even by a hand-edited list that left it out. A
     * destination that is not space is protected by no void rule and gets no LOD promotions, which is a
     * silently half-working handoff rather than a visible mistake.
     */
    private static Set<ResourceLocation> withHost(Set<ResourceLocation> listed) {
        if (listed.contains(handoffHost)) return listed;
        Set<ResourceLocation> widened = new LinkedHashSet<>(listed);
        widened.add(handoffHost);
        return Set.copyOf(widened);
    }

    /**
     * Keeps only well-formed namespaced ids from a hand-edited dimension list. A typo here must not
     * take the config load down the way {@link #apply} would if it propagated: one bad id costs one
     * dimension its space rules, not the whole file its defaults.
     */
    private static Set<ResourceLocation> safeDimensions(List<String> ids) {
        if (ids == null || ids.isEmpty()) return DEFAULT_SPACE_DIMENSIONS;
        Set<ResourceLocation> kept = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null) continue;
            try {
                ResourceLocation parsed = ResourceLocation.tryParse(ChartDefinition.id(id.trim()));
                if (parsed != null) kept.add(parsed);
            } catch (IllegalArgumentException e) {
                XenoPixelsMod.LOGGER.warn("Ignoring malformed space dimension '{}'", id);
            }
        }
        return kept.isEmpty() ? DEFAULT_SPACE_DIMENSIONS : Set.copyOf(kept);
    }

    /** Clamps to {@code [min, max]}, substituting {@code fallback} for a non-finite or below-min value. */
    private static double clampOrdered(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value) || value < min) return fallback;
        return Math.min(value, max);
    }

    public static String statusLine() {
        return "space=" + (enabled ? "on" : "off")
                + " realOrbits=" + realOrbits
                + " orbitHold=" + orbitHoldStiffness + "/" + orbitHoldDamping + "/" + orbitHoldMaxAccel
                + " return=" + (orbitReturnBoost ? orbitReturnStiffness + "/" + orbitReturnDamping + "/"
                        + orbitReturnMaxAccel + "@" + orbitReturnMaxSpeed : "off")
                + " recover=" + (long) orbitRecoverDistance
                + " physical=" + physicalPlanets
                + " smallPlanetField=" + (smallPlanetFieldEnabled ? "on" : "off")
                + " smallPlanets=" + (smallPlanetPhysical ? "on" : "off") + "/" + smallPlanetMaxGlobal
                + " smallPlanetRadialGravity=" + smallPlanetRadialGravity
                + " smallPlanetIncremental=" + smallPlanetBuildIncrementalSections
                + " bodyVoxelsPerTick=" + (generatedBodyBuildBudgetEnabled ? generatedBodyBuildVoxelsPerTick : "off")
                + " bodyBuildMsPerTick=" + (generatedBodyBuildMillisPerTick <= 0.0 ? "unbudgeted" : generatedBodyBuildMillisPerTick)
                + " sableAllocationProbe=" + (sableAllocationPhaseProbe ? "on" : "off")
                + " entry=" + handoffEntry
                + " fieldGravity=" + fieldGravity
                + " work/tick=" + (workPerPhysicsTick <= 0 ? "unbudgeted" : workPerPhysicsTick)
                + " gravityEvery=" + gravityIntervalTicks + "t"
                + " contributors=" + gravityContributors
                + " fieldRange=" + (fieldRange == Double.MAX_VALUE ? "∞" : (long) fieldRange)
                + " proxyBudget=" + proxyBudget
                + " lodEvery=" + lodIntervalTicks + "t"
                + " voidDamage=" + (voidDamage ? "on" : "off")
                + " spaceDims=" + spaceDimensions.size()
                + " handoff=" + (handoffEnabled ? "on" : "off") + "->" + handoffHost
                + " band=" + (long) handoffBandThickness
                + " handoffEvery=" + handoffIntervalTicks + "t"
                + " velocity=" + (velocityCarryover ? "carry" : "zero")
                + " flightGrant=" + (flightGrant ? "on" : "off")
                + " maxSpikeRadius=" + maxSpikeRadius
                + " probe=" + perfProbeEnabled;
    }

    public static class Data {
        public Integer version;
        public boolean enabled = false;
        public int workPerPhysicsTick = 0;
        public int gravityIntervalTicks = 20;
        public int gravityContributors = 3;
        public double fieldRange = 100_000.0;
        public boolean realOrbits = false;
        public boolean orbitWarpAllowed = false;
        public boolean voidseaUnboundedHeight = false;
        public boolean blackHolesEnabled = false;
        public int blackHoleOxygenFactor = 3;
        public boolean cosmicDragonBallsEnabled = false;
        public java.util.List<String> cosmicDragonBallHosts = new java.util.ArrayList<>(java.util.List.of("minecraft:overworld"));
        public int cosmicDragonBallRadius = 3000, cosmicDragonBallDormantDays = 3;
        public String hakaiPlanets = "off";
        public java.util.List<String> hakaiPlanetProtected = new java.util.ArrayList<>(java.util.List.of("xenopixelsmod:earth"));
        public int hakaiPlanetPermissionLevel = 2;
        public double hakaiPlanetReach = 20_000;
        public int hakaiPlanetEraseTicks = 100, hakaiPlanetRestoreMinutes = 0;
        public boolean hakaiPlanetEjectOccupants = true;
        public String universeGrowth = "off";
        public int universeMaxRevealed = 512, universeDiscoveriesPerDay = 8;
        public boolean universeWarp = false;
        public boolean logicalUniverseEnabled = false;
        public double orbitEpochOffsetSeconds = 0.0;
        public double orbitHoldStiffness = 0.02;
        public double orbitHoldDamping = 0.28;
        public double orbitHoldMaxAccel = 0.5;
        public boolean orbitReturnBoost = true;
        public double orbitReturnStiffness = 0.08;
        public double orbitReturnDamping = 0.57;
        public double orbitReturnMaxAccel = 3.0;
        public double orbitReturnMaxSpeed = 60.0;
        public double orbitHeldThreshold = 0.35;
        public int orbitReleaseTicks = 40;
        public double orbitRecoverDistance = 4096.0;
        public double planetSpinDamping = 0.5;
        public boolean planetReleaseStaffLock = true;
        public String physicalPlanets = "proximity";
        public double proximityActivateRange = 1500.0;
        public double proximityReleaseRange = 2200.0;
        public int proximityIntervalTicks = 20;
        public int planetBuildVoxelsPerTick = 5000;
        public boolean planetBuildPoseFix = true;
        public boolean planetBuildFinalize = false;
        public boolean planetOrphanCleanup = true;
        public boolean sableTicketUnloadKeyFix = false;
        public boolean planetSkipSplitHeatMap = false;
        public boolean planetSkipChunkTickets = false;
        public boolean planetBuildFrameFirst = false;
        public boolean planetBuildDeferFinalize = false;
        public boolean planetBuildCoalesceChunkBounds = false;
        public boolean planetBuildEvictIntruders = false;
        public boolean asteroidBelts = false;
        public boolean asteroidPhysical = false;
        public double asteroidActivateRange = 420.0, asteroidReleaseRange = 650.0;
        public int asteroidMaxPerPlayer = 6, asteroidMaxGlobal = 64, asteroidSpawnIntervalTicks = 10,
                asteroidBuildVoxelsPerTick = 2000, asteroidScanCellsPerPass = 4096;
        public boolean asteroidBuildIncrementalSections = false;
        public boolean smallPlanetPhysical = false;
        public boolean smallPlanetRadialGravity = true;
        public boolean smallPlanetFieldEnabled = true;
        public double smallPlanetActivateRange = 420.0, smallPlanetReleaseRange = 650.0;
        public int smallPlanetMaxPerPlayer = 2, smallPlanetMaxGlobal = 8, smallPlanetSpawnIntervalTicks = 40,
                smallPlanetBuildVoxelsPerTick = 512;
        public boolean smallPlanetBuildIncrementalSections = false;
        public boolean generatedBodyBuildBudgetEnabled = true;
        public int generatedBodyBuildVoxelsPerTick = 2_000;
        public double generatedBodyBuildMillisPerTick = 0.0;
        public boolean sableAllocationPhaseProbe = false;
        public boolean asteroidFracture = false;
        public boolean oxygenEnabled = false, oxygenShipsPressurized = true;
        public int oxygenBreathSeconds = 30, oxygenCapsuleSeconds = 300, oxygenReserveCapSeconds = 900;
        public double oxygenKiPerSecond = 5.0, oxygenDamagePerSecond = 1.0, oxygenRefillPerSecond = 10.0;
        public double asteroidFractureDeltaV = 6.0;
        public double asteroidFractureSlabHalfWidth = 0.87;
        public int asteroidFractureCooldownTicks = 40, asteroidMaxFragmentsPerAsteroid = 8, asteroidMaxFragmentsGlobal = 128,
                asteroidFragmentMinBlocks = 8, asteroidFragmentLifetimeTicks = 1200;
        public boolean planetEphemeral = false;
        public boolean planetRecoverLost = false;
        public List<String> physicalPlanetExclude = new java.util.ArrayList<>(List.of("xenospace_spike:surface"));
        public List<String> ignoreChunkPhysicsDimensions = new java.util.ArrayList<>();
        public int physicalPlanetMaxRadius = 200;
        public String planetAssembly = "atomic";
        public String handoffEntry = "surface";
        public double handoffSurfaceMargin = 2.0;
        public boolean fieldGravity = false;
        public boolean voidDamage = false;
        public List<String> spaceDimensions =
                List.of("xenopixelsmod:voidsea", "xenospace_spike:orbit");
        public boolean handoffEnabled = false;
        public Map<String, Double> handoffExitOverrides = new HashMap<>();
        public String handoffHost = "xenopixelsmod:voidsea";
        public double handoffBandThickness = 128.0;
        public double handoffReach = 512.0;
        public int handoffIntervalTicks = 4;
        public boolean velocityCarryover = true;
        public boolean flightGrant = true;
        public int maxSpikeRadius = 8;
        public int proxyBudget = 32;
        public int lodIntervalTicks = 10;
        public double lodProxyFactor = 900.0;
        public double lodFieldFactor = 400.0;
        public double lodAnchorFactor = 60.0;
        public double lodChunkFactor = 1.05;
        public double lodHysteresisMargin = 0.25;
        public boolean perfProbeEnabled = true;
        public JsonObject bodyDefaults = new JsonObject();
    }
}
