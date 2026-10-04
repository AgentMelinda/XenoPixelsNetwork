package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import espi.mynpcs.api.event.PlayerEvent;
import espi.mynpcs.controllers.data.PlayerScriptData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * My NPCs' {@code onServerChat} seeds {@code ChatEvent.message} from
 * {@code ServerChatEvent.getMessage().getString()} and writes a rewrite back as
 * {@code Component.translatable("").append(...)}. It never copies
 * {@code ChatEvent} cancel onto the Forge event. Both hide or keep the signed
 * line on 1.21.1.
 *
 * <p>These injects seed the typed {@code getRawText()}, copy cancel, and replace a
 * rewrite by cancelling the signed line and scheduling a system
 * {@code chat.type.text}. {@code scriptEvent.message} is restored so My NPCs'
 * equals check skips its empty-key write-back.
 */
@Pseudo
@Mixin(targets = "espi.mynpcs.ScriptPlayerEventHandler", remap = false)
public abstract class PlayerChatEventMixin {
    @Unique
    private static boolean xenopixels$appliedLogged;

    @Inject(
            method = "onServerChat(Lnet/neoforged/neoforge/event/ServerChatEvent;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lespi/mynpcs/EventHooks;onPlayerChat("
                            + "Lespi/mynpcs/controllers/data/PlayerScriptData;"
                            + "Lespi/mynpcs/api/event/PlayerEvent$ChatEvent;)V"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 1
    )
    private void xenopixels$seedRawChat(ServerChatEvent serverEvent, CallbackInfo ci,
                                        PlayerScriptData scriptData, String original,
                                        PlayerEvent.ChatEvent scriptEvent) {
        xenopixels$logAppliedOnce();
        scriptEvent.message = NpcScriptSay.typedLine(serverEvent.getRawText(), original);
    }

    @Inject(
            method = "onServerChat(Lnet/neoforged/neoforge/event/ServerChatEvent;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lespi/mynpcs/EventHooks;onPlayerChat("
                            + "Lespi/mynpcs/controllers/data/PlayerScriptData;"
                            + "Lespi/mynpcs/api/event/PlayerEvent$ChatEvent;)V",
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 1
    )
    private void xenopixels$honorChat(ServerChatEvent serverEvent, CallbackInfo ci,
                                      PlayerScriptData scriptData, String original,
                                      PlayerEvent.ChatEvent scriptEvent) {
        String raw = NpcScriptSay.typedLine(serverEvent.getRawText(), original);
        if (scriptEvent.isCanceled()) {
            serverEvent.setCanceled(true);
        } else if (NpcScriptSay.shouldRewrite(raw, scriptEvent.message)
                && NpcScriptSay.broadcastLater(serverEvent.getPlayer(), scriptEvent.message)) {
            serverEvent.setCanceled(true);
        }
        scriptEvent.message = original == null ? "" : original;
    }

    @Unique
    private static void xenopixels$logAppliedOnce() {
        if (xenopixels$appliedLogged) {
            return;
        }
        xenopixels$appliedLogged = true;
        XenoPixelsMod.LOGGER.info("PlayerChatEventMixin applied");
    }
}
