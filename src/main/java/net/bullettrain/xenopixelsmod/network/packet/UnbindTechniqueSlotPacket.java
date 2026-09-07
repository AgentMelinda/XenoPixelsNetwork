package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.techniques.Techniques;
import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.combat.technique.TechniqueSlotBind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Client → server: empty one DragonMineZ technique slot.
 *
 * <p>Deliberately does not go through DMZ's {@code EquipTechniqueC2S}. That path calls
 * {@code Techniques.equipOrSwapTechnique}, which treats an empty id as "already equipped": it looks
 * for a slot whose id equals {@code ""}, finds the first <em>vacant</em> one, and swaps with it. So
 * asking DMZ to clear a slot relocates the technique to the first hole instead of removing it,
 * which is why unbinding appeared to do nothing at all.
 *
 * <p>{@link TechniqueSlotBind#unbind} writes the slot directly with the semantics the menu implies,
 * and the stats sync mirrors what {@code ChaseFlightSystem} does after it edits DMZ state.
 */
public class UnbindTechniqueSlotPacket {

    private final int slotIndex;

    public UnbindTechniqueSlotPacket(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public UnbindTechniqueSlotPacket(FriendlyByteBuf buf) {
        this.slotIndex = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.slotIndex);
    }

    public static void handle(UnbindTechniqueSlotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
            if (data == null) return;
            Techniques techniques = data.getTechniques();
            if (techniques == null) return;
            String[] slots = techniques.getEquippedSlots();
            if (slots == null || msg.slotIndex < 0 || msg.slotIndex >= slots.length) return;
            if (slots[msg.slotIndex] == null || slots[msg.slotIndex].isEmpty()) return;

            TechniqueSlotBind.unbind(slots, msg.slotIndex);
            try {
                NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
            } catch (Throwable ignored) {
                // Sync is best effort; the slot array itself is already authoritative.
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
