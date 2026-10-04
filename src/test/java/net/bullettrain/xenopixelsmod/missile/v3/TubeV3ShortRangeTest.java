package net.bullettrain.xenopixelsmod.missile.v3;

import net.bullettrain.xenopixelsmod.missile.BallisticCalculator;
import net.bullettrain.xenopixelsmod.missile.MissileEject;
import net.bullettrain.xenopixelsmod.missile.MissileFlightSim;
import net.bullettrain.xenopixelsmod.missile.MissileSpeed;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-09-28 owner: a block about 10 blocks away must be hittable, from any tube orientation. */
class TubeV3ShortRangeTest {
    static boolean hits(Vec3 axis, Vec3 offset, int launchY, int speed) {
        Vec3 launch = new Vec3(0, 164, 0);
        Vec3 target = launch.add(offset);
        TubeV3 v3 = TubeV3.plan(launch, target, MissileSpeed.accelFor(speed), MissileSpeed.ticksFor(speed),
                BallisticCalculator.EARTH_GRAVITY, BallisticCalculator.DEFAULT_DRAG, 0, 0, true, 24, axis);
        Vec3 pos = launch;
        Vec3 vel = axis;
        int age = 0;
        while (!MissileEject.finished(age, launch, pos, axis, 24, launchY)) {
            age++;
            vel = axis.scale(1.2 + Math.min(age, 40) * 0.15);
            pos = pos.add(vel);
        }
        for (int t = 0; t < 6000; t++) {
            vel = v3.step(pos, vel, target);
            Vec3 prev = pos;
            pos = pos.add(vel);
            if (MissileFlightSim.reachedTarget(prev, pos, target.x, target.y, target.z, 1.5)) return true;
            if (pos.y < target.y - 40) return false;
        }
        return false;
    }

    @Test
    void closeTargetsAreHitFromUpwardAndSidewaysTubes() {
        Vec3[] axes = {new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        Vec3[] offsets = {new Vec3(10, 0, 0), new Vec3(-10, 0, 0), new Vec3(0, -10, 5), new Vec3(3, -30, 8),
                new Vec3(8, 5, 0)};
        for (Vec3 axis : axes)
            for (Vec3 off : offsets)
                for (int launchY : new int[] {0, 240})
                    for (int speed : new int[] {1, 10, 20})
                        assertTrue(hits(axis, off, launchY, speed),
                                "axis " + axis + " target +" + off + " launch height " + launchY + " speed " + speed);
    }
}
