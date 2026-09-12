package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.model.DMZPlayerModel;
import net.bullettrain.xenopixelsmod.client.anim.studio.AnimBonePose;
import net.bullettrain.xenopixelsmod.client.anim.studio.StudioBoneSampler;
import net.bullettrain.xenopixelsmod.client.anim.studio.StudioBoneSpace;
import net.bullettrain.xenopixelsmod.client.anim.studio.StudioPoseBuffer;
import net.bullettrain.xenopixelsmod.client.anim.studio.XenoRig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies in-game studio bone transforms after DragonMineZ has run its own custom animations, and
 * samples the finished pose back out for the recorder.
 *
 * <p>Rotation is always written, because every bone in the buffer is there to be rotated. Position,
 * scale and visibility are written only when the pose actually owns those channels, so a
 * rotation-only clip leaves DragonMineZ's own translation, scaling and hiding alone instead of
 * flattening them.
 *
 * <p>The studio pose is <em>blended</em> onto whatever DragonMineZ posed rather than overwriting it:
 * at weight 1 that is bit-for-bit the old behaviour, and below 1 it is what lets a clip ease in at
 * the start and back out at the end instead of snapping.
 *
 * <p>Rotation goes through {@link StudioBoneSpace}: the studio holds animation-file degrees, and a
 * live bone holds {@code initialSnapshot + toRadians(±value)} the way {@code AnimationProcessor}
 * writes it, so PV POSE and PV BAKED see the same numbers. This runs at {@code setCustomAnimations}
 * RETURN, which {@code GeoModel.handleAnimations} calls after {@code tickAnimation}.
 *
 * <p>{@code GeoBone} declares {@code setRot*}, {@code setPos*}, {@code setScale*}, {@code setHidden},
 * {@code getInitialSnapshot} and the matching getters; {@code BoneSnapshot} declares
 * {@code getRot*}, {@code getOffset*} and {@code getScale*}; verified with {@code javap} against
 * geckolib-neoforge-1.21.1-4.9.2.jar.
 */
@Mixin(value = DMZPlayerModel.class, remap = false)
public abstract class DmzStudioPoseMixin {
    @Inject(
            method = "setCustomAnimations(Lnet/minecraft/client/player/AbstractClientPlayer;JLsoftware/bernie/geckolib/animation/AnimationState;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void xenopixels$studioPose(AbstractClientPlayer player, long instanceId,
                                       AnimationState<?> state, CallbackInfo ci) {
        if (player == null) return;
        try {
            AnimationProcessor<?> processor = ((DMZPlayerModel<?>) (Object) this).getAnimationProcessor();
            if (processor == null) return;

            if (StudioBoneSampler.wanted() && isLocalPlayer(player)) {
                xenopixels$sample(processor);
            }
            if (!StudioPoseBuffer.appliesTo(player.getUUID())) return;

            float weight = StudioPoseBuffer.weight();
            if (weight <= 0f) return;
            for (var entry : StudioPoseBuffer.live().entrySet()) {
                GeoBone bone = processor.getBone(entry.getKey());
                AnimBonePose pose = entry.getValue();
                if (bone == null || pose == null) continue;
                BoneSnapshot init = bone.getInitialSnapshot();
                bone.setRotX(xenopixels$mix(bone.getRotX(),
                        StudioBoneSpace.toBoneRadians(0, pose.rotX, init.getRotX()), weight));
                bone.setRotY(xenopixels$mix(bone.getRotY(),
                        StudioBoneSpace.toBoneRadians(1, pose.rotY, init.getRotY()), weight));
                bone.setRotZ(xenopixels$mix(bone.getRotZ(),
                        StudioBoneSpace.toBoneRadians(2, pose.rotZ, init.getRotZ()), weight));
                if (pose.hasPosition()) {
                    bone.setPosX(xenopixels$mix(bone.getPosX(), pose.posX, weight));
                    bone.setPosY(xenopixels$mix(bone.getPosY(), pose.posY, weight));
                    bone.setPosZ(xenopixels$mix(bone.getPosZ(), pose.posZ, weight));
                }
                if (pose.hasScale()) {
                    bone.setScaleX(xenopixels$mix(bone.getScaleX(), pose.scaleX, weight));
                    bone.setScaleY(xenopixels$mix(bone.getScaleY(), pose.scaleY, weight));
                    bone.setScaleZ(xenopixels$mix(bone.getScaleZ(), pose.scaleZ, weight));
                }
                // Hiding is a switch, so it only takes effect once the blend has mostly arrived.
                if (pose.hasVisibility() && weight > 0.5f) {
                    bone.setHidden(!pose.visible);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static float xenopixels$mix(float current, float target, float weight) {
        return weight >= 1f ? target : Mth.lerp(weight, current, target);
    }

    private static boolean isLocalPlayer(AbstractClientPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null
                && minecraft.player.getUUID().equals(player.getUUID());
    }

    private static void xenopixels$sample(AnimationProcessor<?> processor) {
        Map<String, AnimBonePose> pose = new LinkedHashMap<>();
        for (String name : XenoRig.COMBAT) {
            GeoBone bone = processor.getBone(name);
            if (bone == null) continue;
            BoneSnapshot init = bone.getInitialSnapshot();
            AnimBonePose sampled = AnimBonePose.rot(
                    StudioBoneSpace.toStudioDegrees(0, bone.getRotX(), init.getRotX()),
                    StudioBoneSpace.toStudioDegrees(1, bone.getRotY(), init.getRotY()),
                    StudioBoneSpace.toStudioDegrees(2, bone.getRotZ(), init.getRotZ()));
            // Position and scale are absolute in GeckoLib, so a bone counts as moved only when it
            // differs from the model's rest values rather than from 0 / 1.
            if (bone.getPosX() != init.getOffsetX() || bone.getPosY() != init.getOffsetY()
                    || bone.getPosZ() != init.getOffsetZ()) {
                sampled.setPosition(bone.getPosX(), bone.getPosY(), bone.getPosZ());
            }
            if (bone.getScaleX() != init.getScaleX() || bone.getScaleY() != init.getScaleY()
                    || bone.getScaleZ() != init.getScaleZ()) {
                sampled.setScale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
            }
            pose.put(name, sampled);
        }
        StudioBoneSampler.submit(pose);
    }
}
