package net.bullettrain.xenopixelsmod.space;

import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.chart.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.io.IOException;
import java.util.*;

/** Chart publication, evaluation, and command reads all belong to the logical server thread. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SpaceChartService {
    private SpaceChartService() {}
    private static final Map<ReloadableServerResources, ChartResources> LISTENERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MinecraftServer, State> STATES = Collections.synchronizedMap(new IdentityHashMap<>());
    public static final class State {
        public final ChartRuntime runtime = new ChartRuntime();
        public int lastVisualRevision=-1;
        public long nextVisualTick;
        /** The visual pass's own one-second clock, independent of the physical-binding pass below. */
        public long nextChartVisualTick;
        /**
         * The last {@link SpaceSnapshot#signature} pushed to clients, so the visual pass builds a list only
         * when there is something new to say. Reset with the chart, which is exactly when it must change.
         */
        public long lastVisualSignature=Long.MIN_VALUE;
        /** How many bodies the last snapshot carried, for {@code /xenospace status}. */
        public int lastVisualBodies;
        public java.util.UUID physicalVisualBody;
        public long tickStartNanos;
        public Map<String, JsonObject> definitions = Map.of();
        public List<String> errors = List.of();
        public List<String> resourceErrors = List.of();
        public Set<String> sidecarDimensions = Set.of();
        /** Validated admin YAML settings, keyed by dimension id; values absent from the sidecar inherit. */
        public Map<String, DimensionSpaceSettings> sidecarSettings = Map.of();
        /** S11 B1 asteroid belts from the last published resources; parents resolve against {@link ChartRuntime#chart}. */
        public Map<String, net.bullettrain.xenopixelsmod.space.asteroid.BeltDefinition> belts = Map.of();
        /** Bumped whenever {@link #belts} is replaced, so the asteroid field sync re-sends. */
        public int beltRevision;
        /** Data-pack assignments for the seven movable planet-form Super Dragon Balls. */
        public Map<Integer, net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetDefinition> dragonBallPlanets = Map.of();
        /** Unfiltered resource input retained so dimension/erasure rebuilds can revalidate host references. */
        public Map<Integer, net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetDefinition> dragonBallPlanetDefinitions = Map.of();
        private ReloadableServerResources lastAttempt;
    }
    @SubscribeEvent public static void reloadListeners(AddReloadListenerEvent event) {
        var listener = new ChartResources();
        LISTENERS.put(event.getServerResources(), listener);
        event.addListener(listener);
    }
    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        refreshResources(event.getServer(), state(event.getServer()));
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        STATES.remove(event.getServer());
        LISTENERS.remove(event.getServer().getServerResources().managers());
    }
    @SubscribeEvent public static void tickStart(ServerTickEvent.Pre event) {
        // Wall-clock tick instrumentation only; the value is consumed in the Post handler below.
        state(event.getServer()).tickStartNanos = System.nanoTime();
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        State state = state(server);
        refreshResources(server, state);
        net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.publishPosition(server, state.runtime.chart);
        state.runtime.tick(SpaceOrbitClock.gameTick(server), XenoSpaceConfig.enabled(),
                XenoSpaceConfig.gravityIntervalTicks(), XenoSpaceConfig.perfProbeEnabled());
        SpaceVisualSync.tick(server,state);
        SpaceVisualSync.tickChartVisuals(server,state);
        net.bullettrain.xenopixelsmod.space.asteroid.AsteroidFieldSync.tick(server, state);
        net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetFieldSync.tick(server, state);
        long start = state.tickStartNanos;
        // Zero guards a Post without a matching Pre (cannot happen in a normal loop; must not record
        // a nonsense span if event ordering ever changes).
        if (start != 0L) {
            state.runtime.recordServerTickSpan(System.nanoTime() - start, SpaceOrbitClock.gameTick(server));
            state.tickStartNanos = 0L;
        }
    }
    public static State state(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("space chart accessed outside server thread");
        return STATES.computeIfAbsent(server, ignored -> new State());
    }
    public static void refresh(MinecraftServer server) { refreshResources(server, state(server)); }
    private static void refreshResources(MinecraftServer server, State state) {
        var owner = server.getServerResources().managers();
        if (owner == state.lastAttempt) return;
        var listener = LISTENERS.get(owner);
        if (listener == null || listener.input() == null) return;
        state.lastAttempt = owner;
        try {
            publish(server, state, listener.input(), true);
        }
        catch (Exception e) { failure(state, e); return; }
        persistDragonBallPlanetHosts(server, state);
    }
    /** Build a complete candidate and all chart-dependent metadata before committing it on the server thread. */
    private static void publish(MinecraftServer server, State state, ChartResources.Input input, boolean log) throws IOException {
        var sidecars = DimensionOverrides.load(FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-space/dimensions"),
                input.definitions(), XenoSpaceConfig.bodyDefaults());
        List<String> errors = new ArrayList<>(input.errors()); errors.addAll(sidecars.errors());
        Set<String> dimensions = new HashSet<>();
        server.levelKeys().forEach(key -> dimensions.add(key.location().toString()));
        // Planet Hakai (design-doc 16b): erased bodies are left out of the chart, nothing is deleted.
        var candidate = ChartAssembly.build(net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.withoutErased(server, sidecars.definitions()),
                new JsonObject(), dimensions);
        ChartRegistry chart = candidate.chart(); errors.addAll(candidate.errors());
        configure(chart);
        chart.copyManualPositionsFrom(state.runtime.chart);
        long now = server.overworld().getGameTime();
        int solved = chart.evaluate(SpaceOrbitClock.seconds(now));
        // Resolve every chart-dependent sidecar before the commit point. An unavailable host is
        // diagnosed and omitted; an unexpected resolver failure aborts before the active chart changes.
        var resolvedDragonBallPlanets = net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetAssignments
                .resolvable(input.dragonBallPlanets(), hostId -> chart.byId(hostId) != null, errors);
        resolvedDragonBallPlanets = net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetAssignments
                .physicallySupported(resolvedDragonBallPlanets, hostId -> {
                    var host = chart.byId(hostId);
                    return host == null ? null : new net.bullettrain.xenopixelsmod.space.dragonball
                            .DragonBallPlanetAssignments.HostOrbitInfo(host.radiusBlocks(), host.mu());
                }, net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetAssignments
                        .PROTOTYPE_BODY_RADIUS_BLOCKS, errors);
        for (var belt : input.belts().values()) if (chart.byId(belt.parentId()) == null)
            errors.add(belt.id() + ": unavailable belt parent " + belt.parentId() + " (belt skipped)");
        List<String> publishedErrors = List.copyOf(errors);
        Set<String> publishedDimensions = sidecars.dimensions();
        Map<String, DimensionSpaceSettings> publishedSidecarSettings = sidecars.settings();
        long nextEvaluation = now + XenoSpaceConfig.gravityIntervalTicks();
        int nextRevision = state.runtime.revision + 1;
        state.runtime.probe.setEnabled(XenoSpaceConfig.perfProbeEnabled());
        if (log) {
            XenoPixelsMod.LOGGER.info("Space chart loaded: {} bodies, {} diagnostics, revision {}",
                    chart.size(), errors.size(), nextRevision);
            for (String error : errors) XenoPixelsMod.LOGGER.warn("Space chart: {}", error);
        }

        state.runtime.revision = nextRevision;
        state.lastVisualSignature = Long.MIN_VALUE;
        state.runtime.solved = solved;
        state.runtime.nextEvaluation = nextEvaluation;
        state.definitions = input.definitions();
        state.resourceErrors = input.errors();
        state.belts = input.belts(); state.beltRevision++;
        state.dragonBallPlanetDefinitions = input.dragonBallPlanets();
        state.dragonBallPlanets = resolvedDragonBallPlanets;
        state.errors = publishedErrors; state.sidecarDimensions = publishedDimensions;
        state.sidecarSettings = publishedSidecarSettings;
        // Commit last; the candidate and its diagnostics have been fully computed above.
        state.runtime.chart = chart;
    }
    private static void configure(ChartRegistry chart) {
        chart.setRealOrbits(XenoSpaceConfig.realOrbits()); chart.setProxyBudget(XenoSpaceConfig.proxyBudget());
        chart.setFieldRange(XenoSpaceConfig.fieldRange()); chart.setThresholds(XenoSpaceConfig.thresholds());
        chart.setGravityContributors(XenoSpaceConfig.gravityContributors());
        for (String rejected : chart.applyExitOverrides(XenoSpaceConfig.handoffExitOverrides()))
            XenoPixelsMod.LOGGER.warn("Ignoring invalid space handoff exit override: {}", rejected);
    }
    public static boolean reloadDimensions(MinecraftServer server) {
        State state = state(server);
        try {
            publish(server, state, new ChartResources.Input(state.definitions, state.resourceErrors, state.belts,
                    state.dragonBallPlanetDefinitions), true);
        }
        catch (Exception e) { failure(state, e); return false; }
        boolean persisted = persistDragonBallPlanetHosts(server, state);
        SpaceAtmosphereSync.sendAll(server);
        return persisted;
    }

    private static boolean persistDragonBallPlanetHosts(MinecraftServer server, State state) {
        try {
            if (state.dragonBallPlanets.isEmpty()) return true;
            net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetAssignments.persistResolvedAssignments(
                    net.bullettrain.xenopixelsmod.space.dragonball.DragonBallSavedData.get(server), state.dragonBallPlanets);
            return true;
        } catch (RuntimeException e) {
            List<String> errors = new ArrayList<>(state.errors);
            errors.add("chart published, but Super Dragon Ball assignments could not be persisted: " + e.getMessage());
            state.errors = List.copyOf(errors);
            XenoPixelsMod.LOGGER.error("Space chart published, but Super Dragon Ball assignment persistence failed", e);
            return false;
        }
    }
    public static List<String> validate(MinecraftServer server) throws IOException {
        state(server); // Enforce thread ownership even for dry runs.
        State candidate = new State();
        publish(server, candidate, ChartResources.scan(server.getResourceManager()), false);
        return candidate.errors;
    }
    private static void failure(State state, Exception e) {
        state.errors = List.of("space chart reload failed: " + e.getMessage());
        XenoPixelsMod.LOGGER.error("Space chart reload failed", e);
    }
    public static String status(MinecraftServer server) {
        State s = state(server);
        return XenoSpaceConfig.statusLine() + "\nchart bodies=" + s.runtime.chart.size()
                + " revision=" + s.runtime.revision + " diagnostics=" + s.errors.size()
                + " solved=" + s.runtime.solved + " drawn=" + s.lastVisualBodies;
    }
}
