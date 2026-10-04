package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import net.bullettrain.xenopixelsmod.npc.NpcDoorInteract;
import net.bullettrain.xenopixelsmod.npc.NpcOnFoundEnemy;
import net.bullettrain.xenopixelsmod.npc.NpcShelterFrom;
import net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour;
import net.bullettrain.xenopixelsmod.npc.XenoNpcJob;
import net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the verified, player-shaped fields in a placed MyNPCs/CustomNPCs tag into the native
 * Xeno profile. The complete source tag remains under {@code NeoForgeData/XenoPixelsSource}; a
 * copied field is not treated as supported merely because it was preserved.
 */
public final class PlacedNpcProfileMigrator {
    private static final String NEOFORGE_DATA = "NeoForgeData";
    private static final String PROFILE = NpcCombatProfile.NBT_KEY;
    private static final String SOURCE = "XenoPixelsSource";
    private static final int SOURCE_SCHEMA = 1;

    /**
     * Where NeoForge persists attachments on a saved entity, and the id our DMZ stats attachment
     * was registered under ({@code DeferredRegister(ATTACHMENT_TYPES, "xenopixelsmod")} +
     * {@code npc_dmz_stats} in {@code NpcDmzStats}).
     */
    private static final String ATTACHMENTS = "neoforge:attachments";
    private static final String DMZ_ATTACHMENT = "xenopixelsmod:npc_dmz_stats";
    /** The pre-attachment reference shape, still read so older saved references keep working. */
    private static final String DMZ_ATTACHMENT_LEGACY = "xenopixels:npc_dmz_stats";

    /** CNPC's per-NPC damage reduction block: four floats where 1.0 means "damage unchanged". */
    static final String KEY_RESISTANCES = "Resistances";
    /** CNPC's job selector, stored as the {@code JobType} ordinal. */
    static final String KEY_JOB = "NpcJob";
    static final String KEY_SCRIPTS = "Scripts";
    static final String KEY_SCRIPT_LANGUAGE = "ScriptLanguage";
    static final String KEY_SCRIPT_ENABLED = "ScriptEnabled";

    /**
     * CNPC line groups and the native category each one speaks for. Verified against
     * {@code noppes.npcs.entity.data.DataAdvanced}; each group is {@code {Lines: [{Line, Song,
     * Slot}]}}. {@code NpcInteractNPCLines} is absent on purpose - NPC-to-NPC speech has no native
     * category and is reported instead, as is {@code OrderedLines} (the native player always cycles).
     */
    private static final Map<String, XenoNpcLines.Category> LINE_GROUPS = java.util.Collections
            .unmodifiableMap(lineGroups());

    private static Map<String, XenoNpcLines.Category> lineGroups() {
        Map<String, XenoNpcLines.Category> map = new LinkedHashMap<>();
        map.put("NpcInteractLines", XenoNpcLines.Category.INTERACT);
        map.put("NpcAttackLines", XenoNpcLines.Category.ATTACK);
        map.put("NpcKillLines", XenoNpcLines.Category.KILL);
        map.put("NpcKilledLines", XenoNpcLines.Category.KILLED);
        map.put("NpcLines", XenoNpcLines.Category.RANDOM);
        return map;
    }

    private PlacedNpcProfileMigrator() {
    }

    /** Applies the migration once and returns whether a native profile was written. */
    public static boolean migrate(CompoundTag entity, String sourceMod) {
        return migrate(entity, sourceMod, entity);
    }

    /**
     * Applies a profile from {@code entity} while retaining the unmodified source snapshot.
     * Conversion callers use this overload because identity fields are rewritten before the native
     * replacement is loaded; the audit payload must still describe the source entity, not that
     * intermediate representation.
     */
    public static boolean migrate(CompoundTag entity, String sourceMod, CompoundTag sourceSnapshot) {
        return migrate(entity, sourceMod, sourceSnapshot, "");
    }

    /**
     * As {@link #migrate(CompoundTag, String, CompoundTag)}, binding {@code scriptId} into the new
     * profile.
     *
     * <p>The script text itself never lives in the profile - only the reference does, so the caller
     * that owns the world store writes the entries first ({@link NpcScriptImport}) and hands back the
     * one id this NPC should run. A blank id leaves the NPC script-less, which is the ordinary state.
     */
    public static boolean migrate(CompoundTag entity, String sourceMod, CompoundTag sourceSnapshot,
                                  String scriptId) {
        return migrate(entity, sourceMod, sourceSnapshot, net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer.fromLegacy(scriptId));
    }

