package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.mmoecon.MmoEconBridge;
import net.bullettrain.xenopixelsmod.plot.PlotArea;
import net.bullettrain.xenopixelsmod.plot.PlotFlags;
import net.bullettrain.xenopixelsmod.plot.PlotLease;
import net.bullettrain.xenopixelsmod.plot.PlotManager;
import net.bullettrain.xenopixelsmod.plot.PlotRegionIndex;
import net.bullettrain.xenopixelsmod.plot.PlotSale;
import net.bullettrain.xenopixelsmod.plot.PlotSelectionHandler;
import net.bullettrain.xenopixelsmod.plot.PlotYaWP;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * The {@code /plot} command tree: claim, release, sell and inspect plots.
 *
 * <p>Uses no YAWP types, so it registers whether or not YAWP is installed. Without YAWP a claim
 * still succeeds and still sells — it is simply not protected by YAWP.</p>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlotCommands {

    private PlotCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(command("plot"));
        dispatcher.register(command("xenoplot"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return Commands.literal(name)
                .then(Commands.literal("claim").executes(ctx -> claim(ctx.getSource())))
                .then(Commands.literal("unclaim").executes(ctx -> unclaim(ctx.getSource())))
                .then(Commands.literal("here").executes(ctx -> here(ctx.getSource())))
                .then(Commands.literal("info").executes(ctx -> here(ctx.getSource())))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("tp").executes(ctx -> teleport(ctx.getSource())))
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.0D))
                                .executes(ctx -> sell(ctx.getSource(),
                                        DoubleArgumentType.getDouble(ctx, "price")))))
                .then(Commands.literal("unlist").executes(ctx -> unlist(ctx.getSource())))
                .then(Commands.literal("buy").executes(ctx -> buy(ctx.getSource())))
                .then(Commands.literal("rent")
                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.0D))
                                .then(Commands.argument("period", DoubleArgumentType.doubleArg(0.01D))
                                        .executes(ctx -> rent(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "price"),
                                                DoubleArgumentType.getDouble(ctx, "period"))))))
                .then(Commands.literal("unrent").executes(ctx -> unrent(ctx.getSource())))
                .then(Commands.literal("lease").executes(ctx -> lease(ctx.getSource())))
                .then(Commands.literal("flags").executes(ctx -> flags(ctx.getSource())))
                .then(flagCommand("build", PlotFlags.ALLOW_BUILD))
                .then(flagCommand("containers", PlotFlags.ALLOW_CONTAINERS))
                .then(flagCommand("interact", PlotFlags.ALLOW_INTERACT))
                .then(flagCommand("pvp", PlotFlags.ALLOW_PVP))
                .then(flagCommand("entry", PlotFlags.ALLOW_ENTRY));
    }

    /**
     * One flag subcommand: {@code /plot <name> <true|false>}.
     *
     * <p>The flag name is a literal rather than a suggestion over an enum, so an unknown flag is
     * a parse failure instead of a silently ignored word.</p>
     */
    private static LiteralArgumentBuilder<CommandSourceStack> flagCommand(String name, int flag) {
        return Commands.literal(name)
                .then(Commands.literal("on").executes(ctx -> setFlag(ctx.getSource(), flag, true)))
                .then(Commands.literal("off").executes(ctx -> setFlag(ctx.getSource(), flag, false)));
    }

    private static int claim(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        PlotSelectionHandler.Result result = PlotSelectionHandler.claimSelection(player, PlotFlags.DEFAULT);
        switch (result) {
            case CLAIMED -> {
                source.sendSuccess(() -> Component.literal(
                        "§aPlot claimed from your WorldEdit selection."), true);
                return 1;
            }
            case NO_WORLDEDIT -> source.sendFailure(Component.literal(
                    "WorldEdit is not installed, so there is no wand to select with."));
            case NO_SELECTION -> source.sendFailure(Component.literal(
                    "Make a WorldEdit selection first; only X and Z are used."));
            case OVERLAP -> source.sendFailure(Component.literal(
                    "That selection overlaps an existing plot."));
            case ENCLOSED -> source.sendFailure(Component.literal(
                    "That selection sits entirely inside an existing plot."));
            case ENCLOSING -> source.sendFailure(Component.literal(
                    "That selection would swallow an existing plot; shrink it."));
            default -> source.sendFailure(Component.literal("You must be on a server level."));
        }
        return 0;
    }

    private static int unclaim(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        PlotArea plot = plotAt(player);
        if (plot == null) {
            source.sendFailure(Component.literal("You are not standing in a plot."));
            return 0;
        }
        if (!plot.ownedBy(player.getUUID())) {
            source.sendFailure(Component.literal("You do not own this plot."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        PlotYaWP.remove(level, plot);
        PlotManager.get(level.getServer()).remove(plot);
        PlotSale.get(level.getServer()).unlist(plot);
        source.sendSuccess(() -> Component.literal("§aPlot released."), true);
        return 1;
    }

    private static int sell(CommandSourceStack source, double price) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        PlotArea plot = plotAt(player);
        if (plot == null || !plot.ownedBy(player.getUUID())) {
            source.sendFailure(Component.literal("You are not standing in a plot you own."));
            return 0;
        }
        PlotSale.get(player.serverLevel().getServer()).list(plot, price);
        source.sendSuccess(() -> Component.literal(String.format(
                "§aPlot listed for §f%.2f§a. Anyone standing in it can §f/plot buy§a.", price)), true);
        return 1;
    }

    private static int unlist(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        PlotArea plot = plotAt(player);
        if (plot == null) {
            source.sendFailure(Component.literal("You are not standing in a plot."));
            return 0;
        }
        boolean removed = PlotSale.get(player.serverLevel().getServer()).unlist(plot);
        if (!removed) {
            source.sendFailure(Component.literal("This plot is not for sale."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§aListing withdrawn."), true);
        return 1;
    }

    private static int buy(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        PlotArea plot = plotAt(player);
        if (plot == null) {
            source.sendFailure(Component.literal("You are not standing in a plot."));
            return 0;
        }
        PlotSale.Result result = PlotSale.buy(player, plot);
        if (result == PlotSale.Result.SUCCESS) {
            source.sendSuccess(() -> Component.literal("§aPlot purchased."), true);
            return 1;
        }
        source.sendFailure(Component.literal(describe(result)));
        return 0;
    }

    private static String describe(PlotSale.Result result) {
        return switch (result) {
            case NO_ECONOMY -> "MMO Econ is not installed, so the sale cannot settle.";
            case INSUFFICIENT_FUNDS -> "You cannot afford this plot.";
            case TRANSFER_FAILED -> "The payment failed; the plot was not transferred.";
            case NOT_FOR_SALE -> "This plot is not for sale.";
            case ALREADY_OWNER -> "You already own this plot.";
            default -> "There is no claimed plot here.";
        };
    }

    /**
     * Lists every flag with its current value.
     *
     * <p>Read-only, and a sibling of the five {@code /plot <flag> on|off} verbs rather than a
     * replacement for them. A player who does not know which flags exist can ask; a player who
     * knows the one they want still types it directly.</p>
     */
    private static int flags(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        PlotArea plot = plotAt(player);
        if (plot == null) {
            source.sendFailure(Component.literal("You are not standing in a plot."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§bPlot flags:"), false);
        for (FlagOption option : FlagOption.values()) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "§7 %s §7| %s§7 | §f/plot %s %s",
                    option.name,
                    PlotFlags.has(plot.flags(), option.bit) ? "§aon" : "§coff",
                    option.name,
                    PlotFlags.has(plot.flags(), option.bit) ? "off" : "on")), false);
        }
        return FlagOption.values().length;
    }

    /** The flag set, in the order the command tree exposes it. */
    private enum FlagOption {
        build(PlotFlags.ALLOW_BUILD),
        containers(PlotFlags.ALLOW_CONTAINERS),
        interact(PlotFlags.ALLOW_INTERACT),
        pvp(PlotFlags.ALLOW_PVP),
        entry(PlotFlags.ALLOW_ENTRY);

        private final int bit;
        private final String name;

        FlagOption(int bit) {
            this.bit = bit;
            this.name = name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static int setFlag(CommandSourceStack source, int flag, boolean enabled) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        PlotArea plot = plotAt(player);
        if (plot == null || !plot.ownedBy(player.getUUID())) {
            source.sendFailure(Component.literal("You are not standing in a plot you own."));
            return 0;
        }
        PlotManager manager = PlotManager.get(player.serverLevel().getServer());
        manager.setFlags(plot, PlotFlags.set(plot.flags(), flag, enabled));
        source.sendSuccess(() -> Component.literal(String.format(
                "§aFlag set to §f%s§a.", enabled ? "on" : "off")), true);
        return 1;
    }

    private static int here(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        PlotArea plot = plotAt(player);
        if (plot == null) {
            source.sendSuccess(() -> Component.literal("§7No plot here."), false);
            return 0;
        }
        ServerLevel level = player.serverLevel();
        StringBuilder flags = new StringBuilder();
        appendFlag(flags, "build", plot.flags(), PlotFlags.ALLOW_BUILD);
        appendFlag(flags, "containers", plot.flags(), PlotFlags.ALLOW_CONTAINERS);
        appendFlag(flags, "interact", plot.flags(), PlotFlags.ALLOW_INTERACT);
        appendFlag(flags, "pvp", plot.flags(), PlotFlags.ALLOW_PVP);
        appendFlag(flags, "entry", plot.flags(), PlotFlags.ALLOW_ENTRY);
        PlotLease.Lease lease = PlotLease.get(level.getServer())
                .at(plot.dimension(), plot.minX(), plot.minZ());
        String rented = lease == null ? "" : String.format(
                " §7| §erented by §f%s §7at §f%.2f§7/%ds",
                lease.renter(), lease.pricePerPeriod(), lease.periodTicks() / 20);
        source.sendSuccess(() -> Component.literal(String.format(
                "§bPlot §f%d,%d §7to §f%d,%d §7(%dx%d, %d blocks) §7| owner §f%s §7| YAWP §f%s%s%s",
                plot.minX(), plot.minZ(), plot.maxX(), plot.maxZ(),
                plot.width(), plot.length(), plot.area(),
                plot.owner(), regionStatus(level, plot), flags, rented)), false);
        return 1;
    }

    /**
     * How a plot's YAWP region stands.
     *
     * <p>Three answers, not two. {@code never-synced} is normal — the plot has simply not been
     * mirrored yet. {@code absent} is drift: a region was written and has since been deleted or
     * renamed outside XenoPixels. Without YAWP the answer is {@code yawp-unavailable}, because
     * then no region can exist for any plot and reporting every one as drifted would be wrong.</p>
     */
    private static String regionStatus(ServerLevel level, PlotArea plot) {
        if (!PlotYaWP.available()) {
            return "yawp-unavailable";
        }
        return switch (PlotRegionIndex.get(level.getServer()).state(level, plot)) {
            case NEVER_SYNCED -> "never-synced";
            case PRESENT -> "present";
            case ABSENT -> "absent (drift)";
        };
    }

    /**
     * Starts a lease on the plot the player is standing in.
     *
     * <p>The renter is the invoking player and the owner is the plot's current owner, so a renter
     * never has to name themselves and cannot rent on someone else's behalf. The period is given
     * in seconds and stored in ticks.</p>
     */
    private static int rent(CommandSourceStack source, double price, double periodSeconds) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        PlotArea plot = plotAt(player);
        if (plot == null) {
            source.sendFailure(Component.literal("You are not standing in a plot."));
            return 0;
        }
        if (plot.ownedBy(player.getUUID())) {
            source.sendFailure(Component.literal("You own this plot; there is nothing to rent."));
            return 0;
        }
        int periodTicks = (int) Math.max(1L, Math.round(periodSeconds * 20.0D));
        ServerLevel level = player.serverLevel();
        boolean started = PlotLease.get(level.getServer()).lease(
                plot, player.getUUID(), price, periodTicks, level.getServer().getTickCount());
        if (!started) {
            source.sendFailure(Component.literal("Those lease terms are not usable."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "§aLeased for §f%.2f§a every §f%.0fs§a; you are charged while online.",
                price, periodSeconds)), true);
        return 1;
    }

    /** Ends the lease the invoking player is party to, whether as renter or as owner. */
    private static int unrent(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        PlotArea plot = plotAt(player);
        if (plot == null) {
            source.sendFailure(Component.literal("You are not standing in a plot."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        PlotLease.Lease lease = PlotLease.get(level.getServer())
                .at(plot.dimension(), plot.minX(), plot.minZ());
        if (lease == null) {
            source.sendFailure(Component.literal("This plot is not leased."));
            return 0;
        }
        if (!lease.renter().equals(player.getUUID()) && !lease.owner().equals(player.getUUID())) {
            source.sendFailure(Component.literal("You are not party to this lease."));
            return 0;
        }
        PlotLease.get(level.getServer()).unlease(plot.dimension(), plot.minX(), plot.minZ());
        source.sendSuccess(() -> Component.literal("§aLease ended."), true);
        return 1;
    }

    private static int lease(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        PlotArea plot = plotAt(player);
        if (plot == null) {
            source.sendFailure(Component.literal("You are not standing in a plot."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        PlotLease.Lease lease = PlotLease.get(level.getServer())
                .at(plot.dimension(), plot.minX(), plot.minZ());
        if (lease == null) {
            source.sendSuccess(() -> Component.literal("§7This plot is not leased."), false);
            return 0;
        }
        long dueIn = Math.max(0L, lease.nextDueTick() - level.getServer().getTickCount());
        source.sendSuccess(() -> Component.literal(String.format(
                "§bLease: §f%s§7 pays §f%s §7every §f%d ticks§7; next due in §f%d ticks",
                lease.renter(), MmoEconBridge.format(MmoEconBridge.toUnits(lease.pricePerPeriod())),
                lease.periodTicks(), dueIn)), false);
        return 1;
    }

    private static void appendFlag(StringBuilder out, String name, int flags, int flag) {
        out.append(" §7| ").append(name).append(' ')
                .append(PlotFlags.has(flags, flag) ? "§aon" : "§coff");
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        List<PlotArea> plots = PlotManager.get(level.getServer()).all();
        if (plots.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No plots have been claimed."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§bPlots:"), false);
        for (PlotArea plot : plots) {
            PlotSale.Listing listing = PlotSale.get(level.getServer())
                    .at(plot.dimension(), plot.minX(), plot.minZ());
            String price = listing == null ? "" : String.format(" §7| §efor sale §f%.2f", listing.price());
            source.sendSuccess(() -> Component.literal(String.format(
                    "§7 %s §f%d,%d §7→ §f%d,%d §7| owner §f%s%s",
                    plot.dimension(), plot.minX(), plot.minZ(),
                    plot.maxX(), plot.maxZ(), plot.owner(), price)), false);
        }
        return plots.size();
    }

    private static int teleport(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        PlotArea plot = plotAt(player);
        if (plot == null) {
            source.sendFailure(Component.literal("You are not standing in a plot."));
            return 0;
        }
        ServerLevel target = player.getServer().getLevel(
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, plot.dimension()));
        if (target == null) {
            source.sendFailure(Component.literal("That plot's dimension is not loaded."));
            return 0;
        }
        double x = (plot.minX() + plot.maxX()) / 2.0D + 0.5D;
        double z = (plot.minZ() + plot.maxZ()) / 2.0D + 0.5D;
        int y = target.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                (int) x, (int) z);
        player.teleportTo(target, x, y, z, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("§aTeleported to plot centre."), false);
        return 1;
    }

    /** The plot the player is standing in, or {@code null}. */
    private static PlotArea plotAt(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        return PlotManager.get(level.getServer()).at(
                level.dimension().location(), player.blockPosition().getX(), player.blockPosition().getZ());
    }
}