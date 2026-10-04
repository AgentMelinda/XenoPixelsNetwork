package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.npc.importer.NpcImportReport;
import net.bullettrain.xenopixelsmod.npc.importer.NpcImportService;
import net.bullettrain.xenopixelsmod.npc.importer.PlacedNpcImport;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.SyncFactionsPacket;
import net.bullettrain.xenopixelsmod.network.packet.SyncNpcStoreIndexPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;

/**
 * {@code /xenonpcimport} — read My NPCs / CustomNPCs content off disk into the Xeno store.
 *
 * <p><b>Dry run is the default.</b> {@code /xenonpcimport all} previews content files and currently
 * loaded placed NPCs; {@code /xenonpcimport all confirm} imports them. A migration that has
 * overwritten the destination before the operator has seen its plan is a migration with no undo,
 * and this is the one command in the mod where that matters most.
 *
 * <p>Operator-gated at level 2 like the rest of the NPC administration surface.
 *
 * <p>The command does not run at startup. Unloaded entity chunks are not touched by this live
 * command; they are named in its report instead of being silently claimed as converted.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcImportCommands {

    /** Enough detail to act on without flooding chat; the full list goes to the log. */
    private static final int MAX_CHAT_LINES = 12;

    private NpcImportCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenonpcimport")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("factions")
                        .executes(context -> run(context.getSource(), true, false))
                        .then(Commands.literal("confirm")
                                .executes(context -> run(context.getSource(), false, false))))
                .then(Commands.literal("all")
                        .executes(context -> run(context.getSource(), true, true))
                        .then(Commands.literal("confirm")
                                .executes(context -> run(context.getSource(), false, true)))));
    }

    private static int run(CommandSourceStack source, boolean dryRun, boolean all) {
        MinecraftServer server = source.getServer();
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            source.sendFailure(Component.literal("The NPC store is not open."));
            return 0;
        }

        // The world folder holds their authored content; the game directory holds clones. Two
        // roots, not one - a single-root assumption finds the content and misses every clone.
        Path worldPath = server.getWorldPath(new LevelResource("."));
        Path gameDir = server.getServerDirectory();

        NpcImportReport report = all
                ? NpcImportService.importAll(worldPath, gameDir, store, dryRun)
                : NpcImportService.importFactions(worldPath, gameDir, store, dryRun);
        if (all) PlacedNpcImport.convertLoaded(server, worldPath, dryRun, report);

        if (!dryRun && report.imported() > 0) {
            ModNetwork.sendToAll(SyncFactionsPacket.current());
            ModNetwork.sendToAll(SyncNpcStoreIndexPacket.current());
        }

        source.sendSuccess(() -> Component.literal(
                (dryRun ? "§eDry run: §f" : "§aImported: §f") + report.summary()), true);

        int shown = 0;
        for (String note : report.notes()) {
            if (shown++ >= MAX_CHAT_LINES) {
                break;
            }
            source.sendSuccess(() -> Component.literal("§7  " + note), false);
        }
        for (NpcImportReport.Failure failure : report.failures()) {
            if (shown++ >= MAX_CHAT_LINES) {
                break;
            }
            source.sendSuccess(() -> Component.literal("§c  " + failure), false);
        }
        int remaining = report.notes().size() + report.failures().size() - shown;
        if (remaining > 0) {
            source.sendSuccess(() -> Component.literal(
                    "§7  ... and " + remaining + " more; see the server log"), false);
        }

        // The whole report goes to the log whatever chat showed, so nothing is lost to a scroll.
        for (String note : report.notes()) {
            XenoPixelsMod.LOGGER.info("NPC import note: {}", note);
        }
        for (NpcImportReport.Failure failure : report.failures()) {
            XenoPixelsMod.LOGGER.warn("NPC import failure: {}", failure);
        }

        if (dryRun && report.imported() > 0) {
            source.sendSuccess(() -> Component.literal(
                    "§7Run §f/xenonpcimport " + (all ? "all" : "factions")
                            + " confirm§7 to write these."), false);
        }
        return report.imported();
    }
}
