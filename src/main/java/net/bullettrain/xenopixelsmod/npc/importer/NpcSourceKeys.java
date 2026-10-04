package net.bullettrain.xenopixelsmod.npc.importer;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accounts for every key on a source NPC tag, so that nothing disappears without a line in the report.
 *
 * <p>The failure mode of a migration is not a crash — it is content that quietly did not arrive.
 * Mapping the keys we can convert is the easy half; this class is the other half, the one that makes
 * "unsupported" a statement an operator can read instead of an absence they have to discover.
 *
 * <p>Each top-level key on the source snapshot falls into exactly one bucket, checked in this order:
 * <ol>
 *   <li><b>consumed</b> — the importer maps it, so it needs no note;</li>
 *   <li><b>unmapped</b> — a real source construct with no native home; reported with the reason and
 *       the family it belongs to, so the exact key names appear in the report;</li>
 *   <li><b>state</b> — vanilla entity bookkeeping or another mod's runtime state, which the live
 *       entity regenerates; not the NPC's definition, so not reported per key;</li>
 *   <li><b>unknown</b> — anything else, reported by name.</li>
 * </ol>
 *
 * <p>Bucket 3 is deliberately narrow and partly rule-based: MyNPCs/CustomNPCs write their definition
 * keys in {@code PascalCase}, so a key that does not start with a capital letter is foreign runtime
 * state (DragonMineZ technique cooldowns and stat patches, for example) rather than something the
 * importer could have mapped. Anything the rule does not cover lands in bucket 4 and is named in the
 * report, which is the safe direction to fail: an extra line, not a silent loss.
 */
public final class NpcSourceKeys {

    /** Keys the importer reads and converts (or deliberately rewrites) on the placed path. */
    private static final Set<String> CONSUMED = Set.of(
            "id", "Name", "Role", "NpcSchema", "SourceMod", "SourceUuid",
            "FactionID", "NPCDialogOptions",
            "Scripts", "ScriptLanguage", "ScriptEnabled",
            "NpcLines", "NpcInteractLines", "NpcAttackLines", "NpcKillLines", "NpcKilledLines",
            "Resistances", "ImmuneToFire", "PotionImmune", "NoFallDamage", "CanDrown",
            "IgnoreCobweb", "BossBar", "NpcJob",
            // CNPC DataAI block -> native AI switches and enum selectors.
            "CanSwim", "AvoidsWater", "CanLeap", "ReturnToStart", "DirectLOS", "AttackInvisible",
            "MountControl", "OnAttack", "DoorInteract", "FindShelter",
            // CNPC DataStats/DataDisplay health, regen and family knobs.
            "BurnInSun", "MaxHealth", "HealthRegen", "CombatRegen", "CreatureType",
            "BossColor", "NpcVisible",
            // CNPC DataMelee/DataRanged attack model.
            "AttackStrenght", "AttackRange", "AttackSpeed", "KnockBack",
            "Accuracy", "MaxFiringRange", "DistanceToMelee", "minDelay", "maxDelay", "ShotCount",
            "BurstCount", "FireRate", "FireIndirect", "AimWhileShooting",
            "FiringSound", "HitSound", "GroundSound",
            // CNPC DataAdvanced sound kit.
            "NpcIdleSound", "NpcAngrySound", "NpcHurtSound", "NpcDeathSound", "NpcStepSound",
            "DisablePitch",
            "Size", "Texture", "SkinUrl", "UsingSkinUrl", "ModelId", "ModelAnimation", "ModelType", "ModelTint",
            "ModelGlowing", "ArmorItems", "HandItems", "NeoForgeData", "neoforge:attachments",
            "XenoPixelsProfile");

    /**
     * Real source constructs with no native equivalent, grouped by the reason they cannot be mapped.
     *
     * <p>The reason text is the report line, and the keys are listed inside it, so a single folded
     * line names every key in the family instead of producing one line per knob.
     */
    private static final Map<String, List<String>> UNMAPPED = unmapped();

