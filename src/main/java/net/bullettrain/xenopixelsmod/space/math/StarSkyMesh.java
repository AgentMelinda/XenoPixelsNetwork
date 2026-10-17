package net.bullettrain.xenopixelsmod.space.math;

import java.util.Random;

/** Deterministic background points, baked once rather than simulated per frame. */
public final class StarSkyMesh {
    public record Mesh(float[] positions, int[] colors) {}
    private StarSkyMesh() {}
    public static Mesh create() {
        Random random = new Random(20261013L);
        float[] positions = new float[1000 * 4 * 3];
        int[] colors = new int[1000 * 4];
        for (int star = 0; star < 1000; star++) {
            double y = random.nextDouble()*2-1, phi = random.nextDouble()*Math.PI*2;
            double horizontal = Math.sqrt(1-y*y), x=horizontal*Math.cos(phi), z=horizontal*Math.sin(phi);
            double ax=-z, ay=0, az=x, length=Math.sqrt(ax*ax+az*az);
            ax/=length; az/=length;
            double bx=y*az, by=z*ax-x*az, bz=-y*ax;
            double size=.045+random.nextDouble()*.085;
            int brightness=100+random.nextInt(156), color=0xff000000|brightness<<16|brightness<<8|brightness;
            for (int corner=0; corner<4; corner++) {
                double u=(corner==0||corner==3 ? -1:1)*size, v=(corner<2 ? -1:1)*size;
                int vertex=star*4+corner, p=vertex*3;
                positions[p]=(float)(x*80+ax*u+bx*v);
                positions[p+1]=(float)(y*80+ay*u+by*v);
                positions[p+2]=(float)(z*80+az*u+bz*v);
                colors[vertex]=color;
            }
        }
        return new Mesh(positions,colors);
    }
}
