package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.inventory.NpcInventoryService;
import net.bullettrain.xenopixelsmod.npc.inventory.XenoNpcInventoryMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.function.Supplier;

/**
 * C2S: "open the slots for the NPC I am editing".
 *
 * <p>A request, not an open. The client names an entity id and nothing else; whether that entity is
 * an NPC, whether the player may edit it, how close they are and which Curios slots it has are all
 * read server-side. The worst a crafted packet can ask for is the slots screen of an NPC the sender
 * could already have opened by hand.
 *
 * <p>Checked in the order {@code XenoNpcBankPacket} established: the entity exists and is one of
 * ours, the player has the permission the editor itself requires, and they are close enough to be
 * standing at it. A refusal answers the player, because a button that does nothing is
 * indistinguishable from a broken one.
 */
public record XenoNpcInventoryOpenPacket(int entityId) {

    /**
     * How far a player may be from the NPC and still open it.
     *
     * <p>Shared with {@link XenoNpcInventoryMenu#stillValid}, which owns the number: walking away
     * closes the screen and this refuses to open it, and both should happen at the same distance.
     * Without it a stale entity id would let somebody rearrange an NPC from across the world.
     */
    private static final double MAX_DISTANCE_SQ = XenoNpcInventoryMenu.MAX_DISTANCE_SQ;

    public XenoNpcInventoryOpenPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            // The same bar the editor screen itself is behind. Rearranging an NPC's gear is an
            // authoring action, not something any player who can reach it may do.
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal(
                        "You need operator permission to edit Xeno NPCs."));
                return;
            }
            Entity entity = player.level().getEntity(entityId);
            if (!(entity instanceof XenoNpcEntity npc) || !npc.isAlive()) {
                return;
            }
            if (player.distanceToSqr(npc) > MAX_DISTANCE_SQ) {
                player.sendSystemMessage(Component.literal("You are too far away."));
                return;
            }
            NpcInventoryService.open(player, npc);
        });
        ctx.setPacketHandled(true);
    }
}
