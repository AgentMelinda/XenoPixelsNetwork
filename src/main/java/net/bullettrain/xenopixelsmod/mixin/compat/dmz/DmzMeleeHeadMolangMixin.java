package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.model.DMZPlayerModel;
import net.bullettrain.xenopixelsmod.client.combat.DmzMeleeHeadGate;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animation.AnimationState;

/**
 * Feeds body yaw and a level pitch into DragonMineZ's Molang look queries for the combo window, so
 * the head (and the hair parented to it) turns with the body instead of tracking the camera.
 *
 * <h2>Why only {@code applyMolangQueries}</h2>
 * This used to swap around {@code setCustomAnimations} as well. Reading DragonMineZ 2.1.3 shows
 * that is pointless: {@code setCustomAnimations} drives the {@code head} bone from
 * {@code EntityModelData.headPitch()} / {@code netHeadYaw()}, which the renderer computed
 * <em>before</em> the call, so swapping the entity's fields inside it cannot move the head at all.
 * Its only real effect there was on the ki-shooting arm's aim, which is not something a punch
 * should be retargeting. Zeroing the head bone itself is {@code DmzMeleeHeadLookMixin}'s job.
 * {@code applyMolangQueries} is different — it reads the entity fields directly to publish
 * {@code query.head_x_rotation} and {@code query.head_y_rotation}, which the animation clips use.
 *
 * <h2>Swap both halves of every interpolated pair</h2>
 * DragonMineZ lerps each of these between its previous-tick and current value:
 * {@code Mth.lerp(pt, xRotO, getXRot())}, and likewise for head yaw. An earlier version set
 * {@code xRot} to zero but left {@code xRotO} at the real pitch, so the published pitch swept from
 * the true pitch down to zero across each tick's frames and snapped back at the next tick — a
 * sawtooth at tick rate on the head bone, and therefore on the hair hanging off it. That is the
 * flicker this class was supposed to prevent. Both halves move together now, and
 * {@link net.minecraft.world.entity.Entity#setXRot} does not touch {@code xRotO}, so it is
 * assigned explicitly.
 *
 * <h2>Descriptors matter here</h2>
 * The target must be named by full descriptor and the handler's parameters must be the exact
 * target types. Declaring them as {@code Object} makes Mixin reject the descriptor, and one bad
 * injector fails the <b>whole</b> mixin - silently, save for one line in the client log:
 * {@code Mixin apply for mod xenopixelsmod failed ...DmzMeleeHeadMolangMixin}. Grep for that after
 * touching this file.
 */
@Mixin(value = DMZPlayerModel.class, remap = false)
public abstract class DmzMeleeHeadMolangMixin {

    @Unique
    private float xenopixels$savedHeadYaw;
    @Unique
    private float xenopixels$savedHeadYawO;
    @Unique
    private float xenopixels$savedPitch;
    @Unique
    private float xenopixels$savedPitchO;
    @Unique
    private AbstractClientPlayer xenopixels$swapped;

    @Inject(
            method = "applyMolangQueries(Lsoftware/bernie/geckolib/animation/AnimationState;D)V",
            at = @At("HEAD"),
            require = 0
    )
    private void xenopixels$headFollowsBodyPre(AnimationState<?> state, double animTime, CallbackInfo ci) {
        xenopixels$swapped = null;
        Object animatable = state != null ? state.getAnimatable() : null;
        if (!DmzMeleeHeadGate.active(animatable)) return;
        if (!(animatable instanceof AbstractClientPlayer player)) return;
        xenopixels$savedHeadYaw = player.yHeadRot;
        xenopixels$savedHeadYawO = player.yHeadRotO;
        xenopixels$savedPitch = player.getXRot();
        xenopixels$savedPitchO = player.xRotO;
        player.yHeadRot = player.yBodyRot;
        player.yHeadRotO = player.yBodyRotO;
        player.setXRot(0.0f);
        player.xRotO = 0.0f;
        xenopixels$swapped = player;
    }

    @Inject(
            method = "applyMolangQueries(Lsoftware/bernie/geckolib/animation/AnimationState;D)V",
            at = @At("RETURN"),
            require = 0
    )
    private void xenopixels$headFollowsBodyPost(AnimationState<?> state, double animTime, CallbackInfo ci) {
        // Restore the player this pass swapped, not whoever the state names now: the model instance
        // is shared across every player it draws, and a shader pack renders the scene more than
        // once per frame.
        AbstractClientPlayer player = xenopixels$swapped;
        if (player == null) return;
        xenopixels$swapped = null;
        player.yHeadRot = xenopixels$savedHeadYaw;
        player.yHeadRotO = xenopixels$savedHeadYawO;
        player.setXRot(xenopixels$savedPitch);
        player.xRotO = xenopixels$savedPitchO;
    }
}
