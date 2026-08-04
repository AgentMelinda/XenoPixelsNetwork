package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Module perf: chunk force-load + thruster force distance.
 * <pre>
 * /xenoperf status|reload
 * /xenoperf set &lt;key&gt; &lt;value&gt;
 * </pre>
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PerfCommands {
    private PerfCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("xenoperf")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            XenoPerfConfig.load();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Perf config reloaded: " + XenoPerfConfig.statusLine()), true);
                            return 1;
                        }))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> set(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                StringArgumentType.getString(ctx, "value"))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoperf <status|reload|set <key> <value>>\n"
                                    + "keys: maxrange (blocks, default 1000000=1000km, 0=unlimited),\n"
                                    + "  thrforce (default off), thralways, thrrange,\n"
                                    + "  forcechunks (default off), targetonly, radius, duration, playerange,\n"
                                    + "  statsync, statsheartbeat, statsdirty"), false);
                    return 1;
                }));
    }

    private static int status(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal(
                "§eXenoPixels Module Perf§r (no global VS2 ship sleep)\n"
                        + XenoPerfConfig.statusLine()), false);
        return 1;
    }

    private static int set(CommandSourceStack src, String key, String raw) {
        String k = key.toLowerCase();
        try {
            switch (k) {
                case "enabled", "perf" -> XenoPerfConfig.perfEnabled = parseBool(raw);
                case "maxrange", "range", "ballisticrange" ->
                        XenoPerfConfig.ballisticMaxRangeBlocks = Integer.parseInt(raw.trim());
                case "forcechunks", "chunks" -> XenoPerfConfig.forceChunksEnabled = parseBool(raw);
                case "targetonly", "target" -> XenoPerfConfig.forceChunksTargetOnly = parseBool(raw);
                case "radius" -> XenoPerfConfig.forceChunksRadius = Integer.parseInt(raw);
                case "duration" -> XenoPerfConfig.forceChunksDurationTicks = Integer.parseInt(raw);
                case "playerange", "playerrange" -> XenoPerfConfig.forceChunksPlayerRange = Double.parseDouble(raw);
                case "thrforce", "thrusterforce", "thrustforce" ->
                        XenoPerfConfig.thrusterPhysForceEnabled = parseBool(raw);
                case "thralways", "thrusteralways" -> XenoPerfConfig.thrusterForceAlways = parseBool(raw);
                case "thrrange", "thrusterrange" -> XenoPerfConfig.thrusterForcePlayerRange = Double.parseDouble(raw);
                case "statsync", "statsyncinterval" -> XenoPerfConfig.statsSyncIntervalTicks = Integer.parseInt(raw);
                case "statsheartbeat" -> XenoPerfConfig.statsSyncHeartbeatTicks = Integer.parseInt(raw);
                case "statsdirty" -> XenoPerfConfig.statsSyncOnlyWhenDirty = parseBool(raw);
                default -> {
                    src.sendFailure(Component.literal("Unknown key: " + key));
                    return 0;
                }
            }
            XenoPerfConfig.apply(XenoPerfConfig.snapshot());
            XenoPerfConfig.save();
            src.sendSuccess(() -> Component.literal(
                    "Set " + k + " → saved. " + XenoPerfConfig.statusLine()), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Bad value: " + e.getMessage()));
            return 0;
        }
    }

    private static boolean parseBool(String raw) {
        String s = raw.trim().toLowerCase();
        if (s.equals("true") || s.equals("on") || s.equals("1") || s.equals("yes")) return true;
        if (s.equals("false") || s.equals("off") || s.equals("0") || s.equals("no")) return false;
        throw new IllegalArgumentException("expected true/false, got: " + raw);
    }
}
