package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public final class XenoNpcEditorLockPacket {
    private final int entityId;
    private final int expectedRevision;
    private final boolean locked;

    public XenoNpcEditorLockPacket(int entityId, int expectedRevision, boolean locked) {
        this.entityId = entityId;
        this.expectedRevision = expectedRevision;
        this.locked = locked;
    }

    public XenoNpcEditorLockPacket(FriendlyByteBuf buf) {
        entityId = buf.readVarInt();
        expectedRevision = buf.readVarInt();
        locked = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(expectedRevision);
        buf.writeBoolean(locked);
    }

    public static void handle(XenoNpcEditorLockPacket packet,
                              Supplier<NetworkEvent.Context> context) {
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

            NpcCombatProfile profile = NpcCombatProfile.read(npc);
            if (profile.editingLocked != packet.locked) {
                profile.editingLocked = packet.locked;
                profile.write(npc);
                npc.npcData().markEdited();
            }
            ModNetwork.sendToPlayer(player, new NpcProfileSaveResultPacket(true,
                    (packet.locked ? "Locked" : "Unlocked")
                            + " Xeno NPC revision " + npc.npcData().revision()));
        });
        context.get().setPacketHandled(true);
    }

    private static void reject(ServerPlayer player, String reason) {
        ModNetwork.sendToPlayer(player, new NpcProfileSaveResultPacket(false, reason));
    }
}
