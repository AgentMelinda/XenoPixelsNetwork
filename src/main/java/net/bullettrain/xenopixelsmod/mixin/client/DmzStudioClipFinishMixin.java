package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;

import java.lang.reflect.Field;

/**
 * Lets a Xeno studio clip on DragonMineZ's melee controller finish instead of being cut.
 *
 * <p><b>Target:</b> {@code attackPredicate}, merged into {@link AbstractClientPlayer} by
 * {@code com.dragonminez.mixin.client.PlayerGeoAnimatableMixin} (DMZ 2.1.3, GeckoLib 4.9.2).
 *
 * <p><b>The bug.</b> One-shot clips ({@code XenoAnimApi.playClip}, the NPC greet wave) go through
 * {@code dragonminez$playMeleeAnimation}. DMZ's predicate keeps that controller alive for
 * {@code dragonminez$attackAnimTicks = max(8, round(12 / speed))} ticks - a punch's length - and
 * then answers {@link PlayState#STOP}, which in GeckoLib drops the pose at once. A 2.5-3 s wave
 * therefore played most of the way and then snapped back to rest instead of reaching its own eased
 * ending (checked with javap: bytecode offsets 158-180 and 241-257 of {@code attackPredicate}).
 *
 * <p><b>The fix.</b> Before DMZ runs, if the controller is still playing one of this mod's clips
 * ({@code combat.xeno_*}) and GeckoLib has not reported it finished, the counter is topped up so
 * DMZ answers CONTINUE. Once the clip ends - on its authored rest pose - the counter lapses and DMZ
 * stops it exactly as before. Punches and DMZ's own clips are untouched.
 *
 * <p>The counter is a field another mod's mixin adds, so it is reached by reflection and cached;
 * {@code require = 0} and a failed lookup both degrade to DMZ's original behaviour.
 */
@Mixin(value = AbstractClientPlayer.class, remap = false, priority = 1100)
public abstract class DmzStudioClipFinishMixin {

    private static final String COUNTER = "dragonminez$attackAnimTicks";
    private static volatile Field counter;
    private static volatile boolean lookupFailed;

    @Inject(method = "attackPredicate", at = @At("HEAD"), require = 0, remap = false)
    private <T extends GeoAnimatable> void xeno$letStudioClipFinish(AnimationState<T> state,
                                                                     CallbackInfoReturnable<PlayState> cir) {
        AnimationController<T> controller = state.getController();
        if (controller == null || controller.hasAnimationFinished()) {
            return;
        }
        AnimationProcessor.QueuedAnimation current = controller.getCurrentAnimation();
        if (current == null || current.animation() == null
                || current.animation().name() == null
                || !current.animation().name().startsWith(XenoAnimApi.PREFIX)) {
            return;
        }
        if (controller.getAnimationState() == AnimationController.State.STOPPED) {
            return;
        }
        Field field = counterField(this);
        if (field == null) {
            return;
        }
        try {
            // Two, because DMZ decrements once per tick before it tests the counter.
            if (field.getInt(this) < 2) {
                field.setInt(this, 2);
            }
        } catch (IllegalAccessException | RuntimeException ignored) {
            lookupFailed = true;
        }
    }

    private static Field counterField(Object self) {
        Field cached = counter;
        if (cached != null || lookupFailed) {
            return cached;
        }
        try {
            Field field = self.getClass().getDeclaredField(COUNTER);
            field.setAccessible(true);
            counter = field;
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Subclasses (LocalPlayer, RemotePlayer, proxies) inherit the field from
            // AbstractClientPlayer, where DMZ's mixin merged it.
            try {
                Field field = AbstractClientPlayer.class.getDeclaredField(COUNTER);
                field.setAccessible(true);
                counter = field;
                return field;
            } catch (ReflectiveOperationException | RuntimeException again) {
                lookupFailed = true;
                return null;
            }
        }
    }
}
