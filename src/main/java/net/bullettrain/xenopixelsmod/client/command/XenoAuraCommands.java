package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.fx.aura.AuraStyle;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /xenoaura dmz|hd|both}: which aura this client draws. DragonMineZ's own aura (dmz), the
 * generated HD aura (hd), or both at once (the default). Saved in the client config.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoAuraCommands {
    private XenoAuraCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        var root = Commands.literal("xenoaura").executes(ctx -> status(ctx.getSource()));
        for (AuraStyle style : AuraStyle.values()) {
            root = root.then(Commands.literal(style.id()).executes(ctx -> set(ctx.getSource(), style)));
        }
        root = root.then(Commands.literal("size")
                .then(Commands.argument("scale", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.2f, 5f))
                        .executes(ctx -> size(ctx.getSource(),
                                com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "scale")))));
        root = root.then(Commands.literal("brightness")
                .then(Commands.argument("level", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0f, 130f))
                        .executes(ctx -> brightness(ctx.getSource(),
                                com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "level")))));
        root = root.then(Commands.literal("layers")
                .then(Commands.literal("on").executes(ctx -> layers(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> layers(ctx.getSource(), false))));
        root = root.then(Commands.literal("follow")
                .then(Commands.literal("on").executes(ctx -> follow(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> follow(ctx.getSource(), false))));
        root = root.then(Commands.literal("debug")
                .then(Commands.literal("on").executes(ctx -> debug(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> debug(ctx.getSource(), false))));
        root = root.then(Commands.literal("inner")
                .then(Commands.argument("level", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.4f, 1f))
                        .executes(ctx -> inner(ctx.getSource(),
                                com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "level")))));
        root = root.then(Commands.literal("v1").executes(ctx -> variant(ctx.getSource(), "v1")))
                .then(Commands.literal("v2").executes(ctx -> variant(ctx.getSource(), "v2")))
                .then(Commands.literal("v3").executes(ctx -> variant(ctx.getSource(), "v3")));
        root = root.then(Commands.literal("live")
                .then(Commands.literal("on").executes(ctx -> live(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> live(ctx.getSource(), false))));
        root = root.then(Commands.literal("maxheight")
                .then(Commands.argument("times", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(1f, 10f))
                        .executes(ctx -> maxHeight(ctx.getSource(),
                                com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "times")))));
        root = root.then(Commands.literal("box")
                .then(Commands.literal("on").executes(ctx -> box(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> box(ctx.getSource(), false))));
        root = root.then(Commands.literal("overlay")
                .then(Commands.literal("on").executes(ctx -> overlay(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> overlay(ctx.getSource(), false))));
        root = root.then(Commands.literal("firstperson")
                .then(Commands.literal("on").executes(ctx -> firstPerson(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> firstPerson(ctx.getSource(), false))));
        dispatcher.register(root);
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Aura: " + AuraStyle.parse(XenoClientConfig.auraStyle).id()
                + ", size " + XenoClientConfig.auraSize + ", brightness " + XenoClientConfig.auraBrightness
                + ", " + XenoClientConfig.auraVariant
                + ", layers " + (XenoClientConfig.auraLayers ? "on" : "off")
                + ", follow " + (XenoClientConfig.auraFollowDmz ? "on" : "off")
                + "  (/xenoaura dmz | hd | both | size <0.2-5> | brightness <0-130> | layers on|off"
                + " | follow on|off | inner <0.4-1> | v1 | v2 | v3 | live on|off | maxheight <1-10> | box on|off | overlay on|off | firstperson on|off; your own second aura: /secondaura)"), false);
        return 1;
    }

    private static int brightness(CommandSourceStack source, float level) {
        float brightness = net.bullettrain.xenopixelsmod.fx.aura.AuraPalette.brightnessOf(level);
        XenoClientConfig.auraBrightness = brightness;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal("HD aura brightness: " + Math.round(brightness * 100f)
                + "% (0 is off; levels 10, 25, 50, 75, 100, 130 - the nearest is used)"), false);
        return 1;
    }

    private static int firstPerson(CommandSourceStack source, boolean on) {
        XenoClientConfig.auraFirstPerson = on;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal(on
                ? "HD aura: first person keeps it alive so it does not reset; the world-space flame is hidden (DragonMineZ's first-person aura still draws)"
                : "HD aura: third person only"), false);
        return 1;
    }

    private static int overlay(CommandSourceStack source, boolean on) {
        XenoClientConfig.auraOverParticles = on;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal(on
                ? "HD aura v2/v3: your own aura in third person draws over rocks, dust and particles"
                : "HD aura v2/v3: your own aura is hidden by whatever is in front of it"), false);
        return 1;
    }

    private static int box(CommandSourceStack source, boolean on) {
        net.bullettrain.xenopixelsmod.client.aura.AuraBoxOverlay.enabled = on;
        source.sendSuccess(() -> Component.literal(on
                ? "Aura box on: the yellow box is where DragonMineZ's aura flame is (third person; /xenoaura both to compare)"
                : "Aura box off"), false);
        return 1;
    }

    private static int maxHeight(CommandSourceStack source, float times) {
        XenoClientConfig.auraMaxHeight = times;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal("HD aura: stretched at most " + times
                + "x tall by the ki aura height"), false);
        return 1;
    }

    private static int live(CommandSourceStack source, boolean on) {
        XenoClientConfig.auraLiveScale = on;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal(on
                ? "HD aura: resized every frame with the ki aura"
                : "HD aura: size fixed when each copy is sent (the earlier behaviour)"), false);
        return 1;
    }

    private static int variant(CommandSourceStack source, String variant) {
        String chosen = net.bullettrain.xenopixelsmod.fx.aura.HdAuraPlan.parseVariant(variant);
        XenoClientConfig.auraVariant = chosen;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal(switch (chosen) {
            case "v3" -> "HD aura: v3, full v1 + spiked flame + punch edges + sparking";
            case "v2" -> "HD aura: v2, the spiked flame (works with dmz | hd | both)";
            default -> "HD aura: v1, the column of fire";
        }), false);
        return 1;
    }

    private static int inner(CommandSourceStack source, float level) {
        XenoClientConfig.auraInnerBrightness = level;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal("HD inner aura: " + level
                + " of the outer brightness (1 is the first look; baked levels, the nearest is used)"), false);
        return 1;
    }

    private static int debug(CommandSourceStack source, boolean on) {
        net.bullettrain.xenopixelsmod.client.aura.HdAuraClient.debug = on;
        source.sendSuccess(() -> Component.literal("HD aura pulse log: " + (on ? "on (see latest.log)" : "off")), false);
        return 1;
    }

    private static int follow(CommandSourceStack source, boolean on) {
        XenoClientConfig.auraFollowDmz = on;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal(on
                ? "HD aura: plays on players whenever DragonMineZ's aura shows, and always on those with /secondaura on"
                : "HD aura: plays only on players whose second aura is on (/secondaura)"), false);
        return 1;
    }

    private static int layers(CommandSourceStack source, boolean on) {
        XenoClientConfig.auraLayers = on;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal(on
                ? "HD aura: stack and transformation layers are their own auras"
                : "HD aura: one aura from the two lowest layers (the first behaviour)"), false);
        return 1;
    }

    private static int size(CommandSourceStack source, float scale) {
        XenoClientConfig.auraSize = scale;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal("HD aura size: " + scale
                + " (1 is DragonMineZ's own aura size)"), false);
        return 1;
    }

    private static int set(CommandSourceStack source, AuraStyle style) {
        XenoClientConfig.auraStyle = style.id();
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal("Aura: " + switch (style) {
            case DMZ -> "DragonMineZ's aura";
            case HD -> "HD aura";
            case BOTH -> "both (to compare)";
        }), false);
        return 1;
    }
}
