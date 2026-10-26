package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.dragonminez.client.render.hair.HairMeshBuilder;
import com.dragonminez.client.render.layer.DMZHairLayer;
import com.dragonminez.common.hair.HairStyleSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.hair.CustomHair;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.compat.npc.NpcHairSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/** Client hair spec + render choice. The set itself comes from {@link NpcHairSet}. */
public final class NpcHairVis {
    private static final HairMeshBuilder MESH = new HairMeshBuilder();
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/hair.png");
    private static final float[] WHITE = {1.0f, 1.0f, 1.0f};

    public record Spec(boolean enabled, String code, String color, int styleId) {}

    private NpcHairVis() {}

    public static void clearCache() {
        NpcHairSet.clearCache();
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
        NpcHairAnimator.Input physics = NpcHairAnimator.input(owner, pose, partialTick,
                from.hair(), to.hair(), progress, from.slot(), to.slot());
        MESH.emit(buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                pose.last().pose(), pose.last().normal(), physics.state(), physics.simulated(),
                rgbInt(from.rgb()), rgbInt(to.rgb()), from.forceColor(), to.forceColor(),
                packedLight, packedOverlay, 1.0f, DMZHairLayer.wantsPixelDetail(owner), null, null, null);
    }

    /**
     * The four hair slots an NPC wears, from whichever source its profile selects.
     *
     * <p>Delegates to {@link NpcHairSet}, which holds the answer because writing an NPC's look onto
     * a player happens on the server thread and cannot reach a client class. The two render modes
     * used to work this out separately - OVERLAY parsed the code here and FULL parsed it again in
     * {@code NpcFullDmzRenderer} - which is how they came to disagree. Both ask this now.
     */
    public static CustomHair[] resolveSet(Spec spec) {
        return spec == null ? null : NpcHairSet.resolve(spec.code(), spec.color(), spec.styleId());
    }

    public static CustomHair[] parseSet(String code) {
        return NpcHairSet.parse(code);
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
        HairStyleSlot selectedSlot = HairStyleSlot.BASE;
        float[] rgb = parseRgb(configuredColor);
        boolean force = rgb != null;

        if (group != null && !group.isBlank() && formId != null && !formId.isBlank()) {
            FormConfig.FormData form = NpcFormLookup.form(race, group, formId);
            if (form != null) {
                HairStyleSlot typedSlot = Boolean.TRUE.equals(form.hasDefinedHairType())
                        ? HairStyleSlot.byHairType(form.getHairType()) : null;
                if (typedSlot != null) selectedSlot = typedSlot;
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
        return new HairChoice(selected, rgb == null ? WHITE : rgb, force, selectedSlot);
    }

    private static CustomHair variant(CustomHair[] set, String type) {
        int index = switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "ssj" -> 1;
            case "ssj2" -> 2;
            case "ssj3" -> 3;
            case "ssj4" -> 4;
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

    private static int rgbInt(float[] rgb) {
        return Math.round(rgb[0] * 255.0f) << 16 | Math.round(rgb[1] * 255.0f) << 8 | Math.round(rgb[2] * 255.0f);
    }

    private record HairChoice(CustomHair hair, float[] rgb, boolean forceColor, HairStyleSlot slot) {}
}
