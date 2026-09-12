package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.gui.character.SkillsMenuScreen;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadataRegistry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SkillsMenuScreen.class, remap = false)
public abstract class DmzSkillsFormTypeIconMixin {
    @Redirect(method = "renderFormsTree",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;fromNamespaceAndPath(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"),
            require = 1)
    private ResourceLocation xenopixels$formTypeIcon(String namespace, String path) {
        String prefix = "textures/gui/icons/";
        String suffix = ".png";
        if ("dragonminez".equals(namespace) && path.startsWith(prefix) && path.endsWith(suffix)) {
            String formType = path.substring(prefix.length(), path.length() - suffix.length());
            ResourceLocation icon = DmzFormMetadataRegistry.formTypeSkillIcon(formType);
            if (icon != null) return icon;
        }
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
