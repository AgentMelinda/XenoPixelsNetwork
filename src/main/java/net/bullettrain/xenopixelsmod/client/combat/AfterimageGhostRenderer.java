package net.bullettrain.xenopixelsmod.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the body a fighter left behind when they Zanzoken out of a hit.
 *
 * <p>The image is the fighter themselves, re-rendered at the position they vanished from. Going
 * through {@link net.minecraft.client.renderer.entity.EntityRenderDispatcher} means whatever
 * renderer is registered for that player draws it — which, with DragonMineZ installed, is DMZ's
 * own player renderer, so the copy arrives wearing their hair, active form, aura and race parts
 * with no appearance code of our own. The same approach {@code NpcFullDmzRenderer} already uses to
 * give NPCs a DMZ body.
 *
 * <p>Ghosts are purely visual and purely local: no entity is spawned, nothing is ticked on the
 * server, and a client that drops the packet simply does not see one.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class AfterimageGhostRenderer {

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    /** Bounded so a burst of dodges cannot turn into a crowd. */
    private static final int MAX_GHOSTS = 8;

    private static final class Ghost {
        final int ownerId;
        final Vec3 pos;
        final float yaw;
        final float pitch;
        final int lifetime;
        int age;

        Ghost(int ownerId, Vec3 pos, float yaw, float pitch, int lifetime) {
            this.ownerId = ownerId;
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.lifetime = Math.max(1, lifetime);
        }
    }

    private AfterimageGhostRenderer() {
    }

    public static void add(int ownerId, Vec3 pos, float yaw, float pitch, int lifetimeTicks) {
        if (!XenoClientConfig.bt3Afterimage) return;
        synchronized (GHOSTS) {
            if (GHOSTS.size() >= MAX_GHOSTS) {
                GHOSTS.remove(0);
            }
            GHOSTS.add(new Ghost(ownerId, pos, yaw, pitch, lifetimeTicks));
        }
    }

    public static void clear() {
        synchronized (GHOSTS) {
            GHOSTS.clear();
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!event.getLevel().isClientSide()) return;
        synchronized (GHOSTS) {
            GHOSTS.removeIf(ghost -> ++ghost.age >= ghost.lifetime);
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<Ghost> snapshot;
        synchronized (GHOSTS) {
            if (GHOSTS.isEmpty()) return;
            snapshot = new ArrayList<>(GHOSTS);
        }

        Camera camera = event.getCamera();
        Vec3 view = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = mc.renderBuffers().bufferSource();

        for (Ghost ghost : snapshot) {
            Entity owner = mc.level.getEntity(ghost.ownerId);
            if (!(owner instanceof LivingEntity living)) continue;

            // The owner is re-rendered at the image's position, so their pose is momentarily
            // moved and put straight back. Anything that reads the live entity between these two
            // lines would see the wrong place, which is why nothing else happens in between.
            double keepX = living.getX();
            double keepY = living.getY();
            double keepZ = living.getZ();
            float keepYaw = living.getYRot();
            float keepBody = living.yBodyRot;
            float keepPitch = living.getXRot();
            try {
                living.setPos(ghost.pos.x, ghost.pos.y, ghost.pos.z);
                living.setYRot(ghost.yaw);
                living.yBodyRot = ghost.yaw;
                living.setXRot(ghost.pitch);

                pose.pushPose();
                try {
                    pose.translate(ghost.pos.x - view.x, ghost.pos.y - view.y, ghost.pos.z - view.z);
                    float alpha = AfterimageFade.alpha(XenoServerConfig.zanzokenGhostFadeMode,
                            XenoServerConfig.zanzokenGhostAlpha,
                            ghost.age + event.getPartialTick().getGameTimeDeltaPartialTick(false),
                            ghost.lifetime);
                    mc.getEntityRenderDispatcher().render(living, 0.0, 0.0, 0.0, ghost.yaw,
                            event.getPartialTick().getGameTimeDeltaPartialTick(false),
                            pose, new AlphaMultiBufferSource(buffers, alpha), 0x00F000F0);
                } finally {
                    pose.popPose();
                }
            } catch (Throwable t) {
                // A renderer that refuses to draw an entity out of place must not take the frame
                // down with it. The dodge itself is server-side and already happened.
            } finally {
                living.setPos(keepX, keepY, keepZ);
                living.setYRot(keepYaw);
                living.yBodyRot = keepBody;
                living.setXRot(keepPitch);
            }
        }
        mc.renderBuffers().bufferSource().endBatch();
    }
}
