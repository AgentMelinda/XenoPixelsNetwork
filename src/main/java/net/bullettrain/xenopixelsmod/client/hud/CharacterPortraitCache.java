package net.bullettrain.xenopixelsmod.client.hud;

import com.dragonminez.client.animation.IPlayerAnimatable;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.lists.SaiyanForms;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.client.combat.DmzAnimHelperClient;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/** One tick-cached DMZ character render shared by both modern HUD layouts. */
public final class CharacterPortraitCache {
    private static final int SIZE = 128;
    private static final float BASE_MODEL_HEIGHT = 1.9f, OOZARU_MODEL_SCALE = 3.8f;
    private static final EquipmentSlot[] PORTRAIT_EQUIPMENT = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST,
            EquipmentSlot.HEAD, EquipmentSlot.BODY
    };
    /**
     * Ticks between portrait re-renders at a healthy framerate.
     *
     * <p>A refresh is not cheap: it flushes the caller's buffers, binds an offscreen
     * {@link TextureTarget}, swaps the projection matrix, renders the whole player model with
     * every layer, then rebinds and restores. Doing that on every tick meant 20 full offscreen
     * entity renders per second — each one a pipeline stall — for a small HUD portrait whose pose
     * is fixed at 180 degrees anyway. Only the animation state can change between ticks, and a
     * portrait does not need 20 Hz to read as animated.
     *
     * <p>Appearance changes bypass this entirely via {@code appearanceHash}, so transforming or
     * swapping gear still updates on the very next frame. Combat poses and a struggling
     * framerate skip the refresh altogether — see {@link #shouldHoldCachedPortrait}.
     */
    private static final int REFRESH_INTERVAL_TICKS = 4;
    /** Stretch the interval once the custom character renderer is already under budget. */
    private static final int SLOW_REFRESH_INTERVAL_TICKS = 10;
    /** Below this, keep the last bust rather than paying for another GeckoLib pass. */
    private static final int HOLD_PORTRAIT_FPS = 28;
    /** Stretch refresh (but still allow one) between this and {@link #HOLD_PORTRAIT_FPS}. */
    private static final int SLOW_PORTRAIT_FPS = 45;

    private static TextureTarget target;
    private static UUID playerId;
    private static int playerTick = Integer.MIN_VALUE;
    private static int appearanceHash;
    private static boolean failed;
    /** Terminal state: even the fallback threw, so stop attempting a portrait entirely. */
    private static boolean disabled;

    private CharacterPortraitCache() {}

    public static void draw(GuiGraphics graphics, AbstractClientPlayer player,
                            int x, int y, int width, int height) {
        int hash = appearanceHash(player);
        // player.tickCount < playerTick catches a respawn/dimension change resetting the counter;
        // the subtraction is only reached once playerTick holds a real value, so the sentinel
        // cannot overflow into a false negative — target == null covers the very first draw.
        boolean appearanceChanged = !player.getUUID().equals(playerId) || hash != appearanceHash;
        boolean stale = target == null
                || player.tickCount < playerTick
                || player.tickCount - playerTick >= refreshIntervalTicks();
        // Re-rendering the live GeckoLib player during a kick/charge, or when the character
        // renderer is already below budget, is what made the world model twitch and dropped
        // the punch/kick. Keep the last bust; appearance (form, hair, gear) still wins.
        boolean hold = target != null && !appearanceChanged && shouldHoldCachedPortrait(player);
        if (!failed && !hold && (stale || appearanceChanged)) {
            refresh(graphics, player, height, hash);
        }
        if (target != null && !failed) {
            blit(graphics, x, y, width, height);
            return;
        }
        if (disabled) return;
        // The fallback runs the same renderDirect that just threw, so it needs its own guard —
        // otherwise a deterministic failure turns "log once and degrade" into an exception out of
        // draw() on every single HUD frame.
        try {
            renderFallback(graphics, player, x, y, width, height);
        } catch (Throwable t) {
            disabled = true;
            XenoPixelsMod.LOGGER.warn("Character portrait disabled after fallback render failed", t);
        }
    }

    private static void refresh(GuiGraphics screenGraphics, AbstractClientPlayer player,
                                int displayHeight, int hash) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        boolean projectionBackedUp = false;
        try {
            screenGraphics.flush();
            if (target == null) {
                target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
                target.setClearColor(0f, 0f, 0f, 0f);
                target.setFilterMode(9729);
            }
            target.clear(Minecraft.ON_OSX);
            RenderSystem.backupProjectionMatrix();
            projectionBackedUp = true;
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(
                    0f, SIZE, SIZE, 0f, 1000f, ClientHooks.getGuiFarPlane()), VertexSorting.ORTHOGRAPHIC_Z);

            GuiGraphics cacheGraphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            renderDirect(cacheGraphics, player, SIZE / 2f, SIZE / 2f,
                    SIZE / (float) Math.max(1, displayHeight));
            cacheGraphics.flush();

            playerId = player.getUUID();
            playerTick = player.tickCount;
            appearanceHash = hash;
        } catch (Throwable error) {
            failed = true;
            XenoPixelsMod.LOGGER.warn("Character portrait cache disabled after render failure", error);
        } finally {
            main.bindWrite(true);
            if (projectionBackedUp) RenderSystem.restoreProjectionMatrix();
            RenderSystem.viewport(0, 0, main.viewWidth, main.viewHeight);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void renderDirect(GuiGraphics graphics, AbstractClientPlayer player,
                                     float centerX, float centerY, float outputScale) {
        // Face the bust with body/head only. yRot/xRot on LocalPlayer ARE the gameplay
        // camera; writing 180/0 here (and into the O-fields) is what made the view snap
        // every time the HUD refreshed the character.
        float bodyRot = player.yBodyRot, bodyRotO = player.yBodyRotO;
        float headRot = player.yHeadRot, headRotO = player.yHeadRotO;
        player.yBodyRot = player.yBodyRotO = player.yHeadRot = player.yHeadRotO = 180f;
        Minecraft mc = Minecraft.getInstance();
        var dispatcher = mc.getEntityRenderDispatcher();
        try {
            float[] modelScale = modelScale(player);
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
            Vector3f translation = new Vector3f(0f,
                    BASE_MODEL_HEIGHT * 0.5f * modelScale[1]
                            + XenoHudConfig.portraitOffset * modelScale[0], 0f);
            // Fresh identity quat so InventoryScreen can conjugate it without touching
            // a shared instance. Dispatcher camera is restored in finally.
            EntityPreviewRenderContext.renderHudPortrait(graphics,
                    Math.round(centerX), Math.round(centerY),
                    XenoHudConfig.portraitScale * outputScale / modelScale[0],
                    translation, pose, new Quaternionf(), player);
        } finally {
            player.yBodyRot = bodyRot;
            player.yBodyRotO = bodyRotO;
            player.yHeadRot = headRot;
            player.yHeadRotO = headRotO;
            dispatcher.overrideCameraOrientation(mc.gameRenderer.getMainCamera().rotation());
        }
    }

    private static void renderFallback(GuiGraphics graphics, AbstractClientPlayer player,
                                       int x, int y, int width, int height) {
        Matrix4f matrix = graphics.pose().last().pose();
        Vector3f a = matrix.transformPosition(new Vector3f(x, y, 0f));
        Vector3f b = matrix.transformPosition(new Vector3f(x + width, y + height, 0f));
        graphics.enableScissor((int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
                (int) Math.ceil(Math.max(a.x, b.x)), (int) Math.ceil(Math.max(a.y, b.y)));
        try {
            renderDirect(graphics, player, x + width / 2f, y + height / 2f, 1f);
        } finally {
            graphics.disableScissor();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static float[] modelScale(AbstractClientPlayer player) {
        float fallback = Math.max(0.01f, player.getScale());
        float[] scale = {fallback, fallback};
        var stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (stats == null) return scale;
        var character = stats.getCharacter();
        Float[] resolved = character.getResolvedModelScaling();
        // Guarded the same way appearanceHash below and DMZ's OverShoulderCamera.cameraScale guard
        // it. Unboxing blind here is a per-frame NPE, and draw()'s fallback path cannot catch it.
        if (resolved == null || resolved.length < 3
                || resolved[0] == null || resolved[1] == null || resolved[2] == null) {
            return scale;
        }
        String form = character.getActiveForm();
        boolean oozaru = character.getRenderLogicKey().startsWith("oozaru")
                || ("saiyan".equalsIgnoreCase(character.getRaceName())
                && (SaiyanForms.OOZARU.equals(form) || SaiyanForms.GOLDEN_OOZARU.equals(form)));
        float intrinsic = oozaru ? OOZARU_MODEL_SCALE : 1f;
        float x = Math.max(0.1f, oozaru ? resolved[0] - 2.8f : resolved[0]) * intrinsic;
        float y = Math.max(0.1f, oozaru ? resolved[1] - 2.8f : resolved[1]) * intrinsic;
        float z = Math.max(0.1f, oozaru ? resolved[2] - 2.8f : resolved[2]) * intrinsic;
        scale[0] = Math.max(x, Math.max(y, z));
        scale[1] = y;
        return scale;
    }

    private static int appearanceHash(AbstractClientPlayer player) {
        int hash = player.getSkin().texture().hashCode();
        hash = 31 * hash + XenoHudConfig.portraitScale;
        hash = 31 * hash + Float.floatToIntBits(XenoHudConfig.portraitOffset);
        for (EquipmentSlot slot : PORTRAIT_EQUIPMENT) {
            hash = 31 * hash + ItemStack.hashItemAndComponents(player.getItemBySlot(slot));
        }
        var stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (stats != null) {
            var character = stats.getCharacter();
            hash = 31 * hash + hash(character.getRaceName());
            hash = 31 * hash + hash(character.getActiveForm());
            hash = 31 * hash + hash(character.getRenderLogicKey());
            hash = 31 * hash + character.getHairId();
            hash = 31 * hash + hash(character.getHairColor());
            for (Float axis : character.getResolvedModelScaling())
                hash = 31 * hash + Float.floatToIntBits(axis == null ? 1f : axis);
        }
        return hash;
    }

    private static int hash(Object value) { return value == null ? 0 : value.hashCode(); }

    /**
     * Skip another offscreen {@code DMZPlayerRenderer} pass while the world model owns a combat
     * pose, or while the custom character renderer is already dropping frames.
     *
     * <p>That second pass shares one GeckoLib instance with the live player. On a loaded pack the
     * HUD lands many milliseconds after the world pose, so controllers interpolate to a different
     * keyframe than the swing the user just triggered — kicks vanish and shoulders twitch until
     * the HUD is hidden.
     */
    private static boolean shouldHoldCachedPortrait(AbstractClientPlayer player) {
        // getCombatPlacementWeight is the side-effect-free "melee ticks remain" read.
        // isPlayingCombatAnimation() decrements DMZ's grace counter, so the HUD must not call it.
        if (player instanceof IPlayerAnimatable anim && anim.dragonminez$getCombatPlacementWeight() > 0f) {
            return true;
        }
        if (Bt3CombatClient.isCharging() || Bt3CombatClient.isGuarding()) {
            return true;
        }
        if (DmzAnimHelperClient.ClientStrikeChain.isArmed()) {
            return true;
        }
        int fps = Minecraft.getInstance().getFps();
        return fps > 0 && fps < HOLD_PORTRAIT_FPS;
    }

    private static int refreshIntervalTicks() {
        int fps = Minecraft.getInstance().getFps();
        if (fps > 0 && fps < SLOW_PORTRAIT_FPS) return SLOW_REFRESH_INTERVAL_TICKS;
        return REFRESH_INTERVAL_TICKS;
    }

    private static void blit(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x, y, 0).setUv(0f, 1f);
        buffer.addVertex(matrix, x, y + height, 0).setUv(0f, 0f);
        buffer.addVertex(matrix, x + width, y + height, 0).setUv(1f, 0f);
        buffer.addVertex(matrix, x + width, y, 0).setUv(1f, 1f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    public static void invalidate() {
        playerTick = Integer.MIN_VALUE;
        appearanceHash = 0;
        failed = false;
    }

    public static void destroy() {
        TextureTarget old = target;
        target = null;
        invalidate();
        if (old == null) return;
        if (RenderSystem.isOnRenderThread()) old.destroyBuffers();
        else RenderSystem.recordRenderCall(old::destroyBuffers);
    }

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { destroy(); }

        @SubscribeEvent
        public static void clone(ClientPlayerNetworkEvent.Clone event) { destroy(); }
    }

    @EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> destroy());
        }
    }
}
