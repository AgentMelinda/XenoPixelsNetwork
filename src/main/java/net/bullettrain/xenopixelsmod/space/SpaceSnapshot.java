package net.bullettrain.xenopixelsmod.space;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.chart.ChartDefinition;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.space.math.BodyLod;
import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Bounded visual snapshot, not client authority or a physics/terrain payload.
 *
 * <p>It carries what the server's LOD ladder decided is worth <em>drawing</em>: every body at
 * {@link BodyLod#PROXY}, {@link BodyLod#FIELD} or {@link BodyLod#ANCHOR}. The two stages left out on
 * purpose are {@link BodyLod#ABSENT} (nothing to show) and {@link BodyLod#BILLBOARD} — a billboard is an
 * <i>instanced</i> quad (§21's "200 of these are free"), and this packet drives one draw call per body, so
 * filling it with billboards would be a frame regression dressed as a feature. {@link BodyLod#CHUNKS} is
 * out because that body's real terrain is loaded, and drawing a sphere over the ground you are standing
 * on is the double-draw this slice is meant to avoid.
 */
public record SpaceSnapshot(int revision,List<Body> bodies) {
    /**
     * The cap on the wire, not a tuning knob. The proxy budget normally keeps the list near a dozen; this
     * exists so a hostile or mistaken encoder cannot make the decoder allocate an unbounded list, and
     * {@link #fromChart} truncates to it rather than throwing — a chart over cap loses spheres, not skies.
     */
    public static final int MAX_BODIES=32;
    /**
     * @param id     the chart body's id, for logs and for a client that wants to key per-body art later
     * @param style  the proxy atlas cell, {@code 0} to {@link CelestialBody#MAX_VISUAL_STYLES}-1
     * @param x      chart-space position, which is also the world position the proxy draws at
     * @param radius the body's own radius in blocks; the drawn quad is sized from it
     */
    public record Body(String id,int style,double x,double y,double z,double radius) {
        public Body {
            ChartDefinition.id(id);
            if(id.length()>256 || style<0 || style>=CelestialBody.MAX_VISUAL_STYLES || !coordinate(x)
                    || !coordinate(y) || !coordinate(z) || !Double.isFinite(radius) || radius<=0
                    || radius>1_000_000)
                throw new IllegalArgumentException("invalid space visual body");
        }
    }
    public SpaceSnapshot {
        if(revision<0 || bodies.size()>MAX_BODIES) throw new IllegalArgumentException("invalid space snapshot bounds");
        bodies=List.copyOf(bodies);
        var ids=new HashSet<String>();
        for(var body:bodies) if(!ids.add(body.id())) throw new IllegalArgumentException("duplicate space visual body");
    }
    private static boolean coordinate(double v) {return Double.isFinite(v)&&Math.abs(v)<=30_000_000;}

    /**
     * The bodies a client should draw right now, in slot order — which is the chart's topological order,
     * so the list is stable across revisions and cannot flicker between two frames.
     *
     * <p>Reads only the ladder stage and the evaluated position, so it is safe to call from the visual
     * pass; it allocates the list, which is why the caller builds it on a change rather than per tick.
     */
    public static SpaceSnapshot fromChart(ChartRegistry chart,int revision) {
        var bodies=new ArrayList<Body>(Math.min(MAX_BODIES,chart.size()));
        for(int slot=0;slot<chart.size() && bodies.size()<MAX_BODIES;slot++) {
            BodyLod stage=chart.lodOf(slot);
            if(stage.ordinal()<BodyLod.PROXY.ordinal() || stage.ordinal()>BodyLod.ANCHOR.ordinal()) continue;
            if(chart.body(slot).kind()==CelestialBody.BodyKind.BLACK_HOLE) continue; // drawn by BlackHoleClient
            if(!coordinate(chart.x(slot))||!coordinate(chart.y(slot))||!coordinate(chart.z(slot)))continue;
            var body=chart.body(slot);
            bodies.add(new Body(body.id(),body.skyStyle(),chart.x(slot),chart.y(slot),chart.z(slot),
                    body.radiusBlocks()));
        }
        return new SpaceSnapshot(revision,bodies);
    }

    /**
     * A change detector for the visual pass: same value means the list {@link #fromChart} would build now
     * is identical to the one last sent. Positions enter rounded to a block, because a sphere half a block
     * off is the same sphere and a moving orbit would otherwise re-send the atlas every interval.
     *
     * <p>Allocation-free by design — this runs on the server tick path §5 of the perf work cleaned up.
     */
    public static long signature(ChartRegistry chart,int revision) {
        long hash=1_469_598_103_934_665_603L*31+revision;
        for(int slot=0;slot<chart.size();slot++) {
            BodyLod stage=chart.lodOf(slot);
            if(stage.ordinal()<BodyLod.PROXY.ordinal() || stage.ordinal()>BodyLod.ANCHOR.ordinal()) continue;
            if(chart.body(slot).kind()==CelestialBody.BodyKind.BLACK_HOLE) continue;
            hash=mix(hash,stage.ordinal()); hash=mix(hash,chart.body(slot).skyStyle());
            hash=mix(hash,round(chart.x(slot))); hash=mix(hash,round(chart.y(slot)));
            hash=mix(hash,round(chart.z(slot))); hash=mix(hash,Math.round(chart.body(slot).radiusBlocks()));
        }
        return hash;
    }
    private static long round(double v) {return Double.isFinite(v)?(long)Math.floor(v+0.5):Long.MIN_VALUE;}
    private static long mix(long hash,long value) {return (hash^value)*1_099_511_628_211L; }
    public static void encode(SpaceSnapshot packet,FriendlyByteBuf buf) {
        buf.writeVarInt(packet.revision);buf.writeVarInt(packet.bodies.size());
        for(var b:packet.bodies) {
            buf.writeUtf(b.id,256);buf.writeVarInt(b.style);buf.writeDouble(b.x);buf.writeDouble(b.y);
            buf.writeDouble(b.z);buf.writeDouble(b.radius);
        }
    }
    public static SpaceSnapshot decode(FriendlyByteBuf buf) {
        int revision=buf.readVarInt(),count=buf.readVarInt();
        if(revision<0 || count<0 || count>MAX_BODIES)throw new IllegalArgumentException("invalid space snapshot bounds");
        var bodies=new ArrayList<Body>(count);
        for(int i=0;i<count;i++)bodies.add(new Body(buf.readUtf(256),buf.readVarInt(),buf.readDouble(),
                buf.readDouble(),buf.readDouble(),buf.readDouble()));
        return new SpaceSnapshot(revision,bodies);
    }
}
