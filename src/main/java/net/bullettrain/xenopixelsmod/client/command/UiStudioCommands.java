package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.ui.runtime.UiActions;
import net.bullettrain.xenopixelsmod.client.ui.runtime.UiRuntime;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.ui.studio.DmzGuiStudioAtlasScreen;
import net.bullettrain.xenopixelsmod.client.ui.studio.DmzGuiStudioScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class UiStudioCommands {
    private UiStudioCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenoui")
                .then(Commands.literal("studio")
                        .then(Commands.literal("chrome")
                                .then(Commands.literal("dmz").executes(ctx -> setChrome(ctx, false)))
                                .then(Commands.literal("atlas").executes(ctx -> setChrome(ctx, true)))
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Studio chrome: " + chromeName()
                                            + " (use /xenoui studio chrome dmz|atlas)"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            boolean atlas = XenoClientConfig.dmzStudioChromeAtlas;
                            mc.execute(() -> mc.setScreen(atlas
                                    ? new DmzGuiStudioAtlasScreen() : new DmzGuiStudioScreen()));
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Opening DMZ GUI Studio (" + chromeName()
                                    + " chrome) — overlay stays off until HUD ON"), false);
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            UiRuntime.reload();
                            String err = UiRuntime.lastError();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    err.isBlank() ? "UI packs reloaded from config/xenopixelsmod/ui/"
                                            : "UI reload: " + err), false);
                            return 1;
                        }))
                .then(Commands.literal("enable")
                        .then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean on = BoolArgumentType.getBool(ctx, "on");
                                    UiRuntime.setEnabled(on);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            on ? "Pack HUD on (F1 still hides it)"
                                                    : "Pack HUD off — existing Xeno HUD unchanged"), false);
                                    return 1;
                                })))
                .then(Commands.literal("hud")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    UiRuntime.setHudId(id);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Active pack HUD: " + id), false);
                                    return 1;
                                })))
                .then(Commands.literal("screen")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    UiActions.open(id);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Opening document " + id), false);
                                    return 1;
                                }))));
    }

    private static String chromeName() {
        return XenoClientConfig.dmzStudioChromeAtlas ? "atlas" : "dmz";
    }

    /**
     * Switches which chrome the studio opens with and saves the choice.
     *
     * <p>Both screens stay available; this only picks the default one. The DMZ screen ships as
     * that default because it is the one proven in game.
     */
    private static int setChrome(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                 boolean atlas) {
        XenoClientConfig.dmzStudioChromeAtlas = atlas;
        XenoClientConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Studio chrome set to " + chromeName() + "; reopen with /xenoui studio"), false);
        return 1;
    }
}
