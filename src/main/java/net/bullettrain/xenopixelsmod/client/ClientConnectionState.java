package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.AfterimageGhostRenderer;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.client.combat.ClientChaseFlightState;
import net.bullettrain.xenopixelsmod.client.combat.ClientLockState;
import net.bullettrain.xenopixelsmod.client.combat.SparkingChargeClientState;
import net.bullettrain.xenopixelsmod.client.combat.SparkingClientState;
import net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimIntentClient;
import net.bullettrain.xenopixelsmod.client.combat.anim.Bt3CinematicRushClient;
import net.bullettrain.xenopixelsmod.client.combat.aura.XenoAuraScaling;
import net.bullettrain.xenopixelsmod.client.combat.fx.CombatFxClient;
import net.bullettrain.xenopixelsmod.client.compat.npc.DmzLockOnClient;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAnimationClient;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAuraClient;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcHairVis;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcTransformHairClient;
import net.bullettrain.xenopixelsmod.client.flight.ClientFlightState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Clears every connection-scoped Xeno client cache before and after a session. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientConnectionState {
    private ClientConnectionState() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        reset();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void reset() {
        XenoServerClientState.clear();
        ClientParty.clearAll();
        ClientFlightState.clear();
        ClientLockState.clear();
        ClientChaseFlightState.reset();
        SparkingClientState.clear();
        SparkingChargeClientState.clear();
        XenoAuraScaling.clear();
        CombatFxClient.reset();
        AfterimageGhostRenderer.clear();
        Bt3AnimIntentClient.clearPrediction();
        Bt3CinematicRushClient.clear();
        Bt3CombatClient.ForgeBus.resetConnectionState();
        NpcAppearanceClient.clear();
        NpcAuraClient.clear();
        NpcAnimationClient.clear();
        NpcTransformHairClient.clearAll();
        NpcHairVis.clearCache();
        DmzLockOnClient.clear();
        DmzClientStats.clear();
    }
}
