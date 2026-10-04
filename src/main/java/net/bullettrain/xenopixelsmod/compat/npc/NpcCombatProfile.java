package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.stats.extras.FormMasteries;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A lightweight, XenoPixels-owned combat profile for non-player NPCs (My NPCs / CustomNPCs
 * entities). DragonMineZ's real race/stats system ({@code com.dragonminez.common.stats.*}) has
 * no construction path for a non-{@link net.minecraft.world.entity.player.Player} entity —
 * {@code StatsProvider}/{@code StatsData} are hard-typed to {@code Player} throughout — so this
 * is a separate, parallel data holder rather than a real DMZ {@code StatsData} instance. The
 * stat field names mirror DMZ's real {@code com.dragonminez.common.stats.character.Stats}
 * fields ({@code strength}, {@code strikePower}, {@code resistance}, {@code vitality},
 * {@code kiPower}, {@code energy}) so the numbers mean the same thing a player's DMZ stats would.
 *
 * <p>Stored directly in the NPC entity's vanilla persistent NBT — no capability/attachment
 * registration needed.
 */
public final class NpcCombatProfile {
    public static final String NBT_KEY = "xenopixels:npc_combat_profile";

    public static final String MODEL_VANILLA = "VANILLA";
    public static final String MODEL_GECKOLIB = "GECKOLIB";
    public static final String MODEL_ENTITY = "ENTITY";

    /** The model kinds, in the order the editor cycles them. */
    public static final java.util.List<String> MODEL_KINDS =
            java.util.List.of(MODEL_VANILLA, MODEL_GECKOLIB, MODEL_ENTITY);

    /** Keeps a hitbox multiplier inside a range that cannot break collision or culling. */
    public static float clampHitbox(float value) {
        if (!Float.isFinite(value) || value <= 0f) {
            return 1.0f;
        }
        return Math.max(0.25f, Math.min(4.0f, value));
    }

    /** The editor's 1–30 Size row uses 5 as normal size; 0 is the old unset value. */
    public static float visualSizeScale(int baseSize) {
        return baseSize <= 0 ? 1.0f : Math.max(1, Math.min(30, baseSize)) / 5.0f;
    }

    /** Collision scale after combining the authored hitbox and visible size. */
    public static float effectiveHitboxScale(float authoredScale, int baseSize, boolean followSize) {
        float scale = clampHitbox(authoredScale) * (followSize ? visualSizeScale(baseSize) : 1.0f);
        // The two independently bounded inputs produce a range of 0.05–24.
        return Math.max(0.05f, Math.min(24.0f, scale));
    }

