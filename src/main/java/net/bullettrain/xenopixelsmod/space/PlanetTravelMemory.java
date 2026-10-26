package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Server-owned, per-player planet-local positions. Reads do not create or dirty records. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlanetTravelMemory {
    public static final String ROOT = "xenopixelsmod:space";
    public static final double REENTRY_OFFSET = 25.0;
    private PlanetTravelMemory() {}

    public static Vec3 lastLeaving(CompoundTag persisted, String body) {
        return read(persisted, body, "LastLeavingPos");
    }
    public static Vec3 lastEntrance(CompoundTag persisted, String body) {
        return read(persisted, body, "LastEntrancePos");
    }
    public static void leaving(CompoundTag persisted, String body, Vec3 point) {
        write(persisted, body, "LastLeavingPos", point);
    }
    public static void entrance(CompoundTag persisted, String body, Vec3 point) {
        write(persisted, body, "LastEntrancePos", point);
    }
    public static double reentryY(double exit, int minY) {
        double y = exit - REENTRY_OFFSET;
        return Double.isFinite(y) && y >= minY && Math.abs(y) < 30_000_000 ? y : Double.NaN;
    }
    public static Vec3 arrival(CompoundTag persisted, String body, double exit, int minY) {
        return arrival(persisted, body, exit, minY, 0.0, 0.0);
    }
    /** Uses a chart-authored planet-local anchor on first arrival; saved player coordinates take precedence. */
    public static Vec3 arrival(CompoundTag persisted, String body, double exit, int minY,
                               double fallbackX, double fallbackZ) {
        double y = reentryY(exit, minY);
        if (!Double.isFinite(y)) return null;
        Vec3 point = lastLeaving(persisted, body);
        if (point == null) point = lastEntrance(persisted, body);
        if (point != null) return new Vec3(point.x, y, point.z);
        if (!supported(fallbackX) || !supported(fallbackZ)) return null;
        return new Vec3(fallbackX, y, fallbackZ);
    }
    /**
     * Arrival for an authored §24 surface anchor. Remembered horizontal coordinates keep the legacy
     * exit-minus-25 altitude rule; a first arrival uses the exact dimension-local authored point.
     */
    public static Vec3 arrival(CompoundTag persisted, String body, double exit, int minY, int height,
                               double fallbackX, double fallbackZ, Vec3 surfaceAnchor) {
        double y = reentryY(exit, minY);
        if (!Double.isFinite(y)) return null;
        Vec3 remembered = lastLeaving(persisted, body);
        if (remembered == null) remembered = lastEntrance(persisted, body);
        if (remembered != null) return new Vec3(remembered.x, y, remembered.z);
        if (surfaceAnchor != null) {
            double maxY = minY + (double) height;
            if (!valid(surfaceAnchor) || surfaceAnchor.y < minY || surfaceAnchor.y >= maxY) return null;
            return surfaceAnchor;
        }
        return arrival(persisted, body, exit, minY, fallbackX, fallbackZ);
    }
    public static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }
    public static void commit(Player player, String leavingBody, Vec3 departure, String enteringBody) {
        CompoundTag persisted = persisted(player);
        // Validate all positions before touching either record.
        if (leavingBody != null) validate(leavingBody, departure);
        if (enteringBody != null) validate(enteringBody, player.position());
        if (leavingBody != null) leaving(persisted, leavingBody, departure);
        if (enteringBody != null) entrance(persisted, enteringBody, player.position());
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
    private static Vec3 read(CompoundTag persisted, String body, String name) {
        CompoundTag record = persisted.getCompound(ROOT).getCompound("Planets").getCompound(body).getCompound(name);
        if (!record.contains("X", Tag.TAG_DOUBLE) || !record.contains("Y", Tag.TAG_DOUBLE)
                || !record.contains("Z", Tag.TAG_DOUBLE)) return null;
        Vec3 value = new Vec3(record.getDouble("X"), record.getDouble("Y"), record.getDouble("Z"));
        return valid(value) ? value : null;
    }
    private static void write(CompoundTag persisted, String body, String name, Vec3 point) {
        validate(body, point);
        CompoundTag root = persisted.getCompound(ROOT);
        CompoundTag planets = root.getCompound("Planets");
        CompoundTag record = planets.getCompound(body);
        CompoundTag pos = new CompoundTag();
        pos.putDouble("X", point.x); pos.putDouble("Y", point.y); pos.putDouble("Z", point.z);
        record.put(name, pos);
        planets.put(body, record); root.put("Planets", planets); root.putInt("Version", 1);
        persisted.put(ROOT, root);
    }
    private static void validate(String body, Vec3 point) {
        if (body == null || ResourceLocation.tryParse(body) == null || !valid(point))
            throw new IllegalArgumentException("invalid planet position");
    }
    private static boolean valid(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y)
                && Double.isFinite(point.z) && supported(point.x)
                && supported(point.y) && supported(point.z);
    }
    private static boolean supported(double value) { return Math.abs(value) < 30_000_000; }
    @SubscribeEvent public static void cloned(PlayerEvent.Clone event) {
        CompoundTag original = persisted(event.getOriginal());
        if (!original.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag target = persisted(event.getEntity());
        target.put(ROOT, original.getCompound(ROOT).copy());
        event.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG, target);
    }
}