    /** Vanilla entity bookkeeping and NeoForge plumbing: the live entity rebuilds all of it. */
    private static final Set<String> STATE = Set.of(
            "AbsorptionAmount", "Air", "Armor", "ArmorDropChances", "Brain", "CanPickUpLoot",
            "DeathTime", "FallDistance", "FallFlying", "Fire", "HandDropChances", "Health",
            "HurtByTimestamp", "HurtTime", "LeftHanded", "Motion", "Pos", "OnGround",
            "PersistenceRequired", "PortalCooldown", "Rotation", "Tags", "UUID",
            "UUIDLeast", "UUIDMost", "WorldUUIDLeast", "WorldUUIDMost", "attributes",
            "custom_name", "custom_name_visible", "FoodLevel", "ModRev", "Revision",
            "neoforge:spawn_type", "ForgeData", "EntityId", "Passengers", "Vehicle", "Leashed",
            "ActiveEffects", "DefaultExtraData", "SleepingX", "SleepingY", "SleepingZ",
            // CNPC's own runtime blob and the transient interaction state it writes back each tick.
            "CNPC_persistantData", "npcInteracting", "stopAndInteract",
            // Vanilla naming and food bookkeeping: the live entity owns these. CNPC writes the
            // PascalCase copies, vanilla reads custom_name / FoodLevel, neither is NPC definition.
            "CustomName", "CustomNameVisible", "foodLevel", "foodSaturationLevel",
            "foodExhaustionLevel", "foodTickTimer");

    /** Every key listed in {@link #UNMAPPED}, flattened, so the sweep below can skip what it named. */
    private static final Set<String> ALL_UNMAPPED = flattenUnmapped();

    private static Set<String> flattenUnmapped() {
        Set<String> out = new LinkedHashSet<>();
        for (List<String> keys : UNMAPPED.values()) {
            out.addAll(keys);
        }
        return java.util.Collections.unmodifiableSet(out);
    }

    private NpcSourceKeys() {
    }

