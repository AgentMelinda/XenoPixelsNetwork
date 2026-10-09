package net.bullettrain.xenopixelsmod.mixin.common;

import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import net.bullettrain.xenopixelsmod.combat.v3.ki.V3NativeKi;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Preserve native V3 impact rendering without affecting native or other addons' impacts. */
@Mixin(value = KiBlastEntity.class, remap = false)
public abstract class KiBlastV3VisualOwnerMixin {
    @ModifyArg(method = {"explodeAndDie", "destroyBlocksInPath"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            remap = true), index = 0, require = 3)
    private Entity xenopixels$markV3ImpactOwner(Entity visual) {
        V3NativeKi.markVisual(((KiBlastEntity)(Object)this).getTechniqueId(), visual);
        return visual;
    }
}
