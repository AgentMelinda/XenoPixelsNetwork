package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem;
import net.bullettrain.xenopixelsmod.combat.Bt3SparkingReleaseCap;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lifts DMZ 2.2's server release cap while Sparking is active.
 *
 * <p>The exact 2.2 fixes.2 TickHandler reads {@link StatsData#getMaxPowerRelease()} both when
 * charging and when enforcing its once-per-second release clamp. Both reads must see the Sparking
 * ceiling, or the independent clamp immediately undoes the charge. The native cap includes racial
 * modifiers and is never reduced here.
 *
 * <p>{@link Bt3SparkingSystem} separately parks and lifts the player's release-limit throttle.
 * This redirect changes neither the Potential Unlock skill nor its native charging ramp. Once
 * Sparking ends, both reads return the native cap again.
 */
@Mixin(targets = "com.dragonminez.server.events.players.TickHandler", remap = false)
public abstract class DmzPowerReleaseSparkingMixin {

    @Redirect(
            method = {
                    "chargePowerRelease(Lcom/dragonminez/common/stats/StatsData;IZ)V",
                    "lambda$onPlayerTick$1(Lnet/minecraft/server/level/ServerPlayer;ILjava/util/UUID;"
                            + "Lcom/dragonminez/common/stats/StatsData;)V"
            },
            at = @At(value = "INVOKE",
                    target = "Lcom/dragonminez/common/stats/StatsData;getMaxPowerRelease()I"),
            require = 2)
    private static int xenopixels$sparkingReleaseCeiling(StatsData data) {
        int nativeCap = data.getMaxPowerRelease();
        if (!(playerOf(data) instanceof ServerPlayer player)) return nativeCap;
        return Bt3SparkingReleaseCap.effective(nativeCap, XenoServerConfig.sparkingReleaseLimit,
                Bt3SparkingSystem.isSparking(player));
    }

    private static net.minecraft.world.entity.player.Player playerOf(StatsData data) {
        try {
            return data.getResources() == null ? null : data.getResources().getPlayer();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
