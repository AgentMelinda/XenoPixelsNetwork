package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.render.effects.AuraBorderRenderer;
import com.dragonminez.common.stats.StatsData;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.bullettrain.xenopixelsmod.client.aura.HakaiEnergyAura;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Selects the existing native body border without setting DMZ's Surge flag. */
@Mixin(value = AuraBorderRenderer.class, remap = false)
public abstract class DmzHakaiEnergyAuraMixin {
    @ModifyReturnValue(method = "playerColor(Lnet/minecraft/world/entity/player/Player;Lcom/dragonminez/common/stats/StatsData;)[F",
            at = @At("RETURN"), require = 1)
    private static float[] xenopixels$hakaiEnergy(float[] original, Player player, StatsData stats) {
        return HakaiEnergyAura.colour(player, stats, original);
    }
}
