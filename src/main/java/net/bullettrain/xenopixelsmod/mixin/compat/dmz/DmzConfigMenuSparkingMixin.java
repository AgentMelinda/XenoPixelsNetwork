package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.function.Consumer;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends a Sparking toggle to DragonMineZ's player options list.
 *
 * <p>{@code ConfigOption} is package-private, so the row is constructed reflectively against the
 * pinned 2.1.3 inner class rather than by naming it. The value is the local
 * {@link XenoClientConfig#sparkingEnabled} opt-out; server Sparking still has to be enabled.
 */
@Mixin(targets = "com.dragonminez.client.gui.character.ConfigMenuScreen", remap = false)
public abstract class DmzConfigMenuSparkingMixin {

    @Shadow
    @Final
    @SuppressWarnings("rawtypes")
    private List configOptions;

    @Inject(method = "initializeConfigOptions", at = @At("RETURN"), require = 1)
    private void xenopixels$addSparkingToggle(CallbackInfo ci) {
        if (configOptions == null) return;
        Object option = xenopixels$sparkingOption();
        if (option != null) configOptions.add(option);
    }

    @Unique
    private static Object xenopixels$sparkingOption() {
        try {
            Class<?> optionClass = Class.forName(
                    "com.dragonminez.client.gui.character.ConfigMenuScreen$ConfigOption");
            Class<?> typeClass = Class.forName(
                    "com.dragonminez.client.gui.character.ConfigMenuScreen$ConfigType");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object booleanType = Enum.valueOf((Class) typeClass, "BOOLEAN");
            Constructor<?> ctor = optionClass.getDeclaredConstructor(
                    String.class, typeClass, float.class, float.class, float.class, Consumer.class);
            ctor.setAccessible(true);
            float value = XenoClientConfig.sparkingEnabled ? 1.0F : 0.0F;
            Consumer<Float> setter = v -> {
                XenoClientConfig.sparkingEnabled = v != null && v > 0.0F;
                XenoClientConfig.save();
            };
            return ctor.newInstance("config.xenopixels.sparking", booleanType,
                    value, 0.0F, 1.0F, setter);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
