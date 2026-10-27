package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.space.math.FallenStarVisibility;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;

/** Draws the shared bounded streak in non-Voidsea night skies, including the shared planet-surfaces cells. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class FallenStarNightSkyRenderer {
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final net.minecraft.resources.ResourceLocation VOIDSEA =
            net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST;

    private FallenStarNightSkyRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        boolean shaderPack = IrisShaderState.packInUse();
        RenderLevelStageEvent.Stage wanted = shaderPack
                ? RenderLevelStageEvent.Stage.AFTER_LEVEL : RenderLevelStageEvent.Stage.AFTER_SKY;
        if (event.getStage() != wanted || shaderPack && IrisShaderState.renderingShadows()) return;

        var mc = net.minecraft.client.Minecraft.getInstance();
        var level = mc.level;
        if (level == null || VOIDSEA.equals(level.dimension().location()) || !level.dimensionType().hasSkyLight()
                || !UniverseClient.hasSnapshot()) return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float visibility = FallenStarVisibility.nightVisibility(level.getStarBrightness(partialTick),
                level.getRainLevel(partialTick));
        int density = XenoClientConfig.spaceFallenStarDensity;
        if (density <= 0 || visibility <= 0.0f) {
            FallenStarSkyLayer.close();
            return;
        }

        Matrix4f view = VIEW.set(event.getModelViewMatrix()).setTranslation(0.0f, 0.0f, 0.0f);
        Matrix4f projection = PROJECTION.set(event.getProjectionMatrix());
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean depthClamp = GL11.glIsEnabled(GL32.GL_DEPTH_CLAMP);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        try {
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            if (shaderPack) {
                mc.getMainRenderTarget().bindWrite(false);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                GL11.glEnable(GL32.GL_DEPTH_CLAMP);
                RenderSystem.disableBlend();
                view.scale(4096.0f);
            }
            FallenStarSkyLayer.draw(view, projection, level.getGameTime() + (double) partialTick,
                    UniverseClient.fieldSeed(), density, visibility);
        } finally {
            com.mojang.blaze3d.vertex.VertexBuffer.unbind();
            RenderSystem.depthMask(depthWrite);
            RenderSystem.depthFunc(depthFunction);
            if (depthClamp) GL11.glEnable(GL32.GL_DEPTH_CLAMP); else GL11.glDisable(GL32.GL_DEPTH_CLAMP);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        FallenStarSkyLayer.close();
    }
}
