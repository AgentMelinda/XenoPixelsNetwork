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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Planet Hakai (design-doc 16b / 22; owner 2026-10-16: "a configuration for hakai to be able to do it to planets").
 * There are two distinct destruction routes. Binary Hakai erasure uses {@link XenoSpaceConfig#hakaiPlanets()} (off by
 * default), the permission floor, and the protected list; a completed area Hakai burns for
 * {@code hakaiPlanetEraseTicks} before recording {@link ErasedBodiesSavedData} and republishing the chart. Separately,
 * §22 free-mode structural damage is governed by the global destructibility ceiling and per-body policy, even while
 * binary Hakai is off. Both routes only unchart bodies and reconcile their physical representation; neither deletes a
 * dimension. Cracked/erased bodies take their moons with them (a removed star's planets stay). The binary Hakai route
 * can restore through {@code /xenospace hakai restore} or its restore timer; cracked bodies require explicit restore.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlanetHakai {
    private record Burn(String id, String name, String caster, Vec3 centre, double radius, Vec3 impact, long start,
                        int duration) {
        Burn(String id, String name, String caster, Vec3 centre, double radius, Vec3 impact, long start) {
            this(id, name, caster, centre, radius, impact, start, XenoSpaceConfig.hakaiPlanetEraseTicks());
        }
    }
    private static long syncRevision;

    private static final Map<String, Burn> BURNING = new LinkedHashMap<>();
    /** Projectile clip hooks can repeat; this bounds integrity mutations to one accepted hit per player/body window. */
    private static final PlanetIntegrityImpactBudget INTEGRITY_IMPACTS = new PlanetIntegrityImpactBudget(8192);
    private static final LinkedHashSet<String> WARNED_POLICY_CLAMPS = new LinkedHashSet<>();
    private static final LinkedHashSet<String> WARNED_IMPACT_REFUSALS = new LinkedHashSet<>();
    private static final LinkedHashSet<String> WARNED_INVALID_INTEGRITY_IDS = new LinkedHashSet<>();
    private static long lastIntegrityStorageWarningTick = Long.MIN_VALUE;
    /** Erased or cracked ids, including dependent moons, that must not be presented as available travel targets. */
    private static volatile java.util.Set<String> unavailableCache = java.util.Set.of();

    public static boolean isUnavailable(String id) { return unavailableCache.contains(id); }
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
        int casterPermission = permission(caster);
        // This callback runs only when the area-Hakai channel completes. The current API does not expose
        // the caster's tier here, so this is sanctioned-Hakai classification, not proof of a tier-3 gate.
        var sidecarVerdict = PlanetHakaiRules.sidecarVerdict(destructibilityPolicy(server, body), casterPermission,
                PlanetHakaiRules.DestructionPath.SANCTIONED_HAKAI);
        var verdict = sidecarVerdict != null ? sidecarVerdict
                : PlanetHakaiRules.verdict(mode, body.id(), body.erasable(), XenoSpaceConfig.hakaiPlanetProtected(),
                casterPermission, XenoSpaceConfig.hakaiPlanetPermissionLevel(),
                ErasedBodiesSavedData.get(server).isErased(body.id()) || BURNING.containsKey(body.id()));
        XenoPixelsMod.LOGGER.info("Planet Hakai: {} aimed at {} centre {} hit at {} blocks -> {}", caster.getScoreboardName(), body.id(), bestCentre,
                Math.round(bestHit), verdict);
        if (verdict != PlanetHakaiRules.Verdict.ALLOWED) {
            caster.displayClientMessage(Component.translatable("message.xenopixelsmod.planet_hakai_refused." + verdict.name().toLowerCase(),
                    body.displayName()), true);
            return false;
        }
        Vec3 hitPoint = eye.add(look.scale(bestHit));
        Vec3 impactDirection = hitPoint.subtract(bestCentre).normalize();
        start(server, body.id(), body.displayName(), caster.getScoreboardName(), bestCentre, body.radiusBlocks(), impactDirection);
        return true;
    }

    /** Aiming tolerance: the sphere a player must look at is this much larger than the planet. */
    private static final double AIM_SLACK = 1.15;

    /**
     * Called by ki-attack impact hooks. Laser/wave hooks can run from projectile tick/clip paths,
     * so any future integrity sink here must enforce its own per-body charge budget.
     */
    public static void onKiAttackImpact(Entity attack, Vec3 worldImpact) {
        if (!(attack instanceof Projectile projectile) || !(projectile.getOwner() instanceof ServerPlayer caster)
                || !(attack.level() instanceof ServerLevel level) || worldImpact == null
                || !PhysicalEarth.HOST.equals(level.dimension().location())) return;
        var mode = PlanetHakaiRules.Mode.parse(XenoSpaceConfig.hakaiPlanets());
        MinecraftServer server = caster.getServer();
        for (var entry : PhysicalEarth.bodies(level).entrySet()) {
            String id = entry.getKey();
            var p = entry.getValue().logicalPose().position();
            Vec3 centre = new Vec3(p.x(), p.y(), p.z());
            double radius = PhysicalEarth.liveRadius(level, id);
            double distance = worldImpact.distanceTo(centre);
            if (radius <= 0 || Math.abs(distance - radius) > Math.max(8.0, radius * 0.04)) continue;
            var chart = SpaceChartService.state(server).runtime.chart;
            int slot = chart.slotOf(id);
            if (slot < 0) continue;
            CelestialBody body = chart.body(slot);
            int casterPermission = permission(caster);
            String policy = destructibilityPolicy(server, body);
            if (PlanetHakaiRules.acceptsFreeImpact(policy, id, XenoSpaceConfig.hakaiPlanetProtected(),
                    ErasedBodiesSavedData.get(server).isErased(id), BURNING.containsKey(id))) {
                damageIntegrity(server, caster, body, centre, radius);
                return;
            }
            if (mode == PlanetHakaiRules.Mode.OFF) {
                warnImpactRefusal(id, "hakai_mode_off", mode, policy);
                return;
            }
            var sidecarVerdict = PlanetHakaiRules.sidecarVerdict(policy, casterPermission,
                    PlanetHakaiRules.DestructionPath.ORDINARY_IMPACT);
            var verdict = sidecarVerdict != null ? sidecarVerdict
                    : PlanetHakaiRules.verdict(mode, id, body.erasable(), XenoSpaceConfig.hakaiPlanetProtected(),
                    casterPermission, XenoSpaceConfig.hakaiPlanetPermissionLevel(),
                    ErasedBodiesSavedData.get(server).isErased(id) || BURNING.containsKey(id));
            if (verdict != PlanetHakaiRules.Verdict.ALLOWED) {
                warnImpactRefusal(id, verdict.name().toLowerCase(java.util.Locale.ROOT), mode, policy);
                return;
            }
            Vec3 direction = worldImpact.subtract(centre);
            direction = direction.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : direction.normalize();
            start(server, id, body.displayName(), caster.getScoreboardName(), centre, radius, direction);
            return;
        }
    }

    /** Sustained free-mode impact path. The per-player/body budget absorbs repeated laser/wave clip callbacks. */
    private static void damageIntegrity(MinecraftServer server, ServerPlayer caster, CelestialBody body, Vec3 centre, double radius) {
        if (!PlanetIntegrityImpactBudget.validBodyId(body.id())) {
            if (WARNED_INVALID_INTEGRITY_IDS.size() < 128 && WARNED_INVALID_INTEGRITY_IDS.add(body.id()))
                XenoPixelsMod.LOGGER.error("Planet integrity impact rejected invalid chart body id '{}'", body.id());
            return;
        }
        double nominalDamage = XenoSpaceConfig.planetIntegrityImpactDamage();
        if (!(nominalDamage > 0.0)) return; // Zero disables structural damage without consuming either cooldown.
        long now = net.bullettrain.xenopixelsmod.space.SpaceOrbitClock.gameTick(server);
        if (!INTEGRITY_IMPACTS.acquire(caster.getUUID(), body.id(), now,
                XenoSpaceConfig.planetIntegrityImpactCooldownTicks(), XenoSpaceConfig.planetIntegrityBodyCooldownTicks())) return;
        PlanetIntegritySavedData data = PlanetIntegritySavedData.get(server);
        double before = data.integrity(body.id());
        if (PlanetIntegrityRules.isDestroyed(before)) return;
        double loss = PlanetIntegrityRules.diminishingImpactLoss(before,
                nominalDamage, XenoSpaceConfig.planetIntegrityDiminishingFloor());
        var result = data.damage(body.id(), loss);
        if (result != PlanetIntegritySavedData.UpdateResult.UPDATED
                && result != PlanetIntegritySavedData.UpdateResult.UNCHANGED) {
            if (lastIntegrityStorageWarningTick == Long.MIN_VALUE || now < lastIntegrityStorageWarningTick
                    || now - lastIntegrityStorageWarningTick >= 1200) {
                XenoPixelsMod.LOGGER.error("Planet integrity impact refused for {} (result={}, entries={}/{}); body remains at {}%",
                        body.id(), result, data.size(), PlanetIntegritySavedData.MAX_ENTRIES, before);
                lastIntegrityStorageWarningTick = now;
            }
            return;
        }
        double after = data.integrity(body.id());
        PlanetIntegritySync.broadcastBody(server, body.id(), after);
        var thresholds = new PlanetIntegrityRules.Thresholds(XenoSpaceConfig.planetIntegrityScarredAt(),
                XenoSpaceConfig.planetIntegrityCriticalAt());
        var priorStage = PlanetIntegrityRules.stage(before, thresholds);
        var nextStage = PlanetIntegrityRules.stage(after, thresholds);
        if (priorStage != nextStage) {
            if (nextStage == PlanetIntegrityRules.Stage.DESTROYED) {
                crackBody(server, body, caster.getScoreboardName(), centre, radius, now);
            } else {
                String key = nextStage == PlanetIntegrityRules.Stage.CRITICAL
                        ? "message.xenopixelsmod.planet_integrity_critical"
                        : "message.xenopixelsmod.planet_integrity_scarred";
                server.getPlayerList().broadcastSystemMessage(Component.translatable(key, body.displayName(), Math.round(after)), false);
            }
        }
        XenoPixelsMod.LOGGER.info("Planet integrity impact: body={} by={} integrity={} -> {} (loss={}, policy=free)",
                body.id(), caster.getScoreboardName(), before, after, loss);
    }

    /** A zero-integrity body is hidden from the chart and physical registry; its planet dimension is never deleted. */
    private static void crackBody(MinecraftServer server, CelestialBody body, String by, Vec3 centre, double radius, long now) {
        String dimension = body.dimensionId();
        boolean reconciled = false;
        try {
            reconciled = republish(server).published();
            if (!reconciled) XenoPixelsMod.LOGGER.error(
                    "Planet {} reached zero integrity but the chart did not publish its removal; persisted integrity keeps it unavailable on the next successful reload",
                    body.id());
        } catch (RuntimeException failure) {
            XenoPixelsMod.LOGGER.error("Planet {} reached zero integrity but chart/physical reconciliation failed; persisted integrity will keep it unavailable on the next reload",
                    body.id(), failure);
        }
        ServerLevel voidsea = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (voidsea != null && reconciled) {
            voidsea.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                    (float) Math.min(96, radius / 2), 0.4f);
            if (XenoSpaceConfig.hakaiPlanetEjectOccupants() && dimension != null) eject(server, voidsea, dimension,
                    new Burn(body.id(), body.displayName(), by, centre, radius, new Vec3(0, 1, 0), now),
                    "message.xenopixelsmod.planet_integrity_ejected");
        }
        String notice = reconciled ? "message.xenopixelsmod.planet_integrity_cracked"
                : "message.xenopixelsmod.planet_integrity_reconcile_failed";
        server.getPlayerList().broadcastSystemMessage(Component.translatable(notice, body.displayName(), by), false);
    }

    /**
     * Where the body actually is in the Voidsea: its live physical centre when built, otherwise its chart position on
     * the staging plane PhysicalEarth uses (y = max(128, min build height + radius + 2), plus any charted y offset).
     */
    static Vec3 voidseaCentre(ServerLevel voidsea, net.bullettrain.xenopixelsmod.space.chart.ChartRegistry chart, int slot) {
        CelestialBody body = chart.body(slot);
        var live = PhysicalEarth.liveCenter(voidsea, body.id());
        if (live != null) return new Vec3(live.x(), live.y(), live.z());
        var focus = net.bullettrain.xenopixelsmod.space.SpaceHomeOrbitalFrame.resolve(voidsea, chart);
        double y = net.bullettrain.xenopixelsmod.space.SpaceHomeOrbitalFrame.chartY(
                chart, slot, focus == null ? Double.NaN : focus.y());
        return new Vec3(chart.x(slot), y, chart.z(slot));
    }

    /** Admin path ({@code /xenospace hakai erase}): skips aiming and permission, still honours protection. */
    public static String adminErase(MinecraftServer server, String id, String by) {
        var chart = SpaceChartService.state(server).runtime.chart;
        int slot = chart.slotOf(id);
        if (slot < 0) return "no charted body " + id;
        if (XenoSpaceConfig.hakaiPlanetProtected().contains(id)) return id + " is protected (hakaiPlanetProtected)";
        if (ErasedBodiesSavedData.get(server).isErased(id) || BURNING.containsKey(id)) return id + " is already erased or burning";
        CelestialBody body = chart.body(slot);
        var sidecarVerdict = PlanetHakaiRules.sidecarVerdict(destructibilityPolicy(server, body), 4,
                PlanetHakaiRules.DestructionPath.ADMIN);
        if (sidecarVerdict != null) return id + " cannot be erased by policy " + destructibilityPolicy(server, body);
        ServerLevel voidsea = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        Vec3 centre = voidsea == null ? new Vec3(chart.x(slot), chart.y(slot), chart.z(slot)) : voidseaCentre(voidsea, chart, slot);
        start(server, id, body.displayName(), by, centre, body.radiusBlocks(), new Vec3(0, 1, 0));
        return "burning " + id;
    }

    private static void start(MinecraftServer server, String id, String name, String by, Vec3 centre, double radius, Vec3 impact) {
        BURNING.put(id, new Burn(id, name, by, centre, radius, impact,
                net.bullettrain.xenopixelsmod.space.SpaceOrbitClock.gameTick(server)));
        var burn = sync(server, BURNING.get(id), true);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, burn);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("message.xenopixelsmod.planet_hakai_start", by, name), false);
        XenoPixelsMod.LOGGER.info("Planet Hakai: {} began erasing {} (r={}) at {}", by, id, radius, centre);
    }

    private static PlanetHakaiSync sync(MinecraftServer server, Burn burn, boolean active) {
        return new PlanetHakaiSync(++syncRevision, burn.start,
                net.bullettrain.xenopixelsmod.space.SpaceOrbitClock.gameTick(server),
                new PlanetHakaiBurn(burn.id, burn.duration, active,
                        (float) burn.impact.x, (float) burn.impact.y, (float) burn.impact.z));
    }

    private static void replay(ServerPlayer player) {
        for (Burn burn : BURNING.values())
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, sync(player.getServer(), burn, true));
    }

    @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) replay(player);
    }

    @SubscribeEvent public static void dimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) replay(player);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = net.bullettrain.xenopixelsmod.space.SpaceOrbitClock.gameTick(server);
        if (now >= nextRestoreCheck) { nextRestoreCheck = now + 200; restoreDue(server); }
        if (BURNING.isEmpty()) return;
        ServerLevel voidsea = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        for (var burn : new ArrayList<>(BURNING.values())) {
            double progress = PlanetHakaiRules.progress(burn.start, now, burn.duration);
            if (now % 20 == 0) {
                var snapshot = sync(server, burn, true);
                for (ServerPlayer player : server.getPlayerList().getPlayers())
                    net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, snapshot);
            }
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
        Vec3 axis = burn.impact;
        Vec3 reference = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 tangent = axis.cross(reference).normalize(), bitangent = axis.cross(tangent).normalize();
        Vec3 planeCentre = burn.centre.add(axis.scale(cut * burn.radius));
        int edge = 40 + (int) (ring / 2);
        for (int i = 0; i < edge; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = ring * (0.97 + 0.08 * random.nextDouble());
            Vec3 point = planeCentre.add(tangent.scale(Math.cos(a) * r)).add(bitangent.scale(Math.sin(a) * r))
                    .add(axis.scale(random.nextDouble() * Math.max(2, burn.radius * 0.08)));
            farParticles(voidsea, i % 3 == 0 ? spark : dust, point.x, point.y, point.z,
                    1, 0.3, 0.6, 0.3, 0.0);
        }
        for (int i = 0; i < 24; i++) {
            double zz = -1 + random.nextDouble() * (cut + 1), a = random.nextDouble() * Math.PI * 2, s = Math.sqrt(Math.max(0, 1 - zz * zz));
            double r = burn.radius * 1.03;
            Vec3 point = burn.centre.add(axis.scale(zz * r)).add(tangent.scale(Math.cos(a) * s * r)).add(bitangent.scale(Math.sin(a) * s * r));
            farParticles(voidsea, dust, point.x, point.y, point.z, 1, 0.4, 0.4, 0.4, 0.0);
        }
        if ((voidsea.getGameTime() / 4) % 10 == 0)
            voidsea.playSound(null, planeCentre.x, planeCentre.y, planeCentre.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS,
                    (float) Math.min(64, burn.radius / 4), 0.5f + (float) progress);
    }

    /** Long-distance particles (vanilla's default only reaches 32 blocks; a planet is watched from hundreds). */
    private static void farParticles(ServerLevel level, DustParticleOptions options, double x, double y, double z, int count,
                                     double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) level.sendParticles(player, options, true, x, y, z, count, dx, dy, dz, speed);
    }

    private static void finish(MinecraftServer server, ServerLevel voidsea, Burn burn) {
        var stop = sync(server, burn, false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, stop);
        if (!ErasedBodiesSavedData.get(server).eraseAtPositionOrUnmapped(burn.id, burn.caster,
                System.currentTimeMillis(), burn.centre.x, burn.centre.z)) {
            XenoPixelsMod.LOGGER.error("Planet Hakai could not persist completion for {}; the chart remains unchanged",
                    burn.id);
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.xenopixelsmod.planet_hakai_storage_failed", burn.name), false);
            return;
        }
        String dimension = dimensionOf(server, burn.id);
        ChartReloadOutcome reload;
        try {
            reload = republish(server);
        } catch (RuntimeException failure) {
            XenoPixelsMod.LOGGER.error("Planet Hakai persisted erasure for {}, but chart/physical reconciliation threw",
                    burn.id, failure);
            server.getPlayerList().broadcastSystemMessage(Component.translatable(
                    "message.xenopixelsmod.planet_hakai_reconcile_failed", burn.name), false);
            return;
        }
        if (!reload.published()) {
            XenoPixelsMod.LOGGER.error("Planet Hakai persisted erasure for {}, but chart publication failed; the body remains in the current chart until a successful reload",
                    burn.id);
            server.getPlayerList().broadcastSystemMessage(Component.translatable(
                    "message.xenopixelsmod.planet_hakai_reconcile_failed", burn.name), false);
            return;
        }
        if (!reload.ancillaryPersistenceSucceeded()) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable(
                    "message.xenopixelsmod.planet_hakai_auxiliary_failed", burn.name), false);
        }
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
        eject(server, voidsea, dimension, burn, "message.xenopixelsmod.planet_hakai_ejected");
    }

    private static void eject(MinecraftServer server, ServerLevel voidsea, String dimension, Burn burn, String messageKey) {
        ResourceLocation key = ResourceLocation.tryParse(dimension);
        ServerLevel level = key == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
        if (level == null || level == voidsea) return;
        for (ServerPlayer player : new ArrayList<>(level.players())) {
            double y = burn.centre.y + burn.radius + 48;
            player.teleportTo(voidsea, burn.centre.x, y, burn.centre.z, player.getYRot(), player.getXRot());
            SpaceFlightGuard.grant(player);
            player.displayClientMessage(Component.translatable(messageKey, burn.name), false);
        }
    }

    public static String restore(MinecraftServer server, String id) {
        boolean erased = ErasedBodiesSavedData.get(server).restore(id);
        boolean integrityReset = PlanetIntegritySavedData.get(server).restore(id);
        if (!erased && !integrityReset) return id + " has no erased or damaged state";
        ChartReloadOutcome reload;
        try {
            reload = republish(server);
        } catch (RuntimeException failure) {
            return "cleared saved state for " + id + " but chart/physical reconciliation failed; inspect server log";
        }
        if (!reload.published()) return "cleared saved state for " + id + " but chart publication failed; retry reload";
        if (integrityReset) PlanetIntegritySync.broadcastBody(server, id, 100.0);
        if (erased) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable("message.xenopixelsmod.planet_hakai_restored", id), false);
        } else {
            server.getPlayerList().broadcastSystemMessage(Component.translatable("message.xenopixelsmod.planet_integrity_restored", id), false);
        }
        return "restored " + id + (reload.ancillaryPersistenceSucceeded() ? ""
                : "; chart published, but auxiliary planet assignment persistence failed; inspect server log");
    }

    public static String restoreAll(MinecraftServer server) {
        int erased = ErasedBodiesSavedData.get(server).restoreAll();
        int integrity = PlanetIntegritySavedData.get(server).restoreAll();
        boolean auxiliaryPersistenceSucceeded = true;
        if (erased + integrity > 0) {
            ChartReloadOutcome reload;
            try {
                reload = republish(server);
            } catch (RuntimeException failure) {
                return "cleared " + erased + " erased bodies and reset " + integrity
                        + " integrity records, but chart/physical reconciliation failed; inspect server log";
            }
            if (!reload.published()) return "cleared " + erased + " erased bodies and reset " + integrity
                    + " integrity records, but chart publication failed; retry reload";
            auxiliaryPersistenceSucceeded = reload.ancillaryPersistenceSucceeded();
            if (integrity > 0) PlanetIntegritySync.broadcast(server);
        }
        return "restored " + erased + " erased bodies and reset " + integrity + " integrity records"
                + (!auxiliaryPersistenceSucceeded
                ? "; chart published, but auxiliary planet assignment persistence failed; inspect server log" : "");
    }

    /** Restores every erased body, republishes the chart, and reconciles physical bodies against the live config. */
    public static String regenerateAll(MinecraftServer server) {
        int restored = ErasedBodiesSavedData.get(server).restoreAll();
        int integrity = PlanetIntegritySavedData.get(server).restoreAll();
        if (restored + integrity > 0) {
            ChartReloadOutcome reload;
            try {
                reload = republish(server);
            } catch (RuntimeException failure) {
                return "restored saved state for " + restored + " erased bodies and " + integrity
                        + " integrity records, but chart/physical reconciliation failed; inspect server log";
            }
            if (!reload.published()) return "restored saved state for " + restored + " erased bodies and " + integrity
                    + " integrity records, but chart publication failed; retry reload";
            if (integrity > 0) PlanetIntegritySync.broadcast(server);
            return "restored " + restored + " Hakai-erased bodies and reset " + integrity
                    + " integrity records; physical reconciliation requested"
                    + (reload.ancillaryPersistenceSucceeded() ? ""
                    : "; auxiliary planet assignment persistence failed; inspect server log");
        }
        if (integrity > 0) PlanetIntegritySync.broadcast(server);
        String physical = PhysicalEarth.reconcile(server);
        return "restored " + restored + " Hakai-erased bodies and reset " + integrity + " integrity records; " + physical;
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
    private static ChartReloadOutcome republish(MinecraftServer server) {
        long revisionBefore = SpaceChartService.state(server).runtime.revision;
        boolean reloadSucceeded = SpaceChartService.reloadDimensions(server);
        long revisionAfter = SpaceChartService.state(server).runtime.revision;
        ChartReloadOutcome outcome = ChartReloadOutcome.classify(reloadSucceeded, revisionBefore, revisionAfter);
        if (!outcome.published()) return outcome;
        PhysicalEarth.reconcile(server);
        return outcome;
    }

    /** Filter used by SpaceChartService before assembling the chart. */
    public static Map<String, JsonObject> withoutErased(MinecraftServer server, Map<String, JsonObject> definitions) {
        var erased = ErasedBodiesSavedData.get(server).erased();
        var gone = new java.util.HashSet<String>(erased.keySet());
        PlanetIntegritySavedData.get(server).entries().forEach((id, integrity) -> {
            if (PlanetIntegrityRules.isDestroyed(integrity)) gone.add(id);
        });
        if (gone.isEmpty()) {
            unavailableCache = java.util.Set.of();
            return definitions;
        }
        // A cracked/erased planet takes its moons with it; killing a star leaves its planets in orbit.
        var visible = PlanetDestructionChartFilter.withoutBodies(definitions, gone);
        // The cache includes dependent moons so PhysicalEarth doesn't re-adopt a removed child during reconciliation.
        gone.addAll(definitions.keySet().stream().filter(id -> !visible.containsKey(id)).toList());
        unavailableCache = java.util.Set.copyOf(gone);
        return visible;
    }

    public static String status(MinecraftServer server) {
        var out = new StringBuilder("planet hakai: mode=" + XenoSpaceConfig.hakaiPlanets() + " protected=" + XenoSpaceConfig.hakaiPlanetProtected()
                + " permission=" + XenoSpaceConfig.hakaiPlanetPermissionLevel() + " reach=" + (int) XenoSpaceConfig.hakaiPlanetReach()
                + " burnTicks=" + XenoSpaceConfig.hakaiPlanetEraseTicks() + " restoreMinutes=" + XenoSpaceConfig.hakaiPlanetRestoreMinutes()
                + " destructibleDefault=" + XenoSpaceConfig.destructibleDefault()
                + " integrityImpactDamage=" + XenoSpaceConfig.planetIntegrityImpactDamage()
                + " impactCooldown=" + XenoSpaceConfig.planetIntegrityImpactCooldownTicks()
                + " bodyCooldown=" + XenoSpaceConfig.planetIntegrityBodyCooldownTicks());
        BURNING.keySet().forEach(id -> out.append("\n burning: ").append(id));
        ErasedBodiesSavedData.get(server).erased().forEach((id, e) -> out.append("\n erased: ").append(id).append(" by ").append(e.by()));
        var integrity = PlanetIntegritySavedData.get(server).entries();
        var chart = SpaceChartService.state(server).runtime.chart;
        out.append("\n integrityRecords=").append(integrity.size())
                .append(" integrityAdmissionAtCap=").append(integrity.size() >= PlanetIntegritySavedData.MAX_ENTRIES);
        int notInChart = 0;
        var notInChartSample = new java.util.ArrayList<String>(8);
        for (String id : integrity.keySet()) {
            if (chart.slotOf(id) >= 0) continue;
            notInChart++;
            if (notInChartSample.size() < 8) notInChartSample.add(id);
        }
        out.append("\n integrityIdsNotInCurrentChart=").append(notInChart);
        if (!notInChartSample.isEmpty()) out.append(" sample=").append(notInChartSample);
        int shown = 0;
        for (var entry : integrity.entrySet()) {
            if (shown++ >= 32) break;
            out.append("\n integrity: ").append(entry.getKey()).append(' ')
                    .append(String.format(java.util.Locale.ROOT, "%.1f", entry.getValue())).append('%');
        }
        if (integrity.size() > 32) out.append("\n ... ").append(integrity.size() - 32).append(" more integrity records omitted");
        return out.toString();
    }

    /** Command input: a bare body name means our namespace ("mars" -> "xenopixelsmod:mars"). */
    public static String key(String id) {
        return PlanetHakaiRules.normalizeBodyId(id);
    }

    private static String dimensionOf(MinecraftServer server, String id) {
        var chart = SpaceChartService.state(server).runtime.chart;
        int slot = chart.slotOf(id);
        return slot < 0 ? null : chart.body(slot).dimensionId();
    }

    private static String destructibilityPolicy(MinecraftServer server, CelestialBody body) {
        String override = null;
        if (body.dimensionId() != null) {
            var settings = SpaceChartService.state(server).sidecarSettings.get(body.dimensionId());
            if (settings != null) override = settings.destructible();
        }
        var effective = PlanetHakaiRules.effectiveDestructibility(XenoSpaceConfig.destructibleDefault(), override);
        if (override != null && !override.isBlank()
                && effective != PlanetHakaiRules.Destructibility.parse(override)) {
            String warningKey = body.id() + "|" + override + "|" + XenoSpaceConfig.destructibleDefault();
            if (WARNED_POLICY_CLAMPS.add(warningKey)) {
                if (WARNED_POLICY_CLAMPS.size() > 512) WARNED_POLICY_CLAMPS.remove(WARNED_POLICY_CLAMPS.iterator().next());
                XenoPixelsMod.LOGGER.warn("Planet {} requested destructible={} but server ceiling destructibleDefault={} limits its effective policy to {}",
                        body.id(), override, XenoSpaceConfig.destructibleDefault(), effective.key());
            }
        }
        return effective.key();
    }

    private static void warnImpactRefusal(String bodyId, String reason, PlanetHakaiRules.Mode mode, String policy) {
        String key = bodyId + "|" + reason + "|" + mode + "|" + policy;
        if (!WARNED_IMPACT_REFUSALS.add(key)) return;
        if (WARNED_IMPACT_REFUSALS.size() > 512)
            WARNED_IMPACT_REFUSALS.remove(WARNED_IMPACT_REFUSALS.iterator().next());
        XenoPixelsMod.LOGGER.info("Planet ki impact did not alter body {} (reason={}, hakaiMode={}, effectivePolicy={})",
                bodyId, reason, mode.key(), policy);
    }

    private static int permission(ServerPlayer player) {
        for (int level = 4; level > 0; level--) if (player.hasPermissions(level)) return level;
        return 0;
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        BURNING.clear(); INTEGRITY_IMPACTS.clear(); WARNED_POLICY_CLAMPS.clear();
        WARNED_IMPACT_REFUSALS.clear(); WARNED_INVALID_INTEGRITY_IDS.clear();
        lastIntegrityStorageWarningTick = Long.MIN_VALUE;
        nextRestoreCheck = 0; unavailableCache = java.util.Set.of();
    }
}
