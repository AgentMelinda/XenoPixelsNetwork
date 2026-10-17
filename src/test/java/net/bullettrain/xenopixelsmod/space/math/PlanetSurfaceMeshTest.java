package net.bullettrain.xenopixelsmod.space.math;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlanetSurfaceMeshTest {
    @Test void surfaceIsBoundedUnitGeometryWithFullWrapAndPoles() {
        var mesh=PlanetSurfaceMesh.create();
        assertEquals(4608*3,mesh.positions().length);
        assertEquals(4608*2,mesh.uv().length);
        boolean seam=false,north=false,south=false;
        for(int vertex=0;vertex<mesh.uv().length/2;vertex++) {
            double x=mesh.positions()[3*vertex],y=mesh.positions()[3*vertex+1],z=mesh.positions()[3*vertex+2];
            assertEquals(1,x*x+y*y+z*z,1e-6);
            float u=mesh.uv()[2*vertex],v=mesh.uv()[2*vertex+1];
            assertTrue(u>=0&&u<=1&&v>=0&&v<=1);
            if(u==1) {seam=true;assertEquals(0,z,1e-6);}
            if(v==0) {north=true;assertEquals(1,y,1e-6);}
            if(v==1) {south=true;assertEquals(-1,y,1e-6);}
        }
        assertTrue(seam&&north&&south);
    }
}
