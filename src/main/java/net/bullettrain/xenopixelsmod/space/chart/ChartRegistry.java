package net.bullettrain.xenopixelsmod.space.chart;

import net.bullettrain.xenopixelsmod.space.math.BodyLod;
import net.bullettrain.xenopixelsmod.space.math.GravityField;
import net.bullettrain.xenopixelsmod.space.math.KeplerSolver;
import net.bullettrain.xenopixelsmod.space.math.OrbitalElements;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Galaxy Atlas at runtime: the authored {@link CelestialBody} rows plus the scratch buffers that
 * make evaluating all of them every tick cost nothing in allocations.
 *
 * <p>§21's whole argument is that an unloaded planet still orbits for free because it is <b>evaluated,
 * not simulated</b> — it has no chunk tickets, no entity ticks, no block entities, just a registry row
 * and some arithmetic. This class is that arithmetic. Two hundred bodies means two hundred Kepler
 * solves on a throttled pass and a flat array read on every frame, with no per-body object, no
 * iterator and no boxing anywhere in the loop.
 *
 * <p><b>Why flat arrays.</b> Position, velocity and LOD are stored as parallel primitive arrays indexed
 * by body slot rather than as fields on a per-body object. The chart is walked end to end on every
 * gravity pass and every render frame, so contiguous doubles stay in cache and the walk allocates
 * nothing — an {@code Iterable<CelestialBody>} with a {@code Vec3} per body would put hundreds of
 * short-lived objects per tick into the young generation for data that is overwritten immediately.
 *
 * <p><b>Hierarchy.</b> Moons orbit planets, planets orbit stars (§20), so a body's absolute position is
 * its parent's absolute position plus its own orbital offset. {@link #rebuild} topologically orders the
 * snapshot so parents always precede children, which makes evaluation a single forward pass with no
 * recursion and no repeated work. A body whose parent is missing is treated as a root and logged by the
 * caller, because §17's rule is that an unresolvable reference degrades rather than aborts the load.
 *
 * <p>Not thread-safe by itself: the server evaluates and owns the buffers, and clients hold their own
 * instance rebuilt from a synced snapshot. Sharing one instance across the physics thread and the
 * render thread would need locking that the per-side design makes unnecessary.
 */
public final class ChartRegistry {

    /** The design's tuned cap on simultaneously detailed proxy spheres (§19.1). */
    public static final int DEFAULT_PROXY_BUDGET = 12;

    private static final CelestialBody[] NO_BODIES = new CelestialBody[0];

    private CelestialBody[] bodies = NO_BODIES;
    private final Map<String, Integer> slotById = new HashMap<>();
    /**
     * First body bound to each dimension, in slot order. Built once per {@link #rebuild} so
     * {@link #slotFor} is a hash lookup on the ladder's per-observer path; {@link #slotForDimension}
     * keeps answering from the raw authored string so a dimension id that is not a valid identifier
     * still resolves for command and test callers.
     */
    private Map<ResourceLocation, Integer> firstSlotByDimension = new HashMap<>();
    /** Parent slot per body, or -1 for a root. Resolved once at rebuild, never per tick. */
    private int[] parentSlot = new int[0];

    // Flat scratch, reallocated only when the body count changes.
    private double[] px = new double[0], py = new double[0], pz = new double[0];
    private double[] vx = new double[0], vy = new double[0], vz = new double[0];
    private boolean[] manual = new boolean[0];
    private double[] exitAltitude = new double[0];
    private double[] manualX = new double[0], manualY = new double[0], manualZ = new double[0];
    private double[] angularSize = new double[0];
    private BodyLod[] lod = new BodyLod[0];

    /** Scratch used by {@link #gravityAt} so the query itself allocates nothing either. */
    private final double[] positionScratch = new double[3];
    private final double[] velocityScratch = new double[3];
    private GravityField field;

    /**
     * Scratch for the proxy-budget cutoff selection. Resized only when the budget changes, so
     * {@link #resolveLods} stays allocation-free on the frame path it runs on.
     */
    private double[] cutoffScratch = new double[DEFAULT_PROXY_BUDGET];

