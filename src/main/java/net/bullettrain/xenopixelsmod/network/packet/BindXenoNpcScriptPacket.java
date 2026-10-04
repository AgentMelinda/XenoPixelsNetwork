package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Writes the script screen's container (tabs, loaded scripts, language, enabled) onto an NPC when
 * the screen was opened with the script tool. The server re-checks permission, entity type, range
 * and every id rather than trusting the screen. An empty container clears the NPC's scripts.
 */
public final class BindXenoNpcScriptPacket {
    private final int entityId;
    private final CompoundTag container;

    public BindXenoNpcScriptPacket(int entityId, CompoundTag container) {
        this.entityId = entityId;
        this.container = container == null ? new CompoundTag() : container;
    }

    public BindXenoNpcScriptPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeNbt(container);
    }

    public static void handle(BindXenoNpcScriptPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal(
                        "You need operator permission to bind NPC scripts."));
                return;
            }
            if (!(player.level().getEntity(packet.entityId) instanceof XenoNpcEntity npc)
                    || player.distanceToSqr(npc) > 64.0 * 64.0) {
                return;
            }
            CompoundTag tag = packet.container;
            if (tag.contains(NpcScriptContainer.TAG_TABS)) {
                String invalid = NpcScriptContainer.reject(tag);
                if (invalid != null) {
                    player.sendSystemMessage(Component.literal("§7Scripts not saved: §f" + invalid));
                    return;
                }
            }
            NpcCombatProfile profile = NpcCombatProfile.read(npc);
            profile.scripts = NpcScriptContainer.read(tag);
            profile.scriptId = profile.scripts.firstScriptId();
            profile.write(npc);
            net.bullettrain.xenopixelsmod.npc.script.NpcScriptHost.forget(npc.getUUID());
            int tabs = profile.scripts.tabs().size();
            player.sendSystemMessage(Component.literal(tabs == 0
                    ? "Scripts cleared from this NPC."
                    : "Saved " + tabs + " script tab" + (tabs == 1 ? "" : "s") + " on this NPC."));
        });
        context.get().setPacketHandled(true);
    }
}
