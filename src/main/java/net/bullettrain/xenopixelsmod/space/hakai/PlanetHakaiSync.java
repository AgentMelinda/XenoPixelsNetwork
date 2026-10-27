package net.bullettrain.xenopixelsmod.space.hakai;

import net.minecraft.network.FriendlyByteBuf;

/** Version 3 planet burn: chart body identity, fixed duration and the original server start time. */
public record PlanetHakaiSync(long revision, long startedTick, long sampledTick, PlanetHakaiBurn burn) {
    public PlanetHakaiSync {
        java.util.Objects.requireNonNull(burn);
        if (revision < 0 || startedTick > sampledTick) throw new IllegalArgumentException("invalid planet sweep clock");
    }
    public static void encode(PlanetHakaiSync p, FriendlyByteBuf b) {
        b.writeVarLong(p.revision); b.writeLong(p.startedTick); b.writeLong(p.sampledTick);
        PlanetHakaiBurn.encode(p.burn, b);
    }
    public static PlanetHakaiSync decode(FriendlyByteBuf b) {
        return new PlanetHakaiSync(b.readVarLong(), b.readLong(), b.readLong(), PlanetHakaiBurn.decode(b));
    }
    public double progressAt(double serverTime) {
        if (!burn.active()) return 1;
        return Math.clamp((Math.max(sampledTick, serverTime) - startedTick) / burn.durationTicks(), 0, 1);
    }
    public boolean newerThan(PlanetHakaiSync previous) {
        return previous == null || revision > previous.revision;
    }
}
