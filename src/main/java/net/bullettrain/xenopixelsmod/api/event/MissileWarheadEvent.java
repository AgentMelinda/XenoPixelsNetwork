package net.bullettrain.xenopixelsmod.api.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on the server just before a tube-launched missile applies its default explosion.
 * Posted on {@code NeoForge.EVENT_BUS}. Additive: {@code XenoPixelsApi.API_VERSION} did not move;
 * test for this class the same way you test for {@code XenoAnimApi}.
 *
 * <p>Cancel to skip the vanilla blast and run your own (Ballistix, Mekanism, etc.). The warhead
 * stack is the item that was in the tube; it is a copy and will be discarded with the missile.
 */
public class MissileWarheadEvent extends Event implements ICancellableEvent {
    private final ServerLevel level;
    private final Entity missile;
    private final Vec3 position;
    private final ItemStack warhead;
    private final String sizeName;
    private float explosionPower;

    public MissileWarheadEvent(ServerLevel level, Entity missile, Vec3 position,
                               ItemStack warhead, String sizeName, float explosionPower) {
        this.level = level;
        this.missile = missile;
        this.position = position;
        this.warhead = warhead == null ? ItemStack.EMPTY : warhead.copy();
        this.sizeName = sizeName == null || sizeName.isBlank() ? "MEDIUM" : sizeName;
        this.explosionPower = explosionPower;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public Entity getMissile() {
        return missile;
    }

    public Vec3 getPosition() {
        return position;
    }

    public ItemStack getWarhead() {
        return warhead;
    }

    /** {@code SMALL}, {@code MEDIUM}, {@code LARGE} or {@code MEGA}. */
    public String getSizeName() {
        return sizeName;
    }

    public float getExplosionPower() {
        return explosionPower;
    }

    public void setExplosionPower(float explosionPower) {
        this.explosionPower = explosionPower;
    }
}
