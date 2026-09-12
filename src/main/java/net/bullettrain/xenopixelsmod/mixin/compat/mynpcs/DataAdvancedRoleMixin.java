package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.entity.data.DataAdvanced;
import net.bullettrain.xenopixelsmod.compat.mynpcs.role.RoleDmzSkillMaster;
import net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMaster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * My NPCs twin of {@code mixin.compat.customnpcs.DataAdvancedRoleMixin}.
 *
 * <p>My NPCs does not normalize the role id, so the native chain could in principle be reached
 * after the sentinel, but the injection is still {@code HEAD} and still cancels, keeping both
 * trees identical and preventing the native if-chain's fall-through from ever running on the
 * sentinel.
 */
@Mixin(value = DataAdvanced.class, remap = false)
public abstract class DataAdvancedRoleMixin {
    @Shadow
    private EntityNPCInterface npc;

    @Inject(method = "setRole", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$setDmzSkillMasterRole(int roleId, CallbackInfo ci) {
        if (!DmzSkillMaster.isSentinel(roleId) || npc == null) {
            return;
        }
        npc.role = new RoleDmzSkillMaster(npc);
        ci.cancel();
    }
}