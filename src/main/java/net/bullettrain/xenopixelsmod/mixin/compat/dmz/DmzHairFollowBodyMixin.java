package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.render.hair.HairRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.client.combat.DmzMeleeHeadGate;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hair inertia has to agree with where the head actually is.
 *
 * <p>The strands hang off the {@code head} bone — {@code DMZHairLayer.renderForBone} translates to
 * that bone's pivot — but the sway that DragonMineZ adds on top projects the player's velocity into
 * head space using {@code Mth.lerp(partialTick, player.yHeadRotO, player.yHeadRot)}, straight off
 * the entity. During the combo window {@code DmzMeleeHeadLookMixin} pins the head bone to the body,
 * so those two disagree by however far the camera has been turned away from the body, and the
 * inertia pushes the hair in a direction the head is not facing — worst while mashing, which is
 * exactly when the body is spinning and the camera is not.
 *
 * <p>This layer renders after {@code setCustomAnimations} has returned, so the swap that
 * {@code DmzMeleeHeadMolangMixin} makes for the Molang queries is long since undone by the time
 * the hair is drawn; it has to be made again here. Both halves of the interpolated pair move
 * together, or the lerp between them produces a per-frame sawtooth.
 */
@Mixin(value = HairRenderer.class, remap = false)
public abstract class DmzHairFollowBodyMixin {

    @Unique
    private static AbstractClientPlayer xenopixels$swapped;
    @Unique
    private static float xenopixels$savedHeadYaw;
    @Unique
    private static float xenopixels$savedHeadYawO;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private static void xenopixels$hairFollowsBodyPre(
            PoseStack pose,
            net.minecraft.client.renderer.MultiBufferSource buffers,
            com.dragonminez.common.hair.CustomHair a,
            com.dragonminez.common.hair.CustomHair b,
            float partial,
            com.dragonminez.common.stats.character.Character character,
            com.dragonminez.common.stats.StatsData stats,
            AbstractClientPlayer player,
            float[] c1, float[] c2,
            boolean p1, boolean p2,
            float f1, int i1, int i2,
            float f2, float f3, float f4,
            CallbackInfo ci) {
        xenopixels$swapped = null;
        if (player == null || !DmzMeleeHeadGate.active(player)) return;
        xenopixels$savedHeadYaw = player.yHeadRot;
        xenopixels$savedHeadYawO = player.yHeadRotO;
        player.yHeadRot = player.yBodyRot;
        player.yHeadRotO = player.yBodyRotO;
        xenopixels$swapped = player;
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private static void xenopixels$hairFollowsBodyPost(
            PoseStack pose,
            net.minecraft.client.renderer.MultiBufferSource buffers,
            com.dragonminez.common.hair.CustomHair a,
            com.dragonminez.common.hair.CustomHair b,
            float partial,
            com.dragonminez.common.stats.character.Character character,
            com.dragonminez.common.stats.StatsData stats,
            AbstractClientPlayer player,
            float[] c1, float[] c2,
            boolean p1, boolean p2,
            float f1, int i1, int i2,
            float f2, float f3, float f4,
            CallbackInfo ci) {
        AbstractClientPlayer swapped = xenopixels$swapped;
        if (swapped == null) return;
        xenopixels$swapped = null;
        swapped.yHeadRot = xenopixels$savedHeadYaw;
        swapped.yHeadRotO = xenopixels$savedHeadYawO;
    }
}
