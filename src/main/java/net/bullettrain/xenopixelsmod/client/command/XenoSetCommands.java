package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
     * {@code /xenoset space planets <bodies|off>} — whether this client draws replicated physical planets.
     *
     * <p>Chart-only planet projections are retired. This client preference only hides or shows surfaces
     * attached to replicated Sable bodies.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> spaceTree() {
        LiteralArgumentBuilder<CommandSourceStack> space = Commands.literal("space")
                .executes(ctx -> planetStatus(ctx.getSource()));
        LiteralArgumentBuilder<CommandSourceStack> planets = Commands.literal("planets")
                .executes(ctx -> planetStatus(ctx.getSource()));
        for (SpacePlanetMode mode : SpacePlanetMode.values()) {
            planets.then(Commands.literal(mode.id).executes(ctx -> setPlanets(ctx.getSource(), mode)));
        }
        return space.then(planets.then(Commands.literal("status").executes(ctx -> planetStatus(ctx.getSource()))));
    }

    private static int setHakai(CommandSourceStack source, String style) {
        XenoClientConfig.hakaiEraseStyle = style;
        XenoClientConfig.save();
        return hakaiStatus(source);
    }

    private static int hakaiStatus(CommandSourceStack source) {
        String style = XenoClientConfig.hakaiEraseStyle;
        source.sendSuccess(() -> Component.literal("Hakai erase style: " + style + ("sweep".equals(style)
                ? " - clean top-to-bottom cut with a violet rim" : " - classic height fade") + " (saved)"), false);
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

    private static String describe(SpacePlanetMode mode) {
        return switch (mode) {
            case BODIES -> "HD surfaces on replicated Sable bodies only";
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
