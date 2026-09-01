package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.AeroConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Runtime toggle for {@link AeroConfig#requirePower}, so an op can flip the FE requirement
 * without editing {@code config/xenopixelsmod-aero.json} and restarting the server. Modeled
 * directly on {@link ShipGravityCommands}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class AeroPowerCommands {
    private AeroPowerCommands() { }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(command("xenoaeropower"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("on").executes(context -> set(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> set(context.getSource(), false)))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .executes(context -> status(context.getSource()));
    }

    private static int set(CommandSourceStack source, boolean required) {
        AeroConfig.requirePower = required;
        AeroConfig.save();
        source.sendSuccess(() -> Component.literal(required
                ? "§eAero power requirement: §cON §7— flight needs FE wired to the seat/controller"
                : "§eAero power requirement: §aOFF §7— flight works with no FE source"), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§eAero power requirement is currently "
                + (AeroConfig.requirePower ? "§cON" : "§aOFF")), false);
        return 1;
    }
}
