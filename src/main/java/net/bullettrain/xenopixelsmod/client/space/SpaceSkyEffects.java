package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.space.math.StarSkyMesh;
import net.bullettrain.xenopixelsmod.space.math.VoidSkyGradient;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * The space sky, at whichever of the three costs the player picked in {@code /xenoset sky}.
 *
 * <p>The cube and the star field are separate buffers with separate lives: {@code void} builds only the
 * cube, {@code stars} builds both, and {@code off} builds neither and hands the frame to vanilla.
 * Switching modes releases whatever the new mode does not draw rather than keeping it resident, so the
 * switch is a real release of VRAM and vertex work rather than a hidden {@code if} inside a draw.
 * Rebuilding on a switch is one cube — the point of the switch is that nobody has to pay it per frame.
 *
 * <p>Planet visuals belong to world rendering, not here.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SpaceSkyEffects extends DimensionSpecialEffects {
    public static final ResourceLocation EFFECTS = ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "space_sky");
    private static final SpaceSkyEffects INSTANCE = new SpaceSkyEffects();
    /** The dome's half-extent in metres from the camera; well inside the nearest projection range. */
    private static final int SHELL = 64;
    private VertexBuffer cube;
    private VertexBuffer stars;
    private VertexBuffer atmosphereTransition;
    /** Which mode the live buffers were built for, so a switch is detected once rather than per frame. */
    private SpaceSkyMode built = null;
    private final Matrix4f view = new Matrix4f();

    private SpaceSkyEffects() { super(Float.NaN, false, SkyType.END, true, false); }
    @SubscribeEvent public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(EFFECTS, INSTANCE);
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            Runnable clear = () -> { INSTANCE.closeBuffers(); OrbitPlanetProxies.closeBuffers(); PhysicalPlanetRenderer.closeBuffers();
                StellarBlastWaveRenderer.closeBuffer(); };
            if (RenderSystem.isOnRenderThread()) clear.run();
            else RenderSystem.recordRenderCall(clear::run);
        });
    }
    private void closeBuffers() {
        if (cube != null) { cube.close(); cube = null; }
        if (stars != null) { stars.close(); stars = null; }
        if (atmosphereTransition != null) { atmosphereTransition.close(); atmosphereTransition = null; }
        FallenStarSkyLayer.close();
        built = null;
        KnownSystemsSkyLayer.close();
    }
    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return color.scale(.15); }
    @Override public boolean isFoggyAt(int x, int y) { return false; }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView,
                             Camera camera, Matrix4f projection, boolean foggy, Runnable setupFog) {
        // This override only runs for a level whose dimension_type declares the "space_sky" effect, so
        // the effect registration is the gate; no per-dimension id check belongs here.
        if (foggy) return true;
        SpaceSkyMode mode = SpaceSkyMode.byName(XenoClientConfig.spaceSkyMode);
        if (mode != built) {
            // A mode change is rare and explicit; this is the one place buffers are built or released,
            // so a switch that lands mid-frame can close a buffer the current draw still wants.
            closeBuffers();
            built = mode;
            XenoPixelsMod.LOGGER.info("Space sky mode: {}", mode.id);
        }
        if (mode == SpaceSkyMode.OFF) return false;
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE), blend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        float fogStart=RenderSystem.getShaderFogStart(), fogEnd=RenderSystem.getShaderFogEnd();
        float[] color=RenderSystem.getShaderColor();
        float red=color[0], green=color[1], blue=color[2], alpha=color[3];
        var shader=RenderSystem.getShader();
        try {
            ensureBuffers(mode);
            FogRenderer.setupNoFog();
            RenderSystem.depthMask(false); RenderSystem.disableDepthTest();
            RenderSystem.disableBlend(); RenderSystem.disableCull();
            RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            view.set(modelView).setTranslation(0,0,0);
            cube.bind(); cube.drawWithShader(view, projection, GameRenderer.getPositionColorShader());
            if (stars != null) {
                stars.bind(); stars.drawWithShader(view, projection, GameRenderer.getPositionColorShader());
                // Galaxy Atlas slice 2: known systems as coloured stars in their true directions.
                KnownSystemsSkyLayer.draw(view, projection, camera.getPosition());
            }
            FallenStarSkyLayer.draw(view, projection, level.getGameTime() + (double) partialTick,
                    UniverseClient.fieldSeed(), mode == SpaceSkyMode.STARS && UniverseClient.hasSnapshot()
                            ? XenoClientConfig.spaceFallenStarDensity : 0);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.depthMask(write);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShaderColor(red,green,blue,alpha);
            if (shader != null) RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd);
        }
        return true;
    }
    /**
     * The same sky, for a frame drawn by an Iris shader pack. Iris maps a dimension its pack does not name to
     * the pack's overworld programs ({@code *:*} → {@code world0}), so the pack paints a blue day sky and this
     * class's {@link #renderSky} never reaches the screen. Called after the pack's passes with the main target
     * bound: the dome is scaled far beyond the far plane and depth-clamped, so every fragment lands at depth
     * exactly 1.0 and {@code LEQUAL} replaces only pixels nothing was drawn at — the pack's sky — while terrain,
     * ships and players keep their pack shading.
     */
    static void drawOverShaderPackSky(Matrix4f modelView, Matrix4f projection, ClientLevel level, float partialTick) {
        INSTANCE.overPack(modelView, projection, level, partialTick);
    }

    /** Draws a space-tinted sky and the existing stars over a planet at its dimension-local altitude fade. */
    static void drawAtmosphereTransition(Matrix4f modelView, Matrix4f projection, float alpha, boolean shaderPack) {
        SpaceSkyMode mode = SpaceSkyMode.byName(XenoClientConfig.spaceSkyMode);
        if (!(alpha > 0.0f) || mode == SpaceSkyMode.OFF) return;
        if (INSTANCE.built != mode) {
            INSTANCE.closeBuffers();
            INSTANCE.built = mode;
        }
        INSTANCE.ensureBuffers(mode);
        if (INSTANCE.atmosphereTransition == null) INSTANCE.atmosphereTransition = INSTANCE.buildAtmosphereTransitionBuffer();

        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean clamp = GL11.glIsEnabled(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int blendSrcRgb = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int blendDstRgb = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int blendSrcAlpha = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int blendDstAlpha = GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        float[] color = RenderSystem.getShaderColor();
        float red = color[0], green = color[1], blue = color[2], oldAlpha = color[3];
        var shader = RenderSystem.getShader();
        try {
            FogRenderer.setupNoFog();
            RenderSystem.depthMask(false);
            if (shaderPack) {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                GL11.glEnable(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP);
                INSTANCE.view.set(modelView).setTranslation(0, 0, 0).scale(4096f);
            } else {
                RenderSystem.disableDepthTest();
                INSTANCE.view.set(modelView).setTranslation(0, 0, 0);
            }
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, Math.min(1.0f, alpha));
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            INSTANCE.atmosphereTransition.bind();
            INSTANCE.atmosphereTransition.drawWithShader(INSTANCE.view, projection, GameRenderer.getPositionColorShader());
            if (mode == SpaceSkyMode.STARS && INSTANCE.stars != null) {
                INSTANCE.stars.bind();
                INSTANCE.stars.drawWithShader(INSTANCE.view, projection, GameRenderer.getPositionColorShader());
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.depthMask(write);
            RenderSystem.depthFunc(depthFunction);
            if (clamp) GL11.glEnable(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP);
            else GL11.glDisable(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP);
            RenderSystem.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShaderColor(red, green, blue, oldAlpha);
            if (shader != null) RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    private VertexBuffer buildAtmosphereTransitionBuffer() {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int[][] faces = {{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
        for (int[] face : faces) for (int corner : face) {
            int y = (corner & 2) == 0 ? -SHELL : SHELL;
            int color = y < 0 ? 0xff17143f : 0xff000000;
            builder.addVertex((corner & 1) == 0 ? -SHELL : SHELL, y,
                    (corner & 4) == 0 ? -SHELL : SHELL).setColor(color);
        }
        return upload(builder);
    }
    private void overPack(Matrix4f modelView, Matrix4f projection, ClientLevel level, float partialTick) {
        SpaceSkyMode mode = SpaceSkyMode.byName(XenoClientConfig.spaceSkyMode);
        if (mode != built) { closeBuffers(); built = mode; }
        if (mode == SpaceSkyMode.OFF) return;
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE), blend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean clamp=GL11.glIsEnabled(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP);
        int function=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        float[] color=RenderSystem.getShaderColor();
        float red=color[0], green=color[1], blue=color[2], alpha=color[3];
        var shader=RenderSystem.getShader();
        try {
            ensureBuffers(mode);
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.depthFunc(GL11.GL_LEQUAL);
            GL11.glEnable(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP);
            RenderSystem.disableBlend(); RenderSystem.disableCull();
            RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            view.set(modelView).setTranslation(0,0,0).scale(4096f);
            cube.bind(); cube.drawWithShader(view, projection, GameRenderer.getPositionColorShader());
            if (stars != null) {
                stars.bind(); stars.drawWithShader(view, projection, GameRenderer.getPositionColorShader());
            }
            FallenStarSkyLayer.draw(view, projection, level.getGameTime() + (double) partialTick, UniverseClient.fieldSeed(),
                    mode == SpaceSkyMode.STARS && UniverseClient.hasSnapshot()
                            ? XenoClientConfig.spaceFallenStarDensity : 0);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.depthMask(write); RenderSystem.depthFunc(function);
            if (clamp) GL11.glEnable(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP); else GL11.glDisable(org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShaderColor(red,green,blue,alpha);
            if (shader != null) RenderSystem.setShader(() -> shader);
        }
    }
    private void ensureBuffers(SpaceSkyMode mode) {
        if (cube != null) return;
        try {
            BufferBuilder builder=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            int[][] faces={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
            for (int[] face : faces) for (int corner : face) {
                int y = (corner&2)==0 ? -SHELL : SHELL;
                builder.addVertex((corner&1)==0 ? -SHELL:SHELL, y,
                        (corner&4)==0 ? -SHELL:SHELL).setColor(colour(mode, y));
            }
            cube=upload(builder);
            if (mode == SpaceSkyMode.STARS) {
                var mesh=StarSkyMesh.create();
                builder=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                for (int i=0;i<mesh.colors().length;i++) builder.addVertex(mesh.positions()[i*3],
                        mesh.positions()[i*3+1],mesh.positions()[i*3+2]).setColor(mesh.colors()[i]);
                stars=upload(builder);
            }
        } catch (RuntimeException | Error failure) { closeBuffers(); throw failure; }
        finally { VertexBuffer.unbind(); }
    }
    /** The stars mode keeps the flat black dome it always drew; only the void mode is tinted. */
    private static int colour(SpaceSkyMode mode, int shellY) {
        return mode == SpaceSkyMode.VOID ? VoidSkyGradient.argb((shellY + (double) SHELL) / (2.0 * SHELL))
                : 0xff000000;
    }
    private static VertexBuffer upload(BufferBuilder builder) {
        VertexBuffer buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);
        try { buffer.bind(); buffer.upload(builder.buildOrThrow()); return buffer; }
        catch (RuntimeException | Error failure) { buffer.close(); throw failure; }
    }
}
