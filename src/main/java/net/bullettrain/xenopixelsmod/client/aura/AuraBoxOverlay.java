package net.bullettrain.xenopixelsmod.client.aura;

import com.dragonminez.common.stats.StatsData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.dmz.DmzAccess;
import net.bullettrain.xenopixelsmod.fx.aura.HdAuraPlan;
import net.bullettrain.xenopixelsmod.mixin.compat.dmz.DmzAuraLayersInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * {@code /xenoaura box on}: a wire box round your own character at the bounds DragonMineZ's aura
 * flame has right now ({@link HdAuraPlan#dmzFlameBox}), from the same aura scale DMZ draws with.
 * With {@code /xenoaura both} DMZ's aura, the HD aura and the box are on screen together, so
 * whether the HD aura is the right size and starts in the right place can be seen rather than
 * argued from numbers. Visual only, this client only, not saved.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class AuraBoxOverlay {
    public static volatile boolean enabled;

    private AuraBoxOverlay() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.getCameraType().isFirstPerson()) return;
        StatsData stats = DmzAccess.stats(player).orElse(null);
        if (stats == null || stats.getCharacter() == null) return;
        float[] box;
        try {
            float[] scale = DmzAuraLayersInvoker.xenopixels$auraScale(stats,
                    DmzAuraLayersInvoker.xenopixels$modelScale(stats));
            box = HdAuraPlan.dmzFlameBox(scale[0], scale[1]);
        } catch (RuntimeException | LinkageError e) {
            enabled = false;
            XenoPixelsMod.LOGGER.warn("Aura box off: DragonMineZ's aura scale could not be read ({})", e.toString());
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        double px = Mth.lerp(partial, player.xo, player.getX());
        double py = Mth.lerp(partial, player.yo, player.getY());
        double pz = Mth.lerp(partial, player.zo, player.getZ());
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(px - camera.x, py - camera.y, pz - camera.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        double half = box[2] / 2.0;
        LevelRenderer.renderLineBox(pose, lines, new AABB(-half, box[0], -half, half, box[1], half),
                1.0f, 1.0f, 0.2f, 1.0f);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
}
