package net.bullettrain.xenopixelsmod.client.combat.beam;

import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.KeyMapping;
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

    /** Matches the server's radius, and DMZ's own charging-entity lookup in TechniqueDispatcher. */
    private static final double SEARCH_RADIUS = 30.0;

    private BeamSurgeClient() {
    }

    /** Packets sent this session, shown by the debug readout so a silent chain is obvious. */
    private static int sentCount;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!XenoClientConfig.beamSurgeClient) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || event.getEntity() != player) return;
        if (player.tickCount % SEND_INTERVAL_TICKS != 0) return;

        boolean keyDown = surgeKeyDown();
        if (!XenoClientConfig.beamSurgeDebug && (!keyDown || minecraft.screen != null)) return;
        AbstractKiProjectile wave = findOwnedWave(player);
        if (keyDown && minecraft.screen == null && wave != null) {
            sentCount++;
            ModNetwork.sendToServer(new BeamSurgePacket());
        }

        if (XenoClientConfig.beamSurgeDebug) {
            // Reports the whole chain in one line, because "it does not work" can mean the key is
            // not registering, the wave is not being recognised as ours, or the server is ignoring
            // a packet that did arrive -- and those need different fixes. Size is synced entity
            // data, so watching it climb proves the server side is applying growth without needing
            // a diagnostic packet of its own.
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§7surge key=§f" + keyDown
                            + " §7wave=§f" + (wave != null)
                            + " §7sent=§f" + sentCount
                            + " §7size=§f" + (wave == null ? "-" : String.format("%.2f", wave.getSize()))
                            + " §7distance=§f" + (wave == null ? "-" : String.format("%.1f blocks", wave.getClashBeamLength()))), true);
        }
    }

    /**
     * Whether the surge key is physically held.
     *
     * <p>Polls the window rather than reading {@link net.minecraft.client.KeyMapping#isDown()}.
     * {@code isDown()} is only set when NeoForge's key dispatch picks a binding out of its
     * modifier/conflict-context buckets, and that goes wrong for exactly the two cases this key
     * lives in: it shares its physical key with another mod's binding, and it sits on a key that is
     * itself a modifier. DragonMineZ hit the same wall and wrote {@code KeyBinds.isPhysicallyDown}
     * for its technique chords, so this reuses that rather than inventing a second answer.
     */
    private static boolean surgeKeyDown() {
        KeyMapping surge = net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient.BEAM_SURGE;
        // The pad is a second source, not a replacement - a gamepad press moves no physical key,
        // so the window poll below can never see it.
        if (net.bullettrain.xenopixelsmod.client.pad.XenoPadInput.held(surge)) return true;
        try {
            return KeyBinds.isPhysicallyDown(surge);
        } catch (Throwable t) {
            return surge.isDown();
        }
    }

    /**
     * Whether this player owns a wave that is currently firing.
     *
     * <p>Reads the world rather than tracking the cast, because a wave is anchored 2.5 blocks in
     * front of its owner and never moves, and because duplicating DMZ's charge-and-release state
     * machine on our side would drift out of step with theirs. Owner identity is compared by UUID
     * — the form DMZ's own {@code isFiringKiAttack} uses — since the client resolves a
     * projectile's owner from the spawn packet and reference equality is the more fragile test.
     */
    public static boolean ownsFiringWave(LocalPlayer player) {
        return findOwnedWave(player) != null;
    }

    /**
     * The firing beam this player owns, or null.
     *
     * <p>Scans {@link AbstractKiProjectile}, not {@code KiWaveEntity}. {@code TechniqueDispatcher}
     * spawns a {@code KiWaveEntity} for WAVE techniques but a {@code KiLaserEntity} for LASER and
     * BEAM ones, so looking only for waves silently excluded every laser and beam from surging.
     * {@code isFiring()} is declared on the shared base, so one scan covers both.
     *
     * <p>{@code KiBlastEntity} is excluded on purpose: a thrown ki ball is a different mechanic and
     * surging it is not what this is for.
     */
    public static AbstractKiProjectile findOwnedWave(LocalPlayer player) {
        if (player == null || player.level() == null) return null;
        AABB box = player.getBoundingBox().inflate(SEARCH_RADIUS);
        for (AbstractKiProjectile beam : player.level().getEntitiesOfClass(
                AbstractKiProjectile.class, box, BeamSurgeClient::surgeable)) {
            if (beam.isOwner(player)) return beam;
        }
        return null;
    }

    /** Alive, firing, not mid-clash, and a sustained beam rather than a thrown ball. */
    private static boolean surgeable(AbstractKiProjectile candidate) {
        return candidate.isAlive() && candidate.isFiring() && !candidate.isClashLocked()
                && !(candidate instanceof KiBlastEntity);
    }
}
