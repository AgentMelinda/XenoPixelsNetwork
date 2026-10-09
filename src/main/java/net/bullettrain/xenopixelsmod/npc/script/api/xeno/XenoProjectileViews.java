package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import xenoapi.npcs.api.entity.IArrow;
import xenoapi.npcs.api.entity.IThrowable;

/** Marker interfaces retain the entity operations without pretending to be custom projectiles. */
final class XenoProjectileViews {
    private XenoProjectileViews() {}

    static final class ArrowView extends XenoEntityAdapter<AbstractArrow> implements IArrow<AbstractArrow> {
        ArrowView(AbstractArrow entity) { super(entity); }
    }

    static final class ThrowableView extends XenoEntityAdapter<ThrowableProjectile>
            implements IThrowable<ThrowableProjectile> {
        ThrowableView(ThrowableProjectile entity) { super(entity); }
    }
}
