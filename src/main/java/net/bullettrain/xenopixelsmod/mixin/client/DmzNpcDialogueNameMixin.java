package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.gui.dialogue.NpcDialogueScreen;
import net.bullettrain.xenopixelsmod.client.npc.dialog.DmzNpcDialoguePresentation;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only Xeno-owned presentations replace the native DMZ NPC name. */
@Mixin(value = NpcDialogueScreen.class, remap = false)
public abstract class DmzNpcDialogueNameMixin {
    @Inject(method = "npcName()Lnet/minecraft/network/chat/Component;", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$customName(CallbackInfoReturnable<Component> callback) {
        Component name = DmzNpcDialoguePresentation.name((NpcDialogueScreen)(Object)this);
        if (name != null) callback.setReturnValue(name);
    }
}
