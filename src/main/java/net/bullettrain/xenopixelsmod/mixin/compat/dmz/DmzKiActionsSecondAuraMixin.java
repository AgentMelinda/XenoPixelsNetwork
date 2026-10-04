package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.gui.radial.RadialNode;
import com.dragonminez.client.gui.radial.nodes.KiActionsNode;
import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.client.aura.SecondAuraNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Appends the HD aura toggle to DragonMineZ's Ki Actions ({@code KiActionsNode#buildChildren} in
 * dragonminez-2.1.3 returns a fresh ArrayList). DMZ's own four entries are left as they are.
 */
@Mixin(value = KiActionsNode.class, remap = false)
public abstract class DmzKiActionsSecondAuraMixin {

    @Inject(method = "buildChildren", at = @At("RETURN"))
    private void xenopixels$secondAura(StatsData stats, CallbackInfoReturnable<List<RadialNode>> cir) {
        List<RadialNode> children = cir.getReturnValue();
        if (children == null) return;
        try {
            children.add(new SecondAuraNode());
            children.add(new net.bullettrain.xenopixelsmod.client.aura.AuraVariantNode());
        } catch (UnsupportedOperationException ignored) {
            // An immutable list from a different DragonMineZ build: the command remains.
        }
    }
}
