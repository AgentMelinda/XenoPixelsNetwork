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
        long signature=SpaceSnapshot.signature(chart,state.runtime.revision);
        if(signature==state.lastVisualSignature)return;
        state.lastVisualSignature=signature;
        var snapshot=SpaceSnapshot.fromChart(chart,state.runtime.revision);
        state.lastVisualBodies=snapshot.bodies().size();
        var listed=XenoSpaceConfig.spaceDimensions();
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

    private static void send(ServerPlayer player) {
        var state=SpaceChartService.state(player.getServer());
        var snapshot=SpaceSnapshot.fromChart(state.runtime.chart,state.runtime.revision);
        AddonNetwork.sendToPlayer(player,snapshot);
        var level=player.serverLevel();
        if(net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST.equals(level.dimension().location()))
            AddonNetwork.sendToPlayer(player,planetBindings(level));
        else AddonNetwork.sendToPlayer(player,new PhysicalPlanetBinding(physicalBody(level)));
    }
    /** Re-sends the planet bindings to everyone in {@code level}, after a planet is created mid-session. */
    public static void resendPlanetBindings(net.minecraft.server.level.ServerLevel level) {
        var packet=planetBindings(level);
        for(ServerPlayer player:level.players()) AddonNetwork.sendToPlayer(player,packet);
    }
    /** Every loaded managed planet in this level; an empty list clears the client's bindings. */
    private static PhysicalPlanetBindings planetBindings(net.minecraft.server.level.ServerLevel level) {
        var entries=new java.util.ArrayList<PhysicalPlanetBindings.Entry>();
        for(var entry:net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.bodies(level).entrySet())
            entries.add(new PhysicalPlanetBindings.Entry(entry.getKey(),entry.getValue().getUniqueId()));
        for(var entry:net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer.bodies(level).entrySet())
            entries.add(new PhysicalPlanetBindings.Entry(entry.getKey(),entry.getValue().getUniqueId(),
                    net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer.radialGravityRadius(entry.getValue())));
        if(net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST.equals(level.dimension().location())) {
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
}
