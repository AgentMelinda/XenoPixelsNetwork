package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.stats.extras.FormMasteries;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
    private static final String TAG_SCHEMA = "Schema";
    private static final int CURRENT_SCHEMA = 4;

    private static final String TAG_RACE = "Race";
    private static final String TAG_STRENGTH = "Strength";
    private static final String TAG_STRIKE_POWER = "StrikePower";
    private static final String TAG_RESISTANCE = "Resistance";
    private static final String TAG_VITALITY = "Vitality";
    private static final String TAG_KI_POWER = "KiPower";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_KI_COLOR = "KiColor";
    private static final String TAG_AURA_ON = "AuraOn";
    private static final String TAG_AURA_COLOR = "AuraColor";
    private static final String TAG_FORM_GROUP = "FormGroup";
    private static final String TAG_FORM = "Form";
    private static final String TAG_TECHNIQUES = "Techniques";
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
    private static final String TAG_DMZ_APPEARANCE = "DmzAppearance";
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
    private static final String TAG_BASE_AURA_STYLE = "BaseAuraStyle";
    private static final String TAG_FORM_AURA_STYLES = "FormAuraStyles";
    private static final String TAG_STACK_AURA_STYLES = "StackAuraStyles";
    /** Packet/NBT UTF stays under 32767 per string. Full-set DMZ codes are longer. */
    public static final int HAIR_CODE_CHUNK = 30000;

    public String raceId = "human";
    public int strength;
    public int strikePower;
    public int resistance;
    public int vitality;
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
    public String skinPlayer = "";
    public String skinUrl = "";
    public final FormMasteries masteries = new FormMasteries();
    public final FormMasteries stackMasteries = new FormMasteries();
    public boolean haloOn;
    public boolean auraRocks = true;
    public boolean auraSparking = true;
    public boolean auraLightning = true;
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
    public float auraScale = 1.0f;
    /** DMZ hair, addon-independent home for the same three values NpcHairBridge writes. */
    public boolean hairEnabled;
    public String hairCode = "";
    public String hairColor = "";
    /** Full player-independent DMZ customization state. */
    public NpcDmzAppearance appearance = new NpcDmzAppearance();

    public NpcCombatProfile() {}

    /** True only when XenoPixels has explicitly attached a combat profile to this entity. */
    public static boolean hasProfile(Entity entity) {
        return entity != null && entity.getPersistentData().contains(NBT_KEY, Tag.TAG_COMPOUND);
    }

    public static NpcCombatProfile read(Entity entity) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(NBT_KEY)) {
            return new NpcCombatProfile();
        }
        return fromTag(root.getCompound(NBT_KEY));
    }

    /** Decodes the same profile payload used by the wand save packet. */
    public static NpcCombatProfile fromTag(CompoundTag tag) {
        NpcCombatProfile profile = new NpcCombatProfile();
        if (tag == null || tag.isEmpty()) {
            return profile;
        }
        profile.raceId = tag.contains(TAG_RACE) ? tag.getString(TAG_RACE) : profile.raceId;
        profile.strength = tag.getInt(TAG_STRENGTH);
        profile.strikePower = tag.getInt(TAG_STRIKE_POWER);
        profile.resistance = tag.getInt(TAG_RESISTANCE);
        profile.vitality = tag.getInt(TAG_VITALITY);
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
        if (schema < CURRENT_SCHEMA && profile.auraColorHex.isEmpty() && profile.auraColor != 0) {
            // TEMPORARY: tracking a base-aura-color corruption bug. Remove once the live
            // trigger is found and fixed for real.
            XenoPixelsMod.LOGGER.info("[AURA-DEBUG] fromTag legacy backfill fired: schema={} auraColor={} -> auraColorHex",
                    schema, formatHex(profile.auraColor));
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
        profile.auraScale = tag.contains(TAG_AURA_SCALE) ? tag.getFloat(TAG_AURA_SCALE) : 1.0f;
        // Older builds baked their fixed 1.7 correction into every new profile. Native DMZ aura
        // now follows NPC size directly, so only that exact legacy default is normalized.
        if (schema < CURRENT_SCHEMA && Float.compare(profile.auraScale, 1.7f) == 0) {
            profile.auraScale = 1.0f;
        }
        profile.auraScale = clampAuraScale(profile.auraScale);
        profile.hairEnabled = tag.getBoolean(TAG_HAIR_ENABLED);
        profile.hairCode = readHairCode(tag);
        profile.hairColor = canonicalizeHairColor(tag.getString(TAG_HAIR_COLOR));
        if (tag.contains(TAG_DMZ_APPEARANCE, Tag.TAG_COMPOUND)) {
            profile.appearance = NpcDmzAppearance.fromTag(tag.getCompound(TAG_DMZ_APPEARANCE));
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
        return profile;
    }

    public CompoundTag toTag() {
        return writeTag();
    }

    public void write(Entity entity) {
        // TEMPORARY: tracking a base-aura-color corruption bug. Remove once the live trigger
        // is found and fixed for real.
        XenoPixelsMod.LOGGER.info("[AURA-DEBUG] write side={} entity={} auraColorHex='{}' auraColor={} formGroup='{}' formId='{}'",
                entity.level().isClientSide() ? "CLIENT" : "SERVER", entity.getUUID(),
                auraColorHex, formatHex(auraColor), formGroup, formId);
        entity.getPersistentData().put(NBT_KEY, writeTag());
        NpcVitalitySync.apply(entity, this);
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            NpcFormAttributeSync.apply(living, this);
        }
        NpcHairBridge.applyProfile(entity, this);
        NpcProfileLifecycle.track(entity);
        NpcProfileLifecycle.repairAi(entity);
        NpcAppearanceFx.sync(entity);
        NpcAuraFx.sync(entity);
    }

    private CompoundTag writeTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_SCHEMA, CURRENT_SCHEMA);
        tag.putString(TAG_RACE, raceId);
        tag.putInt(TAG_STRENGTH, strength);
        tag.putInt(TAG_STRIKE_POWER, strikePower);
        tag.putInt(TAG_RESISTANCE, resistance);
        tag.putInt(TAG_VITALITY, vitality);
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
        tag.put(TAG_DMZ_APPEARANCE, (appearance == null ? new NpcDmzAppearance() : appearance).toTag());
        tag.put(TAG_MASTERIES, masteries.save());
        tag.put(TAG_STACK_MASTERIES, stackMasteries.save());
        ListTag techs = new ListTag();
        for (String id : techniques) {
            if (id != null && !id.isBlank()) {
                techs.add(StringTag.valueOf(id.toLowerCase(Locale.ROOT)));
            }
        }
        tag.put(TAG_TECHNIQUES, techs);
        return tag;
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

    public float chargeFactor() {
        int percent = kiChargePercent <= 0 ? 100 : kiChargePercent;
        return Math.max(0.25f, Math.min(10.0f, percent / 100.0f));
    }

    /** Stand-in for DMZ's real player "power release" stance stat. See the field javadoc. */
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
        return (float) NpcStatMath.meleeDamage(strength,
                NpcFormLookup.multiplier(this, "STR"), releaseMultiplier());
    }

    public float strikeDamage() {
        return (float) NpcStatMath.strikeDamage(strikePower, strength,
                NpcFormLookup.multiplier(this, "SKP"),
                NpcFormLookup.multiplier(this, "STR"), releaseMultiplier());
    }

    /**
     * Mirrors DMZ's real {@code StatsData.getKiDamage()} at default (no form/bonus) scaling:
     * {@code kiPower * releaseMultiplier}. Callers still layer the technique's own
     * {@code getDamageMultiplier()} and this NPC-only {@code chargeFactor()} on top.
     */
    public float kiDamage() {
        return (float) NpcStatMath.kiDamage(kiPower,
                NpcFormLookup.multiplier(this, "PWR"), releaseMultiplier());
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
        tag.put(TAG_BASE_AURA_STYLE, baseAuraStyle.save());
        tag.put(TAG_FORM_AURA_STYLES, saveAuraStyles(formAuraStyles));
        tag.put(TAG_STACK_AURA_STYLES, saveAuraStyles(stackAuraStyles));
        tag.put(TAG_STACK_MASTERIES, stackMasteries.save());
        return tag;
    }

    public void applyVisualOptions(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;
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
        if (tag.contains(TAG_BASE_AURA_STYLE, Tag.TAG_COMPOUND)) baseAuraStyle = NpcAuraStyle.load(tag.getCompound(TAG_BASE_AURA_STYLE));
        formAuraStyles.clear();
        stackAuraStyles.clear();
        loadAuraStyles(tag.getList(TAG_FORM_AURA_STYLES, Tag.TAG_COMPOUND), formAuraStyles);
        loadAuraStyles(tag.getList(TAG_STACK_AURA_STYLES, Tag.TAG_COMPOUND), stackAuraStyles);
        if (tag.contains(TAG_STACK_MASTERIES, Tag.TAG_COMPOUND)) stackMasteries.load(tag.getCompound(TAG_STACK_MASTERIES));
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
}
