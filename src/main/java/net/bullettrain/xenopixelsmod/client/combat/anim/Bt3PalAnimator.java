package net.bullettrain.xenopixelsmod.client.combat.anim;

import net.bullettrain.xenopixelsmod.client.combat.DmzAnimHelperClient;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.minecraft.world.entity.player.Player;

/**
 * Plays a BT3 beat on the DragonMineZ GeckoLib player. PlayerAnimationLibrary is not a
 * dependency; this class name is leftover from the PAL adapter and only dispatches to DMZ.
 */
public final class Bt3PalAnimator {

    private Bt3PalAnimator() {
    }

    public static void register() {
        // PAL factory registration removed with the library.
    }

    /**
     * Plays a beat at whatever speed lets its clip finish inside one mash beat.
     *
     * <p>A held mash retriggers on a fixed cadence, and every clip these beats name is longer than
     * that cadence, so at the authored speed each swing was cut off partway and restarted from its
     * wind-up. {@link Bt3AnimationBinding#mashSpeed} scales the clip to the beat instead.
     *
     * <p>Any queued follow-up strike is dropped first: a chain armed by an earlier charge release
     * would otherwise fire into the middle of the string and restart a swing that is already
     * running.
     */
    public static void play(Player player, Bt3AnimationIntent intent) {
        if (player == null || intent == null || !XenoClientConfig.bt3CombatAnims) {
            return;
        }
        int generation = XenoServerClientState.get().comboAnimGeneration;
        Bt3AnimationBinding.Binding binding = Bt3AnimationBinding.of(intent, generation);
        if (binding == null) {
            return;
        }
        DmzAnimHelperClient.ClientStrikeChain.clear();
        float speed = Bt3AnimationBinding.mashSpeed(
                intent, XenoServerClientState.get().comboMashIntervalTicks, generation);
        DmzAnimHelperClient.playLocalMelee(player, binding.dmzAnim(), false, speed);
    }
}
