package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * C2S: permanently remove a Xeno NPC.
 *
 * <p>This exists because {@code /kill} is deliberately refused on Xeno NPCs, and because it was
 * never a real delete in the first place - {@code XenoNpcEntity.die()} schedules a respawn, so a
 * killed NPC came back a few seconds later. Deletion cancels that respawn and then discards the
 * entity, so nothing re-creates it.
 *
 * <p>Guards match {@link XenoNpcSavePacket} exactly - operator level 2, within 64 blocks, matching
 * revision - and every rejection answers through {@link NpcProfileSaveResultPacket} rather than
 * returning silently, which is the lesson protocol 64 recorded.
 */
public final class XenoNpcDeletePacket {

    private final int entityId;
    private final int expectedRevision;

    public XenoNpcDeletePacket(int entityId, int expectedRevision) {
        this.entityId = entityId;
        this.expectedRevision = expectedRevision;
    }

    public XenoNpcDeletePacket(FriendlyByteBuf buf) {
        entityId = buf.readVarInt();
        expectedRevision = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(expectedRevision);
    }

    public static void handle(XenoNpcDeletePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player == null) {
                return;
            }
            if (!player.hasPermissions(2)) {
                reject(player, NpcProfileSaveResultPacket.NOT_PERMITTED);
                return;
            }
            if (!(player.level().getEntity(packet.entityId) instanceof XenoNpcEntity npc)) {
                reject(player, NpcProfileSaveResultPacket.GONE);
                return;
            }
            if (player.distanceToSqr(npc) > 64.0 * 64.0) {
                reject(player, NpcProfileSaveResultPacket.TOO_FAR);
                return;
            }
            if (npc.npcData().revision() != packet.expectedRevision) {
                reject(player, NpcProfileSaveResultPacket.STALE);
                return;
            }

            String name = npc.npcData().displayName();
            npc.deletePermanently();

            ModNetwork.sendToPlayer(player,
                    new NpcProfileSaveResultPacket(true, "Deleted " + name));
            player.sendSystemMessage(Component.literal("Deleted Xeno NPC " + name));
        });
        context.get().setPacketHandled(true);
    }

    private static void reject(ServerPlayer player, String reason) {
        ModNetwork.sendToPlayer(player, new NpcProfileSaveResultPacket(false, reason));
    }
}
