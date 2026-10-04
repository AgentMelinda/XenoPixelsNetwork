package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.util.SkinGathererProvider;
import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.client.maker.TaottoClientOverlays;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

/**
 * Additive Taotto overlay after DMZ {@code tattoo_N} layers. Does not replace gatherTattooLayers.
 */
@Mixin(value = SkinGathererProvider.class, remap = false)
public abstract class DmzTaottoOverlayMixin {
    @Inject(method = "gatherTattooLayers", at = @At("RETURN"), require = 1)
    private void xenopixels$taotto(
            AbstractClientPlayer player,
            StatsData stats,
            float partialTick,
            BiConsumer<ResourceLocation, float[]> consumer,
            CallbackInfo ci) {
        if (player == null || consumer == null) {
            return;
        }
        ResourceLocation texture = TaottoClientOverlays.texture(player.getUUID());
        if (texture != null) {
            consumer.accept(texture, TaottoClientOverlays.white());
        }
    }
}
