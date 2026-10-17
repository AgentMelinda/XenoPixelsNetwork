package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Sable 2.0.3 SubLevelTicketLoadingSystem.onSubLevelRemoved, UNLOADED branch, looks up the UUID-keyed allTickets map
 * with the ServerSubLevel object itself, so the lookup always misses and the ticket's pointer is never refreshed on
 * unload (javap: offsets 30-39; SubLevel/ServerSubLevel do not override equals/hashCode). The REMOVED branch already
 * uses getUniqueId() for its own map call (remove, not get), so only the single get(Object) call in this method is
 * rewritten. Effect when enabled: the ticket takes sub.getLastSerializationPointer() as Sable intended. That is the
 * body's last saved slot, which a later saveAll may still move; this patch does not chase that later move.
 * Switch: XenoSpaceConfig.sableTicketUnloadKeyFix (off until proven at runtime).
 */
@Mixin(targets = "dev.ryanhcode.sable.api.sublevel.SubLevelTicketLoadingSystem", remap = false)
public abstract class SableTicketUnloadKeyMixin {
    @ModifyArg(method = "onSubLevelRemoved", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            index = 0, require = 1)
    private Object xenopixels$useUuidKey(Object key) {
        if (XenoSpaceConfig.sableTicketUnloadKeyFix() && key instanceof SubLevel sub) return sub.getUniqueId();
        return key;
    }
}
