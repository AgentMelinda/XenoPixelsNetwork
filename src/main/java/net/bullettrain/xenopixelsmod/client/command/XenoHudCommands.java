package net.bullettrain.xenopixelsmod.client.command;

import net.neoforged.fml.common.EventBusSubscriber;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoCooldownHudConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHotbarConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.bullettrain.xenopixelsmod.client.screen.XenoCooldownHudEditScreen;
import net.bullettrain.xenopixelsmod.client.screen.XenoHotbarEditScreen;
import net.bullettrain.xenopixelsmod.client.screen.XenoHudEditScreen;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoHudCommands {
    private XenoHudCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenohud")
                .then(Commands.literal("toggle")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_TOGGLE))
                        .executes(ctx -> {
                            XenoHudConfig.toggleVisible();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Xeno HUD " + (XenoHudConfig.visible ? "shown" : "hidden")),
                                    false);
                            return 1;
                        }))
                .then(Commands.literal("show")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_SHOW))
                        .executes(ctx -> {
                            XenoHudConfig.visible = true;
                            XenoHudConfig.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("Xeno HUD shown"), false);
                            return 1;
                        }))
                .then(Commands.literal("hide")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_HIDE))
                        .executes(ctx -> {
                            XenoHudConfig.visible = false;
                            XenoHudConfig.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("Xeno HUD hidden"), false);
                            return 1;
                        }))
                .then(Commands.literal("edit")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_EDIT))
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
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_RESET))
                        .executes(ctx -> {
                            XenoHudConfig.reset();
                            ctx.getSource().sendSuccess(() -> Component.literal("Xeno HUD reset"), false);
                            return 1;
                        }))
                .then(Commands.literal("renderer")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_RENDERER))
                        .then(Commands.literal("legacy")
                                .executes(ctx -> {
                                    XenoHudConfig.legacyHudRenderer = true;
                                    XenoHudConfig.unifiedHudRenderer = false;
                                    XenoHudConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Xeno HUD renderer: legacy"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("modern")
                                .executes(ctx -> {
                                    XenoHudConfig.legacyHudRenderer = false;
                                    XenoHudConfig.unifiedHudRenderer = false;
                                    XenoHudConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Xeno HUD renderer: modern (textured atlas)"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("modernunified")
                                .executes(ctx -> {
                                    XenoHudConfig.legacyHudRenderer = false;
                                    XenoHudConfig.unifiedHudRenderer = true;
                                    XenoHudConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Xeno HUD renderer: modernunified (stats + combat "
                                                    + "cooldowns in one panel; the cooldown HUD's "
                                                    + "own position and scale are ignored)"), false);
                                    return 1;
                                }))
                        // Kept so existing macros and muscle memory still work after the
                        // flat-rectangle LDLib spike was replaced by the textured renderer.
                        .then(Commands.literal("ldlib")
                                .executes(ctx -> {
                                    XenoHudConfig.legacyHudRenderer = false;
                                    XenoHudConfig.unifiedHudRenderer = false;
                                    XenoHudConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Xeno HUD renderer: modern (alias 'ldlib')"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            String current = XenoHudConfig.legacyHudRenderer ? "legacy"
                                    : XenoHudConfig.unifiedHudRenderer ? "modernunified" : "modern";
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Xeno HUD renderer: " + current
                                            + " (usage: /xenohud renderer <legacy|modern|modernunified>)"), false);
                            return 1;
                        }))
                .then(Commands.literal("techrenderer")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_TECHRENDERER))
                        .then(Commands.literal("legacy")
                                .executes(ctx -> {
                                    XenoHudConfig.legacyTechniqueRenderer = true;
                                    XenoHudConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Xeno technique hotbar renderer: legacy"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("ldlib")
                                .executes(ctx -> {
                                    XenoHudConfig.legacyTechniqueRenderer = false;
                                    XenoHudConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Xeno technique hotbar renderer: ldlib (Phase 6 spike, WIP)"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Xeno technique hotbar renderer: "
                                            + (XenoHudConfig.legacyTechniqueRenderer ? "legacy" : "ldlib")
                                            + " (usage: /xenohud techrenderer <legacy|ldlib>)"), false);
                            return 1;
                        }))
                .then(Commands.literal("party")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_PARTY))
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
                                .requires(XenoPermissions.require(XenoPermissions.XENOHUD_TECHHUD_EDIT))
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
                                .requires(XenoPermissions.require(XenoPermissions.XENOHUD_TECHHUD_RESET))
                                .executes(ctx -> {
                                    XenoHotbarConfig.reset();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Xeno technique HUD layout reset"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Usage: /xenohud techhud <edit|reset>"), false);
                            return 1;
                        }))
                .then(Commands.literal("cd")
                        .then(Commands.literal("edit")
                                .requires(XenoPermissions.require(XenoPermissions.XENOHUD_CD_EDIT))
                                .executes(ctx -> {
                                    if (!XenoClientConfig.hudEditEnabled) {
                                        ctx.getSource().sendFailure(Component.literal("HUD edit disabled in client config"));
                                        return 0;
                                    }
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.execute(() -> mc.setScreen(new XenoCooldownHudEditScreen(mc.screen)));
                                    return 1;
                                }))
                        .then(Commands.literal("show")
                                .requires(XenoPermissions.require(XenoPermissions.XENOHUD_CD_SHOW))
                                .executes(ctx -> {
                                    XenoCooldownHudConfig.visible = true;
                                    XenoCooldownHudConfig.save();
                                    XenoClientConfig.cooldownHudEnabled = true;
                                    XenoClientConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Cooldown HUD shown"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("hide")
                                .requires(XenoPermissions.require(XenoPermissions.XENOHUD_CD_HIDE))
                                .executes(ctx -> {
                                    XenoCooldownHudConfig.visible = false;
                                    XenoCooldownHudConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Cooldown HUD hidden"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("reset")
                                .requires(XenoPermissions.require(XenoPermissions.XENOHUD_CD_RESET))
                                .executes(ctx -> {
                                    XenoCooldownHudConfig.reset();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Cooldown HUD layout reset"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("shape")
                                .requires(XenoPermissions.require(XenoPermissions.XENOHUD_CD_SHAPE))
                                .then(Commands.literal("square")
                                        .executes(ctx -> {
                                            XenoCooldownHudConfig.squareShape = true;
                                            XenoCooldownHudConfig.save();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Combat HUD shape: square (rectangles)"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("para")
                                        .executes(ctx -> {
                                            XenoCooldownHudConfig.squareShape = false;
                                            XenoCooldownHudConfig.save();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Combat HUD shape: parallelogram (slanted)"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("toggle")
                                        .executes(ctx -> {
                                            XenoCooldownHudConfig.toggleSquareShape();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Combat HUD shape: " + (XenoCooldownHudConfig.squareShape
                                                            ? "square" : "parallelogram")), false);
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Combat HUD shape: " + (XenoCooldownHudConfig.squareShape
                                                    ? "square" : "parallelogram")
                                                    + "  (usage: /xenohud cd shape <square|para|toggle>)"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Usage: /xenohud cd <edit|show|hide|reset|shape>"), false);
                            return 1;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(
                            () -> Component.literal(
                                    "Usage: /xenohud <toggle|show|hide|edit|reset|renderer|techrenderer|party|techhud|cd>"),
                            false);
                    return 1;
                }));

        dispatcher.register(Commands.literal("xenoclient")
                .then(Commands.literal("reload")
                        .requires(XenoPermissions.require(XenoPermissions.XENOCLIENT_RELOAD))
                        .executes(ctx -> {
                            XenoClientConfig.load();
                            XenoHudConfig.load();
                            XenoHotbarConfig.load();
                            XenoCooldownHudConfig.load();
                            ctx.getSource().sendSuccess(() -> Component.literal("XenoPixels client config reloaded"), false);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.XENOCLIENT_STATUS))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "hud=" + XenoClientConfig.xenoHudEnabled
                                            + " techbar=" + XenoClientConfig.techniqueHotbarEnabled
                                            + " cooldownHud=" + XenoClientConfig.cooldownHudEnabled
                                            + " party=" + XenoClientConfig.partyHudEnabled
                                            + " titleBtn=" + XenoClientConfig.titleScreenButton
                                            + " pauseBtn=" + XenoClientConfig.pauseScreenButton
                                            + " menu=" + XenoClientConfig.xenoMenuEnabled
                                            + " combat=" + XenoClientConfig.bt3CombatClient
                                            + " vanish=" + XenoClientConfig.bt3VanishClient
                                            + " chase=" + XenoClientConfig.bt3ChaseDashClient
                                            + " backstep=" + XenoClientConfig.bt3BackstepClient
                                            + " charge=" + XenoClientConfig.bt3ChargeAttackClient
                                            + " dragon=" + XenoClientConfig.bt3DragonDashClient
                                            + " glow=" + XenoClientConfig.bt3ChargeGlow
                                            + " anims=" + XenoClientConfig.bt3CombatAnims
                                            + " chain=" + XenoClientConfig.bt3KickChainAnims
                                            + " particles=" + XenoClientConfig.bt3CombatParticles
                                            + " afterimage=" + XenoClientConfig.bt3Afterimage
                                            + " techChatHide=" + XenoClientConfig.techniqueHotbarHideInChat
                                            + " sfx=" + XenoClientConfig.bt3CombatSfx), false);
                            return 1;
                        }))
                .then(Commands.literal("set")
                        .requires(XenoPermissions.require(XenoPermissions.XENOCLIENT_SET))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setFlag(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                BoolArgumentType.getBool(ctx, "value"))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoclient <reload|status|set <key> <true|false>>\n"
                                    + "keys: hud techbar cooldownhud party title pause menu content join edit senzu "
                                    + "combat combo vanish chase backstep charge dragon glow sfx anims chain particles afterimage techchathide"),
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
            case "anims", "animations" -> XenoClientConfig.bt3CombatAnims = value;
            case "chain", "kickchain" -> XenoClientConfig.bt3KickChainAnims = value;
            case "particles", "fx" -> XenoClientConfig.bt3CombatParticles = value;
            case "afterimage" -> XenoClientConfig.bt3Afterimage = value;
            case "cooldownhud", "cdhud" -> XenoClientConfig.cooldownHudEnabled = value;
            case "party" -> XenoClientConfig.partyHudEnabled = value;
            case "techchathide", "hideintechchat" -> XenoClientConfig.techniqueHotbarHideInChat = value;
            default -> {
                source.sendFailure(Component.literal(
                        "Unknown key. Try: hud techbar combat vanish chase backstep charge dragon glow sfx anims chain particles afterimage cooldownhud party techchathide"));
                return 0;
            }
        }
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal("Set client " + k + " = " + value), false);
        return 1;
    }
}
