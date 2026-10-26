package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.space.SpacePlanetMode;
import net.bullettrain.xenopixelsmod.client.space.SpaceSkyMode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The client's {@code /xenoset} — this player's own look and feel, changed live, with no server round
 * trip and no effect on anybody else's settings.
 *
 * <p><b>Why this class owns the root.</b> Brigadier keeps one node per literal name, so every client
 * branch of {@code /xenoset} has to be registered from one place; {@link XenoMusicCommands} hands over
 * its {@code music} tree rather than registering the root twice. The server also has a
 * {@code /xenoset <key> <value>} alias for {@code /xenoconfig set} — that one is permission-gated and
 * changes shared config, and is untouched by this file. The rule of thumb when adding a branch here:
 * if the setting lives on the server (oxygen, void damage, planet size), it belongs to
 * {@code /xenospace}; if it is what this client draws, it belongs to {@code /xenoset}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoSetCommands {
    private XenoSetCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenoset")
                .executes(ctx -> status(ctx.getSource()))
                .then(XenoMusicCommands.musicTree())
                .then(skyTree())
                .then(spaceTree())
                .then(Commands.literal("hakai")
                        .executes(ctx -> hakaiStatus(ctx.getSource()))
                        .then(Commands.literal("classic").executes(ctx -> setHakai(ctx.getSource(), "classic")))
                        .then(Commands.literal("sweep").executes(ctx -> setHakai(ctx.getSource(), "sweep")))
                        .then(Commands.literal("impact").executes(ctx -> setHakai(ctx.getSource(), "impact")))
                        .then(Commands.literal("debug").executes(ctx -> {
                            XenoClientConfig.hakaiSweepDebug = !XenoClientConfig.hakaiSweepDebug;
                            ctx.getSource().sendSuccess(() -> Component.literal("Hakai sweep debug log: "
                                    + (XenoClientConfig.hakaiSweepDebug ? "ON (HAKAI_SWEEP lines in latest.log)" : "off")), false);
                            return 1;
                        }))));
    }

    /** {@code /xenoset sky <void|stars|off|status>} — takes effect on the next rendered frame. */
    private static LiteralArgumentBuilder<CommandSourceStack> skyTree() {
        LiteralArgumentBuilder<CommandSourceStack> sky = Commands.literal("sky")
                .executes(ctx -> status(ctx.getSource()));
        for (SpaceSkyMode mode : SpaceSkyMode.values()) {
            sky.then(Commands.literal(mode.id).executes(ctx -> set(ctx.getSource(), mode)));
        }
        return sky.then(Commands.literal("status").executes(ctx -> status(ctx.getSource())));
    }

    private static int set(CommandSourceStack source, SpaceSkyMode mode) {
        XenoClientConfig.spaceSkyMode = mode.id;
        XenoClientConfig.save();
        // No restart and no screen reopen: SpaceSkyEffects re-reads the key each frame and rebuilds the
        // buffers it needs when the mode it finds is not the mode it drew last time.
        source.sendSuccess(() -> Component.literal("Sky set to " + mode.id + " (saved, applied this frame)"),
                false);
        return status(source);
    }

    /**
     * {@code /xenoset space planets <bodies|off>} controls physical planet surfaces.
     * {@code /xenoset space planets projections <on|off>} independently toggles distant chart projections;
     * {@code ... projections maps <body|atlas>} picks per-body HD maps or the legacy shared style strip.
     * {@code ... projections check} lists far projections, near Sable bodies and small planets with the texture each
     * last bound and whether it is that body's own.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> spaceTree() {
        LiteralArgumentBuilder<CommandSourceStack> space = Commands.literal("space")
                .executes(ctx -> planetStatus(ctx.getSource()));
        space.then(Commands.literal("camera-coast")
                .executes(ctx -> cameraCoastStatus(ctx.getSource()))
                .then(Commands.literal("on").executes(ctx -> setCameraCoast(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> setCameraCoast(ctx.getSource(), false)))
                .then(Commands.literal("status").executes(ctx -> cameraCoastStatus(ctx.getSource()))));
        space.then(Commands.literal("fallen-stars")
                .executes(ctx -> fallenStarsStatus(ctx.getSource()))
                .then(Commands.argument("density", IntegerArgumentType.integer(0, 100))
                        .executes(ctx -> setFallenStarDensity(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "density")))));
        LiteralArgumentBuilder<CommandSourceStack> planets = Commands.literal("planets")
                .executes(ctx -> planetStatus(ctx.getSource()));
        for (SpacePlanetMode mode : SpacePlanetMode.values()) {
            planets.then(Commands.literal(mode.id).executes(ctx -> setPlanets(ctx.getSource(), mode)));
        }
        planets.then(Commands.literal("projections")
                .executes(ctx -> projectionStatus(ctx.getSource()))
                .then(Commands.literal("on").executes(ctx -> setProjections(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> setProjections(ctx.getSource(), false)))
                .then(Commands.literal("status").executes(ctx -> projectionStatus(ctx.getSource())))
                .then(Commands.literal("check").executes(ctx -> projectionCheck(ctx.getSource())))
                .then(Commands.literal("maps")
                        .executes(ctx -> projectionMapStatus(ctx.getSource()))
                        .then(Commands.literal("body").executes(ctx -> setProjectionMaps(ctx.getSource(), true)))
                        .then(Commands.literal("atlas").executes(ctx -> setProjectionMaps(ctx.getSource(), false)))));
        return space.then(planets.then(Commands.literal("status").executes(ctx -> planetStatus(ctx.getSource()))));
    }

    private static int setHakai(CommandSourceStack source, String style) {
        XenoClientConfig.hakaiEraseStyle = style;
        XenoClientConfig.save();
        return hakaiStatus(source);
    }

    private static int hakaiStatus(CommandSourceStack source) {
        String style = XenoClientConfig.hakaiEraseStyle;
        String description = switch (style) {
            case "impact" -> "crosshair-origin spread (server-captured hit point)";
            case "classic" -> "classic height fade";
            default -> "legacy top-to-bottom cut with a violet rim";
        };
        source.sendSuccess(() -> Component.literal("Hakai erase style: " + style + " - " + description + " (saved)"), false);
        return 1;
    }

    private static int setPlanets(CommandSourceStack source, SpacePlanetMode mode) {
        XenoClientConfig.spacePlanetMode = mode.id;
        XenoClientConfig.save();
        // No restart: both renderers read the key each frame, so the next frame is the new look.
        source.sendSuccess(() -> Component.literal("Planets set to " + mode.id + " (saved, applied this frame)"),
                false);
        return planetStatus(source);
    }

    private static int planetStatus(CommandSourceStack source) {
        SpacePlanetMode current = SpacePlanetMode.byName(XenoClientConfig.spacePlanetMode);
        source.sendSuccess(() -> Component.literal("Planets: " + current.id + " — " + describe(current)),
                false);
        return 1;
    }

    private static int setProjections(CommandSourceStack source, boolean enabled) {
        XenoClientConfig.spacePlanetProjections = enabled;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal("Distant planet projections: "
                + (enabled ? "ON" : "OFF") + " (saved, applied this frame)"), false);
        return 1;
    }

    /** Lists every projected body with the texture its far projection binds and whether that is its own map. */
    private static int projectionCheck(CommandSourceStack source) {
        var report = net.bullettrain.xenopixelsmod.client.space.PlanetProjectionReport.current(
                XenoClientConfig.spacePlanetProjectionMaps);
        int total = report.far().size() + report.near().size() + report.small().size() + report.benchmark().size();
        source.sendSuccess(() -> Component.literal("Planet texture check (" + (report.perBodyMaps() ? "body" : "atlas")
                + " far maps): " + total + " entries, " + report.wrong() + " confirmed mismatch(es), "
                + report.unverified() + " unverified"), false);
        source.sendSuccess(() -> Component.literal("Identity check: body id, exact bound texture key and the registered"
                + " texture object (texture-object=). It does not inspect the pixels drawn on screen."), false);
        section(source, "Far chart projections", report.far());
        section(source, "Near Sable bodies", report.near());
        section(source, "Small planets", report.small());
        section(source, "Benchmark SDB spheres (bench poly)", report.benchmark());
        source.sendSuccess(() -> Component.literal(report.aimed()), false);
        return total;
    }

    private static void section(CommandSourceStack source, String title, java.util.List<String> lines) {
        source.sendSuccess(() -> Component.literal("-- " + title + ": " + lines.size()), false);
        for (String line : lines) source.sendSuccess(() -> Component.literal(line), false);
    }

    private static int setProjectionMaps(CommandSourceStack source, boolean perBody) {
        XenoClientConfig.spacePlanetProjectionMaps = perBody;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal("Distant planet projection maps: "
                + (perBody ? "body (each planet's own HD map)" : "atlas (legacy shared style strip)")
                + " (saved, applied this frame)"), false);
        return 1;
    }

    private static int projectionMapStatus(CommandSourceStack source) {
        boolean perBody = XenoClientConfig.spacePlanetProjectionMaps;
        source.sendSuccess(() -> Component.literal("Distant planet projection maps: "
                + (perBody ? "body" : "atlas")), false);
        return 1;
    }

    private static int setFallenStarDensity(CommandSourceStack source, int density) {
        XenoClientConfig.spaceFallenStarDensity = density;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal("Ambient shooting-star chance set to " + density
                + "% per 30-second window (0 disables; saved, applied live)."), false);
        return 1;
    }

    private static int fallenStarsStatus(CommandSourceStack source) {
        int density = XenoClientConfig.spaceFallenStarDensity;
        source.sendSuccess(() -> Component.literal("Ambient shooting stars: "
                + (density == 0 ? "OFF" : density + "% chance per 30-second Voidsea window")), false);
        return 1;
    }

    private static int setCameraCoast(CommandSourceStack source, boolean enabled) {
        XenoClientConfig.spaceCameraCoast = enabled;
        XenoClientConfig.save();
        source.sendSuccess(() -> Component.literal("Outbound space camera coast: "
                + (enabled ? "ON (experimental)" : "OFF (captured-frame transition)")
                + " (saved; applies on the next handoff)"), false);
        return 1;
    }

    private static int cameraCoastStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Outbound space camera coast: "
                + (XenoClientConfig.spaceCameraCoast ? "ON (experimental)" : "OFF (captured-frame transition)")), false);
        return 1;
    }

    private static int projectionStatus(CommandSourceStack source) {
        boolean enabled = XenoClientConfig.spacePlanetProjections;
        source.sendSuccess(() -> Component.literal("Distant planet projections: "
                + (enabled ? "ON" : "OFF")), false);
        return 1;
    }

    private static String describe(SpacePlanetMode mode) {
        return switch (mode) {
            case BODIES -> "HD surfaces on replicated Sable bodies, with distant projections "
                    + (XenoClientConfig.spacePlanetProjections ? "on" : "off");
            case OFF -> "no planets drawn";
        };
    }

    private static int status(CommandSourceStack source) {
        SpaceSkyMode current = SpaceSkyMode.byName(XenoClientConfig.spaceSkyMode);
        source.sendSuccess(() -> Component.literal("Space sky: " + current.id + " — " + describe(current)),
                false);
        return 1;
    }

    private static String describe(SpaceSkyMode mode) {
        return switch (mode) {
            case VOID -> "black fading to a little purple, one cube, no star field";
            case STARS -> "black cube plus the cached star field";
            case OFF -> "nothing drawn here, vanilla's void sky";
        };
    }
}
