package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.yawp.KiRegionFlags;
import net.bullettrain.xenopixelsmod.compat.yawp.YawpRegionLookup;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.Arrays;
import java.util.Map;

/**
 * Operator commands for our per-region ki-griefing flags.
 *
 * <p>These are <b>our</b> flags, not YAWP's — YAWP keys its command layer to a
 * {@code RegionFlag} enum and cannot accept third-party flags, so they are stored and
 * evaluated by us and merely keyed to YAWP region names.
 *
 * <p>Uses no YAWP types, so the command registers whether or not YAWP is installed. Without
 * YAWP the flags simply never match a region.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class KiRegionFlagCommands {

    private KiRegionFlagCommands() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        KiRegionFlags.load(event.getServer());
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(command("kiflag"));
        dispatcher.register(command("xenokiflag"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> list(ctx.getSource()))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("here").executes(ctx -> here(ctx.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("region", StringArgumentType.word())
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(KiRegionFlags.Target.values())
                                                        .map(t -> t.name().toLowerCase()), builder))
                                        .then(Commands.argument("state", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(KiRegionFlags.State.values())
                                                                .map(s -> s.name().toLowerCase()), builder))
                                                .executes(ctx -> set(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "region"),
                                                        StringArgumentType.getString(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "state")))))))
                // Removal is its own verb rather than only "set … default". Clearing a flag is
                // what an operator reaches for after a mistake, and YAWP's own
                // "/wp … flag remove" cannot reach these flags at all (see the class javadoc),
                // so this is the only way to do it.
                .then(Commands.literal("remove")
                        .then(Commands.argument("region", StringArgumentType.word())
                                .executes(ctx -> remove(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "region"), null))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(KiRegionFlags.Target.values())
                                                        .map(t -> t.name().toLowerCase()), builder))
                                        .executes(ctx -> remove(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "region"),
                                                StringArgumentType.getString(ctx, "target"))))));
    }

    /** Names of every flag target, for error messages that cannot drift from the enum. */
    private static String targetNames() {
        return Arrays.stream(KiRegionFlags.Target.values())
                .map(t -> "'" + t.name().toLowerCase() + "'")
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    /** One line of flag state for a region, covering every target. */
    private static String describe(String dim, String region) {
        StringBuilder line = new StringBuilder();
        for (KiRegionFlags.Target target : KiRegionFlags.Target.values()) {
            line.append(" §7| ").append(target.name().toLowerCase()).append(" §f")
                    .append(KiRegionFlags.get(dim, region, target).name().toLowerCase());
        }
        return line.toString();
    }

    /**
     * Clear one target, or every target when {@code targetName} is null.
     *
     * <p>Clearing means setting {@link KiRegionFlags.State#DEFAULT}, which drops the region's
     * entry entirely once no target is set — so a removed flag falls back to the configured
     * YAWP flag mapping rather than becoming an explicit allow.
     */
    private static int remove(CommandSourceStack source, String region, String targetName) {
        String dim = source.getLevel().dimension().location().toString();
        if (targetName == null) {
            for (KiRegionFlags.Target target : KiRegionFlags.Target.values()) {
                KiRegionFlags.set(dim, region, target, KiRegionFlags.State.DEFAULT);
            }
            source.sendSuccess(() -> Component.literal(String.format(
                    "§aCleared all ki flags §7for region §f%s §7(%s)", region, dim)), true);
            return KiRegionFlags.Target.values().length;
        }
        KiRegionFlags.Target target = KiRegionFlags.Target.byName(targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Target must be one of " + targetNames()));
            return 0;
        }
        KiRegionFlags.set(dim, region, target, KiRegionFlags.State.DEFAULT);
        source.sendSuccess(() -> Component.literal(String.format(
                "§aCleared ki-griefing-%s §7for region §f%s §7(%s)",
                target.name().toLowerCase(), region, dim)), true);
        return 1;
    }

    /** Report the region the operator is standing in and its current ki flags. */
    private static int here(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player, or use 'set <region> ...'"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        String region = YawpRegionLookup.regionNameAt(level, pos).orElse(null);
        if (region == null) {
            source.sendSuccess(() -> Component.literal(
                    "§7No active YAWP region here — ki griefing follows the configured flag mapping"),
                    false);
            return 0;
        }
        String dim = level.dimension().location().toString();
        // Built from the Target enum so a new target cannot be silently left out of the
        // readout, which is what happened to "masters" when it was added.
        source.sendSuccess(() -> Component.literal(
                String.format("§bRegion §f%s §7(%s)", region, dim) + describe(dim, region)), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        Map<String, KiRegionFlags.Entry> entries = KiRegionFlags.entries();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "§7No ki region flags set; every region uses the configured YAWP flag mapping"),
                    false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§bKi region flags:"), false);
        entries.forEach((key, entry) -> source.sendSuccess(() -> Component.literal(
                "§7 " + key
                        + " §7| players §f" + entry.players.toLowerCase()
                        + " §7| mobs §f" + entry.mobs.toLowerCase()
                        + " §7| masters §f" + entry.masters.toLowerCase()), false));
        return entries.size();
    }

    private static int set(CommandSourceStack source, String region, String targetName, String stateName) {
        KiRegionFlags.Target target = KiRegionFlags.Target.byName(targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Target must be one of " + targetNames()));
            return 0;
        }
        KiRegionFlags.State state = KiRegionFlags.State.byName(stateName);
        // byName falls back to DEFAULT, so reject an unrecognised word rather than silently
        // clearing a flag the operator meant to set.
        boolean known = Arrays.stream(KiRegionFlags.State.values())
                .anyMatch(s -> s.name().equalsIgnoreCase(stateName));
        if (!known) {
            source.sendFailure(Component.literal("State must be 'allowed', 'denied' or 'default'"));
            return 0;
        }
        String dim = source.getLevel().dimension().location().toString();
        KiRegionFlags.set(dim, region, target, state);
        source.sendSuccess(() -> Component.literal(String.format(
                "§aki-griefing-%s §7for region §f%s §7(%s) §7set to §f%s",
                target.name().toLowerCase(), region, dim, state.name().toLowerCase())), true);
        return 1;
    }
}