    private int proxyBudget = DEFAULT_PROXY_BUDGET;
    private BodyLod.Thresholds thresholds = BodyLod.Thresholds.defaults();
    private double fieldRange = 100_000.0;
    private boolean realOrbits = true;

    /**
     * Bumped on every rebuild. Clients compare it against the value they last synced and re-pull the
     * chart only when it differs (§19.3), so a datapack reload does not re-send an unchanged atlas.
     */
    private int version;

    /** Last time in seconds the positions were evaluated for, so a repeated call is a no-op. */
    private double evaluatedAt = Double.NaN;

    public ChartRegistry() {
        this(new GravityField(3));
    }

    public ChartRegistry(GravityField field) {
        this.field = field == null ? new GravityField(3) : field;
    }

    /**
     * Replaces the whole chart. Called at load and on {@code /xenospace reload} — never per tick.
     *
     * <p>Validation happens before anything is swapped, so a bad pack cannot leave the registry half
     * updated: either the new chart is fully consistent or the old one is untouched. That is §17's
     * validation-first rule, and it is what makes a live datapack reload safe on a running server.
     *
     * @return the rejected entries as {@code "id: reason"} strings, empty when the chart was accepted
     *         in full. Rejected rows are dropped, not fatal, so one bad custom planet cannot blank the
     *         sky for everyone.
     */
    public List<String> rebuild(List<CelestialBody> source) {
        List<String> rejected = new ArrayList<>();
        Map<String, CelestialBody> byId = new LinkedHashMap<>();
        if (source != null) {
            for (CelestialBody body : source) {
                if (body == null) continue;
                if (byId.putIfAbsent(body.id(), body) != null) {
                    rejected.add(body.id() + ": duplicate chart id");
                }
            }
        }

        CelestialBody[] ordered = topologicalOrder(byId, rejected);
        Map<String, Integer> index = new HashMap<>(ordered.length * 2);
        for (int i = 0; i < ordered.length; i++) index.put(ordered[i].id(), i);

        this.bodies = ordered;
        this.slotById.clear();
        this.slotById.putAll(index);
        this.parentSlot = new int[ordered.length];
        Map<ResourceLocation, Integer> byDimension = new HashMap<>();
        for (int i = 0; i < ordered.length; i++) {
            String parent = ordered[i].parentId();
            Integer slot = parent == null ? null : index.get(parent);
            // A parent must sit in an earlier slot for the single forward pass in evaluate() to see its
            // position already computed. That holds for any acyclic chart, because the topological
            // order is what put it there. When it does not hold, the reference took part in a cycle —
            // so the link is dropped and the body becomes a system root. Degraded, but its position is
            // then well-defined instead of depending on which half of the cycle happened to be walked
            // first, which is §17's rule: an unresolvable reference costs one entry, not the chart.
            parentSlot[i] = slot == null || slot >= i ? -1 : slot;
            // CelestialBody only requires a non-blank dimension id, so an authored value that is not a
            // valid identifier is simply absent from the index: the fast lookup degrades, the load does not.
            String dimension = ordered[i].dimensionId();
            if (dimension != null) {
                ResourceLocation key = ResourceLocation.tryParse(dimension);
                if (key != null) byDimension.putIfAbsent(key, i);
            }
        }
        this.firstSlotByDimension = byDimension;

        int count = ordered.length;
        if (px.length != count) {
            px = new double[count];
            py = new double[count];
            pz = new double[count];
            vx = new double[count];
            vy = new double[count];
            vz = new double[count];
            manual = new boolean[count];
            exitAltitude = new double[count];
            manualX = new double[count]; manualY = new double[count]; manualZ = new double[count];
            angularSize = new double[count];
            lod = new BodyLod[count];
        }
        for (int i = 0; i < count; i++) lod[i] = BodyLod.ABSENT;
        java.util.Arrays.fill(manual, false);
        for (int i = 0; i < count; i++) exitAltitude[i] = bodies[i].exitAltitude();
        evaluatedAt = Double.NaN;
        version++;
        return rejected;
    }

