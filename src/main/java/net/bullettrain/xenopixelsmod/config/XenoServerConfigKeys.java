package net.bullettrain.xenopixelsmod.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Typed last-session (and existing {@code /xenoserver set}) knobs.
 * Mutates {@link XenoServerConfig} statics only — callers {@code save()} and broadcast.
 */
public final class XenoServerConfigKeys {

    public enum Kind {
        BOOL, INT, FLOAT
    }

    public static final class Result {
        public final boolean ok;
        public final String message;

        private Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public static final class Key {
        public final String id;
        public final Kind kind;
        public final String help;
        private final Supplier<String> getter;
        private final Consumer<String> applyRaw;

        private Key(String id, Kind kind, String help, Supplier<String> getter, Consumer<String> applyRaw) {
            this.id = id;
            this.kind = kind;
            this.help = help;
            this.getter = getter;
            this.applyRaw = applyRaw;
        }

        public String get() {
            return getter.get();
        }
    }

    private static final Map<String, Key> BY_ID = new LinkedHashMap<>();
    private static final Map<String, String> ALIAS = new LinkedHashMap<>();

    static {
        bool("bt3CombatEnabled", "Master combat switch",
                () -> XenoServerConfig.bt3CombatEnabled,
                v -> XenoServerConfig.bt3CombatEnabled = v,
                "combat");
        bool("bt3ComboEnabled", "Combo string",
                () -> XenoServerConfig.bt3ComboEnabled,
                v -> XenoServerConfig.bt3ComboEnabled = v,
                "combo");
        bool("bt3VanishEnabled", "Vanish",
                () -> XenoServerConfig.bt3VanishEnabled,
                v -> XenoServerConfig.bt3VanishEnabled = v,
                "vanish");
        bool("hakaiEnabled", "Hakai erasure technique",
                () -> XenoServerConfig.hakaiEnabled,
                v -> XenoServerConfig.hakaiEnabled = v,
                "hakai");
        bool("hakaiTargetGlow", "Outline the Hakai target while the channel runs",
                () -> XenoServerConfig.hakaiTargetGlow,
                v -> XenoServerConfig.hakaiTargetGlow = v,
                "hakaiglow");
        flt("hakaiPoiseFraction", "Damage a Hakai channel survives (fraction of max HP)",
                () -> XenoServerConfig.hakaiPoiseFraction,
                v -> XenoServerConfig.hakaiPoiseFraction = Math.max(0f, Math.min(1f, v)),
                "hakaipoise");
        // Registered as a float even though the field is a double: there is no DOUBLE Kind, and
        // an interrupt distance in blocks needs nowhere near double precision.
        flt("hakaiMoveInterruptDistance", "Blocks a Hakai caster may drift before it breaks",
                () -> (float) XenoServerConfig.hakaiMoveInterruptDistance,
                v -> XenoServerConfig.hakaiMoveInterruptDistance = Math.max(0.5, Math.min(32.0, v)),
                "hakaimove");
        bool("bt3ChaseDashEnabled", "Chase dash",
                () -> XenoServerConfig.bt3ChaseDashEnabled,
                v -> XenoServerConfig.bt3ChaseDashEnabled = v,
                "chase");
        bool("chaseFlightEnabled", "Chase dash flies to target instead of teleporting (BT3 default on)",
                () -> XenoServerConfig.chaseFlightEnabled,
                v -> XenoServerConfig.chaseFlightEnabled = v,
                "chaseflight");
        flt("chaseFlightSpeed", "Chase fly speed in blocks/tick",
                () -> (float) XenoServerConfig.chaseFlightSpeed,
                v -> XenoServerConfig.chaseFlightSpeed = Math.max(0.1, Math.min(20.0, v)),
                "chasespeed", "chaseflightspeed");
        flt("chaseMaxRange", "Chase start range in blocks (0 = unlimited)",
                () -> (float) XenoServerConfig.chaseMaxRange,
                v -> XenoServerConfig.chaseMaxRange = Math.max(0.0, v),
                "chaserange", "chasemaxrange");
        integer("chaseFlightTimeoutTicks", "Chase fly timeout in ticks",
                () -> XenoServerConfig.chaseFlightTimeoutTicks,
                v -> XenoServerConfig.chaseFlightTimeoutTicks = Math.max(40, Math.min(12000, v)),
                "chasetimeout");
        flt("vanishMaxRange", "Vanish max range",
                () -> (float) XenoServerConfig.vanishMaxRange,
                v -> XenoServerConfig.vanishMaxRange = Math.max(1.0, v),
                "vanishrange");
        flt("hakaiKiCost", "Hakai KI cost",
                () -> XenoServerConfig.hakaiKiCost,
                v -> XenoServerConfig.hakaiKiCost = Math.max(0f, v),
                "hakaiki");
        flt("hakaiMaxRange", "Hakai range in blocks",
                () -> (float) XenoServerConfig.hakaiMaxRange,
                v -> XenoServerConfig.hakaiMaxRange = Math.max(1.0, Math.min(64.0, v)),
                "hakairange");
        integer("hakaiCooldownTicks", "Hakai cooldown ticks",
                () -> XenoServerConfig.hakaiCooldownTicks,
                v -> XenoServerConfig.hakaiCooldownTicks = Math.max(0, Math.min(2400, v)),
                "hakaicd");
        integer("hakaiChannelTicks", "Hakai channel length ticks",
                () -> XenoServerConfig.hakaiChannelTicks,
                v -> XenoServerConfig.hakaiChannelTicks = Math.max(10, Math.min(400, v)),
                "hakaichannel");
        flt("chaseStopGap", "Blocks short of the target where chase stops (0 = on them)",
                () -> (float) XenoServerConfig.chaseStopGap,
                v -> XenoServerConfig.chaseStopGap = Math.max(0.0, Math.min(8.0, v)),
                "chasestop", "chasestopgap");
        flt("vanishGap", "Blocks behind the target a vanish lands",
                () -> (float) XenoServerConfig.vanishGap,
                v -> XenoServerConfig.vanishGap = Math.max(0.0, Math.min(16.0, v)),
                "vanishdistance", "vanishgap");
        bool("vanishOpenSpotSearch", "Move a vanish landing off solid blocks (off = original)",
                () -> XenoServerConfig.vanishOpenSpotSearch,
                v -> XenoServerConfig.vanishOpenSpotSearch = v,
                "vanishopenspot", "vanishunstick");
        flt("vanishNearField", "Range under which vanish uses the target's own back (0 = off)",
                () -> (float) XenoServerConfig.vanishNearField,
                v -> XenoServerConfig.vanishNearField = Math.max(0.0, Math.min(8.0, v)),
                "vanishnear", "vanishnearfield");
        flt("vanishSide", "Left/right vanish offset",
                () -> (float) XenoServerConfig.vanishSide,
                v -> XenoServerConfig.vanishSide = Math.max(0.0, Math.min(8.0, v)),
                "vanishside");
        flt("kickKnockbackScale", "Kick knockback distance scale",
                () -> XenoServerConfig.kickKnockbackScale,
                v -> XenoServerConfig.kickKnockbackScale = Math.max(0.1f, Math.min(8f, v)),
                "kickkb", "kickknockback");
        integer("comboAnimGeneration", "Combat animation generation: 1 original, 2 twins, 3 BT3, 4 centred BT3 rush",
                () -> XenoServerConfig.comboAnimGeneration,
                v -> XenoServerConfig.comboAnimGeneration =
                        net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog.clampGeneration(v),
                "comboanimgen", "animgen");
        integer("comboMashIntervalTicks", "Ticks between beats of a held mash (also sets clip speed)",
                () -> XenoServerConfig.comboMashIntervalTicks,
                v -> XenoServerConfig.comboMashIntervalTicks = Math.max(2, Math.min(20, v)),
                "mashinterval", "combobeat");
        integer("comboLaunchKickEvery", "Every Nth mash beat is a charge-kick knockback (0 = off)",
                () -> XenoServerConfig.comboLaunchKickEvery,
                v -> XenoServerConfig.comboLaunchKickEvery = Math.max(0, Math.min(32, v)),
                "launchkickevery", "combokickevery");
        flt("comboLauncherUp", "Mash/charge launcher Y (off the floor)",
                () -> XenoServerConfig.comboLauncherUp,
                v -> XenoServerConfig.comboLauncherUp = Math.max(0.2f, Math.min(6f, v)),
                "launcherup", "kickup");
        flt("comboLauncherHoriz", "Mash/charge launcher away (diagonal)",
                () -> XenoServerConfig.comboLauncherHoriz,
                v -> XenoServerConfig.comboLauncherHoriz = Math.max(0.05f, Math.min(4f, v)),
                "launcherhoriz", "kickdiag");
        bool("bt3BackstepEnabled", "Backstep",
                () -> XenoServerConfig.bt3BackstepEnabled,
                v -> XenoServerConfig.bt3BackstepEnabled = v,
                "backstep");
        bool("bt3FinisherEnabled", "Finisher",
                () -> XenoServerConfig.bt3FinisherEnabled,
                v -> XenoServerConfig.bt3FinisherEnabled = v,
                "finisher");
        bool("bt3ChargeAttackEnabled", "Charged fist/kick",
                () -> XenoServerConfig.bt3ChargeAttackEnabled,
                v -> XenoServerConfig.bt3ChargeAttackEnabled = v,
                "charge", "chargeattack");
        bool("bt3DragonDashEnabled", "Dragon dash",
                () -> XenoServerConfig.bt3DragonDashEnabled,
                v -> XenoServerConfig.bt3DragonDashEnabled = v,
                "dragon");
        bool("bt3GuardEnabled", "Guard",
                () -> XenoServerConfig.bt3GuardEnabled,
                v -> XenoServerConfig.bt3GuardEnabled = v,
                "guard", "block");
        bool("bt3SuperCounterEnabled", "Super counter",
                () -> XenoServerConfig.bt3SuperCounterEnabled,
                v -> XenoServerConfig.bt3SuperCounterEnabled = v,
                "counter", "supercounter");
        bool("bt3KiBlastCancelEnabled", "Ki blast cancel",
                () -> XenoServerConfig.bt3KiBlastCancelEnabled,
                v -> XenoServerConfig.bt3KiBlastCancelEnabled = v,
                "kiblast", "kicancel");
        bool("bt3ZBurstEnabled", "Z-Burst",
                () -> XenoServerConfig.bt3ZBurstEnabled,
                v -> XenoServerConfig.bt3ZBurstEnabled = v,
                "zburst");
        bool("bt3LockCycleEnabled", "Lock-on cycle next/prev",
                () -> XenoServerConfig.bt3LockCycleEnabled,
                v -> XenoServerConfig.bt3LockCycleEnabled = v,
                "lockcycle", "lock");
        bool("kiDiskDespawnOnHitBudget", "Ki disk vanishes once its hit budget is spent",
                () -> XenoServerConfig.kiDiskDespawnOnHitBudget,
                v -> XenoServerConfig.kiDiskDespawnOnHitBudget = v);
        bool("bt3ComboPunchesOnly", "Punch-only combo",
                () -> XenoServerConfig.bt3ComboPunchesOnly,
                v -> XenoServerConfig.bt3ComboPunchesOnly = v,
                "punchcombo", "punchesonly", "combopunch");
        bool("protectDmzMasters", "Protect DMZ masters",
                () -> XenoServerConfig.protectDmzMasters,
                v -> XenoServerConfig.protectDmzMasters = v,
                "protectmasters", "masters");
        bool("dmzSagaSpawnCompat", "Recover missing DMZ saga quest enemies",
                () -> XenoServerConfig.dmzSagaSpawnCompat,
                v -> XenoServerConfig.dmzSagaSpawnCompat = v,
                "sagaspawn", "dmzquestspawn");
        bool("bt3RushChainEnabled", "Rush chain",
                () -> XenoServerConfig.bt3RushChainEnabled,
                v -> XenoServerConfig.bt3RushChainEnabled = v,
                "rush", "rushchain");
        flt("rushKiCost", "Ki cost of a Xeno rush strike",
                () -> (float) XenoServerConfig.rushKiCost,
                v -> XenoServerConfig.rushKiCost = Math.max(0.0, v),
                "rushkicost", "rushcost");
        integer("rushCooldownTicks", "Cooldown of a Xeno rush strike in ticks",
                () -> XenoServerConfig.rushCooldownTicks,
                v -> XenoServerConfig.rushCooldownTicks = Math.max(1, v),
                "rushcooldown", "rushcd");
        bool("zanzokenEnabled", "Zanzoken afterimage dodge",
                () -> XenoServerConfig.zanzokenEnabled,
                v -> XenoServerConfig.zanzokenEnabled = v,
                "zanzoken");
        flt("zanzokenKiCost", "Ki spent per Zanzoken press",
                () -> XenoServerConfig.zanzokenKiCost,
                v -> XenoServerConfig.zanzokenKiCost = Math.max(0f, v),
                "zanzokencost");
        integer("zanzokenWindowTicks", "How long a Zanzoken press stays live, in ticks",
                () -> XenoServerConfig.zanzokenWindowTicks,
                v -> XenoServerConfig.zanzokenWindowTicks = Math.max(1, v),
                "zanzokenwindow");
        integer("zanzokenIFramesTicks", "Invulnerability after a landed Zanzoken, in ticks",
                () -> XenoServerConfig.zanzokenIFramesTicks,
                v -> XenoServerConfig.zanzokenIFramesTicks = Math.max(0, v),
                "zanzokeniframes");
        integer("zanzokenCooldownTicks", "Zanzoken cooldown in ticks",
                () -> XenoServerConfig.zanzokenCooldownTicks,
                v -> XenoServerConfig.zanzokenCooldownTicks = Math.max(0, v),
                "zanzokencooldown", "zanzokencd");
        bool("zanzokenGhostAfterimage", "Zanzoken leaves a rendered body copy instead of the dust silhouette",
                () -> XenoServerConfig.zanzokenGhostAfterimage,
                v -> XenoServerConfig.zanzokenGhostAfterimage = v,
                "zanzokenghost");
        integer("zanzokenRingClones", "Copies in the ring Zanzoken throws around the attacker",
                () -> XenoServerConfig.zanzokenRingClones,
                v -> XenoServerConfig.zanzokenRingClones = Math.max(1, Math.min(16, v)),
                "zanzokenring");
        flt("zanzokenRingRadius", "Radius of the Zanzoken ring in blocks",
                () -> (float) XenoServerConfig.zanzokenRingRadius,
                v -> XenoServerConfig.zanzokenRingRadius = Math.max(0.5, Math.min(12.0, v)),
                "zanzokenringradius");
        integer("zanzokenRingTicks", "How long a Zanzoken ring stands, in ticks",
                () -> XenoServerConfig.zanzokenRingTicks,
                v -> XenoServerConfig.zanzokenRingTicks = Math.max(20, Math.min(1200, v)),
                "zanzokenringticks");
        bool("multiFormEnabled", "Shi Shin No Ken multi-form",
                () -> XenoServerConfig.multiFormEnabled,
                v -> XenoServerConfig.multiFormEnabled = v,
                "multiform", "shishin");
        integer("multiFormBodies", "Bodies a fighter divides into, counting their own",
                () -> XenoServerConfig.multiFormBodies,
                v -> XenoServerConfig.multiFormBodies = Math.max(2, Math.min(8, v)),
                "multiformbodies");
        flt("multiFormKiCost", "Ki spent dividing into multi-form",
                () -> XenoServerConfig.multiFormKiCost,
                v -> XenoServerConfig.multiFormKiCost = Math.max(0f, v),
                "multiformcost");
        bool("bt3SonicSwayEnabled", "Sonic sway",
                () -> XenoServerConfig.bt3SonicSwayEnabled,
                v -> XenoServerConfig.bt3SonicSwayEnabled = v,
                "sonic", "sway");
        bool("bt3UltimateEnabled", "Ultimate",
                () -> XenoServerConfig.bt3UltimateEnabled,
                v -> XenoServerConfig.bt3UltimateEnabled = v,
                "ultimate");
        bool("bt3SparkingEnabled", "Sparking",
                () -> XenoServerConfig.bt3SparkingEnabled,
                v -> XenoServerConfig.bt3SparkingEnabled = v,
                "sparking");
        bool("bt3TransformImpactEnabled", "Transform impact",
                () -> XenoServerConfig.bt3TransformImpactEnabled,
                v -> XenoServerConfig.bt3TransformImpactEnabled = v,
                "transformimpact", "impact");
        bool("kiOverchargeEnabled", "Power-release overcharge",
                () -> XenoServerConfig.kiOverchargeEnabled,
                v -> XenoServerConfig.kiOverchargeEnabled = v,
                "kiovercharge", "overcharge");
        bool("chargeOverchargeEnabled", "Hold charge past 175%",
                () -> XenoServerConfig.chargeOverchargeEnabled,
                v -> XenoServerConfig.chargeOverchargeEnabled = v,
                "chargeovercharge");
        bool("chargeOverchargeGriefEnabled", "Charge craters / rocks / disk slice",
                () -> XenoServerConfig.chargeOverchargeGriefEnabled,
                v -> XenoServerConfig.chargeOverchargeGriefEnabled = v,
                "chargegrief", "grief");
        bool("chargeOverchargeDiskSliceEnabled", "Overcharge disk slice",
                () -> XenoServerConfig.chargeOverchargeDiskSliceEnabled,
                v -> XenoServerConfig.chargeOverchargeDiskSliceEnabled = v,
                "diskslice");
        bool("chargeOverchargeCameraEnabled", "Overcharge camera punch",
                () -> XenoServerConfig.chargeOverchargeCameraEnabled,
                v -> XenoServerConfig.chargeOverchargeCameraEnabled = v,
                "chargecamera");
        bool("chargeOverchargeVoicesEnabled", "Overcharge voice lines",
                () -> XenoServerConfig.chargeOverchargeVoicesEnabled,
                v -> XenoServerConfig.chargeOverchargeVoicesEnabled = v,
                "chargevoices");
        bool("kiFullGameplayScaling", "Uncap damage/explosion with visuals",
                () -> XenoServerConfig.kiFullGameplayScaling,
                v -> XenoServerConfig.kiFullGameplayScaling = v,
                "fullgameplay");
        bool("beamSurgeEnabled", "Hold-to-grow ki waves",
                () -> XenoServerConfig.beamSurgeEnabled,
                v -> XenoServerConfig.beamSurgeEnabled = v,
                "beamsurge", "surge");
        bool("dmzHudEnabled", "Stock DMZ HUD",
                () -> XenoServerConfig.dmzHudEnabled,
                v -> XenoServerConfig.dmzHudEnabled = v,
                "dmzhud");
        bool("dmzContentBootstrap", "Install DMZ form/skill JSON on boot",
                () -> XenoServerConfig.dmzContentBootstrap,
                v -> XenoServerConfig.dmzContentBootstrap = v,
                "bootstrap");
        bool("trainingDummyEnabled", "Training dummy",
                () -> XenoServerConfig.trainingDummyEnabled,
                v -> XenoServerConfig.trainingDummyEnabled = v,
                "dummy", "trainingdummy");
        bool("parallelQuestEnabled", "Parallel quests",
                () -> XenoServerConfig.parallelQuestEnabled,
                v -> XenoServerConfig.parallelQuestEnabled = v,
                "quest", "quests", "parallelquest");
        bool("mentorEnabled", "Mentors",
                () -> XenoServerConfig.mentorEnabled,
                v -> XenoServerConfig.mentorEnabled = v,
                "mentor", "mentors");
        bool("npcSayEnabled", "CustomNPCs npc.say() and NPC executeCommand OP chat",
                () -> XenoServerConfig.npcSayEnabled,
                v -> XenoServerConfig.npcSayEnabled = v,
                "npcsay", "npcsayenabled", "npcsaychat");
        integer("dmzStructureY", "Absolute Y for /xenostructure place (0 = terrain)",
                () -> XenoServerConfig.dmzStructureY,
                v -> XenoServerConfig.dmzStructureY = v,
                "structurey");
        integer("dmzStructureYOffset", "Y offset added when placing a DMZ structure",
                () -> XenoServerConfig.dmzStructureYOffset,
                v -> XenoServerConfig.dmzStructureYOffset = v,
                "structureyoffset");
        bool("dmzStructureMaster", "Summon matching master after /xenostructure place",
                () -> XenoServerConfig.dmzStructureMaster,
                v -> XenoServerConfig.dmzStructureMaster = v,
                "structuremaster");
        bool("lockOnThroughBlocks", "DMZ Z-lock acquire and hold through walls",
                () -> XenoServerConfig.lockOnThroughBlocks,
                v -> XenoServerConfig.lockOnThroughBlocks = v,
                "lockthrough", "lockonthrough", "lockthroughblocks");

        flt("chargeOverchargeMaxPercent", "Charge ceiling % (201–2000)",
                () -> XenoServerConfig.chargeOverchargeMaxPercent,
                v -> XenoServerConfig.chargeOverchargeMaxPercent = XenoServerConfig.clampChargeCap(v),
                "chargecap", "cap");
        integer("chargeOverchargeMinLevel", "Min level for charge overcharge",
                () -> XenoServerConfig.chargeOverchargeMinLevel,
                v -> XenoServerConfig.chargeOverchargeMinLevel = Math.max(0, v),
                "chargeminlevel");
        flt("chargeOverchargeSizePerPercent", "Size growth per % above 175",
                () -> XenoServerConfig.chargeOverchargeSizePerPercent,
                v -> XenoServerConfig.chargeOverchargeSizePerPercent = Math.max(0f, v));
        flt("chargeOverchargeSpeedPerPercent", "Speed growth per % above 175",
                () -> XenoServerConfig.chargeOverchargeSpeedPerPercent,
                v -> XenoServerConfig.chargeOverchargeSpeedPerPercent = Math.max(0f, v));
        flt("chargeOverchargeMaxDamageScale", "Soft damage cap on extra charge",
                () -> XenoServerConfig.chargeOverchargeMaxDamageScale,
                v -> XenoServerConfig.chargeOverchargeMaxDamageScale = v > 1f ? v : 4f);
        flt("chargeFormSizeFactor", "Form size factor for charge",
                () -> XenoServerConfig.chargeFormSizeFactor,
                v -> XenoServerConfig.chargeFormSizeFactor = Math.max(0f, v));
        flt("chargeFormSizeLogCap", "ln(formMult) cap",
                () -> XenoServerConfig.chargeFormSizeLogCap,
                v -> XenoServerConfig.chargeFormSizeLogCap = v > 0f ? v : 4f);
        flt("chargeOverchargeCraterMinPercent", "Crater starts at this %",
                () -> XenoServerConfig.chargeOverchargeCraterMinPercent,
                v -> XenoServerConfig.chargeOverchargeCraterMinPercent = Math.max(175f, v));
        integer("chargeOverchargeCraterMaxRadius", "Crater radius blocks",
                () -> XenoServerConfig.chargeOverchargeCraterMaxRadius,
                v -> XenoServerConfig.chargeOverchargeCraterMaxRadius = Math.max(1, Math.min(16, v)));
        integer("chargeOverchargeCraterIntervalTicks", "Ticks between craters",
                () -> XenoServerConfig.chargeOverchargeCraterIntervalTicks,
                v -> XenoServerConfig.chargeOverchargeCraterIntervalTicks = Math.max(2, Math.min(40, v)));
        integer("chargeOverchargeMaxRocks", "Flying rocks per pulse",
                () -> XenoServerConfig.chargeOverchargeMaxRocks,
                v -> XenoServerConfig.chargeOverchargeMaxRocks = Math.max(0, Math.min(16, v)));

        flt("kiProjectileMaxSize", "Creator/runtime size ceiling",
                () -> XenoServerConfig.kiProjectileMaxSize,
                v -> XenoServerConfig.kiProjectileMaxSize = v > 0f && Float.isFinite(v) ? v : 320f,
                "maxsize");
        flt("kiProjectileMaxSpeed", "Creator/runtime speed ceiling",
                () -> XenoServerConfig.kiProjectileMaxSpeed,
                v -> XenoServerConfig.kiProjectileMaxSpeed = v > 0f && Float.isFinite(v) ? v : 32f,
                "maxspeed");
        flt("kiDestructionMaxRadius", "Block-destruction radius cap",
                () -> XenoServerConfig.kiDestructionMaxRadius,
                v -> XenoServerConfig.kiDestructionMaxRadius = v > 0f && Float.isFinite(v) ? v : 32f,
                "destruction");
        integer("kiDestructionBlocksPerTick", "Synchronous cube-check budget",
                () -> XenoServerConfig.kiDestructionBlocksPerTick,
                v -> XenoServerConfig.kiDestructionBlocksPerTick = Math.max(64, v));

        integer("kiDurationTicks", "All-type fire window (0 = stock)",
                () -> XenoServerConfig.kiDurationTicks,
                v -> XenoServerConfig.kiDurationTicks = XenoServerConfig.clampBarrageTicks(v),
                "duration");
        integer("barrageDurationTicks", "Barrage fire window (0 = stock)",
                () -> XenoServerConfig.barrageDurationTicks,
                v -> XenoServerConfig.barrageDurationTicks = XenoServerConfig.clampBarrageTicks(v));
        integer("barrageCooldownTicks", "Barrage cooldown (0 = technique)",
                () -> XenoServerConfig.barrageCooldownTicks,
                v -> XenoServerConfig.barrageCooldownTicks = XenoServerConfig.clampBarrageTicks(v));
        integer("barrageExtraTicks1", "Volley Mastery 1 extra ticks",
                () -> XenoServerConfig.barrageExtraTicks1,
                v -> XenoServerConfig.barrageExtraTicks1 = Math.max(0, v));
        integer("barrageExtraTicks2", "Volley Mastery 2 extra ticks",
                () -> XenoServerConfig.barrageExtraTicks2,
                v -> XenoServerConfig.barrageExtraTicks2 = Math.max(0, v));
        integer("barrageExtraTicks3", "Volley Mastery 3 extra ticks",
                () -> XenoServerConfig.barrageExtraTicks3,
                v -> XenoServerConfig.barrageExtraTicks3 = Math.max(0, v));
        integer("barrageCooldownReduce1", "Volley Mastery 1 cooldown cut",
                () -> XenoServerConfig.barrageCooldownReduce1,
                v -> XenoServerConfig.barrageCooldownReduce1 = Math.max(0, v));
        integer("barrageCooldownReduce2", "Volley Mastery 2 cooldown cut",
                () -> XenoServerConfig.barrageCooldownReduce2,
                v -> XenoServerConfig.barrageCooldownReduce2 = Math.max(0, v));
        integer("barrageCooldownReduce3", "Volley Mastery 3 cooldown cut",
                () -> XenoServerConfig.barrageCooldownReduce3,
                v -> XenoServerConfig.barrageCooldownReduce3 = Math.max(0, v));
        flt("barrageKiPerTick", "Ki drain per tick of a long volley",
                () -> XenoServerConfig.barrageKiPerTick,
                v -> XenoServerConfig.barrageKiPerTick = Math.max(0f, v),
                "barragekipertick");

        integer("guidanceControlRange", "Guidance reach (0 = 192+48×level)",
                () -> XenoServerConfig.guidanceControlRange,
                v -> {
                    XenoServerConfig.guidanceControlRange =
                            Math.max(0, Math.min(XenoServerConfig.GUIDANCE_RANGE_MAX, v));
                    XenoServerConfig.applyGuidanceOverrides();
                },
                "guiderange");
        flt("guidanceTurnRate", "Turn override 0–1 (0 = skill curve)",
                () -> XenoServerConfig.guidanceTurnRate,
                v -> {
                    XenoServerConfig.guidanceTurnRate = clamp01(v);
                    XenoServerConfig.applyGuidanceOverrides();
                },
                "guideturn");
        flt("guidanceCameraRate", "Look-ray lerp 0–1 (0 = 0.38)",
                () -> XenoServerConfig.guidanceCameraRate,
                v -> {
                    XenoServerConfig.guidanceCameraRate = clamp01(v);
                    XenoServerConfig.applyGuidanceOverrides();
                },
                "guiderate");
        integer("guidanceHoldGraceTicks", "Hold-packet grace ticks",
                () -> XenoServerConfig.guidanceHoldGraceTicks,
                v -> XenoServerConfig.guidanceHoldGraceTicks = Math.max(0, Math.min(40, v)),
                "guidegrace");
        flt("guidanceLookRayMin", "Minimum look-ray travel",
                () -> XenoServerConfig.guidanceLookRayMin,
                v -> {
                    XenoServerConfig.guidanceLookRayMin = Math.max(0f, Math.min(64f, v));
                    XenoServerConfig.applyGuidanceOverrides();
                },
                "guidelookmin");

        flt("beamSurgeKiPerTick", "Ki per tick of surge",
                () -> XenoServerConfig.beamSurgeKiPerTick,
                v -> XenoServerConfig.beamSurgeKiPerTick = Math.max(0f, v),
                "surgekipertick");
        flt("beamSurgeStaminaPerTick", "Stamina per tick of surge",
                () -> XenoServerConfig.beamSurgeStaminaPerTick,
                v -> XenoServerConfig.beamSurgeStaminaPerTick = Math.max(0f, v),
                "surgestam");
        flt("beamSurgeCostGrowth", "Extra cost at full surge",
                () -> XenoServerConfig.beamSurgeCostGrowth,
                v -> XenoServerConfig.beamSurgeCostGrowth = Math.max(0f, v),
                "surgecost");
        flt("beamSurgeSizeGain", "Thickness added at full surge",
                () -> XenoServerConfig.beamSurgeSizeGain,
                v -> XenoServerConfig.beamSurgeSizeGain = Math.max(0f, v),
                "surgesize", "sizegain");
        flt("beamSurgeDamageGain", "Damage added at full surge",
                () -> XenoServerConfig.beamSurgeDamageGain,
                v -> XenoServerConfig.beamSurgeDamageGain = Math.max(0f, v),
                "surgedamage", "damagegain");
        flt("beamSurgeReachGain", "Length growth at full surge",
                () -> XenoServerConfig.beamSurgeReachGain,
                v -> XenoServerConfig.beamSurgeReachGain = Math.max(0f, v),
                "surgereach", "reachgain");
        flt("beamSurgeSearchRadius", "How far to look for the owned wave",
                () -> XenoServerConfig.beamSurgeSearchRadius,
                v -> XenoServerConfig.beamSurgeSearchRadius = Math.max(2f, v),
                "surgesearch");
        flt("beamSurgeCeiling", "Surge ceiling at mastery 0",
                () -> XenoServerConfig.beamSurgeCeiling,
                v -> XenoServerConfig.beamSurgeCeiling = clamp01(v),
                "surgeceiling");
        flt("beamSurgeCeilingPerMastery", "Extra ceiling per mastery",
                () -> XenoServerConfig.beamSurgeCeilingPerMastery,
                v -> XenoServerConfig.beamSurgeCeilingPerMastery = clamp01(v));
        flt("beamSurgeRampPerTick", "Gap closed per tick",
                () -> XenoServerConfig.beamSurgeRampPerTick,
                v -> XenoServerConfig.beamSurgeRampPerTick = Math.min(1f, Math.max(0.001f, v)),
                "surgeramp");
        flt("beamSurgeRampPerMastery", "Extra ramp per mastery",
                () -> XenoServerConfig.beamSurgeRampPerMastery,
                v -> XenoServerConfig.beamSurgeRampPerMastery = Math.min(1f, Math.max(0f, v)));
        flt("beamSurgeMaxLength", "Hard beam length cap (0 = none)",
                () -> XenoServerConfig.beamSurgeMaxLength,
                v -> XenoServerConfig.beamSurgeMaxLength = Float.isFinite(v) ? v : 192f,
                "maxlength", "surgelength");
    }

