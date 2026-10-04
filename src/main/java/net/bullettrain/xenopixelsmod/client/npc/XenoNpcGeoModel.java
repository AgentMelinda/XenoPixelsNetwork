package net.bullettrain.xenopixelsmod.client.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.animation.Animation;

import java.util.function.Predicate;

/**
 * Resolves a Xeno NPC's GeckoLib assets from its profile rather than from a fixed constant.
 *
 * <p>{@code modelId} names an asset set, and the three conventional GeckoLib paths are derived from
 * it. That keeps the editor field to a single value and means a pack author drops three files in
 * the usual places rather than configuring each path separately:
 *
 * <pre>
 *   modelId "mypack:ogre"
 *     geo        assets/mypack/geo/ogre.geo.json
 *     texture    assets/mypack/textures/entity/ogre.png
 *     animation  assets/mypack/animations/ogre.animation.json
 * </pre>
 *
 * <p>DMZ also ships texture-only variants such as giant Slug. Those reuse the base rig and shared
 * saga animation, matching DMZ's own saga renderer. Missing geometry uses DMZ's existing fallback
 * rig so an operator's asset choice cannot crash the world renderer.
 *
 * <p>A bare id with no namespace resolves against this mod. {@code modelTexture}, when set,
 * overrides the derived texture so one rig can be reskinned per NPC.
 */
public final class XenoNpcGeoModel extends GeoModel<XenoNpcEntity> {

