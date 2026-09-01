package net.bullettrain.xenopixelsmod.command;

import net.neoforged.fml.common.EventBusSubscriber;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.overcharge.OverchargeVoices;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.config.XenoServerConfigKeys;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.SyncDmzHudStatePacket;
import net.bullettrain.xenopixelsmod.network.SyncServerConfigPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DmzHudCommands {
    private DmzHudCommands() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        XenoServerConfig.load();
        XenoPixelsMod.LOGGER.info("XenoPixels server config loaded (DMZ HUD={}, combat={})",
                XenoServerConfig.dmzHudEnabled ? "ON" : "OFF",
                XenoServerConfig.bt3CombatEnabled ? "ON" : "OFF");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dmzhud")
                .then(Commands.literal("toggle")
                        .requires(XenoPermissions.require(XenoPermissions.DMZHUD_TOGGLE))
                        .executes(ctx -> {
                            boolean on = !XenoServerConfig.dmzHudEnabled;
                            XenoServerConfig.setDmzHudEnabled(on);
                            broadcast();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("DMZ HUD for all players: " + (on ? "ON" : "OFF")),
                                    true);
                            return 1;
                        }))
                .then(Commands.literal("on")
                        .requires(XenoPermissions.require(XenoPermissions.DMZHUD_ON))
                        .executes(ctx -> setDmzHud(ctx.getSource(), true)))
                .then(Commands.literal("off")
                        .requires(XenoPermissions.require(XenoPermissions.DMZHUD_OFF))
                        .executes(ctx -> setDmzHud(ctx.getSource(), false)))
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.DMZHUD_STATUS))
                        .executes(ctx -> {
                            boolean on = XenoServerConfig.dmzHudEnabled;
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("DMZ HUD default (all players): " + (on ? "ON" : "OFF")),
                                    false);
                            return on ? 1 : 0;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("Usage: /dmzhud <toggle|on|off|status>"),
                            false);
                    return 1;
                }));

        dispatcher.register(Commands.literal("xenoserver")
                .then(Commands.literal("reload")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_RELOAD))
                        .executes(ctx -> {
                            XenoServerConfig.load();
                            OverchargeVoices.reload();
                            broadcast();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("XenoPixels server config reloaded + synced"), true);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_STATUS))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(serverStatus()), false);
                            return 1;
                        }))
                .then(Commands.literal("get")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_STATUS))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests(KEY_SUGGEST)
                                .executes(ctx -> getKey(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "key")))))
                .then(Commands.literal("set")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests(KEY_SUGGEST)
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> setKey(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                StringArgumentType.getString(ctx, "value"))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoserver <reload|status|get <key>|set <key> <value>>\n"
                                    + "keys: " + XenoServerConfigKeys.usageKeys()
                                    + " (tab-complete for the full list)"),
                            false);
                    return 1;
                }));
    }

    private static int setDmzHud(CommandSourceStack source, boolean enabled) {
        XenoServerConfig.setDmzHudEnabled(enabled);
        broadcast();
        source.sendSuccess(
                () -> Component.literal("DMZ HUD for all players: " + (enabled ? "ON" : "OFF")),
                true);
        return 1;
    }

    private static final SuggestionProvider<CommandSourceStack> KEY_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    XenoServerConfigKeys.suggest(builder.getRemaining()), builder);

    private static int setKey(CommandSourceStack source, String key, String value) {
        XenoServerConfigKeys.Result result = XenoServerConfigKeys.set(key, value);
        if (!result.ok) {
            source.sendFailure(Component.literal(result.message));
            return 0;
        }
        XenoServerConfig.save();
        broadcast();
        source.sendSuccess(() -> Component.literal(result.message), true);
        return 1;
    }

    private static int getKey(CommandSourceStack source, String key) {
        XenoServerConfigKeys.Result result = XenoServerConfigKeys.get(key);
        if (!result.ok) {
            source.sendFailure(Component.literal(result.message));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message), false);
        return 1;
    }

    private static String serverStatus() {
        return "combat=" + XenoServerConfig.bt3CombatEnabled
                + " combo=" + XenoServerConfig.bt3ComboEnabled
                + " vanish=" + XenoServerConfig.bt3VanishEnabled
                + " chase=" + XenoServerConfig.bt3ChaseDashEnabled
                + " backstep=" + XenoServerConfig.bt3BackstepEnabled
                + " finisher=" + XenoServerConfig.bt3FinisherEnabled
                + " charge=" + XenoServerConfig.bt3ChargeAttackEnabled
                + " dragon=" + XenoServerConfig.bt3DragonDashEnabled
                + " dmzHud=" + XenoServerConfig.dmzHudEnabled
                + " bootstrap=" + XenoServerConfig.dmzContentBootstrap
                + " formMult=" + XenoServerConfig.formStatMultiplier
                + " dummy=" + XenoServerConfig.trainingDummyEnabled
                + " quest=" + XenoServerConfig.parallelQuestEnabled
                + " mentor=" + XenoServerConfig.mentorEnabled
                + "\n" + KiDurationCommands.currentLine()
                + "\n" + BeamSurgeCommands.currentLine();
    }

    public static void broadcast() {
        ModNetwork.sendToAll(new SyncServerConfigPacket(XenoServerConfig.snapshot()));
        ModNetwork.sendToAll(new SyncDmzHudStatePacket(XenoServerConfig.dmzHudEnabled));
    }

    private static void syncTo(ServerPlayer player) {
        ModNetwork.sendToPlayer(player, new SyncServerConfigPacket(XenoServerConfig.snapshot()));
        ModNetwork.sendToPlayer(player, new SyncDmzHudStatePacket(XenoServerConfig.dmzHudEnabled));
    }
}
