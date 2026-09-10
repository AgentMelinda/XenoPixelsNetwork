package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;
import java.util.function.Supplier;

/** Server-to-client form identity + stats + DMZ hair used by the CustomNPC appearance bridge. */
public final class NpcAppearancePacket {
    private static final int HAIR_CHUNK = NpcCombatProfile.HAIR_CODE_CHUNK;
    private final UUID entityUuid;
    private final String race;
    private final String formGroup;
    private final String form;
    private final boolean hairEnabled;
    private final String hairCode;
    private final String hairColor;
    private final int strength;
    private final int strikePower;
    private final int resistance;
    private final int vitality;
    private final int kiPower;
    private final int energy;
    private final boolean authoritative;
    private final int auraColor;
    private final float auraScale;
    private final CompoundTag dmzAppearance;
    private final CompoundTag visualOptions;
    private final String skinPlayer;
    private final String skinUrl;
    private final String skinUuid;

    public NpcAppearancePacket(UUID entityUuid, String race, String formGroup, String form) {
        this(entityUuid, race, formGroup, form, false, "", "", 0, 0, 0, 0, 0, 0, true,
                0, 1.0f, new CompoundTag(), new CompoundTag(), "", "", "");
    }

    public NpcAppearancePacket(UUID entityUuid, String race, String formGroup, String form,
                               boolean hairEnabled, String hairCode, String hairColor,
                               int strength, int strikePower, int resistance,
                               int vitality, int kiPower, int energy, boolean authoritative,
                               int auraColor, float auraScale, CompoundTag dmzAppearance,
                               CompoundTag visualOptions) {
        this(entityUuid, race, formGroup, form, hairEnabled, hairCode, hairColor,
                strength, strikePower, resistance, vitality, kiPower, energy, authoritative,
                auraColor, auraScale, dmzAppearance, visualOptions, "", "", "");
    }

    public NpcAppearancePacket(UUID entityUuid, String race, String formGroup, String form,
                               boolean hairEnabled, String hairCode, String hairColor,
                               int strength, int strikePower, int resistance,
                               int vitality, int kiPower, int energy, boolean authoritative,
                               int auraColor, float auraScale, CompoundTag dmzAppearance,
                               CompoundTag visualOptions,
                               String skinPlayer, String skinUrl, String skinUuid) {
        this.entityUuid = entityUuid;
        this.race = safe(race);
        this.formGroup = safe(formGroup);
        this.form = safe(form);
        this.hairEnabled = hairEnabled;
        this.hairCode = safe(hairCode);
        this.hairColor = NpcCombatProfile.canonicalizeHairColor(hairColor);
        this.strength = strength;
        this.strikePower = strikePower;
        this.resistance = resistance;
        this.vitality = vitality;
        this.kiPower = kiPower;
        this.energy = energy;
        this.authoritative = authoritative;
        this.auraColor = auraColor & 0xFFFFFF;
        this.auraScale = NpcCombatProfile.clampAuraScale(auraScale);
        this.dmzAppearance = dmzAppearance == null ? new CompoundTag() : dmzAppearance.copy();
        this.visualOptions = visualOptions == null ? new CompoundTag() : visualOptions.copy();
        this.skinPlayer = safe(skinPlayer);
        this.skinUrl = safe(skinUrl);
        this.skinUuid = safe(skinUuid);
    }

    public NpcAppearancePacket(FriendlyByteBuf buf) {
        entityUuid = buf.readUUID();
        race = buf.readUtf();
        formGroup = buf.readUtf();
        form = buf.readUtf();
        hairEnabled = buf.readBoolean();
        hairColor = buf.readUtf();
        hairCode = readChunks(buf);
        strength = buf.readVarInt();
        strikePower = buf.readVarInt();
        resistance = buf.readVarInt();
        vitality = buf.readVarInt();
        kiPower = buf.readVarInt();
        energy = buf.readVarInt();
        authoritative = buf.readBoolean();
        auraColor = buf.readInt() & 0xFFFFFF;
        auraScale = NpcCombatProfile.clampAuraScale(buf.readFloat());
        CompoundTag appearance = buf.readNbt();
        dmzAppearance = appearance == null ? new CompoundTag() : appearance;
        CompoundTag options = buf.readNbt();
        visualOptions = options == null ? new CompoundTag() : options;
        skinPlayer = buf.readUtf();
        skinUrl = buf.readUtf();
        skinUuid = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(entityUuid);
        buf.writeUtf(race);
        buf.writeUtf(formGroup);
        buf.writeUtf(form);
        buf.writeBoolean(hairEnabled);
        buf.writeUtf(hairColor);
        writeChunks(buf, hairCode);
        buf.writeVarInt(strength);
        buf.writeVarInt(strikePower);
        buf.writeVarInt(resistance);
        buf.writeVarInt(vitality);
        buf.writeVarInt(kiPower);
        buf.writeVarInt(energy);
        buf.writeBoolean(authoritative);
        buf.writeInt(auraColor);
        buf.writeFloat(auraScale);
        buf.writeNbt(dmzAppearance);
        buf.writeNbt(visualOptions);
        buf.writeUtf(skinPlayer);
        buf.writeUtf(skinUrl);
        buf.writeUtf(skinUuid);
    }

    public static void handle(NpcAppearancePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient.apply(
                        msg.entityUuid, msg.race, msg.formGroup, msg.form,
                        msg.hairEnabled, msg.hairCode, msg.hairColor,
                        msg.strength, msg.strikePower, msg.resistance,
                        msg.vitality, msg.kiPower, msg.energy, msg.authoritative,
                        msg.auraColor, msg.auraScale, msg.dmzAppearance, msg.visualOptions,
                        msg.skinPlayer, msg.skinUrl, msg.skinUuid));
        ctx.get().setPacketHandled(true);
    }

    private static void writeChunks(FriendlyByteBuf buf, String value) {
        String s = value == null ? "" : value;
        if (s.isEmpty()) {
            buf.writeVarInt(0);
            return;
        }
        int n = (s.length() + HAIR_CHUNK - 1) / HAIR_CHUNK;
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            int start = i * HAIR_CHUNK;
            buf.writeUtf(s.substring(start, Math.min(s.length(), start + HAIR_CHUNK)), HAIR_CHUNK);
        }
    }

    private static String readChunks(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n <= 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) {
            out.append(buf.readUtf(HAIR_CHUNK));
        }
        return out.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
