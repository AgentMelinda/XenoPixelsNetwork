package net.bullettrain.xenopixelsmod.compat.npc;

import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.controllers.ScriptContainer;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit, server-side XenoPixels bridge exposed to CustomNPCs scripts as {@code XenoPixels}. */
public final class NpcXenoScriptApi {
    public static final NpcXenoScriptApi INSTANCE = new NpcXenoScriptApi();
    private static final String VERSION = "5";

    private NpcXenoScriptApi() {}

    public static void install() {
        ScriptContainer.Data.put("XenoPixels", INSTANCE);
    }

    public String getVersion() { return VERSION; }

    public boolean hasProfile(ICustomNpc npc) { return entity(npc) != null && NpcCombatProfile.hasProfile(entity(npc)); }

    public Map<String, Object> getProfile(ICustomNpc npc) {
        LivingEntity entity = require(npc);
        NpcCombatProfile p = NpcCombatProfile.read(entity);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("race", p.raceId); out.put("strength", p.strength); out.put("strikePower", p.strikePower);
        out.put("resistance", p.resistance); out.put("vitality", p.vitality); out.put("kiPower", p.kiPower);
        out.put("energy", p.energy); out.put("kiChargePercent", p.kiChargePercent);
        out.put("formGroup", p.formGroup); out.put("form", p.formId);
        out.put("selectedFormGroup", p.selectedFormGroup); out.put("selectedForm", p.selectedFormId);
        out.put("stackGroup", p.stackGroup); out.put("stack", p.stackId);
        out.put("selectedStackGroup", p.selectedStackGroup); out.put("selectedStack", p.selectedStackId);
        out.put("auraOn", p.auraOn); out.put("auraColor", p.auraColorHex);
        out.put("auraScale", p.auraScale); out.put("halo", p.haloOn);
        out.put("auraRocks", p.auraRocks); out.put("auraSparking", p.auraSparking);
        out.put("auraLightning", p.auraLightning);
        out.put("saiyanTail", p.appearance.saiyanTail);
        out.put("tailColor", p.appearance.tailColor);
        NpcResources.Snapshot resources = NpcResources.get(entity, p);
        out.put("currentEnergy", resources.energy()); out.put("maxEnergy", resources.maxEnergy());
        out.put("currentStamina", resources.stamina()); out.put("maxStamina", resources.maxStamina());
        out.put("techniques", List.copyOf(p.techniques));
        out.put("hair", getHair(npc));
        return out;
    }