    private static Map<String, List<String>> unmapped() {
        Map<String, List<String>> map = new java.util.LinkedHashMap<>();
        map.put("CNPC's companion system is a second NPC kind (an owned follower that gains experience,"
                        + " stages and talents); the native follower job is a role on one NPC, and no"
                        + " verified mapping carries the companion state across",
                List.of("CompanionID", "CompanionAge", "CompanionCanAge", "CompanionDefendOwner",
                        "CompanionExp", "CompanionHasInv", "CompanionInventory", "CompanionJob",
                        "CompanionOwner", "CompanionOwnerName", "CompanionStage", "CompanionTalents",
                        "Owner"));
        map.put("CNPC's home point, leash radius and respawn scheduling belong to its own respawn"
                        + " system; the native counterpart is the world store's SPAWNS entries, which"
                        + " are authored rather than derived from these keys",
                List.of("HomeX", "HomeY", "HomeZ", "LeashRadius", "RespawnEnabled",
                        "RespawnDelayTicks", "RespawnTime"));
        map.put("CNPC's role-bound dialog, option map and quest ids are read by its role system; the"
                        + " native quest and dialog bindings are the imported slots themselves, the role"
                        + " name is carried in the profile, and the option id/text maps have no"
                        + " verified native shape",
                List.of("RoleDialog", "RoleDialogId", "RoleOptions", "RoleOptionTexts",
                        "RoleQuestId"));
        map.put("the imported faction id is the authoritative link; CNPC's separate faction name string"
                        + " cannot be resolved against the faction store without guessing",
                List.of("Faction"));
        map.put("CNPC's nametag text and colour have no native profile field - the native nameplate is"
                        + " the entity's own display name, which this importer leaves to vanilla - so"
                        + " the source title was not applied",
                List.of("Title", "TitleColor"));
        map.put("no native field expresses it: the native protection model is the four damage"
                        + " resistances plus fire/potion/fall/drowning/cobweb toggles, and sun and"
                        + " water avoidance already arrive through BurnInSun, FindShelter and"
                        + " AvoidsWater; there is no invulnerability, statue or fire-reaction toggle",
                List.of("Invulnerable", "AvoidsSun", "ReactsToFire", "IsStatue"));
        map.put("native links are stored as UUIDs, and a source link is a display name that cannot be"
                        + " resolved without the whole roster",
                List.of("LinkedNpcName", "LinkedNpcSlot"));
        map.put("native combat has no field for these: move speed and sprinting are the vanilla"
                        + " movement attributes, faction attack/defend toggles and points belong to the"
                        + " faction store, the loot and experience knobs are vanilla or CNPC runtime"
                        + " bookkeeping, and VisibleAvailability is one of CNPC's availability gates",
                List.of("MoveSpeed", "CanSprint", "AttackOtherFactions", "DefendFaction",
                        "FactionPoints", "LootMode", "DropChance", "MaxExp", "MinExp",
                        "KilledTime", "VisibleAvailability"));
        map.put("CNPC's held-weapon items and melee potion effect have no verified native mapping:"
                        + " Weapons stores item stacks, and the Potion* keys store an index into"
                        + " CNPC's editor dropdown with no checked-over path to registry ids",
                List.of("Weapons", "PotionAmp", "PotionDuration", "PotionEffect"));
        map.put("movement is native pathing and the profile's stay-home/path settings; CNPC's"
                        + " wander/aggro pattern knobs do not carry over",
                List.of("ActiveRange", "AggroRange", "WalkingRange", "MovingPatern", "MovingPause",
                        "MovingPos", "MovingState", "MovementType", "SpawnCycle", "TotalTicksAlive",
                        "NpcsTimers", "MoveState", "StandingState", "Orientation"));
        map.put("the native quest model is the imported quest definitions and dialog slots, not CNPC's"
                        + " quest-board slots",
                List.of("FtbQuestCompleteEnabled", "FtbQuestCompleteId", "FtbQuestJobRequired",
                        "FtbQuestRequiredEnabled", "FtbQuestRequiredId", "FtbQuestRoleRequired"));
        map.put("CNPC's transform system is its own second form and is not a DragonMineZ form; the"
                        + " native form fields come from the DMZ stats attachment",
                List.of("TransformIsActive", "TransformEditingModus", "TransformHasAI",
                        "TransformHasAdvanced", "TransformHasDisplay", "TransformHasInv",
                        "TransformHasJob", "TransformHasRole", "TransformHasStats"));
        map.put("the native appearance surface is the profile's model, skin, hair and colour fields;"
                        + " CNPC's extra render layers have no equivalent",
                List.of("CloakTexture", "GlowTexture", "OverlayGlowing", "ShowLayers", "ShowName",
                        "NoLivingAnimation", "SkinColor",
                        "NpcModelData", "MarkovGender", "MarkovGeneratorId", "EntityScale",
                        "HideBodyWhenKilled", "PositionOffsetX", "PositionOffsetY", "PositionOffsetZ"));
        map.put("CNPC's own particle block is a per-NPC effect emitter the native renderer does not"
                        + " carry; the settings are preserved in the source snapshot",
                List.of("pArea", "pDamage", "pDur", "pEffAmp", "pEffect", "pGlows", "pImpact",
                        "pPhysics", "pRender3D", "pSize", "pSpeed", "pSpin", "pStick", "pTrail",
                        "pXlr8"));
        map.put("CNPC's NPC inventory and scene sequences are per-server runtime data (item stacks,"
                        + " keyframed sequences) rather than NPC definition; the native equivalents -"
                        + " trades and drops, the SCENES store - are authored in the Xeno stores, and"
                        + " no verified mapping from these shapes exists",
                List.of("NpcInv", "NpcScenes"));
        map.put("CNPC's bank, transport and controller keys are integer references to server-side"
                        + " records that live outside the NPC tag; those blobs have never been"
                        + " sampled in a tracked fixture (no bank/transport/market data exists in any"
                        + " inspected save), so converting the reference alone would dangle",
                List.of("Bank", "BankId", "RoleBankID", "Transports", "TransporterId", "Controller",
                        "TransportControl"));
        map.put("CNPC orders its line sets explicitly; the native line runner always cycles the"
                        + " category's list, so there is nothing to control",
                List.of("OrderedLines"));
        map.put("NPC-to-NPC speech has no native category; the native equivalent is the CONVERSATION"
                        + " job, which is bound by the job field rather than by a line list",
                List.of("NpcInteractNPCLines"));
        return java.util.Collections.unmodifiableMap(map);
    }

    /** Every key the importer consumes, for tests and for callers that want the contract. */
    public static Set<String> consumed() {
        return CONSUMED;
    }

