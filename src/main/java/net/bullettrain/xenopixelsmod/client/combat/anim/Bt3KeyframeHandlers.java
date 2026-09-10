package net.bullettrain.xenopixelsmod.client.combat.anim;

import net.bullettrain.xenopixelsmod.client.pad.XenoPadInput;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.keyframe.event.CustomInstructionKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;

/** Presentation-only callbacks for XenoPixels keyframes on DMZ's attack controller. */
public final class Bt3KeyframeHandlers {

    private Bt3KeyframeHandlers() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void attach(AnimationController<?> controller) {
        if (controller == null || !"attack_controller".equals(controller.getName())) return;
        attachTyped((AnimationController) controller);
    }

    private static <T extends GeoAnimatable> void attachTyped(AnimationController<T> controller) {
        controller.setSoundKeyframeHandler(Bt3KeyframeHandlers::sound);
        controller.setParticleKeyframeHandler(Bt3KeyframeHandlers::particle);
        controller.setCustomInstructionKeyframeHandler(Bt3KeyframeHandlers::custom);
    }

    private static <T extends GeoAnimatable> void sound(SoundKeyframeEvent<T> event) {
        if (!(event.getAnimatable() instanceof Player player) || !Bt3CinematicRushClient.isActive(player)) return;
        String cue = event.getKeyframeData().getSound();
        boolean heavy = cue != null && cue.contains("finish");
        player.level().playLocalSound(
                player.getX(), player.getY(), player.getZ(),
                heavy ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS,
                heavy ? 1.0f : 0.65f,
                heavy ? 0.72f : 1.18f,
                false
        );
    }

    private static <T extends GeoAnimatable> void particle(ParticleKeyframeEvent<T> event) {
        if (!(event.getAnimatable() instanceof Player player) || !Bt3CinematicRushClient.isActive(player)) return;
        String effect = event.getKeyframeData().getEffect();
        boolean heavy = effect != null && effect.contains("finish");
        int count = heavy ? 8 : 3;
        for (int index = 0; index < count; index++) {
            double spread = heavy ? 0.65 : 0.3;
            double offsetX = (player.getRandom().nextDouble() - 0.5) * spread;
            double offsetY = 0.65 + player.getRandom().nextDouble() * 0.9;
            double offsetZ = (player.getRandom().nextDouble() - 0.5) * spread;
            player.level().addParticle(
                    heavy ? ParticleTypes.CRIT : ParticleTypes.SWEEP_ATTACK,
                    player.getX() + offsetX,
                    player.getY() + offsetY,
                    player.getZ() + offsetZ,
                    0.0, 0.0, 0.0
            );
        }
    }

    private static <T extends GeoAnimatable> void custom(CustomInstructionKeyframeEvent<T> event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(event.getAnimatable() instanceof Player player)
                || player != minecraft.player
                || !Bt3CinematicRushClient.isActive(player)) {
            return;
        }
        String instruction = event.getKeyframeData().getInstructions();
        boolean heavy = instruction != null && instruction.contains("finish");
        XenoPadInput.rumbleImpact(heavy ? 0.85f : 0.34f, heavy ? 7 : 3);
    }
}
