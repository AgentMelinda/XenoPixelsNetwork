package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHotbarConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.bullettrain.xenopixelsmod.client.screen.XenoHotbarEditScreen;
import net.bullettrain.xenopixelsmod.client.screen.XenoHudEditScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoHudCommands {
    private XenoHudCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenohud")
                .then(Commands.literal("toggle")
                        .executes(ctx -> {
                            XenoHudConfig.toggleVisible();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Xeno HUD " + (XenoHudConfig.visible ? "shown" : "hidden")),
                                    false);
                            return 1;
                        }))
                .then(Commands.literal("show")
                        .executes(ctx -> {
                            XenoHudConfig.visible = true;
                            XenoHudConfig.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("Xeno HUD shown"), false);
                            return 1;
                        }))
                .then(Commands.literal("hide")
                        .executes(ctx -> {
                            XenoHudConfig.visible = false;
                            XenoHudConfig.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("Xeno HUD hidden"), false);
                            return 1;
                        }))
                .then(Commands.literal("edit")
                        .executes(ctx -> {
                            if (!XenoClientConfig.hudEditEnabled) {
                                ctx.getSource().sendFailure(Component.literal("HUD edit disabled in client config"));
                                return 0;
                            }
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> mc.setScreen(new XenoHudEditScreen(mc.screen)));
                            return 1;
                        }))
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            XenoHudConfig.reset();
                            ctx.getSource().sendSuccess(() -> Component.literal("Xeno HUD reset"), false);
                            return 1;
                        }))
                .then(Commands.literal("renderer")
                        .then(Commands.literal("legacy")
                                .executes(ctx -> {
                                    XenoHudConfig.legacyHudRenderer = true;
                                    XenoHudConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Xeno HUD renderer: legacy"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("ldlib")
                                .executes(ctx -> {
                                    XenoHudConfig.legacyHudRenderer = false;
                                    XenoHudConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Xeno HUD renderer: ldlib (Phase 4 spike, WIP)"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Xeno HUD renderer: " + (XenoHudConfig.legacyHudRenderer ? "legacy" : "ldlib")
                                            + " (usage: /xenohud renderer <legacy|ldlib>)"), false);
                            return 1;
                        }))
                .then(Commands.literal("techrenderer")
                        .then(Commands.literal("legacy")
                                .executes(ctx -> {
                                    XenoHudConfig.legacyTechniqueRenderer = true;
                                    XenoHudConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Xeno technique hotbar renderer: legacy"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("ldlib")
                                .executes(ctx -> {
                                    XenoHudConfig.legacyTechniqueRenderer = false;
                                    XenoHudConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Xeno technique hotbar renderer: ldlib (Phase 6 spike, WIP)"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Xeno technique hotbar renderer: " + (XenoHudConfig.legacyTechniqueRenderer ? "legacy" : "ldlib")
                                            + " (usage: /xenohud techrenderer <legacy|ldlib>)"), false);
                            return 1;
                        }))
                .then(Commands.literal("party")
                        .then(Commands.literal("show")
                                .executes(ctx -> {
                                    XenoClientConfig.partyHudEnabled = true;
                                    XenoClientConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Xeno party HUD shown"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("hide")
                                .executes(ctx -> {
                                    XenoClientConfig.partyHudEnabled = false;
                                    XenoClientConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Xeno party HUD hidden"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Xeno party HUD: " + (XenoClientConfig.partyHudEnabled ? "shown" : "hidden")
                                            + " (usage: /xenohud party <show|hide>)"), false);
                            return 1;
                        }))
                .then(Commands.literal("techhud")
                        .then(Commands.literal("edit")
                                .executes(ctx -> {
                                    if (!XenoClientConfig.hudEditEnabled) {
                                        ctx.getSource().sendFailure(Component.literal("HUD edit disabled in client config"));
                                        return 0;
                                    }
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.execute(() -> mc.setScreen(new XenoHotbarEditScreen(mc.screen)));
                                    return 1;
                                }))
                        .then(Commands.literal("reset")
                                .executes(ctx -> {
                                    XenoHotbarConfig.reset();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Xeno technique HUD layout reset"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Usage: /xenohud techhud <edit|reset>"), false);
                            return 1;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("Usage: /xenohud <toggle|show|hide|edit|reset|renderer|techrenderer|party|techhud>"),
                            false);
                    return 1;
                }));

        dispatcher.register(Commands.literal("xenoclient")
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            XenoClientConfig.load();
                            XenoHudConfig.load();
                            XenoHotbarConfig.load();
                            ctx.getSource().sendSuccess(() -> Component.literal("XenoPixels client config reloaded"), false);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "hud=" + XenoClientConfig.xenoHudEnabled
                                            + " techbar=" + XenoClientConfig.techniqueHotbarEnabled
                                            + " titleBtn=" + XenoClientConfig.titleScreenButton
                                            + " pauseBtn=" + XenoClientConfig.pauseScreenButton
                                            + " menu=" + XenoClientConfig.xenoMenuEnabled
                                            + " combat=" + XenoClientConfig.bt3CombatClient
                                            + " vanish=" + XenoClientConfig.bt3VanishClient
                                            + " chase=" + XenoClientConfig.bt3ChaseDashClient
                                            + " backstep=" + XenoClientConfig.bt3BackstepClient
                                            + " charge=" + XenoClientConfig.bt3ChargeAttackClient
                                            + " dragon=" + XenoClientConfig.bt3DragonDashClient
                                            + " glow=" + XenoClientConfig.bt3ChargeGlow), false);
                            return 1;
                        }))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setFlag(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                BoolArgumentType.getBool(ctx, "value"))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoclient <reload|status|set <key> <true|false>>\n"
                                    + "keys: hud, techbar, title, pause, menu, content, join, edit, senzu, combat, combo, vanish, chase, backstep, charge, dragon, glow, sfx"),
                            false);
                    return 1;
                }));
    }

    private static int setFlag(CommandSourceStack source, String key, boolean value) {
        String k = key.toLowerCase();
        switch (k) {
            case "hud" -> XenoClientConfig.xenoHudEnabled = value;
            case "techbar", "technique", "kihotbar" -> XenoClientConfig.techniqueHotbarEnabled = value;
            case "title" -> XenoClientConfig.titleScreenButton = value;
            case "pause" -> XenoClientConfig.pauseScreenButton = value;
            case "menu" -> XenoClientConfig.xenoMenuEnabled = value;
            case "content" -> XenoClientConfig.contentScreensEnabled = value;
            case "join" -> XenoClientConfig.joinServerButton = value;
            case "edit" -> XenoClientConfig.hudEditEnabled = value;
            case "senzu" -> XenoClientConfig.senzuCooldownMessages = value;
            case "combat" -> XenoClientConfig.bt3CombatClient = value;
            case "combo" -> XenoClientConfig.bt3ComboClient = value;
            case "vanish" -> XenoClientConfig.bt3VanishClient = value;
            case "chase" -> XenoClientConfig.bt3ChaseDashClient = value;
            case "backstep" -> XenoClientConfig.bt3BackstepClient = value;
            case "charge" -> XenoClientConfig.bt3ChargeAttackClient = value;
            case "dragon" -> XenoClientConfig.bt3DragonDashClient = value;
            case "glow" -> XenoClientConfig.bt3ChargeGlow = value;
            case "sfx" -> XenoClientConfig.bt3CombatSfx = value;
            default -> {
                source.sendFailure(Component.literal("Unknown key: " + key));
                return 0;
            }
        }
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal("Set client " + k + " = " + value), false);
        return 1;
    }
}
