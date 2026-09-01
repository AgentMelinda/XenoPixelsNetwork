package net.bullettrain.xenopixelsmod.command;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.compat.util.LazyOptional;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Toggle a player's real DragonMineZ aura ({@code Status.setAuraActive} + {@code StatsSyncS2C}).
 * Self {@code /xenoaura on|off|toggle} is allowed for everyone; targeting another player is OP.
 * Chase flight uses {@link #apply(ServerPlayer, boolean)} with no permission check.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoAuraCommands {
    private XenoAuraCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenoaura")
                .then(Commands.literal("on")
                        .requires(XenoPermissions.require(XenoPermissions.XENOAURA_SELF))
                        .executes(ctx -> setAura(ctx.getSource(), null, true))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(XenoPermissions.require(XenoPermissions.XENOAURA_OTHERS))
                                .executes(ctx -> setAura(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"), true))))
                .then(Commands.literal("off")
                        .requires(XenoPermissions.require(XenoPermissions.XENOAURA_SELF))
                        .executes(ctx -> setAura(ctx.getSource(), null, false))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(XenoPermissions.require(XenoPermissions.XENOAURA_OTHERS))
                                .executes(ctx -> setAura(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"), false))))
                .then(Commands.literal("toggle")
                        .requires(XenoPermissions.require(XenoPermissions.XENOAURA_SELF))
                        .executes(ctx -> toggle(ctx.getSource(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(XenoPermissions.require(XenoPermissions.XENOAURA_OTHERS))
                                .executes(ctx -> toggle(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoaura <on|off|toggle> [player]"), false);
                    return 1;
                }));
    }

    private static int toggle(CommandSourceStack source, ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer player = target != null ? target : source.getPlayerOrException();
        Status status = statusOf(player);
        boolean on = status == null || !status.isAuraActive();
        return setAura(source, player, on);
    }

    private static int setAura(CommandSourceStack source, ServerPlayer target, boolean on)
            throws CommandSyntaxException {
        ServerPlayer player = target != null ? target : source.getPlayerOrException();
        if (!apply(player, on)) {
            source.sendFailure(Component.literal("No DragonMineZ stats on " + player.getGameProfile().getName()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Aura " + (on ? "ON" : "OFF") + " for " + player.getGameProfile().getName()), true);
        return 1;
    }

    public static boolean isOn(ServerPlayer player) {
        Status status = statusOf(player);
        return status != null && status.isAuraActive();
    }

    /** Same effect as {@code /xenoaura on|off} — no permission check. Used by chase flight. */
    public static boolean apply(ServerPlayer player, boolean on) {
        if (player == null) {
            return false;
        }
        Status status = statusOf(player);
        if (status == null) {
            return false;
        }
        status.setAuraActive(on);
        try {
            status.setPermanentAura(on);
        } catch (Throwable ignored) {
        }
        try {
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static Status statusOf(ServerPlayer player) {
        LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
        StatsData data = opt.orElse(null);
        return data != null ? data.getStatus() : null;
    }
}
