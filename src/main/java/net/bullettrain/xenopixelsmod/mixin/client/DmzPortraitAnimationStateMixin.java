package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.render.DMZPlayerRenderer;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.mixin.client.GeoModelAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stops the XenoHUD character portrait from animating the player a second time each frame.
 *
 * <p><b>Target:</b> {@code DMZPlayerRenderer#render}, the call to
 * {@code GeoModelAccessor#dmz$setLastRenderedInstance(long)}.
 * <b>Written for:</b> Minecraft 1.21.1 / NeoForge 21.1.238, DragonMineZ 2.1.3, GeckoLib 4.9.2.
 * <b>Side:</b> client.
 *
 * <p><b>The bug.</b> {@code lastRenderedInstance} is GeckoLib's marker for "this model instance was
 * already processed this frame"; resetting it to -1 forces a full re-process. DragonMineZ resets it
 * unconditionally at the top of {@code render} (upstream commit {@code 89d7e038}, to fix shared
 * player models) — <i>before</i> it checks {@code isHudPortrait()} a few lines below, which it does
 * use to suppress the fast-fly rotation, shader effects, the transformation mask and the aura.
 *
 * <p>XenoPixels renders the local player again, offscreen, for the HUD portrait. With the reset
 * unconditional, that second render makes GeckoLib animate the same player twice per frame,
 * interleaved with the world render. Two passes sharing one animation state leaves bones driven by
 * more than one controller — arms and shoulders especially — landing on a different interpolation
 * each pass, which shows up as the model jittering in place while charging, a shoulder twitching in
 * flight, and XenoPixels' own kick/fist combat animations breaking mid-swing.
 *
 * <p><b>The fix.</b> Skip only that reset while the portrait is drawing, so the portrait reuses the
 * animation state the world render already computed. The portrait is a posed bust — it does not
 * need its own animation pass — and the world render is left exactly as DragonMineZ intended.
 *
 * <p><b>Guideline notes</b> (§18): {@code @WrapOperation} is the narrowest injector that can decline
 * a single call without touching the surrounding method. The body is one static boolean read, no
 * allocation, no logging, no reflection. {@code require = 0} because the target belongs to another
 * mod: if DragonMineZ moves or removes the reset, this degrades to today's behaviour rather than
 * failing at startup. DragonMineZ itself is not modified.
 */
@Mixin(value = DMZPlayerRenderer.class, remap = false)
public abstract class DmzPortraitAnimationStateMixin {

	@WrapOperation(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lcom/dragonminez/mixin/client/GeoModelAccessor;dmz$setLastRenderedInstance(J)V"
			),
			remap = false,
			require = 0
	)
	private void xeno$keepAnimationStateForPortrait(GeoModelAccessor accessor, long value,
													Operation<Void> original) {
		if (EntityPreviewRenderContext.isHudPortrait()) return;
		original.call(accessor, value);
	}
}
