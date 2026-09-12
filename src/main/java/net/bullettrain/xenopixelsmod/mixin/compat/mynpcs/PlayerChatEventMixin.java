package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import espi.mynpcs.api.event.PlayerEvent;
import espi.mynpcs.controllers.data.PlayerScriptData;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Makes My NPCs' cancellable script chat event cancel the underlying NeoForge chat event. */
@Pseudo
@Mixin(targets = "espi.mynpcs.ScriptPlayerEventHandler", remap = false)
public abstract class PlayerChatEventMixin {

    @WrapOperation(
            method = "onServerChat(Lnet/neoforged/neoforge/event/ServerChatEvent;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lespi/mynpcs/EventHooks;onPlayerChat("
                            + "Lespi/mynpcs/controllers/data/PlayerScriptData;"
                            + "Lespi/mynpcs/api/event/PlayerEvent$ChatEvent;)V"
            ),
            require = 1
    )
    private void xenopixels$honorChatCancellation(PlayerScriptData scriptData,
                                                   PlayerEvent.ChatEvent scriptEvent,
                                                   Operation<Void> original,
                                                   ServerChatEvent serverEvent) {
        original.call(scriptData, scriptEvent);
        if (scriptEvent.isCanceled()) {
            serverEvent.setCanceled(true);
        }
    }
}
