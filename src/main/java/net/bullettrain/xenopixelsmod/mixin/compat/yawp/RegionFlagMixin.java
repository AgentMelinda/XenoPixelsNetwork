package net.bullettrain.xenopixelsmod.mixin.compat.yawp;

import de.z0rdak.yawp.core.flag.RegionFlag;
import net.bullettrain.xenopixelsmod.compat.yawp.XenoKiFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes YAWP's command layer aware of our ki-griefing flag names.
 *
 * <p>YAWP stores valid flags as an enum, which third-party mods cannot extend — but only two
 * <i>static</i> methods on that enum actually gate the command path:
 * {@code getFlagNames()} feeds suggestions and {@code contains()} validates parsing. Widening
 * those two is enough for our names to appear and be accepted under {@code /yawp flag …}.
 *
 * <p>Nothing here makes YAWP <i>store</i> our flags; {@link IFlagArgumentTypeMixin} diverts
 * them to our own storage before that can happen. YAWP's own region data is never written to,
 * so its save files cannot end up holding a flag its deserializer does not understand.
 *
 * <p>{@code require = 0} throughout: if a YAWP update reshapes these, the flags simply stop
 * appearing under {@code /yawp} and {@code /kiflag} continues to work.
 */
@Mixin(value = RegionFlag.class, remap = false)
public class RegionFlagMixin {

    @Inject(method = "getFlagNames", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void xeno$addKiFlagNames(CallbackInfoReturnable<List<String>> cir) {
        List<String> names = cir.getReturnValue();
        if (names == null) return;
        List<String> combined = new ArrayList<>(names);
        for (String name : XenoKiFlag.NAMES) {
            if (!combined.contains(name)) combined.add(name);
        }
        cir.setReturnValue(combined);
    }

    @Inject(method = "contains", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void xeno$acceptKiFlags(String flagIdentifier, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && XenoKiFlag.isOurs(flagIdentifier)) {
            cir.setReturnValue(true);
        }
    }
}
