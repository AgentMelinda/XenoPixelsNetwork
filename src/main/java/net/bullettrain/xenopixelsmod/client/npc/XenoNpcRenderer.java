package net.bullettrain.xenopixelsmod.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Draws a Xeno NPC in whichever form its profile selects.
 *
 * <p>An {@code EntityRenderer} is chosen once at registration, so all three model kinds have to be
 * served from this one class rather than by registering three renderers. {@link #render} therefore
 * dispatches per entity:
 *
 * <ul>
 *   <li>{@code VANILLA} - the humanoid model, with the profile's texture and size.
 *   <li>{@code GECKOLIB} - delegated to {@link XenoNpcGeoRenderer}, whose model resolves its
 *       {@code .geo.json}, texture and animation from the profile.
 *   <li>{@code ENTITY} - mimics any registered {@link EntityType} by rendering a cached stand-in of
 *       that type through the shared dispatcher. This is what makes other mods' models work with no
 *       per-mod code: anything with a registered renderer can be worn.
 * </ul>
 *
 * <p>Tint is applied on the GeckoLib path, where {@code GeoRenderer.getRenderColor} is a supported
 * hook. Vanilla's {@code LivingEntityRenderer} hard-codes its model colour, so a vanilla-path tint
 * would need a mixin; that is deliberately not done here and the editor says so.
 */
public final class XenoNpcRenderer extends MobRenderer<XenoNpcEntity,
        net.minecraft.client.model.PlayerModel<XenoNpcEntity>> {

    /**
     * Fallback skin for an NPC whose profile names no texture.
     *
     * <p>The path matters: {@code textures/entity/steve.png} does not exist in 1.21.1 and rendered
     * every NPC as the missing-texture checkerboard. The player skins live under
     * {@code textures/entity/player/wide/}, verified against the client jar.
     */
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private final XenoNpcGeoRenderer geoRenderer;

    /**
     * Stand-in entities for the ENTITY model kind, one per mimicked type.
     *
     * <p>A renderer needs an instance of the type it draws, and creating one per frame would
     * allocate heavily and re-run entity construction. These are never added to the level: they
     * exist only to be posed and drawn.
     */
    private final Map<EntityType<?>, Entity> mimics = new HashMap<>();

    public XenoNpcRenderer(EntityRendererProvider.Context context) {
        // PlayerModel rather than HumanoidModel: same geometry, plus the skin's outer layer and the
        // cloak part, which Display > Showing Layers and Cape need.
        super(context, new net.minecraft.client.model.PlayerModel<>(
                context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        // Without these an NPC wore its armour and held its weapon and none of it was drawn: this
        // renderer registered no layers at all. Only the humanoid path benefits - the GeckoLib,
        // entity-mimic and full-DMZ branches of render() draw their own models and never reach a
        // layer - which the Inventory page says out loud rather than leaving an operator to guess
        // why the helmet they typed is invisible on a GeckoLib NPC.
        addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<>(this,
                context.getItemInHandRenderer()));
        addLayer(new NpcOverlayLayer(this));
        addLayer(new NpcCapeLayer(this));
        this.geoRenderer = new XenoNpcGeoRenderer(context);
    }

    @Override
    public ResourceLocation getTextureLocation(XenoNpcEntity entity) {
        NpcCombatProfile profile = net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                .renderProfile(entity);
        ResourceLocation custom = parse(profile.textureFor(entity.level()));
        if (entity.level() != null && entity.level().isNight()
                && profile.nightTexture != null && !profile.nightTexture.isBlank()) {
            return custom == null ? DEFAULT_TEXTURE : custom;
        }
        ResourceLocation playerSkin = NpcPlayerSkinClient.texture(entity, profile);
        if (playerSkin != null) return playerSkin;
        return custom == null ? DEFAULT_TEXTURE : custom;
    }

    /** Nameplate lift above the vanilla spot, so tall DMZ hair does not cover the name. */
    static final double NAMEPLATE_RAISE = 0.45;
    /** One text line on the nameplate, in blocks (10 px at the vanilla 0.025 scale). */
    static final double NAMEPLATE_LINE = 0.27;

    /**
     * Drawn once from {@link #render} instead, for every model kind: GeckoLib, mimic and FULL DMZ
     * NPCs never reached this renderer's own name tag, and the FULL path drew its stand-in player's
     * name at player height.
     */
    @Override
    protected void renderNameTag(XenoNpcEntity entity, net.minecraft.network.chat.Component name, PoseStack pose,
                                 MultiBufferSource buffers, int light, float partialTick) {
    }

    /** Name with the title under it, lifted clear of the head. */
    private void drawNameplate(XenoNpcEntity entity, PoseStack pose, MultiBufferSource buffers, int light,
                               float partialTick) {
        NpcCombatProfile profile = net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient.renderProfile(entity);
        if (profile.displayShowName == 1 || profile.displayShowName == 2 && !entity.isCustomNameVisible()) return;
        if (!shouldShowName(entity)) return;
        net.minecraft.network.chat.Component title = entity.nameplateTitle();
        pose.pushPose();
        try {
            pose.translate(0.0, NAMEPLATE_RAISE, 0.0);
            if (title != null) {
                super.renderNameTag(entity, title, pose, buffers, light, partialTick);
                pose.translate(0.0, NAMEPLATE_LINE, 0.0);
            }
            super.renderNameTag(entity, entity.getDisplayName(), pose, buffers, light, partialTick);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public void render(XenoNpcEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        if (!entity.isAlive() && net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                .renderProfile(entity).hideDeadBody) return;
        renderModel(entity, entityYaw, partialTick, pose, buffers, light);
        drawNameplate(entity, pose, buffers, light, partialTick);
        // Bubbles from the same pose as the nameplate, when XenoServerConfig.npcBubblesInEntityPass
        // hands them to this pass (each call checks the switch and returns if it is off).
        net.bullettrain.xenopixelsmod.client.npc.speech.SpeechBubbleRenderer
                .renderInEntityPass(entity, pose, partialTick);
        net.bullettrain.xenopixelsmod.client.npc.dialog.DialogueBubbleRenderer
                .renderInEntityPass(entity, pose, partialTick);
    }

    private void renderModel(XenoNpcEntity entity, float entityYaw, float partialTick, PoseStack pose,
                             MultiBufferSource buffers, int light) {
        NpcCombatProfile profile = net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                .renderProfile(entity);
        String kind = NpcCombatProfile.normalizeModelKind(profile.modelKind);

        switch (kind) {
            case NpcCombatProfile.MODEL_GECKOLIB -> {
                pose.pushPose();
                try {
                    float size = sizeScale(profile.baseSize);
                    pose.scale(size, size, size);
                    geoRenderer.render(entity, entityYaw, partialTick, pose, buffers, light);
                } finally {
                    pose.popPose();
                }
            }
            case NpcCombatProfile.MODEL_ENTITY -> {
                if (!renderMimic(entity, profile, entityYaw, partialTick, pose, buffers, light)) {
                    // An unresolvable or unsafe id falls back to the humanoid rather than vanishing,
                    // so a typo in the editor never makes an NPC invisible.
                    super.render(entity, entityYaw, partialTick, pose, buffers, light);
                }
            }
            default -> {
                showOuterLayers(profile.displayOuterLayers);
                // A FULL-appearance NPC draws through DragonMineZ's own player model, which is what
                // makes its body type, face parts and colours show. Without this the world entity
                // stayed a plain skinned humanoid while the editor preview - which calls
                // renderPreview directly - looked correct, so the settings appeared to do nothing.
                //
                // The existing callers of this path are CustomNPCs and CNPC-Gecko mixins; a native
                // Xeno NPC has no mixin to route it, so the renderer asks here.
                if (!NpcFullDmzRenderer.isFull(entity)
                        || !NpcFullDmzRenderer.render(entity, entityYaw, partialTick, pose, buffers,
                                light)) {
                    super.render(entity, entityYaw, partialTick, pose, buffers, light);
                }
            }
        }
    }

    /**
     * Scales the humanoid model to the profile's size.
     *
     * <p>{@code baseSize} is the same field the reference menu's Size row edits, on its 1-30 scale,
     * where the shipped default of 0 means "unset" and renders at 1.0.
     */
    @Override
    protected void scale(XenoNpcEntity entity, PoseStack pose, float partialTick) {
        float scale = sizeScale(net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                .renderProfile(entity).baseSize);
        if (scale != 1.0f) {
            pose.scale(scale, scale, scale);
        }
        super.scale(entity, pose, partialTick);
    }

    /** Display > Showing Layers: the skin's second layer on or off. */
    private void showOuterLayers(boolean shown) {
        var model = getModel();
        model.hat.visible = shown;
        model.jacket.visible = shown;
        model.leftSleeve.visible = shown;
        model.rightSleeve.visible = shown;
        model.leftPants.visible = shown;
        model.rightPants.visible = shown;
    }

    /** Maps the 1-30 size field onto a render scale; 0 or 5 is unscaled. */
    static float sizeScale(int baseSize) {
        return NpcCombatProfile.visualSizeScale(baseSize);
    }

    /**
     * Draws {@code entity} as the registered type named by the profile.
     *
     * @return false when the id does not resolve, so the caller can fall back
     */
    private boolean renderMimic(XenoNpcEntity entity, NpcCombatProfile profile, float entityYaw,
                                float partialTick, PoseStack pose, MultiBufferSource buffers,
                                int light) {
        ResourceLocation id = parse(profile.modelId);
        if (id == null || entity.level() == null) {
            return false;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            return false;
        }
        // Mimicking a Xeno NPC would re-enter this renderer and recurse until the stack blew.
        if (type == entity.getType() || isXenoNpc(type)) {
            return false;
        }

        Entity stand = mimics.computeIfAbsent(type, t -> t.create(entity.level()));
        if (stand == null) {
            return false;
        }

        // Pose the stand-in on the real NPC so the mimic moves and faces the way the NPC does.
        stand.setPos(entity.getX(), entity.getY(), entity.getZ());
        stand.setYRot(entity.getYRot());
        stand.yRotO = entity.yRotO;
        stand.setXRot(entity.getXRot());
        stand.xRotO = entity.xRotO;
        stand.setOldPosAndRot();
        stand.tickCount = entity.tickCount;
        if (stand instanceof net.minecraft.world.entity.LivingEntity living
                && entity instanceof net.minecraft.world.entity.LivingEntity source) {
            living.yBodyRot = source.yBodyRot;
            living.yBodyRotO = source.yBodyRotO;
            living.yHeadRot = source.yHeadRot;
            living.yHeadRotO = source.yHeadRotO;
            living.walkAnimation.setSpeed(source.walkAnimation.speed());
            living.hurtTime = source.hurtTime;
        }

        EntityRenderer<? super Entity> renderer =
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(stand);
        // Identity, not equality: the generic bound makes == a compile error, and the guard only
        // needs to catch "the dispatcher handed us back ourselves", which would recurse.
        if (renderer == null || (Object) renderer == this) {
            return false;
        }

        float scale = sizeScale(profile.baseSize);
        pose.pushPose();
        try {
            if (scale != 1.0f) {
                pose.scale(scale, scale, scale);
            }
            renderer.render(stand, entityYaw, partialTick, pose, buffers, light);
        } catch (RuntimeException | LinkageError e) {
            // Third-party renderers can assume state their own entity would have. A failure here
            // must not take down the whole world render, so drop to the humanoid instead.
            pose.popPose();
            mimics.remove(type);
            return false;
        }
        pose.popPose();
        return true;
    }

    private static boolean isXenoNpc(EntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key != null && "xenopixelsmod".equals(key.getNamespace())
                && key.getPath().startsWith("xeno_npc");
    }

    /** Lenient parse: the value is operator-typed, so a malformed id is ordinary input. */
    static ResourceLocation parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(raw.trim().toLowerCase(Locale.ROOT));
    }
}
