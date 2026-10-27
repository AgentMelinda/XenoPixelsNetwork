package net.bullettrain.xenopixelsmod.command;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.HandoffOverlayTiming;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.space.chart.EffectiveSpaceConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /xenospace config dump <body>}: the resolved handoff settings of one chart body and where each came
 * from (design §19.3). Read-only. Registered as its own {@code xenospace} literal; Brigadier merges it into the
 * tree {@link SpaceCommands} builds, so this node carries its own permission check.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SpaceConfigCommands {
    private SpaceConfigCommands() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenospace").requires(source -> source.hasPermission(2))
                .then(Commands.literal("config").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("dump")
                                .then(Commands.argument("body", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                bodyIds(ctx.getSource().getServer()), builder))
                                        .executes(ctx -> dump(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "body").toString()))))));
    }

    /** The dump text for one body, or null when the chart has no such body. */
    public static String dump(MinecraftServer server, String bodyId) {
        var state = SpaceChartService.state(server);
        ChartRegistry chart = state.runtime.chart;
        int slot = chart.slotOf(bodyId);
        if (slot < 0) return null;
        var celestial = chart.body(slot);
        var body = EffectiveSpaceConfig.Body.of(celestial, chart.exitAltitude(slot));
        var sidecar = celestial.dimensionId() == null ? null : state.sidecarSettings.get(celestial.dimensionId());
        var globals = new EffectiveSpaceConfig.Globals(XenoSpaceConfig.enabled(), XenoSpaceConfig.handoffEnabled(),
                XenoSpaceConfig.velocityCarryover(), XenoSpaceConfig.handoffBandThickness(),
                XenoSpaceConfig.handoffExitOverrides().get(bodyId), (int) HandoffOverlayTiming.FADE_MILLIS);
        return EffectiveSpaceConfig.format(body, EffectiveSpaceConfig.resolve(body, globals, sidecar));
    }

    private static int dump(CommandSourceStack source, String bodyId) {
        String text = dump(source.getServer(), bodyId);
        if (text == null) {
            source.sendFailure(Component.literal("Unknown chart body: " + bodyId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static List<String> bodyIds(MinecraftServer server) {
        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        List<String> ids = new ArrayList<>(chart.size());
        for (int slot = 0; slot < chart.size(); slot++) ids.add(chart.body(slot).id());
        return ids;
    }
}
