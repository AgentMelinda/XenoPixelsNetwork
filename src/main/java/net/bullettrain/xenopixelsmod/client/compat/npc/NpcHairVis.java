package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.dragonminez.client.render.hair.HairRenderer;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client hair spec + parse cache. Source of truth is the appearance packet / profile. */
public final class NpcHairVis {
    private static final float[] WHITE = {1.0f, 1.0f, 1.0f};
    private static final CustomHair[] INVALID = new CustomHair[0];
    private static final Map<String, CustomHair[]> CACHE = new ConcurrentHashMap<>();

    public record Spec(boolean enabled, String code, String color, int styleId) {}

    private NpcHairVis() {}

    public static void clearCache() {
        CACHE.clear();
    }

    public static Spec spec(LivingEntity owner) {
        if (owner == null) {
            return null;
        }
        NpcAppearanceClient.State appearance = NpcAppearanceClient.get(owner.getUUID());
        if (appearance != null) {
            return new Spec(appearance.hairEnabled(), appearance.hairCode(), appearance.hairColor(),
                    appearance.hairStyleId());
        }
        if (!NpcCombatProfile.hasProfile(owner)) {
            return null;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(owner);
        return new Spec(profile.hairEnabled, profile.hairCode, profile.hairColor,
                profile.hairStyleId);
    }

    public static void render(PoseStack pose, LivingEntity owner, MultiBufferSource buffers,
                              float partialTick, int packedLight, int packedOverlay) {
        Spec spec = spec(owner);
        if (spec == null || !spec.enabled()) {
            return;
        }
        CustomHair[] set = resolveSet(spec);
        if (set == null || set.length == 0 || set[0] == null) {
            return;
        }
        HairChoice from = choose(owner, set, spec.color());
        HairChoice to = from;
        float progress = 0.0f;
        NpcTransformHairClient.Hold hold = NpcTransformHairClient.get(owner.getUUID());
        if (hold != null) {
            to = choose(set, spec.color(), raceOf(owner), hold.toGroup(), hold.toForm());
            progress = hold.progress(owner.level().getGameTime(), partialTick);
        }
        if ((from.hair() == null || from.hair().isEmpty())
                && (to.hair() == null || to.hair().isEmpty())) {
            return;
        }
        NpcHairAnimator.Input physics = NpcHairAnimator.input(owner);
        if (physics == null) return;
        HairRenderer.render(pose, buffers,
                from.hair(), to.hair(), progress,
                null, physics.stats(), physics.player(),
                from.rgb(), to.rgb(), from.forceColor(), to.forceColor(),
                partialTick, packedLight, packedOverlay,
                1.0f, physics.physicsLod(), physics.chargeProgress());
    }

    /**
     * The four hair slots an NPC wears, from whichever source its profile selects.
     *
     * <p>The single answer to "what hair does this NPC have", deliberately. The two appearance
     * modes used to work it out separately — OVERLAY parsed the code here and FULL parsed it again
     * in {@code NpcFullDmzRenderer} — which is how they came to disagree. Both now ask this.
     *
     * <p>{@code styleId} 0 means the custom code; anything else is one of DragonMineZ's
     * character-creation presets, resolved through {@code HairManager} exactly as DMZ resolves it
     * for a real player. An id past the end of the preset list falls back to the code rather than
     * throwing, so a profile written against a larger preset set still renders.
     *
     * @return four slots (base, SSJ, SSJ2, SSJ3), or {@code null} when there is nothing to draw
     */
    public static CustomHair[] resolveSet(Spec spec) {
        if (spec == null) return null;
        int styleId = spec.styleId();
        if (styleId > 0 && styleId <= HairManager.getPresetCount()) {
            String color = spec.color() == null ? "" : spec.color();
            CustomHair base = HairManager.getPresetHair(styleId, color);
            if (base != null) {
                return new CustomHair[]{
                        base,
                        orElse(HairManager.getPresetHairSSJ(styleId, color), base),
                        orElse(HairManager.getPresetHairSSJ2(styleId, color), base),
                        orElse(HairManager.getPresetHairSSJ3(styleId, color), base)
                };
            }
        }
        return parseSet(spec.code());
    }

    private static CustomHair orElse(CustomHair value, CustomHair fallback) {
        return value == null ? fallback : value;
    }

    public static CustomHair[] parseSet(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String key = code.trim();
        CustomHair[] cached = CACHE.get(key);
        if (cached != null) {
            return cached.length == 0 ? null : cached;
        }
        CustomHair[] parsed = null;
        try {
            if (HairManager.isFullSetCode(key)) {
                if (key.startsWith("DMZF4:") || key.length() >= 64) {
                    parsed = HairManager.fromFullSetCode(key);
                }
            } else {
                CustomHair base = HairManager.fromCode(key);
                parsed = base == null ? null : new CustomHair[] {base, base, base, base};
            }
            if (parsed != null && (parsed.length == 0 || parsed[0] == null)) {
                parsed = null;
            }
        } catch (Exception ignored) {
            parsed = null;
        }
        CACHE.put(key, parsed == null ? INVALID : parsed);
        return parsed;
    }

    private static HairChoice choose(LivingEntity owner, CustomHair[] set, String configuredColor) {
        NpcAppearanceClient.State appearance = NpcAppearanceClient.get(owner.getUUID());
        if (appearance != null) {
            return choose(set, configuredColor, appearance.race(), appearance.formGroup(), appearance.form());
        }
        if (NpcCombatProfile.hasProfile(owner)) {
            NpcCombatProfile profile = NpcCombatProfile.read(owner);
            return choose(set, configuredColor, profile.raceId, profile.formGroup, profile.formId);
        }
        return choose(set, configuredColor, "", "", "");
    }

    private static String raceOf(LivingEntity owner) {
        NpcAppearanceClient.State appearance = NpcAppearanceClient.get(owner.getUUID());
        if (appearance != null) {
            return appearance.race();
        }
        if (NpcCombatProfile.hasProfile(owner)) {
            return NpcCombatProfile.read(owner).raceId;
        }
        return "";
    }

    private static HairChoice choose(CustomHair[] set, String configuredColor,
                                     String race, String group, String formId) {
        CustomHair selected = set[0];
        float[] rgb = parseRgb(configuredColor);
        boolean force = rgb != null;

        if (group != null && !group.isBlank() && formId != null && !formId.isBlank()) {
            FormConfig.FormData form = NpcFormLookup.form(race, group, formId);
            if (form != null) {
                if (Boolean.TRUE.equals(form.hasHairCodeOverride())) {
                    CustomHair[] override = parseSet(form.getForcedHairCode());
                    if (override != null && override.length > 0 && override[0] != null) {
                        selected = override[0];
                    }
                } else if (Boolean.TRUE.equals(form.hasDefinedHairType())) {
                    selected = variant(set, form.getHairType());
                }
                // Color field is the base tint. Active form gold/etc. still applies
                // (SSJ #FFE89E) so HairRenderer can lerp black → gold during hold.
                if (Boolean.TRUE.equals(form.hasHairColorOverride())) {
                    float[] overrideRgb = form.getRgbHairColor();
                    if (overrideRgb != null && overrideRgb.length >= 3) {
                        rgb = new float[] {overrideRgb[0], overrideRgb[1], overrideRgb[2]};
                        force = true;
                    }
                }
            }
        }
        return new HairChoice(selected, rgb == null ? WHITE : rgb, force);
    }

    private static CustomHair variant(CustomHair[] set, String type) {
        int index = switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "ssj" -> 1;
            case "ssj2" -> 2;
            case "ssj3" -> 3;
            default -> 0;
        };
        return index < set.length && set[index] != null ? set[index] : set[0];
    }

    private static float[] parseRgb(String value) {
        var parsed = NpcCombatProfile.parseHexColor(value);
        if (parsed.isEmpty()) {
            return null;
        }
        int rgb = parsed.getAsInt();
        return new float[] {
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f
        };
    }

    private record HairChoice(CustomHair hair, float[] rgb, boolean forceColor) {}
}
