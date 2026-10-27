package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Replaces a shader pack's background before celestial overlays, for every dimension using our sky effect. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class SpaceShaderSkyRenderer {
    private SpaceShaderSkyRenderer() {}

    static boolean shouldHandle(boolean pack, boolean shadows, boolean ownsSky) {
        return pack && !shadows && ownsSky;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || !shouldHandle(IrisShaderState.packInUse(), IrisShaderState.renderingShadows(),
                mc.level.effects() instanceof SpaceSkyEffects)) return;
        mc.getMainRenderTarget().bindWrite(false);
        // Let overPack process OFF too: it releases buffers on a mode switch before skipping the draw.
        SpaceSkyEffects.drawOverShaderPackSky(event.getModelViewMatrix(), event.getProjectionMatrix(), mc.level,
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
}