    private XenoServerConfigKeys() {
    }

    public static Key resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String n = raw.trim().toLowerCase(Locale.ROOT);
        String id = ALIAS.getOrDefault(n, n);
        Key key = BY_ID.get(id);
        if (key != null) return key;
        for (Key k : BY_ID.values()) {
            if (k.id.toLowerCase(Locale.ROOT).equals(n)) return k;
        }
        return null;
    }

    public static Result set(String rawKey, String rawValue) {
        Key key = resolve(rawKey);
        if (key == null) {
            return Result.fail("Unknown key '" + rawKey + "'. Try /xenoserver status");
        }
        if (rawValue == null || rawValue.isBlank()) {
            return Result.fail("Missing value for " + key.id);
        }
        String value = rawValue.trim();
        try {
            switch (key.kind) {
                case BOOL -> {
                    Boolean parsed = parseBool(value);
                    if (parsed == null) {
                        return Result.fail("Expected true/false for " + key.id);
                    }
                    key.applyRaw.accept(parsed ? "true" : "false");
                }
                case INT -> {
                    int parsed = Integer.parseInt(value);
                    key.applyRaw.accept(Integer.toString(parsed));
                }
                case FLOAT -> {
                    float parsed = Float.parseFloat(value);
                    if (!Float.isFinite(parsed)) {
                        return Result.fail("Value must be finite for " + key.id);
                    }
                    key.applyRaw.accept(Float.toString(parsed));
                }
            }
        } catch (NumberFormatException e) {
            return Result.fail("Bad " + key.kind.name().toLowerCase(Locale.ROOT)
                    + " for " + key.id + ": " + value);
        }
        return Result.ok(key.id + " = " + key.get());
    }

