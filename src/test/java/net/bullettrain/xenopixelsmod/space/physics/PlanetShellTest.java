package net.bullettrain.xenopixelsmod.space.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanetShellTest {
    @Test void radius100HasOnlyBoundaryVoxels() {
        int count = 0;
        for (int x = -100; x <= 100; x++) for (int y = -100; y <= 100; y++)
            for (int z = -100; z <= 100; z++) {
                boolean expected=false;
                if(filled(x,y,z,100)) {
                    for(int dx=-1;dx<=1&&!expected;dx++) for(int dy=-1;dy<=1&&!expected;dy++)
                        for(int dz=-1;dz<=1&&!expected;dz++) expected=!filled(x+dx,y+dy,z+dz,100);
                }
                boolean actual = PlanetShell.contains(x,y,z,100);
                if (expected != actual) fail("boundary mismatch at " + x + "," + y + "," + z);
                if (actual) count++;
            }
        assertEquals(186_056, count);
        assertFalse(PlanetShell.contains(0,0,0,100));
        assertTrue(PlanetShell.contains(100,0,0,100));
        // Every voxel belongs to one face-connected component: native fracture must not split tip voxels.
        int side=201;
        var seen=new java.util.BitSet(side*side*side);
        int[] queue=new int[count];
        queue[0]=(200*side+100)*side+100;
        seen.set(queue[0]);
        int tail=1;
        int[][] neighbours={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for(int head=0;head<tail;head++) {
            int code=queue[head],z=code%side-100,y=(code/side)%side-100,x=code/(side*side)-100;
            for(var d:neighbours) {
                int nx=x+d[0],ny=y+d[1],nz=z+d[2];
                if(!PlanetShell.contains(nx,ny,nz,100))continue;
                int next=((nx+100)*side+(ny+100))*side+nz+100;
                if(seen.get(next))continue;
                seen.set(next);queue[tail++]=next;
            }
        }
        assertEquals(count,tail,"all shell voxels must be face-connected");
    }
    @Test void smallShellIsSymmetricAndHollow() {
        for (int x=-8;x<=8;x++) for(int y=-8;y<=8;y++) for(int z=-8;z<=8;z++) {
            assertEquals(PlanetShell.contains(x,y,z,8),PlanetShell.contains(-z,-x,y,8));
        }
        assertFalse(PlanetShell.contains(0,0,0,8));
        assertThrows(IllegalArgumentException.class,()->PlanetShell.contains(0,0,0,401));
        assertFalse(PlanetShell.contains(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,100));
        assertFalse(PlanetShell.contains(Integer.MIN_VALUE,0,0,100));
    }
    @Test void periodMatchesCircularAcceleration() {
        double mu = PlanetShell.orbitMu(1000,3600);
        double speed = 2*Math.PI*1000/3600;
        assertEquals(speed*speed/1000,mu/(1000*1000),1e-12);
        assertEquals(0,PlanetShell.orbitMu(0,3600));
    }
    @Test void earthOrbitSeedIsHorizontalAndMatchesPeriod() {
        var orbit=net.bullettrain.xenopixelsmod.space.math.OrbitalElements.circular(1000,3600);
        double[] velocity=new double[3];
        assertSame(velocity,PlanetShell.orbitSeed(orbit,0,velocity));
        assertEquals(0,velocity[0],1e-12);
        assertEquals(0,velocity[1],1e-12);
        assertEquals(2*Math.PI*1000/3600,velocity[2],1e-12);
        PlanetShell.orbitSeed(orbit,900,velocity);
        assertEquals(-2*Math.PI*1000/3600,velocity[0],1e-12);
        assertEquals(0,velocity[1],1e-12);
        assertEquals(0,velocity[2],1e-12);
    }
    private static boolean filled(int x,int y,int z,int r) { return x*x+y*y+z*z<=r*r; }
}
