package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.linearreader.LinearConversionPolicy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.util.function.Consumer;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class LinearConversionCommands {
    private LinearConversionCommands() {}

    @SubscribeEvent
    public static void starting(ServerAboutToStartEvent event) {
        try {
            LinearConversionPolicy.start(FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-linearreader.json"),
                    event.getServer().getWorldPath(LevelResource.ROOT));
            requireSupportedRestrictions(LinearConversionPolicy.settings());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load LinearReader dimension policy safely", exception);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { LinearConversionPolicy.stop(); }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var command = Commands.literal("xenolinear").requires(source -> source.hasPermission(2))
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("enabled").then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> update(ctx.getSource(), settings -> settings.enabled = BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("default").then(Commands.argument("allowed", BoolArgumentType.bool())
                        .executes(ctx -> update(ctx.getSource(), settings -> settings.defaultConversionAllowed = BoolArgumentType.getBool(ctx, "allowed")))))
                .then(Commands.literal("dimension").then(Commands.argument("dimension", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                ctx.getSource().getServer().levelKeys().stream().map(key -> key.location().toString()), builder))
                        .executes(ctx -> dimensionStatus(ctx.getSource(), ResourceLocationArgument.getId(ctx, "dimension").toString()))
                        .then(Commands.argument("allowed", BoolArgumentType.bool()).executes(ctx -> update(ctx.getSource(), settings -> {
                            String dimension = ResourceLocationArgument.getId(ctx, "dimension").toString();
                            LinearConversionPolicy.validateDimension(dimension);
                            settings.dimensions.put(dimension, BoolArgumentType.getBool(ctx, "allowed"));
                        })))
                        .then(Commands.literal("toggle").executes(ctx -> update(ctx.getSource(), settings -> {
                            String dimension = ResourceLocationArgument.getId(ctx, "dimension").toString();
                            LinearConversionPolicy.validateDimension(dimension);
                            settings.dimensions.put(dimension, !settings.dimensions.getOrDefault(dimension, settings.defaultConversionAllowed));
                        })))
                        .then(Commands.literal("reset").executes(ctx -> update(ctx.getSource(), settings ->
                                settings.dimensions.remove(ResourceLocationArgument.getId(ctx, "dimension").toString()))))));
        event.getDispatcher().register(command);
    }

    private static int status(CommandSourceStack source) {
        var settings = LinearConversionPolicy.settings();
        String version = ModList.get().getModContainerById("linearreader")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("absent");
        source.sendSuccess(() -> Component.literal("LinearReader=" + version + "; policy enabled=" + settings.enabled
                + "; default conversion=" + settings.defaultConversionAllowed + "; dimensions=" + settings.dimensions
                + ". Integration supports 1.3.0. Open regions retain their format until restart; existing linear files remain readable."), false);
        return 1;
    }

    private static int dimensionStatus(CommandSourceStack source, String dimension) {
        try {
            LinearConversionPolicy.validateDimension(dimension);
            source.sendSuccess(() -> Component.literal(dimension + ": new linear conversion allowed="
                    + LinearConversionPolicy.settings().allows(dimension)), false);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage())); return 0;
        }
    }

    private static int reload(CommandSourceStack source) {
        try { LinearConversionPolicy.reload(LinearConversionCommands::requireSupportedRestrictions); return status(source); }
        catch (IOException | IllegalArgumentException exception) { source.sendFailure(Component.literal(exception.getMessage())); return 0; }
    }

    private static int update(CommandSourceStack source, Consumer<LinearConversionPolicy.Settings> change) {
        try {
            var settings = LinearConversionPolicy.settings();
            change.accept(settings);
            requireSupportedRestrictions(settings);
            LinearConversionPolicy.save(settings);
            source.sendSuccess(() -> Component.literal("Saved LinearReader dimension policy. Applies to future opens/conversions; restart to release retained Anvil regions."), true);
            return status(source);
        } catch (IOException | IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage())); return 0;
        }
    }

    private static void requireSupportedRestrictions(LinearConversionPolicy.Settings settings) {
        boolean restricted = settings.enabled && (!settings.defaultConversionAllowed || settings.dimensions.containsValue(false));
        var installed = ModList.get().getModContainerById("linearreader");
        if (restricted && installed.isPresent() && !installed.get().getModInfo().getVersion().toString().equals("1.3.0")) {
            throw new IllegalArgumentException("Dimension conversion restrictions require LinearReader 1.3.0; installed "
                    + installed.get().getModInfo().getVersion());
        }
    }
}
