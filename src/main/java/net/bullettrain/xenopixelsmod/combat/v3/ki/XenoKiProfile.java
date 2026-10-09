package net.bullettrain.xenopixelsmod.combat.v3.ki;

import com.dragonminez.common.stats.techniques.KiAttackData;
import com.google.gson.JsonObject;

/**
 * Xeno-owned presentation / tuning profile for a Strike Ki technique.
 *
 * <p>Applied onto a <em>copy</em> of a DragonMineZ {@link KiAttackData} template. Never mutates
 * stock {@code PredefinedTechniques} entries. Inspired by Overhaul-style knobs (colors, size,
 * speed, damage, armor pen, muzzle offsets) but schema and code are scratch-owned.
 *
 * @param techniqueId full id ({@code xenopixelsmod:bt3_…}) or blank for archetype-only rows
 * @param nativeId    DMZ template id ({@code kamehameha}, …); may be blank when inherited
 * @param centerMuzzle when true, lateral cast offset X is forced to 0 (BT3-centred beams)
 */
public record XenoKiProfile(
        String techniqueId,
        String nativeId,
        Integer colorInterior,
        Integer colorExterior,
        Integer colorOutline,
        Float size,
        Float speed,
        Float damageMultiplier,
        Integer armorPenetration,
        Float castOffsetX,
        Float castOffsetY,
        Float castOffsetZ,
        Integer renderType,
        boolean centerMuzzle,
        String fxTrail,
        String fxImpact,
        String fxCharge) {

    public static final int SCHEMA = 1;

    public XenoKiProfile {
        techniqueId = techniqueId == null ? "" : techniqueId;
        nativeId = nativeId == null ? "" : nativeId;
        fxTrail = blankToNull(fxTrail);
        fxImpact = blankToNull(fxImpact);
        fxCharge = blankToNull(fxCharge);
    }

    public static XenoKiProfile empty(String techniqueId, String nativeId) {
        return new XenoKiProfile(techniqueId, nativeId, null, null, null, null, null, null, null,
                null, null, null, null, false, null, null, null);
    }

    public static XenoKiProfile fromJson(String techniqueId, JsonObject json) {
        if (json == null) return empty(techniqueId, "");
        return new XenoKiProfile(
                techniqueId,
                str(json, "nativeId"),
                intOrNull(json, "colorInterior"),
                intOrNull(json, "colorExterior"),
                intOrNull(json, "colorOutline"),
                floatOrNull(json, "size"),
                floatOrNull(json, "speed"),
                floatOrNull(json, "damageMultiplier"),
                intOrNull(json, "armorPenetration"),
                floatOrNull(json, "castOffsetX"),
                floatOrNull(json, "castOffsetY"),
                floatOrNull(json, "castOffsetZ"),
                intOrNull(json, "renderType"),
                bool(json, "centerMuzzle", false),
                fx(json, "trail"),
                fx(json, "impact"),
                fx(json, "charge"));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (!nativeId.isBlank()) json.addProperty("nativeId", nativeId);
        putInt(json, "colorInterior", colorInterior);
        putInt(json, "colorExterior", colorExterior);
        putInt(json, "colorOutline", colorOutline);
        putFloat(json, "size", size);
        putFloat(json, "speed", speed);
        putFloat(json, "damageMultiplier", damageMultiplier);
        putInt(json, "armorPenetration", armorPenetration);
        putFloat(json, "castOffsetX", castOffsetX);
        putFloat(json, "castOffsetY", castOffsetY);
        putFloat(json, "castOffsetZ", castOffsetZ);
        putInt(json, "renderType", renderType);
        if (centerMuzzle) json.addProperty("centerMuzzle", true);
        if (fxTrail != null || fxImpact != null || fxCharge != null) {
            JsonObject fx = new JsonObject();
            if (fxTrail != null) fx.addProperty("trail", fxTrail);
            if (fxImpact != null) fx.addProperty("impact", fxImpact);
            if (fxCharge != null) fx.addProperty("charge", fxCharge);
            json.add("fx", fx);
        }
        return json;
    }

    /** Overlay {@code over} onto this profile; non-null fields in {@code over} win. */
    public XenoKiProfile merge(XenoKiProfile over) {
        if (over == null) return this;
        return new XenoKiProfile(
                !over.techniqueId.isBlank() ? over.techniqueId : techniqueId,
                !over.nativeId.isBlank() ? over.nativeId : nativeId,
                over.colorInterior != null ? over.colorInterior : colorInterior,
                over.colorExterior != null ? over.colorExterior : colorExterior,
                over.colorOutline != null ? over.colorOutline : colorOutline,
                over.size != null ? over.size : size,
                over.speed != null ? over.speed : speed,
                over.damageMultiplier != null ? over.damageMultiplier : damageMultiplier,
                over.armorPenetration != null ? over.armorPenetration : armorPenetration,
                over.castOffsetX != null ? over.castOffsetX : castOffsetX,
                over.castOffsetY != null ? over.castOffsetY : castOffsetY,
                over.castOffsetZ != null ? over.castOffsetZ : castOffsetZ,
                over.renderType != null ? over.renderType : renderType,
                // Explicit overlay always wins for muzzle centering.
                over.centerMuzzle,
                over.fxTrail != null ? over.fxTrail : fxTrail,
                over.fxImpact != null ? over.fxImpact : fxImpact,
                over.fxCharge != null ? over.fxCharge : fxCharge);
    }

    /**
     * Mutates {@code data} in place with clamped presentation stats. Safe on a copied template.
     */
    public void applyTo(KiAttackData data) {
        if (data == null) return;
        if (colorInterior != null) data.setColorInterior(colorInterior & 0xFFFFFF);
        if (colorExterior != null) data.setColorExterior(colorExterior & 0xFFFFFF);
        if (colorOutline != null) data.setColorOutline(colorOutline & 0xFFFFFF);
        if (size != null) data.setSize(clamp(size, 0.2f, 8f));
        if (speed != null) data.setSpeed(clamp(speed, 0.05f, 8f));
        if (damageMultiplier != null) data.setDamageMultiplier(clamp(damageMultiplier, 0.1f, 10f));
        if (armorPenetration != null) data.setArmorPenetration(Math.max(0, Math.min(100, armorPenetration)));
        data.calculateDerivedValues();
    }

    /** Core colour for HD {@link V3KiStyle}, or null to keep archetype default. */
    public Integer styleCore() {
        return colorInterior;
    }

    public Integer styleEdge() {
        return colorExterior != null ? colorExterior : colorInterior;
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String str(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
    }

    private static Integer intOrNull(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return null;
        try {
            return json.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Float floatOrNull(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return null;
        try {
            float v = json.get(key).getAsFloat();
            return Float.isFinite(v) ? v : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean bool(JsonObject json, String key, boolean def) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return def;
        try {
            return json.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return def;
        }
    }

    private static String fx(JsonObject json, String key) {
        if (!json.has("fx") || !json.get("fx").isJsonObject()) return null;
        JsonObject fx = json.getAsJsonObject("fx");
        return fx.has(key) && fx.get(key).isJsonPrimitive() ? blankToNull(fx.get(key).getAsString()) : null;
    }

    private static void putInt(JsonObject json, String key, Integer value) {
        if (value != null) json.addProperty(key, value);
    }

    private static void putFloat(JsonObject json, String key, Float value) {
        if (value != null) json.addProperty(key, value);
    }
}
