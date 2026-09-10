package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.paradigm.ParadigmHologramBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class ParadigmHologramCommands {
    private static final int MAX_RADIUS = 4096;

    private ParadigmHologramCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenoholograms")
                .requires(XenoPermissions.require(XenoPermissions.XENOHOLOGRAMS_CLEAR))
                .then(Commands.literal("clear")
                        .then(Commands.literal("all").executes(ctx -> clearAll(ctx.getSource())))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, MAX_RADIUS))
                                        .executes(ctx -> clearRadius(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "blocks")))))));
    }

    private static int clearAll(CommandSourceStack source) {
        return clear(source, -1);
    }

    private static int clearRadius(CommandSourceStack source, int radius) {
        return clear(source, radius);
    }

    private static int clear(CommandSourceStack source, int radius) {
        if (!ParadigmHologramBridge.available()) {
            source.sendFailure(Component.literal("Paradigm is not loaded."));
            return 0;
        }
        try {
            ParadigmHologramBridge.Result result = radius < 0
                    ? ParadigmHologramBridge.clearAll()
                    : ParadigmHologramBridge.clearRadius(source.getLevel(), source.getPosition(), radius);
            String scope = radius < 0 ? "all dimensions" : radius + " blocks";
            source.sendSuccess(() -> Component.literal(String.format(
                    "§aCleared §f%d §apersistent and §f%d §atemporary Paradigm hologram(s) within §f%s§a.",
                    result.persistent(), result.temporary(), scope)), true);
            return result.total();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            XenoPixelsMod.LOGGER.error("Paradigm hologram cleanup failed", exception);
            source.sendFailure(Component.literal("Paradigm hologram cleanup failed; see the server log."));
            return 0;
        }
    }
}
