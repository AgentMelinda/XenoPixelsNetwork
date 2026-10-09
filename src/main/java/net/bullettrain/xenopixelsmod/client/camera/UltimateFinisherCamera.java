package net.bullettrain.xenopixelsmod.client.camera;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisherRules;
import net.bullettrain.xenopixelsmod.network.packet.UltimateFinisherCameraPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.bus.api.EventPriority;

/** Stage-specific side, arc and over-shoulder shots, with wall clipping and view restoration. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class UltimateFinisherCamera {
    public record Shot(Vec3 position, float yaw, float pitch) {}
    private static UltimateFinisherCameraPacket state;
    private static CameraType previous;
    private static ClientLevel level;
    private static int expires;
    private static Vec3 lastPosition;
    private UltimateFinisherCamera() {}

    public static void apply(UltimateFinisherCameraPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (packet.phase() == UltimateFinisherRules.Phase.STOP) {
            if (state != null && state.sequence() == packet.sequence()) clear();
            return;
        }
        if (mc.player == null || mc.level == null || ContraptionControlCamera.active()) return;
        // Release V3's perspective before this camera captures the user's prior view.
        V3TechniqueCamera.clear();
        if (state == null) {
            previous = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            lastPosition = null;
        }
        state = packet;
        level = mc.level;
        expires = mc.player.tickCount + 260;
    }

    public static boolean active() { return state != null; }

    public static Shot shot(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (state == null || mc.player == null || mc.level != level || ContraptionControlCamera.active()) return null;
        Entity target = mc.level.getEntity(state.targetId());
        if (target == null) return null;
        Vec3 attacker = mc.player.getPosition(partialTick).add(0, mc.player.getBbHeight() * 0.7, 0);
        Vec3 victim = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0);
        Vec3 forward = victim.subtract(attacker).multiply(1, 0, 1);
        if (forward.lengthSqr() < 1e-6) forward = new Vec3(0, 0, 1);
        forward = forward.normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        Vec3 focus;
        Vec3 wanted;
        switch (state.phase()) {
            case APPROACH -> {
                focus = attacker.add(forward.scale(3));
                wanted = attacker.subtract(forward.scale(5)).add(right.scale(2.5)).add(0, 1.4, 0);
            }
            case COMBO, GRAB -> {
                focus = attacker.lerp(victim, 0.5);
                wanted = focus.add(right.scale(4.5)).subtract(forward.scale(1.5)).add(0, 1.1, 0);
            }
            case THROW -> {
                focus = attacker.lerp(victim, 0.55);
                wanted = focus.add(right.scale(7)).subtract(forward.scale(3)).add(0, 3, 0);
            }
            default -> {
                focus = victim;
                wanted = attacker.subtract(forward.scale(3.5)).add(right.scale(2)).add(0, 1, 0);
            }
        }
        Vec3 position = lastPosition == null ? wanted : lastPosition.lerp(wanted, 0.22);
        HitResult wall = mc.level.clip(new ClipContext(attacker, position, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, mc.player));
        if (wall.getType() != HitResult.Type.MISS) position = wall.getLocation().lerp(attacker, 0.1);
        lastPosition = position;
        Vec3 look = focus.subtract(position);
        return new Shot(position, (float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90),
                (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z))));
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (state != null && (mc.player == null || mc.level != level || !mc.player.isAlive()
                || mc.player.tickCount >= expires || mc.player.isPassenger() || ContraptionControlCamera.active())) clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active() || event.getEntity() != Minecraft.getInstance().player) return;
        // Server velocity drives the approach, then holds the caster still. Do not add input drift.
        event.getInput().forwardImpulse = 0;
        event.getInput().leftImpulse = 0;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    private static void clear() {
        Minecraft mc = Minecraft.getInstance();
        if (previous != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(previous);
        state = null;
        previous = null;
        level = null;
        lastPosition = null;
    }
}
