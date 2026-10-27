package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.event.StellarLifecycleService;
import net.bullettrain.xenopixelsmod.space.event.StellarLifecycleSavedData;
import net.bullettrain.xenopixelsmod.space.event.StellarStage;
import net.bullettrain.xenopixelsmod.space.event.StellarStageSnapshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/** Admin controls for manually scripted chart-star lifecycle stages (§23.1); no physical star rebuilds. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class StellarCommands {
    private StellarCommands() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var stageArgument = Commands.argument("stage", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(StellarStage.values()).map(Enum::name), builder))
                .executes(ctx -> setStage(ctx.getSource(),
                        ResourceLocationArgument.getId(ctx, "id").toString(),
                        StringArgumentType.getString(ctx, "stage")));
        var starIdArgument = Commands.argument("id", ResourceLocationArgument.id())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(starIds(ctx.getSource()), builder))
                .then(Commands.literal("stage").then(stageArgument));
        var starCommand = Commands.literal("star")
                .then(Commands.literal("lifecycle")
                        .executes(ctx -> report(ctx.getSource()))
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), false))))
                .then(Commands.literal("ramp")
                        .executes(ctx -> success(ctx.getSource(), "stellar stage ramp="
                                + XenoSpaceConfig.stellarStageRampDays() + " game days"))
                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 365))
                                .executes(ctx -> ramp(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "days")))))
                .then(Commands.literal("wave-duration")
                        .executes(ctx -> success(ctx.getSource(), "stellar wave duration="
                                + XenoSpaceConfig.stellarWaveDurationTicks() + " ticks"))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1,
                                        StellarStageSnapshot.MAX_RAMP_TICKS))
                                .executes(ctx -> waveDuration(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "ticks")))))
                .then(starIdArgument);
        event.getDispatcher().register(Commands.literal("xenospace")
                .requires(source -> source.hasPermission(2)).then(starCommand));
    }

    private static int report(CommandSourceStack source) {
        return success(source, "stellar lifecycle=" + (XenoSpaceConfig.supernovaEnabled() ? "on" : "off")
                + "; ramp=" + XenoSpaceConfig.stellarStageRampDays() + " game days per stage"
                + "; wave-duration=" + XenoSpaceConfig.stellarWaveDurationTicks() + " ticks");
    }

    private static int toggle(CommandSourceStack source, boolean enabled) {
        XenoSpaceConfig.setSupernovaEnabled(enabled);
        StellarLifecycleService.syncAll(source.getServer());
        return report(source);
    }

    private static int ramp(CommandSourceStack source, int days) {
        XenoSpaceConfig.setStellarStageRampDays(days);
        StellarLifecycleService.syncAll(source.getServer());
        return report(source);
    }

    private static int waveDuration(CommandSourceStack source, int ticks) {
        XenoSpaceConfig.setStellarWaveDurationTicks(ticks);
        StellarLifecycleService.syncAll(source.getServer());
        return report(source);
    }

    private static int setStage(CommandSourceStack source, String id, String name) {
        StellarStage stage;
        try {
            stage = StellarStage.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Unknown stage. Use "
                    + Arrays.toString(StellarStage.values())));
            return 0;
        }
        var result = StellarLifecycleService.setStage(source.getServer(), id, stage);
        if (result != StellarLifecycleService.StageUpdateResult.UPDATED) {
            String reason = switch (result) {
                case UNKNOWN_STAR -> "Unknown chart star: " + id;
                case PRESERVED_DATA_LOCKED -> "Stellar lifecycle data has an unrecognized saved format; edits are locked to preserve it";
                case ENTRY_CAP_REACHED -> "Stellar lifecycle entry cap reached (" + StellarLifecycleSavedData.MAX_ENTRIES + ")";
                case INVALID_REQUEST -> "Could not update stellar lifecycle stage; check the star id and saved data";
                case UPDATED -> "updated";
            };
            source.sendFailure(Component.literal(reason));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(id + " lifecycle stage=" + stage), true);
        return 1;
    }

    private static java.util.List<String> starIds(CommandSourceStack source) {
        var chart = SpaceChartService.state(source.getServer()).runtime.chart;
        var result = new java.util.ArrayList<String>();
        for (int i = 0; i < chart.size(); i++) {
            var body = chart.body(i);
            if (body.kind() == CelestialBody.BodyKind.STAR) result.add(body.id());
        }
        return result;
    }

    private static int success(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
