package net.bullettrain.xenopixelsmod.command;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Stats;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.XenoStatCeiling;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
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
import java.util.List;
import java.util.Locale;

/**
 * Reads and writes DragonMineZ stats past DragonMineZ's own cap, and controls that cap.
 *
 * <pre>
 * /xenostats limit                     what the ceiling is now
 * /xenostats limit 2000000000          raise it
 * /xenostats limit off                 lift it as far as DragonMineZ can hold
 * /xenostats limit dmz                 hand it back to DragonMineZ's own config
 * /xenostats get &lt;player&gt;
 * /xenostats set &lt;players&gt; &lt;stat&gt; &lt;value&gt;
 * /xenostats set &lt;players&gt; all &lt;value&gt;
 * /xenostats add &lt;players&gt; &lt;stat&gt; &lt;amount&gt;
 * </pre>
 *
 * <p><b>The writes are DragonMineZ's own.</b> Every value goes through {@code Stats#setStat}, so
 * DragonMineZ's stat-change events fire, its attributes are reapplied and its clamp still runs — the
 * clamp simply has a higher number to clamp to, because {@code DmzStatMaxOverrideMixin} raised what
 * DragonMineZ's config reports. Nothing here writes a field behind DragonMineZ's back, so a value
 * set with this command survives a reload exactly as one set with DragonMineZ's own command does.
 *
 * <p><b>2,147,483,647 is the wall.</b> DragonMineZ keeps each stat in an {@code int}. See
 * {@link XenoStatCeiling}; the command says so rather than pretending {@code off} means unlimited.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoStatsCommands {

    /** DragonMineZ's own stat keys, exactly as {@code Stats#setStat} switches on them. */
    private static final List<String> STATS = List.of("str", "skp", "res", "vit", "pwr", "ene");

    private static final SuggestionProvider<CommandSourceStack> STAT_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    java.util.stream.Stream.concat(STATS.stream(), java.util.stream.Stream.of("all")),
                    builder);

    private XenoStatsCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenostats")
                .then(Commands.literal("limit")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSTATS_LIMIT))
                        .then(Commands.literal("off")
                                .executes(ctx -> setLimit(ctx.getSource(), XenoStatCeiling.MAX)))
                        // "dmz" rather than "0": a server operator reading the help should see that
                        // this hands the setting back rather than sets a ceiling of nothing.
                        .then(Commands.literal("dmz")
                                .executes(ctx -> setLimit(ctx.getSource(), XenoStatCeiling.OFF)))
                        .then(Commands.argument("value", IntegerArgumentType.integer(
                                        XenoStatCeiling.MIN, XenoStatCeiling.MAX))
                                .executes(ctx -> setLimit(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "value"))))
                        .executes(ctx -> limitStatus(ctx.getSource())))
                .then(Commands.literal("get")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSTATS_STATUS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> get(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"))))
                        .executes(ctx -> get(ctx.getSource(), ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("set")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSTATS_SET))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .suggests(STAT_SUGGESTIONS)
                                        .then(Commands.argument("value", IntegerArgumentType.integer(
                                                        0, XenoStatCeiling.MAX))
                                                .executes(ctx -> write(ctx, false))))))
                .then(Commands.literal("add")
                        .requires(XenoPermissions.require(XenoPermissions.XENOSTATS_SET))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("stat", StringArgumentType.word())
                                        .suggests(STAT_SUGGESTIONS)
                                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                                .executes(ctx -> write(ctx, true))))))
                .executes(ctx -> limitStatus(ctx.getSource())));
    }

    private static int setLimit(CommandSourceStack source, int requested) {
        XenoServerConfig.statMaxOverride = XenoStatCeiling.store(requested);
        XenoServerConfig.save();
        source.sendSuccess(() -> Component.literal(limitDescription()), true);
        return 1;
    }

    private static int limitStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(limitDescription()
                + " (usage: /xenostats limit <" + XenoStatCeiling.MIN + "-" + XenoStatCeiling.MAX
                + "|off|dmz>)"), false);
        return 1;
    }

    private static String limitDescription() {
        int setting = XenoServerConfig.statMaxOverride;
        if (XenoStatCeiling.deferring(setting)) {
            return "DragonMineZ stat cap: DragonMineZ's own configured value (no Xeno override)";
        }
        if (setting >= XenoStatCeiling.MAX) {
            return "DragonMineZ stat cap: " + format(XenoStatCeiling.MAX)
                    + " — as high as DragonMineZ can hold, because it stores each stat in an int";
        }
        return "DragonMineZ stat cap: " + format(setting) + " (Xeno override)";
    }

    private static int get(CommandSourceStack source, ServerPlayer player) {
        Stats stats = statsOf(player);
        if (stats == null) {
            source.sendFailure(Component.literal(
                    player.getGameProfile().getName() + " has no DragonMineZ character"));
            return 0;
        }
        StringBuilder out = new StringBuilder(player.getGameProfile().getName() + " —");
        for (String stat : STATS) {
            out.append(' ').append(stat.toUpperCase(Locale.ROOT)).append(' ')
                    .append(format(read(stats, stat)));
        }
        String line = out.toString();
        source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    /**
     * Writes one stat, or every stat, on every selected player.
     *
     * @param relative true for {@code add}, where the value is a delta rather than a target
     */
    private static int write(CommandContext<CommandSourceStack> ctx, boolean relative)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        String stat = StringArgumentType.getString(ctx, "stat").toLowerCase(Locale.ROOT);
        int value = IntegerArgumentType.getInteger(ctx, "value");

        boolean all = "all".equals(stat);
        if (!all && !STATS.contains(stat)) {
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown stat '" + stat + "'. Expected one of " + String.join(", ", STATS)
                            + ", or all"));
            return 0;
        }

        int changed = 0;
        for (ServerPlayer player : players) {
            Stats stats = statsOf(player);
            if (stats == null) continue;
            for (String key : all ? STATS : List.of(stat)) {
                // Saturating rather than wrapping: "add 2000000000" twice on a stat that is already
                // high must land on the ceiling, not on a negative number.
                long target = relative ? (long) read(stats, key) + value : value;
                stats.setStat(key, (int) Math.max(0L, Math.min(XenoStatCeiling.MAX, target)));
            }
            // DragonMineZ's own reapply and its own sync packet: the attributes behind these stats
            // and every tracking client are updated exactly as DMZ updates them itself.
            StatsData data = dataOf(player);
            if (data != null) {
                data.reapplyStatAttributes();
            }
            sync(player);
            changed++;
        }

        int count = changed;
        String label = all ? "all stats" : stat.toUpperCase(Locale.ROOT);
        String verb = relative ? "adjusted" : "set";
        ctx.getSource().sendSuccess(() -> Component.literal(
                verb + " " + label + " on " + count + " player" + (count == 1 ? "" : "s")), true);
        return count;
    }

    private static int read(Stats stats, String key) {
        return switch (key) {
            case "str" -> stats.getStrength();
            case "skp" -> stats.getStrikePower();
            case "res" -> stats.getResistance();
            case "vit" -> stats.getVitality();
            case "pwr" -> stats.getKiPower();
            case "ene" -> stats.getEnergy();
            default -> 0;
        };
    }

    private static StatsData dataOf(ServerPlayer player) {
        try {
            return StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Stats statsOf(ServerPlayer player) {
        StatsData data = dataOf(player);
        return data == null ? null : data.getStats();
    }

    private static void sync(ServerPlayer player) {
        try {
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
        } catch (Throwable ignored) {
            // A sync that fails costs a stale client until the next DragonMineZ sync, never the write.
        }
    }

    /** Grouped, because a nine-digit stat is unreadable as a bare run of digits in chat. */
    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
