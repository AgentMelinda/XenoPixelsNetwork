package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.bullettrain.xenopixelsmod.features.party.PartyObjectiveSnapshot;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Full server-authoritative party state used by the social screen and nearby HUD. */
public final class PartySyncPacket {
    /** Wire cap. {@code XenoPartyConfig.maxMembers} clamps to this so a party cannot outgrow it. */
    public static final int MAX_MEMBERS = 16;
    private static final int MAX_TEXT = 128;

    private final UUID partyId;
    private final List<Member> members;
    private final boolean friendlyFire;
    private final long expiresAtMs;
    private final String pendingInviteFrom;
    private final PartyObjectiveSnapshot objective;

    public PartySyncPacket(UUID partyId, List<Member> members, boolean friendlyFire, long expiresAtMs,
                           String pendingInviteFrom, PartyObjectiveSnapshot objective) {
        this.partyId = partyId;
        this.members = members == null ? List.of() : List.copyOf(members);
        this.friendlyFire = friendlyFire;
        this.expiresAtMs = expiresAtMs;
        this.pendingInviteFrom = safe(pendingInviteFrom, MAX_TEXT);
        this.objective = objective == null ? PartyObjectiveSnapshot.EMPTY : objective;
    }

    public PartySyncPacket(FriendlyByteBuf buf) {
        partyId = buf.readBoolean() ? buf.readUUID() : null;
        int count = Math.max(0, Math.min(MAX_MEMBERS, buf.readVarInt()));
        List<Member> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++) read.add(Member.decode(buf));
        members = List.copyOf(read);
        friendlyFire = buf.readBoolean();
        expiresAtMs = buf.readLong();
        pendingInviteFrom = buf.readUtf(MAX_TEXT);
        objective = new PartyObjectiveSnapshot(
                buf.readUtf(64), buf.readUtf(128), buf.readUtf(MAX_TEXT),
                buf.readVarInt(), buf.readVarInt(), buf.readUtf(32), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(partyId != null);
        if (partyId != null) buf.writeUUID(partyId);
        int count = Math.min(MAX_MEMBERS, members.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) members.get(i).encode(buf);
        buf.writeBoolean(friendlyFire);
        buf.writeLong(expiresAtMs);
        buf.writeUtf(safe(pendingInviteFrom, MAX_TEXT), MAX_TEXT);
        buf.writeUtf(safe(objective.sourceId(), 64), 64);
        buf.writeUtf(safe(objective.objectiveId(), 128), 128);
        buf.writeUtf(safe(objective.title(), MAX_TEXT), MAX_TEXT);
        buf.writeVarInt(Math.max(0, objective.progress()));
        buf.writeVarInt(Math.max(0, objective.goal()));
        buf.writeUtf(safe(objective.state(), 32), 32);
        buf.writeBoolean(objective.canStart());
    }

    public UUID partyId() { return partyId; }
    public List<Member> members() { return members; }
    public boolean friendlyFire() { return friendlyFire; }
    public long expiresAtMs() { return expiresAtMs; }
    public String pendingInviteFrom() { return pendingInviteFrom; }
    public PartyObjectiveSnapshot objective() { return objective; }

    public static void handle(PartySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientScreens.receiveParty.accept(msg));
        ctx.get().setPacketHandled(true);
    }

    private static String safe(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record Member(
            UUID id, String name, boolean online, boolean leader, int entityId, int level,
            float health, float maxHealth, float energy, float maxEnergy,
            float stamina, float maxStamina, int release,
            String form, float sparking, boolean sparkingActive
    ) {
        private void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(safe(name, 64), 64);
            buf.writeBoolean(online);
            buf.writeBoolean(leader);
            buf.writeVarInt(entityId + 1);
            buf.writeVarInt(Math.max(0, level));
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            buf.writeFloat(energy);
            buf.writeFloat(maxEnergy);
            buf.writeFloat(stamina);
            buf.writeFloat(maxStamina);
            buf.writeVarInt(Math.max(0, release));
            buf.writeUtf(safe(form, 64), 64);
            buf.writeFloat(sparking);
            buf.writeBoolean(sparkingActive);
        }

        private static Member decode(FriendlyByteBuf buf) {
            return new Member(buf.readUUID(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt() - 1, buf.readVarInt(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readUtf(64),
                    buf.readFloat(), buf.readBoolean());
        }

        public float hpPercent() { return percent(health, maxHealth); }
        public float kiPercent() { return percent(energy, maxEnergy); }
        public float staminaPercent() { return percent(stamina, maxStamina); }

        private static float percent(float value, float max) {
            return max <= 0f ? 0f : Math.max(0f, Math.min(1f, value / max));
        }
    }
}
