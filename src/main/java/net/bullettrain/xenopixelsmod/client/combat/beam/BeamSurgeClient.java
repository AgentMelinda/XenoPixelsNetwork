package net.bullettrain.xenopixelsmod.client.combat.beam;

import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.BeamSurgePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Reports that the local player is still holding the key that fired their beam.
 *
 * <p>Deliberately a dumb sensor. It answers one question — is the use key down while I own a
 * firing wave — and says so; it never decides how big the beam should be. That decision is
 * entirely {@code BeamSurgeManager}'s, so a modified client gains nothing beyond claiming a key
 * is held, which it could do by holding the key anyway.
 *
 * <p>The wave is found by looking near the player rather than tracked from the cast: a wave is
 * anchored at its origin and grows outward, so it stays put beside its owner, and reading the
 * world avoids duplicating DMZ's charge-and-release state machine on our side where it would
 * drift out of step with theirs.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class BeamSurgeClient {

    /**
     * Every other tick, matching {@code TechniqueChargeC2S.updateAim}'s cadence. The server holds
     * a report valid for several ticks, so this costs nothing in responsiveness.
     */
    private static final int SEND_INTERVAL_TICKS = 2;

    /** Matches the server's search radius; a wave sits beside its owner. */
    private static final double SEARCH_RADIUS = 12.0;

    private BeamSurgeClient() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!XenoClientConfig.beamSurgeClient) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || event.getEntity() != player) return;
        if (player.tickCount % SEND_INTERVAL_TICKS != 0) return;

        // A wave is cast with an Alt+1..4 slot chord and only starts firing once that chord is
        // RELEASED, so there is no DMZ key held during the firing window at all. Surge therefore
        // needs a binding of its own, which the player holds while the beam is out.
        if (!net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient.BEAM_SURGE.isDown()) return;
        if (minecraft.screen != null) return;
        if (!ownsFiringWave(player)) return;

        ModNetwork.sendToServer(new BeamSurgePacket());
    }

    /**
     * Whether this player owns a wave that is currently firing.
     *
     * <p>Public because the shared C binding needs the same answer: while this is true the key
     * drives the surge and the ki-blast cancel stands down.
     *
     * <p>Reads the world rather than tracking the cast, because a wave is anchored 2.5 blocks in
     * front of its owner and never moves, and because duplicating DMZ's charge-and-release state
     * machine on our side would drift out of step with theirs. Owner identity is compared by UUID
     * — the form DMZ's own {@code isFiringKiAttack} uses — since the client resolves a
     * projectile's owner from the spawn packet and reference equality is the more fragile test.
     */
    public static boolean ownsFiringWave(LocalPlayer player) {
        if (player == null || player.level() == null) return false;
        AABB box = player.getBoundingBox().inflate(SEARCH_RADIUS);
        for (KiWaveEntity wave : player.level().getEntitiesOfClass(KiWaveEntity.class, box,
                candidate -> candidate.isAlive() && candidate.isFiring())) {
            if (wave.isOwner(player)) return true;
        }
        return false;
    }
}
