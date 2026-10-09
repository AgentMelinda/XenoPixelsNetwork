package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.bullettrain.xenopixelsmod.client.maker.KiProfileMakerScreen;
import net.bullettrain.xenopixelsmod.client.maker.MakerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client open for Unified Maker Studio ({@code /xenomaker} [race|forms|hair|taotto|ki]).
 *
 * <p>SP: Race/Hair/Taotto/Ki need no OP. Form is SP-only (cheats not required).
 * Remote multiplayer: OP level 2 still required for those makers.
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
                        .executes(ctx -> openHair(ctx.getSource())))
                .then(Commands.literal("taotto")
                        .executes(ctx -> openTaotto(ctx.getSource())))
                .then(Commands.literal("ki")
                        .executes(ctx -> openKi(ctx.getSource()))));
    }

    private static int openHub(CommandSourceStack source) {
        ClientScreens.openXenoMakerHub.run();
        source.sendSuccess(() -> Component.literal("Opened Xeno Maker Studio hub."), false);
        return 1;
    }

    private static int openRace(CommandSourceStack source) {
        if (!MakerAccess.canOpenCosmeticMaker()) {
            source.sendFailure(MakerAccess.denyCosmetic());
            return 0;
        }
        ClientScreens.openRaceCharacterMaker.run();
        source.sendSuccess(() -> Component.literal("Opened Race Character Maker."), false);
        return 1;
    }

    private static int openForms(CommandSourceStack source) {
        if (!MakerAccess.canOpenFormMaker()) {
            source.sendFailure(MakerAccess.denyForm());
            return 0;
        }
        ClientScreens.openFormMaker.run();
        source.sendSuccess(() -> Component.literal("Opened Form Maker (singleplayer)."), false);
        return 1;
    }

    private static int openHair(CommandSourceStack source) {
        if (!MakerAccess.canOpenCosmeticMaker()) {
            source.sendFailure(MakerAccess.denyCosmetic());
            return 0;
        }
        ClientScreens.openHairMaker.run();
        source.sendSuccess(() -> Component.literal("Opened Hair Editor."), false);
        return 1;
    }

    private static int openTaotto(CommandSourceStack source) {
        if (!MakerAccess.canOpenCosmeticMaker()) {
            source.sendFailure(MakerAccess.denyCosmetic());
            return 0;
        }
        ClientScreens.openTaottoMaker.run();
        source.sendSuccess(() -> Component.literal("Opened Taotto."), false);
        return 1;
    }

    private static int openKi(CommandSourceStack source) {
        if (!MakerAccess.canOpenCosmeticMaker()) {
            source.sendFailure(MakerAccess.denyCosmetic());
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new KiProfileMakerScreen(mc.screen));
        }
        source.sendSuccess(() -> Component.literal("Opened Ki Profiles Maker."), false);
        return 1;
    }
}
