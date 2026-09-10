package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.events.FlySkillEvent;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.pad.XenoPadInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Scales DragonMineZ flight speed by how far the left stick is pushed.
 *
 * <p>DMZ's flight <i>direction</i> is digital by construction — {@code handleFlightMovement} reads
 * each of {@code Options.keyUp/keyDown/keyLeft/keyRight} as {@code isDown() ? 1 : 0} and combines
 * them — so a stick cannot steer it more finely than WASD can. What a stick can add is magnitude,
 * and that is all this does: DMZ still decides the direction, and the velocity it produces is
 * multiplied by the stick's deflection before it reaches the player.
 *
 * <p>Only the horizontal components are scaled. Ascend and descend are their own buttons, so
 * scaling Y would make the stick quietly fight them.
 *
 * <p>A centred stick is left alone rather than scaled to zero, so DMZ's own coast and hover
 * behaviour still runs when the player is not steering.
 *
 * <p>Off by default behind {@code padAnalogueFlight}; the emulated-key path alone is what matches
 * keyboard flight exactly.
 */
@Mixin(value = FlySkillEvent.class, remap = false)
public abstract class DmzFlyAnalogueStickMixin {

    /** Below this the stick counts as centred, and DMZ's vector is passed through untouched. */
    private static final float DEADZONE = 0.05f;

    @Redirect(
            method = "handleFlightMovement",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;setDeltaMovement("
                            + "Lnet/minecraft/world/phys/Vec3;)V"),
            require = 0)
    private static void xenopixels$scaleFlightByStick(LocalPlayer player, Vec3 movement) {
        player.setDeltaMovement(xenopixels$scale(movement));
    }

    private static Vec3 xenopixels$scale(Vec3 movement) {
        if (movement == null || !XenoClientConfig.padAnalogueFlight) return movement;
        if (!XenoPadInput.controllerActive()) return movement;

        float x = XenoPadInput.flightRoll();
        float y = XenoPadInput.flightPitch();
        float magnitude = (float) Math.sqrt(x * x + y * y);
        if (magnitude <= DEADZONE) return movement;

        float scale = Math.min(1f, magnitude);
        return new Vec3(movement.x * scale, movement.y, movement.z * scale);
    }
}
