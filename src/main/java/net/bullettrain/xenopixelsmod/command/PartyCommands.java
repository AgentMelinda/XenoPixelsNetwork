package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.features.party.PartyManager;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.OpenPartyScreenPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Complete command surface for the DMZ-backed XenoParty frontend. */
public final class PartyCommands {
    private PartyCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("xenoparty")
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> action(ctx.getSource(), PartyManager.invite(
                                self(ctx.getSource()), EntityArgument.getPlayer(ctx, "player")), "Invite sent"))))
                .then(Commands.literal("accept")
                        .then(Commands.literal("confirm").executes(ctx -> action(ctx.getSource(),
                                PartyManager.accept(self(ctx.getSource()), true), "Joined the party")))
                        .executes(ctx -> action(ctx.getSource(),
                                PartyManager.accept(self(ctx.getSource()), false), "Joined the party")))
                .then(Commands.literal("decline").executes(ctx -> action(ctx.getSource(),
                        PartyManager.decline(self(ctx.getSource())), "Invite declined")))
                .then(Commands.literal("leave").executes(ctx -> action(ctx.getSource(),
                        PartyManager.leave(self(ctx.getSource())), "Left the party")))
                .then(Commands.literal("kick").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> action(ctx.getSource(), PartyManager.kick(
                                self(ctx.getSource()), EntityArgument.getPlayer(ctx, "player")), "Player removed"))))
                .then(Commands.literal("promote").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> action(ctx.getSource(), PartyManager.promote(
                                self(ctx.getSource()), EntityArgument.getPlayer(ctx, "player")), "Leader promoted"))))
                .then(Commands.literal("disband").executes(ctx -> action(ctx.getSource(),
                        PartyManager.disband(self(ctx.getSource())), "Party disbanded")))
                .then(Commands.literal("pvp").executes(ctx -> action(ctx.getSource(),
                        PartyManager.toggleFriendlyFire(self(ctx.getSource())), "Friendly-fire setting changed")))
                .then(Commands.literal("ping").executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("Use the H party-ping key while locked on or aiming at a target"));
                    return 0;
                }))
                .then(Commands.literal("objective")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> action(ctx.getSource(),
                                        PartyManager.startObjective(self(ctx.getSource()),
                                                StringArgumentType.getString(ctx, "id"))
                                                ? null : "No party objective provider accepted that id",
                                        "Party objective started"))))
                .then(Commands.literal("screen").executes(ctx -> {
                    ServerPlayer player = self(ctx.getSource());
                    PartyManager.sendState(player);
                    ModNetwork.sendToPlayer(player, new OpenPartyScreenPacket());
                    return 1;
                }))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§bXenoParty§7: invite, accept, decline, leave, list, kick, promote, disband, pvp, ping, objective, screen"), false);
                    return 1;
                });
    }

    public static LiteralArgumentBuilder<CommandSourceStack> chatAlias() {
        return Commands.literal("pc")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> action(ctx.getSource(), PartyManager.chat(self(ctx.getSource()),
                                StringArgumentType.getString(ctx, "message")), null)));
    }

    private static int list(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = self(source);
        var members = PartyManager.membersOf(player);
        if (members.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a party"));
            return 0;
        }
        StringBuilder text = new StringBuilder("§bParty (" + members.size() + "):");
        for (var id : members) {
            ServerPlayer member = source.getServer().getPlayerList().getPlayer(id);
            text.append("\n §7- ").append(member == null ? "§8" + id.toString().substring(0, 8) + " (offline)"
                    : (PartyManager.isLeader(member) ? "§6★ " : "§f") + member.getName().getString());
        }
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static ServerPlayer self(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static int action(CommandSourceStack source, String error, String success) {
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        if (success != null) source.sendSuccess(() -> Component.literal("§a" + success), false);
        return 1;
    }
}