    /**
     * Orders bodies so every parent precedes its children, dropping every body that takes part in a
     * reference cycle.
     *
     * <p>A cycle is not degradable the way a dangling parent is. A moon orbiting a planet that orbits
     * the moon has no position at all — cutting one link would leave whichever body happened to be
     * walked first acting as the root, so the chart's shape would depend on pack declaration order.
     * Both members are rejected with a reason instead, which is a loud, deterministic answer to a pack
     * bug rather than a silently arbitrary sky.
     */
    private static CelestialBody[] topologicalOrder(Map<String, CelestialBody> byId, List<String> rejected) {
        List<CelestialBody> ordered = new ArrayList<>(byId.size());
        Map<String, Integer> state = new HashMap<>(byId.size() * 2);
        java.util.Set<String> cyclic = new java.util.HashSet<>();
        // 0 = unvisited, 1 = on the current stack, 2 = placed.
        for (String id : byId.keySet()) {
            visit(id, byId, state, ordered, rejected, new ArrayList<>(), cyclic);
        }
        if (cyclic.isEmpty()) return ordered.toArray(new CelestialBody[0]);
        List<CelestialBody> kept = new ArrayList<>(ordered.size());
        for (CelestialBody body : ordered) {
            if (!cyclic.contains(body.id())) kept.add(body);
        }
        return kept.toArray(new CelestialBody[0]);
    }

    private static void visit(String id, Map<String, CelestialBody> byId, Map<String, Integer> state,
                              List<CelestialBody> ordered, List<String> rejected, List<String> stack,
                              java.util.Set<String> cyclic) {
        Integer seen = state.get(id);
        if (seen != null && seen == 2) return;
        if (seen != null && seen == 1) {
            // Everything from this id's first appearance on the stack round to here is in the cycle.
            int start = stack.indexOf(id);
            List<String> ring = start < 0 ? List.of(id) : stack.subList(start, stack.size());
            cyclic.addAll(ring);
            rejected.add(id + ": orbital reference cycle " + String.join(" -> ", ring) + " -> " + id);
            return;
        }
        CelestialBody body = byId.get(id);
        if (body == null) return;
        state.put(id, 1);
        stack.add(id);
        String parent = body.parentId();
        if (parent != null && !byId.containsKey(parent)) {
            // A dangling parent is not fatal: the body becomes a root, matching §17's rule that an
            // unresolvable reference degrades the entry rather than aborting the whole load.
            rejected.add(id + ": unknown parent '" + parent + "', treating as a system root");
        } else if (parent != null) {
            visit(parent, byId, state, ordered, rejected, stack, cyclic);
        }
        stack.remove(stack.size() - 1);
        state.put(id, 2);
        ordered.add(body);
    }

    /**
     * Re-evaluates every body's absolute position and velocity for {@code timeSeconds}.
     *
     * <p>Idempotent within a timestamp: a second call for the same instant returns immediately, so the
     * render thread and the physics pass can both ask without paying twice.
     *
     * @return the number of bodies whose position actually came from live Kepler math
     */
    public int evaluate(double timeSeconds) {
        if (evaluatedAt == timeSeconds) return 0;
        evaluatedAt = timeSeconds;
        int solved = 0;
        for (int i = 0; i < bodies.length; i++) {
            CelestialBody body = bodies[i];
            if (manual[i]) {
                px[i]=manualX[i]; py[i]=manualY[i]; pz[i]=manualZ[i];
                vx[i]=0; vy[i]=0; vz[i]=0;
                continue;
            }
            double ox = 0.0, oy = 0.0, oz = 0.0;
            double dvx = 0.0, dvy = 0.0, dvz = 0.0;

            boolean moving = realOrbits && body.moves() && body.orbit() != null;
            if (moving) {
                OrbitalElements elements = body.orbit();
                KeplerSolver.positionAndVelocity(elements, timeSeconds, positionScratch, velocityScratch);
                ox = positionScratch[0];
                // Kepler's reference plane is XY; Sable body tracking maps that plane to Minecraft XZ.
                // Publish chart positions and velocities in those same world axes so render, gravity,
                // handoff carry-over, and the physical orbit controller agree.
                oy = positionScratch[2];
                oz = positionScratch[1];
                dvx = velocityScratch[0];
                dvy = velocityScratch[2];
                dvz = velocityScratch[1];
                solved++;
            } else {
                ox = body.pinnedX();
                oy = body.pinnedY();
                oz = body.pinnedZ();
            }

            // Absolute = parent absolute + own offset. Safe in one forward pass because rebuild put
            // every parent in an earlier slot.
            int parent = parentSlot[i];
            if (parent >= 0 && parent < i) {
                ox += px[parent];
                oy += py[parent];
                oz += pz[parent];
                dvx += vx[parent];
                dvy += vy[parent];
                dvz += vz[parent];
            }
            px[i] = ox;
            py[i] = oy;
            pz[i] = oz;
            vx[i] = dvx;
            vy[i] = dvy;
            vz[i] = dvz;
        }
        return solved;
    }

