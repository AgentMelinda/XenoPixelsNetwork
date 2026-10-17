package net.bullettrain.xenopixelsmod.space.math;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StarSkyMeshTest {
    @Test void backgroundIsDeterministicBoundedAndOpaque() {
        var a=StarSkyMesh.create(); var b=StarSkyMesh.create();
        assertEquals(4000,a.colors().length);
        assertArrayEquals(a.positions(),b.positions()); assertArrayEquals(a.colors(),b.colors());
        for(int i=0;i<a.colors().length;i++) {
            assertEquals(255,a.colors()[i]>>>24);
            double x=a.positions()[i*3],y=a.positions()[i*3+1],z=a.positions()[i*3+2];
            assertEquals(80,Math.sqrt(x*x+y*y+z*z),.001);
        }
    }
}
