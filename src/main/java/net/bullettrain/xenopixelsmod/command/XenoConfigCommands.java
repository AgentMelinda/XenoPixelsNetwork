package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.overcharge.OverchargeVoices;
import net.bullettrain.xenopixelsmod.combat.targeting.LockOnConfig;
import net.bullettrain.xenopixelsmod.config.XenoConfigRegistry;
import net.bullettrain.xenopixelsmod.config.XenoPartyConfig;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.config.XenoServerConfigKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Dedicated-server front-end for every server-owned config store.
 *
 * <p>{@code /xenoset} is the same setter (docs already used that name). Combat keys
 * still sync through {@code SyncServerConfigPacket}; party / lock-on / perf only
 * write their JSON because clients do not read those files.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoConfigCommands {

    private XenoConfigCommands() {}

    private static final SuggestionProvider<CommandSourceStack> KEY_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    XenoConfigRegistry.suggest(builder.getRemaining()), builder);

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("xenoconfig")
                .then(Commands.literal("list")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_STATUS))
                        .executes(ctx -> list(ctx.getSource(), ""))
                        .then(Commands.argument("prefix", StringArgumentType.word())
                                .suggests(KEY_SUGGEST)
                                .executes(ctx -> list(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "prefix")))))
                .then(Commands.literal("get")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_STATUS))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests(KEY_SUGGEST)
                                .executes(ctx -> get(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "key")))))
                .then(Commands.literal("set")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests(KEY_SUGGEST)
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> set(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                StringArgumentType.getString(ctx, "value"))))))
                .then(Commands.literal("reload")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_RELOAD))
                        .executes(ctx -> reload(ctx.getSource(), "all"))
                        .then(Commands.argument("store", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        new String[] {"all", "combat", "party", "lockon", "perf"}, b))
                                .executes(ctx -> reload(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "store")))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoconfig list [prefix] | get <key> | set <key> <value> "
                                    + "| reload [combat|party|lockon|perf|all]\n"
                                    + "Alias: /xenoset <key> <value>"), false);
                    return 1;
                }));
        d.register(Commands.literal("xenoset")
                .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                .then(Commands.argument("key", StringArgumentType.word())
                        .suggests(KEY_SUGGEST)
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .executes(ctx -> set(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "key"),
                                        StringArgumentType.getString(ctx, "value")))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoset <key> <value>  (same as /xenoconfig set)"), false);
                    return 1;
                }));
    }

    private static int list(CommandSourceStack source, String prefix) {
        if (XenoConfigRegistry.isHudPrefix(prefix)) {
            source.sendSuccess(() -> Component.literal(XenoConfigRegistry.hudOutOfScope()), false);
            return 1;
        }
        String p = prefix == null ? "" : prefix.trim().toLowerCase();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (XenoConfigRegistry.Entry extra : XenoConfigRegistry.extras()) {
            if (!matches(extra.id, extra.help, p)) continue;
            sb.append("§e").append(extra.id).append(" §f= ").append(extra.get())
                    .append(" §7").append(extra.help).append('\n');
            n++;
        }
        for (XenoServerConfigKeys.Key key : XenoServerConfigKeys.all()) {
            if (!matches(key.id, key.help, p)) continue;
            sb.append("§e").append(key.id).append(" §f= ").append(key.get())
                    .append(" §7").append(key.help).append('\n');
            n++;
        }
        if (n == 0) {
            source.sendFailure(Component.literal("No keys matching '" + prefix + "'"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return n;
    }

    private static boolean matches(String id, String help, String prefix) {
        if (prefix.isEmpty()) return true;
        return id.toLowerCase().contains(prefix) || help.toLowerCase().contains(prefix);
    }

    private static int get(CommandSourceStack source, String key) {
        XenoConfigRegistry.Result result = XenoConfigRegistry.get(key);
        if (!result.ok) {
            source.sendFailure(Component.literal(result.message));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message), false);
        return 1;
    }

    private static int set(CommandSourceStack source, String key, String value) {
        XenoConfigRegistry.Result result = XenoConfigRegistry.set(key, value);
        if (!result.ok) {
            source.sendFailure(Component.literal(result.message));
            return 0;
        }
        persist(result.store);
        source.sendSuccess(() -> Component.literal(result.message), true);
        return 1;
    }

    private static void persist(XenoConfigRegistry.Store store) {
        if (store == null) return;
        switch (store) {
            case COMBAT -> {
                XenoServerConfig.save();
                DmzHudCommands.broadcast();
            }
            case PARTY -> XenoPartyConfig.save();
            case LOCKON -> LockOnConfig.save();
            case PERF -> XenoPerfConfig.save();
        }
    }

    private static int reload(CommandSourceStack source, String store) {
        String which = store == null ? "all" : store.trim().toLowerCase();
        boolean combat = which.equals("all") || which.equals("combat");
        boolean party = which.equals("all") || which.equals("party");
        boolean lockon = which.equals("all") || which.equals("lockon");
        boolean perf = which.equals("all") || which.equals("perf");
        if (!combat && !party && !lockon && !perf) {
            source.sendFailure(Component.literal(
                    "Unknown store '" + store + "'. Use combat, party, lockon, perf, or all"));
            return 0;
        }
        if (combat) {
            XenoServerConfig.load();
            OverchargeVoices.reload();
            DmzHudCommands.broadcast();
        }
        if (party) XenoPartyConfig.load();
        if (lockon) LockOnConfig.load();
        if (perf) XenoPerfConfig.load();
        source.sendSuccess(() -> Component.literal("Reloaded " + which), true);
        return 1;
    }
}
