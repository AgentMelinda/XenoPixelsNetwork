package net.bullettrain.xenopixelsmod.combat;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Authoritative target identity and server clock for the version 3 body sweep stream. */
public record HakaiSweepSync(ResourceLocation dimension, UUID target, long revision,
                             long startedTick, long sampledTick, HakaiSweepTiming timing) {
    /** Shared entry point: the standalone NPC distribution does not ship the space subsystem. */
    public static void register() {
        net.bullettrain.xenopixelsmod.api.network.AddonNetwork.register(
                ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "hakai_sweep_sync_v3"),
                HakaiSweepSync.class, net.bullettrain.xenopixelsmod.api.network.AddonPacketDirection.CLIENTBOUND,
                HakaiSweepSync::encode, HakaiSweepSync::decode,
                (packet, context) -> context.enqueueWork(() -> {
                    if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT)
                        net.bullettrain.xenopixelsmod.client.combat.HakaiSweepFx.apply(packet);
                }));
    }
    public HakaiSweepSync {
        java.util.Objects.requireNonNull(dimension);
        java.util.Objects.requireNonNull(target);
        java.util.Objects.requireNonNull(timing);
        if (revision < 0 || startedTick > sampledTick) throw new IllegalArgumentException("invalid sweep clock");
    }

    public static void encode(HakaiSweepSync p, FriendlyByteBuf b) {
        b.writeResourceLocation(p.dimension); b.writeUUID(p.target); b.writeVarLong(p.revision);
        b.writeLong(p.startedTick); b.writeLong(p.sampledTick); HakaiSweepTiming.encode(p.timing, b);
    }

    public static HakaiSweepSync decode(FriendlyByteBuf b) {
        return new HakaiSweepSync(b.readResourceLocation(), b.readUUID(), b.readVarLong(),
                b.readLong(), b.readLong(), HakaiSweepTiming.decode(b));
    }

    public float progressAt(double serverTime) {
        return HakaiSweepTiming.at(timing.progress(), timing.ratePerTick(), Math.max(0, serverTime - sampledTick));
    }

    public boolean matches(ResourceLocation world, UUID uuid) {
        return dimension.equals(world) && target.equals(uuid);
    }

    public boolean newerThan(HakaiSweepSync previous) {
        return previous == null || revision > previous.revision;
    }

    /** Repeated clears have no live owner, but must preserve the completed target's endpoint ring. */
    public boolean resetsSweep(HakaiSweepSync previous) {
        return previous != null && (!target.equals(previous.target)
                || startedTick != previous.startedTick && !terminal());
    }

    public boolean terminal() {
        return timing.progress() <= 0f && timing.ratePerTick() <= 0f;
    }
}