    public static Result get(String rawKey) {
        Key key = resolve(rawKey);
        if (key == null) {
            return Result.fail("Unknown key '" + rawKey + "'");
        }
        return Result.ok(key.id + " = " + key.get() + "  (" + key.help + ")");
    }

    public static List<String> suggest(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String alias : ALIAS.keySet()) {
            if (alias.startsWith(p)) out.add(alias);
        }
        for (String id : BY_ID.keySet()) {
            String lower = id.toLowerCase(Locale.ROOT);
            if (lower.startsWith(p) && !out.contains(lower)) out.add(lower);
        }
        Collections.sort(out);
        return out;
    }

    public static List<Key> all() {
        return List.copyOf(BY_ID.values());
    }

    public static String usageKeys() {
        return "chargeovercharge chargecap chargegrief duration maxsize maxspeed "
                + "destruction guiderange guideturn surge maxlength "
                + "combat combo vanish chase backstep chargeattack dragon";
    }

    private static void bool(String id, String help, Supplier<Boolean> get, Consumer<Boolean> set,
                             String... aliases) {
        Key key = new Key(id, Kind.BOOL, help,
                () -> Boolean.toString(get.get()),
                raw -> set.accept(Boolean.parseBoolean(raw)));
        register(key, aliases);
    }

    private static void integer(String id, String help, Supplier<Integer> get, Consumer<Integer> set,
                                String... aliases) {
        Key key = new Key(id, Kind.INT, help,
                () -> Integer.toString(get.get()),
                raw -> set.accept(Integer.parseInt(raw)));
        register(key, aliases);
    }

    private static void flt(String id, String help, Supplier<Float> get, Consumer<Float> set,
                            String... aliases) {
        Key key = new Key(id, Kind.FLOAT, help,
                () -> formatFloat(get.get()),
                raw -> set.accept(Float.parseFloat(raw)));
        register(key, aliases);
    }

    private static void register(Key key, String... aliases) {
        BY_ID.put(key.id, key);
        ALIAS.put(key.id.toLowerCase(Locale.ROOT), key.id);
        for (String a : aliases) {
            if (a == null || a.isBlank()) continue;
            ALIAS.put(a.toLowerCase(Locale.ROOT), key.id);
        }
    }

    private static Boolean parseBool(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        return switch (v) {
            case "true", "on", "yes", "1" -> Boolean.TRUE;
            case "false", "off", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static String formatFloat(float value) {
        if (value == (int) value) return Integer.toString((int) value);
        return Float.toString(value);
    }
}
