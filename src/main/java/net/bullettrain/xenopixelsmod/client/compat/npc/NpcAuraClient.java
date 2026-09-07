package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.dragonminez.client.render.effects.AuraRenderer;
import com.dragonminez.common.compat.CameraAimHelper;
import com.dragonminez.client.render.shader.DMZShaders;
import com.dragonminez.client.render.util.AuraMeshFactory;
import com.dragonminez.client.render.util.IrisCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
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
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DragonMineZ kakarot shader column on CustomNPCs. Yaw-faces the camera so the
 * texture is readable, but stays world-upright — pitching the quad at the
 * camera flattened it into a ground fan when looking down from the air.
 *
 * <p>The gating around the draw — fade-in ramp, pitch-blended billboard →
 * ground-cross transition, one pulsed spark overlay, per-frame state pruning
 * and the once-per-frame stencil clear — is ported from DMZ's AuraRenderer so
 * this fallback matches its intensity instead of flashing, flickering or
 * corrupting the water seen through it.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class NpcAuraClient {
    private static final ResourceLocation KAKAROT_AURA =
            ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/aura/kakarot_aura.png");
    private static final float CNPC_DEFAULT_SIZE = 5.0f;
    /**
     * The 1.05 alone. DMZ's expanding floor rings are scaled from {@code data.auraScaleX}
     * directly (AuraRenderer :910), which is this base <i>without</i> the 2.2 draw multiplier
     * the billboard and the ground cross both apply -- so the rings are deliberately much
     * tighter than the column they sit under.
     */
    private static final float DMZ_AURA_SCALE = 1.05f;
    /**
     * DMZ's aura size, reproduced exactly so a fallback-rendered NPC matches a FULL-appearance
     * one standing next to it. AuraRenderer multiplies its resolved model scale by
     * {@code getAuraScale}'s 1.05 base (AuraRenderer :360) and then by the 2.2 draw multiplier
     * (:745), uniformly on all three axes. Our {@code scale} already carries the same quantity
     * DMZ's model scale carries for a FULL NPC -- CNPC display size times the profile's aura
     * scale, see {@code NpcFullDmzRenderer.worldAuraFactor}.
     */
    private static final float DMZ_AURA_BASE = 2.2f * DMZ_AURA_SCALE;
    /** Y offset DMZ applies after scaling, so it is in scaled units (AuraRenderer :794). */
    private static final float DMZ_AURA_LIFT = 0.7f;
    /** Ground-effect lift DMZ applies before drawing rings/crosses (AuraRenderer :588). */
    private static final float GROUND_LIFT = 0.05f;
    /** DMZ's fade-in rate, per frame (AuraRenderer :616). */
    private static final float FADE_STEP = 0.005f;
    /** Fade-out is deliberately brisker than fade-in so switching an aura off still reads as off. */
    private static final float GHOST_FADE_STEP = 0.02f;

    private static final ResourceLocation NULL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/null.png");

    private record AuraState(boolean on, int rgb, float scale,
                             boolean lightnings, int lightningRgb,
                             boolean sparking, boolean groundRing,
                             List<NpcAuraResolver.Layer> layers) {}

    private static final Map<UUID, AuraState> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> PULSE_PROGRESS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PULSE_LAST_RENDER = new ConcurrentHashMap<>();
    /**
     * DMZ-style fade ramp, 0 → 1 at 0.005 per frame.
     *
     * <p>It is deliberately never snapped back to 0 for an aura that is merely not being drawn
     * this frame. It used to be, on a 2-game-tick threshold, and because the ramp advances per
     * <i>frame</i> while the threshold counts <i>ticks</i>, any hitch that let two ticks pass
     * between frames restarted the whole 3-second fade -- which is what read as the aura dying
     * and coming back. Now it only starts at 0 when there is no entry at all (a genuinely new
     * aura), and otherwise decays, the way DMZ's own ghost aura does
     * ({@code AuraRenderer.renderShaderGhostAura}).
     */
    private static final Map<UUID, Float> FADE_PROGRESS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_RENDER = new ConcurrentHashMap<>();
    /**
     * Auras that were switched off but still have fade left to burn.
     *
     * <p>DMZ keeps a despawned/deactivated player's {@code CachedAuraData} alive and ramps its
     * alpha down instead of dropping it, so an aura fades out rather than vanishing between two
     * frames. {@code ACTIVE} is packet-authoritative and must clear immediately when the server
     * says off, so the outgoing state is parked here for that ramp-down.
     */
    private static final Map<UUID, AuraState> GHOSTS = new ConcurrentHashMap<>();
    /**
     * Frame counter, bumped once per rendered frame. A shader pack makes Iris dispatch
     * {@link RenderLevelStageEvent} more than once per frame (its shadow pass runs the same
     * stages), and the pulse/fade animations advance per call -- so without this they ran at
     * double speed and visibly jittered. Same source DMZ uses for its own once-per-frame work
     * ({@code PlayerEffectsRenderHandler.onRenderTick}).
     */
    private static long frameId;
    private static long lastAdvancedFrame = Long.MIN_VALUE;

    private NpcAuraClient() {}

    public static void apply(UUID entityUuid, boolean on, int rgb) {
        apply(entityUuid, on, rgb, 1.7f);
    }

    public static void apply(UUID entityUuid, boolean on, int rgb, float scale) {
        apply(entityUuid, on, rgb, scale, false, 0xD9F4FF);
    }

    public static void apply(UUID entityUuid, boolean on, int rgb, float scale,
                             boolean lightnings, int lightningRgb) {
        apply(entityUuid, on, rgb, scale, lightnings, lightningRgb, true, true,
                List.of(new NpcAuraResolver.Layer("kakarot", 0, rgb)));
    }

    public static void apply(UUID entityUuid, boolean on, int rgb, float scale,
                             boolean lightnings, int lightningRgb, boolean sparking,
                             boolean groundRing, List<NpcAuraResolver.Layer> layers) {
        if (entityUuid == null) {
            return;
        }
        if (on || lightnings) {
            GHOSTS.remove(entityUuid);
            ACTIVE.put(entityUuid, new AuraState(on, rgb & 0xFFFFFF,
                    NpcCombatProfile.clampAuraScale(scale), lightnings, lightningRgb & 0xFFFFFF,
                    sparking, groundRing, layers == null ? List.of() : List.copyOf(layers)));
        } else {
            AuraState previous = ACTIVE.remove(entityUuid);
            // Hand the last-known look to the ghost pass so it fades out over ~1 second
            // instead of disappearing between two frames.
            if (previous != null && FADE_PROGRESS.getOrDefault(entityUuid, 0.0f) > 0.0f) {
                GHOSTS.put(entityUuid, previous);
            } else {
                FADE_PROGRESS.remove(entityUuid);
                LAST_RENDER.remove(entityUuid);
            }
        }
    }

    public static boolean isActive(UUID entityUuid) {
        AuraState state = entityUuid == null ? null : ACTIVE.get(entityUuid);
        return state != null && state.on();
    }

    /** One tick of the frame counter per rendered frame, before any render stage runs. */
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        frameId++;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        PULSE_PROGRESS.clear();
        PULSE_LAST_RENDER.clear();
        FADE_PROGRESS.clear();
        LAST_RENDER.clear();
        GHOSTS.clear();
        lastAdvancedFrame = Long.MIN_VALUE;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        if (ACTIVE.isEmpty() && GHOSTS.isEmpty()) {
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
        // DMZ's pitch blend from camera-facing billboard to a flat ground cross. NPCs are
        // never the local player's first-person view, so DMZ's isFirstPerson guard on this
        // branch is always false here.
        float absPitch = Math.abs(camera.getXRot());
        float crossFactor = 0.0f;
        float pitchSquash = 1.0f;
        if (absPitch > 45.0f) {
            crossFactor = (float) Math.pow((absPitch - 45.0f) / 45.0f, 2.0);
            pitchSquash = 1.0f - crossFactor * 0.5f;
        }
        Set<UUID> renderedThisFrame = new HashSet<>();
        boolean stencilReady = false;
        // Only the first stage dispatch of a given frame may step the animations forward.
        boolean advance = frameId != lastAdvancedFrame && !mc.isPaused();
        lastAdvancedFrame = frameId;

        // DMZ's own player-aura draw (PlayerEffectsRenderHandler/AuraRenderer) happens at
        // AFTER_LEVEL and force-rebinds the main target for writing right before drawing --
        // without this, a shader pack's own passes between AFTER_ENTITIES and AFTER_LEVEL can
        // leave a different target bound and the column never reaches the screen.
        mc.getMainRenderTarget().bindWrite(false);

        for (Entity raw : mc.level.entitiesForRendering()) {
            if (!(raw instanceof LivingEntity entity) || !entity.isAlive()) {
                continue;
            }
            UUID id = entity.getUUID();
            AuraState state = ACTIVE.get(id);
            boolean ghost = false;
            if (state == null) {
                state = GHOSTS.get(id);
                if (state == null) {
                    continue;
                }
                ghost = true;
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
            // Only the billboard column has to drop out up close. It is a camera-facing quad
            // through the entity's mid-height, so from inside it the aura fills the screen. The
            // ground pulse and ground cross are flat, world-aligned and down at the feet, and look
            // right from any distance. This used to `continue` past the entity entirely, which is
            // why an NPC's whole aura -- rings included -- vanished the moment you walked up to it.
            // The threshold follows the aura's own width rather than a flat half block, so a large
            // NPC's wider column drops out at the distance it actually starts engulfing the camera.
            double dist = cam.distanceTo(mid);
            boolean insideColumn = dist < Math.max(0.5, width * 0.5);
            // Lazy once-per-frame stencil clear, only before the first entity that actually
            // draws: the aura render types test NOTEQUAL against stencil ref 1, and without
            // a deterministic clear that test (and the water seen through the aura) depends
            // on whatever stale stencil state was left in the buffer.
            if (!stencilReady) {
                prepareStencil(mc);
                stencilReady = true;
            }
            renderedThisFrame.add(id);
            // DMZ fade ramp (AuraRenderer :610-620), with the reset removed: an aura already
            // part-way faded in keeps its progress across frame hitches, chunk reloads and
            // tracking gaps. A ghost ramps back down instead (:664-675) and is dropped at zero.
            long gameTime = entity.level().getGameTime();
            LAST_RENDER.put(id, gameTime);
            float fade = FADE_PROGRESS.getOrDefault(id, 0.0f);
            if (advance) {
                if (ghost) {
                    fade = Math.max(0.0f, fade - GHOST_FADE_STEP);
                } else if (fade < 1.0f) {
                    fade = Math.min(1.0f, fade + FADE_STEP);
                }
                FADE_PROGRESS.put(id, fade);
            }
            if (ghost && fade <= 0.0f) {
                GHOSTS.remove(id);
                FADE_PROGRESS.remove(id);
                LAST_RENDER.remove(id);
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
            // Ground pulse and ground cross must stay world-aligned: draw them before the
            // yaw-facing rotation.
            if (state.on() && state.groundRing() && standsOnGround(entity)) {
                drawGroundPulse(pose, projection, shader, camera, entity, state, scale, partial,
                        fade, advance);
            }
            if (state.on() && crossFactor > 0.0f) {
                drawGroundCross(pose, projection, shader, camera, entity, state, scale, partial,
                        crossFactor * fade);
            }
            Vec3 toCamera = cam.subtract(mid);
            float lookYaw = CameraAimHelper.yaw(entity, toCamera);
            pose.mulPose(Axis.YP.rotationDegrees(180.0f - lookYaw));
            if (state.on() && !insideColumn) {
                drawColumn(pose, projection, shader, entity, state, scale, partial,
                        crossFactor, pitchSquash, fade);
            }
            if (state.lightnings()) {
                drawLightnings(pose, projection, entity, state, partial, sizeMul);
            }
        }

        // DMZ cleanCaches parity: drop per-entity animation state for ids that neither
        // rendered this frame nor are still packet-tracked. ACTIVE itself is never pruned --
        // packets key off it and it deliberately survives despawn (see NpcAuraPacket).
        java.util.function.Predicate<UUID> stale = id -> !renderedThisFrame.contains(id)
                && !ACTIVE.containsKey(id) && !GHOSTS.containsKey(id);
        FADE_PROGRESS.keySet().removeIf(stale);
        LAST_RENDER.keySet().removeIf(stale);
        PULSE_PROGRESS.keySet().removeIf(stale);
        PULSE_LAST_RENDER.keySet().removeIf(stale);
    }

    /**
     * Zero the stencil buffer once before the first aura draw of the frame. Mirrors DMZ's
     * cleanCaches prep (:321-330); under a shader pack AuraRenderer.auraType already hands
     * back the stencil-free compat type, so this clear is simply unused there.
     */
    private static void prepareStencil(Minecraft mc) {
        RenderTarget target = mc.getMainRenderTarget();
        if (!target.isStencilEnabled()) {
            target.enableStencil();
        }
        target.bindWrite(false);
        RenderSystem.stencilMask(255);
        RenderSystem.clear(1024, Minecraft.ON_OSX);
        RenderSystem.stencilMask(0);
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

    /**
     * Port of DMZ's expanding floor ring (AuraRenderer.renderShaderPulseAura).
     * Caller must invoke this while the pose is still world-aligned, before the
     * yaw-facing rotation that billboards the column.
     */
    private static void drawGroundPulse(PoseStack pose, Matrix4f projection, ShaderInstance shader,
                                        Camera camera, LivingEntity entity, AuraState state,
                                        float scale, float partial, float fade, boolean advance) {
        VertexBuffer ground = AuraMeshFactory.getGroundQuad();
        if (ground == null) {
            return;
        }
        UUID id = entity.getUUID();
        long gameTime = entity.level().getGameTime();
        Long lastRender = PULSE_LAST_RENDER.get(id);
        // DMZ resets the animation when an entity was not rendered for over 2 game ticks.
        if (lastRender == null || gameTime - lastRender > 2) {
            PULSE_PROGRESS.put(id, 0.0f);
        }
        PULSE_LAST_RENDER.put(id, gameTime);
        float progress = PULSE_PROGRESS.getOrDefault(id, 0.0f);
        if (advance) {
            progress += 0.01f;
            if (progress >= 1.0f) {
                progress -= 1.0f;
            }
            PULSE_PROGRESS.put(id, progress);
        }

        // Top layer's type/color, same source the column uses.
        List<NpcAuraResolver.Layer> layers = state.layers();
        NpcAuraResolver.Layer top = layers.isEmpty()
                ? new NpcAuraResolver.Layer("kakarot", 0, state.rgb())
                : layers.get(layers.size() - 1);
        int rgb = top.rgb();
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        String auraType = top.type() == null || top.type().isBlank() ? "kakarot" : top.type();
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("dragonminez",
                "textures/entity/races/aura/" + auraType.toLowerCase(Locale.ROOT) + "_cross.png");
        float layerScaleBoost = 1.0f + Math.max(0, top.index()) * 0.15f;
        // The ring basis excludes the column's 2.2 draw multiplier, exactly as DMZ's does.
        float base = DMZ_AURA_SCALE * scale;

        for (int i = 0; i < 2; i++) {
            float p = i == 0 ? progress : (progress + 0.5f) % 1.0f;
            float expansion = 1.0f + 6.0f * p;
            float alphaCurve = Mth.sin(p * Mth.PI);
            pose.pushPose();
            // Lift off the block surface. Without it the ring quad is coplanar with the ground
            // it stands on and Z-fights, which is what read as flickering and flashing. DMZ
            // applies the identical offset before calling renderShaderPulseAura.
            pose.translate(0.0, GROUND_LIFT, 0.0);
            // Face the quad at the camera by testing which side of the ring plane the camera is
            // actually on. This used to test `camera.getXRot() < 0` -- camera pitch -- as a proxy
            // for that, and the two disagree in exactly the case being reported: standing beside an
            // NPC at eye level you are ABOVE its foot rings while often looking slightly UP at its
            // body, so the pitch test flipped a quad that did not need flipping and the ring turned
            // away. Flying overhead removes the disagreement, which is why it reappeared up there.
            if (cameraBelowRing(camera, entity)) {
                pose.mulPose(Axis.XP.rotationDegrees(180.0f));
            }
            float s = base * expansion * 0.5f * layerScaleBoost;
            pose.scale(s, 1.0f, s);
            shader.safeGetUniform("speed").set((entity.tickCount + partial) * 0.5f);
            set4(shader, "color1", Math.min(1f, r * 1.6f), Math.min(1f, g * 1.6f), Math.min(1f, b * 1.6f), 1.0f);
            set4(shader, "color2", Math.min(1f, r * 1.3f), Math.min(1f, g * 1.3f), Math.min(1f, b * 1.3f), 1.0f);
            set4(shader, "color3", r, g, b, 0.85f);
            set4(shader, "color4", r * 0.75f, g * 0.75f, b * 0.75f, 0.65f);
            drawLayer(ground, pose, projection, shader, texture, alphaCurve * 0.6f * fade);
            pose.popPose();
        }
    }

    /**
     * Port of DMZ's pitch-blended ground cross (AuraRenderer :828-845): as the camera looks
     * further past 45 degrees down, the billboard fades out and this flat cross fades in.
     * Caller must invoke this while the pose is still world-aligned, before the yaw-facing
     * rotation that billboards the column.
     */
    private static void drawGroundCross(PoseStack pose, Matrix4f projection, ShaderInstance shader,
                                        Camera camera, LivingEntity entity, AuraState state,
                                        float scale, float partial, float alpha) {
        VertexBuffer ground = AuraMeshFactory.getGroundQuad();
        if (ground == null) {
            return;
        }
        List<NpcAuraResolver.Layer> layers = state.layers();
        NpcAuraResolver.Layer top = layers.isEmpty()
                ? new NpcAuraResolver.Layer("kakarot", 0, state.rgb())
                : layers.get(layers.size() - 1);
        int rgb = top.rgb();
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        String auraType = top.type() == null || top.type().isBlank() ? "kakarot" : top.type();
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("dragonminez",
                "textures/entity/races/aura/" + auraType.toLowerCase(Locale.ROOT) + "_cross.png");
        float layerScaleBoost = 1.0f + Math.max(0, top.index()) * 0.15f;
        // Column basis, so the cross matches the column's footprint at full blend.
        float s = DMZ_AURA_BASE * scale * layerScaleBoost;

        pose.pushPose();
        pose.translate(0.0, GROUND_LIFT, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        if (cameraBelowRing(camera, entity)) {
            pose.mulPose(Axis.XP.rotationDegrees(180.0f));
        }
        pose.scale(s, 1.0f, s);
        bindColors(shader, r, g, b, 1.0f, (entity.tickCount + partial) * 0.5f);
        drawLayer(ground, pose, projection, shader, texture, alpha);
        pose.popPose();
    }

    private static void setMatrix(ShaderInstance shader, String name, Matrix4f matrix) {
        AbstractUniform uniform = shader.safeGetUniform(name);
        if (uniform != null) uniform.set(matrix);
    }

    private static void drawColumn(PoseStack pose, Matrix4f projection, ShaderInstance shader,
                                   LivingEntity entity, AuraState state, float scale,
                                   float partial, float crossFactor, float pitchSquash, float fade) {
        if (crossFactor >= 1.0f) {
            // Fully blended into the ground cross; drawing the billboard here would just
            // add a face-on-invisible quad.
            return;
        }
        float billboardAlpha = (1.0f - crossFactor) * fade;
        float anim = (entity.tickCount + partial) * 0.5f;
        List<NpcAuraResolver.Layer> activeLayers = state.layers().isEmpty()
                ? List.of(new NpcAuraResolver.Layer("kakarot", 0, state.rgb())) : state.layers();
        for (NpcAuraResolver.Layer layer : activeLayers) {
            int rgb = layer.rgb();
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float b = (rgb & 0xFF) / 255f;
            float layerScale = 1.0f + Math.max(0, layer.index()) * 0.15f;
            // Uniform, DMZ-sized, and -- as DMZ does -- scaled before the lift, so the offset
            // rides the scale. The old split scaleX/scaleY made this column relatively wider
            // than DMZ's and left the lift in unscaled units, which is why a fallback NPC's
            // aura did not match a FULL one standing beside it.
            float base = DMZ_AURA_BASE * scale * layerScale;
            String auraType = layer.type() == null || layer.type().isBlank() ? "kakarot" : layer.type();
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("dragonminez",
                    "textures/entity/races/aura/" + auraType + "_aura.png");
            pose.pushPose();
            pose.scale(base, base * pitchSquash, base);
            pose.translate(0.0, DMZ_AURA_LIFT, 0.0);
            VertexBuffer billboard = AuraMeshFactory.getBillboardQuad();
            bindColors(shader, r, g, b, 1.0f, anim);
            // DMZ draws a single yaw-faced billboard whose alpha fades out past a 45 degree
            // camera pitch (handed off to drawGroundCross), not a permanent two-face cross:
            // the old always-on second face plus two constant spark faces read as flashy.
            drawFace(pose, projection, shader, billboard, texture, billboardAlpha);
            if (state.sparking()) {
                // One pulsed spark overlay inside the billboard pose (DMZ :803-824),
                // replacing the two constant-alpha spark faces.
                ResourceLocation sparkTexture = ResourceLocation.fromNamespaceAndPath("dragonminez",
                        "textures/entity/races/aura/sparking_effects.png");
                pose.pushPose();
                float sparkingPulse = 1.0f + Mth.sin((entity.tickCount + partial) * 0.2f) * 0.05f;
                pose.scale(0.8f * sparkingPulse, 0.65f * sparkingPulse, 0.8f * sparkingPulse);
                pose.translate(0.0, -0.25, 0.0);
                drawFace(pose, projection, shader, billboard, sparkTexture, billboardAlpha * 0.8f);
                pose.popPose();
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

    /** One face drawn in the caller's already-scaled pose (never pitched at the camera). */
    private static void drawFace(PoseStack pose, Matrix4f projection, ShaderInstance shader,
                                 VertexBuffer billboard, ResourceLocation texture, float alpha) {
        pose.pushPose();
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
        // DMZ parity (:847-848, :929-931): clear shader uniform state after every layer so
        // it cannot bleed into the next draw and shimmer between frames.
        shader.clear();
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

    /**
     * Whether this entity is planted on the floor for the purpose of drawing ground effects.
     *
     * <p>{@code onGround()} alone is not enough for a CustomNPC: they are routinely nudged by
     * scripts or left hovering a fraction of a block, which reads as airborne while the NPC is
     * visually standing still. The rings are a floor effect, so what matters is whether there is
     * floor immediately under it.
     */
    private static boolean standsOnGround(LivingEntity entity) {
        if (entity.onGround()) {
            return true;
        }
        BlockPos below = BlockPos.containing(entity.getX(), entity.getY() - 0.15, entity.getZ());
        return !entity.level().getBlockState(below).isAir();
    }

    /**
     * Whether the camera is underneath the ground-effect plane, and therefore sees its underside.
     *
     * <p>A plain height comparison against the ring's own Y - the honest form of the test the
     * pitch check was approximating.
     */
    private static boolean cameraBelowRing(Camera camera, LivingEntity entity) {
        Vec3 eye = camera.getPosition();
        return eye != null && eye.y < entity.getY() + GROUND_LIFT;
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