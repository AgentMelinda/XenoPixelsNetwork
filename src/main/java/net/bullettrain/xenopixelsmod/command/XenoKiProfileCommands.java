package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.combat.v3.ki.V3NativeKi;
import net.bullettrain.xenopixelsmod.combat.v3.ki.XenoKiProfile;
import net.bullettrain.xenopixelsmod.combat.v3.ki.XenoKiProfileCatalog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /xenokiprofile reload|status|get <techniqueId>} — Xeno Ki presentation profiles
 * ({@code config/xenopixelsmod/ki_profiles.json}). Scratch-owned; not dmzrevamp.
 *
 * <p>Singleplayer / integrated server: no OP. Dedicated / remote multiplayer: OP level 2.
 */
@EventBusSubscriber(modid = net.bullettrain.xenopixelsmod.XenoPixelsMod.MOD_ID)
public final class XenoKiProfileCommands {
    private XenoKiProfileCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenokiprofile")
                .requires(XenoKiProfileCommands::mayUse)
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("get")
                        .then(Commands.argument("techniqueId", StringArgumentType.greedyString())
                                .executes(ctx -> get(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "techniqueId")))))
                .executes(ctx -> status(ctx.getSource())));
    }

    /** SP/LAN integrated host: allow. Dedicated server: OP (or XENOBARRAGE_SET). */
    private static boolean mayUse(CommandSourceStack source) {
        if (source.getServer() != null && !source.getServer().isDedicatedServer()) {
            return true;
        }
        return source.hasPermission(2);
    }

    private static int reload(CommandSourceStack source) {
        XenoKiProfileCatalog.load();
        int owned = V3NativeKi.refreshOwned().size();
        source.sendSuccess(() -> Component.literal(
                "Ki profiles reloaded from " + XenoKiProfileCatalog.path().toAbsolutePath()
                        + " (" + XenoKiProfileCatalog.profilesView().size() + " techniques, "
                        + XenoKiProfileCatalog.archetypesView().size() + " archetypes; refreshed "
                        + owned + " owned DMZ copies)"), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        XenoKiProfileCatalog.ensureLoaded();
        source.sendSuccess(() -> Component.literal(
                "Ki profiles: " + XenoKiProfileCatalog.profilesView().size() + " techniques, "
                        + XenoKiProfileCatalog.archetypesView().size() + " archetypes @ "
                        + XenoKiProfileCatalog.path().getFileName()), false);
        return 1;
    }

    private static int get(CommandSourceStack source, String techniqueId) {
        XenoKiProfileCatalog.ensureLoaded();
        XenoKiProfile explicit = XenoKiProfileCatalog.profilesView().get(techniqueId);
        final XenoKiProfile shown = explicit != null
                ? explicit
                : XenoKiProfileCatalog.resolve(techniqueId, null, null);
        final String label = explicit != null ? techniqueId + ": " : techniqueId + " (merged/default): ";
        source.sendSuccess(() -> Component.literal(label + shown.toJson()), false);
        return 1;
    }
}
