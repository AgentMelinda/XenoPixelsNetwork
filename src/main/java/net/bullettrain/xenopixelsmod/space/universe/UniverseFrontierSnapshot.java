package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.network.FriendlyByteBuf;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded client view of append-only physical slots hosting revealed long-coordinate logical systems. */
public record UniverseFrontierSnapshot(long fieldSeed, boolean warpAllowed, List<Binding> bindings) {
    public static final int MAX_BINDINGS = FrontierBindings.MAX_BINDINGS;

    public record Binding(InfiniteUniverseField.Cell logical, UniverseField.Cell physical) {
        public Binding {
            Objects.requireNonNull(logical, "logical");
            Objects.requireNonNull(physical, "physical");
        }
    }

    public UniverseFrontierSnapshot {
        if (bindings == null || bindings.size() > MAX_BINDINGS)
            throw new IllegalArgumentException("frontier snapshot exceeds " + MAX_BINDINGS + " bindings");
        bindings = List.copyOf(bindings);
        Set<InfiniteUniverseField.Cell> logical = new HashSet<>();
        Set<UniverseField.Cell> physical = new HashSet<>();
        for (Binding binding : bindings) {
            if (!logical.add(binding.logical()) || !physical.add(binding.physical()))
                throw new IllegalArgumentException("duplicate logical or physical frontier binding");
            if (InfiniteUniverseField.systemAt(fieldSeed, binding.logical()) == null)
                throw new IllegalArgumentException("frontier binding refers to an empty logical cell");
            if (UniverseField.systemAt(fieldSeed, binding.physical()) != null)
                throw new IllegalArgumentException("frontier binding would replace a natural finite system");
        }
    }

    public static UniverseFrontierSnapshot from(long fieldSeed, boolean warpAllowed,
                                                 Map<InfiniteUniverseField.Cell, UniverseField.Cell> bindings) {
        Objects.requireNonNull(bindings, "bindings");
        if (bindings.size() > MAX_BINDINGS)
            throw new IllegalArgumentException("frontier snapshot exceeds " + MAX_BINDINGS + " bindings");
        return new UniverseFrontierSnapshot(fieldSeed, warpAllowed, bindings.entrySet().stream()
                .map(entry -> new Binding(entry.getKey(), entry.getValue())).toList());
    }

    public static void encode(UniverseFrontierSnapshot packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.fieldSeed());
        buf.writeBoolean(packet.warpAllowed());
        buf.writeVarInt(packet.bindings().size());
        for (Binding binding : packet.bindings()) {
            buf.writeLong(binding.logical().x());
            buf.writeLong(binding.logical().z());
            buf.writeVarInt(binding.physical().x());
            buf.writeVarInt(binding.physical().z());
        }
    }

    public static UniverseFrontierSnapshot decode(FriendlyByteBuf buf) {
        long seed = buf.readLong();
        boolean warp = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_BINDINGS)
            throw new IllegalArgumentException("invalid frontier binding count: " + count);
        var bindings = new java.util.ArrayList<Binding>(count);
        for (int i = 0; i < count; i++) {
            long logicalX = buf.readLong(), logicalZ = buf.readLong();
            int physicalX = buf.readVarInt(), physicalZ = buf.readVarInt();
            bindings.add(new Binding(new InfiniteUniverseField.Cell(logicalX, logicalZ),
                    new UniverseField.Cell(physicalX, physicalZ)));
        }
        return new UniverseFrontierSnapshot(seed, warp, bindings);
    }
}
