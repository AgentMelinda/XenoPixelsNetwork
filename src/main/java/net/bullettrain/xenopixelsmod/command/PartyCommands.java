package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.SyncPartyDataPacket;
import net.bullettrain.xenopixelsmod.party.XenoPartyCapabilities;
import net.bullettrain.xenopixelsmod.party.XenoPartyData;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;

/**
 * Commands for managing parties: create, disband, invite, kick, leave, and quest sharing.
 */
public class PartyCommands {

    public static void register(CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("party")
                .then(Commands.literal("create")
                        .executes(PartyCommands::createParty))
                .then(Commands.literal("disband")
                        .requires(src -> src.hasPermission(2))
                        .executes(PartyCommands::disbandParty))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(PartyCommands::invitePlayer)))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(PartyCommands::kickPlayer)))
                .then(Commands.literal("leave")
                        .executes(PartyCommands::leaveParty))
                .then(Commands.literal("quest")
                        .then(Commands.argument("enable", BoolArgumentType.bool())
                                .executes(PartyCommands::toggleQuestSharing))));
    }

    private static int createParty(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        player.getCapability(XenoPartyCapabilities.PARTY_DATA).ifPresent(partyData -> {
            if (partyData.hasParty()) {
                player.displayClientMessage(Component.literal("§cYou are already in a party!"), true);
                return;
            }
            if (partyData.createParty(player)) {
                player.displayClientMessage(Component.literal("§aParty created! You are the leader."), true);
                syncToPlayer(player, partyData);
            } else {
                player.displayClientMessage(Component.literal("§cFailed to create party."), true);
            }
        });
        return 1;
    }

    private static int disbandParty(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        target.getCapability(XenoPartyCapabilities.PARTY_DATA).ifPresent(partyData -> {
            if (!partyData.hasParty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("§7" + target.getName().getString() + " is not in a party."), false);
                return;
            }
            partyData.disbandParty();
            ctx.getSource().sendSuccess(() -> Component.literal("§eDisbanded " + target.getName().getString() + "'s party."), false);
            syncToPlayer(target, partyData);
        });
        return 1;
    }

    private static int invitePlayer(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        
        player.getCapability(XenoPartyCapabilities.PARTY_DATA).ifPresent(partyData -> {
            if (!partyData.hasParty()) {
                player.displayClientMessage(Component.literal("§cYou must create a party first!"), true);
                return;
            }
            if (!partyData.isLeader(player.getUUID())) {
                player.displayClientMessage(Component.literal("§cOnly the leader can invite players!"), true);
                return;
            }
            if (partyData.getPartySize() >= XenoPartyData.MAX_PARTY_SIZE) {
                player.displayClientMessage(Component.literal("§cParty is full!"), true);
                return;
            }
            if (partyData.isMember(target.getUUID())) {
                player.displayClientMessage(Component.literal("§c" + target.getName().getString() + " is already in your party!"), true);
                return;
            }
            
            // Auto-accept invite for simplicity (can be enhanced with pending invites)
            if (partyData.addMember(target)) {
                player.displayClientMessage(Component.literal("§a" + target.getName().getString() + " joined your party!"), true);
                target.displayClientMessage(Component.literal("§aYou joined " + player.getName().getString() + "'s party!"), true);
                
                // Sync to all party members
                syncToAllMembers(player.level, partyData);
            } else {
                player.displayClientMessage(Component.literal("§cFailed to invite " + target.getName().getString()), true);
            }
        });
        return 1;
    }

    private static int kickPlayer(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        
        player.getCapability(XenoPartyCapabilities.PARTY_DATA).ifPresent(partyData -> {
            if (!partyData.hasParty()) {
                player.displayClientMessage(Component.literal("§cYou are not in a party!"), true);
                return;
            }
            if (!partyData.isLeader(player.getUUID())) {
                player.displayClientMessage(Component.literal("§cOnly the leader can kick players!"), true);
                return;
            }
            
            if (partyData.removeMember(target.getUUID())) {
                player.displayClientMessage(Component.literal("§e" + target.getName().getString() + " was kicked from the party."), true);
                target.displayClientMessage(Component.literal("§cYou were kicked from the party."), true);
                
                // Sync to all remaining members
                syncToAllMembers(player.level, partyData);
                syncToPlayer(target, target.getCapability(XenoPartyCapabilities.PARTY_DATA).orElse(new XenoPartyData()));
            } else {
                player.displayClientMessage(Component.literal("§c" + target.getName().getString() + " is not in your party!"), true);
            }
        });
        return 1;
    }

    private static int leaveParty(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        player.getCapability(XenoPartyCapabilities.PARTY_DATA).ifPresent(partyData -> {
            if (!partyData.hasParty()) {
                player.displayClientMessage(Component.literal("§cYou are not in a party!"), true);
                return;
            }
            
            boolean wasLeader = partyData.isLeader(player.getUUID());
            partyData.removeMember(player.getUUID());
            
            if (wasLeader) {
                player.displayClientMessage(Component.literal("§eYou left the party and it has been disbanded."), true);
            } else {
                player.displayClientMessage(Component.literal("§eYou left the party."), true);
            }
            
            // Sync to all remaining members
            if (partyData.hasParty()) {
                syncToAllMembers(player.level, partyData);
            }
            syncToPlayer(player, player.getCapability(XenoPartyCapabilities.PARTY_DATA).orElse(new XenoPartyData()));
        });
        return 1;
    }

    private static int toggleQuestSharing(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean enable = BoolArgumentType.getBool(ctx, "enable");
        
        player.getCapability(XenoPartyCapabilities.PARTY_DATA).ifPresent(partyData -> {
            if (!partyData.hasParty()) {
                player.displayClientMessage(Component.literal("§cYou must be in a party to use quest sharing!"), true);
                return;
            }
            if (!partyData.isLeader(player.getUUID())) {
                player.displayClientMessage(Component.literal("§cOnly the leader can toggle quest sharing!"), true);
                return;
            }
            
            partyData.setQuestSharingEnabled(enable);
            player.displayClientMessage(Component.literal(enable ? "§aQuest sharing enabled!" : "§cQuest sharing disabled."), true);
            syncToAllMembers(player.level, partyData);
        });
        return 1;
    }

    private static void syncToPlayer(ServerPlayer player, XenoPartyData partyData) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncPartyDataPacket(
            partyData.getPartyId(),
            partyData.getLeaderUuid(),
            partyData.getLeaderName(),
            partyData.getMemberUuids(),
            partyData.getMemberNames(),
            partyData.isQuestSharingEnabled()
        ));
    }

    private static void syncToAllMembers(net.minecraft.world.level.Level level, XenoPartyData partyData) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return;
        
        for (ServerPlayer player : sl.players()) {
            if (partyData.isMember(player.getUUID())) {
                syncToPlayer(player, partyData);
            }
        }
    }
}
