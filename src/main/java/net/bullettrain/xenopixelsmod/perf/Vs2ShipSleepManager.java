package net.bullettrain.xenopixelsmod.perf;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.QueryableShipData;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VS2 Hot/Cold ship tiers using {@link ServerShip#setStatic(boolean)}.
 * Gameplay-safe: only parks ships we put to sleep; pilot proximity wakes instantly.
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class Vs2ShipSleepManager {
    /** Ships we cold-parked (may safely setStatic(false) on wake). */
    private static final Set<Long> WE_PARKED = ConcurrentHashMap.newKeySet();
    /** Consecutive evals eligible for sleep. */
    private static final Map<Long, Integer> IDLE_STREAK = new ConcurrentHashMap<>();
    /** Force-hot until server tick. */
    private static final Map<Long, Integer> FORCE_HOT_UNTIL = new ConcurrentHashMap<>();

    private static int lastDynamic;
    private static int lastStatic;
    private static int lastParkedByUs;

    private Vs2ShipSleepManager() {}

    public static int lastDynamicCount() {
        return lastDynamic;
    }

    public static int lastStaticCount() {
        return lastStatic;
    }

    public static int lastParkedByUsCount() {
        return lastParkedByUs;
    }

    public static void forceHot(long shipId, int ticks) {
        FORCE_HOT_UNTIL.put(shipId, Math.max(FORCE_HOT_UNTIL.getOrDefault(shipId, 0), ticks));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;

        MsptWatchdog.onServerTick(server);
        CreatePerfHooks.onServerTick(server);

        if (!XenoPerfConfig.perfEnabled || !XenoPerfConfig.vs2SleepEnabled) return;
        if (server.getTickCount() % Math.max(5, XenoPerfConfig.vs2EvalIntervalTicks) != 0) return;

        try {
            evaluate(server);
        } catch (Throwable t) {
            // VS API mismatch must never crash the server
            if (server.getTickCount() % 200 == 0) {
                XenoPixelsMod.LOGGER.debug("VS2 ship sleep eval skipped: {}", t.toString());
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!XenoPerfConfig.perfEnabled || !XenoPerfConfig.vs2SleepEnabled) return;
        if (event.getEntity().level().isClientSide()) return;
        if (event.getAmount() < 0.05f) return;
        try {
            wakeEntityShip(event.getEntity(), 100);
            Entity src = event.getSource().getEntity();
            if (src != null) wakeEntityShip(src, 100);
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        WE_PARKED.clear();
        IDLE_STREAK.clear();
        FORCE_HOT_UNTIL.clear();
        MsptWatchdog.reset();
        CreatePerfHooks.clear();
    }

    private static void wakeEntityShip(Entity entity, int ticks) {
        if (entity == null) return;
        var ship = VSGameUtilsKt.getShipManaging(entity);
        if (ship != null) {
            forceHot(ship.getId(), ticks);
            if (ship instanceof ServerShip ss && WE_PARKED.contains(ss.getId())) {
                ss.setStatic(false);
                WE_PARKED.remove(ss.getId());
                CreatePerfHooks.onShipWoken(ss.getId());
            }
        }
    }

    private static void evaluate(MinecraftServer server) {
        VsiServerShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(server);
        if (shipWorld == null) return;

        QueryableShipData<LoadedServerShip> loaded = shipWorld.getLoadedShips();
        if (loaded == null || loaded.isEmpty()) {
            lastDynamic = lastStatic = lastParkedByUs = 0;
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        double hotR = MsptWatchdog.effectiveHotRange();
        double warmR = MsptWatchdog.effectiveWarmRange();
        double hotSq = hotR * hotR;
        double warmSq = warmR * warmR;
        double sleepSpeed = XenoPerfConfig.vs2SleepLinearSpeed;
        int needStreak = Math.max(1, (XenoPerfConfig.vs2IdleSeconds * 20)
                / Math.max(5, XenoPerfConfig.vs2EvalIntervalTicks));
        boolean stress = MsptWatchdog.isStressed();

        // decay force-hot
        int interval = Math.max(5, XenoPerfConfig.vs2EvalIntervalTicks);
        Iterator<Map.Entry<Long, Integer>> fit = FORCE_HOT_UNTIL.entrySet().iterator();
        while (fit.hasNext()) {
            Map.Entry<Long, Integer> e = fit.next();
            int left = e.getValue() - interval;
            if (left <= 0) fit.remove();
            else e.setValue(left);
        }

        List<LoadedServerShip> dynamicList = new ArrayList<>();
        int staticCount = 0;
        int dynamicCount = 0;

        for (LoadedServerShip ship : loaded) {
            long id = ship.getId();
            boolean forceHot = FORCE_HOT_UNTIL.getOrDefault(id, 0) > 0;
            boolean playerNearHot = anyPlayerNearShip(ship, players, hotSq);
            boolean playerNearWarm = playerNearHot || anyPlayerNearShip(ship, players, warmSq);
            boolean playerOnShip = anyPlayerOnShip(ship, players);

            double speed = speed(ship);
            boolean slow = speed <= sleepSpeed;

            // Always wake if player close / on ship / combat
            if (forceHot || playerNearHot || playerOnShip) {
                IDLE_STREAK.remove(id);
                if (ship.isStatic() && WE_PARKED.contains(id)) {
                    ship.setStatic(false);
                    WE_PARKED.remove(id);
                    CreatePerfHooks.onShipWoken(id);
                }
                if (!ship.isStatic()) {
                    dynamicCount++;
                    dynamicList.add(ship);
                } else {
                    staticCount++;
                }
                continue;
            }

            // Far + slow → count toward sleep; stress parks immediately beyond warm
            boolean coldCandidate = !playerNearWarm && slow;
            if (stress && XenoPerfConfig.msptForceColdFarShips && !playerNearWarm) {
                coldCandidate = true;
            }

            if (coldCandidate) {
                int streak = IDLE_STREAK.merge(id, 1, Integer::sum);
                boolean parkNow = stress && XenoPerfConfig.msptForceColdFarShips && !playerNearWarm
                        || streak >= needStreak;
                if (parkNow) {
                    if (!ship.isStatic()) {
                        ship.setStatic(true);
                        WE_PARKED.add(id);
                        CreatePerfHooks.onShipColdParked(id);
                    }
                    staticCount++;
                } else if (!ship.isStatic()) {
                    dynamicCount++;
                    dynamicList.add(ship);
                } else {
                    staticCount++;
                }
            } else {
                IDLE_STREAK.remove(id);
                // Moving but only warm-range: keep dynamic
                if (ship.isStatic() && WE_PARKED.contains(id) && playerNearWarm) {
                    ship.setStatic(false);
                    WE_PARKED.remove(id);
                    CreatePerfHooks.onShipWoken(id);
                }
                if (!ship.isStatic()) {
                    dynamicCount++;
                    dynamicList.add(ship);
                } else {
                    staticCount++;
                }
            }
        }

        // Global active-ship budget: park oldest excess dynamic ships that are not near players
        int max = XenoPerfConfig.vs2MaxActiveShipsGlobal;
        if (dynamicList.size() > max) {
            dynamicList.sort(Comparator.comparingDouble(s -> minPlayerDistSq(s, players)));
            // sort ascending distance — park farthest first
            dynamicList.sort((a, b) -> Double.compare(minPlayerDistSq(b, players), minPlayerDistSq(a, players)));
            int excess = dynamicList.size() - max;
            for (int i = 0; i < excess && i < dynamicList.size(); i++) {
                LoadedServerShip s = dynamicList.get(i);
                if (anyPlayerNearShip(s, players, hotSq) || anyPlayerOnShip(s, players)) continue;
                if (FORCE_HOT_UNTIL.getOrDefault(s.getId(), 0) > 0) continue;
                if (!s.isStatic()) {
                    s.setStatic(true);
                    WE_PARKED.add(s.getId());
                    CreatePerfHooks.onShipColdParked(s.getId());
                    dynamicCount--;
                    staticCount++;
                }
            }
        }

        lastDynamic = Math.max(0, dynamicCount);
        lastStatic = staticCount;
        lastParkedByUs = WE_PARKED.size();

        // Drop stale idle entries
        IDLE_STREAK.keySet().removeIf(id -> {
            LoadedServerShip s = loaded.getById(id);
            return s == null;
        });
        WE_PARKED.removeIf(id -> loaded.getById(id) == null);
    }

    private static double speed(ServerShip ship) {
        try {
            Vector3dc v = ship.getVelocity();
            if (v == null) return 0.0;
            return Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
        } catch (Throwable t) {
            return 0.0;
        }
    }

    private static boolean anyPlayerNearShip(LoadedServerShip ship, List<ServerPlayer> players, double rangeSq) {
        double[] c = shipWorldCenter(ship);
        if (c == null) return false;
        for (ServerPlayer p : players) {
            double dx = p.getX() - c[0];
            double dy = p.getY() - c[1];
            double dz = p.getZ() - c[2];
            if (dx * dx + dy * dy + dz * dz <= rangeSq) return true;
        }
        return false;
    }

    private static boolean anyPlayerOnShip(LoadedServerShip ship, List<ServerPlayer> players) {
        long id = ship.getId();
        for (ServerPlayer p : players) {
            var s = VSGameUtilsKt.getShipManaging(p);
            if (s != null && s.getId() == id) return true;
            var stood = VSGameUtilsKt.getShipStoodOn(p);
            if (stood != null && stood.getId() == id) return true;
        }
        return false;
    }

    private static double minPlayerDistSq(LoadedServerShip ship, List<ServerPlayer> players) {
        double[] c = shipWorldCenter(ship);
        if (c == null) return Double.MAX_VALUE;
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            double dx = p.getX() - c[0];
            double dy = p.getY() - c[1];
            double dz = p.getZ() - c[2];
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return best;
    }

    /** World-space ship center from transform (no joml.primitives dependency). */
    private static double[] shipWorldCenter(LoadedServerShip ship) {
        try {
            Vector3dc pos = ship.getTransform() != null ? ship.getTransform().getPositionInWorld() : null;
            if (pos != null) {
                return new double[]{pos.x(), pos.y(), pos.z()};
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
