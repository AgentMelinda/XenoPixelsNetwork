package net.bullettrain.xenopixelsmod.space.hakai;

import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.SpaceFlightGuard;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planet Hakai (design-doc 16b / 22; owner 2026-10-16: "a configuration for hakai to be able to do it to planets").
 * Switch {@link XenoSpaceConfig#hakaiPlanets()} ({@code off} by default). When an area Hakai fires in the Voidsea, the
 * charted body the caster is looking at (ray-sphere test against the chart, so far, non-physical planets count too,
 * up to {@code hakaiPlanetReach}) is checked against the policy; if allowed it burns for {@code hakaiPlanetEraseTicks}
 * and is then erased: recorded in {@link ErasedBodiesSavedData} and the chart is republished without it, which drops it
 * from the physical bodies, the client sky/HD planets, gravity and the handoff. Nothing is deleted from any dimension.
 * Its moons go with it (a star's planets do not). Occupants of its dimension are thrown out into the Voidsea where it was. {@code /xenospace hakai restore} (or the
 * restore timer) brings it back.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlanetHakai {
    private record Burn(String id, String name, String caster, Vec3 centre, double radius, long start) {}

    private static final Map<String, Burn> BURNING = new LinkedHashMap<>();
    /** Erased ids as of the last chart publish, for code that has no server handle (PhysicalEarth.specsFor). */
    private static volatile java.util.Set<String> erasedCache = java.util.Set.of();

    public static boolean isErased(String id) { return erasedCache.contains(id); }
    private static long nextRestoreCheck;

    private PlanetHakai() {}

    /** Called by HakaiAreaSystem when an area Hakai fires. Returns true when a planet was taken as the target. */
    public static boolean onAreaHakaiFired(ServerPlayer caster) {
        var mode = PlanetHakaiRules.Mode.parse(XenoSpaceConfig.hakaiPlanets());
        XenoPixelsMod.LOGGER.info("Planet Hakai: area Hakai fired by {} in {} (mode {})", caster.getScoreboardName(), caster.level().dimension().location(), mode);
        if (mode == PlanetHakaiRules.Mode.OFF || !PhysicalEarth.HOST.equals(caster.level().dimension().location())) return false;
        MinecraftServer server = caster.getServer();
        var chart = SpaceChartService.state(server).runtime.chart;
        Vec3 eye = caster.getEyePosition(), look = caster.getLookAngle();
        int best = -1;
        double bestHit = Double.POSITIVE_INFINITY;
        ServerLevel voidsea = (ServerLevel) caster.level();
        Vec3 bestCentre = null;
        for (int slot = 0; slot < chart.size(); slot++) {
            CelestialBody body = chart.body(slot);
            if (body.kind() == CelestialBody.BodyKind.BLACK_HOLE) continue;
            Vec3 centre = voidseaCentre(voidsea, chart, slot);
            double hit = PlanetHakaiRules.raySphere(eye.x, eye.y, eye.z, look.x, look.y, look.z,
                    centre.x, centre.y, centre.z, body.radiusBlocks() * AIM_SLACK, XenoSpaceConfig.hakaiPlanetReach());
            if (hit >= 0 && hit < bestHit) { bestHit = hit; best = slot; bestCentre = centre; }
        }
        if (best < 0) {
            XenoPixelsMod.LOGGER.info("Planet Hakai: no planet on the look ray from {} dir {} (reach {})", eye, look, XenoSpaceConfig.hakaiPlanetReach());
            return false;
        }
        CelestialBody body = chart.body(best);
        var verdict = PlanetHakaiRules.verdict(mode, body.id(), body.erasable(), XenoSpaceConfig.hakaiPlanetProtected(),
                permission(caster), XenoSpaceConfig.hakaiPlanetPermissionLevel(),
                ErasedBodiesSavedData.get(server).isErased(body.id()) || BURNING.containsKey(body.id()));
        XenoPixelsMod.LOGGER.info("Planet Hakai: {} aimed at {} centre {} hit at {} blocks -> {}", caster.getScoreboardName(), body.id(), bestCentre,
                Math.round(bestHit), verdict);
        if (verdict != PlanetHakaiRules.Verdict.ALLOWED) {
            caster.displayClientMessage(Component.translatable("message.xenopixelsmod.planet_hakai_refused." + verdict.name().toLowerCase(),
                    body.displayName()), true);
            return false;
        }
        start(server, body.id(), body.displayName(), caster.getScoreboardName(), bestCentre, body.radiusBlocks());
        return true;
    }

    /** Aiming tolerance: the sphere a player must look at is this much larger than the planet. */
    private static final double AIM_SLACK = 1.15;

    /**
     * Where the body actually is in the Voidsea: its live physical centre when built, otherwise its chart position on
     * the staging plane PhysicalEarth uses (y = max(128, min build height + radius + 2), plus any charted y offset).
     */
    static Vec3 voidseaCentre(ServerLevel voidsea, net.bullettrain.xenopixelsmod.space.chart.ChartRegistry chart, int slot) {
        CelestialBody body = chart.body(slot);
        var live = PhysicalEarth.liveCenter(voidsea, body.id());
        if (live != null) return new Vec3(live.x(), live.y(), live.z());
        double plane = Math.max(128, voidsea.getMinBuildHeight() + Math.round(body.radiusBlocks()) + 2);
        return new Vec3(chart.x(slot), plane + chart.y(slot), chart.z(slot));
    }

    /** Admin path ({@code /xenospace hakai erase}): skips aiming and permission, still honours protection. */
    public static String adminErase(MinecraftServer server, String id, String by) {
        var chart = SpaceChartService.state(server).runtime.chart;
        int slot = chart.slotOf(id);
        if (slot < 0) return "no charted body " + id;
        if (XenoSpaceConfig.hakaiPlanetProtected().contains(id)) return id + " is protected (hakaiPlanetProtected)";
        if (ErasedBodiesSavedData.get(server).isErased(id) || BURNING.containsKey(id)) return id + " is already erased or burning";
        CelestialBody body = chart.body(slot);
        ServerLevel voidsea = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        Vec3 centre = voidsea == null ? new Vec3(chart.x(slot), chart.y(slot), chart.z(slot)) : voidseaCentre(voidsea, chart, slot);
        start(server, id, body.displayName(), by, centre, body.radiusBlocks());
        return "burning " + id;
    }

    private static void start(MinecraftServer server, String id, String name, String by, Vec3 centre, double radius) {
        BURNING.put(id, new Burn(id, name, by, centre, radius, server.overworld().getGameTime()));
        var burn = new PlanetHakaiBurn(id, XenoSpaceConfig.hakaiPlanetEraseTicks(), true);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, burn);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("message.xenopixelsmod.planet_hakai_start", by, name), false);
        XenoPixelsMod.LOGGER.info("Planet Hakai: {} began erasing {} (r={}) at {}", by, id, radius, centre);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now >= nextRestoreCheck) { nextRestoreCheck = now + 200; restoreDue(server); }
        if (BURNING.isEmpty()) return;
        ServerLevel voidsea = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        for (var burn : new ArrayList<>(BURNING.values())) {
            double progress = PlanetHakaiRules.progress(burn.start, now, XenoSpaceConfig.hakaiPlanetEraseTicks());
            if (voidsea != null && now % 4 == 0) burnFx(voidsea, burn, progress);
            if (progress < 1) continue;
            BURNING.remove(burn.id);
            finish(server, voidsea, burn);
        }
    }

    /**
     * Hakai around the planet, following the cut that sweeps from top to bottom: a dense violet ring of dust and sparks
     * on the cut circle drifting upward off it, and a thin veil over the part still standing. Matches the client's
     * clip (smoothstep of the same progress) so the dust stays on the edge.
     */
    private static void burnFx(ServerLevel voidsea, Burn burn, double progress) {
        var dust = new DustParticleOptions(new Vector3f(0.62f, 0.2f, 0.95f), 4.0f);
        var spark = new DustParticleOptions(new Vector3f(0.95f, 0.75f, 1.0f), 2.5f);
        var random = voidsea.getRandom();
        double eased = progress * progress * (3 - 2 * progress);
        double cut = 1 - 2 * eased, ring = Math.sqrt(Math.max(0, 1 - cut * cut)) * burn.radius;
        double y = burn.centre.y + cut * burn.radius;
        int edge = 40 + (int) (ring / 2);
        for (int i = 0; i < edge; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = ring * (0.97 + 0.08 * random.nextDouble());
            double lift = random.nextDouble() * Math.max(2, burn.radius * 0.08);
            farParticles(voidsea, i % 3 == 0 ? spark : dust, burn.centre.x + Math.cos(a) * r, y + lift, burn.centre.z + Math.sin(a) * r,
                    1, 0.3, 0.6, 0.3, 0.0);
        }
        for (int i = 0; i < 24; i++) {
            double zz = -1 + random.nextDouble() * (cut + 1), a = random.nextDouble() * Math.PI * 2, s = Math.sqrt(Math.max(0, 1 - zz * zz));
            double r = burn.radius * 1.03;
            farParticles(voidsea, dust, burn.centre.x + Math.cos(a) * s * r, burn.centre.y + zz * r, burn.centre.z + Math.sin(a) * s * r, 1, 0.4, 0.4, 0.4, 0.0);
        }
        if ((voidsea.getGameTime() / 4) % 10 == 0)
            voidsea.playSound(null, burn.centre.x, y, burn.centre.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS,
                    (float) Math.min(64, burn.radius / 4), 0.5f + (float) progress);
    }

    /** Long-distance particles (vanilla's default only reaches 32 blocks; a planet is watched from hundreds). */
    private static void farParticles(ServerLevel level, DustParticleOptions options, double x, double y, double z, int count,
                                     double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) level.sendParticles(player, options, true, x, y, z, count, dx, dy, dz, speed);
    }

    private static void finish(MinecraftServer server, ServerLevel voidsea, Burn burn) {
        var stop = new PlanetHakaiBurn(burn.id, 1, false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, stop);
        if (!ErasedBodiesSavedData.get(server).erase(burn.id, burn.caster, System.currentTimeMillis())) return;
        String dimension = dimensionOf(server, burn.id);
        republish(server);
        if (voidsea != null) {
            voidsea.playSound(null, burn.centre.x, burn.centre.y, burn.centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                    (float) Math.min(96, burn.radius / 2), 0.4f);
            if (XenoSpaceConfig.hakaiPlanetEjectOccupants() && dimension != null) eject(server, voidsea, dimension, burn);
        }
        server.getPlayerList().broadcastSystemMessage(Component.translatable("message.xenopixelsmod.planet_hakai_done", burn.caster, burn.name), false);
        XenoPixelsMod.LOGGER.info("Planet Hakai: {} erased {} (dimension {})", burn.caster, burn.id, dimension);
    }

    /** Players inside the erased world's dimension float out into the Voidsea just outside where it stood. */
    private static void eject(MinecraftServer server, ServerLevel voidsea, String dimension, Burn burn) {
        ResourceLocation key = ResourceLocation.tryParse(dimension);
        ServerLevel level = key == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
        if (level == null || level == voidsea) return;
        for (ServerPlayer player : new ArrayList<>(level.players())) {
            double y = burn.centre.y + burn.radius + 48;
            player.teleportTo(voidsea, burn.centre.x, y, burn.centre.z, player.getYRot(), player.getXRot());
            SpaceFlightGuard.grant(player);
            player.displayClientMessage(Component.translatable("message.xenopixelsmod.planet_hakai_ejected", burn.name), false);
        }
    }

    public static String restore(MinecraftServer server, String id) {
        if (!ErasedBodiesSavedData.get(server).restore(id)) return id + " is not erased";
        republish(server);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("message.xenopixelsmod.planet_hakai_restored", id), false);
        return "restored " + id;
    }

    public static String restoreAll(MinecraftServer server) {
        int n = ErasedBodiesSavedData.get(server).restoreAll();
        if (n > 0) republish(server);
        return "restored " + n + " bodies";
    }

    private static void restoreDue(MinecraftServer server) {
        int minutes = XenoSpaceConfig.hakaiPlanetRestoreMinutes();
        if (minutes <= 0) return;
        var data = ErasedBodiesSavedData.get(server);
        long nowMillis = System.currentTimeMillis();
        for (var entry : new ArrayList<>(data.erased().entrySet()))
            if (PlanetHakaiRules.dueForRestore(entry.getValue().erasedAtMillis(), nowMillis, minutes)) restore(server, entry.getKey());
    }

    /** Rebuilds the chart (now without / with the body) and lets the physical planets follow it. */
    private static void republish(MinecraftServer server) {
        SpaceChartService.reloadDimensions(server);
        PhysicalEarth.applyMode(server);
    }

    /** Filter used by SpaceChartService before assembling the chart. */
    public static Map<String, JsonObject> withoutErased(MinecraftServer server, Map<String, JsonObject> definitions) {
        var erased = ErasedBodiesSavedData.get(server).erased();
        erasedCache = java.util.Set.copyOf(erased.keySet());
        if (erased.isEmpty()) return definitions;
        // A planet's moons (and their moons) go with it; left behind they would fall back to root and jump to the origin.
        // Planets do NOT go with an erased star: they keep their (star-centred) orbits as root bodies (design-doc 16b,
        // "other planets' orbits don't care").
        var gone = new java.util.HashSet<String>(erased.keySet());
        boolean grew = true;
        while (grew) {
            grew = false;
            for (var entry : definitions.entrySet()) {
                String parent = parentOf(entry.getValue());
                if (gone.contains(entry.getKey()) || !gone.contains(parent) || isStar(definitions.get(parent))) continue;
                gone.add(entry.getKey());
                grew = true;
            }
        }
        var out = new LinkedHashMap<String, JsonObject>();
        definitions.forEach((id, json) -> { if (!gone.contains(id)) out.put(id, json); });
        return out;
    }

    private static boolean isStar(JsonObject json) {
        try { return json != null && json.has("kind") && "star".equalsIgnoreCase(json.get("kind").getAsString()); }
        catch (RuntimeException malformed) { return false; }
    }

    private static String parentOf(JsonObject json) {
        try {
            if (json.has("orbit") && json.getAsJsonObject("orbit").has("parent")) return json.getAsJsonObject("orbit").get("parent").getAsString();
            if (json.has("parent") && !json.get("parent").isJsonNull()) return json.get("parent").getAsString();
        } catch (RuntimeException malformed) {
            return null;
        }
        return null;
    }

    public static String status(MinecraftServer server) {
        var out = new StringBuilder("planet hakai: mode=" + XenoSpaceConfig.hakaiPlanets() + " protected=" + XenoSpaceConfig.hakaiPlanetProtected()
                + " permission=" + XenoSpaceConfig.hakaiPlanetPermissionLevel() + " reach=" + (int) XenoSpaceConfig.hakaiPlanetReach()
                + " burnTicks=" + XenoSpaceConfig.hakaiPlanetEraseTicks() + " restoreMinutes=" + XenoSpaceConfig.hakaiPlanetRestoreMinutes());
        BURNING.keySet().forEach(id -> out.append("\n burning: ").append(id));
        ErasedBodiesSavedData.get(server).erased().forEach((id, e) -> out.append("\n erased: ").append(id).append(" by ").append(e.by()));
        return out.toString();
    }

    /** Command input: a bare body name means our namespace ("mars" -> "xenopixelsmod:mars"). */
    public static String key(String id) {
        String trimmed = id.trim();
        return trimmed.contains(":") ? trimmed : "xenopixelsmod:" + trimmed;
    }

    private static String dimensionOf(MinecraftServer server, String id) {
        var chart = SpaceChartService.state(server).runtime.chart;
        int slot = chart.slotOf(id);
        return slot < 0 ? null : chart.body(slot).dimensionId();
    }

    private static int permission(ServerPlayer player) {
        for (int level = 4; level > 0; level--) if (player.hasPermissions(level)) return level;
        return 0;
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { BURNING.clear(); nextRestoreCheck = 0; erasedCache = java.util.Set.of(); }
}
