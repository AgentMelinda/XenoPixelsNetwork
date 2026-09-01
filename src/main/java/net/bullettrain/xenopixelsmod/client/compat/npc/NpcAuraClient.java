package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.dragonminez.client.render.effects.AuraRenderer;
import com.dragonminez.common.compat.CameraAimHelper;
import com.dragonminez.client.render.shader.DMZShaders;
import com.dragonminez.client.render.util.AuraMeshFactory;
import com.dragonminez.client.render.util.IrisCompat;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Axis;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDisplayApply;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraResolver;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DragonMineZ kakarot shader column on CustomNPCs. Yaw-faces the camera so the
 * texture is readable, but stays world-upright — pitching the quad at the
 * camera flattened it into a ground fan when looking down from the air.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class NpcAuraClient {
    private static final ResourceLocation KAKAROT_AURA =
            ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/aura/kakarot_aura.png");
    private static final float CNPC_DEFAULT_SIZE = 5.0f;

    private static final ResourceLocation NULL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/null.png");

    private record AuraState(boolean on, int rgb, float scale,
                             boolean lightnings, int lightningRgb,
                             boolean sparking, List<NpcAuraResolver.Layer> layers) {}

    private static final Map<UUID, AuraState> ACTIVE = new ConcurrentHashMap<>();

    private NpcAuraClient() {}

    public static void apply(UUID entityUuid, boolean on, int rgb) {
        apply(entityUuid, on, rgb, 1.7f);
    }

    public static void apply(UUID entityUuid, boolean on, int rgb, float scale) {
        apply(entityUuid, on, rgb, scale, false, 0xD9F4FF);
    }

    public static void apply(UUID entityUuid, boolean on, int rgb, float scale,
                             boolean lightnings, int lightningRgb) {
        apply(entityUuid, on, rgb, scale, lightnings, lightningRgb, true,
                List.of(new NpcAuraResolver.Layer("kakarot", 0, rgb)));
    }

    public static void apply(UUID entityUuid, boolean on, int rgb, float scale,
                             boolean lightnings, int lightningRgb, boolean sparking,
                             List<NpcAuraResolver.Layer> layers) {
        if (entityUuid == null) {
            return;
        }
        if (on || lightnings) {
            ACTIVE.put(entityUuid, new AuraState(on, rgb & 0xFFFFFF,
                    NpcCombatProfile.clampAuraScale(scale), lightnings, lightningRgb & 0xFFFFFF,
                    sparking, layers == null ? List.of() : List.copyOf(layers)));
        } else {
            ACTIVE.remove(entityUuid);
        }
    }

    public static boolean isActive(UUID entityUuid) {
        AuraState state = entityUuid == null ? null : ACTIVE.get(entityUuid);
        return state != null && state.on();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        ShaderInstance shader = shader();
        if (shader == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Matrix4f projection = event.getProjectionMatrix();
        boolean iris = IrisCompat.isShaderPackInUse();

        // DMZ's own player-aura draw (PlayerEffectsRenderHandler/AuraRenderer) happens at
        // AFTER_LEVEL and force-rebinds the main target for writing right before drawing --
        // without this, a shader pack's own passes between AFTER_ENTITIES and AFTER_LEVEL can
        // leave a different target bound and the column never reaches the screen.
        mc.getMainRenderTarget().bindWrite(false);

        for (Entity raw : mc.level.entitiesForRendering()) {
            if (!(raw instanceof LivingEntity entity) || !entity.isAlive()) {
                continue;
            }
            AuraState state = ACTIVE.get(entity.getUUID());
            if (state == null) {
                continue;
            }
            // FULL appearance is a synthetic DMZ player and therefore uses DMZ's native
            // deferred AuraRenderer. Drawing this fallback column as well caused the
            // duplicate aura and did not follow DMZ's race/form model scaling.
            if (NpcFullDmzRenderer.isFull(entity)) {
                continue;
            }
            Vec3 feet = visualFeet(mc, entity, partial);
            float sizeMul = cnpcSizeMul(entity);
            float scale = state.scale() * sizeMul;
            float height = 1.8f * scale;
            float width = 0.6f * scale;
            Vec3 mid = feet.add(0.0, height * 0.5, 0.0);
            double dist = cam.distanceTo(mid);
            if (dist < 0.5) {
                continue;
            }

            // At AFTER_LEVEL the event's own model-view matrix is the real camera-space
            // transform (same source DMZ's own deferred effect pose uses), so seed the pose
            // from it directly. Only reconstruct the view from camera XP/YP -- what DMZ's
            // AuraRenderer.shaderpackViewStack does -- when a shader pack is actually in use,
            // matching DMZ's own Iris workaround instead of doing it unconditionally.
            PoseStack pose = new PoseStack();
            if (iris) {
                pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
                pose.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0f));
            } else {
                pose.last().pose().set(event.getModelViewMatrix());
            }
            // Do not pitch the quad at the camera -- that laid the column on the ground when
            // looking down.
            pose.translate(feet.x - cam.x, feet.y - cam.y, feet.z - cam.z);
            Vec3 toCamera = cam.subtract(mid);
            float lookYaw = CameraAimHelper.yaw(entity, toCamera);
            pose.mulPose(Axis.YP.rotationDegrees(180.0f - lookYaw));
            if (state.on()) {
                drawColumn(pose, projection, shader, entity, state, height, width, partial);
            }
            if (state.lightnings()) {
                drawLightnings(pose, projection, entity, state, partial, sizeMul);
            }
        }
    }

    private static void drawLightnings(PoseStack origin, Matrix4f projection, LivingEntity entity,
                                       AuraState state, float partial, float sizeMul) {
        ShaderInstance shader;
        try {
            shader = DMZShaders.lightningShader;
        } catch (Throwable ignored) {
            return;
        }
        if (shader == null) return;
        VertexBuffer mesh = AuraRenderer.getLightningMesh();
        if (mesh == null) return;
        float intensity = state.on() ? 1.0f : 0.2f;
        int count = state.on() ? 5 : 3;
        float r = ((state.lightningRgb() >> 16) & 0xFF) / 255f;
        float g = ((state.lightningRgb() >> 8) & 0xFF) / 255f;
        float b = (state.lightningRgb() & 0xFF) / 255f;
        setMatrix(shader, "projectionMatrix", projection);
        shader.safeGetUniform("time").set((entity.tickCount + partial) / 20.0f);
        shader.safeGetUniform("speedModifier").set(intensity);
        shader.safeGetUniform("color1").set(Mth.lerp(0.8f, r, 1f),
                Mth.lerp(0.8f, g, 1f), Mth.lerp(0.8f, b, 1f));
        shader.safeGetUniform("color2").set(r, g, b);
        shader.safeGetUniform("alp1").set(1.0f);
        shader.safeGetUniform("alp2").set(0.1f);
        shader.safeGetUniform("power").set(3.0f);
        shader.safeGetUniform("divis").set(1.0f);
        RenderType type = AuraRenderer.lightningType(NULL_TEXTURE);
        AuraRenderer.customSetup(type, NULL_TEXTURE, shader);
        java.util.Random random = new java.util.Random(entity.getId()
                + entity.level().getGameTime() / (state.on() ? 2L : 20L));
        mesh.bind();
        for (int i = 0; i < count; i++) {
            origin.pushPose();
            origin.translate((random.nextFloat() - 0.5f) * 1.2f * sizeMul,
                    random.nextFloat() * entity.getBbHeight(),
                    (random.nextFloat() - 0.5f) * 1.2f * sizeMul);
            origin.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360f));
            origin.mulPose(Axis.ZP.rotationDegrees(90f + (random.nextFloat() - 0.5f) * 40f));
            float boltScale = (0.15f + random.nextFloat() * (state.on() ? 0.5f : 0.25f)) * sizeMul;
            origin.scale(boltScale, boltScale, boltScale);
            setMatrix(shader, "modelMatrix", origin.last().pose());
            if (shader.safeGetUniform("normalMatrix") != null) {
                shader.safeGetUniform("normalMatrix").set(origin.last().normal());
            }
            shader.apply();
            mesh.drawWithShader(origin.last().pose(), projection, shader);
            origin.popPose();
        }
        VertexBuffer.unbind();
        shader.clear();
        AuraRenderer.customClear(type);
    }

    private static void setMatrix(ShaderInstance shader, String name, Matrix4f matrix) {
        AbstractUniform uniform = shader.safeGetUniform(name);
        if (uniform != null) uniform.set(matrix);
    }

    private static void drawColumn(PoseStack pose, Matrix4f projection, ShaderInstance shader,
                                   LivingEntity entity, AuraState state, float height, float width,
                                   float partial) {
        float anim = (entity.tickCount + partial) * 0.5f;
        List<NpcAuraResolver.Layer> activeLayers = state.layers().isEmpty()
                ? List.of(new NpcAuraResolver.Layer("kakarot", 0, state.rgb())) : state.layers();
        for (NpcAuraResolver.Layer layer : activeLayers) {
            int rgb = layer.rgb();
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float b = (rgb & 0xFF) / 255f;
            float layerScale = 1.0f + Math.max(0, layer.index()) * 0.15f;
            float scaleX = 1.15f * (width / 0.6f) * layerScale;
            float scaleY = 1.35f * (height / 1.8f) * layerScale;
            String auraType = layer.type() == null || layer.type().isBlank() ? "kakarot" : layer.type();
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("dragonminez",
                    "textures/entity/races/aura/" + auraType + "_aura.png");
            pose.pushPose();
            pose.translate(0.0, height * 0.5, 0.0);
            VertexBuffer billboard = AuraMeshFactory.getBillboardQuad();
            bindColors(shader, r, g, b, 1.0f, anim);
            // Cross-billboard: a single yaw-only quad is edge-on (near-invisible) when viewed
            // from directly above/below. A second copy rotated 90 degrees keeps at least one
            // face roughly toward the camera from any angle, without pitching either quad
            // toward the camera -- pitching previously laid the column flat on the ground when
            // viewed from below.
            drawFace(pose, projection, shader, billboard, texture, scaleX, scaleY, 0.0f, 1.0f);
            drawFace(pose, projection, shader, billboard, texture, scaleX, scaleY, 90.0f, 1.0f);
            if (state.sparking()) {
                ResourceLocation sparkTexture = ResourceLocation.fromNamespaceAndPath("dragonminez",
                        "textures/entity/races/aura/sparking_effects.png");
                float sparkScaleX = scaleX * 0.6f;
                float sparkScaleY = scaleY * 0.45f;
                drawFace(pose, projection, shader, billboard, sparkTexture, sparkScaleX, sparkScaleY, 0.0f, 0.8f);
                drawFace(pose, projection, shader, billboard, sparkTexture, sparkScaleX, sparkScaleY, 90.0f, 0.8f);
            }
            pose.popPose();
        }
    }

    private static float cnpcSizeMul(LivingEntity entity) {
        int size = NpcDisplayApply.getSize(entity);
        if (size <= 0) {
            return 1.0f;
        }
        return size / CNPC_DEFAULT_SIZE;
    }

    private static ShaderInstance shader() {
        try {
            return DMZShaders.auraShader;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** One face of a cross-billboard: rotate around Y (never pitch), scale, draw, restore. */
    private static void drawFace(PoseStack pose, Matrix4f projection, ShaderInstance shader,
                                 VertexBuffer billboard, ResourceLocation texture,
                                 float scaleX, float scaleY, float yawDeg, float alpha) {
        pose.pushPose();
        if (yawDeg != 0.0f) {
            pose.mulPose(Axis.YP.rotationDegrees(yawDeg));
        }
        pose.scale(scaleX, scaleY, 1.0f);
        drawLayer(billboard, pose, projection, shader, texture, alpha);
        pose.popPose();
    }

    private static void drawLayer(VertexBuffer mesh, PoseStack pose, Matrix4f projection,
                                  ShaderInstance shader, ResourceLocation texture, float alpha) {
        if (mesh == null) {
            return;
        }
        RenderType type = AuraRenderer.auraType(texture);
        AuraRenderer.customSetup(type, texture, shader);
        shader.safeGetUniform("alp1").set(alpha);
        shader.safeGetUniform("modelMatrix").set(pose.last().pose());
        AbstractUniform proj = shader.safeGetUniform("ProjMat");
        if (proj != null) {
            proj.set(projection);
        }
        RenderSystem.setShader(() -> shader);
        mesh.bind();
        mesh.drawWithShader(pose.last().pose(), projection, shader);
        VertexBuffer.unbind();
        AuraRenderer.customClear(type);
    }

    private static void bindColors(ShaderInstance shader, float r, float g, float b, float alpha, float speed) {
        set4(shader, "color1", Math.min(1f, r * 1.15f + 0.12f), Math.min(1f, g * 1.15f + 0.12f), Math.min(1f, b * 1.15f + 0.12f), alpha);
        set4(shader, "color2", r, g, b, alpha);
        set4(shader, "color3", r * 0.55f, g * 0.55f, b * 0.55f, alpha * 0.85f);
        set4(shader, "color4", r * 0.2f, g * 0.2f, b * 0.2f, alpha * 0.5f);
        shader.safeGetUniform("speed").set(speed);
    }

    private static void set4(ShaderInstance shader, String name, float a, float b, float c, float d) {
        AbstractUniform u = shader.safeGetUniform(name);
        if (u != null) {
            u.set(a, b, c, d);
        }
    }

    private static Vec3 visualFeet(Minecraft mc, LivingEntity entity, float partial) {
        double x = Mth.lerp(partial, entity.xo, entity.getX());
        double y = Mth.lerp(partial, entity.yo, entity.getY());
        double z = Mth.lerp(partial, entity.zo, entity.getZ());
        try {
            Vec3 offset = mc.getEntityRenderDispatcher().getRenderer(entity).getRenderOffset(entity, partial);
            if (offset != null) {
                x += offset.x;
                y += offset.y;
                z += offset.z;
            }
        } catch (Throwable ignored) {
        }
        return new Vec3(x, y, z);
    }
}
