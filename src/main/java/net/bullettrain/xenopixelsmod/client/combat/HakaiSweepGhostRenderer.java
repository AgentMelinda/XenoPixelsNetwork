package net.bullettrain.xenopixelsmod.client.combat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.bullettrain.xenopixelsmod.combat.HakaiSweepGhosts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client half of the Hakai block sweep: blocks the server already erased stay visible as "ghosts" that the descending
 * plane cuts away (geometry clipped by {@link HakaiClipMultiBufferSource}, so the cut is a clean line through each
 * block), with a glowing violet sheet where the plane crosses them. Sable ship blocks arrive as plot positions and are
 * drawn with their ship's live render pose, so they vanish in place even while the ship moves.
 */
@EventBusSubscriber(modid = "xenopixelsmod", value = Dist.CLIENT)
public final class HakaiSweepGhostRenderer {
    private record Ghost(BlockPos pos, BlockState state) {}

    private static final class Sweep {
        final long startTick;
        final int durationTicks;
        final float topY, bottomY;
        final List<Ghost> ghosts = new ArrayList<>();

        Sweep(HakaiSweepGhosts packet) {
            startTick = packet.startTick(); durationTicks = packet.durationTicks(); topY = packet.topY(); bottomY = packet.bottomY();
        }
    }

    private static final Map<Integer, Sweep> SWEEPS = new HashMap<>();
    private static final int MAX_GHOSTS = 16_384;
    /** Ghosts drawn per frame (nearest to the camera); far ones in a huge raze just vanish as the plane passes. */
    private static final int DRAW_CAP = 1_200;

    private HakaiSweepGhostRenderer() {}

    public static void apply(HakaiSweepGhosts packet) {
        Sweep sweep = SWEEPS.computeIfAbsent(packet.jobId(), id -> new Sweep(packet));
        for (var g : packet.ghosts()) {
            if (sweep.ghosts.size() >= MAX_GHOSTS) break;
            BlockState state = Block.stateById(g.stateId());
            if (!state.isAir()) sweep.ghosts.add(new Ghost(g.pos(), state));
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || SWEEPS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        var camera = event.getCamera().getPosition();
        var dispatcher = mc.getBlockRenderer();
        var buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        BufferBuilder sheet = null;
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        try {
            for (Iterator<Sweep> sweeps = SWEEPS.values().iterator(); sweeps.hasNext(); ) {
                Sweep sweep = sweeps.next();
                float cut = HakaiSweepGhosts.cutY(sweep.startTick, sweep.durationTicks, sweep.topY, sweep.bottomY, now);
                // Vertices are camera-relative and unrotated here (the model-view matrix carries the camera rotation).
                var clip = new HakaiClipMultiBufferSource(buffers, cut, (float) camera.y, 0f, 0f, 0f, 1f);
                // World blocks the plane has fully passed are dropped first; then only the nearest DRAW_CAP are drawn.
                sweep.ghosts.removeIf(g -> shipOf(mc, g.pos()) == null && g.pos().getY() >= cut);
                List<Ghost> drawn = sweep.ghosts;
                if (drawn.size() > DRAW_CAP) {
                    drawn = new ArrayList<>(drawn);
                    drawn.sort(java.util.Comparator.comparingDouble(g -> g.pos().distToCenterSqr(camera.x, camera.y, camera.z)));
                    drawn = drawn.subList(0, DRAW_CAP);
                }
                java.util.Set<Ghost> gone = new java.util.HashSet<>();
                for (Iterator<Ghost> it = drawn.iterator(); it.hasNext(); ) {
                    Ghost ghost = it.next();
                    pose.pushPose();
                    double worldBottom;
                    ClientSubLevel ship = shipOf(mc, ghost.pos());
                    if (ship != null) {
                        Matrix4d m = ship.renderPose(partial).bakeIntoMatrix(new Matrix4d());
                        Vector3d centre = m.transformPosition(new Vector3d(ghost.pos().getX() + 0.5, ghost.pos().getY() + 0.5, ghost.pos().getZ() + 0.5));
                        worldBottom = centre.y - 0.5;
                        pose.last().pose().translate((float) -camera.x, (float) -camera.y, (float) -camera.z).mul(new Matrix4f(m))
                                .translate(ghost.pos().getX(), ghost.pos().getY(), ghost.pos().getZ());
                    } else {
                        worldBottom = ghost.pos().getY();
                        pose.translate(ghost.pos().getX() - camera.x, ghost.pos().getY() - camera.y, ghost.pos().getZ() - camera.z);
                    }
                    if (worldBottom >= cut) { pose.popPose(); gone.add(ghost); continue; }
                    int light = LevelRenderer.getLightColor(mc.level, ghost.pos());
                    try {
                        dispatcher.renderSingleBlock(ghost.state(), pose, clip, light, OverlayTexture.NO_OVERLAY);
                    } catch (RuntimeException ignored) {
                        // A block model that cannot render detached is simply not ghosted.
                    }
                    pose.popPose();
                    if (cut < worldBottom + 1.0) {
                        if (sheet == null) sheet = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                        double cx, cz;
                        if (ship != null) {
                            Vector3d c = ship.renderPose(partial).bakeIntoMatrix(new Matrix4d())
                                    .transformPosition(new Vector3d(ghost.pos().getX() + 0.5, ghost.pos().getY() + 0.5, ghost.pos().getZ() + 0.5));
                            cx = c.x; cz = c.z;
                        } else { cx = ghost.pos().getX() + 0.5; cz = ghost.pos().getZ() + 0.5; }
                        glowSheet(sheet, (float) (cx - camera.x), (float) (cut - camera.y), (float) (cz - camera.z), now);
                    }
                }
                if (!gone.isEmpty()) sweep.ghosts.removeAll(gone);
                if (sweep.ghosts.isEmpty() && now > sweep.startTick + sweep.durationTicks + 20) sweeps.remove();
            }
            buffers.endBatch();
            if (sheet != null) {
                var mesh = sheet.build();
                if (mesh != null) {
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    RenderSystem.disableCull();
                    RenderSystem.depthMask(false);
                    BufferUploader.drawWithShader(mesh);
                    RenderSystem.depthMask(true);
                    RenderSystem.enableCull();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.disableBlend();
                }
            }
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** A glowing square slightly larger than the block at the cut height: together they form the sweeping sheet. */
    private static void glowSheet(BufferBuilder b, float x, float y, float z, double now) {
        float pulse = 0.7f + 0.3f * (float) Math.sin(now * 0.7);
        int core = ((int) (150 * pulse) << 24) | (226 << 16) | (150 << 8) | 255;
        float h = 0.53f;
        b.addVertex(x - h, y, z - h).setColor(core);
        b.addVertex(x + h, y, z - h).setColor(core);
        b.addVertex(x + h, y, z + h).setColor(core);
        b.addVertex(x - h, y, z + h).setColor(core);
    }

    private static ClientSubLevel shipOf(Minecraft mc, BlockPos pos) {
        try {
            return Sable.HELPER.getContaining(mc.level, pos) instanceof ClientSubLevel ship && !ship.isRemoved() ? ship : null;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { SWEEPS.clear(); }
}
