package net.bullettrain.xenopixelsmod.client.command;

import net.neoforged.fml.common.EventBusSubscriber;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.bullettrain.xenopixelsmod.client.config.DmzMenuMode;
import net.bullettrain.xenopixelsmod.client.config.XenoAuraConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoCooldownHudConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHotbarConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHudRenderer;
import net.bullettrain.xenopixelsmod.client.screen.HudSurfaces;
import net.bullettrain.xenopixelsmod.client.screen.XenoCooldownHudEditScreen;
import net.bullettrain.xenopixelsmod.client.screen.XenoHotbarEditScreen;
import net.bullettrain.xenopixelsmod.client.screen.XenoHudEditScreen;
import net.bullettrain.xenopixelsmod.client.screen.XenoElementsEditScreen;
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
    private static final String[] AURA_KEYS = {
            "enabled", "pivot", "gain", "max", "chargeheight", "chargewidth",
            "kiheight", "kiwidth", "ramp"
    };

    private XenoHudCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    /** Applies a renderer choice, saves it, and reports what was selected. */
    private static int setRenderer(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                   XenoHudRenderer renderer, String description) {
        XenoHudConfig.setRenderer(renderer);
        XenoHudConfig.save();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Xeno HUD renderer: " + description), false);
        return 1;
    }

    /** Applies a DMZ menu mode, saves it, and reports what the V key will now open. */
    private static int setMenuMode(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                   DmzMenuMode mode, String description) {
        XenoHudConfig.dmzMenuMode = mode;
        XenoHudConfig.save();
        ctx.getSource().sendSuccess(
                () -> Component.literal("DragonMineZ menus: " + description), false);
        return 1;
    }

    /** One aura knob, applied, saved and echoed back. */
    private static int setAura(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                               String name, double value, java.util.function.DoubleConsumer apply) {
        apply.accept(value);
        XenoAuraConfig.save();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Aura " + name + ": " + value), false);
        return 1;
    }

    /** Builds a `<name> <value>` branch for one aura knob. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> auraKnob(
            String name, double min, double max, java.util.function.DoubleConsumer apply) {
        return Commands.literal(name).then(Commands.argument("value",
                        DoubleArgumentType.doubleArg(min, max))
                .executes(ctx -> setAura(ctx, name,
                        DoubleArgumentType.getDouble(ctx, "value"), apply)));
    }

    private static int getAuraVisual(CommandSourceStack source, String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) {
            source.sendSuccess(() -> Component.literal(auraVisualStatus()), false);
            return 1;
        }
        String value = auraVisualValue(normalized);
        if (value == null) {
            source.sendFailure(Component.literal("Unknown aura key '" + key + "'. Try: "
                    + String.join(" ", AURA_KEYS)));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Aura " + normalized + " = " + value), false);
        return 1;
    }

    private static int setAuraVisual(CommandSourceStack source, String key, String raw) {
        String normalized = key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
        String value = raw == null ? "" : raw.trim();
        try {
            if (normalized.equals("enabled")) {
                Boolean enabled = parseAuraBoolean(value);
                if (enabled == null) throw new IllegalArgumentException("expected on/off or true/false");
                XenoAuraConfig.enabled = enabled;
            } else {
                double number = Double.parseDouble(value);
                if (!Double.isFinite(number)) throw new IllegalArgumentException("value must be finite");
                switch (normalized) {
                    case "pivot" -> XenoAuraConfig.powerPivot = bounded(number, 1.0, XenoAuraConfig.MAX_PIVOT);
                    case "gain" -> XenoAuraConfig.powerGain = bounded(number, 0.0, XenoAuraConfig.MAX_GAIN);
                    case "max" -> XenoAuraConfig.powerMax = bounded(number, 1.0, XenoAuraConfig.MAX_POWER);
                    case "chargeheight" -> XenoAuraConfig.chargeHeight = bounded(number, 0.0, XenoAuraConfig.MAX_CHARGE_HEIGHT);
                    case "chargewidth" -> XenoAuraConfig.chargeWidth = bounded(number, 0.0, XenoAuraConfig.MAX_CHARGE_WIDTH);
                    case "kiheight" -> XenoAuraConfig.kiChargeHeight = bounded(number, 0.0, XenoAuraConfig.MAX_CHARGE_HEIGHT);
                    case "kiwidth" -> XenoAuraConfig.kiChargeWidth = bounded(number, 0.0, XenoAuraConfig.MAX_CHARGE_WIDTH);
                    case "ramp" -> XenoAuraConfig.rampTicks = bounded(number, 1.0, XenoAuraConfig.MAX_RAMP);
                    default -> {
                        source.sendFailure(Component.literal("Unknown aura key '" + key + "'. Try: "
                                + String.join(" ", AURA_KEYS)));
                        return 0;
                    }
                }
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("Aura " + normalized + " expects a number, got '" + raw + "'"));
            return 0;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid aura " + normalized + ": " + e.getMessage()));
            return 0;
        }
        XenoAuraConfig.save();
        return getAuraVisual(source, normalized);
    }

    private static double bounded(double value, double min, double max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("expected " + min + ".." + max + ", got " + value);
        }
        return value;
    }

    private static Boolean parseAuraBoolean(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "true", "on", "yes", "1" -> Boolean.TRUE;
            case "false", "off", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static String auraVisualValue(String key) {
        return switch (key) {
            case "enabled" -> Boolean.toString(XenoAuraConfig.enabled);
            case "pivot" -> Double.toString(XenoAuraConfig.powerPivot);
            case "gain" -> Double.toString(XenoAuraConfig.powerGain);
            case "max" -> Double.toString(XenoAuraConfig.powerMax);
            case "chargeheight" -> Double.toString(XenoAuraConfig.chargeHeight);
            case "chargewidth" -> Double.toString(XenoAuraConfig.chargeWidth);
            case "kiheight" -> Double.toString(XenoAuraConfig.kiChargeHeight);
            case "kiwidth" -> Double.toString(XenoAuraConfig.kiChargeWidth);
            case "ramp" -> Double.toString(XenoAuraConfig.rampTicks);
            default -> null;
        };
    }

    private static String auraVisualStatus() {
        StringBuilder status = new StringBuilder("Aura visual settings:");
        for (String key : AURA_KEYS) {
            status.append(' ').append(key).append('=').append(auraVisualValue(key));
        }
        return status.toString();
    }

    private static int openPartsEditor(CommandSourceStack source) {
        if (!XenoClientConfig.hudEditEnabled) {
            source.sendFailure(Component.literal("HUD edit disabled in client config"));
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new XenoElementsEditScreen(mc.screen, HudSurfaces.PANEL)));
        return 1;
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
                                .executes(ctx -> setRenderer(ctx, XenoHudRenderer.LEGACY,
                                        "legacy")))
                        .then(Commands.literal("modern")
                                .executes(ctx -> setRenderer(ctx, XenoHudRenderer.MODERN,
                                        "modern (textured atlas)")))
                        .then(Commands.literal("modernunified")
                                .executes(ctx -> setRenderer(ctx, XenoHudRenderer.MODERN_UNIFIED,
                                        "modernunified (stats + combat cooldowns in one panel; the "
                                                + "cooldown HUD's own position and scale are "
                                                + "ignored)")))
                        .then(Commands.literal("bt3")
                                .executes(ctx -> setRenderer(ctx, XenoHudRenderer.BT3,
                                        "bt3 (Budokai Tenkaichi inspired)")))
                        // Kept so existing macros and muscle memory still work after the
                        // flat-rectangle LDLib spike was replaced by the textured renderer.
                        .then(Commands.literal("ldlib")
                                .executes(ctx -> setRenderer(ctx, XenoHudRenderer.MODERN,
                                        "modern (alias 'ldlib')")))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Xeno HUD renderer: " + XenoHudConfig.renderer.id()
                                            + " (usage: /xenohud renderer <"
                                            + XenoHudRenderer.usage() + ">)"), false);
                            return 1;
                        }))
                .then(Commands.literal("aura")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_RENDERER))
                        .then(Commands.literal("on").executes(ctx -> {
                            XenoAuraConfig.enabled = true;
                            XenoAuraConfig.save();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Aura scaling: on"), false);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            XenoAuraConfig.enabled = false;
                            XenoAuraConfig.save();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Aura scaling: off (DragonMineZ's own sizing)"), false);
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            XenoAuraConfig.reset();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Aura scaling reset"), false);
                            return 1;
                        }))
                        .then(auraKnob("pivot", 1.0, 1.0e12, v -> XenoAuraConfig.powerPivot = v))
                        .then(auraKnob("gain", 0.0, XenoAuraConfig.MAX_GAIN, v -> XenoAuraConfig.powerGain = v))
                        .then(auraKnob("max", 1.0, XenoAuraConfig.MAX_POWER, v -> XenoAuraConfig.powerMax = v))
                        .then(auraKnob("chargeheight", 0.0, XenoAuraConfig.MAX_CHARGE_HEIGHT, v -> XenoAuraConfig.chargeHeight = v))
                        .then(auraKnob("chargewidth", 0.0, XenoAuraConfig.MAX_CHARGE_WIDTH, v -> XenoAuraConfig.chargeWidth = v))
                        .then(auraKnob("kiheight", 0.0, XenoAuraConfig.MAX_CHARGE_HEIGHT, v -> XenoAuraConfig.kiChargeHeight = v))
                        .then(auraKnob("kiwidth", 0.0, XenoAuraConfig.MAX_CHARGE_WIDTH, v -> XenoAuraConfig.kiChargeWidth = v))
                        .then(auraKnob("ramp", 1.0, 200.0, v -> XenoAuraConfig.rampTicks = v))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Aura scaling: " + (XenoAuraConfig.enabled ? "on" : "off")
                                            + " (pivot " + XenoAuraConfig.powerPivot
                                            + ", gain " + XenoAuraConfig.powerGain
                                            + ", max " + XenoAuraConfig.powerMax
                                            + ", chargeheight " + XenoAuraConfig.chargeHeight
                                            + ", chargewidth " + XenoAuraConfig.chargeWidth
                                            + ", kiheight " + XenoAuraConfig.kiChargeHeight
                                            + ", kiwidth " + XenoAuraConfig.kiChargeWidth
                                            + ", ramp " + XenoAuraConfig.rampTicks + ")"), false);
                            return 1;
                        }))
                .then(Commands.literal("menus")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_RENDERER))
                        .then(Commands.literal("stock")
                                .executes(ctx -> setMenuMode(ctx, DmzMenuMode.STOCK,
                                        "stock (DragonMineZ's own screens, untouched)")))
                        .then(Commands.literal("theme")
                                .executes(ctx -> setMenuMode(ctx, DmzMenuMode.THEME,
                                        "theme (DMZ's real screens in Xeno chrome)")))
                        .then(Commands.literal("screen")
                                .executes(ctx -> setMenuMode(ctx, DmzMenuMode.SCREEN,
                                        "screen (our own stats page; the rest themed)")))
                        .then(Commands.literal("neon")
                                .executes(ctx -> setMenuMode(ctx, DmzMenuMode.NEON,
                                        "neon (the newer our-style stats page; the rest themed)")))
                        .then(Commands.literal("studio")
                                .executes(ctx -> setMenuMode(ctx, DmzMenuMode.STUDIO,
                                        "studio (authored dmz_menu documents replace assigned V-pages)")))
                        .then(Commands.literal("v3")
                                .executes(ctx -> setMenuMode(ctx, DmzMenuMode.SCREEN,
                                        "screen (XenoHUD V3; alias 'v3')")))
                        // The replacement screen was called 'bt3' before the theming existed.
                        .then(Commands.literal("bt3")
                                .executes(ctx -> setMenuMode(ctx, DmzMenuMode.SCREEN,
                                        "screen (alias 'bt3')")))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "DragonMineZ menus: " + XenoHudConfig.dmzMenuMode.id()
                                            + " (usage: /xenohud menus <"
                                            + DmzMenuMode.usage() + ">)"), false);
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
                .then(Commands.literal("parts")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_EDIT))
                        .then(Commands.literal("on").executes(ctx -> setParts(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setParts(ctx.getSource(), false)))
                        .then(Commands.literal("reset")
                                .executes(ctx -> {
                                    XenoHudConfig.resetParts();
                                    XenoHudConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "HUD element layout reset"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("edit")
                                .executes(ctx -> {
                                    if (!XenoClientConfig.hudEditEnabled) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "HUD edit disabled in client config"));
                                        return 0;
                                    }
                                    return openPartsEditor(ctx.getSource());
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "HUD elements: custom=" + XenoHudConfig.customLayout
                                            + "  " + XenoHudConfig.describeParts()
                                            + " (usage: /xenohud parts <on|off|reset|edit>)"), false);
                            return 1;
                        }))
                .then(Commands.literal("portrait")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_PORTRAIT))
                        .then(Commands.literal("mode")
                                .then(Commands.literal("skin")
                                        .executes(ctx -> setPortraitMode(ctx.getSource(),
                                                XenoHudConfig.PortraitMode.SKIN)))
                                .then(Commands.literal("character")
                                        .executes(ctx -> setPortraitMode(ctx.getSource(),
                                                XenoHudConfig.PortraitMode.CHARACTER))))
                        .then(Commands.literal("mask")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            XenoHudConfig.portraitMask =
                                                    BoolArgumentType.getBool(ctx, "enabled");
                                            XenoHudConfig.save();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Portrait circular mask: "
                                                            + (XenoHudConfig.portraitMask ? "on" : "off")), false);
                                            return 1;
                                        })))
                        .then(Commands.literal("ring")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            XenoHudConfig.transformRing =
                                                    BoolArgumentType.getBool(ctx, "enabled");
                                            XenoHudConfig.save();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Transform charge ring: "
                                                            + (XenoHudConfig.transformRing ? "circular" : "box")), false);
                                            return 1;
                                        })))
                        .then(Commands.literal("scale")
                                .then(Commands.argument("value",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(4, 120))
                                        .executes(ctx -> {
                                            XenoHudConfig.portraitScale = XenoHudConfig.clampPortraitScale(
                                                    com.mojang.brigadier.arguments.IntegerArgumentType
                                                            .getInteger(ctx, "value"));
                                            XenoHudConfig.save();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Portrait scale: " + XenoHudConfig.portraitScale), false);
                                            return 1;
                                        })))
                        .then(Commands.literal("offset")
                                .then(Commands.argument("value",
                                                com.mojang.brigadier.arguments.FloatArgumentType.floatArg(-2.0f, 2.0f))
                                        .executes(ctx -> {
                                            XenoHudConfig.portraitOffset = XenoHudConfig.clampPortraitOffset(
                                                    com.mojang.brigadier.arguments.FloatArgumentType
                                                            .getFloat(ctx, "value"));
                                            XenoHudConfig.save();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Portrait offset: " + XenoHudConfig.portraitOffset), false);
                                            return 1;
                                        })))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Portrait: mode=" + XenoHudConfig.portraitMode.name().toLowerCase()
                                            + " mask=" + XenoHudConfig.portraitMask
                                            + " ring=" + XenoHudConfig.transformRing
                                            + " scale=" + XenoHudConfig.portraitScale
                                            + " offset=" + XenoHudConfig.portraitOffset
                                            + " (usage: /xenohud portrait mode <skin|character>"
                                            + " | mask <true|false> | ring <true|false>"
                                            + " | scale <4..120> | offset <-2.0..2.0>)"), false);
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
                        .then(Commands.literal("style")
                                .then(Commands.literal("textured")
                                        .executes(ctx -> setMenuShell(ctx.getSource(), true)))
                                .then(Commands.literal("classic")
                                        .executes(ctx -> setMenuShell(ctx.getSource(), false)))
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Ki menu style: "
                                                    + (XenoHotbarConfig.xenoverseShell ? "textured" : "classic")
                                                    + " (usage: /xenohud techhud style <textured|classic>)"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("parts")
                                .then(Commands.literal("on")
                                        .executes(ctx -> setMenuParts(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setMenuParts(ctx.getSource(), false)))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> {
                                            XenoHotbarConfig.resetParts();
                                            XenoHotbarConfig.save();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Ki menu element layout reset"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("edit")
                                        .executes(ctx -> {
                                            if (!XenoClientConfig.hudEditEnabled) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "HUD edit disabled in client config"));
                                                return 0;
                                            }
                                            Minecraft mc = Minecraft.getInstance();
                                            mc.execute(() -> mc.setScreen(
                                                    new XenoElementsEditScreen(mc.screen, HudSurfaces.KI_MENU)));
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Ki menu elements: custom=" + XenoHotbarConfig.customLayout
                                                    + "  " + XenoHotbarConfig.describeParts()
                                                    + " (usage: /xenohud techhud parts <on|off|reset|edit>)"), false);
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
                        .then(Commands.literal("text")
                                .then(Commands.literal("on")
                                        .executes(ctx -> setTextLayout(ctx.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setTextLayout(ctx.getSource(), false)))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> {
                                            XenoCooldownHudConfig.resetTextLayout();
                                            XenoCooldownHudConfig.save();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Chip text layout reset to centred defaults"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("edit")
                                        .executes(ctx -> {
                                            if (!XenoClientConfig.hudEditEnabled) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "HUD edit disabled in client config"));
                                                return 0;
                                            }
                                            Minecraft mc = Minecraft.getInstance();
                                            mc.execute(() -> mc.setScreen(
                                                    new XenoElementsEditScreen(mc.screen, HudSurfaces.CHIPS)));
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Chip text: custom=" + XenoCooldownHudConfig.textCustomLayout
                                                    + " scale=" + XenoCooldownHudConfig.textScale
                                                    + " " + XenoCooldownHudConfig.describeParts()
                                                    + " (usage: /xenohud cd text <on|off|reset|edit>)"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("textscale")
                                .then(Commands.argument("value",
                                                com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.35f, 1.0f))
                                        .executes(ctx -> {
                                            XenoCooldownHudConfig.textScale =
                                                    XenoCooldownHudConfig.clampTextScale(
                                                            com.mojang.brigadier.arguments.FloatArgumentType
                                                                    .getFloat(ctx, "value"));
                                            XenoCooldownHudConfig.save();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Cooldown chip text scale: "
                                                            + XenoCooldownHudConfig.textScale), false);
                                            return 1;
                                        }))
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Cooldown chip text scale: " + XenoCooldownHudConfig.textScale
                                                    + " (usage: /xenohud cd textscale <0.35..1.0>)"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("style")
                                .then(Commands.literal("textured")
                                        .executes(ctx -> setShell(ctx.getSource(), true)))
                                .then(Commands.literal("classic")
                                        .executes(ctx -> setShell(ctx.getSource(), false)))
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Cooldown chip style: "
                                                    + (XenoCooldownHudConfig.xenoverseShell ? "textured" : "classic")
                                                    + " (usage: /xenohud cd style <textured|classic>)"), false);
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
                .then(Commands.literal("v3")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_EDIT))
                        .then(Commands.literal("edit")
                                .executes(ctx -> {
                                    if (!XenoClientConfig.hudEditEnabled) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "HUD edit disabled in client config"));
                                        return 0;
                                    }
                                    XenoHudConfig.dmzMenuMode = DmzMenuMode.SCREEN;
                                    XenoHudConfig.save();
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.execute(() -> mc.setScreen(new XenoElementsEditScreen(
                                            mc.screen, HudSurfaces.DMZ_SCREEN)));
                                    return 1;
                                }))
                        .then(Commands.literal("reset")
                                .executes(ctx -> {
                                    net.bullettrain.xenopixelsmod.client.config.XenoDmzScreenConfig.resetParts();
                                    net.bullettrain.xenopixelsmod.client.config.XenoDmzScreenConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "XenoHUD V3 menu layout reset"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Usage: /xenohud v3 <edit|reset>"), false);
                            return 1;
                        }))
                .then(Commands.literal("neon")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_EDIT))
                        .then(Commands.literal("edit")
                                .executes(ctx -> {
                                    if (!XenoClientConfig.hudEditEnabled) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "HUD edit disabled in client config"));
                                        return 0;
                                    }
                                    XenoHudConfig.dmzMenuMode = DmzMenuMode.NEON;
                                    XenoHudConfig.save();
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.execute(() -> mc.setScreen(new XenoElementsEditScreen(
                                            mc.screen, HudSurfaces.DMZ_NEON)));
                                    return 1;
                                }))
                        .then(Commands.literal("reset")
                                .executes(ctx -> {
                                    net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig.resetParts();
                                    net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Neon V3 menu layout reset"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Usage: /xenohud neon <edit|reset>"), false);
                            return 1;
                        }))
                .then(Commands.literal("master")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_EDIT))
                        .then(Commands.literal("edit")
                                .executes(ctx -> {
                                    if (!XenoClientConfig.hudEditEnabled) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "HUD edit disabled in client config"));
                                        return 0;
                                    }
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.execute(() -> mc.setScreen(
                                            new XenoElementsEditScreen(mc.screen, HudSurfaces.MASTER)));
                                    return 1;
                                }))
                        .then(Commands.literal("reset")
                                .executes(ctx -> {
                                    net.bullettrain.xenopixelsmod.client.config.XenoMasterMenuConfig.resetParts();
                                    net.bullettrain.xenopixelsmod.client.config.XenoMasterMenuConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Master menu layout reset"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Usage: /xenohud master <edit|reset>"), false);
                            return 1;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(
                            () -> Component.literal(
                                    "Usage: /xenohud <toggle|show|hide|edit|reset|renderer|menus|techrenderer|party|techhud|cd|v3|master>"),
                            false);
                    return 1;
                }));

        dispatcher.register(Commands.literal("xenoparts")
                .requires(XenoPermissions.require(XenoPermissions.XENOHUD_EDIT))
                .then(Commands.literal("global")
                        .then(Commands.literal("push")
                                .requires(XenoPermissions.require(XenoPermissions.XENOPARTS_GLOBAL))
                                .executes(ctx -> {
                                    net.bullettrain.xenopixelsmod.network.HudPartsNetwork.pushFromClient(
                                            net.bullettrain.xenopixelsmod.client.hud.HudPartsClient.snapshotJson());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Publishing this client's HUD/menu parts as the server global…"),
                                            false);
                                    return 1;
                                }))
                        .then(Commands.literal("clear")
                                .requires(XenoPermissions.require(XenoPermissions.XENOPARTS_GLOBAL))
                                .executes(ctx -> {
                                    net.bullettrain.xenopixelsmod.network.HudPartsNetwork.clearFromClient();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Clearing the server global HUD parts…"), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Usage: /xenoparts global <push|clear>  — push sends YOUR current layout to every joiner"),
                                    false);
                            return 1;
                        }))
                .executes(ctx -> openPartsEditor(ctx.getSource())));

        dispatcher.register(Commands.literal("xenoaura")
                .then(Commands.literal("get")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_RENDERER))
                        .executes(ctx -> getAuraVisual(ctx.getSource(), ""))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                AURA_KEYS, builder))
                                .executes(ctx -> getAuraVisual(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "key")))))
                .then(Commands.literal("set")
                        .requires(XenoPermissions.require(XenoPermissions.XENOHUD_RENDERER))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        net.minecraft.commands.SharedSuggestionProvider.suggest(
                                                AURA_KEYS, builder))
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> setAuraVisual(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                StringArgumentType.getString(ctx, "value")))))));

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
                                            + " deleteConfirmMs=" + XenoClientConfig.deleteConfirmMs
                                            + " shake=" + XenoClientConfig.bt3ScreenShake
                                            + " shakeStr=" + XenoClientConfig.bt3ScreenShakeStrength
                                            + " dmzShake=" + XenoClientConfig.dmzCameraShake
                                            + " combat=" + XenoClientConfig.bt3CombatClient
                                            + " vanish=" + XenoClientConfig.bt3VanishClient
                                            + " chase=" + XenoClientConfig.bt3ChaseDashClient
                                            + " backstep=" + XenoClientConfig.bt3BackstepClient
                                            + " charge=" + XenoClientConfig.bt3ChargeAttackClient
                                            + " dragon=" + XenoClientConfig.bt3DragonDashClient
                                            + " glow=" + XenoClientConfig.bt3ChargeGlow
                                            + " anims=" + XenoClientConfig.bt3CombatAnims
                                            + " headfollow=" + XenoClientConfig.bt3MashHeadFollow
                                            + " chain=" + XenoClientConfig.bt3KickChainAnims
                                            + " particles=" + XenoClientConfig.bt3CombatParticles
                                            + " afterimage=" + XenoClientConfig.bt3Afterimage
                                            + " techChatHide=" + XenoClientConfig.techniqueHotbarHideInChat
                                            + " sfx=" + XenoClientConfig.bt3CombatSfx
                                            + " surge=" + XenoClientConfig.beamSurgeClient
                                            + " surgeDebug=" + XenoClientConfig.beamSurgeDebug
                                            + " barNumbers=" + XenoClientConfig.hudBarNumbers
                                            + " compactNumbers=" + XenoClientConfig.hudCompactNumbers
                                            + " reducedMotion=" + XenoClientConfig.hudReducedMotion
                                            + " sableCull=" + XenoClientConfig.sableContraptionCullClient
                                            + " lockThrough=" + XenoClientConfig.lockOnThroughBlocks
                                            + " supercounter=" + XenoClientConfig.bt3SuperCounterClient
                                            + " guard=" + XenoClientConfig.bt3GuardClient), false);
                            return 1;
                        }))
                .then(Commands.literal("set")
                        .requires(XenoPermissions.require(XenoPermissions.XENOCLIENT_SET))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> setClient(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "key"),
                                                StringArgumentType.getString(ctx, "value"))))))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoclient <reload|status|set <key> <value>>\n"
                                    + "bools: hud techbar cooldownhud party surge shake dmzshake sablecull lockthrough supercounter guard\n"
                                    + "nums: deleteconfirm <ms>  shakestrength <n>"),
                            false);
                    return 1;
                }));
    }

    private static int setClient(CommandSourceStack source, String key, String raw) {
        String k = key.toLowerCase();
        if (k.equals("deleteconfirm") || k.equals("deleteconfirmms")) {
            try {
                int ms = Integer.parseInt(raw.trim());
                XenoClientConfig.deleteConfirmMs = Math.max(0, Math.min(30_000, ms));
                XenoClientConfig.save();
                source.sendSuccess(() -> Component.literal(
                        "Set client deleteconfirm = " + XenoClientConfig.deleteConfirmMs
                                + (XenoClientConfig.deleteConfirmMs == 0 ? " (instant)" : " ms")), false);
                return 1;
            } catch (NumberFormatException e) {
                source.sendFailure(Component.literal("deleteconfirm needs milliseconds (0 = instant)"));
                return 0;
            }
        }
        if (k.equals("shakestrength") || k.equals("shakestr")) {
            try {
                float n = Float.parseFloat(raw.trim());
                if (!Float.isFinite(n)) {
                    source.sendFailure(Component.literal("shakestrength must be finite"));
                    return 0;
                }
                XenoClientConfig.bt3ScreenShakeStrength = Math.max(0f, Math.min(4f, n));
                XenoClientConfig.save();
                source.sendSuccess(() -> Component.literal(
                        "Set client shakestrength = " + XenoClientConfig.bt3ScreenShakeStrength), false);
                return 1;
            } catch (NumberFormatException e) {
                source.sendFailure(Component.literal("shakestrength needs a number"));
                return 0;
            }
        }
        Boolean parsed = parseBool(raw);
        if (parsed == null) {
            source.sendFailure(Component.literal("Expected true/false (or a number for deleteconfirm/shakestrength)"));
            return 0;
        }
        return setFlag(source, k, parsed);
    }

    private static Boolean parseBool(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase()) {
            case "true", "on", "yes", "1" -> Boolean.TRUE;
            case "false", "off", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
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
            case "headfollow", "mashhead" -> XenoClientConfig.bt3MashHeadFollow = value;
            case "chain", "kickchain" -> XenoClientConfig.bt3KickChainAnims = value;
            case "particles", "fx" -> XenoClientConfig.bt3CombatParticles = value;
            case "afterimage" -> {
                XenoClientConfig.bt3Afterimage = value;
                if (!value) net.bullettrain.xenopixelsmod.client.combat.AfterimageGhostRenderer.clear();
            }
            case "cooldownhud", "cdhud" -> XenoClientConfig.cooldownHudEnabled = value;
            case "party" -> XenoClientConfig.partyHudEnabled = value;
            case "techchathide", "hideintechchat" -> XenoClientConfig.techniqueHotbarHideInChat = value;
            case "surge", "beamsurge" -> XenoClientConfig.beamSurgeClient = value;
            case "surgedebug", "beamsurgedebug" -> XenoClientConfig.beamSurgeDebug = value;
            case "barnumbers", "hudbarnumbers", "numbers" -> XenoClientConfig.hudBarNumbers = value;
            case "compactnumbers", "hudcompactnumbers" -> XenoClientConfig.hudCompactNumbers = value;
            case "reducedmotion", "hudreducedmotion", "motion" ->
                    XenoClientConfig.hudReducedMotion = value;
            case "shake", "screenshake" -> XenoClientConfig.bt3ScreenShake = value;
            case "dmzshake", "dmzshakethird", "dmzshakefly", "dmz3pshake", "thirdpersonshake",
                    "dmzflyshake", "flightshake" ->
                    XenoClientConfig.dmzCameraShake = value;
            case "sablecull", "cull", "sablecontraptioncull" ->
                    XenoClientConfig.sableContraptionCullClient = value;
            case "lockthrough", "lockonthrough", "lockthroughblocks" ->
                    XenoClientConfig.lockOnThroughBlocks = value;
            case "supercounter", "counter" -> XenoClientConfig.bt3SuperCounterClient = value;
            case "guard", "block" -> XenoClientConfig.bt3GuardClient = value;
            case "hitboxes", "combatboxes" -> XenoClientConfig.bt3CombatHitboxes = value;
            default -> {
                source.sendFailure(Component.literal(
                        "Unknown key. Try: hud techbar combat vanish chase backstep charge dragon glow sfx anims chain particles afterimage cooldownhud party techchathide surge surgedebug barnumbers compactnumbers reducedmotion shake dmzshake lockthrough supercounter guard"));
                return 0;
            }
        }
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal("Set client " + k + " = " + value), false);
        return 1;
    }

    private static int setMenuShell(CommandSourceStack source, boolean textured) {
        XenoHotbarConfig.xenoverseShell = textured;
        XenoHotbarConfig.save();
        source.sendSuccess(() -> Component.literal("Ki menu style: "
                + (textured ? "textured (Xenoverse panel)" : "classic")), false);
        return 1;
    }

    private static int setMenuParts(CommandSourceStack source, boolean custom) {
        XenoHotbarConfig.customLayout = custom;
        XenoHotbarConfig.save();
        source.sendSuccess(() -> Component.literal("Ki menu elements: "
                + (custom ? "custom (movable)" : "shipped default")), false);
        return 1;
    }

    private static int setParts(CommandSourceStack source, boolean custom) {
        XenoHudConfig.customLayout = custom;
        XenoHudConfig.save();
        source.sendSuccess(() -> Component.literal("HUD element layout: "
                + (custom ? "custom (movable)" : "shipped default")), false);
        return 1;
    }

    private static int setTextLayout(CommandSourceStack source, boolean custom) {
        XenoCooldownHudConfig.textCustomLayout = custom;
        XenoCooldownHudConfig.save();
        source.sendSuccess(() -> Component.literal("Chip text layout: "
                + (custom ? "custom (movable/resizable)" : "centred default")), false);
        return 1;
    }

    private static int setShell(CommandSourceStack source, boolean textured) {
        XenoCooldownHudConfig.xenoverseShell = textured;
        XenoCooldownHudConfig.save();
        source.sendSuccess(() -> Component.literal(
                "Cooldown chip style: " + (textured ? "textured (Xenoverse shell)" : "classic")), false);
        return 1;
    }

    private static int setPortraitMode(CommandSourceStack source, XenoHudConfig.PortraitMode mode) {
        XenoHudConfig.portraitMode = mode;
        XenoHudConfig.save();
        source.sendSuccess(() -> Component.literal(mode == XenoHudConfig.PortraitMode.CHARACTER
                ? "Portrait: DragonMineZ character"
                : "Portrait: player skin"), false);
        return 1;
    }
}
