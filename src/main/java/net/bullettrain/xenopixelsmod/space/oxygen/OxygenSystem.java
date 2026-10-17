package net.bullettrain.xenopixelsmod.space.oxygen;

import com.google.gson.JsonObject;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.combat.v3.V3Resources;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidPhysicalizer;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of design-doc §6 oxygen (switch {@link XenoSpaceConfig#oxygenEnabled()}, default off). Every 10 ticks each
 * survival player is classified as in vacuum or not and stepped through {@link OxygenRules}; while suffocating, once per
 * second they pay {@code oxygenKiPerSecond} ki through {@link V3Resources#spend} and, only if that fails, take
 * {@code oxygenDamagePerSecond} drowning damage. Breathing Capsule supply persists across death and relog in the player's
 * persisted NBT; lungs reset to full on login and respawn. The client gets an {@link OxygenStatus} whenever it changes.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class OxygenSystem {
    static final String RESERVE_KEY = "xenopixelsmod_oxygen_reserve";
    private static final int PERIOD = 10;
    private static final class Tracked { OxygenRules.State state; OxygenStatus lastSent; int secondTicks; }
    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();

    private OxygenSystem() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % PERIOD != 0) return;
        if (!XenoSpaceConfig.oxygenEnabled()) {
            if (!TRACKED.isEmpty()) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) clearHud(player);
                TRACKED.clear();
            }
            return;
        }
        int maxBreath = XenoSpaceConfig.oxygenBreathSeconds() * 20;
        double refillPerTick = XenoSpaceConfig.oxygenRefillPerSecond();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Tracked tracked = TRACKED.computeIfAbsent(player.getUUID(), ignored -> fresh(player, maxBreath));
            boolean exempt = player.isCreative() || player.isSpectator() || !player.isAlive();
            boolean vacuum = !exempt && inVacuum(server, player);
            var before = tracked.state;
            // Design-doc 15: a black hole tidal band makes oxygen run out faster (factor 1 elsewhere or with holes off).
            int drain = vacuum ? PERIOD * net.bullettrain.xenopixelsmod.space.blackhole.BlackHoleSystem.oxygenFactor(player) : PERIOD;
            tracked.state = OxygenRules.step(before, vacuum, drain, maxBreath, refillPerTick);
            if (tracked.state.reserve() != before.reserve()) writeReserve(player, tracked.state.reserve());
            if (tracked.state.phase() == OxygenRules.Phase.SUFFOCATING) {
                tracked.secondTicks += PERIOD;
                if (tracked.secondTicks >= 20) {
                    tracked.secondTicks = 0;
                    suffocate(player);
                }
            } else tracked.secondTicks = 0;
            sync(player, tracked, maxBreath);
        }
    }

    /** Vacuum: the Voidsea host, a listed space dimension without a body, a non-breathable body, or above a body's exit altitude. */
    static boolean inVacuum(MinecraftServer server, ServerPlayer player) {
        ResourceLocation dimension = player.level().dimension().location();
        boolean vacuum;
        if (PhysicalEarth.HOST.equals(dimension)) vacuum = true;
        else {
            var state = SpaceChartService.state(server);
            int slot = state.runtime.chart.slotFor(dimension);
            if (slot >= 0) {
                var body = state.runtime.chart.body(slot);
                // The effective exit (config/command overrides included), the same value SpaceHandoffPass crosses at.
                vacuum = OxygenRules.inVacuum(breathable(state.definitions.get(body.id())), player.getY(),
                        state.runtime.chart.exitAltitude(slot));
            } else vacuum = XenoSpaceConfig.spaceDimensions().contains(dimension);
        }
        return vacuum && !pressurized(player);
    }

    /** {@code "breathable": false} in a body's chart JSON makes its dimension vacuum; absent means breathable. */
    static boolean breathable(JsonObject definition) {
        return definition == null || !definition.has("breathable") || definition.get("breathable").getAsBoolean();
    }

    /**
     * Inside the world bounds of a ship sub-level (not a managed planet or asteroid) counts as a pressurized cabin.
     * An approximation: hull integrity and open cockpits are not modelled yet (§6 hull tie-in).
     */
    static boolean pressurized(ServerPlayer player) {
        if (!XenoSpaceConfig.oxygenShipsPressurized() || !(player.level() instanceof ServerLevel level)) return false;
        double x = player.getX(), y = player.getY() + player.getEyeHeight(), z = player.getZ();
        // Sub-level bounding boxes are world-space (the S11 probe lists them around each body's world position), the same
        // frame as the player's coordinates.
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return false;
        for (var sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel body) || body.isRemoved() || PhysicalEarth.isManaged(body) || AsteroidPhysicalizer.isAsteroid(body)) continue;
            var box = body.boundingBox();
            if (x >= box.minX() && x <= box.maxX() && y >= box.minY() && y <= box.maxY() && z >= box.minZ() && z <= box.maxZ()) return true;
        }
        return false;
    }

    private static void suffocate(ServerPlayer player) {
        float ki = (float) XenoSpaceConfig.oxygenKiPerSecond();
        if (ki > 0f && V3Resources.spend(player, ki, 0f)) return;
        float damage = (float) XenoSpaceConfig.oxygenDamagePerSecond();
        if (damage > 0f) player.hurt(player.damageSources().drown(), damage);
    }

    /** Adds Breathing Capsule supply to an online player; returns false when it is already at the cap. */
    public static boolean addCapsule(ServerPlayer player) {
        int maxBreath = XenoSpaceConfig.oxygenBreathSeconds() * 20;
        Tracked tracked = TRACKED.computeIfAbsent(player.getUUID(), ignored -> fresh(player, maxBreath));
        int cap = XenoSpaceConfig.oxygenReserveCapSeconds() * 20;
        int reserve = OxygenRules.addReserve(tracked.state.reserve(), XenoSpaceConfig.oxygenCapsuleSeconds() * 20, cap);
        if (reserve == tracked.state.reserve()) return false;
        tracked.state = new OxygenRules.State(tracked.state.breath(), reserve, tracked.state.phase());
        writeReserve(player, reserve);
        sync(player, tracked, maxBreath);
        return true;
    }

    private static Tracked fresh(ServerPlayer player, int maxBreath) {
        Tracked tracked = new Tracked();
        tracked.state = new OxygenRules.State(maxBreath, readReserve(player), OxygenRules.Phase.SAFE);
        return tracked;
    }

    private static void sync(ServerPlayer player, Tracked tracked, int maxBreath) {
        var status = new OxygenStatus(tracked.state.breath(), maxBreath, tracked.state.reserve(), tracked.state.phase().ordinal());
        if (status.equals(tracked.lastSent)) return;
        tracked.lastSent = status;
        AddonNetwork.sendToPlayer(player, status);
    }

    private static void clearHud(ServerPlayer player) {
        int maxBreath = XenoSpaceConfig.oxygenBreathSeconds() * 20;
        AddonNetwork.sendToPlayer(player, new OxygenStatus(maxBreath, maxBreath, 0, OxygenRules.Phase.SAFE.ordinal()));
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static int readReserve(Player player) { return Math.max(0, persisted(player).getInt(RESERVE_KEY)); }

    private static void writeReserve(Player player, int reserve) { persisted(player).putInt(RESERVE_KEY, reserve); }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { TRACKED.remove(event.getEntity().getUUID()); }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { TRACKED.remove(event.getEntity().getUUID()); }

    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { TRACKED.remove(event.getEntity().getUUID()); }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { TRACKED.clear(); }
}
