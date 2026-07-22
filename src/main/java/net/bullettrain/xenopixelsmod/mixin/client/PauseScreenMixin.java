package net.bullettrain.xenopixelsmod.mixin.client;

import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.screen.XenoMenuScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addXenoButton(CallbackInfo ci) {
        if (!XenoClientConfig.pauseScreenButton || !XenoClientConfig.xenoMenuEnabled) return;
        this.addRenderableWidget(Button.builder(Component.literal("§bXenoPixels"), b ->
                        this.minecraft.setScreen(new XenoMenuScreen(this)))
                .bounds(this.width / 2 - 100, this.height / 4 + 144, 200, 20)
                .build());
    }
}
