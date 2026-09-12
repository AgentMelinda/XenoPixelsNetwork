package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.render.EntityPreviewRenderContext;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;

/**
 * Stops the XenoHUD character portrait from rewriting the live player's GeckoLib controllers.
 *
 * <p><b>Target:</b> DragonMineZ animation selectors merged into {@link AbstractClientPlayer} by
 * {@code com.dragonminez.mixin.client.PlayerGeoAnimatableMixin} — {@code predicate} (movement),
 * {@code attackPredicate} (kick / punch) and {@code kiPredicate} (charge hold).
 * <b>Written for:</b> Minecraft 1.21.1 / NeoForge 21.1.238, DragonMineZ 2.1.3, GeckoLib 4.9.2.
 * <b>Side:</b> client — registered in the {@code "client"} array of {@code xenopixelsmod.mixins.json}.
 *
 * <p><b>The bug.</b> The portrait re-renders the same player entity the world already posed.
 * {@code setAndContinue(IDLE)} writes idle into the shared movement controller, and a second
 * {@code attackPredicate} pass can consume {@code currentMeleeAnim} or {@code forceAnimationReset}
 * the kick mid-swing. At high FPS the fight is a twitch; once the custom character renderer is
 * already under budget (large packs, shaders) the HUD pass lands far enough from the world pass
 * that the kick never reads and the body jitters. Turning the HUD off removes the second pass,
 * which is why the same swing works with the HUD hidden.
 *
 * <p><b>The fix.</b> While {@link EntityPreviewRenderContext#isHudPortrait()} is set, return
 * {@link PlayState#CONTINUE} without calling {@code setAndContinue}. That keeps whatever the
 * world render already selected. The bust may show the live pose (including flight) instead of
 * a forced idle; that is cheaper than mutating bones the world still owns.
 *
 * <p><b>Guideline notes</b> (§18): each injector is a static boolean read and an early return.
 * {@code require = 0} because the targets are methods another mod's mixin merges in: if DMZ
 * renames them the portrait degrades rather than failing at startup.
 */
@Mixin(value = AbstractClientPlayer.class, remap = false, priority = 1100)
public abstract class DmzPortraitFlightMixin {

	@Inject(method = "predicate", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private <T extends GeoAnimatable> void xeno$keepLiveMovementForPortrait(
			AnimationState<T> state, CallbackInfoReturnable<PlayState> cir) {
		if (EntityPreviewRenderContext.isHudPortrait()) {
			cir.setReturnValue(PlayState.CONTINUE);
		}
	}

	@Inject(method = "attackPredicate", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private <T extends GeoAnimatable> void xeno$keepLiveMeleeForPortrait(
			AnimationState<T> state, CallbackInfoReturnable<PlayState> cir) {
		if (EntityPreviewRenderContext.isHudPortrait()) {
			cir.setReturnValue(PlayState.CONTINUE);
		}
	}

	@Inject(method = "kiPredicate", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private <T extends GeoAnimatable> void xeno$keepLiveKiForPortrait(
			AnimationState<T> state, CallbackInfoReturnable<PlayState> cir) {
		if (EntityPreviewRenderContext.isHudPortrait()) {
			cir.setReturnValue(PlayState.CONTINUE);
		}
	}
}
