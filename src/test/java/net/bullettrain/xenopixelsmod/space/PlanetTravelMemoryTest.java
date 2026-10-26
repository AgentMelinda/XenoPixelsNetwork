package net.bullettrain.xenopixelsmod.space;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanetTravelMemoryTest {
    @Test void planetsAndRecordsStayIndependentAndSourceNbtIsPreserved() {
        CompoundTag data = new CompoundTag();
        data.putString("OtherMod", "keep");
        PlanetTravelMemory.leaving(data, "xenopixelsmod:earth", new Vec3(5400.25, 241.5, -3000.75));
        PlanetTravelMemory.entrance(data, "xenopixelsmod:earth", new Vec3(5400.25, 215, -3000.75));
        PlanetTravelMemory.leaving(data, "xenospace_spike:moon_surface", new Vec3(-60, 701, 45));
        assertEquals(new Vec3(5400.25, 241.5, -3000.75), PlanetTravelMemory.lastLeaving(data, "xenopixelsmod:earth"));
        assertEquals(new Vec3(5400.25, 215, -3000.75), PlanetTravelMemory.lastEntrance(data, "xenopixelsmod:earth"));
        assertEquals(new Vec3(-60, 701, 45), PlanetTravelMemory.lastLeaving(data, "xenospace_spike:moon_surface"));
        assertEquals("keep", data.getString("OtherMod"));
        assertEquals(PlanetTravelMemory.lastLeaving(data, "xenopixelsmod:earth"),
                PlanetTravelMemory.lastLeaving(data.copy(), "xenopixelsmod:earth"));
    }
    @Test void returnRestoresHorizontalDepartureButUsesCurrentExitMinus25() {
        CompoundTag data = new CompoundTag();
        assertEquals(new Vec3(0, 675, 0), PlanetTravelMemory.arrival(data, "xenopixelsmod:earth", 700, -64));
        PlanetTravelMemory.entrance(data, "xenopixelsmod:earth", new Vec3(10, 675, -20));
        assertEquals(new Vec3(10, 215, -20), PlanetTravelMemory.arrival(data, "xenopixelsmod:earth", 240, -64));
        PlanetTravelMemory.leaving(data, "xenopixelsmod:earth", new Vec3(2000, 704, -1500));
        assertEquals(new Vec3(2000, 675, -1500), PlanetTravelMemory.arrival(data, "xenopixelsmod:earth", 700, -64));
        assertNull(PlanetTravelMemory.arrival(data, "xenopixelsmod:earth", -50, -64));
    }
    @Test void firstReturnUsesChartSpatialAnchorAndSavedPositionOverridesIt() {
        CompoundTag data = new CompoundTag();
        assertEquals(new Vec3(8192.5, 675, -16384.5),
                PlanetTravelMemory.arrival(data, "xenopixelsmod:moon", 700, -1024, 8192.5, -16384.5));
        PlanetTravelMemory.entrance(data, "xenopixelsmod:moon", new Vec3(8210, 300, -16370));
        assertEquals(new Vec3(8210, 675, -16370),
                PlanetTravelMemory.arrival(data, "xenopixelsmod:moon", 700, -1024, 8192.5, -16384.5));
        assertEquals(new Vec3(8210, 675, -16370),
                PlanetTravelMemory.arrival(data, "xenopixelsmod:moon", 700, -1024, Double.NaN, -16384.5));
        assertNull(PlanetTravelMemory.arrival(new CompoundTag(), "xenopixelsmod:moon", 700, -1024,
                Double.NaN, -16384.5));
    }
    @Test void authoredSurfaceAnchorIsUsedOnlyWhenNoPlayerPositionWasRemembered() {
        CompoundTag data = new CompoundTag();
        Vec3 authored = new Vec3(12.5, 64.0, -8.0);
        assertEquals(authored, PlanetTravelMemory.arrival(data, "demo:namek", 700, -64, 384,
                0.0, 0.0, authored));
        PlanetTravelMemory.leaving(data, "demo:namek", new Vec3(30, 211, 40));
        assertEquals(new Vec3(30, 675, 40), PlanetTravelMemory.arrival(data, "demo:namek", 700, -64, 384,
                0.0, 0.0, authored), "remembered horizontal location retains the existing exit-minus-25 policy");
        assertNull(PlanetTravelMemory.arrival(new CompoundTag(), "demo:namek", 700, -64, 384,
                0.0, 0.0, new Vec3(0, 384, 0)), "anchor Y must be inside the dimension build range");
    }
    @Test void invalidInputDoesNotOverwriteAnExistingPosition() {
        CompoundTag data = new CompoundTag();
        var point = new Vec3(4, 241, 9);
        PlanetTravelMemory.leaving(data, "xenopixelsmod:earth", point);
        assertThrows(IllegalArgumentException.class, () -> PlanetTravelMemory.leaving(data, "xenopixelsmod:earth", new Vec3(Double.NaN, 0, 0)));
        assertEquals(point, PlanetTravelMemory.lastLeaving(data, "xenopixelsmod:earth"));
        assertNull(PlanetTravelMemory.lastLeaving(new CompoundTag(), "xenopixelsmod:earth"));
    }
}
