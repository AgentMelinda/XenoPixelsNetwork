package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Target: {@code AbstractKiProjectile#getKiGriefingSource}
 * Reason: a missing live owner falls through to the projectile itself, so griefing uses
 *         mob flags / {@code allowKiGriefingMobs} instead of the firing player.
 * Version: NeoForge 1.21.1 / DMZ 2.1.x
 * Side: common. One UUID lookup on the miss path only.
 */
@Mixin(value = AbstractKiProjectile.class, remap = false)
public abstract class KiGriefSourceMixin {

    @Inject(method = "getKiGriefingSource", at = @At("RETURN"), cancellable = true)
    private void xenopixels$cachedOwner(CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() instanceof Player) return;
        UUID id = ((KiProjectileGriefAccess) this).xenopixels$cachedOwnerUUID();
        if (id == null) return;
        AbstractKiProjectile self = (AbstractKiProjectile) (Object) this;
        if (!(self.level() instanceof ServerLevel level) || level.getServer() == null) return;
        Player player = level.getServer().getPlayerList().getPlayer(id);
        if (player != null) cir.setReturnValue(player);
    }
}
