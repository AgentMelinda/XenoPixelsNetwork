package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client open for Unified Maker Studio ({@code /xenomaker} [race|forms|hair]).
 *
 * <p>No Brigadier {@code .requires} — client command sources are often a {@code LocalPlayer}
 * (not {@code ServerPlayer}), so OP {@link XenoPermissions#MAKER_OPEN} checks hide the whole
 * command from chat. Same open pattern as {@code /xenohairui}. Create Race stays gated in the
 * Race screen. Does not shadow {@code /xenoraceformui} or {@code /xenohairui}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoMakerClientCommands {
    private XenoMakerClientCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenomaker")
                .executes(ctx -> openHub(ctx.getSource()))
                .then(Commands.literal("race")
                        .executes(ctx -> openRace(ctx.getSource())))
                .then(Commands.literal("forms")
                        .executes(ctx -> openForms(ctx.getSource())))
                .then(Commands.literal("hair")
                        .executes(ctx -> openHair(ctx.getSource()))));
    }

    private static int openHub(CommandSourceStack source) {
        ClientScreens.openXenoMakerHub.run();
        source.sendSuccess(() -> Component.literal(
                "Opened Xeno Maker Studio hub."), false);
        return 1;
    }

    private static int openRace(CommandSourceStack source) {
        ClientScreens.openRaceCharacterMaker.run();
        source.sendSuccess(() -> Component.literal(
                "Opened Race Character Maker (PR-D6d; runtime unverified)."), false);
        return 1;
    }

    private static int openForms(CommandSourceStack source) {
        ClientScreens.openFormMaker.run();
        source.sendSuccess(() -> Component.literal(
                "Opened Form Maker (PR-D6e; runtime unverified)."), false);
        return 1;
    }

    private static int openHair(CommandSourceStack source) {
        ClientScreens.openHairMaker.run();
        source.sendSuccess(() -> Component.literal(
                "Opened Hair Editor (PR-D7; path READY / runtime unverified)."), false);
        return 1;
    }
}
