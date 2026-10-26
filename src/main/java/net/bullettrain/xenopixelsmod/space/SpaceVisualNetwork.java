package net.bullettrain.xenopixelsmod.space;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.api.network.AddonPacketDirection;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
public final class SpaceVisualNetwork {
    private SpaceVisualNetwork() {}
    static ResourceLocation universeSnapshotPacketId() {
        return ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "universe_snapshot_v3");
    }
    static ResourceLocation infiniteUniversePagePacketId() {
        return ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "infinite_universe_page_v2");
    }
    static ResourceLocation infiniteUniverseCellRevealPacketId() {
        return ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "infinite_universe_cell_reveal_v1");
    }

    static ResourceLocation oxygenStatusPacketId() {
        return ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "oxygen_status_v2");
    }
    public static void register() {
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "planet_hakai_sync_v3"),
                net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiSync.class, AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiSync::encode,
                net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiSync::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.PlanetHakaiClient.apply(packet);
                }));
        net.bullettrain.xenopixelsmod.combat.HakaiSweepSync.register();
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "fallen_stars_settings_v1"),
                FallenStarsSettingsPacket.class, AddonPacketDirection.CLIENTBOUND,
                FallenStarsSettingsPacket::encode, FallenStarsSettingsPacket::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.FallenStarServerSettings.apply(packet.enabled());
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod",
                        net.bullettrain.xenopixelsmod.space.event.StellarStageSnapshot.PACKET_ID),
                net.bullettrain.xenopixelsmod.space.event.StellarStageSnapshot.class,
                AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.event.StellarStageSnapshot::encode,
                net.bullettrain.xenopixelsmod.space.event.StellarStageSnapshot::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.StellarStageClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod",
                        net.bullettrain.xenopixelsmod.space.event.StellarBlastWaveSnapshot.PACKET_ID),
                net.bullettrain.xenopixelsmod.space.event.StellarBlastWaveSnapshot.class,
                AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.event.StellarBlastWaveSnapshot::encode,
                net.bullettrain.xenopixelsmod.space.event.StellarBlastWaveSnapshot::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.StellarBlastWaveClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","physical_planet_binding_v1"),
                PhysicalPlanetBinding.class,AddonPacketDirection.CLIENTBOUND,PhysicalPlanetBinding::encode,PhysicalPlanetBinding::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.PhysicalPlanetRenderer.bind(packet.bodyId());
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","physical_planet_bindings_v3"),
                PhysicalPlanetBindings.class,AddonPacketDirection.CLIENTBOUND,PhysicalPlanetBindings::encode,PhysicalPlanetBindings::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.PhysicalPlanetRenderer.bindAll(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","space_visual_snapshot_v1"),
                SpaceSnapshot.class,AddonPacketDirection.CLIENTBOUND,SpaceSnapshot::encode,SpaceSnapshot::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.OrbitPlanetProxies.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","asteroid_field_v1"),
                net.bullettrain.xenopixelsmod.space.asteroid.AsteroidFieldSnapshot.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.asteroid.AsteroidFieldSnapshot::encode,net.bullettrain.xenopixelsmod.space.asteroid.AsteroidFieldSnapshot::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.AsteroidFieldClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","small_planet_field_v2"),
                net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetFieldSnapshot.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetFieldSnapshot::encode,
                net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetFieldSnapshot::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.SmallPlanetFieldClient.apply(packet);
                }));
        AddonNetwork.register(universeSnapshotPacketId(),
                net.bullettrain.xenopixelsmod.space.universe.UniverseSnapshot.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.universe.UniverseSnapshot::encode,net.bullettrain.xenopixelsmod.space.universe.UniverseSnapshot::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.UniverseClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "universe_frontier_snapshot_v1"),
                net.bullettrain.xenopixelsmod.space.universe.UniverseFrontierSnapshot.class,
                AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.universe.UniverseFrontierSnapshot::encode,
                net.bullettrain.xenopixelsmod.space.universe.UniverseFrontierSnapshot::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.UniverseClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "eclipse_events_v1"),
                net.bullettrain.xenopixelsmod.space.event.EclipseEventSnapshot.class,
                AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.event.EclipseEventSnapshot::encode,
                net.bullettrain.xenopixelsmod.space.event.EclipseEventSnapshot::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.EclipseEventsClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","universe_cell_reveal_v1"),
                net.bullettrain.xenopixelsmod.space.universe.UniverseCellReveal.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.universe.UniverseCellReveal::encode,
                net.bullettrain.xenopixelsmod.space.universe.UniverseCellReveal::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.UniverseClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","universe_warp_v1"),
                net.bullettrain.xenopixelsmod.space.universe.UniverseWarpRequest.class,AddonPacketDirection.SERVERBOUND,
                net.bullettrain.xenopixelsmod.space.universe.UniverseWarpRequest::encode,net.bullettrain.xenopixelsmod.space.universe.UniverseWarpRequest::decode,
                (packet,context)->context.enqueueWork(()->context.sender().ifPresent(player -> {
                    if(!net.bullettrain.xenopixelsmod.space.universe.UniverseField.Cell.inRange(packet.x())
                            || !net.bullettrain.xenopixelsmod.space.universe.UniverseField.Cell.inRange(packet.z())) return;
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            net.bullettrain.xenopixelsmod.space.universe.UniverseService.warp(player,
                                    new net.bullettrain.xenopixelsmod.space.universe.UniverseField.Cell(packet.x(), packet.z()))), true);
                })));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "infinite_universe_page_request_v1"),
                net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageRequest.class,
                AddonPacketDirection.SERVERBOUND,
                net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageRequest::encode,
                net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageRequest::decode,
                (packet, context) -> context.enqueueWork(() -> context.sender().ifPresent(player ->
                        net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageService.request(player, packet))));
        AddonNetwork.register(infiniteUniversePagePacketId(),
                net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageSnapshot.class,
                AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageSnapshot::encode,
                net.bullettrain.xenopixelsmod.space.universe.InfiniteUniversePageSnapshot::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.InfiniteUniverseClient.apply(packet);
                }));
        AddonNetwork.register(infiniteUniverseCellRevealPacketId(),
                net.bullettrain.xenopixelsmod.space.universe.InfiniteUniverseCellReveal.class,
                AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.universe.InfiniteUniverseCellReveal::encode,
                net.bullettrain.xenopixelsmod.space.universe.InfiniteUniverseCellReveal::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.InfiniteUniverseClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","black_holes_v1"),
                net.bullettrain.xenopixelsmod.space.blackhole.BlackHoleSnapshot.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.blackhole.BlackHoleSnapshot::encode,net.bullettrain.xenopixelsmod.space.blackhole.BlackHoleSnapshot::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.BlackHoleClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","black_hole_status_v1"),
                net.bullettrain.xenopixelsmod.space.blackhole.BlackHoleStatus.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.blackhole.BlackHoleStatus::encode,net.bullettrain.xenopixelsmod.space.blackhole.BlackHoleStatus::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.BlackHoleClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","dragon_ball_planets_v1"),
                net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetSnapshot.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetSnapshot::encode,net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetSnapshot::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.DragonBallPlanetClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","planet_hakai_burn_v2"),
                net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiBurn.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiBurn::encode,net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiBurn::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.PlanetHakaiClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "planet_integrity_snapshot_v1"),
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegritySnapshot.class, AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegritySnapshot::encode,
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegritySnapshot::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "planet_integrity_update_v1"),
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityUpdate.class, AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityUpdate::encode,
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityUpdate::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "planet_integrity_atlas_request_v1"),
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasRequest.class, AddonPacketDirection.SERVERBOUND,
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasRequest::encode,
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasRequest::decode,
                (packet, context) -> context.enqueueWork(() -> context.sender().ifPresent(player ->
                        net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasService.request(player, packet))));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "planet_integrity_atlas_snapshot_v1"),
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasSnapshot.class, AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasSnapshot::encode,
                net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityAtlasSnapshot::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.PlanetIntegrityAtlasClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","hakai_sweep_timing_v2"),
                net.bullettrain.xenopixelsmod.combat.HakaiSweepTiming.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.combat.HakaiSweepTiming::encode,net.bullettrain.xenopixelsmod.combat.HakaiSweepTiming::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.combat.HakaiSweepFx.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","hakai_sweep_ghosts_v1"),
                net.bullettrain.xenopixelsmod.combat.HakaiSweepGhosts.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.combat.HakaiSweepGhosts::encode,net.bullettrain.xenopixelsmod.combat.HakaiSweepGhosts::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.combat.HakaiSweepGhostRenderer.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","wish_offer_v1"),
                net.bullettrain.xenopixelsmod.space.dragonball.WishOffer.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.dragonball.WishOffer::encode,net.bullettrain.xenopixelsmod.space.dragonball.WishOffer::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.screen.WishScreen.open(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","wish_choice_v1"),
                net.bullettrain.xenopixelsmod.space.dragonball.WishChoice.class,AddonPacketDirection.SERVERBOUND,
                net.bullettrain.xenopixelsmod.space.dragonball.WishChoice::encode,net.bullettrain.xenopixelsmod.space.dragonball.WishChoice::decode,
                (packet,context)->context.enqueueWork(()->context.sender().ifPresent(player ->
                        net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls.grant(player, packet.wishId()))));
        AddonNetwork.register(oxygenStatusPacketId(),
                net.bullettrain.xenopixelsmod.space.oxygen.OxygenStatus.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.oxygen.OxygenStatus::encode,net.bullettrain.xenopixelsmod.space.oxygen.OxygenStatus::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.hud.OxygenHud.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","asteroid_presence_v1"),
                net.bullettrain.xenopixelsmod.space.asteroid.AsteroidPresence.class,AddonPacketDirection.CLIENTBOUND,
                net.bullettrain.xenopixelsmod.space.asteroid.AsteroidPresence::encode,net.bullettrain.xenopixelsmod.space.asteroid.AsteroidPresence::decode,
                (packet,context)->context.enqueueWork(()-> {
                    if(FMLEnvironment.dist==Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.AsteroidFieldClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","space_handoff_notice_v1"),
                HandoffTransitionNotice.class, AddonPacketDirection.CLIENTBOUND,
                HandoffTransitionNotice::encode, HandoffTransitionNotice::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.SpaceHandoffOverlay.arm();
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","space_handoff_notice_v2"),
                HandoffTransitionNoticeV2.class, AddonPacketDirection.CLIENTBOUND,
                HandoffTransitionNoticeV2::encode, HandoffTransitionNoticeV2::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.SpaceHandoffOverlay.arm(packet.overlayMillis());
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod","space_handoff_notice_v3"),
                HandoffTransitionNoticeV3.class, AddonPacketDirection.CLIENTBOUND,
                HandoffTransitionNoticeV3::encode, HandoffTransitionNoticeV3::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.SpaceHandoffOverlay.arm(
                                packet.overlayMillis(), packet.plasmaShell());
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "space_handoff_notice_v4"),
                HandoffTransitionNoticeV4.class, AddonPacketDirection.CLIENTBOUND,
                HandoffTransitionNoticeV4::encode, HandoffTransitionNoticeV4::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.SpaceHandoffOverlay.armOutboundCoast(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "space_atmosphere_settings_v1"),
                SpaceAtmosphereSettingsPacket.class, AddonPacketDirection.CLIENTBOUND,
                SpaceAtmosphereSettingsPacket::encode, SpaceAtmosphereSettingsPacket::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.SpaceAtmosphereClient.apply(packet);
                }));
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", SpaceOrbitClockSnapshot.PACKET_ID),
                SpaceOrbitClockSnapshot.class, AddonPacketDirection.CLIENTBOUND,
                SpaceOrbitClockSnapshot::encode, SpaceOrbitClockSnapshot::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.space.SpaceOrbitClockClient.apply(packet);
                }));
    }

    public static void sendFallenStarsSettings(net.minecraft.server.level.ServerPlayer player) {
        AddonNetwork.sendToPlayer(player, new FallenStarsSettingsPacket(
                net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.fallenStarsEnabled()));
    }

    public static void syncFallenStarsSettings(net.minecraft.server.MinecraftServer server) {
        for (var player : server.getPlayerList().getPlayers()) sendFallenStarsSettings(player);
    }
}
