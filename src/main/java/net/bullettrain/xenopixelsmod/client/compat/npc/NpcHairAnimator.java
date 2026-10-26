package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.dragonminez.client.render.hair.HairEntityState;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.hair.HairStyleSlot;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-NPC DMZ 2.2 simulation state; no synthetic player or capability is needed. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
final class NpcHairAnimator {
    record Input(HairEntityState state, boolean simulated) {}
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final class State {
        final HairEntityState hair = new HairEntityState();
        final HairEntityState staticHair = new HairEntityState();
        float charge;
        long lastUpdateNanos;
        long lastSeenGameTime;
    }
    private NpcHairAnimator() {}

    static Input input(LivingEntity owner, PoseStack pose, float partialTick,
                       CustomHair from, CustomHair to, float progress,
                       HairStyleSlot fromSlot, HairStyleSlot toSlot) {
        State state = STATES.computeIfAbsent(owner.getUUID(), id -> new State());
        Minecraft mc = Minecraft.getInstance();
        double distanceSqr = mc.getCameraEntity() == null ? 0.0 : owner.distanceToSqr(mc.getCameraEntity());
        String quality = ConfigManager.getUserConfig().getHairPhysicsQuality();
        int substeps = "OFF".equals(quality) || distanceSqr > 2304.0 ? 0
                : "LOW".equals(quality) || distanceSqr > 576.0 ? 1 : 4;
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        boolean perspective = projection.m23() != 0.0f && projection.m33() == 0.0f;
        HairEntityState hair = state.hair;
        // Shadow/portrait passes must not advance physics or replace the world simulation inputs.
        if (!perspective && !hair.matchesInputs(from, to, progress, fromSlot, toSlot)) {
            hair = state.staticHair;
        }
        if (perspective || hair == state.staticHair) hair.updatePoses(from, to, progress, fromSlot, toSlot);
        if (perspective && substeps == 0) hair.invalidateSimulation();
        if (perspective && substeps > 0) {
            long now = Util.getNanos();
            float dt = state.lastUpdateNanos == 0L ? 1.0f
                    : Mth.clamp((now - state.lastUpdateNanos) / 50_000_000.0f, 0.0f, 2.0f);
            state.lastUpdateNanos = now;
            boolean aura = NpcAuraClient.isActive(owner.getUUID());
            state.charge = Mth.clamp(state.charge + (aura ? dt * 0.25f : -dt * 0.15f), 0.0f, 1.0f);
            // The supplied head pose is in view space. Undo the camera rotation and translation
            // to obtain the same entity-relative world frame DMZHairLayer gives its solver.
            var camera = mc.gameRenderer.getMainCamera();
            var anchor = owner.getPosition(partialTick);
            var cameraPos = camera.getPosition();
            Matrix4f head = headToAnchor(pose.last().pose(), camera.rotation(), cameraPos, anchor);
            // Overlay models have no shared GeckoLib collider capture. Leave optional boxes absent.
            hair.simulate(head, anchor.x, anchor.y, anchor.z, now, state.charge, aura ? 1.0f : 0.0f, substeps);
        }
        state.lastSeenGameTime = owner.level().getGameTime();
        if ((state.lastSeenGameTime & 127L) == 0L && STATES.size() >= 16) {
            long gameTime = state.lastSeenGameTime;
            STATES.entrySet().removeIf(entry -> gameTime - entry.getValue().lastSeenGameTime > 200L);
        }
        return new Input(hair, substeps > 0 && hair == state.hair);
    }

    /** Equivalent to inverse(entity base view pose) * head view pose, as used by DMZ. */
    static Matrix4f headToAnchor(Matrix4f headInView, Quaternionfc cameraRotation,
                                Vec3 camera, Vec3 anchor) {
        return new Matrix4f().translation((float) (camera.x - anchor.x),
                (float) (camera.y - anchor.y), (float) (camera.z - anchor.z))
                .rotate(cameraRotation).mul(headInView);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { STATES.clear(); }
}
