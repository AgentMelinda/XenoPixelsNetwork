package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.common.diagnostics.JsonSchema;
import com.google.gson.JsonObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Hides Xeno-owned Oozaru tuning keys from DMZ's strict diagnostic copy only. */
@Mixin(value = JsonSchema.class, remap = false)
public abstract class DmzJsonSchemaXenoFieldsMixin {
    @ModifyVariable(method = "check", at = @At("HEAD"), argsOnly = true)
    private static JsonObject xenopixels$stripExtensionFields(JsonObject original) {
        if (original == null) {
            return null;
        }
        JsonObject copy = original.deepCopy();
        JsonObject forms = copy.getAsJsonObject("forms");
        JsonObject oozaru = forms == null ? null : forms.getAsJsonObject("oozaru");
        if (oozaru != null) {
            oozaru.remove("xenopixelsNpcDisplaySize");
            oozaru.remove("xenopixelsNpcAuraScale");
        }
        return copy;
    }
}
