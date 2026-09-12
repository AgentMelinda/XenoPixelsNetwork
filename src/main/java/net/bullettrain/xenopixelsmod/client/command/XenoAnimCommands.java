package net.bullettrain.xenopixelsmod.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.anim.XenoTechniqueAnimBindings;
import net.bullettrain.xenopixelsmod.client.anim.StudioClipBindings;
import net.bullettrain.xenopixelsmod.client.anim.XenoAnimClip;
import net.bullettrain.xenopixelsmod.client.anim.XenoAnimPlayer;
import net.bullettrain.xenopixelsmod.client.anim.XenoAnimRecorder;
import net.bullettrain.xenopixelsmod.client.anim.XenoClipLibraryClient;
import net.bullettrain.xenopixelsmod.client.anim.XenoStudioClipCache;
import net.bullettrain.xenopixelsmod.client.anim.XenoTechniqueAnimBindingsClient;
import net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimationBinding;
import net.bullettrain.xenopixelsmod.client.screen.XenoAnimStudioScreen;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot;
import net.bullettrain.xenopixelsmod.network.AnimClipsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * In-game animation studio: pose, key, export GeckoLib JSON, and bind a clip to a combat move.
 *
 * <p>{@code /xenoanim studio | play | stopplay | record | save | list | bind | unbind | bindings |
 * reload}. Documented in {@code docs/xeno-anim-studio.md}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoAnimCommands {
    private XenoAnimCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenoanim")
                .then(Commands.literal("record")
                        .then(Commands.literal("start")
                                .executes(ctx -> start(ctx.getSource(), "clip"))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> start(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource()))))
                .then(Commands.literal("save").executes(ctx -> save(ctx.getSource())))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("play")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String name : XenoAnimClip.listSavedNames()) b.suggest(name);
                                    return b.buildFuture();
                                })
                                .executes(ctx -> play(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"), false))
                                .then(Commands.literal("loop").executes(ctx -> play(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"), true)))))
                .then(Commands.literal("stopplay").executes(ctx -> {
                    XenoAnimPlayer.stop();
                    ctx.getSource().sendSuccess(() -> Component.literal("§7Playback stopped"), false);
                    return 1;
                }))
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("bindings").executes(ctx -> bindings(ctx.getSource())))
                .then(Commands.literal("bind")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String name : XenoAnimClip.listSavedNames()) b.suggest(name);
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("intent", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            suggestSlots(b);
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> bind(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "intent"))))))
                .then(Commands.literal("unbind")
                        .then(Commands.argument("intent", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    suggestSlots(b);
                                    return b.buildFuture();
                                })
                                .executes(ctx -> unbind(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "intent")))))
                .then(Commands.literal("global")
                        .then(Commands.literal("push")
                                .executes(ctx -> pushGlobal(ctx.getSource(), null))
                                .then(Commands.literal("all")
                                        .executes(ctx -> pushGlobal(ctx.getSource(), null)))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (String name : XenoAnimClip.listSavedNames()) {
                                                b.suggest(name);
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> pushGlobal(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("clear").executes(ctx -> {
                            AnimClipsNetwork.clearFromClient();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§7Asked the server to clear its clip library"), false);
                            return 1;
                        }))
                        .then(Commands.literal("list").executes(ctx -> listGlobal(ctx.getSource()))))
                .then(Commands.literal("studio").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.setScreen(new XenoAnimStudioScreen(mc.screen)));
                    return 1;
                }))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Usage: /xenoanim studio | play <name> [loop] | stopplay | record start "
                                    + "[name] | save | list | bind <clip> <SLOT> | unbind <SLOT> "
                                    + "| bindings | reload | global push|clear|list"),
                            false);
                    return 1;
                }));
    }

    private static int start(CommandSourceStack source, String name) {
        XenoAnimRecorder.start(name);
        source.sendSuccess(() -> Component.literal(
                "§dRecording §f" + XenoAnimClip.sanitize(name) + " §7- act, then /xenoanim record stop"), false);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        XenoAnimClip clip = XenoAnimRecorder.stop();
        int frames = clip == null ? 0 : clip.frames.size();
        source.sendSuccess(() -> Component.literal(
                "§7Stopped. " + frames + " frames, " + (clip == null ? 0 : clip.lengthSeconds())
                        + "s. /xenoanim save"), false);
        return 1;
    }

    private static int save(CommandSourceStack source) {
        XenoAnimClip clip = XenoAnimRecorder.current();
        if (clip == null || (clip.frames.isEmpty() && clip.isEmpty())) {
            source.sendFailure(Component.literal("Nothing recorded. /xenoanim record start"));
            return 0;
        }
        try {
            Path file = clip.save();
            XenoStudioClipCache.refresh(clip.name);
            source.sendSuccess(() -> Component.literal(
                    "§aSaved " + file.getFileName() + " §7(" + clip.keyCount()
                            + " keys). /xenoanim play " + clip.name), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Save failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int play(CommandSourceStack source, String name, boolean loop) {
        try {
            XenoAnimClip clip = XenoAnimClip.load(name);
            if (clip.isEmpty()) {
                source.sendFailure(Component.literal("Clip has no keys: " + name));
                return 0;
            }
            XenoAnimPlayer.play(clip, loop || clip.loop);
            source.sendSuccess(() -> Component.literal(
                    "§aPlaying §f" + clip.name + (loop || clip.loop ? " §7(loop)" : "")
                            + " §7- /xenoanim stopplay to cancel"), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Cannot play: " + e.getMessage()));
            return 0;
        }
    }

    private static int list(CommandSourceStack source) {
        var names = XenoAnimClip.listSaved();
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No clips in config/xenopixelsmod-anims/"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("§eClips: §f" + String.join(", ", names)), false);
        return names.size();
    }

    /**
     * Uploads local clips to the server so every client gets them on join.
     *
     * <p>Reading the files here rather than re-serialising the parsed clip means what the server
     * stores is byte-for-byte what is on disk - including anything a future version of the format
     * adds that this build would silently drop on a round trip.
     */
    private static int pushGlobal(CommandSourceStack source, String only) {
        List<String> names = only == null
                ? XenoAnimClip.listSavedNames()
                : List.of(XenoAnimClip.sanitize(only));
        if (names.isEmpty()) {
            source.sendFailure(Component.literal("No saved clips to publish"));
            return 0;
        }
        int sent = 0;
        for (String name : names) {
            Path file = XenoAnimClip.dir().resolve(name + ".animation.json");
            if (!Files.isRegularFile(file)) {
                source.sendFailure(Component.literal("No saved clip called " + name));
                continue;
            }
            try {
                AnimClipsNetwork.pushFromClient(name, Files.readString(file));
                sent++;
            } catch (IOException e) {
                source.sendFailure(Component.literal(
                        "Could not read " + name + ": " + e.getMessage()));
            }
        }
        int total = sent;
        source.sendSuccess(() -> Component.literal(
                "§7Sent §f" + total + " §7clip(s) to the server"), false);
        return sent;
    }

    private static int listGlobal(CommandSourceStack source) {
        List<String> names = XenoClipLibraryClient.names();
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "§7This server has published no clips"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(
                "§eFrom the server: §f" + String.join(", ", names)), false);
        return names.size();
    }

    private static int reload(CommandSourceStack source) {
        XenoStudioClipCache.reload();
        StudioClipBindings.load();
        Bt3AnimationBinding.registerStudioNames();
        int baked = XenoStudioClipCache.names().size();
        Map<String, String> failures = XenoStudioClipCache.failures();
        source.sendSuccess(() -> Component.literal("§aReloaded §f" + baked + " §astudio clip(s)"), false);
        failures.forEach((file, error) ->
                source.sendSuccess(() -> Component.literal("§c" + file + ": §7" + error), false));
        return 1;
    }

    private static void suggestSlots(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (TechniqueAnimSlot slot : TechniqueAnimSlot.values()) {
            builder.suggest(slot.name());
        }
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            builder.suggest(intent.name());
        }
    }

    private static int bindings(CommandSourceStack source) {
        Map<String, String> server = XenoTechniqueAnimBindingsClient.all();
        Map<Bt3AnimationIntent, String> local = StudioClipBindings.all();
        if (server.isEmpty() && local.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "§7No clips bound. /xenoanim bind <clip> <SLOT>"), false);
            return 1;
        }
        int n = 0;
        for (var entry : server.entrySet()) {
            boolean baked = XenoStudioClipCache.has(XenoAnimClip.ANIMATION_PREFIX + entry.getValue());
            source.sendSuccess(() -> Component.literal(
                    "§a" + entry.getKey() + " §7-> §e" + entry.getValue()
                            + (baked ? " §8(server)" : " §c(not baked)")), false);
            n++;
        }
        local.forEach((intent, clip) -> {
            if (server.containsKey(intent.name())) return;
            boolean baked = XenoStudioClipCache.has(XenoAnimClip.ANIMATION_PREFIX + clip);
            source.sendSuccess(() -> Component.literal(
                    "§f" + intent.name() + " §7-> §e" + clip
                            + (baked ? " §8(this client)" : " §c(not baked)")), false);
        });
        return n + local.size();
    }

    private static int bind(CommandSourceStack source, String clipName, String slotName) {
        String slot = XenoTechniqueAnimBindings.normalizeSlot(slotName);
        if (slot == null) {
            source.sendFailure(Component.literal("Unknown slot: " + slotName));
            return 0;
        }
        String clip = XenoAnimClip.sanitize(clipName);
        if (!XenoAnimClip.listSavedNames().contains(clip)) {
            source.sendFailure(Component.literal("No saved clip called " + clip));
            return 0;
        }
        Path file = XenoAnimClip.dir().resolve(clip + ".animation.json");
        if (Files.isRegularFile(file)) {
            try {
                AnimClipsNetwork.pushFromClient(clip, Files.readString(file));
            } catch (IOException e) {
                source.sendFailure(Component.literal("Could not publish " + clip + ": " + e.getMessage()));
                return 0;
            }
        }
        AnimClipsNetwork.bindFromClient(slot, clip);
        Bt3AnimationIntent intent = XenoTechniqueAnimBindings.intentOf(slot);
        if (intent != null) {
            StudioClipBindings.bind(intent, clip);
        }
        XenoStudioClipCache.refresh(clip);
        Bt3AnimationBinding.registerStudioNames();
        boolean baked = XenoStudioClipCache.has(XenoAnimClip.ANIMATION_PREFIX + clip);
        source.sendSuccess(() -> Component.literal(baked
                ? "§a" + slot + " §7asked the server to play §f" + clip + " §7for every joiner"
                : "§eSent, but " + clip + " did not bake locally. /xenoanim reload for the reason."),
                false);
        return 1;
    }

    private static int unbind(CommandSourceStack source, String slotName) {
        String slot = XenoTechniqueAnimBindings.normalizeSlot(slotName);
        if (slot == null) {
            source.sendFailure(Component.literal("Unknown slot: " + slotName));
            return 0;
        }
        AnimClipsNetwork.bindFromClient(slot, "");
        Bt3AnimationIntent intent = XenoTechniqueAnimBindings.intentOf(slot);
        boolean removed = intent != null && StudioClipBindings.unbind(intent);
        source.sendSuccess(() -> Component.literal(
                "§7Asked the server to restore " + slot
                        + (removed ? " (local bind cleared)" : "")), false);
        return 1;
    }
}
