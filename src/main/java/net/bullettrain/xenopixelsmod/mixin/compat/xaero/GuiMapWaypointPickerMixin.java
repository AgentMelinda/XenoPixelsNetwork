package net.bullettrain.xenopixelsmod.mixin.compat.xaero;

import net.bullettrain.xenopixelsmod.compat.xaero.XaeroWaypointPicker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.element.HoveredMapElementHolder;
import xaero.map.mods.SupportMods;
import xaero.map.mods.gui.Waypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Selects real Xaero-managed waypoints from the World Map icon or manager list. */
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class GuiMapWaypointPickerMixin {
    @Shadow(remap = false) private ResourceKey<Level> mouseBlockDim;
    @Shadow(remap = false) private HoveredMapElementHolder<?, ?> viewed;
    @Shadow(remap = false) public boolean waypointMenu;

    @Inject(method = "init()V", at = @At("TAIL"), remap = false)
    private void xenopixels$openWaypointManager(CallbackInfo ci) {
        if (XaeroWaypointPicker.isPicking() && SupportMods.minimap()) {
            waypointMenu = true;
        }
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void xenopixels$selectManagedWaypoint(double mouseX, double mouseY, int button,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !XaeroWaypointPicker.isPicking() || viewed == null) return;
        Object element = viewed.getElement();
        if (!(element instanceof Waypoint waypoint)) return;
        XaeroWaypointPicker.accept(waypoint, mouseBlockDim);
        cir.setReturnValue(true);
    }

    @Inject(method = "removed()V", at = @At("TAIL"), remap = false)
    private void xenopixels$cancelWaypointSelection(CallbackInfo ci) {
        if (XaeroWaypointPicker.isPicking()) {
            XaeroWaypointPicker.cancel();
        }
    }
}
