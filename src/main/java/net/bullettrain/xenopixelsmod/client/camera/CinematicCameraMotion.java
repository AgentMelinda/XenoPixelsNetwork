package net.bullettrain.xenopixelsmod.client.camera;

import net.minecraft.world.phys.Vec3;

/** Simulation-time damping shared by cinematic cameras; rendering twice cannot advance it twice. */
public final class CinematicCameraMotion {
    public record Frame(Vec3 position, Vec3 focus) {}
    private Frame last;
    private double time;

    public Frame update(Vec3 position, Vec3 focus, double tickTime) {
        if (last == null || tickTime < time) {
            time = tickTime;
            return last = new Frame(position, focus);
        }
        double seconds = Math.min(.25, Math.max(0, (tickTime - time) / 20));
        time = tickTime;
        double alpha = 1 - Math.exp(-seconds / .12);
        Vec3 next = last.position().lerp(position, alpha);
        // A changing terrain ray must not instantly raise/lower the camera by several blocks.
        double y = last.position().y + Math.clamp(next.y - last.position().y, -12 * seconds, 12 * seconds);
        return last = new Frame(new Vec3(next.x, y, next.z), last.focus().lerp(focus, alpha));
    }

    public void reset() { last = null; }
}