    /**
     * Resolves every body's LOD against one observer and enforces the global proxy budget.
     *
     * <p>The budget is applied by angular size rather than by slot order, so the twelve proxies that
     * survive are the twelve that actually look biggest — a distant gas giant loses its sphere to a
     * near moon, which is what a player would expect. Ties break on slot, which is stable across ticks
     * because the snapshot is immutable, so the set does not flicker between frames.
     *
     * @return the number of bodies left at {@link BodyLod#PROXY} after the budget was applied
     */
    public int resolveLods(double observerX, double observerY, double observerZ, boolean inViewTest) {
        for (int i = 0; i < bodies.length; i++) {
            double dx = px[i] - observerX;
            double dy = py[i] - observerY;
            double dz = pz[i] - observerZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            angularSize[i] = KeplerSolver.angularSize(bodies[i].radiusBlocks(), distance);
            // A single-observer resolution treats everything as in view; the render side narrows this
            // with a real frustum test before it draws anything.
            lod[i] = BodyLod.resolve(distance, bodies[i].radiusBlocks(), lod[i], thresholds, inViewTest);
        }
        return applyProxyBudget();
    }

    /**
     * Resolve every body's LOD against the nearest of several observers, then enforce the proxy budget.
     *
     * <p>This is what the live server-side driver calls. One chart-wide ladder state — rather than a
     * per-viewpoint ladder — is what makes the server's activation decisions deterministic and cheap:
     * a body a player flew up to stays promoted for everyone until the nearest observer recedes past
     * the hysteresis margin. Each body's distance is the minimum over the observers; the angular sizes
     * the budget compares are taken at that same nearest distance, so "keep the biggest spheres" holds
     * no matter who is looking.
     *
     * <p>With {@code count == 0} every body resolves at infinite distance, which {@link BodyLod#resolve}
     * maps to {@link BodyLod#ABSENT}. That is the correct idle cost for a chart-bound dimension with no
     * players in it: the ladder gives every stage back and the walk is the only work left.
     *
     * @param observerX parallel arrays of observer positions; only the first {@code count} entries read
     * @param count     how many observers there are; may be zero
     * @param inViewTest server-side the frustum is unknown, so callers pass true and let the render side
     *                   narrow the purely visual stages per-viewpoint
     * @return the number of bodies left at {@link BodyLod#PROXY} after the budget was applied
     */
    public int resolveLodsForObservers(double[] observerX, double[] observerY, double[] observerZ,
                                       int count, boolean inViewTest) {
        for (int i = 0; i < bodies.length; i++) {
            double nearest2 = Double.POSITIVE_INFINITY;
            for (int o = 0; o < count; o++) {
                double dx = px[i] - observerX[o];
                double dy = py[i] - observerY[o];
                double dz = pz[i] - observerZ[o];
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < nearest2) nearest2 = d2;
            }
            double distance = Math.sqrt(nearest2);
            angularSize[i] = KeplerSolver.angularSize(bodies[i].radiusBlocks(), distance);
            lod[i] = BodyLod.resolve(distance, bodies[i].radiusBlocks(), lod[i], thresholds, inViewTest);
        }
        return applyProxyBudget();
    }

    /**
     * Enforce the global proxy budget against the angular sizes and stages the most recent resolution
     * pass left in the scratch arrays. Shared by the single- and multi-observer entry points so the
     * demotion rule exists in exactly one place. Allocation-free: the cutoff scratch is grown only when
     * the budget changes, never here.
     *
     * @return the number of bodies left at {@link BodyLod#PROXY}
     */
    private int applyProxyBudget() {
        int proxies = 0;
        for (int i = 0; i < bodies.length; i++) if (lod[i] == BodyLod.PROXY) proxies++;
        if (proxies <= proxyBudget) return proxies;

        // A budget of zero cannot go through the cutoff selection below: that scratch array is at
        // least one element long, so the "budget-th largest" would resolve to the single largest proxy
        // and keep it. Zero spheres means zero spheres.
        if (proxyBudget <= 0) {
            for (int i = 0; i < bodies.length; i++) {
                if (lod[i] == BodyLod.PROXY) lod[i] = BodyLod.BILLBOARD;
            }
            return 0;
        }

        // Over budget: find the angular size of the budget-th largest proxy and demote everything
        // smaller. Insertion into a fixed-size sorted array keeps this allocation-free.
        double[] cutoffs = cutoffScratch;
        java.util.Arrays.fill(cutoffs, 0.0);
        for (int i = 0; i < bodies.length; i++) {
            if (lod[i] != BodyLod.PROXY) continue;
            insertDescending(cutoffs, angularSize[i]);
        }
        double cutoff = cutoffs[cutoffs.length - 1];
        int aboveCutoff = 0;
        for (int i = 0; i < bodies.length; i++) {
            if (lod[i] == BodyLod.PROXY && angularSize[i] > cutoff) aboveCutoff++;
        }
        int tiesRemaining = proxyBudget - aboveCutoff;
        int kept = 0;
        for (int i = 0; i < bodies.length; i++) {
            if (lod[i] != BodyLod.PROXY) continue;
            if (angularSize[i] > cutoff || (angularSize[i] == cutoff && tiesRemaining-- > 0)) {
                kept++;
            } else {
                lod[i] = BodyLod.BILLBOARD;
            }
        }
        return kept;
    }

    /** Immutable diagnostic result. Stage counts follow {@link BodyLod#values()} order. */
    public record Inspection(int proxies, List<Integer> stageCounts, double ax, double ay, double az,
                             int contributors, int culled) {
        public Inspection { stageCounts = List.copyOf(stageCounts); }
    }

    /**
     * Command-only preview against the current evaluated positions and effective settings.
     * Starts the hypothetical observer at ABSENT; it never changes live LOD/gravity scratch,
     * evaluates orbits, loads terrain, or applies forces. Allocations are deliberate here.
     */
    public Inspection inspectAt(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("inspection coordinates must be finite");
        }
        var copy = new ChartRegistry(new GravityField(field.capacity()));
        copy.bodies = bodies; // Immutable rows; rebuild replaces this array rather than mutating it.
        copy.px = px.clone(); copy.py = py.clone(); copy.pz = pz.clone();
        copy.angularSize = new double[bodies.length];
        copy.lod = new BodyLod[bodies.length];
        java.util.Arrays.fill(copy.lod, BodyLod.ABSENT);
        copy.setProxyBudget(proxyBudget);
        copy.thresholds = thresholds;
        copy.fieldRange = fieldRange;
        int proxies = copy.resolveLods(x, y, z, true);
        int[] counts = new int[BodyLod.values().length];
        for (BodyLod stage : copy.lod) counts[stage.ordinal()]++;
        List<Integer> stages = new ArrayList<>(counts.length);
        for (int count : counts) stages.add(count);
        double[] gravity = copy.gravityAt(x, y, z, new double[3]);
        return new Inspection(proxies, stages, gravity[0], gravity[1], gravity[2],
                copy.lastGravityContributors(), copy.lastGravityCulled());
    }

    private static void insertDescending(double[] sorted, double value) {
        int slot = sorted.length - 1;
        if (value <= sorted[slot]) return;
        while (slot > 0 && sorted[slot - 1] < value) {
            sorted[slot] = sorted[slot - 1];
            slot--;
        }
        sorted[slot] = value;
    }

    /**
     * Writes the net gravitational acceleration at a point into {@code out} and returns it.
     *
     * <p>This is the §11 throttled field sum: range-cut, capped at the accumulator's contributor
     * limit, and allocation-free. Positions must already be current, so callers evaluate first — the
     * scheduler does that once per pass for everyone.
     */
    public double[] gravityAt(double x, double y, double z, double[] out) {
        field.reset(fieldRange);
        for (int i = 0; i < bodies.length; i++) {
            double mu = bodies[i].mu();
            if (!(mu > 0.0)) continue;
            field.add(px[i] - x, py[i] - y, pz[i] - z, mu, bodies[i].radiusBlocks(), bodies[i].falloff());
        }
        return field.finish(out);
    }

    /**
     * The live, §11-bounded sibling of {@link #gravityAt}: it sums only bodies the lazy-activation
     * ladder currently <b>simulates</b> — {@code FIELD}, {@code ANCHOR}, or {@code CHUNKS} — so a
     * distant billboard or an {@code ABSENT} planet pulls on nothing. This is the point of §21: a body
     * only costs gravity once someone is close enough for it to matter, and the stage is exactly the
     * gate. It reads the same live {@code lod} scratch the {@code SpaceLodDriver} pass writes, so the
     * producer and consumer share one source of truth and the sum stays allocation-free.
     *
     * <p>Kept separate from {@link #gravityAt} on purpose: that method evaluates the <i>whole</i> field
     * at a hypothetical point for {@link #inspectAt}'s preview and must not change semantics. Callers
     * that want "everything, ignoring activation" use {@code gravityAt}; callers that must respect the
     * activation ladder use this.
     *
     * @return {@code out}, the net world-space acceleration from simulating bodies only
     */
    public double[] simulatingGravityAt(double x, double y, double z, double[] out) {
        field.reset(fieldRange);
        for (int i = 0; i < bodies.length; i++) {
            if (!lod[i].simulates()) continue;
            double mu = bodies[i].mu();
            if (!(mu > 0.0)) continue;
            field.add(px[i] - x, py[i] - y, pz[i] - z, mu, bodies[i].radiusBlocks(), bodies[i].falloff());
        }
        return field.finish(out);
    }

    /**
     * The orbital velocity carried through a handoff (§20's velocity carry-over), written into
     * {@code out}. A ship leaving a body inherits this, which is what makes the transition continuous
     * instead of a hard stop at the edge of the band.
     */
    public double[] velocityOf(int slot, double[] out) {
        if (slot < 0 || slot >= bodies.length) {
            out[0] = 0.0;
            out[1] = 0.0;
            out[2] = 0.0;
            return out;
        }
        out[0] = vx[slot];
        out[1] = vy[slot];
        out[2] = vz[slot];
        return out;
    }

    public int slotOf(String id) {
        Integer slot = id == null ? null : slotById.get(id);
        return slot == null ? -1 : slot;
    }

    /**
     * The first body bound to a dimension, or -1, answered by scanning every body's authored string.
     * Command and test callers use this; the LOD driver uses {@link #slotFor}, which is the same answer
     * without the scan or the string. A body someone is standing in holds CHUNKS against the ladder
     * either way, rather than depending on the dimension's spawn coordinates happening to match.
     *
     * <p>First match, not nearest: the groundwork chart binds at most one showcase body per dimension,
     * and a deterministic answer beats an ambiguous one until §17's sidecars allow several.
     */
    public int slotForDimension(String dimensionId) {
        if (dimensionId == null) return -1;
        for (int i = 0; i < bodies.length; i++) {
            if (dimensionId.equals(bodies[i].dimensionId())) return i;
        }
        return -1;
    }

    /**
     * The same first-bound-body answer as {@link #slotForDimension}, from the index {@link #rebuild}
     * built, so a caller that already holds the dimension's {@link ResourceLocation} neither scans the
     * chart nor allocates its string form. This is the tick-path accessor: {@code SpaceLodDriver} calls
     * it once per observer per ladder pass.
     */
    public int slotFor(ResourceLocation dimensionId) {
        Integer slot = firstSlotByDimension.get(dimensionId);
        return slot == null ? -1 : slot;
    }

    public CelestialBody body(int slot) {
        return slot < 0 || slot >= bodies.length ? null : bodies[slot];
    }

    /** Server handoff rule only; authored bodies and client wire data stay immutable. */
    public double exitAltitude(int slot) { return exitAltitude[slot]; }

    public List<String> applyExitOverrides(Map<String, Double> overrides) {
        ArrayList<String> rejected = new ArrayList<>();
        for (int i = 0; i < bodies.length; i++) {
            CelestialBody body = bodies[i];
            Double y = overrides.get(body.id());
            if (y != null && Double.isFinite(y) && y <= body.warmUpAltitude()) {
                rejected.add(body.id() + " exit override " + y + " is not above warm-up altitude " + body.warmUpAltitude());
                y = null;
            }
            exitAltitude[i] = y != null && Double.isFinite(y) ? y : body.exitAltitude();
        }
        return List.copyOf(rejected);
    }

    public CelestialBody byId(String id) {
        return body(slotOf(id));
    }

    /** Absolute chart-space placement, independent of the body's authored parent/orbit.
     * Does not move surface blocks/entities or instantiate a physics body. */
    public void moveBody(String id, double x, double y, double z) {
        int slot = requireBody(id);
        if (!validCoordinate(x) || !validCoordinate(y) || !validCoordinate(z))
            throw new IllegalArgumentException("body coordinates must be finite and within +/-30000000");
        manualX[slot]=x; manualY[slot]=y; manualZ[slot]=z; manual[slot]=true;
        evaluatedAt=Double.NaN;
    }
    public void resumeBody(String id) {
        manual[requireBody(id)]=false;
        evaluatedAt=Double.NaN;
    }
    /** Called on publication, not on a tick; identities missing in the candidate are skipped. */
    public void copyManualPositionsFrom(ChartRegistry previous) {
        for (int i=0;i<previous.size();i++) {
            if (previous.manual[i] && slotOf(previous.body(i).id())>=0)
                moveBody(previous.body(i).id(),previous.manualX[i],previous.manualY[i],previous.manualZ[i]);
        }
    }
    public boolean manuallyPositioned(int slot) { return slot>=0 && slot<manual.length && manual[slot]; }
    public double velocityX(int slot) { return vx[slot]; }
    public double velocityY(int slot) { return vy[slot]; }
    public double velocityZ(int slot) { return vz[slot]; }
    private int requireBody(String id) {
        int slot=slotOf(id);
        if(slot<0) throw new IllegalArgumentException("unknown space body: "+id);
        return slot;
    }
    private static boolean validCoordinate(double coordinate) {
        return Double.isFinite(coordinate) && Math.abs(coordinate)<=30_000_000;
    }

    public int size() {
        return bodies.length;
    }

    public int version() {
        return version;
    }

    public BodyLod lodOf(int slot) {
        return slot < 0 || slot >= bodies.length ? BodyLod.ABSENT : lod[slot];
    }

    public double x(int slot) {
        return px[slot];
    }

    public double y(int slot) {
        return py[slot];
    }

    public double z(int slot) {
        return pz[slot];
    }

    /** How many bodies contributed to the last {@link #gravityAt}, for the perf probe. */
    public int lastGravityContributors() {
        return field.contributors();
    }

    /** How many bodies the range cut rejected on the last {@link #gravityAt}. */
    public int lastGravityCulled() {
        return field.culledCount();
    }

    public void setProxyBudget(int budget) {
        this.proxyBudget = Math.max(0, budget);
        cutoffScratch = new double[Math.max(1, proxyBudget)];
    }

    public int proxyBudget() {
        return proxyBudget;
    }

    public void setThresholds(BodyLod.Thresholds thresholds) {
        this.thresholds = thresholds == null ? BodyLod.Thresholds.defaults() : thresholds;
    }

    public void setFieldRange(double fieldRange) {
        this.fieldRange = Math.max(0.0, fieldRange);
    }

    /** Rebuilt on config reload, never per query. */
    public void setGravityContributors(int count) {
        field = new GravityField(Math.max(1, Math.min(32, count)));
    }

    /** §20's {@code space.gravity.real_orbits}: off pins every body at its authored offset. */
    public void setRealOrbits(boolean realOrbits) {
        if (this.realOrbits != realOrbits) {
            this.realOrbits = realOrbits;
            evaluatedAt = Double.NaN;
        }
    }

    public boolean realOrbits() {
        return realOrbits;
    }
}
