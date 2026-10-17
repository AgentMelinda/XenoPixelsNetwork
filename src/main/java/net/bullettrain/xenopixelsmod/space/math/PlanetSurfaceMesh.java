package net.bullettrain.xenopixelsmod.space.math;

/** Cached 3D unit sphere with full-longitude UVs. Seam vertices intentionally duplicate. */
public final class PlanetSurfaceMesh {
    public static final int BANDS=24, SEGMENTS=48;
    public record Mesh(float[] positions,float[] uv) {}
    private PlanetSurfaceMesh() {}
    public static Mesh create() {
        int count=BANDS*SEGMENTS*4;
        float[] positions=new float[count*3],uv=new float[count*2];
        int vertex=0;
        for(int band=0;band<BANDS;band++) for(int segment=0;segment<SEGMENTS;segment++) {
            vertex=put(positions,uv,vertex,band,segment);
            vertex=put(positions,uv,vertex,band,segment+1);
            vertex=put(positions,uv,vertex,band+1,segment+1);
            vertex=put(positions,uv,vertex,band+1,segment);
        }
        return new Mesh(positions,uv);
    }
    private static int put(float[] positions,float[] uv,int vertex,int band,int segment) {
        double theta=Math.PI*band/BANDS,phi=2*Math.PI*segment/SEGMENTS;
        positions[vertex*3]=(float)(Math.sin(theta)*Math.cos(phi));
        positions[vertex*3+1]=(float)Math.cos(theta);
        positions[vertex*3+2]=(float)(Math.sin(theta)*Math.sin(phi));
        uv[vertex*2]=(float)segment/SEGMENTS;uv[vertex*2+1]=(float)band/BANDS;
        return vertex+1;
    }
}
