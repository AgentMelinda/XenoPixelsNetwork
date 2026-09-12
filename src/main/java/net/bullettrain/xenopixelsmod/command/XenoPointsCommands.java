package net.bullettrain.xenopixelsmod.command;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ResourceSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.math.BigDecimal;
import java.util.Collection;

/** Quest- and command-block-friendly counterpart to DragonMineZ's training-points command. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoPointsCommands {
    private static final BigDecimal MAX_POINTS = BigDecimal.valueOf(Float.MAX_VALUE - 1.0f);

    private XenoPointsCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("xenopoints").requires(source -> source.hasPermission(2));
        for (Operation operation : Operation.values()) {
            root.then(Commands.literal(operation.id)
                    .then(Commands.argument("amount", StringArgumentType.word())
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .executes(context -> apply(
                                            context.getSource(),
                                            EntityArgument.getPlayers(context, "targets"),
                                            StringArgumentType.getString(context, "amount"),
                                            operation)))
                            // Fallback for the NPC mods. Brigadier only reaches this when the
                            // vanilla selector fails to parse, so "@a", "@p", a username and a UUID
                            // all still take the branch above and behave exactly as before; this
                            // catches "@dp" and display names, which the selector cannot resolve.
                            .then(Commands.argument("target", StringArgumentType.greedyString())
                                    .executes(context -> applyToNamed(
                                            context.getSource(),
                                            StringArgumentType.getString(context, "target"),
                                            StringArgumentType.getString(context, "amount"),
                                            operation)))));
        }
        dispatcher.register(root);

        // Merge a fallback branch into DragonMineZ's own command. Its normal selector path stays
        // untouched; this branch only handles NPC-specific @dp tokens and display names.
        var dmzRoot = Commands.literal("dmzpoints").requires(source -> source.hasPermission(2));
        for (Operation operation : Operation.values()) {
            dmzRoot.then(Commands.literal(operation.id)
                    .then(Commands.argument("amount", StringArgumentType.word())
                            .then(Commands.argument("npcTarget", StringArgumentType.greedyString())
                                    .executes(context -> applyToNamed(
                                            context.getSource(),
                                            StringArgumentType.getString(context, "npcTarget"),
                                            StringArgumentType.getString(context, "amount"),
                                            operation)))));
        }
        dispatcher.register(dmzRoot);
    }

    /**
     * Resolves the NPC mods' own player tokens, then applies the operation.
     *
     * <p>CustomNPCs and My NPCs rewrite {@code @dp} to the interacting player's <em>display name</em>
     * and skip the rewrite entirely when no player is in scope, so what reaches a command is either
     * a literal {@code @dp} or a nickname — neither of which vanilla's selector can resolve.
     */
    private static int applyToNamed(CommandSourceStack source, String rawTarget, String rawAmount,
                                    Operation operation) {
        ServerPlayer resolved = resolve(source, rawTarget);
        if (resolved == null) {
            source.sendFailure(Component.literal(NpcTargetToken.isDialogPlayer(rawTarget)
                    ? "No player to apply " + rawTarget + " to: this command ran with no player in "
                            + "scope, so the NPC mod had nobody to substitute"
                    : "No online player matches: " + rawTarget));
            return 0;
        }
        java.util.List<ServerPlayer> targets = NpcTargetToken.isDialogPlayer(rawTarget)
                ? net.bullettrain.xenopixelsmod.compat.npc.NpcPartyReward.recipients(resolved)
                : java.util.List.of(resolved);
        return apply(source, targets, rawAmount, operation);
    }

    private static ServerPlayer resolve(CommandSourceStack source, String rawTarget) {
        if (NpcTargetToken.isDialogPlayer(rawTarget)) {
            // The player the command is running for, when the NPC mod left the token in place.
            if (source.getEntity() instanceof ServerPlayer player) return player;
            if (source.getServer() == null) return null;
            ServerPlayer nearest = null;
            double nearestDistance = 4.0;
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                if (player.level() != source.getLevel()) continue;
                double distance = player.distanceToSqr(source.getPosition());
                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    nearest = player;
                }
            }
            return nearest;
        }
        if (source.getServer() == null) return null;
        var online = source.getServer().getPlayerList().getPlayers();
        // Username first: a nickname that collides with somebody else's account must not win.
        for (ServerPlayer player : online) {
            if (NpcTargetToken.matchesUsername(rawTarget, player.getGameProfile().getName())) {
                return player;
            }
        }
        for (ServerPlayer player : online) {
            if (NpcTargetToken.matches(rawTarget, player.getGameProfile().getName(),
                    player.getDisplayName() == null ? "" : player.getDisplayName().getString())) {
                return player;
            }
        }
        return null;
    }

    private static int apply(CommandSourceStack source, Collection<ServerPlayer> targets,
                             String rawAmount, Operation operation) {
        Float amount = parseAmount(rawAmount);
        if (amount == null) {
            source.sendFailure(Component.literal("Amount must be a non-negative whole number within the DragonMineZ points range: " + rawAmount));
            return 0;
        }

        int changed = 0;
        for (ServerPlayer player : targets) {
            if (applyToPlayer(player, amount, operation)) changed++;
        }

        int result = changed;
        source.sendSuccess(() -> Component.literal(
                "DragonMineZ points: " + operation.id + " " + rawAmount + " for " + result + " player(s)"), true);
        return changed;
    }

    /** Direct scripting counterpart to {@code /xenopoints add}, without command permissions. */
    public static boolean addPoints(ServerPlayer player, int amount) {
        return player != null && amount > 0 && applyToPlayer(player, amount, Operation.ADD);
    }

    private static boolean applyToPlayer(ServerPlayer player, float amount, Operation operation) {
        var stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (stats == null) return false;

        float current = stats.getResources().getTrainingPoints();
        stats.getResources().setTrainingPoints(operation.apply(current, amount));
        NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(player), player);
        return true;
    }

    static Float parseAmount(String rawAmount) {
        if (rawAmount == null || !rawAmount.matches("\\d+")) return null;
        try {
            BigDecimal parsed = new BigDecimal(rawAmount);
            if (parsed.compareTo(MAX_POINTS) > 0) return null;
            return parsed.floatValue();
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    enum Operation {
        SET("set") {
            @Override float apply(float current, float amount) { return amount; }
        },
        ADD("add") {
            @Override float apply(float current, float amount) {
                return Math.min(Float.MAX_VALUE - 1.0f, validCurrent(current) + amount);
            }
        },
        REMOVE("remove") {
            @Override float apply(float current, float amount) {
                return Math.max(0.0f, validCurrent(current) - amount);
            }
        };

        private final String id;

        Operation(String id) {
            this.id = id;
        }

        abstract float apply(float current, float amount);

        static float validCurrent(float current) {
            return Float.isFinite(current) ? Math.max(0.0f, current) : 0.0f;
        }
    }
}
