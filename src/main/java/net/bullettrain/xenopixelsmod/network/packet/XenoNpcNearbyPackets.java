package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.npc.NpcNearbyList;
import net.bullettrain.xenopixelsmod.npc.XenoNpcNearbyService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Packets of the Nearby NPCs wand screen (protocol 99). */
public final class XenoNpcNearbyPackets {
    private XenoNpcNearbyPackets() {}

    static void sendList(ServerPlayer player) {
        ModNetwork.sendToPlayer(player, new List_(XenoNpcNearbyService.list(player)));
    }

    /** C2S: open or refresh the screen. */
    public record Request() {
        public Request(FriendlyByteBuf buf) { this(); }
        public void encode(FriendlyByteBuf buf) {}
        public static void handle(Request msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "You need operator permission to manage Xeno NPCs."));
                    return;
                }
                sendList(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** S2C: the NPCs in range, nearest first. */
    public record List_(List<NpcNearbyList.Entry> entries) {
        public List_(FriendlyByteBuf buf) { this(read(buf)); }

        private static List<NpcNearbyList.Entry> read(FriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), NpcNearbyList.MAX_ENTRIES);
            List<NpcNearbyList.Entry> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(new NpcNearbyList.Entry(buf.readVarInt(), buf.readUtf(64), buf.readUtf(32),
                        buf.readDouble(), buf.readVarInt(), buf.readBoolean()));
            }
            return out;
        }

        public void encode(FriendlyByteBuf buf) {
            int n = Math.min(entries.size(), NpcNearbyList.MAX_ENTRIES);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                NpcNearbyList.Entry e = entries.get(i);
                buf.writeVarInt(e.entityId());
                buf.writeUtf(e.name().length() > 64 ? e.name().substring(0, 64) : e.name(), 64);
                buf.writeUtf(e.role(), 32);
                buf.writeDouble(e.distance());
                buf.writeVarInt(e.revision());
                buf.writeBoolean(e.frozen());
            }
        }

        public static void handle(List_ msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> net.bullettrain.xenopixelsmod.client.ClientScreens.openNpcNearby
                    .accept(msg.entries));
            ctx.get().setPacketHandled(true);
        }
    }

    /** C2S: one action; the server answers with a fresh list. */
    public record Action(XenoNpcNearbyService.Action action, int entityId, String role) {
        public Action(FriendlyByteBuf buf) {
            this(buf.readEnum(XenoNpcNearbyService.Action.class), buf.readVarInt(), buf.readUtf(32));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(action);
            buf.writeVarInt(entityId);
            buf.writeUtf(role == null ? "" : role, 32);
        }

        public static void handle(Action msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                XenoNpcNearbyService.perform(player, msg.action, msg.entityId, msg.role);
                if (player.hasPermissions(2)) sendList(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
