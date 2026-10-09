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
        BOOL, INT, FLOAT, TEXT
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
        integer("npcHitRecoveryTicks", "NPC hit recovery before turning/attacking (0-200 ticks; 0 disables)",
                () -> XenoServerConfig.npcHitRecoveryTicks,
                v -> XenoServerConfig.npcHitRecoveryTicks = Math.max(0, Math.min(200, v)), "npcHitRecovery");
        bool("bt3CombatEnabled", "Master combat switch",
                () -> XenoServerConfig.bt3CombatEnabled,
                v -> XenoServerConfig.bt3CombatEnabled = v,
                "combat");
        bool("bt3ComboEnabled", "Combo string",
                () -> XenoServerConfig.bt3ComboEnabled,
                v -> XenoServerConfig.bt3ComboEnabled = v,
                "combo");
        bool("bt3CinematicRushEnabled", "X-X-X then Dragon Dash cinematic rush",
                () -> XenoServerConfig.bt3CinematicRushEnabled,
                v -> XenoServerConfig.bt3CinematicRushEnabled = v,
                "cinematicrush", "xxxa");
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
        text("hakaiMode", "Which Hakai J casts: single (one target) or area (sphere where you look)",
                () -> XenoServerConfig.hakaiMode,
                v -> XenoServerConfig.hakaiMode = XenoServerConfig.normaliseHakaiMode(v),
                "hakaitype");
        flt("hakaiAreaRadius", "Area Hakai sphere radius in blocks (1-4096)",
                () -> (float) XenoServerConfig.hakaiAreaRadius,
                v -> XenoServerConfig.hakaiAreaRadius = XenoServerConfig.clampHakaiAreaRadius(v),
                "hakairadius");
        integer("hakaiAreaMaxTargets", "Most living things one area Hakai erases (1-64)",
                () -> XenoServerConfig.hakaiAreaMaxTargets,
                v -> XenoServerConfig.hakaiAreaMaxTargets = Math.max(1, Math.min(64, v)),
                "hakaitargets");
        bool("hakaiBlocks", "Area Hakai erases blocks in its sphere (plots and claims can veto)",
                () -> XenoServerConfig.hakaiBlocks,
                v -> XenoServerConfig.hakaiBlocks = v,
                "hakaiblocks");
        bool("hakaiShips", "Area Hakai erases Sable ship blocks in its sphere",
                () -> XenoServerConfig.hakaiShips,
                v -> XenoServerConfig.hakaiShips = v,
                "hakaiships");
        integer("hakaiBlockLimit", "Most blocks one area Hakai erases (0-100000000)",
                () -> XenoServerConfig.hakaiBlockLimit,
                v -> XenoServerConfig.hakaiBlockLimit = Math.max(0,
                        Math.min(XenoServerConfig.HAKAI_BLOCK_LIMIT_MAX, v)),
                "hakaiblocklimit");
        integer("hakaiBlocksPerTick", "Blocks that start fading per tick (1-512)",
                () -> XenoServerConfig.hakaiBlocksPerTick,
                v -> XenoServerConfig.hakaiBlocksPerTick = Math.max(1, Math.min(512, v)),
                "hakaiblockrate");
        integer("hakaiBlockFadeTicks", "Ticks each block cracks and fades before vanishing (0-100)",
                () -> XenoServerConfig.hakaiBlockFadeTicks,
                v -> XenoServerConfig.hakaiBlockFadeTicks = Math.max(0, Math.min(100, v)),
                "hakaiblockfade");
        bool("hakaiBlocksUnbreakable", "Area Hakai erases unbreakable blocks too (never bedrock)",
                () -> XenoServerConfig.hakaiBlocksUnbreakable,
                v -> XenoServerConfig.hakaiBlocksUnbreakable = v,
                "hakaiunbreakable");
        flt("hakaiAreaFxScale", "Size of the area Hakai effect on top of its radius (0.1-5)",
                () -> XenoServerConfig.hakaiAreaFxScale,
                v -> XenoServerConfig.hakaiAreaFxScale = Math.max(0.1f, Math.min(5.0f, v)),
                "hakaiareafx", "hakaifxradius");
        bool("hakaiSpareDmzStructures", "Area Hakai never erases DragonMineZ structures",
                () -> XenoServerConfig.hakaiSpareDmzStructures,
                v -> XenoServerConfig.hakaiSpareDmzStructures = v,
                "hakaisparedmz");
        text("hakaiBlockShape", "Area Hakai blocks: sphere, or raze (the building down to the ground)",
                () -> XenoServerConfig.hakaiBlockShape,
                v -> XenoServerConfig.hakaiBlockShape = XenoServerConfig.normaliseHakaiBlockShape(v),
                "hakaishape");
        integer("hakaiRazeHeight", "Blocks above and below the look point the raze shape reaches (1-256)",
                () -> XenoServerConfig.hakaiRazeHeight,
                v -> XenoServerConfig.hakaiRazeHeight = Math.max(1, Math.min(256, v)),
                "hakairazeheight");
        bool("formPassives", "God-form passives at all (Ultra Ego, Hakaishin, Ultra Instinct)",
                () -> XenoServerConfig.formPassives,
                v -> XenoServerConfig.formPassives = v,
                "godpassives");
        bool("ueImmunity", "Ultra Ego: weaker attackers deal no damage",
                () -> XenoServerConfig.ueImmunity,
                v -> XenoServerConfig.ueImmunity = v,
                "ueimmune");
        bool("uePenetration", "Ultra Ego: bonus defense penetration on ki and melee",
                () -> XenoServerConfig.uePenetration,
                v -> XenoServerConfig.uePenetration = v,
                "uepen");
        bool("ueProjectileAura", "Ultra Ego: the aura deletes weaker projectiles and ki",
                () -> XenoServerConfig.ueProjectileAura,
                v -> XenoServerConfig.ueProjectileAura = v,
                "ueaura");
        bool("uePunchBreak", "Ultra Ego: punching a weaker ki blast destroys it",
                () -> XenoServerConfig.uePunchBreak,
                v -> XenoServerConfig.uePunchBreak = v,
                "uepunch");
        bool("hakaiMantle", "Hakaishin: every incoming attack and ki blast is erased",
                () -> XenoServerConfig.hakaiMantle,
                v -> XenoServerConfig.hakaiMantle = v,
                "mantle");
        bool("hakaiMantleNoKnockback", "Hakaishin: punches and kicks cannot knock the wearer back",
                () -> XenoServerConfig.hakaiMantleNoKnockback,
                v -> XenoServerConfig.hakaiMantleNoKnockback = v,
                "mantleknockback");
        bool("uiDodge", "Ultra Instinct: auto-dodge by mastery (none against another UI)",
                () -> XenoServerConfig.uiDodge,
                v -> XenoServerConfig.uiDodge = v,
                "uidodge");
        flt("uiDodgeScale", "Multiplies every Ultra Instinct dodge chance (0-2)",
                () -> XenoServerConfig.uiDodgeScale,
                v -> XenoServerConfig.uiDodgeScale = Math.max(0.0f, Math.min(2.0f, v)),
                "uidodgescale");
        bool("hakaishinNeedsHakai", "Hakaishin also needs Hakai unlocked (off: /dmzform alone grants it)",
                () -> XenoServerConfig.hakaishinNeedsHakai,
                v -> XenoServerConfig.hakaishinNeedsHakai = v,
                "hakaishinhakai");
        text("hakaiSparedDimensions", "Dimensions where Hakai never erases blocks (comma list of ids)",
                () -> XenoServerConfig.hakaiSparedDimensions,
                v -> XenoServerConfig.hakaiSparedDimensions = v == null ? "" : v.trim(),
                "hakaisafedims");
        text("hakaiSparedBlocks", "Blocks Hakai never erases anywhere (comma list of block ids)",
                () -> XenoServerConfig.hakaiSparedBlocks,
                v -> XenoServerConfig.hakaiSparedBlocks = v == null ? "" : v.trim(),
                "hakaisafeblocks");
        bool("hakaiFadeEnabled", "Fade the Hakai victim's body as the channel charges",
                () -> XenoServerConfig.hakaiFadeEnabled,
                v -> XenoServerConfig.hakaiFadeEnabled = v,
                "hakaifade");
        flt("hakaiFadeMinAlpha", "Lowest alpha a fading Hakai body may reach (0 = invisible)",
                () -> XenoServerConfig.hakaiFadeMinAlpha,
                v -> XenoServerConfig.hakaiFadeMinAlpha = Math.max(0f, Math.min(1f, v)),
                "hakaifademin");
        flt("hakaiFadeCurve", "Hakai fade ramp exponent (1 = linear, >1 fades late, <1 fades early)",
                () -> XenoServerConfig.hakaiFadeCurve,
                v -> XenoServerConfig.hakaiFadeCurve = Math.max(0.25f, Math.min(4.0f, v)),
                "hakaifadecurve");
        integer("hakaiFadeRestoreTicks", "Ticks a Hakai body takes to fade back to solid",
                () -> XenoServerConfig.hakaiFadeRestoreTicks,
                v -> XenoServerConfig.hakaiFadeRestoreTicks = Math.max(0,
                        Math.min(XenoServerConfig.HAKAI_FADE_RESTORE_TICKS_MAX, v)),
                "hakaifaderestore");
        flt("hakaiFadeSpeed", "Hakai wipe versus channel (2 = ghost gone at half charge)",
                () -> XenoServerConfig.hakaiFadeSpeed,
                v -> XenoServerConfig.hakaiFadeSpeed = Math.max(0.25f, Math.min(4.0f, v)),
                "hakaifadespeed");
        flt("hakaiFadeBand", "Soft height of the Hakai dissolve line (fraction of body)",
                () -> XenoServerConfig.hakaiFadeBand,
                v -> XenoServerConfig.hakaiFadeBand = Math.max(0.04f, Math.min(0.5f, v)),
                "hakaifadeband");
        rgb("hakaiFxColor", "Packed RGB for Hakai dust and silhouette fill",
                () -> XenoServerConfig.hakaiFxColor,
                v -> XenoServerConfig.hakaiFxColor = v & 0xFFFFFF,
                "hakaicolor");
        rgb("hakaiFxRimColor", "Packed RGB for Hakai outline and sparks",
                () -> XenoServerConfig.hakaiFxRimColor,
                v -> XenoServerConfig.hakaiFxRimColor = v & 0xFFFFFF,
                "hakairim");
        bool("effekseerEnabled", "Effekseer effects (AAA Particles); off = vanilla particles",
                () -> XenoServerConfig.effekseerEnabled,
                v -> XenoServerConfig.effekseerEnabled = v,
                "effekseer", "effects");
        bool("effekseerPunches", "Effekseer punch / guard hit effects",
                () -> XenoServerConfig.effekseerPunches,
                v -> XenoServerConfig.effekseerPunches = v,
                "punchfx");
        bool("effekseerHakai", "Effekseer Hakai channel and erase effects",
                () -> XenoServerConfig.effekseerHakai,
                v -> XenoServerConfig.effekseerHakai = v,
                "hakaieffek");
        bool("effekseerMissiles", "Effekseer missile thruster and explosion effects",
                () -> XenoServerConfig.effekseerMissiles,
                v -> XenoServerConfig.effekseerMissiles = v,
                "missilefx");
        flt("effekseerPunchScale", "Punch effect size (0.05-3, heavy hits are 1.6x)",
                () -> XenoServerConfig.effekseerPunchScale,
                v -> XenoServerConfig.effekseerPunchScale = Math.max(0.05f, Math.min(3.0f, v)),
                "punchsize", "punchscale");
        flt("effekseerHakaiScale", "Hakai effect size (0.05-5)",
                () -> XenoServerConfig.effekseerHakaiScale,
                v -> XenoServerConfig.effekseerHakaiScale = XenoServerConfig.effectScale(v),
                "hakaisize", "hakaiscale");
        flt("effekseerMissileScale", "Missile thruster / explosion effect size (0.05-5)",
                () -> XenoServerConfig.effekseerMissileScale,
                v -> XenoServerConfig.effekseerMissileScale = XenoServerConfig.effectScale(v),
                "missilesize", "missilescale");
        bool("effekseerSparking", "Effekseer Sparking aura and start burst (off: vanilla dust aura)",
                () -> XenoServerConfig.effekseerSparking,
                v -> XenoServerConfig.effekseerSparking = v,
                "sparkingfx");
        bool("effekseerShipThrusters", "Effekseer ship thruster plumes (off: vanilla flames)",
                () -> XenoServerConfig.effekseerShipThrusters,
                v -> XenoServerConfig.effekseerShipThrusters = v,
                "thrusterfx");
        flt("effekseerSparkingScale", "Sparking effect size (0.05-5)",
                () -> XenoServerConfig.effekseerSparkingScale,
                v -> XenoServerConfig.effekseerSparkingScale = XenoServerConfig.effectScale(v),
                "sparkingsize", "sparkingscale");
        flt("effekseerThrusterScale", "Ship thruster plume size (0.05-5)",
                () -> XenoServerConfig.effekseerThrusterScale,
                v -> XenoServerConfig.effekseerThrusterScale = XenoServerConfig.effectScale(v),
                "thrustersize", "thrusterscale");
        // One size per effect, named after it: /xenoset missile_explosion 2 (or fxscale_missile_explosion).
        for (net.bullettrain.xenopixelsmod.fx.effek.EffectSlot slot
                : net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.values()) {
            String name = slot.name().toLowerCase(Locale.ROOT);
            flt("fxscale_" + name, "Size of the " + name + " effect, on top of its category (0.05-50)"
                            + (slot == net.bullettrain.xenopixelsmod.fx.effek.EffectSlot.MISSILE_THRUSTER
                            ? "; above 1 the flame also starts further behind the tail" : ""),
                    () -> XenoServerConfig.slotScale(slot),
                    v -> XenoServerConfig.setSlotScale(slot, v),
                    name);
        }
        bool("effekseerKiImpacts", "Ki attack explosions play the punch impact (off: DMZ's explosion)",
                () -> XenoServerConfig.effekseerKiImpacts,
                v -> XenoServerConfig.effekseerKiImpacts = v,
                "kiimpact", "kifx");
        bool("effekseerKiAttacks", "Owned Combat V3 ki: true = AAA HD effects, false = native DragonMineZ rendering (legacy; prefer kiAttackVisualMode)",
                () -> XenoServerConfig.effekseerKiAttacks,
                v -> {
                    XenoServerConfig.effekseerKiAttacks = v;
                    XenoServerConfig.kiAttackVisualMode = v ? "aaa" : "dmz";
                },
                "hdki", "kiattacks");
        // String modes use the bool bridge above plus /xenokiattacks; registry string helper may be absent.
        flt("effekseerKiImpactScale", "Ki explosion impact size (0.05-5)",
                () -> XenoServerConfig.effekseerKiImpactScale,
                v -> XenoServerConfig.effekseerKiImpactScale = XenoServerConfig.effectScale(v),
                "kiimpactsize");
        bool("effekseerSparkingSmooth", "Sparking aura: steadier constant-brightness version (default: classic look)",
                () -> XenoServerConfig.effekseerSparkingSmooth,
                v -> XenoServerConfig.effekseerSparkingSmooth = v,
                "sparkingsmooth");
        flt("effekseerExplosionScale", "Missile explosion size (0.05-50)",
                () -> XenoServerConfig.effekseerExplosionScale,
                v -> XenoServerConfig.effekseerExplosionScale = Math.max(0.05f, Math.min(50.0f, v)),
                "explosionsize", "explosionscale");
        integer("effekseerRange", "Blocks: punch and Hakai effects reach (8-512)",
                () -> XenoServerConfig.effekseerRange,
                v -> XenoServerConfig.effekseerRange = Math.max(8, Math.min(512, v)),
                "effectrange");
        integer("effekseerMissileRange", "Blocks: missile effects reach (8-2048)",
                () -> XenoServerConfig.effekseerMissileRange,
                v -> XenoServerConfig.effekseerMissileRange = Math.max(8, Math.min(2048, v)),
                "missilefxrange");
        integer("effekseerPunchesPerTick", "Server cap on punch effects per tick (1-512)",
                () -> XenoServerConfig.effekseerPunchesPerTick,
                v -> XenoServerConfig.effekseerPunchesPerTick = Math.max(1, Math.min(512, v)),
                "punchcap");
        bool("hakaiFxEnabled", "Hakai dust and silhouette particles",
                () -> XenoServerConfig.hakaiFxEnabled,
                v -> XenoServerConfig.hakaiFxEnabled = v,
                "hakaifx");
        bool("hakaiDustEnabled", "Hakai dust / sparks / caster aura",
                () -> XenoServerConfig.hakaiDustEnabled,
                v -> XenoServerConfig.hakaiDustEnabled = v,
                "hakaidust");
        bool("hakaiSilhouetteEnabled", "Hakai particle silhouette fill",
                () -> XenoServerConfig.hakaiSilhouetteEnabled,
                v -> XenoServerConfig.hakaiSilhouetteEnabled = v,
                "hakaisilhouette");
        rgb("hakaiSilhouetteColor", "Packed RGB for Hakai silhouette fill",
                () -> XenoServerConfig.hakaiSilhouetteColor,
                v -> XenoServerConfig.hakaiSilhouetteColor = v & 0xFFFFFF,
                "hakaisilhouettecolor");
        rgb("hakaiGlowColor", "Packed RGB for the Hakai target outline",
                () -> XenoServerConfig.hakaiGlowColor,
                v -> XenoServerConfig.hakaiGlowColor = v & 0xFFFFFF,
                "hakaiglowcolor");
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
        flt("chargePunchKnockback", "Charged punch knockback distance (1 = original, 0-30)",
                () -> XenoServerConfig.chargePunchKnockback,
                v -> XenoServerConfig.chargePunchKnockback = Math.max(0f, Math.min(30f, v)),
                "punchkb", "punchknockback");
        bool("chargePunchParabolic", "Charged punch throws the target in an arc (parabola)",
                () -> XenoServerConfig.chargePunchParabolic,
                v -> XenoServerConfig.chargePunchParabolic = v,
                "puncharc", "punchparabola");
        flt("chargePunchArcHeight", "Charged punch arc: upward launch at full charge (0-5)",
                () -> XenoServerConfig.chargePunchArcHeight,
                v -> XenoServerConfig.chargePunchArcHeight = Math.max(0f, Math.min(5f, v)),
                "puncharcheight");
        flt("kickTapCharge", "Strength of one click of the charged-kick key (0.25-1; hold to charge more)",
                () -> XenoServerConfig.kickTapCharge,
                v -> XenoServerConfig.kickTapCharge = Math.max(0.25f, Math.min(1f, v)),
                "kicktap");
        flt("kickKnockbackScale", "Kick knockback distance scale",
                () -> XenoServerConfig.kickKnockbackScale,
                v -> XenoServerConfig.kickKnockbackScale = Math.max(0.1f, Math.min(30f, v)),
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
        integer("superCounterWindowTicks", "Super-counter parry window",
                () -> XenoServerConfig.superCounterWindowTicks,
                v -> XenoServerConfig.superCounterWindowTicks = Math.max(4, Math.min(40, v)),
                "counterwindow");
        flt("superCounterKiCost", "Super-counter ki cost",
                () -> XenoServerConfig.superCounterKiCost,
                v -> XenoServerConfig.superCounterKiCost = Math.max(0f, v),
                "counterkicost");
        flt("superCounterDamageScale", "Super-counter damage scale",
                () -> XenoServerConfig.superCounterDamageScale,
                v -> XenoServerConfig.superCounterDamageScale = v > 0f ? v : 1.35f,
                "counterscale");
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
        bool("protectMastersFromCombatKnockback", "Masters immune to Xeno combat knockback",
                () -> XenoServerConfig.protectMastersFromCombatKnockback,
                v -> XenoServerConfig.protectMastersFromCombatKnockback = v,
                "masterkb", "masterknockback");
        bool("migrateCustomNpcsWorldData", "Migrate CustomNPCs world data to My NPCs on load",
                () -> XenoServerConfig.migrateCustomNpcsWorldData,
                v -> XenoServerConfig.migrateCustomNpcsWorldData = v,
                "npcmigrate", "migratenpcs");
        bool("npcDmzStatsAuthoritative", "Use DMZ/Xeno stats exclusively for profiled NPC combat",
                () -> XenoServerConfig.npcDmzStatsAuthoritative,
                v -> XenoServerConfig.npcDmzStatsAuthoritative = v,
                "npcauthority");
        text("npcDamageMode", "Profiled NPC damage mode (dmz, mynpc, or numeric)",
                XenoServerConfig::normalizedNpcDamageMode,
                v -> XenoServerConfig.npcDamageMode = XenoServerConfig.normalizeNpcDamageMode(v, "dmz"),
                "npcdamage", "damagemode");
        flt("npcNumericDamage", "Damage for profiled NPC numeric mode",
                () -> XenoServerConfig.npcNumericDamage,
                v -> XenoServerConfig.npcNumericDamage = XenoServerConfig.clampNpcNumericDamage(v),
                "numericdamage", "npcdamagevalue");
        bool("xenoNpcSizeScalesHitbox", "Native XenoNPC hitbox follows Display Size",
                () -> XenoServerConfig.xenoNpcSizeScalesHitbox,
                v -> XenoServerConfig.xenoNpcSizeScalesHitbox = v,
                "npcsizehitbox", "sizehitbox");
        integer("npcScriptTickInterval", "Ticks between CustomNPCs/MyNPCs scripted tick events",
                () -> XenoServerConfig.npcScriptTickInterval,
                v -> XenoServerConfig.npcScriptTickInterval = Math.max(1, Math.min(20, v)),
                "npctickdelay");
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
        bool("comboRoutesEnabled", "Grant-gated BT3 combo-route strikes",
                () -> XenoServerConfig.comboRoutesEnabled,
                v -> XenoServerConfig.comboRoutesEnabled = v,
                "comboroutes", "rushcomboenabled");
        integer("comboRouteHitCount", "Hits per combo-route string (3 or 4)",
                () -> XenoServerConfig.comboRouteHitCount,
                v -> XenoServerConfig.comboRouteHitCount = v < 4 ? 3 : 4,
                "comboroutehits");
        bool("comboRouteAutoReapproach", "Auto rush back after combo knockback",
                () -> XenoServerConfig.comboRouteAutoReapproach,
                v -> XenoServerConfig.comboRouteAutoReapproach = v,
                "comboroutererush");
        integer("comboRouteMaxReapproach", "Auto re-rush cap (0 or 1)",
                () -> XenoServerConfig.comboRouteMaxReapproach,
                v -> XenoServerConfig.comboRouteMaxReapproach = Math.max(0, Math.min(1, v)));
        flt("comboRouteRange", "Rush-combo start range. 0 = unlimited, no max",
                () -> (float) XenoServerConfig.comboRouteRange,
                v -> XenoServerConfig.comboRouteRange = v,
                "rushcomborange", "comborange");
        flt("comboRouteHitRange", "Rush-combo hit/close range. 0 = unlimited, no max",
                () -> (float) XenoServerConfig.comboRouteHitRange,
                v -> XenoServerConfig.comboRouteHitRange = v,
                "rushcombohitrange");
        integer("comboRouteApproachTimeoutTicks", "Ticks allowed to close on the target",
                () -> XenoServerConfig.comboRouteApproachTimeoutTicks,
                v -> XenoServerConfig.comboRouteApproachTimeoutTicks = Math.max(10, v));
        integer("comboRouteHitTicks", "Ticks between combo-route hits",
                () -> XenoServerConfig.comboRouteHitTicks,
                v -> XenoServerConfig.comboRouteHitTicks = Math.max(2, v));
        flt("comboRouteKiCost", "KI spent to start a combo route",
                () -> (float) XenoServerConfig.comboRouteKiCost,
                v -> XenoServerConfig.comboRouteKiCost = Math.max(0.0, v));
        integer("comboRouteCooldownTicks", "Cooldown after a combo-route cast",
                () -> XenoServerConfig.comboRouteCooldownTicks,
                v -> XenoServerConfig.comboRouteCooldownTicks = Math.max(1, v));
        bool("comboRoutePvpEnabled", "Combo routes against players",
                () -> XenoServerConfig.comboRoutePvpEnabled,
                v -> XenoServerConfig.comboRoutePvpEnabled = v);
        bool("comboRoutePveEnabled", "Combo routes against non-players",
                () -> XenoServerConfig.comboRoutePveEnabled,
                v -> XenoServerConfig.comboRoutePveEnabled = v);
        bool("rushcomboEnabled", "Enable the rush-combo route",
                () -> XenoServerConfig.rushcomboEnabled,
                v -> XenoServerConfig.rushcomboEnabled = v);
        bool("liftcomboEnabled", "Enable the lift-combo route",
                () -> XenoServerConfig.liftcomboEnabled,
                v -> XenoServerConfig.liftcomboEnabled = v);
        flt("rushKnockbackLeftRight", "Xeno rush left/right knockback distance",
                () -> (float) XenoServerConfig.rushKnockbackLeftRight,
                v -> XenoServerConfig.rushKnockbackLeftRight = Math.max(0.0, v));
        flt("rushKnockbackLeftRightUp", "Xeno rush left/right upward knockback",
                () -> (float) XenoServerConfig.rushKnockbackLeftRightUp,
                v -> XenoServerConfig.rushKnockbackLeftRightUp = Math.max(0.0, v));
        flt("rushKnockbackBreaker", "Xeno rush breaker knockback distance",
                () -> (float) XenoServerConfig.rushKnockbackBreaker,
                v -> XenoServerConfig.rushKnockbackBreaker = Math.max(0.0, v));
        flt("rushKnockbackBreakerUp", "Xeno rush breaker upward knockback",
                () -> (float) XenoServerConfig.rushKnockbackBreakerUp,
                v -> XenoServerConfig.rushKnockbackBreakerUp = Math.max(0.0, v));
        flt("rushKnockbackFinisher", "Xeno rush finisher knockback distance",
                () -> (float) XenoServerConfig.rushKnockbackFinisher,
                v -> XenoServerConfig.rushKnockbackFinisher = Math.max(0.0, v));
        flt("rushKnockbackFinisherUp", "Xeno rush finisher upward knockback",
                () -> (float) XenoServerConfig.rushKnockbackFinisherUp,
                v -> XenoServerConfig.rushKnockbackFinisherUp = Math.max(0.0, v));
        flt("rushKnockbackDown", "Xeno rush downward knockback when looking down",
                () -> (float) XenoServerConfig.rushKnockbackDown,
                v -> XenoServerConfig.rushKnockbackDown = Math.max(0.0, v));
        flt("rushKnockbackVerticalPitch", "Look pitch that uses a vertical knockback line",
                () -> (float) XenoServerConfig.rushKnockbackVerticalPitch,
                v -> XenoServerConfig.rushKnockbackVerticalPitch = Math.max(1.0, Math.min(89.0, v)));
        flt("rushComboKnockTravel", "Rush-combo knock travel (how far the opponent is sent)",
                () -> (float) XenoServerConfig.rushComboKnockTravel,
                v -> XenoServerConfig.setRushComboKnockTravel(v),
                "rushcomboknock");
        flt("rushComboKnockUp", "Rush-combo upward knock travel",
                () -> (float) XenoServerConfig.rushComboKnockUp,
                v -> XenoServerConfig.setRushComboKnockUp(v));
        flt("rushComboKnockDown", "Rush-combo downward knock travel",
                () -> (float) XenoServerConfig.rushComboKnockDown,
                v -> XenoServerConfig.setRushComboKnockDown(v));
        flt("comboRouteKnockbackDistance", "Rush-combo knock travel (legacy id)",
                () -> (float) XenoServerConfig.comboRouteKnockbackDistance,
                v -> XenoServerConfig.setRushComboKnockTravel(v));
        flt("comboRouteKnockbackUp", "Rush-combo upward knock travel (legacy id)",
                () -> (float) XenoServerConfig.comboRouteKnockbackUp,
                v -> XenoServerConfig.setRushComboKnockUp(v));
        flt("comboRouteKnockbackDown", "Rush-combo downward knock travel (legacy id)",
                () -> (float) XenoServerConfig.comboRouteKnockbackDown,
                v -> XenoServerConfig.setRushComboKnockDown(v));
        flt("liftComboKnockTravel", "Lift-combo knock travel (how far the opponent is sent)",
                () -> (float) XenoServerConfig.liftComboKnockTravel,
                v -> XenoServerConfig.setLiftComboKnockTravel(v),
                "liftcomboknock");
        flt("liftComboKnockUp", "Lift-combo upward knock travel",
                () -> (float) XenoServerConfig.liftComboKnockUp,
                v -> XenoServerConfig.setLiftComboKnockUp(v));
        flt("liftComboKnockDown", "Lift-combo downward knock travel",
                () -> (float) XenoServerConfig.liftComboKnockDown,
                v -> XenoServerConfig.setLiftComboKnockDown(v));
        bool("zanzokenEnabled", "Zanzoken afterimage dodge",
                () -> XenoServerConfig.zanzokenEnabled,
                v -> XenoServerConfig.zanzokenEnabled = v,
                "zanzoken");
        bool("zanzokenRequireTiming",
                "Zanzoken only fires if a punch lands during the press window. Off: ring on press.",
                () -> XenoServerConfig.zanzokenRequireTiming,
                v -> XenoServerConfig.zanzokenRequireTiming = v,
                "zanzokentiming", "zanzokenwindowrequired");
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
        integer("zanzokenGhostFadeMode", "Zanzoken ghost fade: 1 semi fade, 2 solid fade, 3 constant alpha",
                () -> XenoServerConfig.zanzokenGhostFadeMode,
                v -> XenoServerConfig.zanzokenGhostFadeMode = Math.max(1, Math.min(3, v)),
                "zanzokenfade");
        flt("zanzokenGhostAlpha", "Zanzoken semi-transparent ghost alpha",
                () -> XenoServerConfig.zanzokenGhostAlpha,
                v -> XenoServerConfig.zanzokenGhostAlpha = Math.max(0.05f, Math.min(1.0f, v)),
                "zanzokenalpha");
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
        bool("zanzokenRingHitable", "Players can destroy standing Zanzoken ring copies",
                () -> XenoServerConfig.zanzokenRingHitable,
                v -> XenoServerConfig.zanzokenRingHitable = v,
                "zanzokenhitable");
        bool("zanzokenRingDisperseAll", "A player hit on one Zanzoken copy takes the whole ring",
                () -> XenoServerConfig.zanzokenRingDisperseAll,
                v -> XenoServerConfig.zanzokenRingDisperseAll = v,
                "zanzokendisperseall");
        bool("zanzokenRingPopOne", "A player hit pops only that Zanzoken copy (inverse of disperse-all)",
                () -> !XenoServerConfig.zanzokenRingDisperseAll,
                v -> XenoServerConfig.zanzokenRingDisperseAll = !v,
                "zanzokenpopone");
        flt("zanzokenDetectRange", "How far Zanzoken fools nearby AI onto afterimages",
                () -> (float) XenoServerConfig.clampedZanzokenDetectRange(),
                v -> XenoServerConfig.zanzokenDetectRange =
                        net.bullettrain.xenopixelsmod.combat.clone.CloneDetectRange.clamp(v),
                "zanzokendetect", "zandetect");
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
        flt("multiFormRadius", "Distance multi-form copies stand from the fighter",
                () -> XenoServerConfig.clampedMultiFormRadius(),
                v -> XenoServerConfig.multiFormRadius = XenoServerConfig.clampMultiFormRadius(v),
                "multiformradius", "multiformdistance");
        text("multiFormAi", "Multi-form copy AI: clone, brain or multiform",
                () -> XenoServerConfig.normalizeMultiFormAi(XenoServerConfig.multiFormAi),
                v -> XenoServerConfig.multiFormAi = XenoServerConfig.normalizeMultiFormAi(v),
                "multiformai", "cloneai");
        bool("multiFormRetaliate", "Copies fight whoever just hit the split fighter",
                () -> XenoServerConfig.multiFormRetaliate,
                v -> XenoServerConfig.multiFormRetaliate = v,
                "multiformretaliate");
        bool("multiFormHostile", "Copies pick a nearby hostile without lock-on",
                () -> XenoServerConfig.multiFormHostile,
                v -> XenoServerConfig.multiFormHostile = v,
                "multiformhostile");
        bool("multiFormLook", "Copies fight what the split fighter is looking at",
                () -> XenoServerConfig.multiFormLook,
                v -> XenoServerConfig.multiFormLook = v,
                "multiformlook");
        bool("multiFormVanish", "Copies may vanish while fighting",
                () -> XenoServerConfig.multiFormVanish,
                v -> XenoServerConfig.multiFormVanish = v,
                "multiformvanish");
        flt("multiFormDetectRange", "How far multi-form copies notice and fight a target",
                () -> (float) XenoServerConfig.clampedMultiFormDetectRange(),
                v -> XenoServerConfig.multiFormDetectRange =
                        net.bullettrain.xenopixelsmod.combat.clone.CloneDetectRange.clamp(v),
                "multiformdetect", "multiformleash");
        flt("brainDeflectMinDistance", "No NPC ki-deflect closer than this many blocks (0 = any range)",
                () -> (float) XenoServerConfig.clampedBrainDeflectMinDistance(),
                v -> XenoServerConfig.brainDeflectMinDistance =
                        XenoServerConfig.clampBrainDeflectMinDistance(v),
                "npckideflectmin", "deflectmindistance");
        flt("npcAttackStartRadius", "Blocks an NPC must close to before melee attacks start "
                        + "(per-NPC Melee Range still wins; 4.5 = old DMZ band)",
                () -> (float) XenoServerConfig.clampedNpcAttackStartRadius(),
                v -> XenoServerConfig.npcAttackStartRadius =
                        XenoServerConfig.clampNpcAttackStartRadius(v),
                "npcattackradius", "attackradius", "attackstartradius");
        bool("npcMeleeHeightRule", "NPC melee reaches targets a block or two up/down "
                        + "(off: old straight-line distance)",
                () -> XenoServerConfig.npcMeleeHeightRule,
                v -> XenoServerConfig.npcMeleeHeightRule = v,
                "npcheightreach", "meleeheightrule");
        flt("npcMeleeHeightReach", "Blocks of air allowed between NPC and target hitboxes for melee (0-8)",
                () -> (float) XenoServerConfig.clampNpcMeleeHeightReach(XenoServerConfig.npcMeleeHeightReach),
                v -> XenoServerConfig.npcMeleeHeightReach = XenoServerConfig.clampNpcMeleeHeightReach(v),
                "meleeheight", "npcmeleeheight");
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
        integer("sparkingDurationTicks", "Sparking length in ticks; also sets the ki drain speed",
                () -> XenoServerConfig.sparkingDurationTicks,
                v -> XenoServerConfig.sparkingDurationTicks = Math.max(20, Math.min(1200, v)),
                "sparkingduration", "sparkinglength");
        integer("sparkingChargeTicks", "Full-ki charge time before Sparking activates",
                () -> XenoServerConfig.sparkingChargeTicks,
                v -> XenoServerConfig.sparkingChargeTicks = Math.max(20, Math.min(1200, v)),
                "sparkingcharge", "sparkingmaxpower");
        integer("sparkingCooldownTicks", "Ticks after Sparking before it can be used again",
                () -> XenoServerConfig.sparkingCooldownTicks,
                v -> XenoServerConfig.sparkingCooldownTicks = Math.max(0, Math.min(12000, v)),
                "sparkingcooldown", "sparkingcd");
        integer("sparkingReleaseLimit", "Release ceiling while Sparking, in percent",
                () -> XenoServerConfig.sparkingReleaseLimit,
                v -> XenoServerConfig.sparkingReleaseLimit = Math.max(1, Math.min(1000, v)),
                "sparkingrelease", "sparkinglimit");
        flt("sparkingMoveSpeedMult", "Movement speed multiplier while Sparking",
                () -> XenoServerConfig.sparkingMoveSpeedMult,
                v -> XenoServerConfig.sparkingMoveSpeedMult = Math.max(1f, Math.min(4f, v)),
                "sparkingmove", "sparkingspeed");
        flt("sparkingAttackSpeedMult", "Attack speed multiplier while Sparking",
                () -> XenoServerConfig.sparkingAttackSpeedMult,
                v -> XenoServerConfig.sparkingAttackSpeedMult = Math.max(1f, Math.min(4f, v)),
                "sparkingattack", "sparkingatkspeed");
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
        bool("dmzFormProtectedEditOverride",
                "DANGER: allow validated Form Studio writes to native/bundled DMZ groups",
                () -> XenoServerConfig.dmzFormProtectedEditOverride,
                v -> XenoServerConfig.dmzFormProtectedEditOverride = v,
                "formeditoverride", "dmzformoverride");
        bool("trainingDummyEnabled", "Training dummy",
                () -> XenoServerConfig.trainingDummyEnabled,
                v -> XenoServerConfig.trainingDummyEnabled = v,
                "dummy", "trainingdummy");
        bool("trainingDummySkillPoints", "Training dummies pay a skill point at 25, 100 and 250 hits",
                () -> XenoServerConfig.trainingDummySkillPoints,
                v -> XenoServerConfig.trainingDummySkillPoints = v,
                "dummypoints");
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
        bool("npcCommandsIgnoreCommandBlockSetting",
                "Let NPC quest/dialog commands run while command blocks are disabled",
                () -> XenoServerConfig.npcCommandsIgnoreCommandBlockSetting,
                v -> XenoServerConfig.npcCommandsIgnoreCommandBlockSetting = v,
                "npccommands", "npccommandblock");
        text("combatControllerMode",
                "Combat controller: legacy (default, v1), bt3_manual, or v2 (XenoCombat v2). "
                        + "Prefer /xenocombat mode so live combat state is swept on switch",
                XenoServerConfig::normalizedCombatControllerMode,
                v -> XenoServerConfig.combatControllerMode = XenoServerConfig.normalizeCombatControllerMode(v),
                "combatmode", "controllermode", "combatcontroller", "bt3mode");
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
        flt("kiExplosionMaxRadius", "Ki-explosion entity radius cap (0 = uncapped)",
                () -> XenoServerConfig.kiExplosionMaxRadius,
                v -> XenoServerConfig.kiExplosionMaxRadius = Float.isFinite(v) && v >= 0f ? v : 64f,
                "explosion", "maxexplosion");
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
                    int parsed = parseIntOrHex(value);
                    key.applyRaw.accept(Integer.toString(parsed));
                }
                case FLOAT -> {
                    float parsed = Float.parseFloat(value);
                    if (!Float.isFinite(parsed)) {
                        return Result.fail("Value must be finite for " + key.id);
                    }
                    key.applyRaw.accept(Float.toString(parsed));
                }
                case TEXT -> key.applyRaw.accept(value);
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
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        for (Key key : BY_ID.values()) {
            if (key.id.toLowerCase(Locale.ROOT).startsWith(p)) unique.add(key.id);
        }
        for (Map.Entry<String, String> alias : ALIAS.entrySet()) {
            if (!alias.getKey().startsWith(p)) continue;
            Key key = BY_ID.get(alias.getValue());
            if (key != null) unique.add(key.id);
        }
        List<String> out = new ArrayList<>(unique);
        Collections.sort(out);
        return out;
    }

    /**
     * Copies leftover alias JSON keys onto the canonical {@code Data} field name, then drops
     * the aliases. Canonical wins when both are present — that is the field {@code apply()} reads.
     *
     * @return true if the object was mutated
     */
    public static boolean promoteCanonicalFields(com.google.gson.JsonObject json) {
        if (json == null || json.entrySet().isEmpty()) return false;
        Map<String, String> present = new LinkedHashMap<>();
        for (String key : json.keySet()) {
            present.put(key.toLowerCase(Locale.ROOT), key);
        }
        boolean changed = false;
        for (Map.Entry<String, String> alias : List.copyOf(ALIAS.entrySet())) {
            String aliasLower = alias.getKey();
            String canonical = alias.getValue();
            if (canonical == null || aliasLower.equals(canonical.toLowerCase(Locale.ROOT))) continue;
            String jsonAlias = present.get(aliasLower);
            if (jsonAlias == null) continue;
            String jsonCanonical = present.get(canonical.toLowerCase(Locale.ROOT));
            if (jsonCanonical == null) {
                json.add(canonical, json.get(jsonAlias));
                present.put(canonical.toLowerCase(Locale.ROOT), canonical);
            }
            json.remove(jsonAlias);
            present.remove(aliasLower);
            changed = true;
        }
        return changed;
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
                raw -> set.accept(parseIntOrHex(raw)));
        register(key, aliases);
    }

    private static void rgb(String id, String help, Supplier<Integer> get, Consumer<Integer> set,
                            String... aliases) {
        Key key = new Key(id, Kind.INT, help,
                () -> String.format("0x%06X", get.get() & 0xFFFFFF),
                raw -> set.accept(parseIntOrHex(raw) & 0xFFFFFF));
        register(key, aliases);
    }

    /** Decimal, {@code 0xRRGGBB}, or {@code #RRGGBB}. */
    static int parseIntOrHex(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X") || trimmed.startsWith("#")) {
            return Integer.decode(trimmed);
        }
        return Integer.parseInt(trimmed);
    }

    private static void flt(String id, String help, Supplier<Float> get, Consumer<Float> set,
                            String... aliases) {
        Key key = new Key(id, Kind.FLOAT, help,
                () -> formatFloat(get.get()),
                raw -> set.accept(Float.parseFloat(raw)));
        register(key, aliases);
    }

    private static void text(String id, String help, Supplier<String> get, Consumer<String> set,
                             String... aliases) {
        Key key = new Key(id, Kind.TEXT, help, get, set::accept);
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
