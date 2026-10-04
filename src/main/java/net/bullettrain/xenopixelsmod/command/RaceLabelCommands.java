package net.bullettrain.xenopixelsmod.command;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.dmz.race.RaceLabelRegistry;
import net.bullettrain.xenopixelsmod.dmz.race.RacePackService;
import net.bullettrain.xenopixelsmod.network.race.RaceLabelNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.Locale;

/**
 * Operator commands for custom-race picker literals and grants.
 *
 * <pre>
 * /xenorace list
 * /xenorace name &lt;id&gt; &lt;display name&gt;
 * /xenorace desc &lt;id&gt; &lt;description&gt;
 * /xenorace give &lt;players&gt; &lt;id&gt;
 * </pre>
 *
 * <p>Literals are not written into {@code en_us.json}. They live in
 * {@code config/dragonminez/races/&lt;id&gt;/xeno_labels.json} and are synced to clients.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class RaceLabelCommands {
    private static final SuggestionProvider<CommandSourceStack> RACE_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(RacePackService.knownRaceIds(), builder);

    private RaceLabelCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenorace")
                .then(Commands.literal("list")
                        .requires(XenoPermissions.require(XenoPermissions.RACE_EDIT))
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("name")
                        .requires(XenoPermissions.require(XenoPermissions.RACE_EDIT))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(RACE_IDS)
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> setName(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("desc")
                        .requires(XenoPermissions.require(XenoPermissions.RACE_EDIT))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(RACE_IDS)
                                .then(Commands.argument("description", StringArgumentType.greedyString())
                                        .executes(ctx -> setDesc(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "description"))))))
                .then(Commands.literal("give")
                        .requires(XenoPermissions.require(XenoPermissions.RACE_GIVE))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(RACE_IDS)
                                        .executes(ctx -> give(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "players"),
                                                StringArgumentType.getString(ctx, "id"))))))
                .executes(ctx -> help(ctx.getSource())));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Usage: /xenorace list | name <id> <display name> | desc <id> <description> | give <players> <id>\n"
                        + "Literals live in config/dragonminez/races/<id>/xeno_labels.json and are not en_us keys."),
                false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        StringBuilder out = new StringBuilder();
        for (String id : RacePackService.knownRaceIds()) {
            if (out.length() > 0) {
                out.append('\n');
            }
            String name = RaceLabelRegistry.displayName(id);
            String desc = RaceLabelRegistry.description(id);
            boolean custom = RacePackService.isCustomPack(id);
            out.append(custom ? "* " : "  ").append(id);
            if (!name.isBlank()) {
                out.append(" — ").append(name);
            }
            if (!desc.isBlank()) {
                out.append(" | ").append(clip(desc, 48));
            }
        }
        String line = out.isEmpty() ? "No races found." : out.toString();
        source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    private static int setName(CommandSourceStack source, String id, String name) {
        return setLabels(source, id, name, RaceLabelRegistry.description(id), "name");
    }

    private static int setDesc(CommandSourceStack source, String id, String description) {
        return setLabels(source, id, RaceLabelRegistry.displayName(id), description, "description");
    }

    private static int setLabels(CommandSourceStack source, String id, String name, String description,
                                 String field) {
        String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        var result = RaceLabelRegistry.writeSidecar(key, name, description);
        if (!result.ok()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        RaceLabelNetwork.broadcast(source.getServer());
        source.sendSuccess(() -> Component.literal("Race " + key + " " + field + " set to "
                + (field.equals("name") ? RaceLabelRegistry.displayName(key)
                : clip(RaceLabelRegistry.description(key), 80))), true);
        return 1;
    }

    private static int give(CommandSourceStack source, Collection<ServerPlayer> players, String id) {
        String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (!RacePackService.validateRaceId(key).ok()) {
            source.sendFailure(Component.literal("Invalid race id: " + id));
            return 0;
        }
        if (!raceLoaded(key) && !RacePackService.isCustomPack(key)
                && !RacePackService.isDefaultRace(key)) {
            source.sendFailure(Component.literal("Race pack is not loaded: " + key
                    + " (need config/dragonminez/races/" + key + "/character.json)"));
            return 0;
        }
        int granted = 0;
        for (ServerPlayer player : players) {
            if (applyRace(player, key)) {
                granted++;
                String shown = RaceLabelRegistry.displayName(key);
                player.sendSystemMessage(Component.literal("Race set to "
                        + (shown.isBlank() ? key : shown + " (" + key + ")")));
            }
        }
        int count = granted;
        source.sendSuccess(() -> Component.literal("Gave race " + key + " to " + count + " player(s)"),
                true);
        return granted;
    }

    private static boolean applyRace(ServerPlayer player, String raceId) {
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (data == null || data.getCharacter() == null) {
            return false;
        }
        data.getCharacter().setRace(raceId);
        try {
            data.updateTransformationSkillLimits(raceId);
        } catch (Throwable ignored) {
        }
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
        return true;
    }

    private static boolean raceLoaded(String raceId) {
        try {
            return ConfigManager.isRaceLoaded(raceId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
