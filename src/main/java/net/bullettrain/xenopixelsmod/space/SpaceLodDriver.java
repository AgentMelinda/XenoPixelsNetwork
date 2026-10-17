package net.bullettrain.xenopixelsmod.space;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Arrays;
import java.util.List;

/**
 * The live driver for §21's lazy-activation ladder: it walks the chart once per interval and moves each
 * body's {@link net.bullettrain.xenopixelsmod.space.math.BodyLod} to whatever the nearest observer
 * warrants, honouring the hysteresis thresholds and the global proxy budget that
 * {@link ChartRegistry#resolveLodsForObservers} already implements.
 *
 * <p>This slice is deliberately a <b>state machine only</b>. It mutates nothing but the ladder stage
 * itself — no chunk loading, no gravity, no anchor warm-up. Those consumers (§11's bounded FIELD and
 * ANCHOR activation) are later phases that read the stage this pass writes. Keeping the producer
 * separate lets the groundwork measure the resolution cost against a real server before any activation
 * side effects ride on top of it.
 *
 * <p><b>Why a scheduler pass and not the {@code ServerTickEvent} handler.</b> The ladder is per-server
 * and cheap, so it belongs on the same budgeted, exception-isolated, interval-throttled clock the rest
 * of the space work uses rather than as another thing competing for the main tick. The pass runs inside
 * Sable's physics tick, which — as {@code OrbitalGravitySystem} demonstrates by mutating
 * {@code ServerSubLevel}s there — is the server thread, so reading the chart through
 * {@link SpaceChartService#state} is thread-legal. Should that ever change, {@code state} throws and
 * the scheduler isolates the failure to this one pass instead of taking the tick down.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SpaceLodDriver implements SpaceTickScheduler.Pass {
    private static final SpaceLodDriver PASS = new SpaceLodDriver();

    /**
     * Observer scratch, reused across sweeps. Grows once to the highest concurrent player-in-bound-
     * dimension count seen and never shrinks, so a steady-state server allocates nothing per resolution.
     */
    private double[] obsX = new double[16];
    private double[] obsY = new double[16];
    private double[] obsZ = new double[16];

    private SpaceLodDriver() {}

    /**
     * Registration is gated on {@link XenoSpaceConfig#enabled()} so that switching space off means the
     * pass is never invoked, not that it is invoked and returns — the difference is the whole cost of
     * the ladder, and the config's own javadoc has promised this since §21 was written. Flipping the
     * switch back on needs a restart; {@code run()} keeps its early return so off is immediate.
     */
    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        if (XenoSpaceConfig.enabled()) SpaceTickScheduler.register(PASS);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SpaceTickScheduler.unregister(PASS); }

    @Override public String name() { return "lod-ladder"; }

    @Override public int intervalTicks() { return XenoSpaceConfig.lodIntervalTicks(); }

    /**
     * The chart is a single server-wide ladder, so exactly one physics system should drive it per sweep.
     * Keying on the host overworld makes that deterministic; every other dimension's physics system ticks
     * the same chart and would otherwise race it. This pass is the clock only — the observers it collects
     * come from all chart-bound dimensions, not just this level.
     */
    @Override
    public boolean appliesTo(SubLevelPhysicsSystem system) {
        ServerLevel level = system.getLevel();
        return level != null && level == level.getServer().overworld();
    }

    @Override
    public int run(SubLevelPhysicsSystem system, double deltaSeconds) {
        if (!XenoSpaceConfig.enabled()) return 0;
        MinecraftServer server = system.getLevel().getServer();
        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        if (chart.size() == 0) return 0;
        int observers = collectObservers(server, chart);
        // inView is true server-side: the frustum is a per-viewpoint render concern, and the purely
        // visual stages narrow it on the client. On the server, a body the player can reach is relevant
        // whether or not it happens to be on screen this tick.
        return chart.resolveLodsForObservers(obsX, obsY, obsZ, observers, true);
    }

    /**
     * Collect the observers that drive the ladder, in the two shapes a space observer can be in.
     *
     * <p>A player standing inside a <b>chart-bound</b> dimension is represented at that body's live chart
     * position, giving the body they occupy a distance of zero and therefore {@code CHUNKS}. A player
     * flying a dimension that {@link SpaceDimensions} calls space without binding a body to it — the
     * shipped {@code xenospace_spike:orbit} host — is represented at their own position, because chart
     * space and that dimension's world coordinates are the same numbers (that is what the visual proxies
     * draw at). Both cases matter: keyed on chart bindings alone, every body stays {@code ABSENT} in the
     * only dimension anyone can currently enter, which is how the groundwork ended up with an empty sky.
     *
     * <p>Players in an ordinary dimension are skipped: the groundwork has no production Voidsea to place
     * them in, and an overworld farmer must not pin 200 charted bodies open from a field. With no
     * qualifying players at all, {@code count == 0} resolves every body at infinite distance — all
     * {@code ABSENT} — which is §21's idle cost for a chart-bound world with nobody in it.
     *
     * @return how many observers were written into the scratch arrays
     */
    private int collectObservers(MinecraftServer server, ChartRegistry chart) {
        int count = 0;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        var listed = XenoSpaceConfig.spaceDimensions();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            var dimension = player.level().dimension().location();
            int slot = chart.slotFor(dimension);
            double x, y, z;
            if (slot >= 0) {
                x = chart.x(slot); y = chart.y(slot); z = chart.z(slot);
            } else if (listed.contains(dimension)) {
                x = player.getX(); y = player.getY(); z = player.getZ();
            } else continue;
            if (count == obsX.length) {
                int next = obsX.length * 2;
                obsX = Arrays.copyOf(obsX, next);
                obsY = Arrays.copyOf(obsY, next);
                obsZ = Arrays.copyOf(obsZ, next);
            }
            obsX[count] = x;
            obsY[count] = y;
            obsZ[count] = z;
            count++;
        }
        return count;
    }
}
