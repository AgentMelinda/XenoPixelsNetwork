package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay;
import net.neoforged.neoforge.event.ServerChatEvent;
import noppes.npcs.api.event.PlayerEvent;
import noppes.npcs.controllers.data.PlayerScriptData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * CustomNPCs {@code invoke(ServerChatEvent)} seeds {@code ChatEvent.message} from
 * {@code getMessage().getString()} and writes a rewrite back as
 * {@code Component.translatable("").append(...)}. Clients drop that empty-key
 * signed content on 1.21.1.
 *
 * <p>These injects seed the typed {@code getRawText()}, replace a rewrite by
 * cancelling the signed line and scheduling {@code chat.type.text}, and restore
 * {@code scriptEvent.message} so CustomNPCs' equals check skips its write-back.
 * Cancel is already copied onto {@code ServerChatEvent}; {@code cancelChat}
 * still marks the ThreadLocal for the LOWEST fallback.
 */
@Pseudo
@Mixin(targets = "noppes.npcs.ScriptPlayerEventHandler", remap = false)
public abstract class PlayerChatEventMixin {
    @Unique
    private static boolean xenopixels$appliedLogged;

    @Inject(
            method = "invoke(Lnet/minecraftforge/event/ServerChatEvent;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnoppes/npcs/EventHooks;onPlayerChat("
                            + "Lnoppes/npcs/controllers/data/PlayerScriptData;"
                            + "Lnoppes/npcs/api/event/PlayerEvent$ChatEvent;)V"
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
            method = "invoke(Lnet/minecraftforge/event/ServerChatEvent;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnoppes/npcs/EventHooks;onPlayerChat("
                            + "Lnoppes/npcs/controllers/data/PlayerScriptData;"
                            + "Lnoppes/npcs/api/event/PlayerEvent$ChatEvent;)V",
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 1
    )
    private void xenopixels$honorChat(ServerChatEvent serverEvent, CallbackInfo ci,
                                      PlayerScriptData scriptData, String original,
                                      PlayerEvent.ChatEvent scriptEvent) {
        String raw = NpcScriptSay.typedLine(serverEvent.getRawText(), original);
        if (NpcScriptSay.isScriptChatCanceled(scriptEvent)) {
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
        XenoPixelsMod.LOGGER.info("CustomNpcsPlayerChatEventMixin applied");
    }
}