    /** Normalises any stored value to a supported kind, defaulting to VANILLA. */
    public static String normalizeModelKind(String value) {
        if (value == null) {
            return MODEL_VANILLA;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return MODEL_KINDS.contains(upper) ? upper : MODEL_VANILLA;
    }

    private static final String TAG_SCHEMA = "Schema";
    private static final int CURRENT_SCHEMA = 20;
    private static final String TAG_NATIVE_MELEE_DEFAULTS_APPLIED = "NativeMeleeDefaultsApplied";
    private static final String TAG_NATIVE_FALL_DEFAULT_APPLIED = "NativeFallDefaultApplied";
    private static final String TAG_DMZ_BASE_STATS_APPLIED = "DmzBaseStatsApplied";
    private static final java.util.List<String> XENO_DEFAULT_MELEE_ANIMATIONS = java.util.List.of(
            "combat.xeno_dmz_punch_right_v4",
            "combat.xeno_dmz_punch_left_v4",
            "combat.xeno_dmz_punch_right_v4",
            "combat.xeno_dmz_punch_left_v4");
    private static final String TAG_AUTHORITATIVE = "Authoritative";
    private static final String TAG_KNOCKABLE = "Knockable";
    private static final String TAG_PUNCHABLE = "Punchable";
    private static final String TAG_PIN_NATIVE = "PinNativeCombat";
    private static final String TAG_DMZ_SNAPSHOT = "DmzStatSnapshot";
    /** AuraColorHex and the native aura-scale migration were completed in schema 4. */
    private static final int AURA_SCHEMA = 4;
    /** The default aura-scale change (1.0 → 1.7) was completed in schema 7. */
    private static final int AURA_DEFAULT_SCHEMA = 7;

    private static final String TAG_RACE = "Race";
    private static final String TAG_STRENGTH = "Strength";
    private static final String TAG_STRIKE_POWER = "StrikePower";
    private static final String TAG_RESISTANCE = "Resistance";
    private static final String TAG_VITALITY = "Vitality";
    private static final String TAG_MAX_HEALTH_OVERRIDE = "MaxHealthOverride";
    private static final String TAG_NPC_MELEE_PROPS = "NpcMeleeProps";
    private static final String TAG_NPC_RANGED_PROPS = "NpcRangedProps";
    private static final String TAG_NPC_PROJECTILE_PROPS = "NpcProjectileProps";
    private static final String TAG_NPC_RESISTANCE_PROPS = "NpcResistanceProps";
    private static final String TAG_KI_POWER = "KiPower";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_KI_COLOR = "KiColor";
    private static final String TAG_AURA_ON = "AuraOn";
    private static final String TAG_AURA_COLOR = "AuraColor";
    private static final String TAG_FORM_GROUP = "FormGroup";
    private static final String TAG_FORM = "Form";
    private static final String TAG_TECHNIQUES = "Techniques";
    private static final String TAG_TECHNIQUE_LEVELS = "TechniqueLevels";
    private static final String TAG_SKIN_PLAYER = "SkinPlayer";
    private static final String TAG_SKIN_URL = "SkinUrl";
    private static final String TAG_MASTERIES = "Masteries";
    private static final String TAG_FORM_POWER = "FormPower";
    private static final String TAG_BASE_SIZE = "BaseSize";
    private static final String TAG_KI_CHARGE = "KiChargePercent";
    private static final String TAG_POWER_RELEASE = "PowerReleasePercent";
    private static final String TAG_AURA_SCALE = "AuraScale";
    private static final String TAG_HAIR_ENABLED = "HairEnabled";
    private static final String TAG_HAIR_CODE = "HairCode";
    private static final String TAG_HAIR_CODE_CHUNKS = "HairCodeChunks";
    private static final String TAG_HAIR_COLOR = "HairColor";
    private static final String TAG_HAIR_STYLE = "HairStyleId";
    private static final String TAG_DMZ_APPEARANCE = "DmzAppearance";
    private static final String TAG_MODEL_KIND = "ModelKind";
    private static final String TAG_MODEL_ID = "ModelId";
    private static final String TAG_MODEL_ANIMATION = "ModelAnimation";
    private static final String TAG_MODEL_TEXTURE = "ModelTexture";
    private static final String TAG_MODEL_TINT = "ModelTint";
    private static final String TAG_MODEL_GLOWING = "ModelGlowing";
    /** Display > Cape / Overlay / Showing Layers; drawn by NpcCapeLayer and NpcOverlayLayer. */
    private static final String TAG_DISPLAY_CAPE = "DisplayCape";
    private static final String TAG_DISPLAY_OVERLAY = "DisplayOverlay";
    private static final String TAG_DISPLAY_OVERLAY_GLOW = "DisplayOverlayGlow";
    private static final String TAG_DISPLAY_LAYERS = "DisplayOuterLayers";
    private static final String TAG_FIRE_IMMUNE = "ImmuneToFire";
    private static final String TAG_BURNS_IN_SUN = "BurnsInSun";
    private static final String TAG_CAN_DROWN = "CanDrown";
    private static final String TAG_NO_FALL_DAMAGE = "NoFallDamage";
    private static final String TAG_POTION_IMMUNE = "PotionImmune";
    private static final String TAG_COBWEB_AFFECTED = "CobwebAffected";
    private static final String TAG_VISIBLE = "Visible";
    private static final String TAG_HITBOX_SCALE = "HitboxScale";
    private static final String TAG_BOSS_BAR = "BossBar";
    private static final String TAG_BOSS_BAR_COLOR = "BossBarColor";
    private static final String TAG_CREATURE_TYPE = "CreatureType";
    private static final String TAG_AURA_COLOR_HEX = "AuraColorHex";
    private static final String TAG_SELECTED_FORM_GROUP = "SelectedFormGroup";
    private static final String TAG_SELECTED_FORM = "SelectedForm";
    private static final String TAG_STACK_GROUP = "StackFormGroup";
    private static final String TAG_STACK_FORM = "StackForm";
    private static final String TAG_SELECTED_STACK_GROUP = "SelectedStackFormGroup";
    private static final String TAG_SELECTED_STACK_FORM = "SelectedStackForm";
    private static final String TAG_STACK_MASTERIES = "StackMasteries";
    private static final String TAG_HALO = "Halo";
    private static final String TAG_ROCKS = "AuraRocks";
    private static final String TAG_SPARKING = "AuraSparking";
    private static final String TAG_LIGHTNING = "AuraLightning";
    private static final String TAG_GROUND_RING = "AuraGroundRing";
    private static final String TAG_FLY_ON = "FlySkillOn";
    private static final String TAG_FLY_LEVEL = "FlySkillLevel";
    private static final String TAG_SKILLS = "DmzSkills";
    private static final String TAG_KI_SENSE_LOCK_ON = "KiSenseLockOn";
    private static final String TAG_AGGRO_MULTIPLIER = "AggroMultiplier";
    private static final String TAG_AIM_ACCURACY = "AimAccuracy";
    private static final String TAG_BRAIN = "CombatBrain";
    private static final String TAG_BRAIN_VERSION = "BrainVersion";
    private static final String TAG_BRAIN_STRIKE = "BrainStrike";
    private static final String TAG_BRAIN_CHARGE = "BrainCharge";
    private static final String TAG_BRAIN_FLYING_FIST = "BrainFlyingFist";
    private static final String TAG_BRAIN_HEAVY_HIT = "BrainHeavyHit";
    private static final String TAG_BRAIN_BONE_CRUSHER = "BrainBoneCrusher";
    private static final String TAG_BRAIN_KIAI = "BrainKiai";
    private static final String TAG_BRAIN_RANDOM_KI = "BrainRandomKi";
    private static final String TAG_BRAIN_KI_BLAST = "BrainKiBlast";
    private static final String TAG_BRAIN_KI_WAVE = "BrainKiWave";
    private static final String TAG_BRAIN_KI_DISK = "BrainKiDisk";
    private static final String TAG_BRAIN_KI_NAMED = "BrainKiNamed";
    private static final String TAG_BRAIN_FLY = "BrainFly";
    private static final String TAG_BRAIN_CAN_USE_FLIGHT = "CanUseFlight";
    private static final String TAG_BRAIN_LAND = "BrainLand";
    private static final String TAG_BRAIN_ASCEND = "BrainAscend";
    private static final String TAG_BRAIN_DISENGAGE = "BrainDisengage";
    private static final String TAG_BRAIN_VANISH = "BrainVanish";
    private static final String TAG_BRAIN_ZANZOKEN = "BrainZanzoken";
    private static final String TAG_BRAIN_CHASE = "BrainChase";
    private static final String TAG_BRAIN_DEFLECT_BLAST = "BrainDeflectBlast";
    private static final String TAG_BRAIN_DEFLECT_WAVE = "BrainDeflectWave";
    private static final String TAG_BRAIN_CHANCES = "BrainChances";
    private static final String TAG_BRAIN_MODIFIERS = "BrainModifiers";
    private static final String TAG_BRAIN_SPECIAL_CD = "BrainSpecialCooldown";
    private static final String TAG_BRAIN_XENO_SPECIALS = "BrainXenoSpecials";
    private static final String TAG_SCENE_TRIGGER = "SceneTrigger";
    private static final String TAG_HEALTH_REGEN = "HealthRegen";
    private static final String TAG_COMBAT_REGEN = "CombatRegen";
    private static final String TAG_AI_CAN_SWIM = "AiCanSwim";
    private static final String TAG_AI_AVOIDS_WATER = "AiAvoidsWater";
    private static final String TAG_AI_DOOR_INTERACT = "AiDoorInteract";
    private static final String TAG_AI_LEAP = "AiLeapAtTarget";
    private static final String TAG_AI_RETURN_HOME = "AiReturnToStart";
    private static final String TAG_AI_ON_FOUND_ENEMY = "AiOnFoundEnemy";
    private static final String TAG_AI_SHELTER_FROM = "AiShelterFrom";
    private static final String TAG_AI_MUST_SEE_TARGET = "AiMustSeeTarget";
    private static final String TAG_AI_ATTACK_INVISIBLE = "AiAttackInvisible";
    private static final String TAG_AI_MOUNT_CONTROL = "AiMountControl";
    /** Canonical action keys for chance/modifier maps (not the master combatBrain switch). */
    public static final String[] BRAIN_ACTION_KEYS = {
            "strike", "charge", "flyingFist", "heavyHit", "boneCrusher", "kiai", "randomKi",
            "kiBlast", "kiWave", "kiDisk", "kiNamed", "fly", "land", "ascend", "disengage",
            "vanish", "zanzoken", "chase", "deflectBlast", "deflectWave"
    };
    private static final String TAG_KI_WEAPON_ON = "KiWeaponOn";
    private static final String TAG_KI_WEAPON_TYPE = "KiWeaponType";
    private static final String TAG_BASE_AURA_STYLE = "BaseAuraStyle";
    private static final String TAG_FORM_AURA_STYLES = "FormAuraStyles";
    private static final String TAG_STACK_AURA_STYLES = "StackAuraStyles";
    private static final String TAG_MELEE_ANIMATION = "MeleeAnimation";

    // --- Advanced tab (MyNPCs parity: Sounds / Night / Linked / Editing Mode / Marks) ---
    private static final String TAG_SOUND_LIVING = "SoundLiving";
    private static final String TAG_SOUND_ANGRY = "SoundAngry";
    private static final String TAG_SOUND_HURT = "SoundHurt";
    private static final String TAG_SOUND_DEATH = "SoundDeath";
    private static final String TAG_SOUND_STEP = "SoundStep";
    private static final String TAG_SOUND_HAS_PITCH = "SoundHasPitch";
    private static final String TAG_NIGHT_TEXTURE = "NightTexture";
    private static final String TAG_LINKED_NPCS = "LinkedNpcs";
    private static final String TAG_EDITING_LOCKED = "EditingLocked";
    private static final String TAG_MARK_ICON = "MarkIcon";
    private static final String TAG_MARK_COLOR = "MarkColor";
    private static final String TAG_BUBBLE_PALETTE = "BubblePalette";
    private static final String TAG_BUBBLE_HEIGHT = "BubbleHeight";
    private static final String TAG_BUBBLE_DURATION = "BubbleDurationTicks";
    private static final String TAG_BUBBLE_MAX_LINES = "BubbleMaxLines";
    private static final String TAG_NPC_LINES = "NpcLines";
    private static final String TAG_AMBIENT_LINES = "AmbientLines";
    private static final String TAG_TRANSPORT_NETWORK = "TransportNetwork";
    private static final String TAG_BANK = "BankId";
    private static final String TAG_SCENE = "SceneId";
    private static final String TAG_SCRIPT = "ScriptId";
    private static final String TAG_PATH = "Path";
    private static final String TAG_STAY_HOME = "StayHome";
    private static final String TAG_SOCIAL = "SocialGestures";
    private static final String TAG_GUARD_ANIMALS = "GuardAnimals";
    private static final String TAG_GUARD_MONSTERS = "GuardMonsters";
    private static final String TAG_GUARD_CREEPERS = "GuardCreepers";
    private static final String TAG_BARD_SOUND = "BardSound";
    private static final String TAG_BARD_JUKEBOX = "BardJukebox";
    private static final String TAG_BARD_LOOPS = "BardLoops";
    private static final String TAG_BARD_ON = "BardOnDistance";
    private static final String TAG_BARD_HAS_OFF = "BardHasOffDistance";
    private static final String TAG_BARD_OFF = "BardOffDistance";
    private static final String TAG_HEALER_RANGE = "HealerRange";
    private static final String TAG_HEALER_TYPE = "HealerType";
    private static final String TAG_HEALER_SPEED = "HealerSpeed";
    private static final String TAG_HEALER_EFFECTS = "HealerEffects";
    private static final String TAG_FOLLOWER_NAME = "FollowerName";
    private static final String TAG_ITEM_GIVER_ITEMS = "ItemGiverItems";
    private static final String TAG_ITEM_GIVER_METHOD = "ItemGiverMethod";
    private static final String TAG_ITEM_GIVER_COOLDOWN_TYPE = "ItemGiverCooldownType";
    private static final String TAG_ITEM_GIVER_COOLDOWN = "ItemGiverCooldown";
    private static final String TAG_ITEM_GIVER_LINES = "ItemGiverLines";
    private static final String TAG_ITEM_GIVER_AVAILABILITY = "ItemGiverAvailability";
    private static final String TAG_JOB = "Job";
    private static final String TAG_JOB_ENABLED = "JobEnabled";

    /** The pre-network inline destination list. Read once by the migration, never written. */
    private static final String LEGACY_TAG_TRANSPORTS = "Transports";

    /** {@code SpeechBubbleRenderer.HEIGHT_ABOVE_ENTITY} before it was per-NPC. */
    public static final float DEFAULT_BUBBLE_HEIGHT = 0.7f;

    /** Four blocks clears any NPC in the game; beyond that the bubble leaves the screen. */
    public static final float MAX_BUBBLE_HEIGHT = 4.0f;

    /** {@code SpeechBubbleQueue.DEFAULT_LIFETIME_TICKS} before it was per-NPC. */
    public static final int DEFAULT_BUBBLE_DURATION_TICKS = 60;

    /** One minute. Longer reads as a bubble that never goes away. */
    public static final int MAX_BUBBLE_DURATION_TICKS = 1200;

    /** More than this cannot fit the largest bubble sprite whatever the atlas says. */
    public static final int MAX_BUBBLE_LINES = 8;

    /** Lines one category may hold. */
    public static final int MAX_LINES_PER_CATEGORY = 16;

    /** Characters one line may hold. These become entity NBT and a save payload. */
    public static final int MAX_LINE_LENGTH = 256;
    private static final String TAG_OPTION_PALETTE = "OptionPalette";
    private static final String TAG_BUBBLE_SHAPE = "BubbleShape";
    private static final String TAG_MELEE_ANIM_SLOTS = "MeleeAnimSlots";
    private static final String TAG_DIALOGUE = "Dialogue";
    private static final String TAG_STATE_CLIPS = "StateClips";
    /**
     * Ordered attack-animation slots per NPC.
     *
     * <p>Raised from 20. Safe to change: every other site derives from this constant, and the
     * reader clamps with {@code Math.min(MELEE_SLOT_COUNT, list.size())} over a stored list - so an
     * NPC saved when this was 20 loads unchanged and its extra slots start empty. Lowering it would
     * silently drop the tail of an existing set, so only ever raise it.
     */
    public static final int MELEE_SLOT_COUNT = 40;
    /** Packet/NBT UTF stays under 32767 per string. Full-set DMZ codes are longer. */
    public static final int HAIR_CODE_CHUNK = 30000;

    /** Existing explicit profiles migrate as authoritative; unprofiled NPCs remain untouched. */
    public boolean authoritative = true;
    public boolean knockable = true;
    public boolean punchable = true;
    /**
     * Whether XenoPixels overwrites this NPC's native CustomNPCs combat fields.
     *
     * <p>On by default, which is what authoritative DragonMineZ stats have always done: melee
     * strength, knockback, the regen values, the four resistances and the ranged fields are all
     * forced, so XenoPixels supplies the whole hit and CustomNPCs' own numbers cannot stack on top.
     *
     * <p>Turn it off for an NPC whose native fields an admin wants to own. It was previously
     * impossible to keep a hand-set damage or resistance value: the overwrite re-runs on every
     * profile save and on any DragonMineZ stat edit, so the value silently reverted and there was no
     * way to opt out of it.
     */
    public boolean pinNativeCombat = true;
    public String raceId = "human";
    public int strength;
    public int strikePower;
    public int resistance;
    public int vitality;

    /**
     * An explicit max health that wins over the vitality calculation.
     *
     * <p>Zero means unset, and the DragonMineZ-parity formula applies as it always has. Above zero
     * this value is used directly and vitality stops feeding health for this NPC.
     *
     * <p>It exists because parity is not always what an operator wants. DMZ derives a player's max
     * HP from vitality roughly one-for-one, so an NPC given a large VIT for its damage or its
     * resistances also ends up with a matching health pool, and the only way to lower it was to
     * lower the stat that was set for another reason entirely.
     */
    public int maxHealthOverride;
    /** Optional native NPC melee damage override; zero keeps the DragonMineZ formula. */
    public float npcMeleeDamage;
    /**
     * Optional melee reach in blocks; zero keeps the global default from
     * {@code /xenoset npcAttackStartRadius} (1.0 block, so an NPC must be close before it starts
     * attacking).
     */
    public float npcMeleeRange;
    /** Melee speed multiplier; 1 is the current DragonMineZ decision cadence. */
    public float npcMeleeSpeed = 1.0f;
    public float npcMeleeKnockback;
    /** Registry id, or empty for no added effect. */
    public String npcMeleeEffect = "";
    public int npcMeleeEffectDuration = 100;
    public int npcMeleeEffectAmplifier;
    /** Basic ranged attack tuning, used by the native combat brain's ki blast. */
    public float npcRangedAccuracy = 0.85f;
    public float npcRangedRange;
    public float npcRangedMinRange;
    public int npcRangedMinDelay = 50;
    public int npcRangedMaxDelay = 50;
    /**
     * Projectiles released together per shot, which is not the same thing as Burst Count.
     *
     * <p>My NPCs keeps both: {@code shotCount} is how many projectiles leave at once -- a spread --
     * and {@code burstCount} is how many times that spread repeats, {@code fireRate} ticks apart.
     * We only had the second, so a five-shot shotgun NPC could not be expressed at all.
     */
    public int npcRangedShotCount = 1;
    public int npcRangedBurstCount = 1;
    public int npcRangedBurstRate = 4;
    public boolean npcRangedIndirect;
    /** no, distant, hidden: when a ranged shot should turn/pose the caster toward its target. */
    public String npcRangedAimMode = "distant";
    public String npcRangedFireSound = "";
    public String npcRangedHitSound = "";
    public String npcRangedGroundSound = "";
    /** Overrides for the basic ki blast; zero-valued fields retain DragonMineZ defaults. */
    public float npcProjectileStrength;
    public float npcProjectileKnockback;
    public float npcProjectileSize;
    public float npcProjectileSpeed;
    /** none, normal, constant, accelerate */
    public String npcProjectileGravity = "none";
    /** 0 disables impact explosion, otherwise radius in blocks (clamped to 4). */
    public float npcProjectileExplosion;
    public String npcProjectileEffect = "";
    /**
     * How long the projectile's own effect lasts, and how strong.
     *
     * <p>Separate from the melee pair, which is what these used to borrow -- so editing a melee
     * poison quietly re-timed the projectile's one as well. My NPCs has always kept {@code pDur}
     * and {@code pEffAmp} apart from the melee fields for exactly that reason.
     */
    public int npcProjectileEffectDuration = 100;
    public int npcProjectileEffectAmplifier;
    /** Particle drawn along the projectile's path. {@code none} keeps DragonMineZ's own look. */
    public String npcProjectileTrail = "none";
    /** Tumbles in flight. The DMZ ki renderer reads entity yaw and pitch, so this is visible. */
    public boolean npcProjectileSpins;
    /** Stops dead where it lands instead of carrying on. */
    public boolean npcProjectileSticks;
    /** Vanilla outline, so a slow projectile stays readable through terrain. */
    public boolean npcProjectileGlows;
    /** Per-channel reductions in percent, from 0 (none) through 100 (immune). */
    public int npcKnockbackResistance;
    public int npcArrowResistance;
    public int npcMeleeResistance;
    public int npcExplosionResistance;
    public int kiPower;
    public int energy;
    /** Packed RGB, or 0 to keep the technique/default color. */
    public int kiColor;
    public boolean auraOn;
    /** Packed RGB for tinted dust around the NPC; 0 = white. */
    public int auraColor;
    /** Canonical #RRGGBB; empty inherits the race default. Unlike the legacy int, black is valid. */
    public String auraColorHex = "";
    /** Wand selection is independent from the committed active form. */
    public String selectedFormGroup = "";
    public String selectedFormId = "";
    public String formGroup = "";
    public String formId = "";
    public String selectedStackGroup = "";
    public String selectedStackId = "";
    public String stackGroup = "";
    public String stackId = "";
    public final List<String> techniques = new ArrayList<>();
    public final NpcTechniqueLevels techniqueLevels = new NpcTechniqueLevels();
    public String skinPlayer = "";
    public String skinUrl = "";
    public final FormMasteries masteries = new FormMasteries();
    public final FormMasteries stackMasteries = new FormMasteries();
    public boolean haloOn;
    public boolean auraRocks = true;
    public boolean auraSparking = true;
    public boolean auraLightning = true;
    /** The expanding floor rings under an aura. Default on, matching the pre-toggle behavior. */
    public boolean auraGroundRing = true;
    /**
     * DragonMineZ's {@code fly} skill. On a FULL-appearance NPC this drives DMZ's own flight
     * pose ({@code DMZPlayerRenderer} consults {@code FlySkillEvent.isFlyingFast}); on every NPC
     * it also switches CustomNPCs' navigator to the flying one through {@code NpcFlightBridge}.
     */
    public boolean flySkillOn;
    /** DMZ clamps its fly cost curve to levels 1..10 ({@code FlySkillEvent.getFlyCostMultiplier}). */
    public int flySkillLevel = 1;
    /** Every DMZ skill this NPC has, by lower-case id. See {@link NpcSkillSet}. */
    public final NpcSkillSet skills = new NpcSkillSet();
    /** Keep the last attacker locked as this NPC's target while DMZ Ki Sense is enabled. */
    public boolean kiSenseLockOnRetaliator;
    /**
     * Multiplier over CustomNPCs' own stored aggro range. Defaults to 2 because CNPC's aggro
     * range doubles as the <em>retention</em> range, so the stock value makes an NPC give up on
     * a target far sooner than a fight lasts. See {@link NpcAggroBridge}.
     */
    public float aggroMultiplier = 2.0f;
    /**
     * How well this NPC leads a moving target when firing ki, 0..1. At 0 it fires at where the
     * target is right now (the old behaviour, and an easy miss against anything strafing); at 1
     * it solves the intercept exactly. Lower it to author a deliberately weak shooter.
     */
    public float aimAccuracy = 0.85f;
    /**
     * Opt-in autonomous combat ({@code NpcCombatBrain}). Off by default so every existing
     * scripted NPC keeps behaving exactly as its script says.
     */
    public boolean combatBrain;
    /** Missing NBT stays {@link NpcCombatBrainVersion#V1}. */
    /** V1 in XenoPixels; the XenoNPCs release, where V9 is the only brain, starts on V9. */
    public NpcCombatBrainVersion brainVersion = NpcBrainPolicy.resolve(NpcCombatBrainVersion.V1);

    /** Selects a brain version and applies V9's safe deflection defaults once. */
    public void setBrainVersion(NpcCombatBrainVersion version) {
        NpcCombatBrainVersion next = NpcBrainPolicy.resolve(version);
        if (next == NpcCombatBrainVersion.V9 && brainVersion != NpcCombatBrainVersion.V9) {
            applyV9SafeDefaults();
        }
        brainVersion = next;
    }

    /** V9 starts with ki-blast and ki-wave deflection off. */
    private void applyV9SafeDefaults() {
        brainDeflectBlast = false;
        brainDeflectWave = false;
    }

    /**
     * Applies the DMZ skills editor's Fly control to the skill capability only.
     *
     * <p>The hub Fly row and the Brain tab actions are deliberately left alone: the skill says
     * the NPC owns flight, the Brain tab says whether the combat brain may reach for it. Flipping
     * {@code brainFly} here used to override the operator's action choice every time a skill was
     * toggled, which read as the two controls being wired together.
     */
    public void setDmzFlyEnabled(boolean enabled) {
        flySkillOn = enabled;
        flySkillLevel = Math.max(1, flySkillLevel);
        skills.set(NpcSkillSet.FLY, enabled, flySkillLevel);
    }

    /**
     * Whether this NPC may use the moves this mod added on top of the DragonMineZ port.
     *
     * <p>The BT3 combo choreography and the repositioning set - chase, vanish, backstep,
     * teleportAbove. {@link NpcCombatBrainVersion#usesXenoSpecials} says which versions consult
     * this at all: V7 never does (it is the clean DMZ tree), and every other version reads it.
     * Hakai is not covered - no brain casts it, only the script APIs do.
     *
     * <p>On by default, because that is what every brain did before the switch existed. An NPC
     * saved before this shipped therefore keeps behaving as it did - see {@code readBrainFlags},
     * where the absent key resolves to true rather than to {@code getBoolean}'s false.
     */
    public boolean xenoSpecials = true;
    /** DMZ strike techniques in melee. Default on so existing brain NPCs keep striking. */
    public boolean brainStrike = true;
    public boolean brainCharge = true;
    public boolean brainFlyingFist;
    public boolean brainHeavyHit;
    public boolean brainBoneCrusher;
    public boolean brainKiai;
    public boolean brainRandomKi;
    public boolean brainKiBlast = true;
    public boolean brainKiWave = true;
    public boolean brainKiDisk = true;
    public boolean brainKiNamed = true;
    /**
     * Whether the combat brain may reach for flight. Default on, like the other movement
     * actions: NPCs configured before this key existed flew whenever their Fly skill said so,
     * so the absent key must keep resolving true (see {@code canUseFlight} and readBrainFlags).
     */
    public boolean brainFly = true;
    /**
     * Master permission for the combat brain to use flight at all, independent of the Fly skill
     * and of the action toggles. Default on: NPCs saved before this key existed flew when their
     * skill and action said so, and the absent key must keep resolving true (see readBrainFlags).
     */
    public boolean canUseFlight = true;
    public boolean brainLand = true;
    public boolean brainAscend = true;
    public boolean brainDisengage = true;
    public boolean brainVanish = true;
    /** When on, Zanzoken afterimages can pull this brain off the real fighter. */
    public boolean brainZanzoken = true;
    public boolean brainChase = true;
    /** Bat back incoming blasts / disks. Default on. */
    public boolean brainDeflectBlast = true;
    /** Bat back clashable waves. Default off so clash stays the beam answer. */
    public boolean brainDeflectWave;
    private final java.util.Map<String, Integer> brainChances = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Float> brainModifiers = new java.util.LinkedHashMap<>();
    /** Shared gap after any special, in ticks. */
    public int brainSpecialCooldown = 80;
    public boolean kiWeaponOn;
    public String kiWeaponType = "blade";
    public NpcAuraStyle baseAuraStyle = new NpcAuraStyle();
    public final java.util.Map<String, NpcAuraStyle> formAuraStyles = new java.util.LinkedHashMap<>();
    public final java.util.Map<String, NpcAuraStyle> stackAuraStyles = new java.util.LinkedHashMap<>();
    /** Multiplier from the active DMZ {@code FormData} power stat. 1 = base. */
    public double formPower = 1.0;
    /** CNPC display size to restore on descend. 0 = unset. */
    public int baseSize;
    /** Ki attack charge percent (100 = stock). */
    public int kiChargePercent = 100;
    /**
     * DMZ's real damage formulas ({@code StatsData.getMeleeDamage/getKiDamage}) all multiply by
     * the player's "power release" stance stat, which defaults to a mere 5% and must be raised
     * manually. NPCs have no equivalent resource/UI, so this stands in for it -- 100 means an
     * NPC fights at the same effective output as a player at full power release with the same
     * stat values. Lower it per-NPC to deliberately make one weaker without touching its stats.
     */
    public int powerReleasePercent = 100;
    /** Additional multiplier applied after the native aura follows CNPC display size. */
    public float auraScale = 1.7f;
    /** DMZ hair, addon-independent home for the same three values NpcHairBridge writes. */
    public boolean hairEnabled;
    public String hairCode = "";
    public String hairColor = "";
    /**
     * Which DragonMineZ hair the NPC wears: {@code 0} means the custom {@link #hairCode}, and any
     * other value is that built-in character-creation preset.
     *
     * <p>This is DMZ's own {@code Character.hairId}, and DMZ only consults the custom hair when it
     * is zero ({@code HairManager.getEffectiveHair}) — so it has to be set deliberately rather than
     * inherited, or a race config with a non-zero default silently overrides the builder's code.
     */
    public int hairStyleId;
    /** Full player-independent DMZ customization state. */
    public NpcDmzAppearance appearance = new NpcDmzAppearance();

    /**
     * How this NPC is drawn.
     *
     * <p>{@code VANILLA} is the humanoid model with {@link #modelTexture}; {@code GECKOLIB} loads a
     * {@code .geo.json} rig named by {@link #modelId}; {@code ENTITY} mimics the registered entity
     * type named by {@link #modelId}, which is how models from other mods are supported without a
     * per-mod bridge. Stored as a string so an unknown future value degrades to VANILLA rather than
     * failing to deserialize.
     */
    public String modelKind = MODEL_VANILLA;

    /** GeckoLib asset name for {@code GECKOLIB}, or an entity type id for {@code ENTITY}. */
    public String modelId = "";

    /** Optional GeckoLib animation JSON resource id; blank derives it from {@link #modelId}. */
    public String modelAnimation = "";

    /** Texture path for the vanilla and GeckoLib paths; blank means the built-in default. */
    public String modelTexture = "";

    /** Multiplied over the model, 0xRRGGBB. White leaves the texture untouched. */
    public int modelTint = 0xFFFFFF;

    /** Renders at full brightness, matching the reference menu's "Glowing" switch. */
    public boolean modelGlowing;
    /** Cape texture for the native humanoid model; blank draws none. */
    public String displayCape = "";
    /** Second texture drawn over the skin (CustomNPCs' overlay); blank draws none. */
    public String displayOverlay = "";
    /** Whether the overlay ignores lighting, as a glowing eye/aura overlay does. */
    public boolean displayOverlayGlow;
    /** Whether the skin's outer layer (hat, jacket, sleeves, trousers) is drawn. */
    public boolean displayOuterLayers = true;

    // --- Behaviour flags -------------------------------------------------
    // The MyNPCs reference offers these on its Stats and Display tabs. Each maps onto a real
    // vanilla hook; XenoNpcBehaviour is what applies them to the entity.

    /** Never takes fire or lava damage. */
    public boolean fireImmune;

    /** Catches fire in daylight, the way a zombie does. */
    public boolean burnsInSun;

    /** Runs out of air underwater. Vanilla breathing is tag-driven, so this is enforced directly. */
    public boolean canDrown = true;

    public boolean noFallDamage;

    /** Ignores every applied mob effect. */
    public boolean potionImmune;

    /** Slowed by cobwebs. Off makes an NPC walk straight through them. */
    public boolean cobwebAffected = true;

    /** Rendered at all. A hidden NPC still exists and still interacts. */
    public boolean visible = true;

    /** Multiplies the hitbox, matching the reference's Hitbox row. 1.0 is unscaled. */
    public float hitboxScale = 1.0f;

    /** Shows a boss bar to nearby players. */
    public boolean bossBar;

    /** Boss bar colour name, matching {@code BossEvent.BossBarColor}. */
    public String bossBarColor = "PURPLE";

    /**
     * Creature family, as the reference's Creature Type row.
     *
     * <p>Stored as a name rather than an enum because 1.21 removed {@code MobType} in favour of
     * entity type tags, so this is a label the behaviour layer interprets rather than a vanilla
     * value that can be set directly.
     */
    public String creatureType = "normal";
    /**
     * Studio / catalog clip played on a committed melee hit. Empty keeps the built-in alternating
     * punches.
     */
    public String meleeAnimation = "";
    /** Prevents native-XenoNPC defaults from returning after an author intentionally clears them. */
    private boolean nativeMeleeAnimationDefaultsApplied;
    /** Set once No Fall Damage has been defaulted on, so an author who turns it off keeps that. */
    private boolean nativeFallDefaultApplied;
    private boolean dmzBaseStatsApplied;
    /**
     * Custom sound ids, the MyNPCs Advanced > Sounds set. Blank means "use the default".
     *
     * <p>Held as plain strings rather than resolved {@code SoundEvent}s because a profile is read
     * and written on both sides and long before any registry is safe to touch. They are resolved
     * leniently at play time, so an id that does not exist is silent rather than a crash.
     */
    /**
     * Health healed per second while this NPC is not fighting.
     *
     * <p>Zero, which is what every NPC did before this existed - nothing heals on its own. A guard
     * on a post is the obvious use: it should be back to full by the time the next thing wanders
     * up, without an operator having to stand there and heal it.
     */
    public float healthRegen;

    /**
     * Health healed per second while it <em>is</em> fighting.
     *
     * <p>Separate from {@link #healthRegen} and also zero by default, because they want very
     * different numbers: out of combat is about recovering between fights, and in combat is about
     * how long a boss stands up. One value covering both would make any boss unkillable or any
     * guard permanently wounded.
     */
    public float combatRegen;

    /** Most a regen field may be set to, per second. Past this an NPC is simply unkillable. */
    public static final float MAX_REGEN = 100.0f;

    /**
     * Whether it floats rather than sinking.
     *
     * <p>On, because {@code FloatGoal} was added unconditionally before this switch existed and an
     * NPC that suddenly drowned in a world it had been standing in for months would be a surprise.
     * Off is for something meant to stay on the bottom.
     */
    public boolean aiCanSwim = true;

    /**
     * Whether it treats water as something to path around.
     *
     * <p>Off, which is vanilla's default malus. On raises the cost of a water tile so a route goes
     * round a pond rather than through it - it does not stop the NPC entering water it is pushed
     * into, and {@link #aiCanSwim} is what decides whether it floats once there.
     */
    public boolean aiAvoidsWater;

    /** What it does about a door in its way. Nothing, by default, as before. */
    public net.bullettrain.xenopixelsmod.npc.NpcDoorInteract aiDoorInteract =
            net.bullettrain.xenopixelsmod.npc.NpcDoorInteract.DISABLED;

    /** Whether it pounces the last stretch at a target, the way a wolf does. Off by default. */
    public boolean aiLeapAtTarget;

    /**
     * Whether the leash brings it home at all.
     *
     * <p>On, because the leash has always run for any NPC with a home and a radius. Off leaves an
     * NPC wherever it ends up - which is what a follower or a wanderer wants, and what somebody
     * driving an NPC by command or scene wants too.
     */
    public boolean aiReturnToStart = true;

    /** MyNPCs-compatible response to being attacked. */
    public net.bullettrain.xenopixelsmod.npc.NpcOnFoundEnemy aiOnFoundEnemy =
            net.bullettrain.xenopixelsmod.npc.NpcOnFoundEnemy.RETALIATE;

    /** Shelter policy; Disabled preserves the default behavior. */
    public net.bullettrain.xenopixelsmod.npc.NpcShelterFrom aiShelterFrom =
            net.bullettrain.xenopixelsmod.npc.NpcShelterFrom.DISABLED;

    /** Whether autonomous target selection requires line of sight. */
    public boolean aiMustSeeTarget = true;

    /** Whether autonomous target selection ignores invisibility penalties. */
    public boolean aiAttackInvisible;

    /** Whether a player riding this NPC may steer it. */
    public boolean aiMountControl;

    /**
     * The route this NPC walks, or an empty path.
     *
     * <p>Per-NPC rather than shared library content - the deliberate counter-example to the §0
     * ownership rule, alongside ambient lines, because a route is drawn around where one NPC
     * stands and My NPCs stores it the same way.
     */
    public net.bullettrain.xenopixelsmod.npc.path.NpcPath path =
            new net.bullettrain.xenopixelsmod.npc.path.NpcPath();

    /**
     * Whether this NPC holds its spot instead of wandering off it.
     *
     * <p><b>The default is asymmetric on purpose.</b> A freshly built profile has it on, so an NPC
     * you place today stands where you put it. An NPC already saved in a world has no such key, and
     * {@code tag.getBoolean} answers false for a missing key - so every NPC that existed before
     * this shipped keeps wandering exactly as it did. Nothing changes underneath a running world
     * until the box is ticked.
     *
     * <p>Read by {@code StayHomeStrollGoal}, which gates the wander goal rather than removing it.
     */
    public boolean stayHome = true;

    /**
     * Whether this NPC waves at arriving players and makes small idle gestures.
     *
     * <p>Same asymmetric default, for the same reason: a new NPC is sociable, and nobody's existing
     * village suddenly starts waving.
     */
    public boolean socialGestures = true;

    /** Guard job: which kinds of thing it answers. Their three toggles, their defaults. */
    public boolean guardAnimals;
    public boolean guardMonsters = true;
    public boolean guardCreepers = true;

    /** Bard job: the sound, and how far away it starts and stops. Their fields, their defaults. */
    public String bardSound = "";
    public boolean bardJukebox = true;
    public boolean bardLoops;
    public int bardOnDistance = 2;
    public boolean bardHasOffDistance = true;
    public int bardOffDistance = 64;

    /** Native Healer job. Effect ids are namespaced so modded effects survive registry reordering. */
    public int healerRange = 8;
    /** 0 friendly, 1 hostile, 2 everyone (the MyNPCs selector order). */
    public int healerType = 2;
    public int healerSpeed = 20;
    public final java.util.Map<String, Integer> healerEffects = new java.util.LinkedHashMap<>();
    public static final int MAX_HEALER_EFFECTS = 16;

    /** Follower job target, matching an NPC's visible name within twenty blocks. */
    public String followerName = "";

    /** MyNPCs Item Giver's nine stack slots and selection/cooldown modes. */
    public static final int ITEM_GIVER_SLOTS = 9;
    public final net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack[] itemGiverItems =
            new net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack[ITEM_GIVER_SLOTS];
    public int itemGiverMethod;
    public int itemGiverCooldownType;
    public int itemGiverCooldown = 10;
    public final java.util.List<String> itemGiverLines = new java.util.ArrayList<>();
    public net.bullettrain.xenopixelsmod.features.progression.QuestAvailability
            itemGiverAvailability = net.bullettrain.xenopixelsmod.features.progression
            .QuestAvailability.NONE;

    /** How far a bard can be heard at all. Past this the sound would not reach anybody. */
    public static final int MAX_BARD_DISTANCE = 128;

    public String soundLiving = "";
    public String soundAngry = "";
    public String soundHurt = "";
    public String soundDeath = "";
    public String soundStep = "";
    /** MyNPCs' "Has Pitch": vary the pitch per play instead of always 1.0. */
    public boolean soundHasPitch = true;

    /** Texture swapped in after dusk. Blank keeps the day texture at all hours. */
    public String nightTexture = "";

    /**
     * The texture to draw this NPC with right now, honouring {@link #nightTexture}.
     *
     * <p>MyNPCs' Advanced &gt; Night is a second skin that takes over after dusk. The field existed
     * before this did, but nothing ever read it - the renderers went straight to
     * {@link #modelTexture} - which is precisely why the editor row was disabled as a dead control.
     * This is the consumer that makes it real, and it lives on the profile rather than in one
     * renderer because two of them resolve textures independently and would otherwise disagree.
     *
     * @param level may be null, in which case the day texture is used
     */
    public String textureFor(net.minecraft.world.level.Level level) {
        if (level != null && level.isNight() && nightTexture != null && !nightTexture.isBlank()) {
            return nightTexture;
        }
        return modelTexture;
    }

    /**
     * NPCs edited together with this one.
     *
     * <p>Stored as UUID strings; an entry that no longer resolves to a live NPC is skipped rather
     * than removed, because the other NPC may simply be in an unloaded chunk.
     */
    public final java.util.List<String> linkedNpcs = new java.util.ArrayList<>();

    /** MyNPCs' Editing Mode: when set, the editor opens read-only. */
    public boolean editingLocked = false;

    /**
     * Atlas palette for this NPC's speech bubble, and for its dialogue option bubbles.
     *
     * <p>Every atlas sprite is generated in four palettes, so this is a real choice rather than a
     * tint: the bubble art itself changes, borders and all. Stored as the palette name so the
     * profile does not depend on a client-only enum.
     */
    public String bubblePalette = DEFAULT_PALETTE;

    /**
     * Blocks above the NPC's head the bubble floats, replacing
     * {@code SpeechBubbleRenderer.HEIGHT_ABOVE_ENTITY}.
     *
     * <p>Defaults to that constant, so an NPC that sets nothing renders exactly as before. Zero is
     * legitimate - a bubble at the head itself - which is why the lower bound is zero and not a
     * sentinel.
     */
    public float bubbleHeight = DEFAULT_BUBBLE_HEIGHT;

    /** Ticks a line stays up, replacing {@code SpeechBubbleQueue.DEFAULT_LIFETIME_TICKS}. */
    public int bubbleDurationTicks = DEFAULT_BUBBLE_DURATION_TICKS;

    /**
     * Lines a bubble may wrap to, or zero to use whatever the atlas sprite allows.
     *
     * <p>Zero rather than the computed default because {@code SpeechBubbleLayout.maxLines()} reads
     * the atlas, which is client state and changes with the sprite sheet. Storing its value would
     * freeze one client's answer onto the NPC for everybody.
     */
    public int bubbleMaxLines;

    /**
     * Whether this NPC speaks unprompted.
     *
     * <p>Gates the ambient path only - the {@code RANDOM} line said to a nearby player and the
     * {@code WORLD} line said to nobody. Interact, attack, kill and killed lines are answers to
     * something the player did and are not affected: an NPC that went silent when hit would read
     * as broken rather than as quiet.
     *
     * <p>On by default, because every NPC spoke before this field existed.
     */
    public boolean ambientLinesEnabled = true;

    /**
     * What a trader NPC will swap.
     *
     * <p>Empty for every NPC that is not a trader, and written to disk only when it holds
     * something — every NPC in a world carries this object, and only a trader should pay for it.
     *
     * <p>The editor's Trader page has drawn eighteen disabled slots since long before anything
     * backed them. This is what they write into.
     */
    /**
     * The dialogues this NPC offers, as references into the shared library.
     *
     * <p>My NPCs' model, matched deliberately: twelve numbered slots pointing at
     * {@code dialogs/<category>/<id>} in the world store. A reference rather than a copy, so
     * twenty guards can share one greeting and fixing its typo fixes all twenty.
     */
    public final net.bullettrain.xenopixelsmod.npc.dialog.NpcDialogSlots dialogSlots =
            new net.bullettrain.xenopixelsmod.npc.dialog.NpcDialogSlots();

    /**
     * Which transport network this NPC serves, or blank for every NPC that is not a transporter.
     *
     * <p>A reference into the world store, not a copy of the destinations. It used to be an inline
     * list: a transport network is shared by its nature — two transporters in one city offer the
     * same places — so a private copy per NPC meant renaming a destination required finding and
     * editing every NPC that listed it.
     *
     * <p>Resolved through {@code TransportNetworks}. A network an operator has since deleted
     * resolves to nothing and the NPC offers no travel, rather than resurrecting a stale copy.
     */
    public String transportNetwork = "";

    /**
     * The bank this NPC tells for, or blank.
     *
     * <p>A reference into the world store for the same reason transports are: two tellers in one
     * city offer one bank, and a player who deposited with the first expects the second to know.
     * Resolved through {@code Banks}; a bank an operator has since deleted resolves to nothing and
     * the NPC talks instead of opening an empty vault.
     */
    public String bankId = "";
    /** Shared scene selected for this NPC; played by an operator command. */
    public String sceneId = "";

    /**
     * Which NPC script runs for this NPC, or blank for one that has none.
     *
     * <p>An id into the world store's {@code scripts} folder, resolved by
     * {@code XenoNpcScripts} through {@code XenoScriptRunner} — a reference, never a copy, for the
     * same reason the bank and transport keys are: one script shared by a dozen NPCs is one script
     * to edit, and a copy per NPC is a dozen places for it to be wrong.
     *
     * <p>Blank is the ordinary state. It is also what every NPC written before this key existed
     * reads back as, so an old world gains no behaviour from upgrading.
     */
    public String scriptId = "";

    /**
     * Script tabs (CustomNPCs' 1..n tabs, each with loaded scripts), the container's language and
     * enabled switch. When non-empty it is authoritative and {@link #scriptId} mirrors tab 1, so
     * code that only knows the single id keeps working. Read by {@code NpcScriptHost}.
     */
    public net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer scripts =
            new net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer();

    /** Binds exactly one script, replacing any tabs: the legacy single-id path (bind packet, importer). */
    public void bindSingleScript(String id) {
        scriptId = id == null ? "" : id.trim();
        net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer single =
                net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer.fromLegacy(scriptId);
        single.setEnabled(scripts.enabled());
        single.setLanguage(scripts.language());
        scripts = single;
    }

    /**
     * What makes this NPC's scene play.
     *
     * <p>Manual by default - the command - which is what every scene did before triggers existed.
     * Stored by name rather than ordinal, so the list can be reordered and an unknown value from a
     * newer build reads back as Manual rather than as whatever sits at that index.
     */
    public net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTrigger sceneTrigger =
            net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTrigger.MANUAL;

    /**
     * What this NPC does when nobody is talking to it - My NPCs' second axis.
     *
     * <p>Independent of {@code role}: their editor lets one NPC be a Trader <em>and</em> a Bard,
     * and nothing about the two interferes. Held as an id string rather than an enum ordinal so
     * that inserting a job into the list later cannot silently reclassify saved NPCs.
     */
    public String job = "";
    /** Existing job NPCs keep running when this setting is absent in older saves. */
    public boolean jobEnabled = true;

    /**
     * Destinations read from an NPC saved before networks existed.
     *
     * <p>Populated only by {@code readAdvancedTab} when it finds the old inline {@code Transports}
     * list, and consumed once by the migration. Never written back to the tag, so an NPC that has
     * been migrated stops carrying it — one fact, one home.
     */
    public transient net.bullettrain.xenopixelsmod.npc.transport.NpcTransportList legacyTransports;

    public final net.bullettrain.xenopixelsmod.npc.trade.NpcTradeList trades =
            new net.bullettrain.xenopixelsmod.npc.trade.NpcTradeList();

    /**
     * What this NPC wears and holds.
     *
     * <p>Six slots as item ids. Visible only on the humanoid render path - the GeckoLib, entity and
     * full-DMZ paths draw their own models and never see an equipment slot - but worn on every one
     * of them, which is what the combat attributes read.
     */
    public final net.bullettrain.xenopixelsmod.npc.inventory.NpcGear gear =
            new net.bullettrain.xenopixelsmod.npc.inventory.NpcGear();

    /** What this NPC leaves behind, and what it is worth in experience. */
    public final net.bullettrain.xenopixelsmod.npc.inventory.NpcDropList drops =
            new net.bullettrain.xenopixelsmod.npc.inventory.NpcDropList();

    /**
     * This NPC's own ambient lines, empty when it uses its role's.
     *
     * <p>Lines came only from the role's datapack file, so every trader in a world said the same
     * four things and nothing in game could change that - the same trap dialogue hit, and it is
     * solved the same way: the editable copy lives on the profile.
     *
     * <p><b>Full override.</b> An NPC with any line of its own uses only its own, in every
     * category. Per-category fallback was considered and rejected: one rule is easier to reason
     * about and to show honestly in an editor than six independent ones. The cost is real - giving
     * one guard a custom greeting costs it the role's combat lines until those are typed in too.
     */
    public final java.util.Map<net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category,
            java.util.List<String>> lines = new java.util.EnumMap<>(
                    net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.class);
    public String optionPalette = "GOLD";
    /**
     * Default outline for this NPC's speech bubbles (rounded, thought, shout, banner). A script
     * line may override it per message. Travels in {@link #visualOptionsTag} because the renderer
     * is client-side.
     */
    public String bubbleShape = "rounded";

    /** Trader matching: accept worn payment. True is how every trade matched before the switch. */
    public boolean tradeIgnoreDamage = true;
    /** Trader matching: accept renamed / enchanted / otherwise changed payment. Default true. */
    public boolean tradeIgnoreNbt = true;

    /** Folds any string onto a generated bubble outline; unknown and blank read as rounded. */
    public static String canonicalBubbleShape(String shape) {
        net.bullettrain.xenopixelsmod.npc.lines.BubbleShape parsed =
                net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.byName(shape,
                        net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.ROUNDED);
        return parsed == net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.INHERIT
                ? "rounded" : parsed.id();
    }

    /** The palettes the atlas is generated in, and the one a new NPC starts on. */
    public static final java.util.List<String> PALETTES =
            java.util.List.of("BLUE", "GOLD", "GREEN", "RED");
    public static final String DEFAULT_PALETTE = "BLUE";

    /** Folds any string onto a real palette, so a bad value renders rather than throwing. */
/**
     * Reads the height, treating a missing key and a nonsense one differently.
     *
     * <p>Absent means "written before the field existed", so it takes the old constant. Present but
     * non-finite is a crafted value and takes the same, because a NaN height would put the bubble
     * nowhere at all. A negative clamps to zero, which is a real position rather than an error.
     */
    private static float readBubbleHeight(CompoundTag tag) {
        if (!tag.contains(TAG_BUBBLE_HEIGHT)) {
            return DEFAULT_BUBBLE_HEIGHT;
        }
        float stored = tag.getFloat(TAG_BUBBLE_HEIGHT);
        if (!Float.isFinite(stored)) {
            return DEFAULT_BUBBLE_HEIGHT;
        }
        return Math.max(0.0f, Math.min(MAX_BUBBLE_HEIGHT, stored));
    }

    /** Whether this NPC speaks its own lines instead of its role's. */
    public boolean hasOwnLines() {
        for (java.util.List<String> category : lines.values()) {
            if (!category.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** This NPC's own lines for one category, empty when it has none. */
    public java.util.List<String> linesFor(net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category category) {
        java.util.List<String> stored = category == null ? null : lines.get(category);
        return stored == null ? java.util.List.of() : stored;
    }

    /**
     * Replaces one category's lines.
     *
     * <p>Blank and null entries are dropped - a blank line renders as an empty bubble, which reads
     * as the NPC glitching rather than as an author's choice. Count and length are capped because
     * these travel in a save packet and end up in entity NBT.
     */
    public void setLines(net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category category, java.util.List<String> replacement) {
        if (category == null) {
            return;
        }
        java.util.List<String> clean = new java.util.ArrayList<>();
        for (String line : replacement == null ? java.util.List.<String>of() : replacement) {
            if (line == null || line.isBlank() || clean.size() >= MAX_LINES_PER_CATEGORY) {
                continue;
            }
            clean.add(line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) : line);
        }
        if (clean.isEmpty()) {
            lines.remove(category);
        } else {
            lines.put(category, java.util.List.copyOf(clean));
        }
    }

        public static String canonicalPalette(String palette) {
        if (palette == null) {
            return DEFAULT_PALETTE;
        }
        String key = palette.trim().toUpperCase(Locale.ROOT);
        return PALETTES.contains(key) ? key : DEFAULT_PALETTE;
    }

    /** Floating mark above the NPC's head: "" or one of {@link #MARK_ICONS}. */
    public String markIcon = "";
    /** Mark tint, 0xRRGGBB. */
    public int markColor = 0xFFFFFF;

    /** The six MyNPCs marks, in the order that menu lists them. */
    public static final java.util.List<String> MARK_ICONS = java.util.List.of(
            "", "cross", "exclamation", "pointer", "question", "skull", "star");

    /** Twenty attack-page placeholders. Empty and Off until filled; cycle skips blanks. */
    /**
     * This NPC's own conversation, or an empty tag when it has none.
     *
     * <p>Held as NBT rather than a parsed tree because that is what crosses the save path and what
     * the editor edits; {@link #dialogue()} parses it on demand. An NPC with its own dialogue uses
     * it, and one without falls back to whatever its role's datapack entry names - the two coexist
     * rather than compete.
     *
     * <p>Server-side only. It is deliberately absent from {@code visualOptionsTag}: a client is
     * sent the tree it needs when a conversation actually opens, so replicating every nearby NPC's
     * whole script to every nearby player would be paying for something nobody is reading.
     */
    public CompoundTag dialogueTag = XenoDialogueNbt.empty();
    public final String[] meleeSlotClips = new String[MELEE_SLOT_COUNT];
    public final boolean[] meleeSlotOn = new boolean[MELEE_SLOT_COUNT];
    /** Transform / charge slots. {@code PUNCH} is mirrored by {@link #meleeAnimation}. */
    public java.util.Map<String, String> stateClips = new java.util.LinkedHashMap<>();

    public void setStateClip(String slot, String clip) {
        String key = slot == null ? "" : slot.trim().toUpperCase(Locale.ROOT);
        if (key.isEmpty()) {
            return;
        }
        String value = clip == null ? "" : clip.trim();
        if (value.isBlank()) {
            stateClips.remove(key);
        } else {
            stateClips.put(key, value);
        }
        if ("PUNCH".equals(key)) {
            meleeAnimation = value;
        }
    }

    public String getStateClip(String slot) {
        String key = slot == null ? "" : slot.trim().toUpperCase(Locale.ROOT);
        if ("PUNCH".equals(key) && meleeAnimation != null && !meleeAnimation.isBlank()) {
            return meleeAnimation;
        }
        return stateClips.getOrDefault(key, "");
    }

    /** Empty plus every clip {@code XenoAnimApi.playClip} accepts, for the wand cycle. */
    public static java.util.List<String> meleeAnimationChoices() {
        java.util.List<String> clips = new java.util.ArrayList<>();
        clips.add("");
        clips.addAll(net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.listClips());
        return clips;
    }

    public static String stepMeleeAnimation(String current, int dir) {
        return NpcFormLookup.step(meleeAnimationChoices(), current == null ? "" : current.trim(), dir);
    }

    public String meleeSlotClip(int index) {
        if (index < 0 || index >= MELEE_SLOT_COUNT) {
            return "";
        }
        String value = meleeSlotClips[index];
        return value == null ? "" : value.trim();
    }

    /**
     * This NPC's own dialogue, or null when it has none.
     *
     * <p>Null is what callers fall through on, to the dialogue this NPC's role names. An empty or
     * unopenable stored dialogue reads as null for the same reason: it should not shadow the
     * role's and leave the NPC silent.
     */
    public net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue dialogue() {
        return XenoDialogueNbt.read(dialogueTag);
    }

    /** Replaces this NPC's own dialogue. A null tag clears it back to the role's. */
    public void setDialogue(CompoundTag tag) {
        dialogueTag = tag == null ? XenoDialogueNbt.empty() : tag.copy();
    }

    public boolean meleeSlotOn(int index) {
        return index >= 0 && index < MELEE_SLOT_COUNT && meleeSlotOn[index];
    }

    public void setMeleeSlot(int index, String clip, boolean on) {
        if (index < 0 || index >= MELEE_SLOT_COUNT) {
            return;
        }
        meleeSlotClips[index] = clip == null ? "" : clip.trim();
        meleeSlotOn[index] = on;
    }

    public void setMeleeSlotClip(int index, String clip) {
        if (index < 0 || index >= MELEE_SLOT_COUNT) {
            return;
        }
        meleeSlotClips[index] = clip == null ? "" : clip.trim();
    }

    public void setMeleeSlotOn(int index, boolean on) {
        if (index < 0 || index >= MELEE_SLOT_COUNT) {
            return;
        }
        meleeSlotOn[index] = on;
    }

    private float effectiveMelee = Float.NaN;
    private float effectiveStrike = Float.NaN;
    private float effectiveKi = Float.NaN;

    /** Transient clone adapter values, never written into general NPC configuration. */
    public void setEffectiveDamage(double melee, double strike, double ki) {
        effectiveMelee = finiteDamage(melee);
        effectiveStrike = finiteDamage(strike);
        effectiveKi = finiteDamage(ki);
    }

    private static float finiteDamage(double damage) {
        return Double.isFinite(damage) ? (float) Math.max(0, Math.min(Float.MAX_VALUE, damage)) : 0f;
    }

    public NpcCombatProfile() {
        java.util.Arrays.fill(itemGiverItems,
                net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack.EMPTY);
        itemGiverLines.add("Have these items {player}");
    }

    /** True only when XenoPixels has explicitly attached a combat profile to this entity. */
    public static boolean hasProfile(Entity entity) {
        return entity != null
                && !(entity instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity)
                && storedProfile(entity) != null;
    }

    /** Namespaced ForgeData key, or unnamespaced {@link NpcProfilePersistence#CNPC_KEY} backup. */
    private static CompoundTag storedProfile(Entity entity) {
        if (entity == null) return null;
        CompoundTag root = entity.getPersistentData();
        if (root.contains(NBT_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag tag = root.getCompound(NBT_KEY);
            return tag.isEmpty() ? null : tag;
        }
        if (root.contains(NpcProfilePersistence.CNPC_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag tag = root.getCompound(NpcProfilePersistence.CNPC_KEY);
            return tag.isEmpty() ? null : tag;
        }
        return null;
    }

    public static NpcCombatProfile read(Entity entity) {
        if (entity instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity clone) {
            return clone.combatProfile();
        }
        CompoundTag stored = storedProfile(entity);
        boolean nativeXenoNpc = entity instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
        if (stored == null) {
            return freshDefaults(nativeXenoNpc, freshSkillIds(entity), NpcSkillSet::maxLevelOf);
        }
        NpcCombatProfile profile = fromTag(stored);
        applyNativeMeleeAnimationDefaults(nativeXenoNpc, profile);
        return profile;
    }

    /** DMZ's skill ids for an unconfigured NPC; none for a player, whose skills are DMZ's own. */
    private static List<String> freshSkillIds(Entity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player
                ? List.of() : NpcSkillSet.knownIds();
    }

    /**
     * The profile of an NPC nobody has configured yet (2026-09-29 owner: "it should be as default
     * the v9 brain not v1 and all skills under dmz toggeled on with max level of every skill").
     *
     * <p>Only an NPC with no stored profile gets this. A saved profile reads what it saved - an old
     * save with no brain tag still reads V1 - so existing NPCs do not change underneath anyone.
     * The combat brain ({@link #combatBrain}) starts switched on, running V9.
     */
    static NpcCombatProfile freshDefaults(boolean nativeXenoNpc, List<String> skillIds,
                                          java.util.function.ToIntFunction<String> maxLevel) {
        NpcCombatProfile profile = new NpcCombatProfile();
        applyNativeMeleeAnimationDefaults(nativeXenoNpc, profile);
        profile.setBrainVersion(NpcCombatBrainVersion.V9);
        // Explicitly, not only through the switch above: where V9 is already the starting brain
        // (XenoNPCs) setBrainVersion has nothing to switch from.
        if (profile.brainVersion == NpcCombatBrainVersion.V9) profile.applyV9SafeDefaults();
        // 2026-09-30 owner: "combat brain v9 is not on by default" - a new NPC fights with it.
        profile.combatBrain = true;
        // 2026-09-30 owner: "fall damage off by default".
        profile.noFallDamage = true;
        profile.grantAllSkills(skillIds, maxLevel);
        // 2026-09-30 owner: new NPCs start as DMZ body type 2 with hair on, hair style 1.
        profile.appearance.bodyType = FRESH_BODY_TYPE;
        profile.hairEnabled = true;
        profile.hairStyleId = FRESH_HAIR_STYLE;
        return profile;
    }

    /** DMZ body type a new NPC starts with. */
    public static final int FRESH_BODY_TYPE = 2;
    /** DMZ hair style a new NPC starts with (0 would mean "the race's default"). */
    public static final int FRESH_HAIR_STYLE = 1;

    /** Every listed DMZ skill on at its max level, keeping the Fly row in step with the fly skill. */
    /** DMZ's skill whose active flag shows the ki weapon; {@link #kiWeaponOn} owns it. */
    public static final String KI_WEAPON_SKILL = "kimanipulation";

    public void grantAllSkills(List<String> skillIds, java.util.function.ToIntFunction<String> maxLevel) {
        if (skillIds == null || maxLevel == null) {
            return;
        }
        for (String id : skillIds) {
            int level = Math.max(1, maxLevel.applyAsInt(id));
            // Ki manipulation is learned at max but left inactive: its active flag is what draws
            // the ki weapon, and that belongs to the editor's own KI Weapon toggle.
            skills.set(id, !KI_WEAPON_SKILL.equals(NpcSkillSet.canonical(id)), level);
            if (NpcSkillSet.FLY.equals(NpcSkillSet.canonical(id))) {
                flySkillOn = true;
                flySkillLevel = clampFlySkillLevel(level);
            }
        }
    }

    /**
     * Read-only view of a profile, parsed at most once per stored-NBT revision.
     *
     * <p>{@link #read} rebuilds the mastery maps, both aura-style maps, the technique list, the
     * appearance and the chunked hair strings on every call, and the per-tick NPC systems called
     * it for every profiled NPC every tick purely to look at a flag or two. This serves those
     * paths from a cache instead.
     *
     * <p><b>The returned instance is shared — never mutate it, and never pass it to
     * {@link #write}.</b> Any caller that intends to change something must use {@link #read},
     * which still returns a private copy. The cache invalidates on tag identity:
     * {@code CompoundTag.getCompound} hands back the stored instance, and {@link #write} always
     * stores a freshly built tag, so a write (or an entity reloading its NBT) is detected as a
     * different reference and forces a reparse.
     */
    public static NpcCombatProfile readCached(Entity entity) {
        if (entity instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity clone)
            return clone.combatProfile();
        if (entity == null) {
            return new NpcCombatProfile();
        }
        boolean nativeXenoNpc = entity instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
        CompoundTag stored = storedProfile(entity);
        if (stored == null) {
            // Shared read-only defaults: the same never-mutate contract as a cached parse.
            return entity instanceof net.minecraft.world.entity.player.Player
                    ? Defaults.PLAYER : Defaults.fresh(nativeXenoNpc);
        }
        java.util.Map<Entity, Cached> cache = cacheFor(entity);
        Cached cached = cache.get(entity);
        if (cached != null && cached.tag == stored) {
            return cached.profile;
        }
        NpcCombatProfile parsed = fromTag(stored);
        applyNativeMeleeAnimationDefaults(nativeXenoNpc, parsed);
        cache.put(entity, new Cached(stored, parsed));
        return parsed;
    }

    /**
     * One cache per logical side. {@code Entity.equals} compares entity ids, and in single player
     * the client copy of an entity carries the same id as the server copy, so a single map made
     * the two sides evict each other on every call and reparse.
     */
    private static java.util.Map<Entity, Cached> cacheFor(Entity entity) {
        return entity.level() != null && entity.level().isClientSide() ? CLIENT_CACHE : CACHE;
    }

    private static NpcCombatProfile defaultsFor(boolean nativeXenoNpc) {
        NpcCombatProfile profile = new NpcCombatProfile();
        applyNativeMeleeAnimationDefaults(nativeXenoNpc, profile);
        return profile;
    }

    /** Lazy holder, so building the defaults never runs during this class's own static init. */
    private static final class Defaults {
        static final NpcCombatProfile PLAYER = defaultsFor(false);
        private static volatile Object skillsConfig;
        private static volatile NpcCombatProfile nativeFresh;
        private static volatile NpcCombatProfile foreignFresh;

        /**
         * The fresh-NPC defaults, rebuilt when DMZ's skills config changes (it loads after this
         * mod, and reloads with the server), so "every skill at max" tracks the real skill list.
         */
        static NpcCombatProfile fresh(boolean nativeXenoNpc) {
            Object config = NpcSkillSet.configIdentity();
            if (nativeFresh == null || config != skillsConfig) {
                List<String> ids = NpcSkillSet.knownIds();
                nativeFresh = freshDefaults(true, ids, NpcSkillSet::maxLevelOf);
                foreignFresh = freshDefaults(false, ids, NpcSkillSet::maxLevelOf);
                skillsConfig = config;
            }
            return nativeXenoNpc ? nativeFresh : foreignFresh;
        }
    }

    /**
     * Enables the four default attacks once for native XenoNPCs. Existing profiles with authored
     * attack clips keep them as-is, and saved empty slots stay empty after an author clears them.
     * MyNPCs/CustomNPCs retain their own animation defaults.
     */
    static void applyNativeMeleeAnimationDefaults(boolean nativeXenoNpc,
                                                   NpcCombatProfile profile) {
        applyNativeFallDefault(nativeXenoNpc, profile);
        applyDmzBaseStats(nativeXenoNpc, profile, dmzBaseStats(profile));
        if (!nativeXenoNpc || profile == null || profile.nativeMeleeAnimationDefaultsApplied) {
            return;
        }
        if (!hasConfiguredMeleeAnimation(profile)) {
            for (int i = 0; i < XENO_DEFAULT_MELEE_ANIMATIONS.size(); i++) {
                profile.setMeleeSlot(i, XENO_DEFAULT_MELEE_ANIMATIONS.get(i), true);
            }
        }
        profile.nativeMeleeAnimationDefaultsApplied = true;
    }

    /**
     * 2026-09-30 owner: "fall damage off by default" / "npc dying on fall damage". Native Xeno
     * NPCs saved before that default get No Fall Damage turned on once; the marker keeps an author's
     * later choice to turn it back off.
     */
    /**
     * 2026-09-30 owner: "he doens't damage me too on hits", "they dont have battlepower". A fresh
     * profile never set its six stats, so the NPC's DragonMineZ blob read STR/VIT/RES/SKP/PWR/ENE 0:
     * zero battle power, and melee from STR 0 with no RES stamina to spend. Once per native NPC, a
     * profile still at all zeros takes the base stats DMZ gives a new character of its race and class.
     * Authored stats are never touched, and a later all-zero edit is the author's choice.
     *
     * @param base STR, SKP, RES, VIT, PWR, ENE from DMZ's race config; null when it is unavailable,
     *             which leaves the profile to be tried again on a later load
     * @return whether the stats were set
     */
    static boolean applyDmzBaseStats(boolean nativeXenoNpc, NpcCombatProfile profile, int[] base) {
        if (!nativeXenoNpc || profile == null || profile.dmzBaseStatsApplied) return false;
        boolean allZero = profile.strength == 0 && profile.strikePower == 0 && profile.resistance == 0
                && profile.vitality == 0 && profile.kiPower == 0 && profile.energy == 0;
        if (!allZero) {
            profile.dmzBaseStatsApplied = true;
            return false;
        }
        if (base == null || base.length != 6) return false;
        profile.strength = Math.max(0, base[0]);
        profile.strikePower = Math.max(0, base[1]);
        profile.resistance = Math.max(0, base[2]);
        profile.vitality = Math.max(0, base[3]);
        profile.kiPower = Math.max(0, base[4]);
        profile.energy = Math.max(0, base[5]);
        profile.dmzBaseStatsApplied = true;
        return true;
    }

    /** DMZ's base stats for the profile's race and class, or null when its config is not loaded. */
    static int[] dmzBaseStats(NpcCombatProfile profile) {
        if (profile == null) return null;
        try {
            String race = profile.raceId == null || profile.raceId.isBlank() ? "human" : profile.raceId;
            var raceConfig = com.dragonminez.common.config.ConfigManager.getRaceStats(race);
            if (raceConfig == null) return null;
            var classStats = raceConfig.getClassStats(profile.characterClass());
            var base = classStats == null ? null : classStats.getBaseStats();
            if (base == null) return null;
            Integer[] values = {base.getStrength(), base.getStrikePower(), base.getResistance(),
                    base.getVitality(), base.getKiPower(), base.getEnergy()};
            int[] out = new int[6];
            for (int i = 0; i < 6; i++) {
                if (values[i] == null) return null;
                out[i] = values[i];
            }
            return out;
        } catch (Throwable notLoaded) {
            return null;
        }
    }

    static void applyNativeFallDefault(boolean nativeXenoNpc, NpcCombatProfile profile) {
        if (!nativeXenoNpc || profile == null || profile.nativeFallDefaultApplied) {
            return;
        }
        profile.noFallDamage = true;
        profile.nativeFallDefaultApplied = true;
    }

    private static boolean hasConfiguredMeleeAnimation(NpcCombatProfile profile) {
        if (profile.meleeAnimation != null && !profile.meleeAnimation.isBlank()) {
            return true;
        }
        String punch = profile.stateClips.get("PUNCH");
        if (punch != null && !punch.isBlank()) {
            return true;
        }
        for (int i = 0; i < MELEE_SLOT_COUNT; i++) {
            if (!profile.meleeSlotClip(i).isBlank() || profile.meleeSlotOn(i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Weak keys so a despawned or unloaded entity's cached profile is collectable without any
     * explicit eviction call, and so the cache can never keep an entity alive. Synchronized
     * because profiles are read from both the server thread and the client render thread.
     */
    private static final java.util.Map<Entity, Cached> CACHE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<Entity, Cached> CLIENT_CACHE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private record Cached(CompoundTag tag, NpcCombatProfile profile) {}

    /** Decodes the same profile payload used by the wand save packet. */
    public static NpcCombatProfile fromTag(CompoundTag tag) {
        NpcCombatProfile profile = new NpcCombatProfile();
        if (tag == null || tag.isEmpty()) {
            return profile;
        }
        profile.authoritative = !tag.contains(TAG_AUTHORITATIVE) || tag.getBoolean(TAG_AUTHORITATIVE);
        profile.knockable = !tag.contains(TAG_KNOCKABLE) || tag.getBoolean(TAG_KNOCKABLE);
        profile.punchable = !tag.contains(TAG_PUNCHABLE) || tag.getBoolean(TAG_PUNCHABLE);
        profile.pinNativeCombat = !tag.contains(TAG_PIN_NATIVE) || tag.getBoolean(TAG_PIN_NATIVE);
        profile.raceId = tag.contains(TAG_RACE) ? tag.getString(TAG_RACE) : profile.raceId;
        profile.strength = tag.getInt(TAG_STRENGTH);
        profile.strikePower = tag.getInt(TAG_STRIKE_POWER);
        profile.resistance = tag.getInt(TAG_RESISTANCE);
        profile.vitality = tag.getInt(TAG_VITALITY);
        profile.maxHealthOverride = Math.max(0, tag.getInt(TAG_MAX_HEALTH_OVERRIDE));
        if (tag.contains(TAG_NPC_MELEE_PROPS, Tag.TAG_COMPOUND)) {
            CompoundTag props = tag.getCompound(TAG_NPC_MELEE_PROPS);
            profile.npcMeleeDamage = clampNpcMeleeDamage(props.getFloat("Damage"));
            profile.npcMeleeRange = clampNpcMeleeRange(props.getFloat("Range"));
            profile.npcMeleeSpeed = clampNpcMeleeSpeed(props.getFloat("Speed"));
            profile.npcMeleeKnockback = clampNpcProjectileKnockback(props.getFloat("Knockback"));
            profile.npcMeleeEffect = safeResourceId(props.getString("Effect"));
            profile.npcMeleeEffectDuration = clampNpcEffectDuration(props.getInt("EffectDuration"));
            profile.npcMeleeEffectAmplifier = clampNpcEffectAmplifier(props.getInt("EffectAmplifier"));
        }
        if (tag.contains(TAG_NPC_RANGED_PROPS, Tag.TAG_COMPOUND)) {
            CompoundTag props = tag.getCompound(TAG_NPC_RANGED_PROPS);
            profile.npcRangedAccuracy = clampNpcRangedAccuracy(props.getFloat("Accuracy"));
            profile.npcRangedRange = clampNpcRangedRange(props.getFloat("Range"));
            profile.npcRangedMinRange = clampNpcRangedRange(props.getFloat("MinRange"));
            profile.npcRangedMinDelay = clampNpcRangedDelay(props.getInt("MinDelay"));
            profile.npcRangedMaxDelay = Math.max(profile.npcRangedMinDelay,
                    clampNpcRangedDelay(props.getInt("MaxDelay")));
            profile.npcRangedShotCount = clampNpcShotCount(props.getInt("ShotCount"));
            profile.npcRangedBurstCount = clampNpcBurstCount(props.getInt("BurstCount"));
            profile.npcRangedBurstRate = clampNpcRangedDelay(props.getInt("BurstRate"));
            profile.npcRangedIndirect = props.getBoolean("Indirect");
            profile.npcRangedAimMode = canonicalNpcAimMode(props.getString("AimMode"));
            profile.npcRangedFireSound = safeResourceId(props.getString("FireSound"));
            profile.npcRangedHitSound = safeResourceId(props.getString("HitSound"));
            profile.npcRangedGroundSound = safeResourceId(props.getString("GroundSound"));
        }
        if (tag.contains(TAG_NPC_PROJECTILE_PROPS, Tag.TAG_COMPOUND)) {
            CompoundTag props = tag.getCompound(TAG_NPC_PROJECTILE_PROPS);
            profile.npcProjectileStrength = clampNpcProjectileStrength(props.getFloat("Strength"));
            profile.npcProjectileKnockback = clampNpcProjectileKnockback(props.getFloat("Knockback"));
            profile.npcProjectileSize = clampNpcProjectileSize(props.getFloat("Size"));
            profile.npcProjectileSpeed = clampNpcProjectileSpeed(props.getFloat("Speed"));
            profile.npcProjectileGravity = canonicalProjectileGravity(props.getString("Gravity"));
            profile.npcProjectileExplosion = clampNpcProjectileExplosion(props.getFloat("Explosion"));
            profile.npcProjectileEffect = safeResourceId(props.getString("Effect"));
            // A profile written before the projectile had its own effect timing used the melee
            // pair, so that is what an older save has to keep reading - otherwise every existing
            // projectile effect would silently drop to the one-tick floor on first load.
            profile.npcProjectileEffectDuration = props.contains("EffectDuration", Tag.TAG_INT)
                    ? clampNpcEffectDuration(props.getInt("EffectDuration"))
                    : profile.npcMeleeEffectDuration;
            profile.npcProjectileEffectAmplifier = props.contains("EffectAmplifier", Tag.TAG_INT)
                    ? clampNpcEffectAmplifier(props.getInt("EffectAmplifier"))
                    : profile.npcMeleeEffectAmplifier;
            profile.npcProjectileTrail = canonicalProjectileTrail(props.getString("Trail"));
            profile.npcProjectileSpins = props.getBoolean("Spins");
            profile.npcProjectileSticks = props.getBoolean("Sticks");
            profile.npcProjectileGlows = props.getBoolean("Glows");
        }
        if (tag.contains(TAG_NPC_RESISTANCE_PROPS, Tag.TAG_COMPOUND)) {
            CompoundTag props = tag.getCompound(TAG_NPC_RESISTANCE_PROPS);
            profile.npcKnockbackResistance = clampNpcResistance(props.getInt("Knockback"));
            profile.npcArrowResistance = clampNpcResistance(props.getInt("Arrow"));
            profile.npcMeleeResistance = clampNpcResistance(props.getInt("Melee"));
            profile.npcExplosionResistance = clampNpcResistance(props.getInt("Explosion"));
        }
        profile.kiPower = tag.getInt(TAG_KI_POWER);
        profile.energy = tag.getInt(TAG_ENERGY);
        profile.kiColor = tag.getInt(TAG_KI_COLOR);
        profile.auraOn = tag.getBoolean(TAG_AURA_ON);
        int schema = tag.contains(TAG_SCHEMA) ? tag.getInt(TAG_SCHEMA) : 0;
        profile.auraColor = tag.getInt(TAG_AURA_COLOR);
        profile.auraColorHex = canonicalizeOptionalColor(tag.getString(TAG_AURA_COLOR_HEX));
        // Legacy-data migration only (a save from before AuraColorHex existed) -- gated by
        // schema, not just "hex happens to be blank right now", so a stray/transient write to
        // the bare auraColor int on an already-current-schema profile can never get silently
        // canonicalized into permanent hex state on the next read. Unconditional before this,
        // that self-reinforcement made a bare-int corruption from ANY source (however it got
        // there) stick forever, surviving descend/restarts.
        if (schema < AURA_SCHEMA && profile.auraColorHex.isEmpty() && profile.auraColor != 0) {
            profile.auraColorHex = formatHex(profile.auraColor);
        }
        profile.formGroup = tag.getString(TAG_FORM_GROUP);
        profile.formId = tag.getString(TAG_FORM);
        profile.selectedFormGroup = tag.contains(TAG_SELECTED_FORM_GROUP)
                ? tag.getString(TAG_SELECTED_FORM_GROUP) : profile.formGroup;
        profile.selectedFormId = tag.contains(TAG_SELECTED_FORM)
                ? tag.getString(TAG_SELECTED_FORM) : profile.formId;
        profile.stackGroup = tag.getString(TAG_STACK_GROUP);
        profile.stackId = tag.getString(TAG_STACK_FORM);
        profile.selectedStackGroup = tag.contains(TAG_SELECTED_STACK_GROUP)
                ? tag.getString(TAG_SELECTED_STACK_GROUP) : profile.stackGroup;
        profile.selectedStackId = tag.contains(TAG_SELECTED_STACK_FORM)
                ? tag.getString(TAG_SELECTED_STACK_FORM) : profile.stackId;
        profile.haloOn = tag.getBoolean(TAG_HALO);
        profile.auraRocks = !tag.contains(TAG_ROCKS) || tag.getBoolean(TAG_ROCKS);
        profile.auraSparking = !tag.contains(TAG_SPARKING) || tag.getBoolean(TAG_SPARKING);
        profile.auraLightning = !tag.contains(TAG_LIGHTNING) || tag.getBoolean(TAG_LIGHTNING);
        profile.auraGroundRing = !tag.contains(TAG_GROUND_RING) || tag.getBoolean(TAG_GROUND_RING);
        profile.flySkillOn = tag.getBoolean(TAG_FLY_ON);
        profile.flySkillLevel = clampFlySkillLevel(
                tag.contains(TAG_FLY_LEVEL) ? tag.getInt(TAG_FLY_LEVEL) : 1);
        profile.aggroMultiplier = clampAggroMultiplier(
                tag.contains(TAG_AGGRO_MULTIPLIER) ? tag.getFloat(TAG_AGGRO_MULTIPLIER) : 2.0f);
        profile.aimAccuracy = clampAimAccuracy(
                tag.contains(TAG_AIM_ACCURACY) ? tag.getFloat(TAG_AIM_ACCURACY) : 0.85f);
        profile.readBrainFlags(tag);
        if (tag.contains(TAG_SKILLS, Tag.TAG_LIST)) {
            profile.skills.load(tag.getList(TAG_SKILLS, Tag.TAG_COMPOUND));
        }
        profile.kiSenseLockOnRetaliator = tag.getBoolean(TAG_KI_SENSE_LOCK_ON);
        // Schema 9 generalized the single fly toggle into a full skill map. Older profiles
        // carry their fly state across so a configured flying NPC keeps flying.
        if (schema < SKILL_MAP_SCHEMA && profile.flySkillOn) {
            profile.skills.set(NpcSkillSet.FLY, true, profile.flySkillLevel);
        }
        profile.kiWeaponOn = tag.getBoolean(TAG_KI_WEAPON_ON);
        profile.kiWeaponType = canonicalKiWeaponType(tag.getString(TAG_KI_WEAPON_TYPE));
        if (tag.contains(TAG_BASE_AURA_STYLE, Tag.TAG_COMPOUND)) {
            profile.baseAuraStyle = NpcAuraStyle.load(tag.getCompound(TAG_BASE_AURA_STYLE));
        }
        loadAuraStyles(tag.getList(TAG_FORM_AURA_STYLES, Tag.TAG_COMPOUND), profile.formAuraStyles);
        loadAuraStyles(tag.getList(TAG_STACK_AURA_STYLES, Tag.TAG_COMPOUND), profile.stackAuraStyles);
        profile.skinPlayer = tag.getString(TAG_SKIN_PLAYER);
        profile.skinUrl = tag.getString(TAG_SKIN_URL);
        profile.formPower = tag.contains(TAG_FORM_POWER) ? tag.getDouble(TAG_FORM_POWER) : 1.0;
        if (profile.formPower <= 0.0) {
            profile.formPower = 1.0;
        }
        profile.baseSize = tag.getInt(TAG_BASE_SIZE);
        profile.kiChargePercent = tag.contains(TAG_KI_CHARGE) ? tag.getInt(TAG_KI_CHARGE) : 100;
        if (profile.kiChargePercent <= 0) {
            profile.kiChargePercent = 100;
        }
        profile.powerReleasePercent = tag.contains(TAG_POWER_RELEASE) ? tag.getInt(TAG_POWER_RELEASE) : 100;
        if (profile.powerReleasePercent <= 0) {
            profile.powerReleasePercent = 100;
        }
        profile.auraScale = tag.contains(TAG_AURA_SCALE) ? tag.getFloat(TAG_AURA_SCALE) : 1.7f;
        // Older builds baked their fixed 1.7 correction into every new profile. Native DMZ aura
        // now follows NPC size directly, so only that exact legacy default is normalized.
        if (schema < AURA_SCHEMA && Float.compare(profile.auraScale, 1.7f) == 0) {
            profile.auraScale = 1.0f;
        }
        // Schema 7 raised the default aura scale from 1.0 to 1.7. writeTag writes AuraScale
        // unconditionally, so a stored 1.0 on a pre-schema-7 save is indistinguishable from
        // the old default and every exactly-1.0 profile migrates once here.
        if (schema < AURA_DEFAULT_SCHEMA && Float.compare(profile.auraScale, 1.0f) == 0) {
            profile.auraScale = 1.7f;
        }
        profile.auraScale = clampAuraScale(profile.auraScale);
        profile.hairEnabled = tag.getBoolean(TAG_HAIR_ENABLED);
        profile.hairCode = readHairCode(tag);
        profile.hairColor = canonicalizeHairColor(tag.getString(TAG_HAIR_COLOR));
        profile.hairStyleId = Math.max(0, tag.getInt(TAG_HAIR_STYLE));
        if (tag.contains(TAG_DMZ_APPEARANCE, Tag.TAG_COMPOUND)) {
            profile.appearance = NpcDmzAppearance.fromTag(tag.getCompound(TAG_DMZ_APPEARANCE));
        }
        profile.modelKind = normalizeModelKind(tag.getString(TAG_MODEL_KIND));
        profile.modelId = tag.getString(TAG_MODEL_ID);
        profile.modelAnimation = tag.getString(TAG_MODEL_ANIMATION);
        profile.modelTexture = tag.getString(TAG_MODEL_TEXTURE);
        // An absent tint must not read as black, so a missing key means untinted white.
        profile.modelTint = tag.contains(TAG_MODEL_TINT)
                ? (tag.getInt(TAG_MODEL_TINT) & 0xFFFFFF) : 0xFFFFFF;
        profile.modelGlowing = tag.getBoolean(TAG_MODEL_GLOWING);
        profile.displayCape = tag.getString(TAG_DISPLAY_CAPE);
        profile.displayOverlay = tag.getString(TAG_DISPLAY_OVERLAY);
        profile.displayOverlayGlow = tag.getBoolean(TAG_DISPLAY_OVERLAY_GLOW);
        profile.displayOuterLayers = !tag.contains(TAG_DISPLAY_LAYERS) || tag.getBoolean(TAG_DISPLAY_LAYERS);
        profile.fireImmune = tag.getBoolean(TAG_FIRE_IMMUNE);
        profile.burnsInSun = tag.getBoolean(TAG_BURNS_IN_SUN);
        // Absent keys must keep the shipped default rather than reading as false, or every NPC
        // saved before this block existed would silently stop drowning and start phasing through
        // cobwebs.
        profile.canDrown = !tag.contains(TAG_CAN_DROWN) || tag.getBoolean(TAG_CAN_DROWN);
        profile.noFallDamage = tag.getBoolean(TAG_NO_FALL_DAMAGE);
        profile.potionImmune = tag.getBoolean(TAG_POTION_IMMUNE);
        profile.cobwebAffected = !tag.contains(TAG_COBWEB_AFFECTED)
                || tag.getBoolean(TAG_COBWEB_AFFECTED);
        profile.visible = !tag.contains(TAG_VISIBLE) || tag.getBoolean(TAG_VISIBLE);
        profile.hitboxScale = tag.contains(TAG_HITBOX_SCALE)
                ? clampHitbox(tag.getFloat(TAG_HITBOX_SCALE)) : 1.0f;
        profile.bossBar = tag.getBoolean(TAG_BOSS_BAR);
        if (tag.contains(TAG_BOSS_BAR_COLOR)) {
            profile.bossBarColor = tag.getString(TAG_BOSS_BAR_COLOR);
        }
        if (tag.contains(TAG_CREATURE_TYPE)) {
            profile.creatureType = tag.getString(TAG_CREATURE_TYPE);
        }
        if (tag.contains(TAG_MASTERIES)) {
            profile.masteries.load(tag.getCompound(TAG_MASTERIES));
        }
        if (tag.contains(TAG_STACK_MASTERIES)) {
            profile.stackMasteries.load(tag.getCompound(TAG_STACK_MASTERIES));
        }
        ListTag techs = tag.getList(TAG_TECHNIQUES, Tag.TAG_STRING);
        for (int i = 0; i < techs.size(); i++) {
            String id = techs.getString(i);
            if (!id.isBlank()) {
                profile.techniques.add(id.toLowerCase(Locale.ROOT));
            }
        }
        if (tag.contains(TAG_TECHNIQUE_LEVELS, Tag.TAG_COMPOUND)) {
            profile.techniqueLevels.load(tag.getCompound(TAG_TECHNIQUE_LEVELS));
        }
        profile.meleeAnimation = tag.getString(TAG_MELEE_ANIMATION);
        profile.readMeleeAnimSlots(tag);
        profile.nativeMeleeAnimationDefaultsApplied =
                tag.getBoolean(TAG_NATIVE_MELEE_DEFAULTS_APPLIED);
        profile.nativeFallDefaultApplied = tag.getBoolean(TAG_NATIVE_FALL_DEFAULT_APPLIED);
        profile.dmzBaseStatsApplied = tag.getBoolean(TAG_DMZ_BASE_STATS_APPLIED);
        if (tag.contains(TAG_DIALOGUE, Tag.TAG_COMPOUND)) {
            profile.dialogueTag = tag.getCompound(TAG_DIALOGUE).copy();
        }
        profile.readAdvancedTab(tag);
        if (tag.contains(TAG_STATE_CLIPS, Tag.TAG_COMPOUND)) {
            CompoundTag clips = tag.getCompound(TAG_STATE_CLIPS);
            for (String key : clips.getAllKeys()) {
                String value = clips.getString(key);
                if (!key.isBlank() && !value.isBlank()) {
                    profile.stateClips.put(key.toUpperCase(Locale.ROOT), value);
                }
            }
        }
        return profile;
    }

    public CompoundTag toTag() {
        return writeTag();
    }

    /**
     * Returns {@code profileTag} with only {@code kiWeaponOn} changed.
     *
     * <p>The Stats tab's KI Weapon button saves the whole profile, so it has to rebuild the
     * payload from the NPC's current state rather than from a blank profile — otherwise the
     * appearance, transformation, mastery and combat fields owned by the other DMZ screens would
     * be overwritten with defaults on every toggle.
     */
    public static CompoundTag withKiWeapon(CompoundTag profileTag, boolean kiWeaponOn) {
        NpcCombatProfile profile = fromTag(profileTag);
        profile.kiWeaponOn = kiWeaponOn;
        return profile.toTag();
    }

    /**
     * Returns {@code profileTag} with only {@code combatBrain} changed, so the Stats tab
     * toggle cannot clobber fields owned by other screens.
     */
    public static CompoundTag withCombatBrain(CompoundTag profileTag, boolean combatBrain) {
        NpcCombatProfile profile = fromTag(profileTag);
        profile.combatBrain = combatBrain;
        return profile.toTag();
    }

    public static CompoundTag withBrainVersion(CompoundTag profileTag, NpcCombatBrainVersion version) {
        NpcCombatProfile profile = fromTag(profileTag);
        profile.setBrainVersion((version == null ? NpcCombatBrainVersion.V1 : version).legacyCompatible());
        return profile.toTag();
    }

    public void write(Entity entity) {
        if (entity instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity) return;
        boolean wasBrain = false;
        if (!entity.level().isClientSide() && entity instanceof net.minecraft.world.entity.LivingEntity
                && hasProfile(entity)) {
            wasBrain = readCached(entity).combatBrain;
        }
        CompoundTag tag = writeTag();
        CompoundTag existing = entity.getPersistentData().contains(NBT_KEY)
                ? entity.getPersistentData().getCompound(NBT_KEY) : new CompoundTag();
        boolean unchanged = existing.equals(tag);
        entity.getPersistentData().put(NBT_KEY, tag);
        entity.getPersistentData().put(NpcProfilePersistence.CNPC_KEY, tag.copy());
        if (!entity.level().isClientSide() && wasBrain && !combatBrain
                && entity instanceof net.minecraft.world.entity.LivingEntity living) {
            NpcCombatBrain.disengage(living);
            net.bullettrain.xenopixelsmod.compat.npc.brain.v2.NpcSagaCombatBrain.disengage(living);
        }
        NpcProfileLifecycle.track(entity);
        if (entity.level().isClientSide() || unchanged) {
            NpcHairBridge.applyProfile(entity, this);
            return;
        }
        NpcCounterpartSync.force(entity, this);
        // Open editors and DMZ screens on this NPC see the change instead of saving a stale copy back.
        NpcProfileWatchers.changed(entity, tag);
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            NpcFormAttributeSync.apply(living, this);
            NpcMeleeDamage.applyProfile(living, this);
        }
        // Only on a change, because vanilla saves an entity's equipment in its own NBT and it comes
        // back on load without being reapplied. Blanking a field still clears the slot: applyTo
        // writes every slot, including the empty ones.
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            gear.applyTo(mob);
            // The AI settings that are entity state rather than per-tick decisions: the water malus
            // and whether the navigation may route through a door.
            net.bullettrain.xenopixelsmod.npc.NpcAiGoals.apply(mob, this);
        }
        NpcHairBridge.applyProfile(entity, this);
        NpcProfileLifecycle.repairAi(entity);
        NpcAppearanceFx.sync(entity);
        NpcAuraFx.sync(entity);
    }

    private CompoundTag writeTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_SCHEMA, CURRENT_SCHEMA);
        tag.putBoolean(TAG_AUTHORITATIVE, authoritative);
        tag.putBoolean(TAG_KNOCKABLE, knockable);
        tag.putBoolean(TAG_PUNCHABLE, punchable);
        tag.putBoolean(TAG_PIN_NATIVE, pinNativeCombat);
        tag.putString(TAG_RACE, raceId);
        tag.putInt(TAG_STRENGTH, strength);
        tag.putInt(TAG_STRIKE_POWER, strikePower);
        tag.putInt(TAG_RESISTANCE, resistance);
        tag.putInt(TAG_VITALITY, vitality);
        tag.putInt(TAG_MAX_HEALTH_OVERRIDE, Math.max(0, maxHealthOverride));
        CompoundTag meleeProps = new CompoundTag();
        meleeProps.putFloat("Damage", clampNpcMeleeDamage(npcMeleeDamage));
        meleeProps.putFloat("Range", clampNpcMeleeRange(npcMeleeRange));
        meleeProps.putFloat("Speed", clampNpcMeleeSpeed(npcMeleeSpeed));
        meleeProps.putFloat("Knockback", clampNpcProjectileKnockback(npcMeleeKnockback));
        meleeProps.putString("Effect", safeResourceId(npcMeleeEffect));
        meleeProps.putInt("EffectDuration", clampNpcEffectDuration(npcMeleeEffectDuration));
        meleeProps.putInt("EffectAmplifier", clampNpcEffectAmplifier(npcMeleeEffectAmplifier));
        tag.put(TAG_NPC_MELEE_PROPS, meleeProps);
        CompoundTag rangedProps = new CompoundTag();
        rangedProps.putFloat("Accuracy", clampNpcRangedAccuracy(npcRangedAccuracy));
        rangedProps.putFloat("Range", clampNpcRangedRange(npcRangedRange));
        rangedProps.putFloat("MinRange", clampNpcRangedRange(npcRangedMinRange));
        rangedProps.putInt("MinDelay", clampNpcRangedDelay(npcRangedMinDelay));
        rangedProps.putInt("MaxDelay", Math.max(clampNpcRangedDelay(npcRangedMinDelay),
                clampNpcRangedDelay(npcRangedMaxDelay)));
        rangedProps.putInt("ShotCount", clampNpcShotCount(npcRangedShotCount));
        rangedProps.putInt("BurstCount", clampNpcBurstCount(npcRangedBurstCount));
        rangedProps.putInt("BurstRate", clampNpcRangedDelay(npcRangedBurstRate));
        rangedProps.putBoolean("Indirect", npcRangedIndirect);
        rangedProps.putString("AimMode", canonicalNpcAimMode(npcRangedAimMode));
        rangedProps.putString("FireSound", safeResourceId(npcRangedFireSound));
        rangedProps.putString("HitSound", safeResourceId(npcRangedHitSound));
        rangedProps.putString("GroundSound", safeResourceId(npcRangedGroundSound));
        tag.put(TAG_NPC_RANGED_PROPS, rangedProps);
        CompoundTag projectileProps = new CompoundTag();
        projectileProps.putFloat("Strength", clampNpcProjectileStrength(npcProjectileStrength));
        projectileProps.putFloat("Knockback", clampNpcProjectileKnockback(npcProjectileKnockback));
        projectileProps.putFloat("Size", clampNpcProjectileSize(npcProjectileSize));
        projectileProps.putFloat("Speed", clampNpcProjectileSpeed(npcProjectileSpeed));
        projectileProps.putString("Gravity", canonicalProjectileGravity(npcProjectileGravity));
        projectileProps.putFloat("Explosion", clampNpcProjectileExplosion(npcProjectileExplosion));
        projectileProps.putString("Effect", safeResourceId(npcProjectileEffect));
        projectileProps.putInt("EffectDuration", clampNpcEffectDuration(npcProjectileEffectDuration));
        projectileProps.putInt("EffectAmplifier",
                clampNpcEffectAmplifier(npcProjectileEffectAmplifier));
        projectileProps.putString("Trail", canonicalProjectileTrail(npcProjectileTrail));
        projectileProps.putBoolean("Spins", npcProjectileSpins);
        projectileProps.putBoolean("Sticks", npcProjectileSticks);
        projectileProps.putBoolean("Glows", npcProjectileGlows);
        tag.put(TAG_NPC_PROJECTILE_PROPS, projectileProps);
        CompoundTag resistanceProps = new CompoundTag();
        resistanceProps.putInt("Knockback", clampNpcResistance(npcKnockbackResistance));
        resistanceProps.putInt("Arrow", clampNpcResistance(npcArrowResistance));
        resistanceProps.putInt("Melee", clampNpcResistance(npcMeleeResistance));
        resistanceProps.putInt("Explosion", clampNpcResistance(npcExplosionResistance));
        tag.put(TAG_NPC_RESISTANCE_PROPS, resistanceProps);
        tag.putInt(TAG_KI_POWER, kiPower);
        tag.putInt(TAG_ENERGY, energy);
        tag.putInt(TAG_KI_COLOR, kiColor);
        tag.putBoolean(TAG_AURA_ON, auraOn);
        if (!auraColorHex.isEmpty()) {
            auraColor = parseHexColor(auraColorHex).orElse(auraColor) & 0xFFFFFF;
        }
        tag.putInt(TAG_AURA_COLOR, auraColor);
        tag.putString(TAG_AURA_COLOR_HEX, canonicalizeOptionalColor(auraColorHex));
        tag.putString(TAG_FORM_GROUP, formGroup == null ? "" : formGroup);
        tag.putString(TAG_FORM, formId == null ? "" : formId);
        tag.putString(TAG_SELECTED_FORM_GROUP, safe(selectedFormGroup));
        tag.putString(TAG_SELECTED_FORM, safe(selectedFormId));
        tag.putString(TAG_STACK_GROUP, safe(stackGroup));
        tag.putString(TAG_STACK_FORM, safe(stackId));
        tag.putString(TAG_SELECTED_STACK_GROUP, safe(selectedStackGroup));
        tag.putString(TAG_SELECTED_STACK_FORM, safe(selectedStackId));
        tag.putBoolean(TAG_HALO, haloOn);
        tag.putBoolean(TAG_ROCKS, auraRocks);
        tag.putBoolean(TAG_SPARKING, auraSparking);
        tag.putBoolean(TAG_LIGHTNING, auraLightning);
        tag.putBoolean(TAG_GROUND_RING, auraGroundRing);
        tag.putBoolean(TAG_FLY_ON, flySkillOn);
        tag.putInt(TAG_FLY_LEVEL, flySkillLevel);
        tag.put(TAG_SKILLS, skills.save());
        tag.putBoolean(TAG_KI_SENSE_LOCK_ON, kiSenseLockOnRetaliator);
        tag.putFloat(TAG_AGGRO_MULTIPLIER, aggroMultiplier);
        tag.putFloat(TAG_AIM_ACCURACY, aimAccuracy);
        writeBrainFlags(tag);
        tag.putBoolean(TAG_KI_WEAPON_ON, kiWeaponOn);
        tag.putString(TAG_KI_WEAPON_TYPE, canonicalKiWeaponType(kiWeaponType));
        tag.put(TAG_BASE_AURA_STYLE, baseAuraStyle.save());
        tag.put(TAG_FORM_AURA_STYLES, saveAuraStyles(formAuraStyles));
        tag.put(TAG_STACK_AURA_STYLES, saveAuraStyles(stackAuraStyles));
        tag.putString(TAG_SKIN_PLAYER, skinPlayer == null ? "" : skinPlayer);
        tag.putString(TAG_SKIN_URL, skinUrl == null ? "" : skinUrl);
        tag.putDouble(TAG_FORM_POWER, formPower <= 0.0 ? 1.0 : formPower);
        tag.putInt(TAG_BASE_SIZE, baseSize);
        tag.putInt(TAG_KI_CHARGE, kiChargePercent <= 0 ? 100 : kiChargePercent);
        tag.putInt(TAG_POWER_RELEASE, powerReleasePercent <= 0 ? 100 : powerReleasePercent);
        tag.putFloat(TAG_AURA_SCALE, clampAuraScale(auraScale));
        tag.putBoolean(TAG_HAIR_ENABLED, hairEnabled);
        writeHairCode(tag, hairCode);
        tag.putString(TAG_HAIR_COLOR, canonicalizeHairColor(hairColor));
        tag.putInt(TAG_HAIR_STYLE, Math.max(0, hairStyleId));
        tag.put(TAG_DMZ_APPEARANCE, (appearance == null ? new NpcDmzAppearance() : appearance).toTag());
        tag.putString(TAG_MODEL_KIND, normalizeModelKind(modelKind));
        tag.putString(TAG_MODEL_ID, modelId == null ? "" : modelId.trim());
        tag.putString(TAG_MODEL_ANIMATION, modelAnimation == null ? "" : modelAnimation.trim());
        tag.putString(TAG_MODEL_TEXTURE, modelTexture == null ? "" : modelTexture.trim());
        tag.putInt(TAG_MODEL_TINT, modelTint & 0xFFFFFF);
        tag.putBoolean(TAG_MODEL_GLOWING, modelGlowing);
        tag.putString(TAG_DISPLAY_CAPE, safe(displayCape));
        tag.putString(TAG_DISPLAY_OVERLAY, safe(displayOverlay));
        tag.putBoolean(TAG_DISPLAY_OVERLAY_GLOW, displayOverlayGlow);
        tag.putBoolean(TAG_DISPLAY_LAYERS, displayOuterLayers);
        tag.putBoolean(TAG_FIRE_IMMUNE, fireImmune);
        tag.putBoolean(TAG_BURNS_IN_SUN, burnsInSun);
        tag.putBoolean(TAG_CAN_DROWN, canDrown);
        tag.putBoolean(TAG_NO_FALL_DAMAGE, noFallDamage);
        tag.putBoolean(TAG_POTION_IMMUNE, potionImmune);
        tag.putBoolean(TAG_COBWEB_AFFECTED, cobwebAffected);
        tag.putBoolean(TAG_VISIBLE, visible);
        tag.putFloat(TAG_HITBOX_SCALE, clampHitbox(hitboxScale));
        tag.putBoolean(TAG_BOSS_BAR, bossBar);
        tag.putString(TAG_BOSS_BAR_COLOR, bossBarColor == null ? "PURPLE" : bossBarColor);
        tag.putString(TAG_CREATURE_TYPE, creatureType == null ? "normal" : creatureType);
        tag.put(TAG_MASTERIES, masteries.save());
        tag.put(TAG_STACK_MASTERIES, stackMasteries.save());
        ListTag techs = new ListTag();
        for (String id : techniques) {
            if (id != null && !id.isBlank()) {
                techs.add(StringTag.valueOf(id.toLowerCase(Locale.ROOT)));
            }
        }
        tag.put(TAG_TECHNIQUES, techs);
        tag.put(TAG_TECHNIQUE_LEVELS, techniqueLevels.save());
        tag.putString(TAG_MELEE_ANIMATION, meleeAnimation == null ? "" : meleeAnimation.trim());
        writeMeleeAnimSlots(tag);
        tag.putBoolean(TAG_NATIVE_MELEE_DEFAULTS_APPLIED, nativeMeleeAnimationDefaultsApplied);
        tag.putBoolean(TAG_NATIVE_FALL_DEFAULT_APPLIED, nativeFallDefaultApplied);
        tag.putBoolean(TAG_DMZ_BASE_STATS_APPLIED, dmzBaseStatsApplied);
        tag.put(TAG_DIALOGUE, dialogueTag == null ? XenoDialogueNbt.empty() : dialogueTag.copy());
        tag.put(TAG_STATE_CLIPS, writeStateClips());
        writeAdvancedTab(tag);
        return tag;
    }

    private void writeMeleeAnimSlots(CompoundTag tag) {
        ListTag list = new ListTag();
        for (int i = 0; i < MELEE_SLOT_COUNT; i++) {
            CompoundTag slot = new CompoundTag();
            slot.putString("Clip", meleeSlotClip(i));
            slot.putBoolean("On", meleeSlotOn[i]);
            list.add(slot);
        }
        tag.put(TAG_MELEE_ANIM_SLOTS, list);
    }

    private void readMeleeAnimSlots(CompoundTag tag) {
        clearMeleeAnimSlots();
        if (tag == null || !tag.contains(TAG_MELEE_ANIM_SLOTS, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(TAG_MELEE_ANIM_SLOTS, Tag.TAG_COMPOUND);
        int n = Math.min(MELEE_SLOT_COUNT, list.size());
        for (int i = 0; i < n; i++) {
            CompoundTag slot = list.getCompound(i);
            meleeSlotClips[i] = slot.getString("Clip");
            meleeSlotOn[i] = slot.getBoolean("On");
        }
    }

    private void overlayMeleeAnimSlots(CompoundTag tag) {
        if (tag != null && tag.contains(TAG_MELEE_ANIM_SLOTS, Tag.TAG_LIST)) {
            readMeleeAnimSlots(tag);
        }
    }

    private void clearMeleeAnimSlots() {
        java.util.Arrays.fill(meleeSlotClips, "");
        java.util.Arrays.fill(meleeSlotOn, false);
    }

    private CompoundTag writeStateClips() {
        CompoundTag clips = new CompoundTag();
        stateClips.forEach((slot, clip) -> {
            if (slot != null && clip != null && !slot.isBlank() && !clip.isBlank()) {
                clips.putString(slot.toUpperCase(Locale.ROOT), clip);
            }
        });
        return clips;
    }

    /** Stable fingerprint for idempotent counterpart synchronization. */
    public int authorityFingerprint() {
        return java.util.Objects.hash(authoritative, pinNativeCombat,
                raceId, strength, strikePower, resistance,
                vitality, kiPower, energy, formGroup, formId, stackGroup, stackId,
                selectedFormGroup, selectedFormId, selectedStackGroup, selectedStackId,
                powerReleasePercent, formPower, kiChargePercent);
    }

    /** Defensive, player-independent DMZ-shaped snapshot for scripts and integrations. */
    public CompoundTag dmzStatSnapshot() {
        CompoundTag snapshot = new CompoundTag();
        snapshot.putInt(TAG_SCHEMA, CURRENT_SCHEMA);
        snapshot.putBoolean(TAG_AUTHORITATIVE, authoritative);
        snapshot.putString(TAG_RACE, raceId == null ? "human" : raceId);
        snapshot.putInt(TAG_STRENGTH, Math.max(0, strength));
        snapshot.putInt(TAG_STRIKE_POWER, Math.max(0, strikePower));
        snapshot.putInt(TAG_RESISTANCE, Math.max(0, resistance));
        snapshot.putInt(TAG_VITALITY, Math.max(0, vitality));
        snapshot.putInt(TAG_KI_POWER, Math.max(0, kiPower));
        snapshot.putInt(TAG_ENERGY, Math.max(0, energy));
        snapshot.putString(TAG_FORM_GROUP, safe(formGroup));
        snapshot.putString(TAG_FORM, safe(formId));
        snapshot.putString(TAG_STACK_GROUP, safe(stackGroup));
        snapshot.putString(TAG_STACK_FORM, safe(stackId));
        snapshot.putInt(TAG_POWER_RELEASE, Math.max(1, powerReleasePercent));
        snapshot.putDouble(TAG_FORM_POWER, formPower <= 0.0 ? 1.0 : formPower);
        return snapshot;
    }

    public boolean addTechnique(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (techniques.contains(key)) {
            return false;
        }
        techniques.add(key);
        return true;
    }

    public boolean removeTechnique(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return techniques.remove(id.toLowerCase(Locale.ROOT));
    }

    public static float clampAuraScale(float scale) {
        if (!Float.isFinite(scale)) {
            return 1.0f;
        }
        return Math.max(0.25f, Math.min(10.0f, scale));
    }

    /**
     * DMZ registers the fly skill with a max level and clamps its own cost curve to 1..10
     * ({@code FlySkillEvent.getFlyCostMultiplier}), so a level outside that range is meaningless.
     */
    public static int clampFlySkillLevel(int level) {
        return Math.max(1, Math.min(FLY_SKILL_MAX_LEVEL, level));
    }

    /** Max level this mod registers the DMZ {@code fly} skill at, matching DMZ's own clamp. */
    public static final int FLY_SKILL_MAX_LEVEL = 10;

    /** Schema that replaced the single fly toggle with the full {@link NpcSkillSet} map. */
    private static final int SKILL_MAP_SCHEMA = 9;

    /** Aggro multiplier bounds. Above 8 the derived range hits the bridge's own ceiling anyway. */
    public static float clampAggroMultiplier(float multiplier) {
        if (!Float.isFinite(multiplier)) {
            return 2.0f;
        }
        return Math.max(0.25f, Math.min(8.0f, multiplier));
    }

    /** Aim lead blend, 0 (no lead) to 1 (exact intercept). */
    public static float clampAimAccuracy(float accuracy) {
        if (!Float.isFinite(accuracy)) {
            return 0.85f;
        }
        return Math.max(0.0f, Math.min(1.0f, accuracy));
    }

    /** Shared special-move gap, 40..400 ticks. */
    public static int clampSpecialCooldown(int ticks) {
        return Math.max(40, Math.min(400, ticks));
    }

    public static float clampNpcMeleeDamage(float value) {
        return finiteClamp(value, 0.0f, 2048.0f, 0.0f);
    }

    public static float clampNpcMeleeRange(float value) {
        return finiteClamp(value, 0.0f, 64.0f, 0.0f);
    }

    public static float clampNpcMeleeSpeed(float value) {
        return finiteClamp(value, 0.1f, 10.0f, 1.0f);
    }

    public static float clampNpcRangedAccuracy(float value) {
        return finiteClamp(value, 0.0f, 1.0f, 0.85f);
    }

    public static float clampNpcRangedRange(float value) {
        return finiteClamp(value, 0.0f, 128.0f, 0.0f);
    }

    public static int clampNpcRangedDelay(int ticks) {
        return Math.max(1, Math.min(1200, ticks));
    }

    public static int clampNpcBurstCount(int count) {
        return Math.max(1, Math.min(16, count));
    }

    /**
     * Projectiles per shot. The floor of one is also the migration: a profile saved before this
     * existed reads a zero, and a zero here would mean an NPC that fires nothing at all.
     */
    public static int clampNpcShotCount(int count) {
        return Math.max(1, Math.min(16, count));
    }

    public static float clampNpcProjectileStrength(float value) {
        return finiteClamp(value, 0.0f, 2048.0f, 0.0f);
    }

    public static float clampNpcProjectileKnockback(float value) {
        return finiteClamp(value, 0.0f, 16.0f, 0.0f);
    }

    public static float clampNpcProjectileSize(float value) {
        return finiteClamp(value, 0.0f, 8.0f, 0.0f);
    }

    public static float clampNpcProjectileSpeed(float value) {
        return finiteClamp(value, 0.0f, 8.0f, 0.0f);
    }

    public static float clampNpcProjectileExplosion(float value) {
        return finiteClamp(value, 0.0f, 4.0f, 0.0f);
    }

    public static int clampNpcResistance(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public static int clampNpcEffectDuration(int ticks) {
        return Math.max(1, Math.min(72000, ticks));
    }

    public static int clampNpcEffectAmplifier(int amplifier) {
        return Math.max(0, Math.min(10, amplifier));
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }

    private static String safeResourceId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation
                .tryParse(value.trim().toLowerCase(Locale.ROOT));
        return id == null ? "" : id.toString();
    }

    public static java.util.List<String> projectileGravityModes() {
        return java.util.List.of("none", "normal", "constant", "accelerate");
    }

    public static String canonicalProjectileGravity(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return projectileGravityModes().contains(normalized) ? normalized : "none";
    }

    /**
     * Trail particles offered for a projectile.
     *
     * <p>Vanilla particle types rather than My NPCs' own numbered trail list: theirs indexes
     * textures that ship with their mod, and naming one here would be a control that points at
     * something we do not have.
     */
    public static java.util.List<String> projectileTrailModes() {
        return java.util.List.of("none", "smoke", "flame", "crit", "portal", "end_rod", "soul");
    }

    public static String canonicalProjectileTrail(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return projectileTrailModes().contains(normalized) ? normalized : "none";
    }

    public static java.util.List<String> npcAimModes() {
        return java.util.List.of("no", "distant", "hidden");
    }

    public static String canonicalNpcAimMode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return npcAimModes().contains(normalized) ? normalized : "distant";
    }

    public static int clampBrainChance(int chance) {
        return Math.max(0, Math.min(100, chance));
    }

    public static float clampBrainModifier(float modifier) {
        if (!Float.isFinite(modifier)) {
            return 1.0f;
        }
        return Math.max(0.05f, Math.min(10.0f, modifier));
    }

    /** DMZ only ships models and textures for these three ki-weapon identifiers. */
    public static final List<String> KI_WEAPON_TYPES = List.of("blade", "clawlance", "scythe");

    public static String canonicalKiWeaponType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return KI_WEAPON_TYPES.contains(normalized) ? normalized : "blade";
    }

    public static boolean isKiWeaponType(String type) {
        if (type == null) return false;
        return KI_WEAPON_TYPES.contains(type.trim().toLowerCase(Locale.ROOT));
    }

    public float chargeFactor() {
        int percent = kiChargePercent <= 0 ? 100 : kiChargePercent;
        return Math.max(0.25f, Math.min(10.0f, percent / 100.0f));
    }

    /** Stand-in for DMZ's real player "power release" stance stat. See the field javadoc. */
    /**
     * The DragonMineZ character class this NPC counts as, never blank.
     *
     * <p>One definition because two things read it and they must agree: health scaling goes through
     * {@code NpcVitalitySync.vitalityScaling}, and ki damage goes through the real {@code StatsData}
     * that {@code NpcDmzStats} builds. When those disagreed, an NPC's HP used its configured class
     * while its ki damage silently used DragonMineZ's own "warrior" default.
     *
     * <p>"warrior" is the fallback because it is what both {@link NpcDmzAppearance} and DMZ's own
     * {@code Character} already default to, so an NPC that was never given a class keeps the
     * numbers it has today.
     */
    public String characterClass() {
        if (appearance != null && appearance.characterClass != null
                && !appearance.characterClass.isBlank()) {
            return appearance.characterClass;
        }
        return "warrior";
    }

    public float releaseMultiplier() {
        int percent = powerReleasePercent <= 0 ? 100 : powerReleasePercent;
        return percent / 100.0f;
    }

    /**
     * Mirrors DMZ's real {@code StatsData.getMeleeDamage()} at default (no form/bonus)
     * scaling: {@code 1.0 + strength * releaseMultiplier}. {@code strikePower} deliberately
     * plays no part here -- DMZ's own formula never reads it for vanilla melee.
     */
    public float meleeDamage() {
        if (!Float.isNaN(effectiveMelee)) return effectiveMelee;
        return (float) NpcStatMath.meleeDamage(strength,
                NpcFormLookup.multiplier(this, "STR"), NpcStatScaling.of(this, "STR"),
                releaseMultiplier());
    }

    public float strikeDamage() {
        if (!Float.isNaN(effectiveStrike)) return effectiveStrike;
        return (float) NpcStatMath.strikeDamage(strikePower, strength,
                NpcFormLookup.multiplier(this, "SKP"), NpcFormLookup.multiplier(this, "STR"),
                NpcStatScaling.of(this, "SKP"), NpcStatScaling.of(this, "STR"),
                releaseMultiplier());
    }

    /**
     * Mirrors DMZ's real {@code StatsData.getKiDamage()} at default (no form/bonus) scaling:
     * {@code kiPower * releaseMultiplier}. Callers still layer the technique's own
     * {@code getDamageMultiplier()} and this NPC-only {@code chargeFactor()} on top.
     */
    public float kiDamage() {
        if (!Float.isNaN(effectiveKi)) return effectiveKi;
        return (float) NpcStatMath.kiDamage(kiPower,
                NpcFormLookup.multiplier(this, "PWR"), NpcStatScaling.of(this, "PWR"),
                releaseMultiplier());
    }

    private static final java.util.Map<String, Integer> NAMED_COLORS = java.util.Map.ofEntries(
            java.util.Map.entry("white", 0xFFFFFF),
            java.util.Map.entry("black", 0x1A1A1A),
            java.util.Map.entry("gold", 0xFFD700),
            java.util.Map.entry("ssj", 0xF5D76E),
            java.util.Map.entry("yellow", 0xFFFF00),
            java.util.Map.entry("red", 0xFF0000),
            java.util.Map.entry("blue", 0x3399FF),
            java.util.Map.entry("green", 0x33CC33),
            java.util.Map.entry("pink", 0xFF69B4),
            java.util.Map.entry("purple", 0xAA00FF),
            java.util.Map.entry("orange", 0xFF8800),
            java.util.Map.entry("brown", 0x6B3A2A),
            java.util.Map.entry("gray", 0xA0A0A0),
            java.util.Map.entry("grey", 0xA0A0A0),
            java.util.Map.entry("cyan", 0x00FFFF)
    );

    /**
     * Accepts {@code FF00AA}, {@code #FF00AA}, {@code 0xFF00AA}, or a name ({@code white},
     * {@code gold}, {@code ssj}, …). Returns empty if invalid.
     */
    public static java.util.OptionalInt parseHexColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.OptionalInt.empty();
        }
        String s = raw.trim();
        Integer named = NAMED_COLORS.get(s.toLowerCase(Locale.ROOT));
        if (named != null) {
            return java.util.OptionalInt.of(named);
        }
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        try {
            long value = s.startsWith("0x") || s.startsWith("0X")
                    ? Long.decode(s)
                    : Long.parseLong(s, 16);
            return java.util.OptionalInt.of((int) (value & 0xFFFFFF));
        } catch (NumberFormatException e) {
            return java.util.OptionalInt.empty();
        }
    }

    public static void writeHairCode(CompoundTag tag, String code) {
        String value = code == null ? "" : code;
        if (value.length() <= HAIR_CODE_CHUNK) {
            tag.putString(TAG_HAIR_CODE, value);
            tag.remove(TAG_HAIR_CODE_CHUNKS);
            return;
        }
        tag.putString(TAG_HAIR_CODE, "");
        ListTag chunks = new ListTag();
        for (int i = 0; i < value.length(); i += HAIR_CODE_CHUNK) {
            chunks.add(StringTag.valueOf(value.substring(i, Math.min(value.length(), i + HAIR_CODE_CHUNK))));
        }
        tag.put(TAG_HAIR_CODE_CHUNKS, chunks);
    }

    public static String readHairCode(CompoundTag tag) {
        if (tag.contains(TAG_HAIR_CODE_CHUNKS, Tag.TAG_LIST)) {
            ListTag chunks = tag.getList(TAG_HAIR_CODE_CHUNKS, Tag.TAG_STRING);
            if (!chunks.isEmpty()) {
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < chunks.size(); i++) {
                    out.append(chunks.getString(i));
                }
                return out.toString();
            }
        }
        return tag.getString(TAG_HAIR_CODE);
    }

    /** Stores {@code #RRGGBB}, or empty when unset/invalid. */
    public static String canonicalizeHairColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        java.util.OptionalInt parsed = parseHexColor(raw);
        return parsed.isEmpty() ? "" : formatHex(parsed.getAsInt());
    }

    public static String formatHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    public void setAuraColor(String value) {
        String canonical = canonicalizeOptionalColor(value);
        auraColorHex = canonical;
        auraColor = canonical.isEmpty() ? 0 : parseHexColor(canonical).orElse(0);
    }

    public NpcAuraStyle auraStyle(boolean stack, String group, String form, boolean create) {
        java.util.Map<String, NpcAuraStyle> map = stack ? stackAuraStyles : formAuraStyles;
        String key = auraKey(group, form);
        if (key.isEmpty()) return null;
        return create ? map.computeIfAbsent(key, ignored -> new NpcAuraStyle()) : map.get(key);
    }

    /** Compact subset synchronized to clients for the wand and renderer proxy. */
    public CompoundTag visualOptionsTag() {
        CompoundTag tag = new CompoundTag();
        // The native renderer reads these on tracking clients. Persistent entity NBT is not a
        // live network channel, so a saved model or size otherwise appears unchanged until relog.
        tag.putString(TAG_MODEL_KIND, normalizeModelKind(modelKind));
        tag.putString(TAG_MODEL_ID, safe(modelId));
        tag.putString(TAG_MODEL_ANIMATION, safe(modelAnimation));
        tag.putString(TAG_MODEL_TEXTURE, safe(modelTexture));
        tag.putString(TAG_NIGHT_TEXTURE, safe(nightTexture));
        tag.putString(TAG_SKIN_PLAYER, safe(skinPlayer));
        tag.putInt(TAG_MODEL_TINT, modelTint & 0xFFFFFF);
        tag.putInt(TAG_BASE_SIZE, baseSize);
        tag.putString(TAG_DISPLAY_CAPE, safe(displayCape));
        tag.putString(TAG_DISPLAY_OVERLAY, safe(displayOverlay));
        tag.putBoolean(TAG_DISPLAY_OVERLAY_GLOW, displayOverlayGlow);
        tag.putBoolean(TAG_DISPLAY_LAYERS, displayOuterLayers);
        tag.putString(TAG_AURA_COLOR_HEX, canonicalizeOptionalColor(auraColorHex));
        tag.putString(TAG_SELECTED_FORM_GROUP, safe(selectedFormGroup));
        tag.putString(TAG_SELECTED_FORM, safe(selectedFormId));
        tag.putString(TAG_STACK_GROUP, safe(stackGroup));
        tag.putString(TAG_STACK_FORM, safe(stackId));
        tag.putString(TAG_SELECTED_STACK_GROUP, safe(selectedStackGroup));
        tag.putString(TAG_SELECTED_STACK_FORM, safe(selectedStackId));
        tag.putBoolean(TAG_AURA_ON, auraOn);
        tag.putBoolean(TAG_HALO, haloOn);
        tag.putBoolean(TAG_ROCKS, auraRocks);
        tag.putBoolean(TAG_SPARKING, auraSparking);
        tag.putBoolean(TAG_LIGHTNING, auraLightning);
        tag.putBoolean(TAG_GROUND_RING, auraGroundRing);
        tag.putBoolean(TAG_FLY_ON, flySkillOn);
        tag.putInt(TAG_FLY_LEVEL, flySkillLevel);
        tag.put(TAG_SKILLS, skills.save());
        tag.putFloat(TAG_AGGRO_MULTIPLIER, aggroMultiplier);
        tag.putFloat(TAG_AIM_ACCURACY, aimAccuracy);
        writeBrainFlags(tag);
        tag.putBoolean(TAG_KI_WEAPON_ON, kiWeaponOn);
        tag.putString(TAG_KI_WEAPON_TYPE, canonicalKiWeaponType(kiWeaponType));
        // Live visual state every nearby client needs, so it rides the compact options tag the
        // appearance packet already carries rather than widening the packet itself.
        tag.putInt(TAG_HAIR_STYLE, Math.max(0, hairStyleId));
        tag.put(TAG_BASE_AURA_STYLE, baseAuraStyle.save());
        tag.put(TAG_FORM_AURA_STYLES, saveAuraStyles(formAuraStyles));
        tag.put(TAG_STACK_AURA_STYLES, saveAuraStyles(stackAuraStyles));
        tag.put(TAG_STACK_MASTERIES, stackMasteries.save());
        tag.putString(TAG_MELEE_ANIMATION, meleeAnimation == null ? "" : meleeAnimation.trim());
        writeMeleeAnimSlots(tag);
        // The mark rides the visual-options tag because it is drawn on the client and nothing
        // else would carry it there - the profile itself is server-side. Same reason the hair
        // style and aura styles are here, and the same reason the bubble palettes follow: the
        // speech and dialogue bubbles are drawn client-side too.
        tag.putString(TAG_MARK_ICON, canonicalMarkIcon(markIcon));
        tag.putInt(TAG_MARK_COLOR, markColor & 0xFFFFFF);
        tag.putString(TAG_BUBBLE_PALETTE, canonicalPalette(bubblePalette));
        tag.putFloat(TAG_BUBBLE_HEIGHT, bubbleHeight);
        tag.putInt(TAG_BUBBLE_DURATION, bubbleDurationTicks);
        tag.putInt(TAG_BUBBLE_MAX_LINES, bubbleMaxLines);
        tag.putBoolean(TAG_AMBIENT_LINES, ambientLinesEnabled);
        if (hasOwnLines()) {
            CompoundTag lineTag = new CompoundTag();
            for (var entry : lines.entrySet()) {
                ListTag list = new ListTag();
                for (String line : entry.getValue()) {
                    list.add(StringTag.valueOf(line));
                }
                lineTag.put(entry.getKey().name(), list);
            }
            tag.put(TAG_NPC_LINES, lineTag);
        }
        tag.putString(TAG_OPTION_PALETTE, canonicalPalette(optionPalette));
        tag.putString(TAG_BUBBLE_SHAPE, canonicalBubbleShape(bubbleShape));
        return tag;
    }

    public void applyVisualOptions(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;
        if (tag.contains(TAG_MODEL_KIND)) modelKind = normalizeModelKind(tag.getString(TAG_MODEL_KIND));
        if (tag.contains(TAG_MODEL_ID)) modelId = tag.getString(TAG_MODEL_ID);
        if (tag.contains(TAG_MODEL_ANIMATION)) modelAnimation = tag.getString(TAG_MODEL_ANIMATION);
        if (tag.contains(TAG_MODEL_TEXTURE)) modelTexture = tag.getString(TAG_MODEL_TEXTURE);
        if (tag.contains(TAG_NIGHT_TEXTURE)) nightTexture = tag.getString(TAG_NIGHT_TEXTURE);
        if (tag.contains(TAG_SKIN_PLAYER)) skinPlayer = tag.getString(TAG_SKIN_PLAYER);
        if (tag.contains(TAG_MODEL_TINT)) modelTint = tag.getInt(TAG_MODEL_TINT) & 0xFFFFFF;
        if (tag.contains(TAG_BASE_SIZE)) baseSize = Math.max(0, Math.min(30, tag.getInt(TAG_BASE_SIZE)));
        if (tag.contains(TAG_DISPLAY_CAPE)) displayCape = tag.getString(TAG_DISPLAY_CAPE);
        if (tag.contains(TAG_DISPLAY_OVERLAY)) displayOverlay = tag.getString(TAG_DISPLAY_OVERLAY);
        if (tag.contains(TAG_DISPLAY_OVERLAY_GLOW)) displayOverlayGlow = tag.getBoolean(TAG_DISPLAY_OVERLAY_GLOW);
        if (tag.contains(TAG_DISPLAY_LAYERS)) displayOuterLayers = tag.getBoolean(TAG_DISPLAY_LAYERS);
        setAuraColor(tag.getString(TAG_AURA_COLOR_HEX));
        selectedFormGroup = tag.getString(TAG_SELECTED_FORM_GROUP);
        selectedFormId = tag.getString(TAG_SELECTED_FORM);
        stackGroup = tag.getString(TAG_STACK_GROUP);
        stackId = tag.getString(TAG_STACK_FORM);
        selectedStackGroup = tag.getString(TAG_SELECTED_STACK_GROUP);
        selectedStackId = tag.getString(TAG_SELECTED_STACK_FORM);
        auraOn = tag.getBoolean(TAG_AURA_ON);
        haloOn = tag.getBoolean(TAG_HALO);
        auraRocks = !tag.contains(TAG_ROCKS) || tag.getBoolean(TAG_ROCKS);
        auraSparking = !tag.contains(TAG_SPARKING) || tag.getBoolean(TAG_SPARKING);
        auraLightning = !tag.contains(TAG_LIGHTNING) || tag.getBoolean(TAG_LIGHTNING);
        auraGroundRing = !tag.contains(TAG_GROUND_RING) || tag.getBoolean(TAG_GROUND_RING);
        flySkillOn = tag.getBoolean(TAG_FLY_ON);
        flySkillLevel = clampFlySkillLevel(tag.contains(TAG_FLY_LEVEL) ? tag.getInt(TAG_FLY_LEVEL) : 1);
        aggroMultiplier = clampAggroMultiplier(
                tag.contains(TAG_AGGRO_MULTIPLIER) ? tag.getFloat(TAG_AGGRO_MULTIPLIER) : 2.0f);
        aimAccuracy = clampAimAccuracy(
                tag.contains(TAG_AIM_ACCURACY) ? tag.getFloat(TAG_AIM_ACCURACY) : 0.85f);
        overlayBrainFlags(tag);
        if (tag.contains(TAG_SKILLS, Tag.TAG_LIST)) {
            skills.load(tag.getList(TAG_SKILLS, Tag.TAG_COMPOUND));
        }
        kiWeaponOn = tag.getBoolean(TAG_KI_WEAPON_ON);
        kiWeaponType = canonicalKiWeaponType(tag.getString(TAG_KI_WEAPON_TYPE));
        hairStyleId = Math.max(0, tag.getInt(TAG_HAIR_STYLE));
        if (tag.contains(TAG_BASE_AURA_STYLE, Tag.TAG_COMPOUND)) baseAuraStyle = NpcAuraStyle.load(tag.getCompound(TAG_BASE_AURA_STYLE));
        formAuraStyles.clear();
        stackAuraStyles.clear();
        loadAuraStyles(tag.getList(TAG_FORM_AURA_STYLES, Tag.TAG_COMPOUND), formAuraStyles);
        loadAuraStyles(tag.getList(TAG_STACK_AURA_STYLES, Tag.TAG_COMPOUND), stackAuraStyles);
        if (tag.contains(TAG_STACK_MASTERIES, Tag.TAG_COMPOUND)) stackMasteries.load(tag.getCompound(TAG_STACK_MASTERIES));
        if (tag.contains(TAG_MELEE_ANIMATION)) {
            meleeAnimation = tag.getString(TAG_MELEE_ANIMATION);
        }
        if (tag.contains(TAG_MARK_ICON)) {
            markIcon = canonicalMarkIcon(tag.getString(TAG_MARK_ICON));
        }
        if (tag.contains(TAG_MARK_COLOR)) {
            markColor = tag.getInt(TAG_MARK_COLOR) & 0xFFFFFF;
        }
        if (tag.contains(TAG_BUBBLE_PALETTE)) {
            bubblePalette = canonicalPalette(tag.getString(TAG_BUBBLE_PALETTE));
        }
        if (tag.contains(TAG_BUBBLE_SHAPE)) {
            bubbleShape = canonicalBubbleShape(tag.getString(TAG_BUBBLE_SHAPE));
        }
        if (tag.contains(TAG_OPTION_PALETTE)) {
            optionPalette = canonicalPalette(tag.getString(TAG_OPTION_PALETTE));
        }
        overlayMeleeAnimSlots(tag);
    }

    public static String auraKey(String group, String form) {
        if (group == null || group.isBlank() || form == null || form.isBlank()) return "";
        return group.trim().toLowerCase(Locale.ROOT) + "\u0000" + form.trim().toLowerCase(Locale.ROOT);
    }

    private static void loadAuraStyles(ListTag list, java.util.Map<String, NpcAuraStyle> out) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String key = auraKey(entry.getString("Group"), entry.getString("Form"));
            if (!key.isEmpty()) out.put(key, NpcAuraStyle.load(entry.getCompound("Style")));
        }
    }

    private static ListTag saveAuraStyles(java.util.Map<String, NpcAuraStyle> styles) {
        ListTag list = new ListTag();
        styles.forEach((key, style) -> {
            int split = key.indexOf('\u0000');
            if (split <= 0 || split >= key.length() - 1 || style == null) return;
            CompoundTag entry = new CompoundTag();
            entry.putString("Group", key.substring(0, split));
            entry.putString("Form", key.substring(split + 1));
            entry.put("Style", style.save());
            list.add(entry);
        });
        return list;
    }

    private static String canonicalizeOptionalColor(String raw) {
        if (raw == null || raw.isBlank()) return "";
        java.util.OptionalInt parsed = parseHexColor(raw);
        return parsed.isPresent() ? formatHex(parsed.getAsInt()) : "";
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static boolean flagOrDefault(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key) ? tag.getBoolean(key) : fallback;
    }

    // ---------------------------------------------------------------- Advanced tab

    /**
     * Writes the MyNPCs Advanced set: custom sounds, the night texture, linked NPCs, the editing
     * lock and the floating mark.
     *
     * <p>Kept as one method with {@link #readAdvancedTab} so the two cannot fall out of step. A
     * field written here and not read back is a setting that silently resets on reload, which is
     * the shape of the "stuff is not saving" bug this editor already had once.
     */
    private void writeAdvancedTab(CompoundTag tag) {
        tag.putString(TAG_SOUND_LIVING, safe(soundLiving));
        tag.putString(TAG_SOUND_ANGRY, safe(soundAngry));
        tag.putString(TAG_SOUND_HURT, safe(soundHurt));
        tag.putString(TAG_SOUND_DEATH, safe(soundDeath));
        tag.putString(TAG_SOUND_STEP, safe(soundStep));
        tag.putBoolean(TAG_SOUND_HAS_PITCH, soundHasPitch);
        tag.putString(TAG_NIGHT_TEXTURE, safe(nightTexture));
        tag.putBoolean(TAG_EDITING_LOCKED, editingLocked);
        tag.putString(TAG_MARK_ICON, canonicalMarkIcon(markIcon));
        tag.putInt(TAG_MARK_COLOR, markColor & 0xFFFFFF);
        tag.putString(TAG_BUBBLE_PALETTE, canonicalPalette(bubblePalette));
        tag.putFloat(TAG_BUBBLE_HEIGHT, bubbleHeight);
        tag.putInt(TAG_BUBBLE_DURATION, bubbleDurationTicks);
        tag.putInt(TAG_BUBBLE_MAX_LINES, bubbleMaxLines);
        tag.putBoolean(TAG_AMBIENT_LINES, ambientLinesEnabled);
        if (hasOwnLines()) {
            CompoundTag lineTag = new CompoundTag();
            for (var entry : lines.entrySet()) {
                ListTag list = new ListTag();
                for (String line : entry.getValue()) {
                    list.add(StringTag.valueOf(line));
                }
                lineTag.put(entry.getKey().name(), list);
            }
            tag.put(TAG_NPC_LINES, lineTag);
        }
        trades.saveTo(tag);
        tag.putBoolean("TradeIgnoreDamage", tradeIgnoreDamage);
        tag.putBoolean("TradeIgnoreNbt", tradeIgnoreNbt);
        gear.saveTo(tag);
        drops.saveTo(tag);
        dialogSlots.saveTo(tag);
        if (!transportNetwork.isEmpty()) {
            tag.putString(TAG_TRANSPORT_NETWORK, transportNetwork);
        }
        if (!bankId.isEmpty()) {
            tag.putString(TAG_BANK, bankId);
        }
        if (!sceneId.isEmpty()) {
            tag.putString(TAG_SCENE, sceneId);
            // Written only alongside a scene: a trigger on an NPC with no scene is a setting that
            // cannot do anything, and every untouched NPC would grow the key for nothing.
            tag.putString(TAG_SCENE_TRIGGER, sceneTrigger == null
                    ? net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTrigger.MANUAL.name()
                    : sceneTrigger.name());
        }
        if (!scripts.isEmpty()) {
            scriptId = scripts.firstScriptId();
            scripts.write(tag);
        }
        if (!scriptId.isEmpty()) {
            tag.putString(TAG_SCRIPT, scriptId);
        }
        if (path != null && !path.isEmpty()) {
            tag.put(TAG_PATH, path.save());
        }
        tag.putBoolean(TAG_STAY_HOME, stayHome);
        tag.putBoolean(TAG_SOCIAL, socialGestures);
        tag.putBoolean(TAG_GUARD_ANIMALS, guardAnimals);
        tag.putBoolean(TAG_GUARD_MONSTERS, guardMonsters);
        tag.putBoolean(TAG_GUARD_CREEPERS, guardCreepers);
        if (!bardSound.isEmpty()) {
            tag.putString(TAG_BARD_SOUND, bardSound);
        }
        tag.putBoolean(TAG_BARD_JUKEBOX, bardJukebox);
        tag.putBoolean(TAG_BARD_LOOPS, bardLoops);
        tag.putInt(TAG_BARD_ON, bardOnDistance);
        tag.putBoolean(TAG_BARD_HAS_OFF, bardHasOffDistance);
        tag.putInt(TAG_BARD_OFF, bardOffDistance);
        tag.putInt(TAG_HEALER_RANGE, Math.max(0, Math.min(64, healerRange)));
        tag.putInt(TAG_HEALER_TYPE, Math.max(0, Math.min(2, healerType)));
        tag.putInt(TAG_HEALER_SPEED, Math.max(10, Math.min(1200, healerSpeed)));
        ListTag healerList = new ListTag();
        healerEffects.forEach((id, amplifier) -> {
            if (healerList.size() >= MAX_HEALER_EFFECTS || id == null
                    || net.minecraft.resources.ResourceLocation.tryParse(id) == null) return;
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", id);
            entry.putInt("Amplifier", Math.max(0, Math.min(9, amplifier == null ? 0 : amplifier)));
            healerList.add(entry);
        });
        tag.put(TAG_HEALER_EFFECTS, healerList);
        if (followerName != null && !followerName.isBlank()) {
            tag.putString(TAG_FOLLOWER_NAME,
                    followerName.substring(0, Math.min(64, followerName.length())));
        }
        ListTag giverItems = new ListTag();
        for (int slot = 0; slot < ITEM_GIVER_SLOTS; slot++) {
            var item = itemGiverItems[slot];
            if (item == null || item.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            entry.put("Item", item.saved());
            giverItems.add(entry);
        }
        if ("item_giver".equals(job) || !giverItems.isEmpty()
                || itemGiverMethod != 0 || itemGiverCooldownType != 0
                || itemGiverCooldown != 10
                || !itemGiverLines.equals(java.util.List.of("Have these items {player}"))
                || !net.bullettrain.xenopixelsmod.features.progression.QuestAvailability.NONE
                .equals(itemGiverAvailability)) {
            tag.putInt(TAG_ITEM_GIVER_METHOD, Math.max(0, Math.min(4, itemGiverMethod)));
            tag.putInt(TAG_ITEM_GIVER_COOLDOWN_TYPE,
                    Math.max(0, Math.min(2, itemGiverCooldownType)));
            tag.putInt(TAG_ITEM_GIVER_COOLDOWN,
                    Math.max(0, Math.min(86400, itemGiverCooldown)));
            tag.put(TAG_ITEM_GIVER_ITEMS, giverItems);
            ListTag giverLines = new ListTag();
            for (String line : itemGiverLines) {
                if (giverLines.size() >= 16) break;
                if (line != null && !line.isBlank()) giverLines.add(net.minecraft.nbt.StringTag
                        .valueOf(line.substring(0, Math.min(256, line.length()))));
            }
            tag.put(TAG_ITEM_GIVER_LINES, giverLines);
            tag.putString(TAG_ITEM_GIVER_AVAILABILITY,
                    (itemGiverAvailability == null
                            ? net.bullettrain.xenopixelsmod.features.progression.QuestAvailability.NONE
                            : itemGiverAvailability).toJson().toString());
        }
        if (!job.isEmpty()) {
            tag.putString(TAG_JOB, job);
        }
        if (!job.isEmpty() || !jobEnabled) {
            tag.putBoolean(TAG_JOB_ENABLED, jobEnabled);
        }
        tag.putString(TAG_OPTION_PALETTE, canonicalPalette(optionPalette));
        tag.putString(TAG_BUBBLE_SHAPE, canonicalBubbleShape(bubbleShape));

        net.minecraft.nbt.ListTag linked = new net.minecraft.nbt.ListTag();
        for (String id : linkedNpcs) {
            if (id != null && !id.isBlank()) {
                linked.add(net.minecraft.nbt.StringTag.valueOf(id.trim()));
            }
        }
        tag.put(TAG_LINKED_NPCS, linked);
    }

    /** Reads what {@link #writeAdvancedTab} wrote, leaving defaults for keys an old save lacks. */
    private void readAdvancedTab(CompoundTag tag) {
        soundLiving = tag.getString(TAG_SOUND_LIVING);
        soundAngry = tag.getString(TAG_SOUND_ANGRY);
        soundHurt = tag.getString(TAG_SOUND_HURT);
        soundDeath = tag.getString(TAG_SOUND_DEATH);
        soundStep = tag.getString(TAG_SOUND_STEP);
        // Absent means an older profile, and the MyNPCs default for Has Pitch is on.
        soundHasPitch = !tag.contains(TAG_SOUND_HAS_PITCH) || tag.getBoolean(TAG_SOUND_HAS_PITCH);
        nightTexture = tag.getString(TAG_NIGHT_TEXTURE);
        editingLocked = tag.getBoolean(TAG_EDITING_LOCKED);
        markIcon = canonicalMarkIcon(tag.getString(TAG_MARK_ICON));
        markColor = tag.contains(TAG_MARK_COLOR) ? tag.getInt(TAG_MARK_COLOR) & 0xFFFFFF : 0xFFFFFF;
        bubblePalette = canonicalPalette(tag.getString(TAG_BUBBLE_PALETTE));
        optionPalette = canonicalPalette(tag.getString(TAG_OPTION_PALETTE));
        bubbleShape = canonicalBubbleShape(tag.getString(TAG_BUBBLE_SHAPE));

        // A tag written before these fields existed keeps the old hardcoded behaviour: the
        // renderer's 0.7 block height, a 60-tick life, and whatever the atlas allows for lines.
        bubbleHeight = readBubbleHeight(tag);
        bubbleDurationTicks = tag.contains(TAG_BUBBLE_DURATION)
                ? Math.max(1, Math.min(MAX_BUBBLE_DURATION_TICKS, tag.getInt(TAG_BUBBLE_DURATION)))
                : DEFAULT_BUBBLE_DURATION_TICKS;
        bubbleMaxLines = Math.max(0, Math.min(MAX_BUBBLE_LINES, tag.getInt(TAG_BUBBLE_MAX_LINES)));
        // Absent means a profile written before the toggle existed, and every NPC spoke then.
        ambientLinesEnabled = !tag.contains(TAG_AMBIENT_LINES) || tag.getBoolean(TAG_AMBIENT_LINES);
        trades.loadFrom(tag);
        tradeIgnoreDamage = !tag.contains("TradeIgnoreDamage") || tag.getBoolean("TradeIgnoreDamage");
        tradeIgnoreNbt = !tag.contains("TradeIgnoreNbt") || tag.getBoolean("TradeIgnoreNbt");
        gear.loadFrom(tag);
        drops.loadFrom(tag);
        dialogSlots.loadFrom(tag);
        transportNetwork = tag.getString(TAG_TRANSPORT_NETWORK);
        bankId = tag.getString(TAG_BANK);
        sceneId = tag.getString(TAG_SCENE);
        // Absent reads as blank, which is an NPC with no script - the state of every NPC written
        // before this key existed.
        scriptId = tag.getString(TAG_SCRIPT);
        scripts = net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer.read(tag);
        // Absent reads as Manual, which is what an NPC saved before triggers existed did.
        sceneTrigger = net.bullettrain.xenopixelsmod.npc.scene.XenoNpcSceneTrigger
                .byName(tag.getString(TAG_SCENE_TRIGGER));
        path = tag.contains(TAG_PATH)
                ? net.bullettrain.xenopixelsmod.npc.path.NpcPath.load(tag.getCompound(TAG_PATH))
                : new net.bullettrain.xenopixelsmod.npc.path.NpcPath();
        // Absent means an NPC that predates these, and both stay off for it - see the fields.
        stayHome = tag.getBoolean(TAG_STAY_HOME);
        socialGestures = tag.getBoolean(TAG_SOCIAL);
        // Absent means a profile written before the guard job existed. Monsters and creepers on,
        // animals off - a guard that attacked livestock on sight would be a surprise.
        guardAnimals = tag.getBoolean(TAG_GUARD_ANIMALS);
        guardMonsters = !tag.contains(TAG_GUARD_MONSTERS) || tag.getBoolean(TAG_GUARD_MONSTERS);
        guardCreepers = !tag.contains(TAG_GUARD_CREEPERS) || tag.getBoolean(TAG_GUARD_CREEPERS);
        bardSound = tag.getString(TAG_BARD_SOUND);
        // Absent means a profile written before the bard existed, and its defaults are the ones
        // the reference ships - not false and zero, which would read as a bard that never plays.
        bardJukebox = !tag.contains(TAG_BARD_JUKEBOX) || tag.getBoolean(TAG_BARD_JUKEBOX);
        bardLoops = tag.getBoolean(TAG_BARD_LOOPS);
        bardOnDistance = tag.contains(TAG_BARD_ON)
                ? Math.max(0, Math.min(MAX_BARD_DISTANCE, tag.getInt(TAG_BARD_ON))) : 2;
        bardHasOffDistance = !tag.contains(TAG_BARD_HAS_OFF) || tag.getBoolean(TAG_BARD_HAS_OFF);
        bardOffDistance = tag.contains(TAG_BARD_OFF)
                ? Math.max(0, Math.min(MAX_BARD_DISTANCE, tag.getInt(TAG_BARD_OFF))) : 64;
        healerRange = tag.contains(TAG_HEALER_RANGE)
                ? Math.max(0, Math.min(64, tag.getInt(TAG_HEALER_RANGE))) : 8;
        healerType = tag.contains(TAG_HEALER_TYPE)
                ? Math.max(0, Math.min(2, tag.getInt(TAG_HEALER_TYPE))) : 2;
        healerSpeed = tag.contains(TAG_HEALER_SPEED)
                ? Math.max(10, Math.min(1200, tag.getInt(TAG_HEALER_SPEED))) : 20;
        healerEffects.clear();
        ListTag healerList = tag.getList(TAG_HEALER_EFFECTS, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < healerList.size() && healerEffects.size() < MAX_HEALER_EFFECTS; i++) {
            CompoundTag entry = healerList.getCompound(i);
            String id = entry.getString("Id");
            if (id.length() <= 128 && net.minecraft.resources.ResourceLocation.tryParse(id) != null) {
                healerEffects.put(id, Math.max(0, Math.min(9, entry.getInt("Amplifier"))));
            }
        }
        followerName = tag.getString(TAG_FOLLOWER_NAME);
        if (followerName.length() > 64) followerName = followerName.substring(0, 64);
        itemGiverMethod = Math.max(0, Math.min(4, tag.getInt(TAG_ITEM_GIVER_METHOD)));
        itemGiverCooldownType = Math.max(0, Math.min(2, tag.getInt(TAG_ITEM_GIVER_COOLDOWN_TYPE)));
        itemGiverCooldown = tag.contains(TAG_ITEM_GIVER_COOLDOWN)
                ? Math.max(0, Math.min(86400, tag.getInt(TAG_ITEM_GIVER_COOLDOWN))) : 10;
        java.util.Arrays.fill(itemGiverItems,
                net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack.EMPTY);
        ListTag giverItems = tag.getList(TAG_ITEM_GIVER_ITEMS, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < giverItems.size() && index < ITEM_GIVER_SLOTS; index++) {
            CompoundTag entry = giverItems.getCompound(index);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < ITEM_GIVER_SLOTS && entry.contains("Item", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                itemGiverItems[slot] = net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack
                        .load(entry.getCompound("Item"));
            }
        }
        itemGiverLines.clear();
        if (tag.contains(TAG_ITEM_GIVER_LINES, net.minecraft.nbt.Tag.TAG_LIST)) {
            ListTag giverLines = tag.getList(TAG_ITEM_GIVER_LINES, net.minecraft.nbt.Tag.TAG_STRING);
            for (int index = 0; index < giverLines.size() && itemGiverLines.size() < 16; index++) {
                String line = giverLines.getString(index);
                if (!line.isBlank()) itemGiverLines.add(line.substring(0, Math.min(256, line.length())));
            }
        } else {
            itemGiverLines.add("Have these items {player}");
        }
        itemGiverAvailability = net.bullettrain.xenopixelsmod.features.progression
                .QuestAvailability.NONE;
        if (tag.contains(TAG_ITEM_GIVER_AVAILABILITY, net.minecraft.nbt.Tag.TAG_STRING)) {
            try {
                String json = tag.getString(TAG_ITEM_GIVER_AVAILABILITY);
                if (json.length() <= 8192) itemGiverAvailability = net.bullettrain.xenopixelsmod
                        .features.progression.QuestAvailability.fromJson(
                                com.google.gson.JsonParser.parseString(json));
            } catch (RuntimeException ignored) {
                // A damaged legacy tag cannot make the NPC unloadable.
            }
        }
        // Normalised on read rather than trusted: an unknown id becomes NONE, so a job removed
        // from the enum does not leave an NPC pointing at something nothing can run.
        job = net.bullettrain.xenopixelsmod.npc.XenoNpcJob.byId(tag.getString(TAG_JOB)).id();
        jobEnabled = !tag.contains(TAG_JOB_ENABLED) || tag.getBoolean(TAG_JOB_ENABLED);
        // An NPC saved before networks existed still carries its destinations inline. They are
        // held aside rather than read as current state: the migration lifts them into the store on
        // the next save, and dropping them here would delete an operator's work.
        legacyTransports = null;
        if (tag.contains(LEGACY_TAG_TRANSPORTS)) {
            var legacy = new net.bullettrain.xenopixelsmod.npc.transport.NpcTransportList();
            legacy.loadFrom(tag);
            if (!legacy.isEmpty()) {
                legacyTransports = legacy;
            }
        }
        lines.clear();
        if (tag.contains(TAG_NPC_LINES)) {
            CompoundTag lineTag = tag.getCompound(TAG_NPC_LINES);
            for (net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category category
                    : net.bullettrain.xenopixelsmod.npc.lines.XenoNpcLines.Category.values()) {
                if (!lineTag.contains(category.name())) {
                    continue;
                }
                ListTag stored = lineTag.getList(category.name(), Tag.TAG_STRING);
                java.util.List<String> read = new java.util.ArrayList<>();
                for (int i = 0; i < stored.size(); i++) {
                    read.add(stored.getString(i));
                }
                setLines(category, read);
            }
        }

        linkedNpcs.clear();
        net.minecraft.nbt.ListTag linked = tag.getList(TAG_LINKED_NPCS, Tag.TAG_STRING);
        for (int i = 0; i < linked.size(); i++) {
            String id = linked.getString(i);
            if (id != null && !id.isBlank()) {
                linkedNpcs.add(id.trim());
            }
        }
    }

    /**
     * Folds an arbitrary string onto one of {@link #MARK_ICONS}, or "" for none.
     *
     * <p>A mark that is not in the list would ask the renderer for a sprite that does not exist, so
     * an unknown value is read as "no mark" rather than carried around waiting to fail.
     */
    public static String canonicalMarkIcon(String icon) {
        if (icon == null) {
            return "";
        }
        String key = icon.trim().toLowerCase(Locale.ROOT);
        return MARK_ICONS.contains(key) ? key : "";
    }

    /** The id of the sound for {@code slot}, or "" when that slot is on the default. */
    public String soundFor(String slot) {
        if (slot == null) {
            return "";
        }
        return switch (slot.trim().toLowerCase(Locale.ROOT)) {
            case "living", "idle", "ambient" -> safe(soundLiving);
            case "angry", "anger" -> safe(soundAngry);
            case "hurt" -> safe(soundHurt);
            case "death", "die" -> safe(soundDeath);
            case "step" -> safe(soundStep);
            default -> "";
        };
    }

    private void writeBrainFlags(CompoundTag tag) {
        tag.putBoolean(TAG_BRAIN, combatBrain);
        tag.putInt(TAG_BRAIN_VERSION,
                (brainVersion == null ? NpcCombatBrainVersion.V1 : brainVersion).legacyCompatible().stored());
        tag.putBoolean(TAG_BRAIN_STRIKE, brainStrike);
        tag.putBoolean(TAG_BRAIN_CHARGE, brainCharge);
        tag.putBoolean(TAG_BRAIN_FLYING_FIST, brainFlyingFist);
        tag.putBoolean(TAG_BRAIN_HEAVY_HIT, brainHeavyHit);
        tag.putBoolean(TAG_BRAIN_BONE_CRUSHER, brainBoneCrusher);
        tag.putBoolean(TAG_BRAIN_KIAI, brainKiai);
        tag.putBoolean(TAG_BRAIN_RANDOM_KI, brainRandomKi);
        tag.putBoolean(TAG_BRAIN_KI_BLAST, brainKiBlast);
        tag.putBoolean(TAG_BRAIN_KI_WAVE, brainKiWave);
        tag.putBoolean(TAG_BRAIN_KI_DISK, brainKiDisk);
        tag.putBoolean(TAG_BRAIN_KI_NAMED, brainKiNamed);
        tag.putBoolean(TAG_BRAIN_FLY, brainFly);
        tag.putBoolean(TAG_BRAIN_CAN_USE_FLIGHT, canUseFlight);
        tag.putBoolean(TAG_BRAIN_LAND, brainLand);
        tag.putBoolean(TAG_BRAIN_ASCEND, brainAscend);
        tag.putBoolean(TAG_BRAIN_DISENGAGE, brainDisengage);
        tag.putBoolean(TAG_BRAIN_VANISH, brainVanish);
        tag.putBoolean(TAG_BRAIN_ZANZOKEN, brainZanzoken);
        tag.putBoolean(TAG_BRAIN_CHASE, brainChase);
        tag.putBoolean(TAG_BRAIN_DEFLECT_BLAST, brainDeflectBlast);
        tag.putBoolean(TAG_BRAIN_DEFLECT_WAVE, brainDeflectWave);
        tag.putInt(TAG_BRAIN_SPECIAL_CD, clampSpecialCooldown(brainSpecialCooldown));
        CompoundTag chances = new CompoundTag();
        CompoundTag modifiers = new CompoundTag();
        for (String key : BRAIN_ACTION_KEYS) {
            chances.putInt(key, brainChance(key));
            modifiers.putFloat(key, brainModifier(key));
        }
        tag.putBoolean(TAG_BRAIN_XENO_SPECIALS, xenoSpecials);
        tag.putFloat(TAG_HEALTH_REGEN, healthRegen);
        tag.putFloat(TAG_COMBAT_REGEN, combatRegen);
        tag.putBoolean(TAG_AI_CAN_SWIM, aiCanSwim);
        tag.putBoolean(TAG_AI_AVOIDS_WATER, aiAvoidsWater);
        tag.putInt(TAG_AI_DOOR_INTERACT, aiDoorInteract == null ? 0 : aiDoorInteract.ordinal());
        tag.putBoolean(TAG_AI_LEAP, aiLeapAtTarget);
        tag.putBoolean(TAG_AI_RETURN_HOME, aiReturnToStart);
        tag.putInt(TAG_AI_ON_FOUND_ENEMY,
                aiOnFoundEnemy == null ? 0 : aiOnFoundEnemy.ordinal());
        tag.putInt(TAG_AI_SHELTER_FROM,
                aiShelterFrom == null ? 2 : aiShelterFrom.ordinal());
        tag.putBoolean(TAG_AI_MUST_SEE_TARGET, aiMustSeeTarget);
        tag.putBoolean(TAG_AI_ATTACK_INVISIBLE, aiAttackInvisible);
        tag.putBoolean(TAG_AI_MOUNT_CONTROL, aiMountControl);
        tag.put(TAG_BRAIN_CHANCES, chances);
        tag.put(TAG_BRAIN_MODIFIERS, modifiers);
    }

    /**
     * Moves an NPC stored as V7 before V8 existed onto V8.
     *
     * <p>V7 used to mean "fully DragonMineZ toggles-wise" while still firing the BT3 combos, Hakai
     * and the teleport moves. It now means the ported tree and nothing of ours. Redefining it in
     * place would have taken those moves away from every NPC already set to it, next time the world
     * loaded, without anybody asking for that on those NPCs - so they land on V8 instead, which is
     * byte-for-byte the behaviour they have today. Anything can be put back on the new V7 with one
     * cycle of the editor's Brain row.
     *
     * <p>Recognised by the absence of {@code BrainXenoSpecials}, which is written on every save
     * from now on. That is a better marker than the schema number: the schema is bumped for many
     * unrelated reasons, and a profile rewritten by some other migration would then be mistaken for
     * an old one. A tag that carries the key has been saved since this shipped and means exactly
     * what it says.
     */
    private static NpcCombatBrainVersion migrateLegacyV7(NpcCombatBrainVersion stored,
                                                         CompoundTag tag) {
        if (stored == NpcCombatBrainVersion.V7 && !tag.contains(TAG_BRAIN_XENO_SPECIALS)) {
            return NpcCombatBrainVersion.V8;
        }
        return stored;
    }

    private void readBrainFlags(CompoundTag tag) {
        combatBrain = tag.getBoolean(TAG_BRAIN);
        brainVersion = NpcBrainPolicy.resolve(tag.contains(TAG_BRAIN_VERSION)
                ? NpcCombatBrainVersion.fromStored(tag.getInt(TAG_BRAIN_VERSION)).legacyCompatible()
                : NpcCombatBrainVersion.V1);
        // Default on: every brain used these before the switch existed, and getBoolean would answer
        // false for a key an older save never wrote - which would silently disarm NPCs that have
        // always had them.
        xenoSpecials = flagOrDefault(tag, TAG_BRAIN_XENO_SPECIALS, true);
        // Both of these default ON, so flagOrDefault rather than getBoolean: an NPC saved before
        // the switches existed has no key, and getBoolean would answer false - which would sink
        // every NPC standing in water and unanchor every one with a home.
        // Zero is both the default and what an absent key reads as, so nothing needs a fallback
        // here - an NPC saved before these existed simply does not heal, exactly as before.
        healthRegen = clampRegen(tag.getFloat(TAG_HEALTH_REGEN));
        combatRegen = clampRegen(tag.getFloat(TAG_COMBAT_REGEN));
        aiCanSwim = flagOrDefault(tag, TAG_AI_CAN_SWIM, true);
        aiReturnToStart = flagOrDefault(tag, TAG_AI_RETURN_HOME, true);
        aiAvoidsWater = tag.getBoolean(TAG_AI_AVOIDS_WATER);
        aiDoorInteract = net.bullettrain.xenopixelsmod.npc.NpcDoorInteract
                .byIndex(tag.getInt(TAG_AI_DOOR_INTERACT));
        aiLeapAtTarget = tag.getBoolean(TAG_AI_LEAP);
        aiOnFoundEnemy = net.bullettrain.xenopixelsmod.npc.NpcOnFoundEnemy
                .byIndex(tag.getInt(TAG_AI_ON_FOUND_ENEMY));
        aiShelterFrom = net.bullettrain.xenopixelsmod.npc.NpcShelterFrom
                .byIndex(tag.contains(TAG_AI_SHELTER_FROM)
                        ? tag.getInt(TAG_AI_SHELTER_FROM) : 2);
        aiMustSeeTarget = flagOrDefault(tag, TAG_AI_MUST_SEE_TARGET, true);
        aiAttackInvisible = tag.getBoolean(TAG_AI_ATTACK_INVISIBLE);
        aiMountControl = tag.getBoolean(TAG_AI_MOUNT_CONTROL);
        brainVersion = migrateLegacyV7(brainVersion, tag);
        brainStrike = flagOrDefault(tag, TAG_BRAIN_STRIKE, true);
        brainCharge = flagOrDefault(tag, TAG_BRAIN_CHARGE, true);
        brainFlyingFist = tag.getBoolean(TAG_BRAIN_FLYING_FIST);
        brainHeavyHit = tag.getBoolean(TAG_BRAIN_HEAVY_HIT);
        brainBoneCrusher = tag.getBoolean(TAG_BRAIN_BONE_CRUSHER);
        brainKiai = tag.getBoolean(TAG_BRAIN_KIAI);
        brainRandomKi = tag.getBoolean(TAG_BRAIN_RANDOM_KI);
        brainKiBlast = flagOrDefault(tag, TAG_BRAIN_KI_BLAST, true);
        brainKiWave = flagOrDefault(tag, TAG_BRAIN_KI_WAVE, true);
        brainKiDisk = flagOrDefault(tag, TAG_BRAIN_KI_DISK, true);
        brainKiNamed = flagOrDefault(tag, TAG_BRAIN_KI_NAMED, true);
        brainFly = flagOrDefault(tag, TAG_BRAIN_FLY, true);
        canUseFlight = flagOrDefault(tag, TAG_BRAIN_CAN_USE_FLIGHT, true);
        brainLand = flagOrDefault(tag, TAG_BRAIN_LAND, true);
        brainAscend = flagOrDefault(tag, TAG_BRAIN_ASCEND, true);
        brainDisengage = flagOrDefault(tag, TAG_BRAIN_DISENGAGE, true);
        brainVanish = flagOrDefault(tag, TAG_BRAIN_VANISH, true);
        brainZanzoken = flagOrDefault(tag, TAG_BRAIN_ZANZOKEN, true);
        brainChase = flagOrDefault(tag, TAG_BRAIN_CHASE, true);
        brainDeflectBlast = flagOrDefault(tag, TAG_BRAIN_DEFLECT_BLAST,
                brainVersion != NpcCombatBrainVersion.V9);
        brainDeflectWave = tag.getBoolean(TAG_BRAIN_DEFLECT_WAVE);
        brainSpecialCooldown = tag.contains(TAG_BRAIN_SPECIAL_CD)
                ? clampSpecialCooldown(tag.getInt(TAG_BRAIN_SPECIAL_CD)) : 80;
        brainChances.clear();
        brainModifiers.clear();
        loadBrainTuning(tag);
    }

    /**
     * Compact appearance snapshots omit brain keys. Defaulting those to true would turn Charge
     * (and other default-on flags) back on after the Brain screen saved them off.
     */
    private void overlayBrainFlags(CompoundTag tag) {
        if (tag.contains(TAG_BRAIN)) combatBrain = tag.getBoolean(TAG_BRAIN);
        if (tag.contains(TAG_BRAIN_VERSION)) {
            brainVersion = migrateLegacyV7(
                    NpcCombatBrainVersion.fromStored(tag.getInt(TAG_BRAIN_VERSION))
                            .legacyCompatible(), tag);
        }
        if (tag.contains(TAG_BRAIN_XENO_SPECIALS)) {
            xenoSpecials = tag.getBoolean(TAG_BRAIN_XENO_SPECIALS);
        }
        if (tag.contains(TAG_BRAIN_STRIKE)) brainStrike = tag.getBoolean(TAG_BRAIN_STRIKE);
        if (tag.contains(TAG_BRAIN_CHARGE)) brainCharge = tag.getBoolean(TAG_BRAIN_CHARGE);
        if (tag.contains(TAG_BRAIN_FLYING_FIST)) brainFlyingFist = tag.getBoolean(TAG_BRAIN_FLYING_FIST);
        if (tag.contains(TAG_BRAIN_HEAVY_HIT)) brainHeavyHit = tag.getBoolean(TAG_BRAIN_HEAVY_HIT);
        if (tag.contains(TAG_BRAIN_BONE_CRUSHER)) brainBoneCrusher = tag.getBoolean(TAG_BRAIN_BONE_CRUSHER);
        if (tag.contains(TAG_BRAIN_KIAI)) brainKiai = tag.getBoolean(TAG_BRAIN_KIAI);
        if (tag.contains(TAG_BRAIN_RANDOM_KI)) brainRandomKi = tag.getBoolean(TAG_BRAIN_RANDOM_KI);
        if (tag.contains(TAG_BRAIN_KI_BLAST)) brainKiBlast = tag.getBoolean(TAG_BRAIN_KI_BLAST);
        if (tag.contains(TAG_BRAIN_KI_WAVE)) brainKiWave = tag.getBoolean(TAG_BRAIN_KI_WAVE);
        if (tag.contains(TAG_BRAIN_KI_DISK)) brainKiDisk = tag.getBoolean(TAG_BRAIN_KI_DISK);
        if (tag.contains(TAG_BRAIN_KI_NAMED)) brainKiNamed = tag.getBoolean(TAG_BRAIN_KI_NAMED);
        if (tag.contains(TAG_BRAIN_FLY)) brainFly = tag.getBoolean(TAG_BRAIN_FLY);
        if (tag.contains(TAG_BRAIN_CAN_USE_FLIGHT)) {
            canUseFlight = tag.getBoolean(TAG_BRAIN_CAN_USE_FLIGHT);
        }
        if (tag.contains(TAG_BRAIN_LAND)) brainLand = tag.getBoolean(TAG_BRAIN_LAND);
        if (tag.contains(TAG_BRAIN_ASCEND)) brainAscend = tag.getBoolean(TAG_BRAIN_ASCEND);
        if (tag.contains(TAG_BRAIN_DISENGAGE)) brainDisengage = tag.getBoolean(TAG_BRAIN_DISENGAGE);
        if (tag.contains(TAG_BRAIN_VANISH)) brainVanish = tag.getBoolean(TAG_BRAIN_VANISH);
        if (tag.contains(TAG_BRAIN_ZANZOKEN)) brainZanzoken = tag.getBoolean(TAG_BRAIN_ZANZOKEN);
        if (tag.contains(TAG_BRAIN_CHASE)) brainChase = tag.getBoolean(TAG_BRAIN_CHASE);
        if (tag.contains(TAG_BRAIN_DEFLECT_BLAST)) brainDeflectBlast = tag.getBoolean(TAG_BRAIN_DEFLECT_BLAST);
        if (tag.contains(TAG_BRAIN_DEFLECT_WAVE)) brainDeflectWave = tag.getBoolean(TAG_BRAIN_DEFLECT_WAVE);
        if (tag.contains(TAG_BRAIN_SPECIAL_CD)) {
            brainSpecialCooldown = clampSpecialCooldown(tag.getInt(TAG_BRAIN_SPECIAL_CD));
        }
        loadBrainTuning(tag);
    }

    private void loadBrainTuning(CompoundTag tag) {
        if (tag.contains(TAG_BRAIN_CHANCES, Tag.TAG_COMPOUND)) {
            CompoundTag chances = tag.getCompound(TAG_BRAIN_CHANCES);
            for (String raw : chances.getAllKeys()) {
                try {
                    brainChances.put(canonicalBrainAction(raw), clampBrainChance(chances.getInt(raw)));
                } catch (IllegalArgumentException ignored) {
                    // Unknown keys from a newer client stay ignored.
                }
            }
        }
        if (tag.contains(TAG_BRAIN_MODIFIERS, Tag.TAG_COMPOUND)) {
            CompoundTag modifiers = tag.getCompound(TAG_BRAIN_MODIFIERS);
            for (String raw : modifiers.getAllKeys()) {
                try {
                    brainModifiers.put(canonicalBrainAction(raw),
                            clampBrainModifier(modifiers.getFloat(raw)));
                } catch (IllegalArgumentException ignored) {
                    // Unknown keys from a newer client stay ignored.
                }
            }
        }
    }

    public java.util.Map<String, Object> brainFlagMap() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("combatBrain", combatBrain);
        out.put("version", (brainVersion == null ? NpcCombatBrainVersion.V1 : brainVersion).label());
        out.put("strike", brainStrike);
        out.put("charge", brainCharge);
        out.put("flyingFist", brainFlyingFist);
        out.put("heavyHit", brainHeavyHit);
        out.put("boneCrusher", brainBoneCrusher);
        out.put("kiai", brainKiai);
        out.put("randomKi", brainRandomKi);
        out.put("kiBlast", brainKiBlast);
        out.put("kiWave", brainKiWave);
        out.put("kiDisk", brainKiDisk);
        out.put("kiNamed", brainKiNamed);
        out.put("fly", brainFly);
        out.put("land", brainLand);
        out.put("ascend", brainAscend);
        out.put("disengage", brainDisengage);
        out.put("vanish", brainVanish);
        out.put("zanzoken", brainZanzoken);
        out.put("chase", brainChase);
        out.put("deflectBlast", brainDeflectBlast);
        out.put("deflectWave", brainDeflectWave);
        out.put("specialCooldown", clampSpecialCooldown(brainSpecialCooldown));
        return out;
    }

    public boolean brainFlag(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "combatbrain", "brain" -> combatBrain;
            case "strike" -> brainStrike;
            case "charge" -> brainCharge;
            case "flyingfist" -> brainFlyingFist;
            case "heavyhit" -> brainHeavyHit;
            case "bonecrusher" -> brainBoneCrusher;
            case "kiai" -> brainKiai;
            case "randomki" -> brainRandomKi;
            case "kiblast" -> brainKiBlast;
            case "kiwave" -> brainKiWave;
            case "kidisk" -> brainKiDisk;
            case "kinamed" -> brainKiNamed;
            case "fly" -> brainFly;
            case "land" -> brainLand;
            case "ascend" -> brainAscend;
            case "disengage" -> brainDisengage;
            case "vanish" -> brainVanish;
            case "zanzoken" -> brainZanzoken;
            case "chase" -> brainChase;
            case "deflectblast" -> brainDeflectBlast;
            case "deflectwave" -> brainDeflectWave;
            default -> throw new IllegalArgumentException("Unknown brain flag: " + name);
        };
    }

    public void setBrainFlag(String name, boolean on) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "combatbrain", "brain" -> combatBrain = on;
            case "strike" -> brainStrike = on;
            case "charge" -> brainCharge = on;
            case "flyingfist" -> brainFlyingFist = on;
            case "heavyhit" -> brainHeavyHit = on;
            case "bonecrusher" -> brainBoneCrusher = on;
            case "kiai" -> brainKiai = on;
            case "randomki" -> brainRandomKi = on;
            case "kiblast" -> brainKiBlast = on;
            case "kiwave" -> brainKiWave = on;
            case "kidisk" -> brainKiDisk = on;
            case "kinamed" -> brainKiNamed = on;
            case "fly" -> brainFly = on;
            case "land" -> brainLand = on;
            case "ascend" -> brainAscend = on;
            case "disengage" -> brainDisengage = on;
            case "vanish" -> brainVanish = on;
            case "zanzoken" -> brainZanzoken = on;
            case "chase" -> brainChase = on;
            case "deflectblast" -> brainDeflectBlast = on;
            case "deflectwave" -> brainDeflectWave = on;
            default -> throw new IllegalArgumentException("Unknown brain flag: " + name);
        }
    }

    public int brainChance(String name) {
        String key = canonicalBrainAction(name);
        Integer stored = brainChances.get(key);
        return stored != null ? clampBrainChance(stored) : defaultBrainChance(key);
    }

    public void setBrainChance(String name, int chance) {
        brainChances.put(canonicalBrainAction(name), clampBrainChance(chance));
    }

    public float brainModifier(String name) {
        String key = canonicalBrainAction(name);
        Float stored = brainModifiers.get(key);
        return stored != null ? clampBrainModifier(stored) : 1.0f;
    }

    public void setBrainModifier(String name, float modifier) {
        brainModifiers.put(canonicalBrainAction(name), clampBrainModifier(modifier));
    }

    /**
     * Whether the brain may perform {@code name} right now.
     *
     * <p>The single gate every brain action passes through, which is why the V7 bypass lives here
     * rather than at each call site: there are well over a dozen of those across two brains, and it
     * only takes one of them forgetting for a toggle to read as dead.
     *
     * <p>{@link NpcCombatBrainVersion#V7} is the "fully DragonMineZ" brain - no sub-toggles, every
     * choice made by the ported DMZ decision tree - so for it this answers true and lets that tree
     * decide. The editor hides the toggles in that mode, so nothing on screen claims otherwise.
     */
    public boolean allowBrainAction(String name, RandomSource random) {
        if (brainVersion != null && !brainVersion.honoursToggles()) {
            return true;
        }
        if (!brainFlag(name)) {
            return false;
        }
        int chance = brainChance(name);
        if (chance <= 0) {
            return false;
        }
        if (chance >= 100) {
            return true;
        }
        return random != null && random.nextInt(100) < chance;
    }

    /**
     * Whether this NPC may use a move this mod added on top of the DragonMineZ port.
     *
     * <p>The companion to {@link #allowBrainAction}, and separate from it on purpose: that one
     * gates the ported DMZ actions against the Brain tab's per-action switches, and those are a
     * different question from "is the Xeno layer switched on at all". Folding them together would
     * mean V7 - which bypasses {@code allowBrainAction} entirely so DMZ's tree can decide - also
     * bypassed this, which is the opposite of what V7 is for.
     *
     * <p>The single place every Xeno special asks, so a new one cannot forget: the BT3 combo
     * beats, Hakai, and the repositioning moves all route through here.
     */
    public boolean allowXenoSpecial() {
        if (brainVersion != null && !brainVersion.usesXenoSpecials()) {
            return false;
        }
        return xenoSpecials;
    }

    /** A regen rate off the wire, clamped rather than trusted. */
    public static float clampRegen(float value) {
        if (Float.isNaN(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(MAX_REGEN, value));
    }

    public static int defaultBrainChance(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "deflectblast" -> 35;
            case "deflectwave" -> 20;
            default -> 100;
        };
    }

    public static String canonicalBrainAction(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "strike" -> "strike";
            case "charge" -> "charge";
            case "flyingfist" -> "flyingFist";
            case "heavyhit" -> "heavyHit";
            case "bonecrusher" -> "boneCrusher";
            case "kiai" -> "kiai";
            case "randomki" -> "randomKi";
            case "kiblast" -> "kiBlast";
            case "kiwave" -> "kiWave";
            case "kidisk" -> "kiDisk";
            case "kinamed" -> "kiNamed";
            case "fly" -> "fly";
            case "land" -> "land";
            case "ascend" -> "ascend";
            case "disengage" -> "disengage";
            case "vanish" -> "vanish";
            case "zanzoken" -> "zanzoken";
            case "chase" -> "chase";
            case "deflectblast" -> "deflectBlast";
            case "deflectwave" -> "deflectWave";
            default -> throw new IllegalArgumentException("Unknown brain action: " + name);
        };
    }
}
