package net.bullettrain.xenopixelsmod.mixin.compat.yawp;

import com.mojang.brigadier.context.CommandContext;
import de.z0rdak.yawp.commands.arguments.flag.IFlagArgumentType;
import de.z0rdak.yawp.commands.arguments.region.RegionArgumentType;
import de.z0rdak.yawp.core.flag.IFlag;
import de.z0rdak.yawp.core.region.IProtectedRegion;
import de.z0rdak.yawp.core.region.RegionType;
import net.bullettrain.xenopixelsmod.compat.yawp.XenoKiFlag;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Resolves our ki-griefing flags without touching YAWP's region storage.
 *
 * <p>YAWP's own {@code getFlag} looks the name up in the region's {@code Map<String, IFlag>}
 * and, when absent, tells the operator to "add" it first — which would insert our flag into
 * YAWP's data and hand it to YAWP's serializer. Intercepting at HEAD returns a transient
 * {@link XenoKiFlag} instead, so our flags are readable and settable through YAWP's commands
 * while living entirely in our own storage.
 *
 * <p>This is the safety boundary for the whole integration: YAWP never persists a flag it
 * cannot parse back, so its region files stay valid even if this mod is removed.
 */
@Mixin(value = IFlagArgumentType.class, remap = false)
public class IFlagArgumentTypeMixin {

    @Inject(method = "getFlag", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void xeno$resolveKiFlag(CommandContext<CommandSourceStack> context, String argName,
                                           CallbackInfoReturnable<IFlag> cir) {
        try {
            String identifier = context.getArgument(argName, String.class);
            if (!XenoKiFlag.isOurs(identifier)) return;

            // Resolve the region through YAWP's own argument type, exactly as the method we are
            // injecting into does two instructions later. Guessing the region argument's name
            // (this previously tried "local", "region", "name") silently produced an empty
            // region for every command shape that does not use one of those keys, and an empty
            // region key reads and writes a storage slot no lookup will ever consult — so the
            // flag appeared to set and then did nothing.
            RegionType regionType = RegionArgumentType.getRegionType(context);
            if (regionType == null) return;
            IProtectedRegion region = RegionArgumentType.getRegion(context, regionType);
            if (region == null) return;

            String name = region.getName();
            if (name == null || name.isBlank()) return;
            // The region's own dimension, not the executor's. `/wp local <dim> <region> …` can
            // address a region in a dimension the operator is not standing in, and keying on
            // the command source's level put those flags under the wrong dimension entirely.
            String dimension = region.getDim().location().toString();

            cir.setReturnValue(new XenoKiFlag(identifier, dimension, name));
        } catch (Throwable ignored) {
            // Fall through to YAWP's own resolution rather than breaking the command.
        }
    }
}
