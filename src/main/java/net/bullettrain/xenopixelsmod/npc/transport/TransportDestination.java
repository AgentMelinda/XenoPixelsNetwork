package net.bullettrain.xenopixelsmod.npc.transport;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/**
 * Somewhere a transporter NPC will send a player.
 *
 * <p>The dimension is held as a registry id string rather than a resolved level, for the same
 * reason trade items are held as ids: a destination in a dimension this server does not have must
 * degrade to a destination that cannot be travelled to, not to a crash or to a silent landing
 * somewhere else.
 */
public record TransportDestination(String id, String name, String dimension,
                                   double x, double y, double z, float yaw, Unlock unlock) {

    /** When a destination appears in the list. */
    public enum Unlock {
        /** Always offered. A capital city everybody knows about. */
        ALWAYS,
        /**
         * Offered only once this player has been told about it.
         *
         * <p>Unlocked by travelling from a transporter that offers it, or by an operator command.
         * My NPCs distinguishes "by discovery" from "by prior interaction"; those are not
         * separable without guessing at what discovery means here, so there is one rule rather
         * than two that differ by a coin flip.
         */
        VISITED;

        public static Unlock parse(String raw) {
            if (raw != null && raw.trim().equalsIgnoreCase("visited")) {
                return VISITED;
            }
            return ALWAYS;
        }
    }

    /** The vanilla world border caps here; past it a teleport names a place that cannot load. */
    public static final double MAX_COORDINATE = 30_000_000.0;

    public TransportDestination {
        id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        name = name == null || name.isBlank() ? id : name.trim();
        dimension = dimension == null ? "" : dimension.trim().toLowerCase(Locale.ROOT);
        x = clamp(x);
        y = clamp(y);
        z = clamp(z);
        yaw = Float.isFinite(yaw) ? yaw : 0.0f;
        unlock = unlock == null ? Unlock.ALWAYS : unlock;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            // A NaN coordinate would pass every range check and land the player nowhere.
            return 0.0;
        }
        return Math.max(-MAX_COORDINATE, Math.min(MAX_COORDINATE, value));
    }

    /** An empty row in the editor. */
    public static TransportDestination empty() {
        return new TransportDestination("", "", "minecraft:overworld", 0, 64, 0, 0, Unlock.ALWAYS);
    }

    /**
     * Whether this can actually be travelled to.
     *
     * <p>A destination needs an id, to be remembered by, and a dimension, to arrive in. A row
     * missing either is one an operator is still filling in, and offering it would put a button in
     * front of a player that cannot work.
     */
    public boolean usable() {
        return !id.isEmpty() && !dimension.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Name", name);
        tag.putString("Dim", dimension);
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Yaw", yaw);
        tag.putString("Unlock", unlock.name());
        return tag;
    }

    public static TransportDestination load(CompoundTag tag) {
        if (tag == null) {
            return empty();
        }
        return new TransportDestination(
                tag.getString("Id"),
                tag.getString("Name"),
                tag.getString("Dim"),
                tag.getDouble("X"),
                tag.getDouble("Y"),
                tag.getDouble("Z"),
                tag.getFloat("Yaw"),
                Unlock.parse(tag.getString("Unlock")));
    }
}
