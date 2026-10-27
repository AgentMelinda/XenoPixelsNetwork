package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasSnapshot;

import java.util.List;
import java.util.function.Function;

/** Reuses the bounded screen-space marker layout until its snapshot or map projection changes. */
public final class PlanetIntegrityAtlasMarkerCache {
    private long requestId = Long.MIN_VALUE;
    private double centreX = Double.NaN, centreZ = Double.NaN, scale = Double.NaN;
    private int width = -1, height = -1;
    private List<PlanetIntegrityAtlasMarkerLayout.Cluster> layout = List.of();

    public List<PlanetIntegrityAtlasMarkerLayout.Cluster> layout(PlanetIntegrityAtlasSnapshot snapshot,
            double centreX, double centreZ, double scale, int width, int height,
            Function<PlanetIntegrityAtlasSnapshot.Entry, PlanetIntegrityAtlasMarkerLayout.Point> project) {
        if (snapshot == null) return List.of();
        if (snapshot.requestId() == requestId && Double.compare(this.centreX, centreX) == 0
                && Double.compare(this.centreZ, centreZ) == 0 && Double.compare(this.scale, scale) == 0
                && this.width == width && this.height == height) return layout;
        this.requestId = snapshot.requestId();
        this.centreX = centreX;
        this.centreZ = centreZ;
        this.scale = scale;
        this.width = width;
        this.height = height;
        layout = PlanetIntegrityAtlasMarkerLayout.layout(snapshot.entries(), project);
        return layout;
    }

    public void clear() {
        requestId = Long.MIN_VALUE;
        centreX = Double.NaN;
        centreZ = Double.NaN;
        scale = Double.NaN;
        width = -1;
        height = -1;
        layout = List.of();
    }
}