    /**
     * As above, carrying every imported script as its own tab (CustomNPCs keeps one script per
     * tab, each with its own globals), plus the source NPC's language and enabled switch.
     */
    public static boolean migrate(CompoundTag entity, String sourceMod, CompoundTag sourceSnapshot,
                                  net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer scripts) {
        if (entity == null) {
            return false;
        }
        CompoundTag data = entity.contains(NEOFORGE_DATA, Tag.TAG_COMPOUND)
                ? entity.getCompound(NEOFORGE_DATA) : new CompoundTag();
        if (data.contains(PROFILE, Tag.TAG_COMPOUND)
                && data.getCompound(PROFILE).getInt("Schema") > 0) {
            return false;
        }

        CompoundTag source = sourceSnapshot == null ? entity.copy() : sourceSnapshot.copy();
        CompoundTag profileTag = buildProfile(entity, scripts);
        data.put(PROFILE, profileTag);
        preserveSource(data, source, sourceMod);
        entity.put(NEOFORGE_DATA, data);
        return true;
    }

    /** Builds the profile without touching the input, which makes the mapping unit-testable. */
    public static CompoundTag profileTag(CompoundTag source) {
        return profileTag(source, "");
    }

    /** Builds the profile that {@link #migrate} would write, bound to {@code scriptId}. */
    public static CompoundTag profileTag(CompoundTag source, String scriptId) {
        return buildProfile(source == null ? new CompoundTag() : source, net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer.fromLegacy(scriptId));
    }

