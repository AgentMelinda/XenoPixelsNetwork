package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.SpaceAtmosphereSettingsPacket;
import net.bullettrain.xenopixelsmod.space.math.SkyTransition;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client presentation of the server-resolved atmosphere transition for the current dimension. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class SpaceAtmosphereClient {
    private static SpaceAtmosphereSettingsPacket current;

    private SpaceAtmosphereClient() {}

    public static void apply(SpaceAtmosphereSettingsPacket packet) {
        current = packet;
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) return;
        SpaceAtmosphereSettingsPacket settings = currentSettings();
        if (settings == null || !settings.enabled()) return;
        float progress = progress(event.getCamera().getPosition().y, settings);
        if (progress <= 0.0f) return;
        var color = SkyTransition.fogColor(new SkyTransition.Rgb(event.getRed(), event.getGreen(), event.getBlue()), progress);
        event.setRed(color.red());
        event.setGreen(color.green());
        event.setBlue(color.blue());
    }

    @SubscribeEvent
    public static void renderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != net.minecraft.world.level.material.FogType.NONE) return;
        SpaceAtmosphereSettingsPacket settings = currentSettings();
        if (settings == null || !settings.enabled()) return;
        float progress = progress(event.getCamera().getPosition().y, settings);
        if (progress <= 0.0f) return;
        // NeoForge applies modified fog distances only when the event is canceled.
        event.scaleFarPlaneDistance(1.0f + progress * 0.65f);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void renderSkyLayer(RenderLevelStageEvent event) {
        boolean shaderPack = IrisShaderState.packInUse();
        RenderLevelStageEvent.Stage wanted = shaderPack
                ? RenderLevelStageEvent.Stage.AFTER_LEVEL : RenderLevelStageEvent.Stage.AFTER_SKY;
        if (event.getStage() != wanted || shaderPack && IrisShaderState.renderingShadows()) return;
        SpaceAtmosphereSettingsPacket settings = currentSettings();
        if (settings == null || !settings.enabled()) return;
        float progress = progress(event.getCamera().getPosition().y, settings);
        if (progress <= 0.0f) return;
        if (shaderPack) Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        SpaceSkyEffects.drawAtmosphereTransition(event.getModelViewMatrix(), event.getProjectionMatrix(), progress, shaderPack);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        current = null;
    }

    private static SpaceAtmosphereSettingsPacket currentSettings() {
        var level = Minecraft.getInstance().level;
        SpaceAtmosphereSettingsPacket settings = current;
        if (level == null || settings == null || !settings.dimension().equals(level.dimension().location())) return null;
        if (settings.cellBound()) {
            var player = Minecraft.getInstance().player;
            if (player == null) return null;
            long cellX = Math.floorDiv((long) player.blockPosition().getX(),
                    net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells.CELL_SIZE);
            long cellZ = Math.floorDiv((long) player.blockPosition().getZ(),
                    net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells.CELL_SIZE);
            if (cellX != settings.surfaceCellX() || cellZ != settings.surfaceCellZ()) return null;
        }
        return settings;
    }

    private static float progress(double cameraY, SpaceAtmosphereSettingsPacket settings) {
        return SkyTransition.factor(cameraY, settings.startAltitude(), settings.endAltitude());
    }
}
