package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.features.party.PartyManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * {@code /xenoparty} — invite, accept, leave, list.
 *
 * <p>Commands rather than a GUI because a party is something you form mid-fight with someone
 * standing next to you, and a screen that pauses the world to do it is the wrong shape. They are
 * also usable from a server console and by anyone with a keybind macro.
 *
 * <p>No permission level: forming a party is not an administrative act, and requiring op would
 * make the feature useless on the servers it exists for.
 */
public final class PartyCommands {

    private PartyCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("xenoparty")
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer self = ctx.getSource().getPlayerOrException();
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    String error = PartyManager.invite(self, target);
                                    return reply(ctx.getSource(), error,
                                            "Invited " + target.getName().getString());
                                })))
                .then(Commands.literal("accept")
                        .executes(ctx -> {
                            ServerPlayer self = ctx.getSource().getPlayerOrException();
                            return reply(ctx.getSource(), PartyManager.accept(self), "Joined the party");
                        }))
                .then(Commands.literal("leave")
                        .executes(ctx -> {
                            ServerPlayer self = ctx.getSource().getPlayerOrException();
                            return reply(ctx.getSource(), PartyManager.leave(self, false), "Left the party");
                        }))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            ServerPlayer self = ctx.getSource().getPlayerOrException();
                            List<UUID> members = PartyManager.membersOf(self.getUUID());
                            if (members.isEmpty()) {
                                ctx.getSource().sendFailure(Component.literal("You are not in a party"));
                                return 0;
                            }
                            StringBuilder sb = new StringBuilder("Party (" + members.size() + "):");
                            for (UUID id : members) {
                                ServerPlayer member = ctx.getSource().getServer().getPlayerList().getPlayer(id);
                                sb.append("\n §7- §f")
                                        .append(member == null ? "(offline)" : member.getName().getString());
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            return 1;
                        }));
    }

    /** Brigadier convention: 1 for success, 0 for a handled failure. */
    private static int reply(CommandSourceStack source, String error, String success) {
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a" + success), false);
        return 1;
    }
}
