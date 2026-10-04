package net.bullettrain.xenopixelsmod.client.anim;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.anim.XenoClipChat;
import net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;

import java.util.List;

/**
 * Intercepts {@code !cliplist} / {@code !cliphelp} on the client before the signed
 * chat packet is sent. Verified NeoForge 21.1.248 {@code ClientChatEvent}: cancel
 * means the line is not submitted. That avoids the 1.21.1 signed-chat drop that
 * made the server-only hook look dead in runClient.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoClipChatClient {
    private XenoClipChatClient() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientChat(ClientChatEvent event) {
        String typed = event.getMessage() == null ? "" : event.getMessage().trim();
        String cmd = XenoClipChat.command(typed);
        if (!"cliplist".equals(cmd) && !"cliphelp".equals(cmd)) {
            return;
        }
        event.setCanceled(true);
        Minecraft.getInstance().execute(() -> handle(cmd));
        XenoPixelsMod.LOGGER.info("Xeno clip chat client handled: {}", typed);
    }

    public static int showList() {
        List<String> library = XenoClipLibraryClient.names();
        if (!library.isEmpty()) {
            return sayChunks("Library: ", library);
        }
        List<String> all = XenoAnimApi.listClips();
        if (!all.isEmpty()) {
            return sayChunks("Clips: ", all);
        }
        say("No clips. Publish studio files with /xenoanim global push");
        return 1;
    }

    public static int showHelp() {
        say("!clip <name> [speed] [ticks] [hold]  play a published studio clip");
        say("!clipstop  return to idle");
        say("!cliplist  names this server can play");
        say("Or type /cliplist or /xenoanim cliplist");
        return 1;
    }

    private static void handle(String cmd) {
        if ("cliphelp".equals(cmd)) {
            showHelp();
            return;
        }
        showList();
    }

    private static int sayChunks(String prefix, List<String> names) {
        int count = 0;
        for (String line : XenoClipChat.chunkNames(prefix, names)) {
            say(line);
            count++;
        }
        return Math.max(1, count);
    }

    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) {
            return;
        }
        mc.gui.getChat().addMessage(Component.literal(text));
    }
}
