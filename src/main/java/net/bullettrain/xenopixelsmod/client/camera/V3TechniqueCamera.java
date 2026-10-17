package net.bullettrain.xenopixelsmod.client.camera;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState;
import net.bullettrain.xenopixelsmod.combat.v3.V3State;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3CameraBeat;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3CameraPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/** Plays server-owned technique and dash cameras, synchronized to the admitted fighter state. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class V3TechniqueCamera {
    public record Shot(Vec3 position, float yaw, float pitch) {}
    private static final V3CameraSession SESSION = new V3CameraSession();
    private static CameraType previous;
    private static ClientLevel level;
    private static int receivedTick;
    private static int expires;
    /** Frozen attacker→victim horizontal basis so rush APPROACH cannot thrash the cinematic. */
    private static Vec3 frozenForward;
    private static Vec3 wideFallbackDirection;
    private static final CinematicCameraMotion MOTION = new CinematicCameraMotion();
    private V3TechniqueCamera() {}

    public static void apply(CombatV3CameraPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        var target = V3ClientState.target();
        boolean allowed = mc.player != null && mc.level != null && XenoServerClientState.v3Controller()
                && !ContraptionControlCamera.active() && !UltimateFinisherCamera.active();
        boolean newCast = SESSION.active() == null;
        if (!SESSION.apply(packet, V3ClientState.session(), target == null ? null : target.target(),
                allowed && cameraState())) return;
        if (SESSION.active() == null) { clear(); return; }
        if (previous == null) {
            previous = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        level = mc.level;
        receivedTick = mc.player.tickCount;
        expires = receivedTick + packet.durationTicks() - packet.elapsedTicks()
                + Math.max(120, V3ClientState.config().strikeCameraHoldTicks() + 20);
        // Capture a stable basis once per cast; updating every frame during rush flickered with lock-on.
        if (!newCast) return;
        MOTION.reset();
        frozenForward = null;
        wideFallbackDirection = null;
        if (mc.player != null && target != null) {
            Entity entity = mc.level.getEntity(packet.targetId());
            if (entity != null) {
                Vec3 delta = entity.position().subtract(mc.player.position()).multiply(1, 0, 1);
                if (delta.lengthSqr() > 1.0e-4) frozenForward = delta.normalize();
            }
        }
    }

    public static boolean active() { return SESSION.active() != null; }

    public static Shot shot(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        CombatV3CameraPacket packet = SESSION.active();
        if (packet == null) return null;
        if (!valid(mc, packet)) { clear(); return null; }
        Entity target = mc.level.getEntity(packet.targetId());
        if (target == null || !packet.target().equals(target.getUUID()) || !target.isAlive()) {
            clear(); return null;
        }
        double elapsed = packet.elapsedTicks() + (packet.paused() ? 0 : mc.player.tickCount - receivedTick + partialTick);
        if (elapsed >= packet.durationTicks()) {
            if (!packet.paused()) { clear(); return null; }
            elapsed = packet.durationTicks() - .001;
        }
        V3CameraBeat current = null, prior = null;
        for (V3CameraBeat beat : packet.beats()) {
            if (elapsed < beat.tick()) break;
            prior = current;
            current = beat;
        }
        if (current == null) return null;
        double t = current.progress(elapsed);
        Vec3 offset = prior == null ? current.position() : prior.position().lerp(current.position(), t);
        offset = offset.scale(V3ClientState.config().cinematicCameraDistanceScale());
        Vec3 lookOffset = prior == null ? current.look() : prior.look().lerp(current.look(), t);
        double focusWeight = prior == null ? current.focus() : prior.focus() + (current.focus() - prior.focus()) * t;
        Vec3 attacker = mc.player.getPosition(partialTick).add(0, mc.player.getBbHeight() * 0.65, 0);
        Vec3 victim = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0);
        // The cast basis stays fixed across actor crossings and pause/resume packets.
        if (frozenForward == null) {
            Vec3 delta = victim.subtract(attacker).multiply(1, 0, 1);
            frozenForward = delta.lengthSqr() > 1e-6 ? delta.normalize() : new Vec3(0, 0, 1);
        }
        Vec3 forward = frozenForward;
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        else right = right.normalize();
        Vec3 focus = attacker.lerp(victim, focusWeight);
        Vec3 position = focus.add(transform(offset, forward, right));
        Vec3 lookAt = focus.add(transform(lookOffset, forward, right));
        // Wide action/launch shots must contain both actors even when a shove
        // changes their horizontal separation or height. Opening closeups keep
        // their authored focus. UltimateFinisher uses its own camera controller.
        boolean firing = net.bullettrain.xenopixelsmod.client.combat.v3.ClientCastPresentation
                .firing(mc.player.getUUID(), packet.cast());
        // Focus interpolation describes the look point, not whether the shot is an action
        // wide. Evil Flames was already releasing while its blended focus was only .35.
        boolean wide = V3CameraFraming.shouldFitBoth(current.focus(), attacker.distanceTo(victim), firing);
        if (wide) {
            if (wideFallbackDirection != null)
                position = lookAt.add(wideFallbackDirection.scale(position.distanceTo(lookAt)));
            var framed = V3CameraFraming.fit(attacker, mc.player.getBbHeight(), victim, target.getBbHeight(),
                    position, lookAt, Math.min(70, mc.options.fov().get()),
                    mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight()));
            position = framed.position();
            focus = framed.lookAt();
            lookAt = framed.lookAt();
        }
        var smoothed = MOTION.update(position, lookAt, mc.player.tickCount + partialTick);
        position = smoothed.position();
        lookAt = smoothed.focus();
        if (wide) {
            // Damping can move the rendered position inside the required distance after a
            // launch; enforce framing on the final smoothed shot as well.
            var framed = V3CameraFraming.fit(attacker, mc.player.getBbHeight(), victim, target.getBbHeight(),
                    position, lookAt, Math.min(70, mc.options.fov().get()),
                    mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight()));
            position = framed.position(); lookAt = framed.lookAt();
        }
        // Clip the damped shot once. Switching to an elevated alternative every frame caused
        // the reported up/down jumping at terrain edges.
        HitResult wall = mc.level.clip(new ClipContext(lookAt, position, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, mc.player));
        if (wall.getType() != HitResult.Type.MISS && wide) {
            // Choose a horizontal alternative once per cast rather than oscillating up/down
            // at terrain edges. Only accept a ray-clear shot that still fits both fighters.
            Vec3[] alternatives = {right.add(0,.25,0), right.scale(-1).add(0,.25,0),
                    forward.add(0,.25,0), forward.scale(-1).add(0,.25,0)};
            for (Vec3 direction : alternatives) {
                var frame = V3CameraFraming.fit(attacker, mc.player.getBbHeight(), victim, target.getBbHeight(),
                        lookAt.add(direction.normalize().scale(position.distanceTo(lookAt))), lookAt,
                        Math.min(70, mc.options.fov().get()),
                        mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight()));
                HitResult alternative = mc.level.clip(new ClipContext(frame.lookAt(), frame.position(),
                        ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
                if (alternative.getType() == HitResult.Type.MISS) {
                    wideFallbackDirection = direction.normalize(); position = frame.position();
                    lookAt = frame.lookAt(); wall = alternative; break;
                }
            }
        }
        if (wall.getType() != HitResult.Type.MISS) position = wall.getLocation().lerp(lookAt, 0.1);
        Vec3 look = lookAt.subtract(position);
        return new Shot(position, (float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90),
                (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z))));
    }

    private static Vec3 transform(Vec3 offset, Vec3 forward, Vec3 right) {
        return right.scale(offset.x).add(0, offset.y, 0).add(forward.scale(offset.z));
    }
    private static boolean valid(Minecraft mc, CombatV3CameraPacket packet) {
        var target = V3ClientState.target();
        return mc.player != null && mc.level == level && mc.player.isAlive() && !mc.player.isPassenger()
                && mc.player.tickCount < expires && XenoServerClientState.v3Controller()
                && packet.session().equals(V3ClientState.session()) && target != null
                && packet.target().equals(target.target()) && cameraState()
                && !ContraptionControlCamera.active() && !UltimateFinisherCamera.active();
    }
    private static boolean cameraState() {
        var state = V3ClientState.state();
        return V3ClientState.fighterState() == V3State.CINEMATIC
                || state != null && state.window() == net.bullettrain.xenopixelsmod.combat.v3.V3Window.DASH_CROSS
                && (state.state() == V3State.TRAVEL || state.state() == V3State.IDLE && state.windowTicksLeft() > 0);
    }
    @SubscribeEvent public static void onTick(ClientTickEvent.Post event) {
        if (active() && !valid(Minecraft.getInstance(), SESSION.active())) clear();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMovement(MovementInputUpdateEvent event) {
        if (!(XenoServerClientState.v3Controller()
                && V3ClientState.fighterState() == V3State.CINEMATIC)
                || event.getEntity() != Minecraft.getInstance().player) return;
        event.getInput().forwardImpulse = 0; event.getInput().leftImpulse = 0;
        event.getInput().jumping = false; event.getInput().shiftKeyDown = false;
    }
    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear(); SESSION.reset();
    }
    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        if (previous != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(previous);
        }
        SESSION.clear(); previous = null; level = null; frozenForward = null; wideFallbackDirection = null; MOTION.reset();
    }
}
