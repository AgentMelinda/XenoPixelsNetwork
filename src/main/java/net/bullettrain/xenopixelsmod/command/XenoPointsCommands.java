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
                                            operation)))));
        }
        dispatcher.register(root);
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
