package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.compat.customnpcs.role.RoleDmzSkillMaster;
import net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMaster;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes the XenoPixels skill-master sentinel into {@link RoleDmzSkillMaster} before CustomNPCs
 * normalizes the role id.
 *
 * <p>CustomNPCs' own {@code setRole(int)} pre-normalizes its argument:
 *
 * <pre>{@code if (id >= 8) id -= 2; id %= 8; }</pre>
 *
 * <p>so every input collapses into {@code 0..7} — exactly the seven native roles. No sentinel can
 * survive that arithmetic, and a mid-chain injection is therefore impossible. Injecting at
 * {@code HEAD}, matching the raw argument, and returning before the native body runs is the only
 * point where the sentinel is still distinguishable. {@code getType()} returns the same raw
 * sentinel so the value round-trips through {@code putInt("Role", ...)} unchanged.
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