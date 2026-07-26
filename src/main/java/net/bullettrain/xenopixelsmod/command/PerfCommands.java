package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.bullettrain.xenopixelsmod.perf.CreatePerfHooks;
import net.bullettrain.xenopixelsmod.perf.MsptWatchdog;
import net.bullettrain.xenopixelsmod.perf.Vs2ShipSleepManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
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
                                    + "keys: enabled, vs2sleep, hotrange, warmrange, maxships, "
                                    + "msptwatch, msptthreshold, createsleep, createcoupled, "
                                    + "createhotrange, createmaxcontraptions"), false);
                    return 1;
                }));
    }

    private static int status(CommandSourceStack src) {
        String create = CreatePerfHooks.isCreateLoaded()
                ? ("yes contraptions=" + CreatePerfHooks.lastContraptionCount()
                + " frozen=" + CreatePerfHooks.lastFrozenCount())
                : "not loaded";
        src.sendSuccess(() -> Component.literal(
                "§eXenoPixels Perf§r\n"
                        + XenoPerfConfig.statusLine() + "\n"
                        + String.format("MSPT avg: %.1f  stressed: %s  effHot: %.0f  effWarm: %.0f\n",
                        MsptWatchdog.averageMspt(),
                        MsptWatchdog.isStressed(),
                        MsptWatchdog.effectiveHotRange(),
                        MsptWatchdog.effectiveWarmRange())
                        + "VS2 ships dynamic=" + Vs2ShipSleepManager.lastDynamicCount()
                        + " static=" + Vs2ShipSleepManager.lastStaticCount()
                        + " parkedByUs=" + Vs2ShipSleepManager.lastParkedByUsCount() + "\n"
                        + "Create: " + create), false);
        return 1;
    }

    private static int set(CommandSourceStack src, String key, String raw) {
        String k = key.toLowerCase();
        try {
            switch (k) {
                case "enabled", "perf" -> XenoPerfConfig.perfEnabled = parseBool(raw);
                case "vs2sleep", "sleep" -> XenoPerfConfig.vs2SleepEnabled = parseBool(raw);
                case "hotrange", "hot" -> XenoPerfConfig.vs2HotRange = Double.parseDouble(raw);
                case "warmrange", "warm" -> XenoPerfConfig.vs2WarmRange = Double.parseDouble(raw);
                case "maxships" -> XenoPerfConfig.vs2MaxActiveShipsGlobal = Integer.parseInt(raw);
                case "idleseconds" -> XenoPerfConfig.vs2IdleSeconds = Integer.parseInt(raw);
                case "msptwatch" -> XenoPerfConfig.msptWatchdogEnabled = parseBool(raw);
                case "msptthreshold", "mspt" -> XenoPerfConfig.msptThreshold = Double.parseDouble(raw);
                case "createsleep", "create" -> XenoPerfConfig.createPerfEnabled = parseBool(raw);
                case "createcoupled", "coupled" -> XenoPerfConfig.createCoupledSleep = parseBool(raw);
                case "createhotrange" -> XenoPerfConfig.createHotRange = Double.parseDouble(raw);
                case "createmaxcontraptions", "maxcontraptions" ->
                        XenoPerfConfig.createMaxContraptionsGlobal = Integer.parseInt(raw);
                default -> {
                    src.sendFailure(Component.literal("Unknown key: " + key));
                    return 0;
                }
            }
            // re-clamp via apply snapshot roundtrip
            XenoPerfConfig.apply(XenoPerfConfig.snapshot());
            XenoPerfConfig.save();
            src.sendSuccess(() -> Component.literal("Set " + k + " → saved. " + XenoPerfConfig.statusLine()), true);
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
