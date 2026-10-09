package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.projectile.XenoItemProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import xenoapi.npcs.api.entity.IEntity;
import xenoapi.npcs.api.entity.IProjectile;
import xenoapi.npcs.api.item.IItemStack;

/** Typed access to the actual scripted item projectile, without raw entity handles. */
public final class XenoItemProjectileAdapter extends XenoEntityAdapter<XenoItemProjectileEntity>
        implements IProjectile<XenoItemProjectileEntity> {
    public XenoItemProjectileAdapter(XenoItemProjectileEntity entity) { super(entity); }
    /** Detached item view; setItem commits a replacement and marks its synced component dirty. */
    @Override public IItemStack getItem() { return XenoApiAdapters.wrap(entity.getItem().copy()); }
    @Override public void setItem(IItemStack item) {
        serverThread();
        var stack = XenoApiAdapters.unwrap(item);
        if (stack.isEmpty()) throw new IllegalArgumentException("Projectile item cannot be empty");
        entity.setItem(stack.copyWithCount(1));
    }
    @Override public boolean getHasGravity() { return !entity.isNoGravity(); }
    @Override public void setHasGravity(boolean gravity) { serverThread(); entity.setNoGravity(!gravity); }
    @Override public int getAccuracy() { return entity.accuracy(); }
    @Override public void setAccuracy(int accuracy) { serverThread(); entity.accuracy(accuracy); }
    @Override public void setHeading(IEntity target) {
        serverThread();
        var other = XenoApiAdapters.unwrap(target);
        if (other == null || other.level() != entity.level() || other.isRemoved())
            throw new IllegalArgumentException("Projectile target must be live and in the same level");
        setHeading(other.getX(), other.getY() + other.getBbHeight() * 0.5, other.getZ());
    }
    @Override public void setHeading(double x, double y, double z) {
        serverThread();
        XenoApiAdapters.requireFinite("IProjectile.setHeading", x, y, z);
        if (!entity.level().hasChunkAt(BlockPos.containing(x, y, z)))
            throw new IllegalArgumentException("Projectile target position must be loaded");
        entity.aim(new Vec3(x, y, z).subtract(entity.position()));
    }
    @Override public void setHeading(float yaw, float pitch) {
        serverThread();
        XenoApiAdapters.requireFinite("IProjectile.setHeading", yaw, pitch);
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        entity.aim(new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)));
    }
    @Override public void enableEvents() {
        serverThread();
        entity.enableScriptEvents(net.bullettrain.xenopixelsmod.npc.script.ProjectileScriptContext.capture());
    }
}
