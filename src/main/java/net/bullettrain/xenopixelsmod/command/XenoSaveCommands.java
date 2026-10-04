package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.server.WorldSaveFlush;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * {@code /xenosave} and {@code /xenorestart [seconds]} — flush Linear dirty regions,
 * then stop so the host can start the jar again.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoSaveCommands {
    private static final int DEFAULT_SECONDS = 10;
    private static final int MIN_SECONDS = 1;
    private static final int MAX_SECONDS = 300;

    private static ServerBossEvent bar;
    private static int remainingSeconds;
    private static int totalSeconds;
    private static int tickInSecond;

    private XenoSaveCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("xenosave")
                .requires(XenoPermissions.require(XenoPermissions.XENOSAVE))
                .executes(ctx -> save(ctx.getSource())));
        d.register(Commands.literal("xenorestart")
                .requires(XenoPermissions.require(XenoPermissions.XENORESTART))
                .executes(ctx -> startRestart(ctx.getSource(), DEFAULT_SECONDS))
                .then(Commands.literal("cancel").executes(ctx -> cancelRestart(ctx.getSource())))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(MIN_SECONDS, MAX_SECONDS))
                        .executes(ctx -> startRestart(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "seconds")))));
        d.register(Commands.literal("xenopixels")
                .then(Commands.literal("save")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSAVE))
                        .executes(ctx -> save(ctx.getSource())))
                .then(Commands.literal("restart")
                        .requires(XenoPermissions.require(XenoPermissions.XENORESTART))
                        .executes(ctx -> startRestart(ctx.getSource(), DEFAULT_SECONDS))
                        .then(Commands.literal("cancel").executes(ctx -> cancelRestart(ctx.getSource())))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(MIN_SECONDS, MAX_SECONDS))
                                .executes(ctx -> startRestart(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "seconds"))))));
        d.register(Commands.literal("xeno")
                .then(Commands.literal("save")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSAVE))
                        .executes(ctx -> save(ctx.getSource())))
                .then(Commands.literal("restart")
                        .requires(XenoPermissions.require(XenoPermissions.XENORESTART))
                        .executes(ctx -> startRestart(ctx.getSource(), DEFAULT_SECONDS))
                        .then(Commands.literal("cancel").executes(ctx -> cancelRestart(ctx.getSource())))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(MIN_SECONDS, MAX_SECONDS))
                                .executes(ctx -> startRestart(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "seconds"))))));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onStopping(ServerStoppingEvent event) {
        WorldSaveFlush.flush(event.getServer());
        clearBar();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (bar == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        tickInSecond++;
        if (tickInSecond < 20) {
            refreshBar();
            return;
        }
        tickInSecond = 0;
        remainingSeconds--;
        if (remainingSeconds > 0) {
            refreshBar();
            return;
        }
        WorldSaveFlush.Result result = WorldSaveFlush.flush(server);
        if (!result.ok()) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "Restart save failed: " + result.error()), false);
        }
        clearBar();
        server.halt(false);
    }

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (bar != null && event.getEntity() instanceof ServerPlayer player) {
            bar.addPlayer(player);
        }
    }

    private static int save(CommandSourceStack source) {
        WorldSaveFlush.Result result = WorldSaveFlush.flush(source.getServer());
        if (!result.ok()) {
            source.sendFailure(Component.literal("Save failed: " + result.error()));
            return 0;
        }
        // Always report what reached the disk, including when nothing did. "World saved" on its own
        // said the same thing whether the flush wrote the world or silently did nothing, which is
        // why a rollback could not be told apart from a failed save. The newest write time here is
        // what to compare against the files after the next one.
        source.sendSuccess(() -> Component.literal(
                "World saved" + (result.linearRegions() > 0
                        ? " (" + result.linearRegions() + " Linear region(s) flushed)"
                        : " (no Linear regions were dirty)")
                        + "; " + result.diskSummary() + "."),
                true);
        return 1;
    }

    private static int startRestart(CommandSourceStack source, int seconds) {
        MinecraftServer server = source.getServer();
        if (bar != null) {
            source.sendFailure(Component.literal("A restart is already counting down. /xenorestart cancel"));
            return 0;
        }
        totalSeconds = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
        remainingSeconds = totalSeconds;
        tickInSecond = 0;
        bar = new ServerBossEvent(
                title(),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
        bar.setVisible(true);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            bar.addPlayer(player);
        }
        refreshBar();
        source.sendSuccess(() -> Component.literal(
                "Restart in " + totalSeconds + "s. /xenorestart cancel to abort."), true);
        return 1;
    }

    private static int cancelRestart(CommandSourceStack source) {
        if (bar == null) {
            source.sendFailure(Component.literal("No restart is counting down."));
            return 0;
        }
        clearBar();
        source.sendSuccess(() -> Component.literal("Restart cancelled."), true);
        return 1;
    }

    private static void refreshBar() {
        if (bar == null) {
            return;
        }
        bar.setName(title());
        float progress = totalSeconds <= 0 ? 0.0f : remainingSeconds / (float) totalSeconds;
        bar.setProgress(Mth.clamp(progress, 0.0f, 1.0f));
    }

    private static Component title() {
        return Component.literal("Server is restarting in " + Math.max(0, remainingSeconds));
    }

    private static void clearBar() {
        if (bar != null) {
            bar.removeAllPlayers();
            bar.setVisible(false);
            bar = null;
        }
        remainingSeconds = 0;
        totalSeconds = 0;
        tickInSecond = 0;
    }
}
