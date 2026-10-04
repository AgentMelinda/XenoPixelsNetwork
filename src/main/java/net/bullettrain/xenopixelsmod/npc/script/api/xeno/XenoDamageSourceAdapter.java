package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import xenoapi.npcs.api.IDamageSource;
import xenoapi.npcs.api.entity.IEntity;

import java.util.Objects;

/** A damage source as XenoAPI's {@link IDamageSource}. Read-only. */
public final class XenoDamageSourceAdapter implements IDamageSource {
    private final DamageSource source;

    public XenoDamageSourceAdapter(DamageSource source) {
        this.source = Objects.requireNonNull(source);
    }

    /** The damage type's message id, e.g. {@code mob} or {@code arrow}, as CustomNPCs reports it. */
    @Override public String getType() { return source.getMsgId(); }
    @Override public boolean isUnblockable() { return source.is(DamageTypeTags.BYPASSES_ARMOR); }
    @Override public boolean isProjectile() { return source.is(DamageTypeTags.IS_PROJECTILE); }
    @Override public IEntity getTrueSource() { return XenoApiAdapters.wrap(source.getEntity()); }
    @Override public IEntity getImmediateSource() { return XenoApiAdapters.wrap(source.getDirectEntity()); }

    @Override
    public DamageSource getMCDamageSource() {
        throw XenoApiAdapters.unsupported("IDamageSource.getMCDamageSource (raw handles are not exposed)");
    }
}
