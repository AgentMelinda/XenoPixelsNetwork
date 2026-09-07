package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import net.minecraft.world.entity.Entity;
import noppes.npcs.api.IPos;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.data.IData;
import noppes.npcs.api.wrapper.EntityWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Arrays;

/**
 * Restores script conveniences used by older CustomNPCs scripts while delegating all storage,
 * lookup, and riding behavior to the current wrapper API.
 */
@Mixin(value = EntityWrapper.class, remap = false)
public abstract class EntityWrapperLegacyApiMixin {
    @Shadow protected Entity entity;
    @Shadow public abstract double getX();
    @Shadow public abstract double getY();
    @Shadow public abstract double getZ();
    @Shadow public abstract IPos getPos();
    @Shadow public abstract IWorld getWorld();
    @Shadow public abstract IData getTempdata();
    @Shadow public abstract IData getStoreddata();
    @Shadow public abstract IEntity[] getRiders();
    @Shadow public abstract void addRider(IEntity rider);
    @Shadow public abstract void clearRiders();

    public double getDistanceTo(IEntity target) {
        if (target == null) {
            return Double.POSITIVE_INFINITY;
        }

        double deltaX = getX() - target.getX();
        double deltaY = getY() - target.getY();
        double deltaZ = getZ() - target.getZ();
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    public IEntity[] getSurroundingEntities(int range) {
        return getSurroundingEntities(range, -1);
    }

    public IEntity[] getSurroundingEntities(int range, int type) {
        return Arrays.stream(getWorld().getNearbyEntities(getPos(), range, type))
                .filter(candidate -> candidate != null && candidate.getMCEntity() != entity)
                .toArray(IEntity[]::new);
    }

    public Object getTempData(String key) {
        return getTempdata().get(key);
    }

    public void setTempData(String key, Object value) {
        getTempdata().put(key, value);
    }

    public boolean hasTempData(String key) {
        return getTempdata().has(key);
    }

    public void removeTempData(String key) {
        getTempdata().remove(key);
    }

    public void clearTempData() {
        getTempdata().clear();
    }

    public Object getStoredData(String key) {
        return getStoreddata().get(key);
    }

    public void setStoredData(String key, Object value) {
        getStoreddata().put(key, value);
    }

    public boolean hasStoredData(String key) {
        return getStoreddata().has(key);
    }

    public void removeStoredData(String key) {
        getStoreddata().remove(key);
    }

    public void clearStoredData() {
        getStoreddata().clear();
    }

    public IEntity getRider() {
        IEntity[] riders = getRiders();
        return riders.length == 0 ? null : riders[0];
    }

    public void setRider(IEntity rider) {
        clearRiders();
        if (rider != null) {
            addRider(rider);
        }
    }
}
