package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.gui.radial.nodes.FormSelectNode;
import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadataRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Serves Form Studio names and per-form icons to DragonMineZ's radial form selector.
 *
 * <p>Both injections are {@code require = 1} even though this config sets
 * {@code defaultRequire: 0}: the targets were verified against dragonminez-2.1.3
 * ({@code FormSelectNode#label(StatsData)} and {@code #icon(StatsData)}), and without the
 * requirement a future DragonMineZ rename would silently drop custom names and icons instead of
 * failing where someone can see it. The whole config is still gated on the dragonminez mod id by
 * {@code ConditionalMixinPlugin}, so this cannot fire when DragonMineZ is absent.
 */
@Mixin(value = FormSelectNode.class, remap = false)
public abstract class DmzFormSelectMetadataMixin {
    @Shadow @Final private String race;
    @Shadow @Final private String group;
    @Shadow @Final private String form;
    @Shadow @Final private boolean stack;

    @Inject(method = "label", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$localizedLabel(StatsData stats, CallbackInfoReturnable<Component> cir) {
        String locale = Minecraft.getInstance().getLanguageManager().getSelected();
        String name = DmzFormMetadataRegistry.displayName(
                stack ? DmzFormKind.STACK : DmzFormKind.NORMAL, race, group, form, locale);
        if (!name.isBlank()) cir.setReturnValue(Component.literal(name));
    }

    @Inject(method = "icon", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$perFormIcon(StatsData stats, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation icon = DmzFormMetadataRegistry.formIcon(
                stack ? DmzFormKind.STACK : DmzFormKind.NORMAL, race, group, form);
        if (icon != null) cir.setReturnValue(icon);
    }
}
