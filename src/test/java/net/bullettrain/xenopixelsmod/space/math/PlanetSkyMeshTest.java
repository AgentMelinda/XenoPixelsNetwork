package net.bullettrain.xenopixelsmod.space.math;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanetSkyMeshTest {
    @Test void globeVerticesStayOnASphereAndTrianglesFaceOutward() {
        var mesh = PlanetSkyMesh.create(PlanetSkyMesh.Style.EARTH);
        assertEquals(24 * 64 * 6, mesh.colors().length);
        assertEquals(mesh.colors().length * 3, mesh.positions().length);
        float[] p = mesh.positions();
        for (int i = 0; i < p.length; i += 3) {
            assertEquals(1, Math.sqrt(p[i]*p[i] + p[i+1]*p[i+1] + p[i+2]*p[i+2]), 1e-6);
        }
        for (int i = 0; i < p.length; i += 9) {
            double ux=p[i+3]-p[i], uy=p[i+4]-p[i+1], uz=p[i+5]-p[i+2];
            double vx=p[i+6]-p[i], vy=p[i+7]-p[i+1], vz=p[i+8]-p[i+2];
            double nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
            assertTrue(nx*p[i]+ny*p[i+1]+nz*p[i+2] >= -1e-7, "inward winding at " + i);
        }
    }

    @Test void everyPlanetHasOpaqueSurfaceAndLightAndShade() {
        for (var style : PlanetSkyMesh.Style.values()) {
            var mesh = PlanetSkyMesh.create(style);
            int low=255, high=0;
            for (int color : mesh.colors()) {
                assertEquals(255, color >>> 24);
                int brightness = ((color >> 16 & 255) + (color >> 8 & 255) + (color & 255))/3;
                low=Math.min(low, brightness); high=Math.max(high, brightness);
            }
            assertTrue(high-low > 40, "globe must be shaded: " + style);
        }
    }
}
