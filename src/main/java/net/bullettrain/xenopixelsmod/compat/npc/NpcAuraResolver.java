package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.config.RaceCharacterConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves NPC aura state with the same normal/extra/stack layer precedence as DMZ players. */
public final class NpcAuraResolver {
    public record Layer(String type, int index, int rgb, boolean threeD,
                        FormConfig.Aura3DStyle threeDStyle) {
        public Layer(String type, int index, int rgb, boolean threeD) {
            this(type, index, rgb, threeD, null);
        }
        public Layer(String type, int index, int rgb) { this(type, index, rgb, false, null); }
    }
    public record Resolved(List<Layer> layers, boolean lightning, int lightningRgb,
                           boolean rocks, boolean sparking, boolean groundRing) {
        public int particleRgb() {
            return layers.isEmpty() ? 0xFFFFFF : layers.get(layers.size() - 1).rgb();
        }
    }

    private NpcAuraResolver() {}

    public static Resolved resolve(NpcCombatProfile profile) {
        if (profile == null) profile = new NpcCombatProfile();
        Map<Integer, Layer> layers = new LinkedHashMap<>();
        String raceType = raceAuraType(profile.raceId);
        int baseRgb = baseRgb(profile);
        NpcAuraStyle baseStyle = profile.baseAuraStyle;
        put(layers, layer(raceType, raceAura3DType(profile.raceId), 0, baseRgb, baseStyle, null,
                raceAura3DStyle(profile.raceId)));
        addExtra(layers, null, baseStyle, null);

        FormConfig.FormData normal = NpcFormLookup.activeForm(profile);
        NpcAuraStyle normalStyle = profile.auraStyle(false, profile.formGroup, profile.formId, false);
        if (normal != null) {
            int rgb = color(normal.getAuraColor(), baseRgb);
            String type = nonBlank(normal.getAuraType(), raceType);
            int index = normal.getAuraLayer() == null ? 0 : normal.getAuraLayer();
            put(layers, layer(type, normal.getAuraType3D(), index, rgb, normalStyle, baseStyle,
                    normal.getAura3DStyle()));
            addExtra(layers, normal, normalStyle, baseStyle);
        }

        FormConfig.FormData stack = NpcFormLookup.activeStackForm(profile);
        NpcAuraStyle stackStyle = profile.auraStyle(true, profile.stackGroup, profile.stackId, false);
        if (stack != null) {
            int rgb = color(stack.getAuraColor(), 0xFFFFFF);
            String type = nonBlank(stack.getAuraType(), "kakarot");
            int index = stack.getAuraLayer() == null ? 1 : stack.getAuraLayer();
            put(layers, layer(type, stack.getAuraType3D(), index, rgb, stackStyle, baseStyle,
                    stack.getAura3DStyle()));
            addExtra(layers, stack, stackStyle, baseStyle);
        }

        List<Layer> ordered = new ArrayList<>(layers.values());
        ordered.sort(Comparator.comparingInt(Layer::index));
        Lightning lightning = resolveLightning(profile, normal, normalStyle, stack, stackStyle, baseStyle);
        return new Resolved(List.copyOf(ordered), profile.auraLightning && lightning.enabled,
                lightning.rgb, profile.auraRocks, profile.auraSparking, profile.auraGroundRing);
    }

    public static int baseRgb(NpcCombatProfile profile) {
        if (profile != null && profile.auraColorHex != null && !profile.auraColorHex.isBlank()) {
            return NpcCombatProfile.parseHexColor(profile.auraColorHex).orElse(0xFFFFFF);
        }
        if (profile != null && profile.auraColor != 0) return profile.auraColor & 0xFFFFFF;
        try {
            RaceCharacterConfig race = ConfigManager.getRaceCharacter(
                    profile == null || profile.raceId == null || profile.raceId.isBlank() ? "human" : profile.raceId);
            if (race != null) return color(race.getDefaultAuraColor(), 0xFFFFFF);
        } catch (Throwable ignored) {
        }
        return 0xFFFFFF;
    }

    private static Layer layer(String type, String type3D, int index, int rgb, NpcAuraStyle override,
                               NpcAuraStyle inherited, FormConfig.Aura3DStyle threeDStyle) {
        if (override != null && override.enabled) {
            type = nonBlank(override.primaryType, type);
            if (override.primaryLayer >= 0) index = override.primaryLayer;
            if (!override.primaryColor.isBlank()) rgb = color(override.primaryColor, rgb);
        }
        boolean threeD = mode(override == null ? NpcAuraStyle.Toggle.INHERIT : override.threeD,
                inherited == null ? NpcAuraStyle.Toggle.INHERIT : inherited.threeD, false);
        if (threeD) type = spatialType(type3D, override == null ? "" : override.primaryType,
                override, inherited);
        return new Layer(nonBlank(type, "kakarot"), clamp(index), rgb & 0xFFFFFF, threeD,
                threeD ? (threeDStyle == null ? FormConfig.Aura3DStyle.DEFAULT : threeDStyle) : null);
    }

