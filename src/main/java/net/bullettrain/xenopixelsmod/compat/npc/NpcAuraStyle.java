package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;

/** Per-NPC override for one of DMZ's base, normal-form, or stack-form aura layers. */
public final class NpcAuraStyle {
    public enum Toggle {
        INHERIT, ON, OFF;
        public Toggle next() { return values()[(ordinal() + 1) % values().length]; }
        public boolean resolve(boolean inherited) { return this == INHERIT ? inherited : this == ON; }
        public static Toggle read(String value) {
            try { return valueOf(value); } catch (IllegalArgumentException ignored) { return INHERIT; }
        }
    }
    public Toggle smooth = Toggle.INHERIT;
    public Toggle threeD = Toggle.INHERIT;
    public boolean enabled;
    public String primaryColor = "";
    public String primaryType = "";
    /** -1 inherits DMZ's layer. */
    public int primaryLayer = -1;
    public boolean extraConfigured;
    public boolean extraEnabled;
    public String extraColor = "";
    public String extraType = "";
    /** -1 inherits DMZ's layer. */
    public int extraLayer = -1;
    public boolean lightningConfigured;
    public boolean lightningEnabled;
    public String lightningColor = "";

    public NpcAuraStyle copy() {
        NpcAuraStyle out = new NpcAuraStyle();
        out.smooth = smooth;
        out.threeD = threeD;
        out.enabled = enabled;
        out.primaryColor = primaryColor;
        out.primaryType = primaryType;
        out.primaryLayer = primaryLayer;
        out.extraConfigured = extraConfigured;
        out.extraEnabled = extraEnabled;
        out.extraColor = extraColor;
        out.extraType = extraType;
        out.extraLayer = extraLayer;
        out.lightningConfigured = lightningConfigured;
        out.lightningEnabled = lightningEnabled;
        out.lightningColor = lightningColor;
        return out;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Smooth", smooth.name());
        tag.putString("ThreeD", threeD.name());
        tag.putBoolean("Enabled", enabled);
        tag.putString("PrimaryColor", canonical(primaryColor));
        tag.putString("PrimaryType", safe(primaryType));
        tag.putInt("PrimaryLayer", clampLayer(primaryLayer));
        tag.putBoolean("ExtraConfigured", extraConfigured);
        tag.putBoolean("ExtraEnabled", extraEnabled);
        tag.putString("ExtraColor", canonical(extraColor));
        tag.putString("ExtraType", safe(extraType));
        tag.putInt("ExtraLayer", clampLayer(extraLayer));
        tag.putBoolean("LightningConfigured", lightningConfigured);
        tag.putBoolean("LightningEnabled", lightningEnabled);
        tag.putString("LightningColor", canonical(lightningColor));
        return tag;
    }

    public static NpcAuraStyle load(CompoundTag tag) {
        NpcAuraStyle out = new NpcAuraStyle();
        if (tag == null || tag.isEmpty()) return out;
        out.smooth = Toggle.read(tag.getString("Smooth"));
        out.threeD = Toggle.read(tag.getString("ThreeD"));
        out.enabled = tag.getBoolean("Enabled");
        out.primaryColor = canonical(tag.getString("PrimaryColor"));
        out.primaryType = safe(tag.getString("PrimaryType"));
        out.primaryLayer = clampLayer(tag.contains("PrimaryLayer") ? tag.getInt("PrimaryLayer") : -1);
        out.extraConfigured = tag.getBoolean("ExtraConfigured");
        out.extraEnabled = tag.getBoolean("ExtraEnabled");
        out.extraColor = canonical(tag.getString("ExtraColor"));
        out.extraType = safe(tag.getString("ExtraType"));
        out.extraLayer = clampLayer(tag.contains("ExtraLayer") ? tag.getInt("ExtraLayer") : -1);
        out.lightningConfigured = tag.getBoolean("LightningConfigured");
        out.lightningEnabled = tag.getBoolean("LightningEnabled");
        out.lightningColor = canonical(tag.getString("LightningColor"));
        return out;
    }

    public static int clampLayer(int layer) {
        return layer < 0 ? -1 : Math.min(6, layer);
    }

    private static String canonical(String value) {
        if (value == null || value.isBlank()) return "";
        return NpcCombatProfile.parseHexColor(value).isPresent()
                ? NpcCombatProfile.formatHex(NpcCombatProfile.parseHexColor(value).getAsInt()) : "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
