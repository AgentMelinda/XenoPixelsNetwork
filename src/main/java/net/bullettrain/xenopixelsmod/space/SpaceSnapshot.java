package net.bullettrain.xenopixelsmod.space;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.chart.ChartDefinition;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.space.math.BodyLod;
import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.joml.Vector3dc;

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
     * @param id     the body's stable id, for logs and for per-body art lookup
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
     * The bodies a client should draw right now. Live physical extras are first in their supplied stable
     * order; charted bodies follow in topological slot order. Reserving room for extras prevents a full
     * chart from making persistent uncharted physical bodies disappear from far LOD.
     *
     * <p>Reads only the ladder stage and the evaluated position, so it is safe to call from the visual
     * pass; it allocates the list, which is why the caller builds it on a change rather than per tick.
     */
    public static SpaceSnapshot fromChart(ChartRegistry chart,int revision) {
        return fromChart(chart, revision, Map.of(), List.of(), Double.NaN);
    }
    /**
     * The same bounded LOD snapshot, with live Sable centres substituted for loaded physical bodies. A far LOD then
     * follows the real simulated body instead of drawing a second stale globe at its authored chart coordinate.
     */
    public static SpaceSnapshot fromChart(ChartRegistry chart,int revision,Map<String,? extends Vector3dc> liveCentres) {
        return fromChart(chart, revision, liveCentres, List.of(), Double.NaN);
    }

    /** Adds bounded live physical bodies that do not have chart rows, such as persistent SDB planets. */
    public static SpaceSnapshot fromChart(ChartRegistry chart,int revision,
                                           Map<String,? extends Vector3dc> liveCentres,List<Body> liveExtras) {
        return fromChart(chart, revision, liveCentres, liveExtras, Double.NaN);
    }

    /** Adds the persisted home-system Y frame for chart-only bodies; live physical centres still win. */
    public static SpaceSnapshot fromChart(ChartRegistry chart,int revision,
                                           Map<String,? extends Vector3dc> liveCentres,List<Body> liveExtras,
                                           double homeFrameY) {
        int extraCapacity=liveExtras==null?0:liveExtras.size();
        var bodies=new ArrayList<Body>(Math.min(MAX_BODIES,chart.size()+extraCapacity));
        if(liveExtras!=null) for(Body extra:liveExtras) {
            if(bodies.size()==MAX_BODIES) break;
            if(extra!=null && !containsId(bodies,extra.id())) bodies.add(extra);
        }
        for(int slot=0;slot<chart.size() && bodies.size()<MAX_BODIES;slot++) {
            BodyLod stage=chart.lodOf(slot);
            if(stage.ordinal()<BodyLod.PROXY.ordinal() || stage.ordinal()>BodyLod.ANCHOR.ordinal()) continue;
            if(chart.body(slot).kind()==CelestialBody.BodyKind.BLACK_HOLE) continue; // drawn by BlackHoleClient
            if(!coordinate(chart.x(slot))||!coordinate(chart.y(slot))||!coordinate(chart.z(slot)))continue;
            var body=chart.body(slot);
            if(containsId(bodies,body.id())) continue;
            Vector3dc centre=liveCentres.get(body.id());
            double x=centre!=null&&coordinate(centre.x())?centre.x():chart.x(slot);
            double y=centre!=null&&coordinate(centre.y())?centre.y():SpaceHomeOrbitalFrame.chartY(chart,slot,homeFrameY);
            double z=centre!=null&&coordinate(centre.z())?centre.z():chart.z(slot);
            bodies.add(new Body(body.id(),body.skyStyle(),x,y,z,
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
        return signature(chart, revision, Map.of(), List.of(), Double.NaN);
    }
    public static long signature(ChartRegistry chart,int revision,Map<String,? extends Vector3dc> liveCentres) {
        return signature(chart, revision, liveCentres, List.of(), Double.NaN);
    }
    public static long signature(ChartRegistry chart,int revision,
                                Map<String,? extends Vector3dc> liveCentres,List<Body> liveExtras) {
        return signature(chart, revision, liveCentres, liveExtras, Double.NaN);
    }

    public static long signature(ChartRegistry chart,int revision,
                                Map<String,? extends Vector3dc> liveCentres,List<Body> liveExtras,
                                double homeFrameY) {
        long hash=1_469_598_103_934_665_603L*31+revision;
        int count=0;
        if(liveExtras!=null) for(int i=0;i<liveExtras.size() && count<MAX_BODIES;i++) {
            Body body=liveExtras.get(i);
            if(body==null || containsId(liveExtras,i,body.id())) continue;
            hash=mixBody(hash,body,BodyLod.PROXY.ordinal());
            count++;
        }
        for(int slot=0;slot<chart.size();slot++) {
            if(count>=MAX_BODIES) break;
            BodyLod stage=chart.lodOf(slot);
            if(stage.ordinal()<BodyLod.PROXY.ordinal() || stage.ordinal()>BodyLod.ANCHOR.ordinal()) continue;
            if(chart.body(slot).kind()==CelestialBody.BodyKind.BLACK_HOLE) continue;
            if(containsId(liveExtras,chart.body(slot).id())) continue;
            hash=mix(hash,stage.ordinal()); hash=mix(hash,chart.body(slot).skyStyle());
            hash=mixString(hash,chart.body(slot).id());
            Vector3dc centre=liveCentres.get(chart.body(slot).id());
            hash=mix(hash,round(centre!=null&&coordinate(centre.x())?centre.x():chart.x(slot)));
            hash=mix(hash,round(centre!=null&&coordinate(centre.y())?centre.y():SpaceHomeOrbitalFrame.chartY(chart,slot,homeFrameY)));
            hash=mix(hash,round(centre!=null&&coordinate(centre.z())?centre.z():chart.z(slot)));
            hash=mix(hash,Math.round(chart.body(slot).radiusBlocks()));
            count++;
        }
        return hash;
    }
    private static boolean containsId(List<Body> bodies,String id) {
        if(bodies==null) return false;
        for(Body body:bodies) if(body!=null && body.id().equals(id)) return true;
        return false;
    }
    private static boolean containsId(List<Body> bodies,int before,String id) {
        for(int i=0;i<before;i++) {
            Body body=bodies.get(i);
            if(body!=null && body.id().equals(id)) return true;
        }
        return false;
    }
    private static long mixBody(long hash,Body body,int stage) {
        hash=mix(hash,stage); hash=mix(hash,body.style()); hash=mixString(hash,body.id());
        hash=mix(hash,round(body.x())); hash=mix(hash,round(body.y())); hash=mix(hash,round(body.z()));
        return mix(hash,Math.round(body.radius()));
    }
    private static long mixString(long hash,String value) {
        hash=mix(hash,value.length());
        for(int i=0;i<value.length();i++) hash=mix(hash,value.charAt(i));
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