    public boolean setProfile(ICustomNpc npc, String race, int strength, int strikePower, int resistance,
                              int vitality, int kiPower, int energy) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        p.raceId = race == null || race.isBlank() ? "human" : race;
        p.strength = Math.max(0, strength); p.strikePower = Math.max(0, strikePower);
        p.resistance = Math.max(0, resistance); p.vitality = Math.max(0, vitality);
        p.kiPower = Math.max(0, kiPower); p.energy = Math.max(0, energy); p.write(entity); return true;
    }

    public boolean setKiCharge(ICustomNpc npc, int percent) { return mutate(npc, p -> p.kiChargePercent = Math.max(1, Math.min(1000, percent))); }
    /** DMZ's real "power release" stance stat has no NPC equivalent; 100 = full power. */
    public boolean setPowerRelease(ICustomNpc npc, int percent) { return mutate(npc, p -> p.powerReleasePercent = Math.max(1, Math.min(100, percent))); }
    public boolean setAura(ICustomNpc npc, boolean on) { return mutate(npc, p -> p.auraOn = on); }
    public boolean setAuraColor(ICustomNpc npc, String hex) { return color(npc, hex, true); }
    /**
     * Clearer-named alias for {@link #setAuraColor} -- this only ever affects the base/
     * untransformed aura layer; a transformed form's own DMZ aura color (or its per-form style
     * override) always wins while transformed. Same effect, kept as a separate method rather
     * than renaming {@code setAuraColor} so existing scripts keep working unchanged.
     */
    public boolean setBaseAuraColor(ICustomNpc npc, String hex) { return color(npc, hex, true); }
    public boolean setKiColor(ICustomNpc npc, String hex) { return color(npc, hex, false); }
    public boolean setAuraScale(ICustomNpc npc, float scale) { return mutate(npc, p -> p.auraScale = NpcCombatProfile.clampAuraScale(scale)); }
    public boolean setHalo(ICustomNpc npc, boolean on) { return mutate(npc, p -> p.haloOn = on); }
    public boolean setAuraRocks(ICustomNpc npc, boolean on) { return mutate(npc, p -> p.auraRocks = on); }
    public boolean setAuraSparking(ICustomNpc npc, boolean on) { return mutate(npc, p -> p.auraSparking = on); }
    public boolean setAuraLightning(ICustomNpc npc, boolean on) { return mutate(npc, p -> p.auraLightning = on); }
    public boolean setSaiyanTail(ICustomNpc npc, boolean on) { return mutate(npc, p -> p.appearance.saiyanTail = on); }
    /** Empty color restores DMZ's race/body/form tail-color inheritance. */
    public boolean setTailColor(ICustomNpc npc, String hex) {
        if (hex == null || hex.isBlank()) return clearTailColor(npc);
        var parsed = NpcCombatProfile.parseHexColor(hex);
        if (parsed.isEmpty()) return false;
        return mutate(npc, p -> p.appearance.tailColor =
                NpcCombatProfile.formatHex(parsed.getAsInt()));
    }
    public boolean clearTailColor(ICustomNpc npc) {
        return mutate(npc, p -> p.appearance.tailColor = "");
    }

    public List<String> listFormGroups(String race) { return NpcFormLookup.groups(race); }
    public List<String> listForms(String race, String group) { return NpcFormLookup.forms(race, group); }
    public List<String> listStackGroups() { return NpcFormLookup.stackGroups(); }
    public List<String> listStackForms(String group) { return NpcFormLookup.stackForms(group); }

    public boolean selectForm(ICustomNpc npc, String group, String form) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        if (NpcFormLookup.form(p.raceId, group, form) == null) return false;
        p.selectedFormGroup = safe(group); p.selectedFormId = safe(form);
        NpcFormLookup.grantMastery(p, group, form); p.write(entity); return true;
    }

    public boolean selectStack(ICustomNpc npc, String group, String form) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        var data = NpcFormLookup.stackForm(group, form); if (data == null) return false;
        p.selectedStackGroup = safe(group); p.selectedStackId = safe(form);
        double max = NpcFormLookup.maxMastery(data);
        if (p.stackMasteries.getMastery(group, form) <= 0.0) p.stackMasteries.setMastery(group, form, max, max);
        p.write(entity); return true;
    }

    public boolean setMastery(ICustomNpc npc, String group, String form, double percent) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        var data = NpcFormLookup.form(p.raceId, group, form); double max = NpcFormLookup.maxMastery(data);
        p.masteries.setMastery(group, form, max * Math.max(0, Math.min(100, percent)) / 100.0, max); p.write(entity); return true;
    }

    public boolean setStackMastery(ICustomNpc npc, String group, String form, double percent) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        var data = NpcFormLookup.stackForm(group, form); if (data == null) return false;
        double max = NpcFormLookup.maxMastery(data);
        p.stackMasteries.setMastery(group, form, max * Math.max(0, Math.min(100, percent)) / 100.0, max);
        p.write(entity); return true;
    }

    public boolean addTechnique(ICustomNpc npc, String id) { return mutate(npc, p -> p.addTechnique(id)); }
    public boolean removeTechnique(ICustomNpc npc, String id) { return mutate(npc, p -> p.removeTechnique(id)); }
    public List<String> listTechniques(ICustomNpc npc) { return List.copyOf(NpcCombatProfile.read(require(npc)).techniques); }

    public boolean ascend(ICustomNpc npc, String group, String form, int ticks) { return NpcTransformSystem.start(require(npc), group, form, ticks); }
    public boolean stack(ICustomNpc npc, String group, String form, int ticks) { return NpcTransformSystem.startStack(require(npc), group, form, ticks); }
    public boolean unstack(ICustomNpc npc) { return NpcTransformSystem.unstack(require(npc)); }
    public boolean descend(ICustomNpc npc, int ticks) { return NpcTransformSystem.descend(require(npc)); }
    public boolean descendOne(ICustomNpc npc, int ticks) { return NpcTransformSystem.descendOne(require(npc), ticks); }
    public boolean isTransforming(ICustomNpc npc) { return NpcTransformSystem.isHolding(require(npc).getUUID()); }

    public Map<String, Object> getAuraStyle(ICustomNpc npc, String scope, String group, String form) {
        NpcCombatProfile p = NpcCombatProfile.read(require(npc));
        NpcAuraStyle style = auraStyle(p, scope, group, form, false);
        return styleMap(style == null ? new NpcAuraStyle() : style);
    }

    public boolean setAuraStyle(ICustomNpc npc, String scope, String group, String form,
                                boolean enabled, String primaryColor, String primaryType, int primaryLayer,
                                boolean extraConfigured, boolean extraEnabled, String extraColor,
                                String extraType, int extraLayer, boolean lightningConfigured,
                                boolean lightningEnabled, String lightningColor) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        NpcAuraStyle style = auraStyle(p, scope, group, form, true); if (style == null) return false;
        style.enabled = enabled;
        style.primaryColor = optionalColor(primaryColor); style.primaryType = safe(primaryType);
        style.primaryLayer = NpcAuraStyle.clampLayer(primaryLayer);
        style.extraConfigured = extraConfigured; style.extraEnabled = extraEnabled;
        style.extraColor = optionalColor(extraColor); style.extraType = safe(extraType);
        style.extraLayer = NpcAuraStyle.clampLayer(extraLayer);
        style.lightningConfigured = lightningConfigured; style.lightningEnabled = lightningEnabled;
        style.lightningColor = optionalColor(lightningColor);
        p.write(entity); return true;
    }

    public boolean clearAuraStyle(ICustomNpc npc, String scope, String group, String form) {
        LivingEntity entity = require(npc); NpcCombatProfile p = NpcCombatProfile.read(entity);
        String normalized = safe(scope).toLowerCase(java.util.Locale.ROOT);
        if ("base".equals(normalized)) p.baseAuraStyle = new NpcAuraStyle();
        else {
            String key = NpcCombatProfile.auraKey(group, form); if (key.isBlank()) return false;
            ("stack".equals(normalized) ? p.stackAuraStyles : p.formAuraStyles).remove(key);
        }
        p.write(entity); return true;
    }

    public Map<String, Object> getResolvedAura(ICustomNpc npc) {
        NpcAuraResolver.Resolved resolved = NpcAuraResolver.resolve(NpcCombatProfile.read(require(npc)));
        Map<String, Object> out = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> layers = new java.util.ArrayList<>();
        for (NpcAuraResolver.Layer layer : resolved.layers()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", layer.type()); entry.put("layer", layer.index());
            entry.put("color", NpcCombatProfile.formatHex(layer.rgb())); layers.add(entry);
        }
        out.put("layers", layers); out.put("lightning", resolved.lightning());
        out.put("lightningColor", NpcCombatProfile.formatHex(resolved.lightningRgb()));
        out.put("rocks", resolved.rocks()); out.put("sparking", resolved.sparking());
        return out;
    }

    public boolean fireTechnique(ICustomNpc npc, String id, IEntity target, int durationTicks) {
        LivingEntity caster = require(npc);
        LivingEntity aim = target != null && target.getMCEntity() instanceof LivingEntity living ? living : null;
        return NpcKiAttackDispatcher.fire(id, caster, NpcCombatProfile.read(caster), durationTicks, aim);
    }
    /**
     * Same as the 4-arg {@code fireTechnique}, plus a one-shot colour override for just this
     * attack -- unlike {@link #setKiColor}, this never touches the profile's persistent
     * {@code kiColor}, so it does not recolour the NPC's later attacks.
     */
    public boolean fireTechnique(ICustomNpc npc, String id, IEntity target, int durationTicks, String hex) {
        LivingEntity caster = require(npc);
        LivingEntity aim = target != null && target.getMCEntity() instanceof LivingEntity living ? living : null;
        int colorOverride = NpcCombatProfile.parseHexColor(hex).orElse(0);
        return NpcKiAttackDispatcher.fire(id, caster, NpcCombatProfile.read(caster), durationTicks, aim, colorOverride);
    }
    public int getTechniqueCooldown(ICustomNpc npc, String id) { return NpcKiCooldowns.remaining(require(npc), id); }
    public boolean isTechniqueReady(ICustomNpc npc, String id) { return NpcKiCooldowns.ready(require(npc), id); }
    public void clearTechniqueCooldown(ICustomNpc npc, String id) { NpcKiCooldowns.clear(require(npc), id); }

    public double getCurrentEnergy(ICustomNpc npc) { LivingEntity e = require(npc); return NpcResources.get(e, NpcCombatProfile.read(e)).energy(); }
    public double getMaxEnergy(ICustomNpc npc) { LivingEntity e = require(npc); return NpcResources.get(e, NpcCombatProfile.read(e)).maxEnergy(); }
    public double getCurrentStamina(ICustomNpc npc) { LivingEntity e = require(npc); return NpcResources.get(e, NpcCombatProfile.read(e)).stamina(); }
    public double getMaxStamina(ICustomNpc npc) { LivingEntity e = require(npc); return NpcResources.get(e, NpcCombatProfile.read(e)).maxStamina(); }
    public boolean setCurrentEnergy(ICustomNpc npc, double value) { LivingEntity e = require(npc); NpcResources.setEnergy(e, NpcCombatProfile.read(e), value); return true; }
    public boolean setCurrentStamina(ICustomNpc npc, double value) { LivingEntity e = require(npc); NpcResources.setStamina(e, NpcCombatProfile.read(e), value); return true; }

    public Map<String, Object> getHair(ICustomNpc npc) {
        NpcCombatProfile profile = NpcCombatProfile.read(require(npc));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", profile.hairEnabled);
        out.put("code", profile.hairCode == null ? "" : profile.hairCode);
        out.put("color", profile.hairColor == null ? "" : profile.hairColor);
        return out;
    }
    public boolean setHairEnabled(ICustomNpc npc, boolean enabled) { return NpcHairBridge.setEnabled(require(npc), enabled) == NpcHairBridge.Result.OK; }
    public boolean setHairCode(ICustomNpc npc, String code) { return NpcHairBridge.setCode(require(npc), code) == NpcHairBridge.Result.OK; }
    public boolean setHairColor(ICustomNpc npc, String color) { return NpcHairBridge.setColor(require(npc), color) == NpcHairBridge.Result.OK; }

    private interface ProfileEdit { void apply(NpcCombatProfile profile); }
    private boolean mutate(ICustomNpc npc, ProfileEdit edit) { LivingEntity e = require(npc); NpcCombatProfile p = NpcCombatProfile.read(e); edit.apply(p); p.write(e); return true; }
    private boolean color(ICustomNpc npc, String value, boolean aura) { var parsed = NpcCombatProfile.parseHexColor(value); if (parsed.isEmpty()) return false; return mutate(npc, p -> { if (aura) p.setAuraColor(NpcCombatProfile.formatHex(parsed.getAsInt())); else p.kiColor = parsed.getAsInt(); }); }
    private static NpcAuraStyle auraStyle(NpcCombatProfile p, String scope, String group, String form, boolean create) {
        String normalized = safe(scope).toLowerCase(java.util.Locale.ROOT);
        if ("base".equals(normalized)) return p.baseAuraStyle;
        if (!"form".equals(normalized) && !"stack".equals(normalized)) return null;
        return p.auraStyle("stack".equals(normalized), group, form, create);
    }
    private static Map<String, Object> styleMap(NpcAuraStyle style) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", style.enabled); out.put("primaryColor", style.primaryColor);
        out.put("primaryType", style.primaryType); out.put("primaryLayer", style.primaryLayer);
        out.put("extraConfigured", style.extraConfigured); out.put("extraEnabled", style.extraEnabled);
        out.put("extraColor", style.extraColor); out.put("extraType", style.extraType);
        out.put("extraLayer", style.extraLayer); out.put("lightningConfigured", style.lightningConfigured);
        out.put("lightningEnabled", style.lightningEnabled); out.put("lightningColor", style.lightningColor);
        return out;
    }
    private static String optionalColor(String value) {
        if (value == null || value.isBlank()) return "";
        return NpcCombatProfile.parseHexColor(value).isPresent()
                ? NpcCombatProfile.formatHex(NpcCombatProfile.parseHexColor(value).getAsInt()) : "";
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static LivingEntity require(ICustomNpc npc) { LivingEntity e = entity(npc); if (e == null) throw new IllegalArgumentException("XenoPixels requires a live CustomNPC entity"); return e; }
    private static LivingEntity entity(ICustomNpc npc) { return npc == null ? null : npc.getMCEntity(); }
}
