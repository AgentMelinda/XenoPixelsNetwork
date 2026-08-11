package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.network.C2S.KiBlastC2S;
import com.dragonminez.common.network.PacketRateLimiter;
import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes DragonMineZ basic-ki-blast cadence server-configurable from XenoPixels. */
@Mixin(value = KiBlastC2S.class, remap = false)
public abstract class KiBlastC2SMixin {
    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true)
    private static void xenopixelsmod$rateLimitKiBlast(ServerPlayer player, KiBlastC2S packet,
                                                        StatsData data, CallbackInfo ci) {
        int cooldown = Math.max(1, XenoServerConfig.kiBlastCooldownTicks);
        if (!PacketRateLimiter.allow(player.getUUID(), "xenopixels_ki_blast",
                player.level().getGameTime(), cooldown)) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "lambda$handle$0", constant = @Constant(intValue = 32))
    private static int xenopixelsmod$configuredKiBlastCooldown(int original) {
        return Math.max(1, XenoServerConfig.kiBlastCooldownTicks);
    }
}
