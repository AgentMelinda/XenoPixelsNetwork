package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.music.XenoMusicPlayer;
import net.bullettrain.xenopixelsmod.client.music.XenoMusicTracks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /xenomusic} — the client-only background music player. {@code on|off}, {@code next},
 * {@code prev}, {@code track <n>}, {@code volume <0-1>}, {@code list}. The pause-menu plate offers
 * the same controls; this command exists so the settings are scriptable per client. These keys
 * are client-only. {@code /xenoset music} exposes the same controls locally without changing
 * another player's settings or sending a server command.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoMusicCommands {
    private XenoMusicCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(controls("xenomusic"));
        event.getDispatcher().register(Commands.literal("xenoset").then(controls("music")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> controls(String name) {
        return Commands.literal(name).executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("on").executes(ctx -> { XenoMusicPlayer.setEnabled(true); return status(ctx.getSource()); }))
                .then(Commands.literal("off").executes(ctx -> { XenoMusicPlayer.setEnabled(false); return status(ctx.getSource()); }))
                .then(Commands.literal("next").executes(ctx -> { XenoMusicPlayer.next(); return status(ctx.getSource()); }))
                .then(Commands.literal("prev").executes(ctx -> { XenoMusicPlayer.previous(); return status(ctx.getSource()); }))
                .then(Commands.literal("track").then(Commands.argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int number = IntegerArgumentType.getInteger(ctx, "number");
                            if (!XenoMusicPlayer.select(number - 1)) {
                                ctx.getSource().sendFailure(Component.literal("No track " + number + "; there are "
                                        + XenoClientConfig.musicTracks.size()));
                                return 0;
                            }
                            return status(ctx.getSource());
                        })))
                .then(Commands.literal("volume").then(Commands.argument("level", FloatArgumentType.floatArg(0f, 1f))
                        .executes(ctx -> {
                            XenoMusicPlayer.setVolume(FloatArgumentType.getFloat(ctx, "level"));
                            return status(ctx.getSource());
                        })))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())));
    }

    private static int status(CommandSourceStack source) {
        var tracks = XenoClientConfig.musicTracks;
        int index = tracks.isEmpty() ? 0 : Math.floorMod(XenoClientConfig.musicTrack, tracks.size()) + 1;
        source.sendSuccess(() -> Component.literal("Music " + (XenoClientConfig.musicEnabled ? "on" : "off")
                + " | " + XenoMusicPlayer.currentTrackName() + " (" + index + "/" + tracks.size() + ") | volume "
                + Math.round(XenoClientConfig.musicVolume * 100) + "% | "
                + (XenoMusicPlayer.playing() ? "playing" : "stopped")), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        var tracks = XenoClientConfig.musicTracks;
        for (int i = 0; i < tracks.size(); i++) {
            final String line = (i + 1) + ". " + XenoMusicTracks.displayName(tracks.get(i)) + "  (" + tracks.get(i) + ")";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return tracks.size();
    }
}
