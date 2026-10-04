package net.bullettrain.xenopixelsmod.anim;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi;
import net.bullettrain.xenopixelsmod.compat.npc.NpcScriptSay;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.List;

/**
 * Native {@code !cliplist} / {@code !clip} / {@code !clipstop} / {@code !cliphelp}.
 * Does not use My NPCs player scripts. Cancels the signed {@code ServerChatEvent}
 * so 1.21.1 clients do not keep the typed {@code !} line.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoClipChatEvents {
    private XenoClipChatEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerChat(ServerChatEvent event) {
        String typed = XenoClipChat.typedLine(
                event.getRawText(),
                event.getMessage() == null ? "" : event.getMessage().getString());
        if (!XenoClipChat.isHandled(typed)) {
            return;
        }
        event.setCanceled(true);
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server != null && !server.isSameThread()) {
            server.execute(() -> handle(player, typed));
        } else {
            handle(player, typed);
        }
        XenoPixelsMod.LOGGER.info("Xeno clip chat handled: {} player={}",
                typed, player.getScoreboardName());
    }

    static void handle(ServerPlayer player, String typed) {
        String cmd = XenoClipChat.command(typed);
        switch (cmd) {
            case "cliphelp" -> tellHelp(player);
            case "clipstop" -> {
                if (XenoAnimApi.stopClip(player)) {
                    NpcScriptSay.tell(player, "Stopped.");
                } else {
                    NpcScriptSay.tell(player, "This player cannot show DragonMineZ clips.");
                }
            }
            case "cliplist" -> tellList(player);
            case "clip" -> play(player, XenoClipChat.parts(typed));
            default -> {
            }
        }
    }

    private static void tellHelp(ServerPlayer player) {
        NpcScriptSay.tell(player, "!clip <name> [speed] [ticks] [hold]  play a published studio clip");
        NpcScriptSay.tell(player, "!clipstop  return to idle");
        NpcScriptSay.tell(player, "!cliplist  names this server can play");
    }

    private static void tellList(ServerPlayer player) {
        List<String> library = XenoAnimApi.listLibraryClips();
        if (!library.isEmpty()) {
            for (String line : XenoClipChat.chunkNames("Library: ", library)) {
                NpcScriptSay.tell(player, line);
            }
            return;
        }
        List<String> all = XenoAnimApi.listClips();
        if (!all.isEmpty()) {
            for (String line : XenoClipChat.chunkNames("Clips: ", all)) {
                NpcScriptSay.tell(player, line);
            }
            return;
        }
        NpcScriptSay.tell(player, "No clips. Publish studio files with /xenoanim global push");
    }

    private static void play(ServerPlayer player, String[] parts) {
        if (parts.length < 2) {
            NpcScriptSay.tell(player, "Usage: !clip <name> [speed] [ticks] [hold]");
            return;
        }
        String name = parts[1];
        if (!XenoAnimApi.isClipAvailable(name)) {
            NpcScriptSay.tell(player, "Unknown clip " + name + ". Publish it with /xenoanim global push");
            return;
        }
        if (!XenoAnimApi.canPlay(player)) {
            NpcScriptSay.tell(player, "This player cannot show DragonMineZ clips.");
            return;
        }
        float speed = 1.0f;
        if (parts.length > 2) {
            try {
                speed = Float.parseFloat(parts[2]);
            } catch (NumberFormatException ignored) {
                speed = 1.0f;
            }
        }
        int ticks = 0;
        if (parts.length > 3) {
            try {
                ticks = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {
                ticks = 0;
            }
        }
        boolean hold = parts.length > 4 && "hold".equalsIgnoreCase(parts[4]);
        if (XenoAnimApi.playClip(player, name, speed, ticks, hold)) {
            int length = XenoAnimApi.clipDuration(name);
            NpcScriptSay.tell(player, "Playing " + name
                    + (hold ? " (hold)" : "")
                    + (ticks > 0 ? " " + ticks + "t" : (length > 0 ? " " + length + "t" : "")));
        } else {
            NpcScriptSay.tell(player, "Could not play " + name);
        }
    }
}
