package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.features.playerrole.PlayerRoleId;
import net.bullettrain.xenopixelsmod.features.playerrole.PlayerRoleService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Operator commands for persistent player roles (cosmetic / trainer gating).
 *
 * <pre>
 * /xenorole get &lt;player&gt;
 * /xenorole set &lt;player&gt; none|angel
 * /xenorole clear &lt;player&gt;
 * </pre>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlayerRoleCommands {
    private static final SuggestionProvider<CommandSourceStack> ROLE_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(PlayerRoleId.values())
                            .map(PlayerRoleId::id)
                            .collect(Collectors.toList()),
                    builder);

    private PlayerRoleCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenorole")
                .then(Commands.literal("get")
                        .requires(XenoPermissions.require(XenoPermissions.ROLE_GET))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> get(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .requires(XenoPermissions.require(XenoPermissions.ROLE_SET))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests(ROLE_SUGGESTIONS)
                                        .executes(ctx -> set(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "role"))))))
                .then(Commands.literal("clear")
                        .requires(XenoPermissions.require(XenoPermissions.ROLE_SET))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> clear(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .executes(ctx -> help(ctx.getSource())));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Usage: /xenorole get <player> | set <player> none|angel | clear <player>\n"
                        + "Player roles are cosmetic + trainer gating only (no combat modifiers)."),
                false);
        return 1;
    }

    private static int get(CommandSourceStack source, ServerPlayer player) {
        PlayerRoleId role = PlayerRoleService.get(player);
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().getName() + " role: " + role.id()), false);
        return 1;
    }

    private static int set(CommandSourceStack source, ServerPlayer player, String rawRole) {
        PlayerRoleId parsed = PlayerRoleId.parse(rawRole);
        String normalized = rawRole == null ? "" : rawRole.trim().toLowerCase(Locale.ROOT);
        boolean known = false;
        for (PlayerRoleId candidate : PlayerRoleId.values()) {
            if (candidate.id().equals(normalized)) {
                known = true;
                break;
            }
        }
        if (!known) {
            source.sendFailure(Component.literal(
                    "Unknown role '" + rawRole + "'. Use none or angel."));
            return 0;
        }
        PlayerRoleService.grant(player, parsed, "admin:/xenorole");
        source.sendSuccess(() -> Component.literal(
                "Set " + player.getGameProfile().getName() + " role to " + parsed.id()), true);
        return 1;
    }

    private static int clear(CommandSourceStack source, ServerPlayer player) {
        PlayerRoleService.revoke(player, "admin:/xenorole");
        source.sendSuccess(() -> Component.literal(
                "Cleared role for " + player.getGameProfile().getName()), true);
        return 1;
    }
}
