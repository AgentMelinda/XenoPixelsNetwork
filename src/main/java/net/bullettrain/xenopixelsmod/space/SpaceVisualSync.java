package net.bullettrain.xenopixelsmod.space;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Gets the server's visual decisions onto the clients that can use them: the charted bodies worth
 * drawing, and which physical Sable planet the local renderer should bind to.
 *
 * <p><b>Two sends, two reasons.</b> A login or dimension change needs the current answer for that one
 * player, so {@link #send} builds it on the spot. The repeated pass exists because the chart is not
 * static — bodies orbit, the ladder promotes and demotes them, and an operator moves one with a command
 * — so the answer changes underneath a player who did nothing. That pass is throttled to one second and
 * gated on {@link SpaceSnapshot#signature}, so a still chart sends nothing at all; the list is built on a
 * change rather than on the tick, which is the §5 allocation rule.
 *
 * <p>No packets or snapshot building when no space-dimension player could receive them.
 */
@EventBusSubscriber(modid=XenoPixelsMod.MOD_ID)
public final class SpaceVisualSync {
    private static final ResourceLocation ORBIT=ResourceLocation.fromNamespaceAndPath("xenospace_spike","orbit");
    /** Seconds between chart-visual checks. Positions in the packet round to a block, so a slower
     * cadence than the ladder is invisible; a faster one is packets for nothing. */
    private static final int INTERVAL_TICKS=20;
    private static final java.util.Map<net.minecraft.server.level.ServerLevel,java.util.UUID> BINDINGS=new java.util.IdentityHashMap<>();
    private static final java.util.Map<net.minecraft.server.level.ServerLevel,PhysicalPlanetBindings> HOST_BINDINGS=new java.util.IdentityHashMap<>();
    private static final java.util.Set<java.util.UUID> ORBIT_SETTINGS_WARNED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private SpaceVisualSync() {}
    public static void tick(MinecraftServer server,SpaceChartService.State state) {
        long now=server.overworld().getGameTime();
        if(now<state.nextVisualTick)return;
        state.nextVisualTick=now+INTERVAL_TICKS;
        var orbit=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ORBIT));
        if(orbit!=null) {
            var body=physicalBody(orbit);
            if(!java.util.Objects.equals(body,BINDINGS.get(orbit))) {
                BINDINGS.put(orbit,body);
                for(var player:orbit.players())AddonNetwork.sendToPlayer(player,new PhysicalPlanetBinding(body));
            }
        }
        var host=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST));
        if(host!=null) {
            var bindings=planetBindings(host);
            if(!bindings.equals(HOST_BINDINGS.get(host))) {
                HOST_BINDINGS.put(host,bindings);
                for(var player:host.players())AddonNetwork.sendToPlayer(player,bindings);
            }
        }
    }

    /**
     * The charted bodies a client should draw, sent when the set or their positions moved. Skipped
     * entirely while {@code space.enabled} is off, which is what keeps the promise that the master switch
     * means nothing space-side runs — and it costs nothing, because with the ladder never driven every
     * body is {@code ABSENT} and the list would be empty anyway.
     */
    public static void tickChartVisuals(MinecraftServer server,SpaceChartService.State state) {
        if(!XenoSpaceConfig.enabled())return;
        long now=server.overworld().getGameTime();
        if(now<state.nextChartVisualTick)return;
        state.nextChartVisualTick=now+INTERVAL_TICKS;
        ChartRegistry chart=state.runtime.chart;
        boolean hasSpaceRecipient=false;
        var listed=XenoSpaceConfig.spaceDimensions();
        for(ServerPlayer player:server.getPlayerList().getPlayers())
            if(SpaceDimensions.isSpace(chart,player.level().dimension().location(),listed)) {
                hasSpaceRecipient=true;
                break;
            }
        // Login and dimension-change handlers send an immediate snapshot. With nobody in a space dimension,
        // rebuilding moving SDB projections once per second only allocates and scans bodies for zero recipients.
        if(!hasSpaceRecipient) {
            // /xenospace status reports the currently drawable observer snapshot; avoid leaving a stale
            // non-zero value behind after the last space player leaves.
            state.lastVisualBodies=0;
            return;
        }
        var liveCentres=livePhysicalCentres(server);
        var extras=liveSdbProjections(server);
        double homeFrameY=homeFrameY(server,chart);
        long signature=SpaceSnapshot.signature(chart,state.runtime.revision,liveCentres,extras,homeFrameY);
        if(signature==state.lastVisualSignature)return;
        state.lastVisualSignature=signature;
        var snapshot=SpaceSnapshot.fromChart(chart,state.runtime.revision,liveCentres,extras,homeFrameY);
        state.lastVisualBodies=snapshot.bodies().size();
        for(ServerPlayer player:server.getPlayerList().getPlayers())
            if(SpaceDimensions.isSpace(chart,player.level().dimension().location(),listed))
                AddonNetwork.sendToPlayer(player,snapshot);
        XenoPixelsMod.LOGGER.info("Space visual snapshot built: revision={}, planets={} for space dimensions {}",
                state.runtime.revision,snapshot.bodies().size(),listed);
    }

    /** What {@code /xenospace list visuals} would push right now; builds the list, changes nothing. */
    public static String preview(ChartRegistry chart) {
        var snapshot=SpaceSnapshot.fromChart(chart,0);
        StringBuilder out=new StringBuilder("drawn now: "+snapshot.bodies().size()+" of "+chart.size()
                +" charted bodies (cap "+SpaceSnapshot.MAX_BODIES+")");
        for(var body:snapshot.bodies())
            out.append("\n ").append(body.id()).append(" cell=").append(body.style()).append(" at ")
                    .append(Math.round(body.x())).append(',').append(Math.round(body.y())).append(',')
                    .append(Math.round(body.z())).append(" r=").append(Math.round(body.radius()));
        return out.toString();
    }
    /** Preview the same chart-plus-live-physical snapshot used by server sync. */
    public static String preview(MinecraftServer server) {
        ChartRegistry chart=SpaceChartService.state(server).runtime.chart;
        var snapshot=SpaceSnapshot.fromChart(chart,0,livePhysicalCentres(server),liveSdbProjections(server),homeFrameY(server,chart));
        StringBuilder out=new StringBuilder("drawn now: "+snapshot.bodies().size()+" of "+chart.size()
                +" charted bodies plus live physical extras (cap "+SpaceSnapshot.MAX_BODIES+")");
        for(var body:snapshot.bodies())
            out.append("\n ").append(body.id()).append(" cell=").append(body.style()).append(" at ")
                    .append(Math.round(body.x())).append(',').append(Math.round(body.y())).append(',')
                    .append(Math.round(body.z())).append(" r=").append(Math.round(body.radius()));
        return out.toString();
    }

    private static void send(ServerPlayer player) {
        var state=SpaceChartService.state(player.getServer());
        SpaceAtmosphereSync.send(player);
        net.bullettrain.xenopixelsmod.space.event.StellarLifecycleService.send(player);
        ChartRegistry chart=state.runtime.chart;
        boolean inSpace=SpaceDimensions.isSpace(chart,player.level().dimension().location(),XenoSpaceConfig.spaceDimensions());
        sendOrbitSettings(player,SpaceOrbitClockSnapshot.capture());
        if(inSpace) {
            try {
                SpaceLodDriver.resolveForSnapshot(player.getServer(),chart,player);
            } catch(Throwable failure) {
                // A visual refresh must not break login or dimension-change packet delivery.
                XenoPixelsMod.LOGGER.warn("Could not resolve space LODs before immediate player snapshot",failure);
            }
        }
        var snapshot=SpaceSnapshot.fromChart(chart,state.runtime.revision,livePhysicalCentres(player.getServer()),
                liveSdbProjections(player.getServer()),homeFrameY(player.getServer(),chart));
        AddonNetwork.sendToPlayer(player,snapshot);
        var level=player.serverLevel();
        if(net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST.equals(level.dimension().location()))
            AddonNetwork.sendToPlayer(player,planetBindings(level));
        else AddonNetwork.sendToPlayer(player,new PhysicalPlanetBinding(physicalBody(level)));
    }
    /** Sends changed server orbit mode/epoch settings; game ticks themselves are already vanilla-synchronized. */
    public static void syncOrbitSettings(MinecraftServer server) {
        var packet=SpaceOrbitClockSnapshot.capture();
        for(ServerPlayer player:server.getPlayerList().getPlayers()) sendOrbitSettings(player,packet);
    }
    private static void sendOrbitSettings(ServerPlayer player, SpaceOrbitClockSnapshot packet) {
        try {
            AddonNetwork.sendToPlayer(player,packet);
            ORBIT_SETTINGS_WARNED.remove(player.getUUID());
        } catch (RuntimeException failure) {
            if (ORBIT_SETTINGS_WARNED.add(player.getUUID()))
                XenoPixelsMod.LOGGER.warn("Could not send orbital settings to {}", player.getGameProfile().getName(), failure);
        }
    }
    /** Re-sends the planet bindings to everyone in {@code level}, after a planet is created mid-session. */
    public static void resendPlanetBindings(net.minecraft.server.level.ServerLevel level) {
        var packet=planetBindings(level);
        for(ServerPlayer player:level.players()) AddonNetwork.sendToPlayer(player,packet);
    }
    private static java.util.Map<String,org.joml.Vector3dc> livePhysicalCentres(MinecraftServer server) {
        var host=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST));
        if(host==null)return java.util.Map.of();
        var bodies=net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.bodies(host);
        if(bodies.isEmpty())return java.util.Map.of();
        var centres=new java.util.HashMap<String,org.joml.Vector3dc>(bodies.size());
        for(var entry:bodies.entrySet()) {
            var p=entry.getValue().logicalPose().position();
            // Keep the signature and packet built immediately afterward on the same pose snapshot.
            centres.put(entry.getKey(),new org.joml.Vector3d(p.x(),p.y(),p.z()));
        }
        return java.util.Map.copyOf(centres);
    }

    private static double homeFrameY(MinecraftServer server,ChartRegistry chart) {
        var host=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST));
        if(host==null)return Double.NaN;
        var focus=SpaceHomeOrbitalFrame.resolve(host,chart);
        return focus==null?Double.NaN:focus.y();
    }
    /** Current server-owned SDB Sable poses, in stable slot order; never falls back to chart/analytic positions. */
    private static java.util.List<SpaceSnapshot.Body> liveSdbProjections(MinecraftServer server) {
        if (!net.bullettrain.xenopixelsmod.space.dragonball.DragonBallModeGuard.planetaryEnabled(server))
            return java.util.List.of();
        var host=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST));
        if(host==null)return java.util.List.of();
        var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(host);
        if(container==null)return java.util.List.of();
        var saved=net.bullettrain.xenopixelsmod.space.dragonball.DragonBallSavedData.get(server);
        if(!saved.isGameplayStateValid())return java.util.List.of();
        var extras=new java.util.ArrayList<SpaceSnapshot.Body>(net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls.COUNT);
        for(int slot=1;slot<=net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls.COUNT;slot++) {
            var id=saved.planetBodyUuid(slot);
            if(id==null || !(container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body)
                    || body.isRemoved()) continue;
            var p=body.logicalPose().position();
            var box=body.getPlot().getBoundingBox();
            double radius=net.bullettrain.xenopixelsmod.space.physics.PlanetBodyGeometry.hullRadius(box);
            if(!Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z())
                    || Math.abs(p.x())>30_000_000 || Math.abs(p.y())>30_000_000 || Math.abs(p.z())>30_000_000
                    || !(radius>0) || radius>1_000_000) continue;
            var identity=net.bullettrain.xenopixelsmod.space.dragonball.DragonBallIdentity.forSlot(slot);
            extras.add(new SpaceSnapshot.Body(identity.planetKey(),0,p.x(),p.y(),p.z(),radius));
        }
        return java.util.List.copyOf(extras);
    }
    /** The exact binding list clients of this level are sent, for the read-only binding audit. */
    public static PhysicalPlanetBindings currentBindings(net.minecraft.server.level.ServerLevel level) {
        return planetBindings(level);
    }
    /** Every loaded managed planet in this level; an empty list clears the client's bindings. */
    private static PhysicalPlanetBindings planetBindings(net.minecraft.server.level.ServerLevel level) {
        var entries=new java.util.ArrayList<PhysicalPlanetBindings.Entry>();
        for(var entry:net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.bodies(level).entrySet())
            entries.add(new PhysicalPlanetBindings.Entry(entry.getKey(),entry.getValue().getUniqueId()));
        for(var entry:net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer.bodies(level).entrySet())
            entries.add(new PhysicalPlanetBindings.Entry(entry.getKey(),entry.getValue().getUniqueId(),
                    net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer.radialGravityRadius(entry.getValue())));
        if(net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST.equals(level.dimension().location())
                && net.bullettrain.xenopixelsmod.space.dragonball.DragonBallModeGuard.planetaryEnabled(level.getServer())) {
            var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            var saved=net.bullettrain.xenopixelsmod.space.dragonball.DragonBallSavedData.get(level.getServer());
            for(int slot=1;slot<=net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls.COUNT;slot++) {
                var id=saved.planetBodyUuid(slot);
                if(id==null || container==null) continue;
                if(container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body
                        && !body.isRemoved()) {
                    var identity=net.bullettrain.xenopixelsmod.space.dragonball.DragonBallIdentity.forSlot(slot);
                    entries.add(new PhysicalPlanetBindings.Entry(identity.planetKey(),id));
                }
            }
        }
        return new PhysicalPlanetBindings(java.util.List.copyOf(entries), XenoSpaceConfig.smallPlanetGravityEnabled());
    }
    private static java.util.UUID physicalBody(net.minecraft.server.level.ServerLevel level) {
        var earth=net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.loadedBodyId(level);
        return earth!=null?earth:net.bullettrain.xenopixelsmod.space.physics.PhysicalPlanetSpike.loadedBodyId(level);
    }
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        BINDINGS.keySet().removeIf(level->level.getServer()==event.getServer());
        HOST_BINDINGS.keySet().removeIf(level->level.getServer()==event.getServer());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(e.getEntity() instanceof ServerPlayer player)send(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if(e.getEntity() instanceof ServerPlayer player)send(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        ORBIT_SETTINGS_WARNED.remove(e.getEntity().getUUID());
    }
}
