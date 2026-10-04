package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.scene.XenoNpcScene;
import net.bullettrain.xenopixelsmod.npc.scene.XenoNpcScenePlayback;
import net.bullettrain.xenopixelsmod.npc.scene.XenoNpcScenes;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.SyncNpcStoreIndexPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator authoring and playback of shared native NPC scenes. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoNpcSceneCommands {
    private XenoNpcSceneCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /**
     * Says whether this NPC can actually perform its scene, link by link.
     *
     * <p>Exists because a {@code clip} step that cannot play <b>says nothing at all</b>: the server
     * broadcasts, the client discards, and the scene reports success. Three separate settings have
     * to agree before a clip draws, they live on two different editor pages, and nothing in the game
     * connects them - so "my waves do not play" had no answer short of reading the source.
     *
     * <p>Every line is measured from the NPC in front of you rather than described in general.
     */
    private static int check(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        net.minecraft.world.entity.Entity entity = EntityArgument.getEntity(ctx, "npc");
        if (!(entity instanceof XenoNpcEntity npc)) {
            ctx.getSource().sendFailure(Component.literal("That is not a Xeno NPC."));
            return 0;
        }
        CommandSourceStack source = ctx.getSource();
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        source.sendSuccess(() -> Component.literal("§b" + npc.getName().getString()
                + "§7 - scene readiness"), false);

        // 1. Is there a scene at all, and what starts it.
        if (profile.sceneId.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§c x §7no scene assigned"), false);
            return 0;
        }
        XenoNpcScene scene = XenoNpcScenes.get(profile.sceneId);
        if (scene == null) {
            source.sendSuccess(() -> Component.literal("§c x §7scene §f"
                    + profile.sceneId + "§7 does not exist"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a v §7scene §f"
                + profile.sceneId + "§7, plays on §f"
                + profile.sceneTrigger.label()), false);

        // 2. SAY steps always work. CLIP steps are the ones with conditions.
        long clips = scene.steps().stream()
                .filter(step -> step.kind() == XenoNpcScene.Kind.CLIP).count();
        if (clips == 0) {
            source.sendSuccess(() -> Component.literal(
                    "§a v §7no clip steps - nothing else to check"), false);
            return 1;
        }

        // 3. Every clip must resolve against the library.
        int missing = 0;
        for (XenoNpcScene.Step step : scene.steps()) {
            if (step.kind() != XenoNpcScene.Kind.CLIP) {
                continue;
            }
            if (net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.resolve(step.value()) == null) {
                missing++;
                source.sendSuccess(() -> Component.literal("§c x §7clip §f"
                        + step.value() + "§7 is not in the animation library"), false);
            }
        }
        if (missing == 0) {
            source.sendSuccess(() -> Component.literal("§a v §7all " + clips
                    + " clip(s) found in the library"), false);
        }

        // 4. The server will only broadcast for a FULL-appearance NPC.
        boolean full = profile.appearance != null && profile.appearance.mode
                == net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance.Mode.FULL;
        source.sendSuccess(() -> Component.literal(full
                ? "§a v §7appearance is §fFULL"
                : "§c x §7appearance is §f"
                        + (profile.appearance == null ? "OFF" : profile.appearance.mode.name())
                        + "§7 - set Display > DMZ Appearance to FULL"), false);

        // 5. And only the vanilla-model renderer draws what the server sent. This is the link that
        // is easiest to get wrong, because it lives on a different page from the one above and
        // neither mentions the other.
        String kind = NpcCombatProfile.normalizeModelKind(profile.modelKind);
        boolean drawable = NpcCombatProfile.MODEL_VANILLA.equals(kind);
        source.sendSuccess(() -> Component.literal(drawable
                ? "§a v §7model is §fVANILLA§7, which draws clips"
                : "§c x §7model is §f" + kind
                        + "§7 - only VANILLA draws clips; GECKOLIB and ENTITY"), false);
        if (!drawable) {
            source.sendSuccess(() -> Component.literal(
                    "§7     render their own model and ignore the clip."), false);
        }

        boolean ready = missing == 0 && full && drawable;
        source.sendSuccess(() -> Component.literal(ready
                ? "§aReady - clips will play."
                : "§eClips will not play until the lines above are fixed."), false);
        return ready ? 1 : 0;
    }

    /**
     * Plays one clip and reports what every server-side stage answered.
     *
     * <p>{@code check} reports configuration; this reports <em>behaviour</em>. Every link in the
     * clip chain reads correct on paper, so the only way left to find where it dies is to run it and
     * have each stage say what it returned - which the normal path does not, because
     * {@code playClip} collapses five decisions into one boolean nobody looks at.
     *
     * <p>Server-side only, and that is the point: if all four lines here pass, the clip definitely
     * left the server and the fault is on the client, which halves the search.
     */
    private static int testClip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        net.minecraft.world.entity.Entity entity = EntityArgument.getEntity(ctx, "npc");
        if (!(entity instanceof XenoNpcEntity npc)) {
            ctx.getSource().sendFailure(Component.literal("That is not a Xeno NPC."));
            return 0;
        }
        CommandSourceStack source = ctx.getSource();
        String raw = StringArgumentType.getString(ctx, "clip");

        // 1. Does the name resolve to an animation at all.
        String resolved = XenoAnimApi.resolve(raw);
        source.sendSuccess(() -> Component.literal(resolved == null
                ? "§c1 resolve  x §7'" + raw + "' names no animation"
                : "§a1 resolve  v §f" + resolved), false);
        if (resolved == null) {
            return 0;
        }

        // 2. Will the animator accept this entity.
        boolean can = XenoAnimApi.canPlay(npc);
        source.sendSuccess(() -> Component.literal(can
                ? "§a2 canPlay  v §7this NPC can be animated"
                : "§c2 canPlay  x §7refused - appearance is not FULL"), false);
        if (!can) {
            return 0;
        }

        // 3. Is anyone close enough to be sent the packet. A clip broadcast to nobody is a clip
        // that played perfectly and was seen by no one.
        int viewers = 0;
        if (npc.level() instanceof net.minecraft.server.level.ServerLevel level) {
            for (net.minecraft.server.level.ServerPlayer viewer : level.players()) {
                if (viewer.distanceToSqr(npc) <= net.bullettrain.xenopixelsmod.compat.npc
                        .NpcDmzAnim.BROADCAST_RANGE
                        * net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAnim.BROADCAST_RANGE) {
                    viewers++;
                }
            }
        }
        final int inRange = viewers;
        source.sendSuccess(() -> Component.literal(inRange > 0
                ? "§a3 viewers  v §f" + inRange + "§7 player(s) in range"
                : "§c3 viewers  x §7nobody within 96 blocks to send it to"), false);

        // 4. And the send itself.
        boolean sent = XenoAnimApi.playClip(npc, raw);
        source.sendSuccess(() -> Component.literal(sent
                ? "§a4 playClip v §7sent §f" + resolved
                : "§c4 playClip x §7refused"), false);
        source.sendSuccess(() -> Component.literal(sent && inRange > 0
                ? "§eServer side is clean - if nothing moved, the fault is on the client."
                : "§cIt never left the server."), false);
        return sent ? 1 : 0;
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenoscene")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("show")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(XenoNpcSceneCommands::show)))
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(XenoNpcSceneCommands::create))))
                .then(Commands.literal("say")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("tick", IntegerArgumentType.integer(0,
                                        XenoNpcScene.MAX_TIME))
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(ctx -> add(ctx, XenoNpcScene.Kind.SAY))))))
                .then(Commands.literal("clip")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("tick", IntegerArgumentType.integer(0,
                                        XenoNpcScene.MAX_TIME))
                                        .then(Commands.argument("clip", StringArgumentType.word())
                                                .executes(ctx -> add(ctx, XenoNpcScene.Kind.CLIP))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                        .executes(XenoNpcSceneCommands::removeStep))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(XenoNpcSceneCommands::delete)))
                .then(Commands.literal("start")
                        .then(Commands.argument("npc", EntityArgument.entity())
                                .executes(ctx -> start(ctx, null))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> start(ctx,
                                                StringArgumentType.getString(ctx, "id"))))))
                .then(Commands.literal("testclip")
                        .then(Commands.argument("npc", EntityArgument.entity())
                                .then(Commands.argument("clip", StringArgumentType.word())
                                        .executes(XenoNpcSceneCommands::testClip))))
                .then(Commands.literal("check")
                        .then(Commands.argument("npc", EntityArgument.entity())
                                .executes(XenoNpcSceneCommands::check)))
                .then(Commands.literal("stop")
                        .then(Commands.argument("npc", EntityArgument.entity())
                                .executes(ctx -> control(ctx, "stop"))))
                .then(Commands.literal("pause")
                        .then(Commands.argument("npc", EntityArgument.entity())
                                .executes(ctx -> control(ctx, "pause"))))
                .then(Commands.literal("resume")
                        .then(Commands.argument("npc", EntityArgument.entity())
                                .executes(ctx -> control(ctx, "resume"))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("npc", EntityArgument.entity())
                                .executes(ctx -> control(ctx, "reset"))))
                .then(Commands.literal("time")
                        .then(Commands.argument("npc", EntityArgument.entity())
                                .executes(XenoNpcSceneCommands::time)
                                .then(Commands.argument("tick", IntegerArgumentType.integer(0,
                                        XenoNpcScene.MAX_TIME))
                                        .executes(XenoNpcSceneCommands::seek)))));
    }

    private static int list(CommandSourceStack source) {
        var scenes = XenoNpcScenes.all();
        source.sendSuccess(() -> Component.literal("Scenes: " + (scenes.isEmpty() ? "none"
                : scenes.stream().map(XenoNpcScene::id).limit(32)
                        .reduce((a, b) -> a + ", " + b).orElse("none"))), false);
        return scenes.size();
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        XenoNpcScene scene = XenoNpcScenes.get(id);
        if (scene == null) {
            return fail(ctx.getSource(), "No scene: " + id);
        }
        ok(ctx.getSource(), scene.name() + " (" + id + "): " + scene.steps().size() + " steps");
        for (int i = 0; i < scene.steps().size(); i++) {
            XenoNpcScene.Step step = scene.steps().get(i);
            String detail = i + ": " + step.time() + "t " + step.kind() + " " + step.value();
            ctx.getSource().sendSuccess(() -> Component.literal(detail), false);
        }
        return scene.steps().size();
    }

    private static int create(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        String bad = XenoNpcStorePaths.reject(id);
        if (bad != null) {
            return fail(ctx.getSource(), bad);
        }
        if (XenoNpcScenes.get(id) != null) {
            return fail(ctx.getSource(), "Scene already exists: " + id);
        }
        String name = StringArgumentType.getString(ctx, "name").trim();
        if (name.isEmpty() || name.length() > XenoNpcScene.MAX_NAME) {
            return fail(ctx.getSource(), "Name must be 1-" + XenoNpcScene.MAX_NAME + " characters");
        }
        String refusal = XenoNpcScenes.put(new XenoNpcScene(id, name));
        if (refusal != null) {
            return fail(ctx.getSource(), refusal);
        }
        ModNetwork.sendToAll(SyncNpcStoreIndexPacket.current());
        return ok(ctx.getSource(), "Created scene " + id);
    }

    private static int add(CommandContext<CommandSourceStack> ctx, XenoNpcScene.Kind kind) {
        String id = StringArgumentType.getString(ctx, "id");
        XenoNpcScene scene = XenoNpcScenes.get(id);
        if (scene == null) {
            return fail(ctx.getSource(), "No scene: " + id);
        }
        String value = StringArgumentType.getString(ctx,
                kind == XenoNpcScene.Kind.SAY ? "text" : "clip").trim();
        if (kind == XenoNpcScene.Kind.CLIP && !XenoAnimApi.isClipAvailable(value)) {
            return fail(ctx.getSource(), "Unknown animation clip: " + value);
        }
        XenoNpcScene.Step step;
        try {
            step = new XenoNpcScene.Step(IntegerArgumentType.getInteger(ctx, "tick"), kind, value);
        } catch (IllegalArgumentException invalid) {
            return fail(ctx.getSource(), "Step is empty or too long");
        }
        if (!scene.add(step)) {
            return fail(ctx.getSource(), "Scene already has " + XenoNpcScene.MAX_STEPS + " steps");
        }
        String refusal = XenoNpcScenes.put(scene);
        if (refusal != null) {
            return fail(ctx.getSource(), refusal);
        }
        ModNetwork.sendToAll(SyncNpcStoreIndexPacket.current());
        return ok(ctx.getSource(), "Added " + kind + " at tick " + step.time());
    }

    private static int removeStep(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        XenoNpcScene scene = XenoNpcScenes.get(id);
        int index = IntegerArgumentType.getInteger(ctx, "index");
        if (scene == null || !scene.remove(index)) {
            return fail(ctx.getSource(), "No scene step " + index + " in " + id);
        }
        String refusal = XenoNpcScenes.put(scene);
        if (refusal != null) {
            return fail(ctx.getSource(), refusal);
        }
        ModNetwork.sendToAll(SyncNpcStoreIndexPacket.current());
        return ok(ctx.getSource(), "Removed scene step " + index + " from " + id);
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        if (XenoNpcScenes.get(id) == null) {
            return fail(ctx.getSource(), "No scene: " + id);
        }
        String refusal = XenoNpcScenes.remove(id);
        if (refusal != null) {
            return fail(ctx.getSource(), refusal);
        }
        ModNetwork.sendToAll(SyncNpcStoreIndexPacket.current());
        return ok(ctx.getSource(), "Deleted scene " + id);
    }

    private static int start(CommandContext<CommandSourceStack> ctx, String id)
            throws CommandSyntaxException {
        if (!(EntityArgument.getEntity(ctx, "npc") instanceof XenoNpcEntity npc)) {
            return fail(ctx.getSource(), "Target must be a native Xeno NPC");
        }
        String chosen = id == null ? NpcCombatProfile.read(npc).sceneId : id;
        if (chosen.isBlank()) {
            return fail(ctx.getSource(), "NPC has no assigned scene");
        }
        return XenoNpcScenePlayback.start(npc, chosen)
                ? ok(ctx.getSource(), "Started scene " + chosen + " on " + npc.getName().getString())
                : fail(ctx.getSource(), "Scene is missing or has no steps: " + chosen);
    }

    private static int control(CommandContext<CommandSourceStack> ctx, String action)
            throws CommandSyntaxException {
        XenoNpcEntity npc = npc(ctx);
        if (npc == null) {
            return fail(ctx.getSource(), "Target must be a native Xeno NPC");
        }
        boolean changed = switch (action) {
            case "stop" -> XenoNpcScenePlayback.stop(npc);
            case "pause" -> XenoNpcScenePlayback.pause(npc);
            case "resume" -> XenoNpcScenePlayback.resume(npc);
            case "reset" -> XenoNpcScenePlayback.reset(npc);
            default -> false;
        };
        return changed ? ok(ctx.getSource(), action + " scene on " + npc.getName().getString())
                : fail(ctx.getSource(), "Scene is not in a state that can " + action);
    }

    private static int time(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        XenoNpcEntity npc = npc(ctx);
        if (npc == null) {
            return fail(ctx.getSource(), "Target must be a native Xeno NPC");
        }
        long tick = XenoNpcScenePlayback.time(npc);
        return tick < 0 ? fail(ctx.getSource(), "NPC has no playing scene")
                : ok(ctx.getSource(), "Scene time: " + tick + " ticks");
    }

    private static int seek(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        XenoNpcEntity npc = npc(ctx);
        if (npc == null) {
            return fail(ctx.getSource(), "Target must be a native Xeno NPC");
        }
        int tick = IntegerArgumentType.getInteger(ctx, "tick");
        return XenoNpcScenePlayback.seek(npc, tick)
                ? ok(ctx.getSource(), "Scene time set to " + tick + " ticks")
                : fail(ctx.getSource(), "NPC has no playing scene");
    }

    private static XenoNpcEntity npc(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return EntityArgument.getEntity(ctx, "npc") instanceof XenoNpcEntity npc ? npc : null;
    }

    private static int ok(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