    private static CompoundTag buildProfile(CompoundTag source, net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer scripts) {
        NpcCombatProfile profile = new NpcCombatProfile();
        CompoundTag dmzProfile = nested(source, "NeoForgeData", "XenoPixelsProfile");
        CompoundTag attachment = dmzAttachment(source);
        CompoundTag character = nested(attachment, "Character");
        CompoundTag stats = nested(attachment, "Stats");
        CompoundTag resources = nested(attachment, "Resources");

        // The source DMZ attachment is authoritative when present; the flat fields are the
        // verified fallback used by older placed entities and clones.
        CompoundTag dmzCharacter = firstCompound(character,
                nested(attachment, "CharacterData"), nested(source, "Character"));
        CompoundTag dmzStats = firstCompound(stats,
                nested(attachment, "StatsData"), nested(source, "Stats"));
        CompoundTag dmzResources = firstCompound(resources, nested(attachment, "ResourcesData"),
                nested(source, "Resources"));

        profile.raceId = firstString(dmzCharacter, dmzProfile, source, "Race", "human");
        profile.strength = firstInt(dmzStats, dmzProfile, source, "STR", "Strength", 0);
        profile.strikePower = firstInt(dmzStats, dmzProfile, source, "SKP", "StrikePower", 0);
        profile.resistance = firstInt(dmzStats, dmzProfile, source, "RES", "Resistance", 0);
        profile.vitality = firstInt(dmzStats, dmzProfile, source, "VIT", "Vitality", 0);
        profile.kiPower = firstInt(dmzStats, dmzProfile, source, "PWR", "KiPower", 0);
        profile.energy = firstInt(dmzStats, dmzProfile, source, "ENE", "Energy", 0);
        profile.powerReleasePercent = clampRelease(firstInt(dmzResources, dmzProfile, source,
                "Release", "PowerReleasePercent", 100));

        NpcDmzAppearance appearance = profile.appearance;
        appearance.mode = NpcDmzAppearance.Mode.FULL;
        appearance.gender = firstString(dmzCharacter, dmzProfile, source, "Gender", appearance.gender);
        appearance.characterClass = firstString(dmzCharacter, dmzProfile, source, "Class", appearance.characterClass);
        appearance.bodyType = firstInt(dmzCharacter, dmzProfile, source, "BodyType", 0);
        appearance.eyesType = firstInt(dmzCharacter, dmzProfile, source, "EyesType", 0);
        appearance.eyebrowsType = firstInt(dmzCharacter, dmzProfile, source, "EyebrowsType", appearance.eyesType);
        appearance.noseType = firstInt(dmzCharacter, dmzProfile, source, "NoseType", 0);
        appearance.mouthType = firstInt(dmzCharacter, dmzProfile, source, "MouthType", 0);
        appearance.tattooType = firstInt(dmzCharacter, dmzProfile, source, "TattooType", 0);
        appearance.boobScale = firstFloat(dmzCharacter, dmzProfile, source, "BoobScale", 1.0f);
        appearance.bodyColor = firstString(dmzCharacter, dmzProfile, source, "BodyColor", appearance.bodyColor);
        appearance.bodyColor2 = firstString(dmzCharacter, dmzProfile, source, "BodyColor2", appearance.bodyColor2);
        appearance.bodyColor3 = firstString(dmzCharacter, dmzProfile, source, "BodyColor3", appearance.bodyColor3);
        appearance.eye1Color = firstString(dmzCharacter, dmzProfile, source, "Eye1Color", appearance.eye1Color);
        appearance.eye2Color = firstString(dmzCharacter, dmzProfile, source, "Eye2Color", appearance.eye2Color);
        appearance.tailColor = firstString(dmzCharacter, dmzProfile, source, "TailColor", "");
        appearance.tailUseRaceColor = appearance.tailColor.isBlank();
        appearance.activeHeadBone = firstString(dmzCharacter, dmzProfile, source, "ActiveHeadBone", "");
        appearance.saiyanTail = firstBoolean(dmzCharacter, dmzProfile, source, "HasSaiyanTail", "SaiyanTail", false);
        appearance.renderHairBase = !dmzCharacter.contains("RenderHairBase")
                || dmzCharacter.getBoolean("RenderHairBase");

        profile.formGroup = firstString(dmzCharacter, dmzProfile, source, "CurrentFormGroup", "");
        profile.formId = firstString(dmzCharacter, dmzProfile, source, "CurrentForm", "");
        profile.selectedFormGroup = firstString(dmzCharacter, dmzProfile, source, "SelectedFormGroup", profile.formGroup);
        profile.selectedFormId = firstString(dmzCharacter, dmzProfile, source, "SelectedForm", profile.formId);
        profile.stackGroup = firstString(dmzCharacter, dmzProfile, source, "CurrentStackFormGroup", "");
        profile.stackId = firstString(dmzCharacter, dmzProfile, source, "CurrentStackForm", "");
        profile.selectedStackGroup = firstString(dmzCharacter, dmzProfile, source, "SelectedStackFormGroup", profile.stackGroup);
        profile.selectedStackId = firstString(dmzCharacter, dmzProfile, source, "SelectedStackForm", profile.stackId);

        profile.skinPlayer = firstString(dmzProfile, dmzCharacter, source, "SkinPlayer", "");
        profile.skinUrl = firstString(dmzProfile, dmzCharacter, source, "SkinUrl", "");
        profile.modelTexture = firstString(source, dmzProfile, "Texture", "");
        profile.modelId = firstString(source, dmzProfile, "ModelId", "");
        profile.modelAnimation = firstString(source, dmzProfile, "ModelAnimation", "");
        profile.modelKind = modelKind(firstInt(source, dmzProfile, new CompoundTag(), "ModelType", 0), profile.modelId);
        profile.modelTint = firstInt(source, dmzProfile, new CompoundTag(), "ModelTint", profile.modelTint);
        profile.modelGlowing = firstBoolean(source, dmzProfile, "ModelGlowing", false);
        profile.baseSize = Math.max(0, firstInt(source, dmzProfile, new CompoundTag(), "Size", "BaseSize", 0));
        profile.hairEnabled = dmzCharacter.contains("HairBase") || !firstString(dmzCharacter, "HairColor", "").isBlank();
        profile.hairColor = firstString(dmzCharacter, dmzProfile, source, "HairColor", "");
        profile.hairStyleId = firstInt(dmzCharacter, dmzProfile, source, "HairId", "HairStyleId", 0);

        applyJob(profile, source);
        applyProtection(profile, source);
        applyAi(profile, source);
        applyStats(profile, source);
        applyDisplay(profile, source);
        applyMelee(profile, source);
        applyRanged(profile, source);
        applySounds(profile, source);
        applyLines(profile, source);
        if (scripts != null && !scripts.isEmpty()) {
            profile.scripts = scripts;
            profile.scriptId = scripts.firstScriptId();
        }

        CompoundTag out = profile.toTag();
        copyEquipment(source, out);
        return out;
    }