    private static void addExtra(Map<Integer, Layer> layers, FormConfig.FormData data, NpcAuraStyle override, NpcAuraStyle inherited) {
        boolean configured = override != null && override.enabled && override.extraConfigured;
        boolean enabled = configured ? override.extraEnabled : data != null && data.hasExtraAura();
        if (!enabled) return;
        String type = data == null ? "kakarot" : nonBlank(data.getExtraAuraType(), "kakarot");
        int index = data == null ? 1 : data.getExtraAuraLayer();
        int rgb = data == null ? 0xFFFFFF : color(data.getExtraAuraColor(), 0xFFFFFF);
        if (override != null && override.enabled) {
            type = nonBlank(override.extraType, type);
            if (override.extraLayer >= 0) index = override.extraLayer;
            if (!override.extraColor.isBlank()) rgb = color(override.extraColor, rgb);
        }
        boolean threeD = mode(override == null ? NpcAuraStyle.Toggle.INHERIT : override.threeD,
                inherited == null ? NpcAuraStyle.Toggle.INHERIT : inherited.threeD, false);
        if (threeD) {
            type = spatialType(data == null ? "smooth" : data.getExtraAuraType3D(),
                    override == null ? "" : override.extraType, override, inherited);
        }
        FormConfig.Aura3DStyle threeDStyle = null;
        if (threeD && data != null) {
            threeDStyle = data.getExtraAura3DStyle();
            if (threeDStyle == null) threeDStyle = FormConfig.Aura3DStyle.DEFAULT;
        } else if (threeD) {
            threeDStyle = FormConfig.Aura3DStyle.DEFAULT;
        }
        put(layers, new Layer(type, clamp(index), rgb, threeD, threeDStyle));
    }

    private static String spatialType(String dmzType, String selected, NpcAuraStyle style, NpcAuraStyle inherited) {
        String type = nonBlank(dmzType, "smooth");
        if (style != null && style.enabled && ("smooth".equals(selected)
                || "sparking".equals(selected) || "god".equals(selected))) type = selected;
        NpcAuraStyle.Toggle smooth = style == null ? NpcAuraStyle.Toggle.INHERIT : style.smooth;
        NpcAuraStyle.Toggle baseSmooth = inherited == null ? NpcAuraStyle.Toggle.INHERIT : inherited.smooth;
        if (smooth == NpcAuraStyle.Toggle.INHERIT) smooth = baseSmooth;
        if (smooth == NpcAuraStyle.Toggle.ON) return "smooth";
        if (smooth == NpcAuraStyle.Toggle.OFF && "smooth".equals(type)) return "sparking";
        return type;
    }

    static boolean mode(NpcAuraStyle.Toggle mode, NpcAuraStyle.Toggle inherited, boolean dmzDefault) {
        return mode.resolve(inherited.resolve(dmzDefault));
    }

    private static Lightning resolveLightning(NpcCombatProfile profile,
                                               FormConfig.FormData normal, NpcAuraStyle normalStyle,
                                               FormConfig.FormData stack, NpcAuraStyle stackStyle,
                                               NpcAuraStyle baseStyle) {
        if (stack != null) return lightning(stack, stackStyle, 0xD9F4FF);
        if (normal != null) return lightning(normal, normalStyle, 0xD9F4FF);
        if (baseStyle != null && baseStyle.enabled && baseStyle.lightningConfigured) {
            return new Lightning(baseStyle.lightningEnabled,
                    color(baseStyle.lightningColor, baseRgb(profile)));
        }
        return new Lightning(false, 0xD9F4FF);
    }

    private static Lightning lightning(FormConfig.FormData data, NpcAuraStyle style, int fallback) {
        boolean enabled = data != null && Boolean.TRUE.equals(data.getHasLightnings());
        int rgb = data == null ? fallback : color(data.getLightningColor(),
                color(data.getAuraColor(), fallback));
        if (style != null && style.enabled && style.lightningConfigured) enabled = style.lightningEnabled;
        if (style != null && style.enabled && !style.lightningColor.isBlank()) rgb = color(style.lightningColor, rgb);
        return new Lightning(enabled, rgb);
    }

    private static void put(Map<Integer, Layer> layers, Layer layer) {
        if (layer != null) layers.put(layer.index(), layer);
    }

    private static String raceAura3DType(String raceId) {
        try {
            RaceCharacterConfig race = ConfigManager.getRaceCharacter(
                    raceId == null || raceId.isBlank() ? "human" : raceId);
            return race == null ? "smooth" : nonBlank(race.getAuraType3D(), "smooth");
        } catch (Throwable ignored) {
            // Same fallback as raceAuraType: this runs on the render path for every NPC, and DMZ's
            // race table is not guaranteed to be populated when the first aura is resolved.
        }
        return "smooth";
    }

    private static FormConfig.Aura3DStyle raceAura3DStyle(String raceId) {
        try {
            RaceCharacterConfig race = ConfigManager.getRaceCharacter(
                    raceId == null || raceId.isBlank() ? "human" : raceId);
            return race == null || race.getAura3DStyle() == null
                    ? FormConfig.Aura3DStyle.DEFAULT : race.getAura3DStyle();
        } catch (Throwable ignored) {
            return FormConfig.Aura3DStyle.DEFAULT;
        }
    }

    private static String raceAuraType(String raceId) {
        try {
            RaceCharacterConfig race = ConfigManager.getRaceCharacter(
                    raceId == null || raceId.isBlank() ? "human" : raceId);
            if (race != null) return nonBlank(race.getAuraType(), "kakarot");
        } catch (Throwable ignored) {
        }
        return "kakarot";
    }

    private static int color(String value, int fallback) {
        return NpcCombatProfile.parseHexColor(value).orElse(fallback) & 0xFFFFFF;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static int clamp(int layer) { return Math.max(0, Math.min(6, layer)); }
    private record Lightning(boolean enabled, int rgb) {}
}
