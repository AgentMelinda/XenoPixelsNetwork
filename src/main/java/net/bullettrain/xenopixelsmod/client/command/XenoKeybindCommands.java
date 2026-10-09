package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.keybind.XenoKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * {@code /xenokeybind}: the client-side keybind system.
 *
 * <ul>
 *   <li>{@code status} - what a cleanup would unbind, the backups on disk, the auto setting</li>
 *   <li>{@code backup} - save every binding now</li>
 *   <li>{@code clean} - back up, then unbind every binding that is not Minecraft's, DragonMineZ's
 *       or XenoPixels'</li>
 *   <li>{@code restore [file]} - put a backup back; the newest when no file is named</li>
 *   <li>{@code preset xv2} - back up, then apply the Xenoverse-style layout</li>
 *   <li>{@code auto on|off} - whether the cleanup runs by itself the first time v2 is active</li>
 * </ul>
 *
 * <p>Separate from {@code /xenobind}, which switches individual direct inputs on and off.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoKeybindCommands {

    private XenoKeybindCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenokeybind")
                .executes(ctx -> status())
                .then(Commands.literal("status").executes(ctx -> status()))
                .then(Commands.literal("backup").executes(ctx -> backup()))
                .then(Commands.literal("clean").executes(ctx -> clean()))
                .then(Commands.literal("restore")
                        .executes(ctx -> restore(null))
                        .then(Commands.argument("file", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(XenoKeybinds.backups(), b))
                                .executes(ctx -> restore(StringArgumentType.getString(ctx, "file")))))
                .then(Commands.literal("preset")
                        .then(Commands.literal("xv2").executes(ctx -> preset())))
                .then(Commands.literal("auto")
                        .executes(ctx -> {
                            feedback("Automatic keybind cleanup is "
                                    + (XenoClientConfig.keybindAutoClean ? "§aon" : "§coff"));
                            return 1;
                        })
                        .then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(ctx -> auto(BoolArgumentType.getBool(ctx, "on"))))));
    }

    private static int status() {
        Minecraft mc = Minecraft.getInstance();
        Map<String, String> foreign = XenoKeybinds.foreign(mc);
        feedback("§aXeno keybinds§7: " + foreign.size() + " binding(s) from other mods are bound");
        int shown = 0;
        for (Map.Entry<String, String> e : foreign.entrySet()) {
            if (shown++ >= 12) {
                feedback("§7  ... and " + (foreign.size() - 12) + " more");
                break;
            }
            feedback("§7  " + Component.translatable(e.getKey()).getString() + " §8= §f" + e.getValue());
        }
        List<String> backups = XenoKeybinds.backups();
        feedback("§7Backups: " + backups.size()
                + (backups.isEmpty() ? "" : " (newest " + backups.get(0) + ")"));
        feedback("§7Automatic cleanup: " + (XenoClientConfig.keybindAutoClean ? "on" : "off")
                + (XenoClientConfig.keybindAutoCleanDone ? ", already ran" : ", has not run"));
        return foreign.size();
    }

    private static int backup() {
        try {
            feedback("§aSaved §f" + XenoKeybinds.backup(Minecraft.getInstance()).getFileName());
            return 1;
        } catch (IOException e) {
            feedback("§cCould not write a backup: " + e.getMessage());
            return 0;
        }
    }

    private static int clean() {
        try {
            XenoKeybinds.CleanResult result = XenoKeybinds.clean(Minecraft.getInstance());
            feedback("§aUnbound " + result.unbound() + " binding(s) from other mods§7; kept "
                    + result.kept() + ". Backup: §f" + result.backup().getFileName()
                    + "§7. Undo with §f/xenokeybind restore");
            return result.unbound();
        } catch (IOException e) {
            feedback("§cNothing was changed: the backup could not be written (" + e.getMessage() + ")");
            return 0;
        }
    }

    private static int restore(String file) {
        try {
            int restored = XenoKeybinds.restore(Minecraft.getInstance(), file);
            if (restored < 0) {
                feedback(file == null ? "§cThere is no backup to restore" : "§cNo backup named " + file);
                return 0;
            }
            feedback("§aRestored " + restored + " binding(s)");
            return restored;
        } catch (IOException e) {
            feedback("§cRestore failed: " + e.getMessage());
            return 0;
        }
    }

    private static int preset() {
        try {
            var backup = XenoKeybinds.applyXv2Preset(Minecraft.getInstance());
            feedback("§aApplied the XV2 layout§7. Backup: §f" + backup.getFileName()
                    + "§7. Undo with §f/xenokeybind restore");
            return 1;
        } catch (IOException e) {
            feedback("§cNothing was changed: the backup could not be written (" + e.getMessage() + ")");
            return 0;
        }
    }

    private static int auto(boolean on) {
        XenoClientConfig.keybindAutoClean = on;
        XenoClientConfig.save();
        feedback("Automatic keybind cleanup " + (on ? "§aon" : "§coff"));
        return 1;
    }

    private static void feedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