    /**
     * Reports every source key that was not carried across.
     *
     * <p>One folded line per family, naming the exact keys inside it, because the same dozen families
     * repeat on every NPC in the world and a report nobody reads is the same as no report.
     *
     * @param source the untouched source snapshot (not the rewritten native tag)
     * @param npcKey identity used in the report, normally {@code mod/uuid}
     */
    public static void audit(@Nullable CompoundTag source, String npcKey, NpcImportReport report) {
        if (source == null) {
            return;
        }
        Set<String> present = new LinkedHashSet<>(source.getAllKeys());
        for (Map.Entry<String, List<String>> family : UNMAPPED.entrySet()) {
            List<String> hits = new java.util.ArrayList<>();
            for (String key : family.getValue()) {
                if (present.contains(key)) {
                    hits.add(key);
                }
            }
            if (hits.isEmpty()) {
                continue;
            }
            report.noteGrouped("source key(s) " + quote(hits) + " were not imported - "
                    + family.getKey(), npcKey);
        }
        for (String key : present) {
            if (CONSUMED.contains(key) || STATE.contains(key) || ALL_UNMAPPED.contains(key)) {
                continue;
            }
            if (isForeignRuntimeState(key)) {
                continue;
            }
            report.noteGrouped("source key \"" + key + "\" is not understood by the importer and was"
                    + " left in the preserved source snapshot", npcKey);
        }
        if (PlacedNpcProfileMigrator.usesLineSounds(source)) {
            report.noteGrouped("some source lines request a per-line sound; the native line profile"
                    + " carries text only, so the sound request was dropped", npcKey);
        }
        List<String> amplified = amplifiedResistances(source);
        if (!amplified.isEmpty()) {
            report.noteGrouped("resistance value(s) " + quote(amplified) + " ask for the NPC to take"
                    + " extra damage; the native resistance fields only express reduction, so the"
                    + " vulnerability was not applied", npcKey);
        }
    }

    /**
     * Resistance keys whose source value asks for the opposite of what the native field means.
     *
     * <p>MyNpcs scales incoming damage by {@code 2 - r}, so a value below 1.0 makes the NPC take
     * extra damage. Our four resistance fields are reductions only, so that half of the range has
     * nowhere to go and {@link PlacedNpcProfileMigrator} leaves it alone. The key counts as consumed,
     * so this is where the part that did not survive gets said out loud.
     */
    static List<String> amplifiedResistances(@Nullable CompoundTag source) {
        List<String> out = new java.util.ArrayList<>();
        if (source == null || !(source.get(PlacedNpcProfileMigrator.KEY_RESISTANCES)
                instanceof CompoundTag block)) {
            return out;
        }
        for (String key : List.of("Melee", "Arrow", "Explosion", "Knockback")) {
            if (block.get(key) instanceof net.minecraft.nbt.NumericTag value
                    && value.getAsFloat() < 1.0f) {
                out.add(key);
            }
        }
        return out;
    }

    private static String quote(List<String> keys) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append('"').append(keys.get(i)).append('"');
        }
        return out.toString();
    }

    /**
     * Another mod's per-entity runtime state.
     *
     * <p>MyNPCs/CustomNPCs write their definition keys in {@code PascalCase}; other mods bolt state
     * onto the same entity in {@code camelCase} or {@code snake_case} (DragonMineZ technique state
     * like {@code combatRegenPerSecond} or {@code fixedMaxHealth}, cooldowns like
     * {@code dragonminez:technique_cooldown}). A key that does not start with a capital letter is
     * therefore not something this importer could have mapped, and reporting it per NPC would bury
     * the lines that matter.
     */
    private static boolean isForeignRuntimeState(String key) {
        if (key.isEmpty() || !Character.isLowerCase(key.charAt(0))) {
            return false;
        }
        int colon = key.indexOf(':');
        if (colon < 0) {
            return true;
        }
        String namespace = key.substring(0, colon);
        // Ours is never foreign: if this importer ever stops reading one of our own keys, the report
        // has to say so instead of shrugging.
        if (namespace.equals("xenopixelsmod") || namespace.equals("xenopixels")) {
            return false;
        }
        return true;
    }
}
