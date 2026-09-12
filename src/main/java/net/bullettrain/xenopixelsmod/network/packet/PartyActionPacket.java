package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.config.XenoPartyConfig;
import net.bullettrain.xenopixelsmod.features.party.PartyManager;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;
import java.util.function.Supplier;

/** Compact server-authoritative action channel used by the party screen and P/H controls. */
public final class PartyActionPacket {
    public enum Action {
        REQUEST_SYNC, INVITE, ACCEPT, ACCEPT_CONFIRM, DECLINE, LEAVE, DISBAND, TOGGLE_PVP,
        KICK, PROMOTE, CHAT, START_OBJECTIVE, PING, TOGGLE_SHARE_QUESTS
    }

    /** Cached because {@code values()} allocates a fresh array on every decode. */
    private static final Action[] ACTIONS = Action.values();

    private final Action action;
    private final UUID targetId;
    private final int entityId;
    private final String text;

    public PartyActionPacket(Action action) { this(action, null, -1, ""); }
    public PartyActionPacket(Action action, UUID targetId) { this(action, targetId, -1, ""); }
    public PartyActionPacket(Action action, String text) { this(action, null, -1, text); }
    public PartyActionPacket(Action action, int entityId) { this(action, null, entityId, ""); }

    public PartyActionPacket(Action action, UUID targetId, int entityId, String text) {
        this.action = action;
        this.targetId = targetId;
        this.entityId = entityId;
        this.text = text == null ? "" : text;
    }

    public PartyActionPacket(FriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        action = ordinal >= 0 && ordinal < ACTIONS.length ? ACTIONS[ordinal] : Action.REQUEST_SYNC;
        targetId = buf.readBoolean() ? buf.readUUID() : null;
        entityId = buf.readVarInt() - 1;
        text = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action.ordinal());
        buf.writeBoolean(targetId != null);
        if (targetId != null) buf.writeUUID(targetId);
        buf.writeVarInt(entityId + 1);
        buf.writeUtf(text.length() > 256 ? text.substring(0, 256) : text, 256);
    }

    public static void handle(PartyActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender == null) {
            ctx.get().setPacketHandled(true);
            return;
        }
        ctx.get().enqueueWork(() -> apply(sender, msg));
        ctx.get().setPacketHandled(true);
    }

    private static void apply(ServerPlayer sender, PartyActionPacket msg) {
        String error = switch (msg.action) {
            case REQUEST_SYNC -> { PartyManager.sendState(sender); yield null; }
            case INVITE -> {
                ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(msg.text.strip());
                yield target == null ? "That player is not online" : PartyManager.invite(sender, target);
            }
            case ACCEPT -> PartyManager.accept(sender, false);
            case ACCEPT_CONFIRM -> PartyManager.accept(sender, true);
            case DECLINE -> PartyManager.decline(sender);
            case LEAVE -> PartyManager.leave(sender);
            case DISBAND -> PartyManager.disband(sender);
            case TOGGLE_PVP -> PartyManager.toggleFriendlyFire(sender);
            case CHAT -> PartyManager.chat(sender, msg.text);
            case START_OBJECTIVE -> PartyManager.startObjective(sender, msg.text) ? null : "Quest integration is not available";
            case KICK -> {
                yield PartyManager.kick(sender, msg.targetId);
            }
            case PROMOTE -> {
                ServerPlayer target = msg.targetId == null ? null : sender.getServer().getPlayerList().getPlayer(msg.targetId);
                yield target == null ? "That party member is offline" : PartyManager.promote(sender, target);
            }
            case PING -> ping(sender, msg.entityId);
            case TOGGLE_SHARE_QUESTS -> PartyManager.toggleShareQuests(sender);
        };
        if (error == null) return;
        // Every successful path already resyncs the whole party, which includes the sender, so an
        // unconditional resend here just doubled every action's packet count. A rejected action
        // syncs nothing, so the sender is refreshed to undo any optimistic client-side change.
        sender.displayClientMessage(net.minecraft.network.chat.Component.literal("§c" + error), true);
        PartyManager.sendState(sender);
    }

    private static String ping(ServerPlayer sender, int entityId) {
        UUID partyId = PartyManager.partyOf(sender);
        if (partyId == null) return "You are not in a party";
        Entity target = sender.serverLevel().getEntity(entityId);
        if (!(target instanceof LivingEntity living) || !living.isAlive()) return "No valid target to ping";
        if (target instanceof ServerPlayer player && PartyManager.sameParty(sender, player)) {
            return "Party members cannot be marked as hostile targets";
        }
        if (sender.distanceToSqr(target) > XenoPartyConfig.pingRange * XenoPartyConfig.pingRange) {
            return "Target is too far away";
        }
        PartyManager.touch(sender.getServer(), partyId);
        long expires = sender.serverLevel().getGameTime() + XenoPartyConfig.pingDurationTicks;
        PartyPingPacket packet = new PartyPingPacket(sender.getUUID(), target.getUUID(), target.getId(),
                target.getName().getString(), expires);
        for (UUID id : PartyManager.membersOf(sender)) {
            ServerPlayer member = sender.getServer().getPlayerList().getPlayer(id);
            if (member != null && member.serverLevel() == sender.serverLevel()) ModNetwork.sendToPlayer(member, packet);
        }
        // No syncParty: the marker went out on its own packet and the roster is unchanged.
        return null;
    }
}
