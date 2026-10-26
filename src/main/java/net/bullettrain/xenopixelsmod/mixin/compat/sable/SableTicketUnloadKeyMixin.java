package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.SubLevelTicketsSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sable 2.0.3 SubLevelTicketLoadingSystem.onSubLevelRemoved, UNLOADED branch, looks up the UUID-keyed allTickets map
 * with the ServerSubLevel object itself, so the lookup always misses and the ticket's pointer is never refreshed on
 * unload (javap: offsets 30-39; SubLevel/ServerSubLevel do not override equals/hashCode). The REMOVED branch already
 * uses getUniqueId() for its own map call (remove, not get), so only the single get(Object) call in this method is
 * rewritten. Effect when enabled: the ticket takes sub.getLastSerializationPointer() as Sable intended, and its
 * SavedData is marked dirty so the refreshed pointer is eligible for the next save. That is the body's last saved
 * slot, which a later saveAll may still move; this patch does not chase that later move.
 * Switch: XenoSpaceConfig.sableTicketUnloadKeyFix.
 */
@Mixin(targets = "dev.ryanhcode.sable.api.sublevel.SubLevelTicketLoadingSystem", remap = false)
public abstract class SableTicketUnloadKeyMixin {
    @Shadow @Final private ServerSubLevelContainer container;

    @ModifyArg(method = "onSubLevelRemoved", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            index = 0, require = 1)
    private Object xenopixels$useUuidKey(Object key) {
        if (XenoSpaceConfig.sableTicketUnloadKeyFix() && key instanceof SubLevel sub) return sub.getUniqueId();
        return key;
    }

    @Inject(method = "onSubLevelRemoved", at = @At("TAIL"), require = 1)
    private void xenopixels$persistRefreshedUnloadPointer(SubLevel subLevel, SubLevelRemovalReason reason,
                                                           CallbackInfo ci) {
        if (XenoSpaceConfig.sableTicketUnloadKeyFix() && reason == SubLevelRemovalReason.UNLOADED)
            SubLevelTicketsSavedData.getOrLoad(container.getLevel()).setDirty();
    }
}
