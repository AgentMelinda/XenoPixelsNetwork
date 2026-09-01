package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.config.XenoServerConfigKeys;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * Hold-to-grow ki wave knobs.
 * <pre>
 * /xenosurge status
 * /xenosurge on|off
 * /xenosurge set &lt;key&gt; &lt;value&gt;
 * </pre>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class BeamSurgeCommands {

    private static final List<String> SURGE_KEYS = List.of(
            "kipertick", "stam", "cost", "sizegain", "damagegain", "reachgain",
            "search", "ceiling", "ramp", "maxlength"
    );

    private static final SuggestionProvider<CommandSourceStack> KEY_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(SURGE_KEYS, builder);

    private BeamSurgeCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenosurge")
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_STATUS))
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("on")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                        .executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(Commands.literal("off")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                        .executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(Commands.literal("set")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSERVER_SET))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests(KEY_SUGGEST)
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> set(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                StringArgumentType.getString(ctx, "value"))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenosurge <status|on|off|set <key> <value>>\n"
                                    + "keys: kipertick stam cost sizegain damagegain reachgain "
                                    + "search ceiling ramp maxlength\n"
                                    + currentLine()), false);
                    return 1;
                }));
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(currentLine()), false);
        return 1;
    }

    private static int setEnabled(CommandSourceStack source, boolean on) {
        return apply(source, "beamSurgeEnabled", Boolean.toString(on));
    }

    private static int set(CommandSourceStack source, String key, String value) {
        String mapped = switch (key.toLowerCase()) {
            case "kipertick", "ki" -> "beamSurgeKiPerTick";
            case "stam", "stamina", "stamper tick" -> "beamSurgeStaminaPerTick";
            case "cost", "costgrowth" -> "beamSurgeCostGrowth";
            case "sizegain", "size" -> "beamSurgeSizeGain";
            case "damagegain", "damage" -> "beamSurgeDamageGain";
            case "reachgain", "reach" -> "beamSurgeReachGain";
            case "search", "radius" -> "beamSurgeSearchRadius";
            case "ceiling" -> "beamSurgeCeiling";
            case "ceilingper", "ceilingmastery" -> "beamSurgeCeilingPerMastery";
            case "ramp" -> "beamSurgeRampPerTick";
            case "rampmastery" -> "beamSurgeRampPerMastery";
            case "maxlength", "length" -> "beamSurgeMaxLength";
            default -> key;
        };
        return apply(source, mapped, value);
    }

    private static int apply(CommandSourceStack source, String key, String value) {
        XenoServerConfigKeys.Result result = XenoServerConfigKeys.set(key, value);
        if (!result.ok) {
            source.sendFailure(Component.literal(result.message));
            return 0;
        }
        XenoServerConfig.save();
        DmzHudCommands.broadcast();
        source.sendSuccess(() -> Component.literal(result.message), true);
        return 1;
    }

    static String currentLine() {
        return "surge=" + XenoServerConfig.beamSurgeEnabled
                + " ki/t=" + XenoServerConfig.beamSurgeKiPerTick
                + " stam/t=" + XenoServerConfig.beamSurgeStaminaPerTick
                + " sizeGain=" + XenoServerConfig.beamSurgeSizeGain
                + " dmgGain=" + XenoServerConfig.beamSurgeDamageGain
                + " reachGain=" + XenoServerConfig.beamSurgeReachGain
                + " ceiling=" + XenoServerConfig.beamSurgeCeiling
                + " maxLength=" + XenoServerConfig.beamSurgeMaxLength
                + " search=" + XenoServerConfig.beamSurgeSearchRadius;
    }
}
