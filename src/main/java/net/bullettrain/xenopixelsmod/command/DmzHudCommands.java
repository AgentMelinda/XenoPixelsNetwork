package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.SyncDmzHudStatePacket;
import net.bullettrain.xenopixelsmod.network.SyncServerConfigPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
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
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("toggle")
                        .executes(ctx -> {
                            boolean on = !XenoServerConfig.dmzHudEnabled;
                            XenoServerConfig.setDmzHudEnabled(on);
                            broadcast();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("DMZ HUD for all players: " + (on ? "ON" : "OFF")),
                                    true);
                            return 1;
                        }))
                .then(Commands.literal("on").executes(ctx -> setDmzHud(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> setDmzHud(ctx.getSource(), false)))
                .then(Commands.literal("status")
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
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            XenoServerConfig.load();
                            broadcast();
                            ctx.getSource().sendSuccess(() -> Component.literal("XenoPixels server config reloaded + synced"), true);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            XenoServerConfig.Data d = XenoServerConfig.snapshot();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "combat=" + d.bt3CombatEnabled
                                            + " combo=" + d.bt3ComboEnabled
                                            + " vanish=" + d.bt3VanishEnabled
                                            + " chase=" + d.bt3ChaseDashEnabled
                                            + " backstep=" + d.bt3BackstepEnabled
                                            + " finisher=" + d.bt3FinisherEnabled
                                            + " charge=" + d.bt3ChargeAttackEnabled
                                            + " dragon=" + d.bt3DragonDashEnabled
                                            + " dmzHud=" + d.dmzHudEnabled
                                            + " bootstrap=" + d.dmzContentBootstrap), false);
                            return 1;
                        }))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setFlag(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                BoolArgumentType.getBool(ctx, "value"))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoserver <reload|status|set <key> <true|false>>\n"
                                    + "keys: combat, combo, vanish, chase, backstep, finisher, charge, dragon, dmzhud, bootstrap"),
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

    private static int setFlag(CommandSourceStack source, String key, boolean value) {
        String k = key.toLowerCase();
        switch (k) {
            case "combat" -> XenoServerConfig.bt3CombatEnabled = value;
            case "combo" -> XenoServerConfig.bt3ComboEnabled = value;
            case "vanish" -> XenoServerConfig.bt3VanishEnabled = value;
            case "chase" -> XenoServerConfig.bt3ChaseDashEnabled = value;
            case "backstep" -> XenoServerConfig.bt3BackstepEnabled = value;
            case "finisher" -> XenoServerConfig.bt3FinisherEnabled = value;
            case "charge" -> XenoServerConfig.bt3ChargeAttackEnabled = value;
            case "dragon" -> XenoServerConfig.bt3DragonDashEnabled = value;
            case "dmzhud" -> XenoServerConfig.dmzHudEnabled = value;
            case "bootstrap" -> XenoServerConfig.dmzContentBootstrap = value;
            default -> {
                source.sendFailure(Component.literal("Unknown key: " + key));
                return 0;
            }
        }
        XenoServerConfig.save();
        broadcast();
        source.sendSuccess(() -> Component.literal("Set " + k + " = " + value), true);
        return 1;
    }

    public static void broadcast() {
        ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new SyncServerConfigPacket(XenoServerConfig.snapshot()));
        ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new SyncDmzHudStatePacket(XenoServerConfig.dmzHudEnabled));
    }

    private static void syncTo(ServerPlayer player) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncServerConfigPacket(XenoServerConfig.snapshot()));
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncDmzHudStatePacket(XenoServerConfig.dmzHudEnabled));
    }
}