    /**
     * Native job id for a verified CNPC {@code JobType} ordinal.
     *
     * <p>{@link XenoNpcJob} is declared in the same order as the source enum (NONE, BARD, HEALER,
     * GUARD, ITEMGIVER, FOLLOWER, SPAWNER, CONVERSATION, CHUNKLOADER, PUPPET, BUILDER, FARMER),
     * which was checked against the shipped {@code JobType} constants and each {@code roles.Job*}
     * class rather than assumed. Anything outside that range has no equivalent and yields "".
     */
    public static String jobIdFor(int ordinal) {
        XenoNpcJob[] jobs = XenoNpcJob.values();
        return ordinal >= 0 && ordinal < jobs.length ? jobs[ordinal].id() : "";
    }

    /** Source line groups this migrator reads, in the order it reads them. */
    public static List<String> consumedLineKeys() {
        return List.copyOf(LINE_GROUPS.keySet());
    }

    /**
     * Whether any imported line asks for a per-line sound.
     *
     * <p>CNPC stores a {@code Song} alongside each line and plays it with the bubble. The native
     * profile carries text only, so this is the fact the report needs in order to say what was lost
     * instead of leaving it unmentioned.
     */
    public static boolean usesLineSounds(CompoundTag source) {
        for (String key : LINE_GROUPS.keySet()) {
            ListTag entries = nested(source, key).getList("Lines", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                if (!entries.getCompound(i).getString("Song").isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** CNPC's job ordinal, or -1 when the NPC carries none. */
    static int sourceJobOrdinal(CompoundTag source) {
        return source.contains(KEY_JOB) ? source.getInt(KEY_JOB) : -1;
    }

    private static void applyJob(NpcCombatProfile profile, CompoundTag source) {
        String job = jobIdFor(sourceJobOrdinal(source));
        if (!job.isEmpty()) {
            profile.job = job;
        }
    }

    /**
     * Maps the CNPC protection block onto the native profile.
     *
     * <p>Every key is checked for presence first: these are booleans whose native defaults are not
     * uniformly false ({@code canDrown} and {@code cobwebAffected} both default to true), so writing
     * a default for a source that never mentioned them would silently change the NPC.
     */
    private static void applyProtection(NpcCombatProfile profile, CompoundTag source) {
        CompoundTag resistances = nested(source, KEY_RESISTANCES);
        profile.npcMeleeResistance = resistance(profile.npcMeleeResistance, resistances, "Melee");
        profile.npcArrowResistance = resistance(profile.npcArrowResistance, resistances, "Arrow");
        profile.npcExplosionResistance = resistance(profile.npcExplosionResistance, resistances,
                "Explosion");
        profile.npcKnockbackResistance = resistance(profile.npcKnockbackResistance, resistances,
                "Knockback");
        if (source.contains("ImmuneToFire")) {
            profile.fireImmune = source.getBoolean("ImmuneToFire");
        }
        if (source.contains("PotionImmune")) {
            profile.potionImmune = source.getBoolean("PotionImmune");
        }
        if (source.contains("NoFallDamage")) {
            profile.noFallDamage = source.getBoolean("NoFallDamage");
        }
        if (source.contains("CanDrown")) {
            profile.canDrown = source.getBoolean("CanDrown");
        }
        if (source.contains("IgnoreCobweb")) {
            profile.cobwebAffected = !source.getBoolean("IgnoreCobweb");
        }
        if (source.contains("BossBar")) {
            profile.bossBar = source.getBoolean("BossBar");
        }
    }

    /**
     * CNPC applies {@code damage *= (2 - r)} with r = 1.0 meaning no change; the native field is a
     * reduction percentage. So r = 1.5 is 50 and r = 2.0 is full immunity. Anything below 1.0 asks
     * for extra damage, which the native field cannot express; the current value is kept and
     * {@link NpcSourceKeys#audit} says which keys were left behind.
     */
    private static int resistance(int current, CompoundTag source, String key) {
        if (!source.contains(key)) {
            return current;
        }
        float value = source.getFloat(key);
        if (!Float.isFinite(value) || value <= 1.0f) {
            return current;
        }
        return NpcCombatProfile.clampNpcResistance((int) Math.round((value - 1.0f) * 100.0));
    }

    /**
     * Maps CNPC's {@code DataAI} block onto the native AI switches and the three enum selectors.
     *
     * <p>Ordinal semantics were read out of the shipped jar, not guessed: {@code OnAttack}
     * {Retaliate, Panic, Retreat, Nothing} matches {@link NpcOnFoundEnemy} exactly,
     * {@code FindShelter} {Darkness, Sunlight, Disabled} matches {@link NpcShelterFrom} exactly,
     * and {@code DoorInteract} {Break, Open, Disabled} runs in the opposite order from
     * {@link NpcDoorInteract} {Disabled, Open, Break} - hence the {@code 2 - value} flip. Booleans
     * are copied only when present because the native defaults are not uniformly false
     * ({@code aiCanSwim}, {@code aiReturnToStart} and {@code aiMustSeeTarget} all default to true).
     */
    private static void applyAi(NpcCombatProfile profile, CompoundTag source) {
        if (source.contains("CanSwim")) {
            profile.aiCanSwim = source.getBoolean("CanSwim");
        }
        if (source.contains("AvoidsWater")) {
            profile.aiAvoidsWater = source.getBoolean("AvoidsWater");
        }
        if (source.contains("CanLeap")) {
            profile.aiLeapAtTarget = source.getBoolean("CanLeap");
        }
        if (source.contains("ReturnToStart")) {
            profile.aiReturnToStart = source.getBoolean("ReturnToStart");
        }
        if (source.contains("DirectLOS")) {
            profile.aiMustSeeTarget = source.getBoolean("DirectLOS");
        }
        if (source.contains("AttackInvisible")) {
            profile.aiAttackInvisible = source.getBoolean("AttackInvisible");
        }
        if (source.contains("MountControl")) {
            profile.aiMountControl = source.getBoolean("MountControl");
        }
        if (source.contains("OnAttack")) {
            profile.aiOnFoundEnemy = NpcOnFoundEnemy.byIndex(source.getInt("OnAttack"));
        }
        if (source.contains("DoorInteract")) {
            profile.aiDoorInteract = NpcDoorInteract.byIndex(2 - source.getInt("DoorInteract"));
        }
        if (source.contains("FindShelter")) {
            profile.aiShelterFrom = NpcShelterFrom.byIndex(source.getInt("FindShelter"));
        }
    }

    /**
     * Maps CNPC's {@code DataStats} health, regen and creature-family knobs.
     *
     * <p>Regen semantics were verified in the shipped {@code EntityNPCInterface}: CNPC heals
     * {@code HealthRegen} HP while out of combat and {@code CombatRegen} HP while in combat, once
     * every 20 ticks - i.e. both are HP per second, which is exactly what the native float fields
     * mean, so the values carry over directly. {@code CreatureType} ordinals
     * {normal, undead, arthropod, illager, aquatic} are in the same order as
     * {@link XenoNpcBehaviour#creatureTypes()}, so the index names the family.
     */
    private static void applyStats(NpcCombatProfile profile, CompoundTag source) {
        if (source.contains("BurnInSun")) {
            profile.burnsInSun = source.getBoolean("BurnInSun");
        }
        if (source.contains("MaxHealth")) {
            int maxHealth = source.getInt("MaxHealth");
            if (maxHealth > 0) {
                profile.maxHealthOverride = maxHealth;
            }
        }
        if (source.contains("HealthRegen")) {
            profile.healthRegen = Math.max(0.0f, source.getInt("HealthRegen"));
        }
        if (source.contains("CombatRegen")) {
            profile.combatRegen = Math.max(0.0f, source.getInt("CombatRegen"));
        }
        if (source.contains("CreatureType")) {
            List<String> families = XenoNpcBehaviour.creatureTypes();
            int index = Math.max(0, Math.min(families.size() - 1, source.getInt("CreatureType")));
            profile.creatureType = families.get(index);
        }
    }

    /**
     * Maps the two {@code DataDisplay} knobs that have native counterparts.
     *
     * <p>{@code BossColor} is the {@link BossEvent.BossBarColor} ordinal (default PINK), and the
     * native profile stores the enum name. {@code NpcVisible} is {Yes, No, Partly}; "Partly" gates
     * visibility behind the NPC's availability rules, which have no native equivalent, so it
     * degrades to visible and the availability keys stay reported as unsupported.
     */
    private static void applyDisplay(NpcCombatProfile profile, CompoundTag source) {
        if (source.contains("BossColor")) {
            BossEvent.BossBarColor[] colors = BossEvent.BossBarColor.values();
            int index = Math.max(0, Math.min(colors.length - 1, source.getInt("BossColor")));
            profile.bossBarColor = colors[index].name();
        }
        if (source.contains("NpcVisible")) {
            profile.visible = source.getInt("NpcVisible") != 1;
        }
    }

    /**
     * Maps CNPC's {@code DataMelee} block onto the native melee props.
     *
     * <p>{@code AttackSpeed} is the source's ticks-between-swings counter (its own default is 20,
     * one swing per second); the native field is a multiplier whose 1.0 is that same reference
     * cadence, so the conversion is {@code 20 / ticks}. {@code PotionEffect} and friends stay
     * behind: the source stores an index into its editor dropdown and no verified mapping to
     * registry ids exists.
     */
    private static void applyMelee(NpcCombatProfile profile, CompoundTag source) {
        if (source.contains("AttackStrenght")) {
            profile.npcMeleeDamage = NpcCombatProfile.clampNpcMeleeDamage(
                    source.getInt("AttackStrenght"));
        }
        if (source.contains("AttackRange")) {
            profile.npcMeleeRange = NpcCombatProfile.clampNpcMeleeRange(
                    source.getInt("AttackRange"));
        }
        if (source.contains("AttackSpeed")) {
            int ticks = source.getInt("AttackSpeed");
            if (ticks > 0) {
                profile.npcMeleeSpeed = NpcCombatProfile.clampNpcMeleeSpeed(20.0f / ticks);
            }
        }
        if (source.contains("KnockBack")) {
            profile.npcMeleeKnockback = NpcCombatProfile.clampNpcProjectileKnockback(
                    source.getInt("KnockBack"));
        }
    }

    /**
     * Maps CNPC's {@code DataRanged} block onto the native ranged props.
     *
     * <p>{@code Accuracy} is a 0..100 percent in the source and a 0..1 blend natively. The source
     * fires ranged only beyond {@code DistanceToMelee}, which is what the native minimum range
     * means, and repeats each burst {@code FireRate} ticks apart, matching
     * {@code npcRangedBurstRate}. {@code minDelay}/{@code maxDelay} keep their lowercase source
     * spelling in the export - verified against real clone files - and land on the native attack
     * delay window. {@code AimWhileShooting} selects between the native "distant" and "no" aim
     * modes; "hidden" is an editor-only mode the source has no notion of.
     */
    private static void applyRanged(NpcCombatProfile profile, CompoundTag source) {
        if (source.contains("Accuracy")) {
            profile.npcRangedAccuracy = NpcCombatProfile.clampNpcRangedAccuracy(
                    source.getInt("Accuracy") / 100.0f);
        }
        if (source.contains("MaxFiringRange")) {
            profile.npcRangedRange = NpcCombatProfile.clampNpcRangedRange(
                    source.getInt("MaxFiringRange"));
        }
        if (source.contains("DistanceToMelee")) {
            profile.npcRangedMinRange = NpcCombatProfile.clampNpcRangedRange(
                    source.getInt("DistanceToMelee"));
        }
        if (source.contains("minDelay")) {
            profile.npcRangedMinDelay = NpcCombatProfile.clampNpcRangedDelay(
                    source.getInt("minDelay"));
        }
        if (source.contains("maxDelay")) {
            profile.npcRangedMaxDelay = NpcCombatProfile.clampNpcRangedDelay(
                    source.getInt("maxDelay"));
        }
        if (source.contains("ShotCount")) {
            profile.npcRangedShotCount = NpcCombatProfile.clampNpcShotCount(
                    source.getInt("ShotCount"));
        }
        if (source.contains("BurstCount")) {
            profile.npcRangedBurstCount = NpcCombatProfile.clampNpcBurstCount(
                    source.getInt("BurstCount"));
        }
        if (source.contains("FireRate")) {
            profile.npcRangedBurstRate = NpcCombatProfile.clampNpcRangedDelay(
                    source.getInt("FireRate"));
        }
        if (source.contains("FireIndirect")) {
            profile.npcRangedIndirect = source.getInt("FireIndirect") != 0;
        }
        if (source.contains("AimWhileShooting")) {
            profile.npcRangedAimMode = source.getBoolean("AimWhileShooting") ? "distant" : "no";
        }
        copyString(source, "FiringSound", value -> profile.npcRangedFireSound = value);
        copyString(source, "HitSound", value -> profile.npcRangedHitSound = value);
        copyString(source, "GroundSound", value -> profile.npcRangedGroundSound = value);
    }

    /**
     * Maps CNPC's {@code DataAdvanced} sound kit onto the native per-NPC sound fields.
     *
     * <p>The source stores sound-event ids as strings (verified in real clone exports), which is
     * what the native fields hold. {@code DisablePitch} inverts into {@code soundHasPitch}. Empty
     * strings mean "no custom sound" on both sides and are skipped.
     */
    private static void applySounds(NpcCombatProfile profile, CompoundTag source) {
        copyString(source, "NpcIdleSound", value -> profile.soundLiving = value);
        copyString(source, "NpcAngrySound", value -> profile.soundAngry = value);
        copyString(source, "NpcHurtSound", value -> profile.soundHurt = value);
        copyString(source, "NpcDeathSound", value -> profile.soundDeath = value);
        copyString(source, "NpcStepSound", value -> profile.soundStep = value);
        if (source.contains("DisablePitch")) {
            profile.soundHasPitch = !source.getBoolean("DisablePitch");
        }
    }

    /** Copies a non-blank string key into the profile, leaving the native default otherwise. */
    private static void copyString(CompoundTag source, String key,
                                   java.util.function.Consumer<String> target) {
        if (source.contains(key, Tag.TAG_STRING) && !source.getString(key).isBlank()) {
            target.accept(source.getString(key));
        }
    }

    /** Copies each CNPC line group into its native category, in source order. */
    private static void applyLines(NpcCombatProfile profile, CompoundTag source) {
        for (Map.Entry<String, XenoNpcLines.Category> group : LINE_GROUPS.entrySet()) {
            List<String> lines = readLines(nested(source, group.getKey()));
            if (lines.isEmpty() || !profile.linesFor(group.getValue()).isEmpty()) {
                continue;
            }
            // setLines applies the count and length caps and drops blanks, so nothing is re-checked here.
            profile.setLines(group.getValue(), lines);
        }
    }

    private static List<String> readLines(CompoundTag group) {
        List<String> out = new ArrayList<>();
        ListTag entries = group.getList("Lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            String text = entries.getCompound(i).getString("Line");
            if (text != null && !text.isBlank()) {
                out.add(text);
            }
        }
        return out;
    }

    private static void copyEquipment(CompoundTag source, CompoundTag profile) {
        CompoundTag gear = new CompoundTag();
        ListTag armor = source.getList("ArmorItems", Tag.TAG_COMPOUND);
        putStack(gear, "Feet", armor, 0);
        putStack(gear, "Legs", armor, 1);
        putStack(gear, "Chest", armor, 2);
        putStack(gear, "Head", armor, 3);
        ListTag hands = source.getList("HandItems", Tag.TAG_COMPOUND);
        putStack(gear, "Main", hands, 0);
        putStack(gear, "Off", hands, 1);
        if (!gear.isEmpty()) {
            profile.put("Gear", gear);
        }
    }

    private static void putStack(CompoundTag gear, String key, ListTag list, int index) {
        if (index >= list.size()) return;
        CompoundTag item = list.getCompound(index);
        if (!item.isEmpty() && item.contains("id")) gear.put(key, item.copy());
    }

    private static CompoundTag profileTag(NpcCombatProfile profile) {
        return profile.toTag();
    }

    private static void preserveSource(CompoundTag data, CompoundTag source, String sourceMod) {
        if (data.contains(SOURCE, Tag.TAG_COMPOUND)) return;
        CompoundTag retained = new CompoundTag();
        retained.putInt("Schema", SOURCE_SCHEMA);
        retained.putString("SourceMod", sourceMod == null ? "unknown" : sourceMod);
        retained.put("OriginalEntity", source);
        data.put(SOURCE, retained);
    }

    /**
     * Finds the DMZ stats an NPC was carrying, preferring the real serialized location.
     *
     * <p>NeoForge writes attachments under {@code neoforge:attachments}, keyed by the registered id
     * ({@code xenopixelsmod:npc_dmz_stats} - see the {@code DeferredRegister} in {@code NpcDmzStats}).
     * Reading them from {@code NeoForgeData} instead finds nothing, which silently drops race, stats
     * and forms for every converted NPC while {@code NpcCounterpartSync} then rewrites the attachment
     * from the empty profile. The {@code NeoForgeData} lookup is kept as a fallback because tracked
     * reference material and older test fixtures store the stats there.
     */
    private static CompoundTag dmzAttachment(CompoundTag source) {
        CompoundTag attachment = nested(source, ATTACHMENTS, DMZ_ATTACHMENT);
        if (!attachment.isEmpty()) {
            return attachment;
        }
        CompoundTag data = nested(source, NEOFORGE_DATA);
        CompoundTag legacy = nested(data, DMZ_ATTACHMENT_LEGACY);
        return legacy.isEmpty() ? data : legacy;
    }

    private static CompoundTag nested(CompoundTag root, String... keys) {
        CompoundTag current = root;
        for (String key : keys) {
            if (current == null || !current.contains(key, Tag.TAG_COMPOUND)) return new CompoundTag();
            current = current.getCompound(key);
        }
        return current;
    }

    private static CompoundTag firstCompound(CompoundTag... values) {
        for (CompoundTag value : values) if (value != null && !value.isEmpty()) return value;
        return new CompoundTag();
    }

    private static String firstString(CompoundTag a, CompoundTag b, CompoundTag c,
                                      String key, String fallback) {
        String value = firstString(a, key, "");
        if (value.isBlank()) value = firstString(b, key, "");
        if (value.isBlank()) value = firstString(c, key, "");
        return value.isBlank() ? fallback : value;
    }

    private static String firstString(CompoundTag a, CompoundTag b, String key, String fallback) {
        String value = firstString(a, key, "");
        if (value.isBlank()) value = firstString(b, key, "");
        return value.isBlank() ? fallback : value;
    }

    private static String firstString(CompoundTag tag, String key, String fallback) {
        return tag != null && tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }

    private static int firstInt(CompoundTag a, CompoundTag b, CompoundTag c,
                                String key, String alias, int fallback) {
        if (a.contains(key)) return a.getInt(key);
        if (a.contains(alias)) return a.getInt(alias);
        if (b.contains(key)) return b.getInt(key);
        if (b.contains(alias)) return b.getInt(alias);
        if (c.contains(key)) return c.getInt(key);
        if (c.contains(alias)) return c.getInt(alias);
        return fallback;
    }

    private static int firstInt(CompoundTag a, CompoundTag b, CompoundTag c,
                                String key, int fallback) {
        if (a.contains(key)) return a.getInt(key);
        if (b.contains(key)) return b.getInt(key);
        if (c.contains(key)) return c.getInt(key);
        return fallback;
    }

    private static float firstFloat(CompoundTag a, CompoundTag b, CompoundTag c,
                                    String key, float fallback) {
        if (a.contains(key)) return a.getFloat(key);
        if (b.contains(key)) return b.getFloat(key);
        if (c.contains(key)) return c.getFloat(key);
        return fallback;
    }

    private static boolean firstBoolean(CompoundTag a, CompoundTag b, CompoundTag c,
                                        String key, String alias, boolean fallback) {
        if (a.contains(key)) return a.getBoolean(key);
        if (a.contains(alias)) return a.getBoolean(alias);
        if (b.contains(key)) return b.getBoolean(key);
        if (b.contains(alias)) return b.getBoolean(alias);
        if (c.contains(key)) return c.getBoolean(key);
        if (c.contains(alias)) return c.getBoolean(alias);
        return fallback;
    }

    private static boolean firstBoolean(CompoundTag a, CompoundTag b, String key, boolean fallback) {
        if (a.contains(key)) return a.getBoolean(key);
        if (b.contains(key)) return b.getBoolean(key);
        return fallback;
    }

    private static boolean firstBoolean(CompoundTag a, CompoundTag b, CompoundTag c,
                                       String key, boolean fallback) {
        if (a.contains(key)) return a.getBoolean(key);
        if (b.contains(key)) return b.getBoolean(key);
        if (c.contains(key)) return c.getBoolean(key);
        return fallback;
    }

    private static String modelKind(int sourceModelType, String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            return sourceModelType == 2 ? NpcCombatProfile.MODEL_ENTITY : NpcCombatProfile.MODEL_GECKOLIB;
        }
        return NpcCombatProfile.MODEL_VANILLA;
    }

    private static int clampRelease(int percent) {
        return percent <= 0 ? 100 : Math.min(100, percent);
    }
}
