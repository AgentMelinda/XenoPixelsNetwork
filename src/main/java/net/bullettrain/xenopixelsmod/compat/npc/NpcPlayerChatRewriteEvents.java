package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

/**
 * Safety net for player-chat rewrite when the onServerChat / invoke mixin does not apply.
 *
 * <p>My NPCs and CustomNPCs write a changed line back as {@code Component.translatable("")}.
 * NeoForge 21.1.248 then attaches that as unsigned signed-chat content, which clients drop,
 * so the typed {@code #hello} stays on screen. Canceling the official {@code ServerChatEvent}
 * makes the decorator return null (no signed broadcast); the replacement is a system
 * {@code chat.type.text} on the server thread.
 *
 * <p>Does not reference NPC-mod types. No-ops unless {@code mynpcs} or {@code customnpcs}
 * is loaded.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcPlayerChatRewriteEvents {
    private NpcPlayerChatRewriteEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stashTypedLine(ServerChatEvent event) {
        String decorated = event.getMessage() == null ? "" : event.getMessage().getString();
        NpcScriptSay.markTypedLine(NpcScriptSay.typedLine(event.getRawText(), decorated));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterMyNpcsChat(ServerChatEvent event) {
        try {
            if (!ModList.get().isLoaded("mynpcs") && !ModList.get().isLoaded("customnpcs")) {
                return;
            }
            if (NpcScriptSay.consumeChatCancelled()) {
                event.setCanceled(true);
                return;
            }
            if (event.isCanceled()) {
                return;
            }
            Component message = event.getMessage();
            if (!NpcScriptSay.isEmptyKeyWriteback(message)) {
                return;
            }
            String rewritten = message.getString();
            event.setCanceled(true);
            NpcScriptSay.broadcastLater(event.getPlayer(), rewritten);
            XenoPixelsMod.LOGGER.info(
                    "NPC empty-key chat write-back rewritten: raw={} rewritten={} canceled=true",
                    event.getRawText(), rewritten);
        } finally {
            NpcScriptSay.clearTypedLine();
        }
    }
}