    /** Used when the profile names no model, so the renderer always has something to ask for. */
    private static final ResourceLocation FALLBACK =
            ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "xeno_npc");
    private static final ResourceLocation DMZ_FALLBACK_MODEL = ResourceLocation.fromNamespaceAndPath(
            "dragonminez", "geo/entity/enemies/robotxv.geo.json");
    private static final ResourceLocation DMZ_SAGA_ANIMATION = ResourceLocation.fromNamespaceAndPath(
            "dragonminez", "animations/entity/sagas/saga_base.animation.json");

    /** Per-NPC ease across the ends of a scripted clip; weak, so an unloaded NPC's goes with it. */
    private final java.util.Map<XenoNpcEntity, ClipPoseBlend> clipBlends = new java.util.WeakHashMap<>();

    @Override
    public void setCustomAnimations(XenoNpcEntity entity, long instanceId,
                                    software.bernie.geckolib.animation.AnimationState<XenoNpcEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);
        ClipPoseBlend blend = clipBlends.computeIfAbsent(entity, e -> new ClipPoseBlend());
        blend.frame(entity.scriptedClipActive(), state.getAnimationTick());
        for (software.bernie.geckolib.cache.object.GeoBone bone : getAnimationProcessor().getRegisteredBones()) {
            float[] animated = {bone.getRotX(), bone.getRotY(), bone.getRotZ(),
                    bone.getPosX(), bone.getPosY(), bone.getPosZ()};
            float[] shown = blend.apply(bone.getName(), animated);
            // Written only during an ease: a manual write marks the bone as changed for GeckoLib.
            if (shown != animated) {
                bone.setRotX(shown[0]);
                bone.setRotY(shown[1]);
                bone.setRotZ(shown[2]);
                bone.setPosX(shown[3]);
                bone.setPosY(shown[4]);
                bone.setPosZ(shown[5]);
            }
        }
    }

    @Override
    public ResourceLocation getModelResource(XenoNpcEntity entity) {
        ResourceLocation id = assetId(entity);
        return resolveModelResource(id, this::resourceExists);
    }

    static ResourceLocation resolveModelResource(ResourceLocation id, Predicate<ResourceLocation> exists) {
        String path = id.getPath();
        ResourceLocation candidate;
        if (path.startsWith("textures/") && path.endsWith(".png")) {
            candidate = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                    "geo/" + path.substring("textures/".length(), path.length() - 4) + ".geo.json");
        } else {
            candidate = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                    "geo/" + id.getPath() + ".geo.json");
        }
        if (exists.test(candidate)) return candidate;
        // DMZ's giant/first-person Slug PNGs are skins for the ordinary Slug geometry.
        String geoPath = candidate.getPath();
        if ("dragonminez".equals(id.getNamespace()) && geoPath.startsWith("geo/entity/sagas/")) {
            for (String suffix : new String[]{"_giant.geo.json", "_fp.geo.json"}) {
                if (geoPath.endsWith(suffix)) {
                    ResourceLocation base = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                            geoPath.substring(0, geoPath.length() - suffix.length()) + ".geo.json");
                    if (exists.test(base)) return base;
                }
            }
        }
        return DMZ_FALLBACK_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(XenoNpcEntity entity) {
        NpcCombatProfile profile = net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                .renderProfile(entity);
        if (entity.level() == null || !entity.level().isNight()
                || profile.nightTexture == null || profile.nightTexture.isBlank()) {
            ResourceLocation playerSkin = NpcPlayerSkinClient.texture(entity, profile);
            if (playerSkin != null) return playerSkin;
        }
        ResourceLocation override = parse(profile.textureFor(entity.level()));
        if (override != null) {
            return override;
        }
        ResourceLocation id = assetId(entity);
        if (id.getPath().startsWith("textures/") && id.getPath().endsWith(".png")) return id;
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                "textures/entity/" + id.getPath() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(XenoNpcEntity entity) {
        ResourceLocation id = assetId(entity);
        NpcCombatProfile profile = net.bullettrain.xenopixelsmod.client.compat.npc
                .NpcAppearanceClient.renderProfile(entity);
        ResourceLocation resolved = resolveAnimationResource(id, parse(profile.modelAnimation),
                this::resourceExists);
        entity.noteAnimationFile(resolved, profile.meleeAnimation);
        return resolved;
    }

    @Override
    public ResourceLocation[] getAnimationResourceFallbacks(XenoNpcEntity entity) {
        // GeckoLib 4.9.2 throws when its final fallback is not baked. Resource reloads can
        // temporarily leave that cache empty, so append the combat file only after it exists.
        ResourceLocation combat = net.bullettrain.xenopixelsmod.client.combat.anim
                .Bt3AnimationBinding.DMZ_ANIMATION_FILE;
        return GeckoLibCache.getBakedAnimations().containsKey(combat)
                ? new ResourceLocation[] {combat} : new ResourceLocation[0];
    }

    @Override
    public Animation getAnimation(XenoNpcEntity entity, String name) {
        Animation baked = super.getAnimation(entity, name);
        return baked != null ? baked
                : net.bullettrain.xenopixelsmod.client.anim.XenoStudioClipCache.get(name);
    }

    static ResourceLocation resolveAnimationResource(ResourceLocation id,
                                                     Predicate<ResourceLocation> exists) {
        return resolveAnimationResource(id, null, exists);
    }

    static ResourceLocation resolveAnimationResource(ResourceLocation id,
                                                     ResourceLocation override,
                                                     Predicate<ResourceLocation> exists) {
        if (override != null && exists.test(override)) return override;
        String path = id.getPath();
        ResourceLocation candidate;
        if (path.startsWith("textures/") && path.endsWith(".png")) {
            candidate = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                    "animations/" + path.substring("textures/".length(), path.length() - 4)
                            + ".animation.json");
        } else {
            candidate = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                    "animations/" + id.getPath() + ".animation.json");
        }
        return exists.test(candidate) ? candidate : DMZ_SAGA_ANIMATION;
    }

    private boolean resourceExists(ResourceLocation location) {
        return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
    }

    private static ResourceLocation assetId(XenoNpcEntity entity) {
        ResourceLocation parsed = parse(net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                .renderProfile(entity).modelId);
        return parsed == null ? FALLBACK : parsed;
    }

    /**
     * Parses a resource location, returning null rather than throwing.
     *
     * <p>The value comes from a text field an operator typed, so a malformed id is expected input,
     * not an exceptional condition - a throw here would break rendering for every NPC in view.
     */
    private static ResourceLocation parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(raw.trim().toLowerCase(java.util.Locale.ROOT));
    }
}
