package net.bullettrain.xenopixelsmod.fx.effek;

import dev.architectury.networking.NetworkManager;
import mod.chloeprime.aaaparticles.api.common.AAALevel;
import mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo;
import mod.chloeprime.aaaparticles.common.network.S2CAddParticle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The only class that touches AAA Particles. Uses its common API only
 * ({@code ParticleEmitterInfo}, {@code AAALevel}), so it is safe on a dedicated server; the library
 * sends the effect to players within {@code range} of its position.
 */
final class AaaEffekSender implements EffekSender {
    @Override
    public void send(ServerLevel level, Request r) {
        if (r.boundEntity() >= 0) {
            Entity entity = level.getEntity(r.boundEntity());
            if (entity != null) {
                sendBound(level, r, entity);
                return;
            }
        }
        ParticleEmitterInfo info = ParticleEmitterInfo.create(level, r.id());
        info.position(r.pos());
        if (r.forward() != null && r.forward().lengthSqr() > 1.0e-9) info.rotationFromForward(r.forward());
        info.scale(r.scale());
        AAALevel.addParticle(level, r.range(), info);
    }

    /**
     * Bound to the entity and turned along its velocity (verified against AAA 2.3.1's
     * ParticleEmitterInfo.spawnInWorld: velocity rotation only applies when an entity-space offset
     * is set, and a set absolute position would be added on top of the entity's, so none is set).
     * AAA sends an unpositioned effect to the whole dimension, so the range check is done here and
     * the packet goes only to players near the entity.
     *
     * <p>The packet must go through Architectury's NetworkManager, exactly as AAA sends it: its
     * channel is registered with Architectury's own payload wrapper, and handing the raw payload to
     * NeoForge's PacketDistributor failed to encode and disconnected the player (2026-09-29,
     * "Failed encoding custom payload aaa_particles:0: ClassCastException ... BufCustomPacketPayload").
     * Architectury 13.0.8 is a compile-only dependency; the game always loads it, because AAA needs it.
     */
    private static void sendBound(ServerLevel level, Request r, Entity entity) {
        ParticleEmitterInfo info = ParticleEmitterInfo.create(level, r.id());
        info.bindOnEntity(entity);
        switch (r.follow()) {
            // Velocity rotation needs an entity-space offset set (AAA 2.3.1 spawnInWorld).
            case VELOCITY -> {
                info.entitySpaceRelativePosition(0.0, 0.0, 0.0);
                info.useEntityVelocityAsRotation();
            }
            // Head space: placed at the eyes and rotated (pitch, -yaw) from the view every frame,
            // which turns local +Z along the look.
            // The offset is rotated with the view; +Z is behind the eyes (AaaHeadSpaceOffsetTest).
            case LOOK -> {
                info.useEntityHeadSpace();
                info.entitySpaceRelativePosition(0.0, 0.0, r.lookAnchor());
            }
            // Bound with nothing else set: AAA places it at the entity's interpolated position
            // every frame and never rotates it, so an upright effect stays upright.
            default -> { }
        }
        info.scale(r.scale());
        S2CAddParticle packet = S2CAddParticle.of(info);
        for (ServerPlayer player : level.players()) {
            if (inRange(player.position(), r.pos(), r.range())) {
                NetworkManager.sendToPlayer(player, packet);
            }
        }
    }

    static boolean inRange(net.minecraft.world.phys.Vec3 player, net.minecraft.world.phys.Vec3 pos, double range) {
        return player.distanceToSqr(pos) <= range * range;
    }
}
