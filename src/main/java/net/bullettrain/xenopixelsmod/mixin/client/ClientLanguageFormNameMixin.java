package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadataRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Serves player-facing names for forms and form groups created in the XenoPixels DMZ Form Studio.
 *
 * <p>DragonMineZ resolves every form label through {@code Component.translatable} on the
 * {@code race.dragonminez.…} key space (verified in dragonminez-2.1.3 across
 * {@code FormSelectNode}, {@code CharacterStatsScreen}, {@code SkillsMenuScreen},
 * {@code QuestTreeScreen}, {@code CharacterCustomizationScreen} and {@code ClientStatsEvents}),
 * so answering the language lookup covers every one of those screens without patching each of
 * them. The metadata arrives from the server, which is why no resource pack is needed.
 *
 * <p>The injection returns immediately for every key this mod does not own:
 * {@link DmzFormMetadataRegistry#translate} first rejects anything outside the
 * {@code race.dragonminez.} prefix and then needs an exact hit in the configured key index.
 */
@Mixin(ClientLanguage.class)
public abstract class ClientLanguageFormNameMixin {
    @Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$formName(String key, String fallback, CallbackInfoReturnable<String> cir) {
        String name = DmzFormMetadataRegistry.translate(key, xenopixels$locale());
        if (name != null) cir.setReturnValue(name);
    }

    @Inject(method = "has(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$hasFormName(String key, CallbackInfoReturnable<Boolean> cir) {
        if (DmzFormMetadataRegistry.translate(key, xenopixels$locale()) != null) {
            cir.setReturnValue(true);
        }
    }

    private static String xenopixels$locale() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.getLanguageManager() == null
                ? "en_us" : minecraft.getLanguageManager().getSelected();
    }
}
