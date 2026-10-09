package net.bullettrain.xenopixelsmod.npc.projectile;

import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Item-rendered throwable with vanilla swept collision and ordinary server damage. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoItemProjectileEntity extends ThrowableItemProjectile {
    private float damage = 1;
    private float knockback;
    private float launchSpeed = 1.5f;
    private int accuracy = 100;
    private int flightAge;
    private LivingEntity pendingVictim;
    private float acceptedDamage;
    private net.bullettrain.xenopixelsmod.npc.script.ProjectileScriptContext.Token scriptEvents;
    public void enableScriptEvents(net.bullettrain.xenopixelsmod.npc.script.ProjectileScriptContext.Token token) {
        scriptEvents = token;
    }
    private boolean hasScriptEvents() {
        if (scriptEvents != null && (!scriptEvents.valid() || !scriptEvents.matchesLevel(level()))) scriptEvents = null;
        return scriptEvents != null;
    }
    private void scriptEvent(String hook, xenoapi.npcs.api.event.ProjectileEvent event) {
        var token = scriptEvents;
        if (token == null) return;
        if (!token.valid()) { scriptEvents = null; return; }
        if (!net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.enter(getUUID(), hook)) return;
        try {
            token.deliver(getUUID(), hook, event);
            net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.post(event);
        } finally {
            net.bullettrain.xenopixelsmod.npc.script.api.xeno.event.XenoEventDispatch.exit(getUUID(), hook);
        }
    }
    public XenoItemProjectileEntity(EntityType<? extends XenoItemProjectileEntity> type, Level level) { super(type, level); }
    @Override protected Item getDefaultItem() { return Items.SNOWBALL; }
    public int accuracy() { return accuracy; }
    public void accuracy(int value) { ItemProjectileRules.accuracy(value); accuracy = value; }
    public float launchSpeed() { return launchSpeed; }
    public void configure(float damage, float knockback, float speed, int accuracy, boolean gravity) {
        this.damage = ItemProjectileRules.damage(damage);
        this.knockback = Float.isFinite(knockback) ? Math.clamp(knockback, 0, 10) : 0;
        launchSpeed = ItemProjectileRules.speed(speed);
        accuracy(accuracy);
        setNoGravity(!gravity);
    }
    public void aim(Vec3 direction) {
        if (!ItemProjectileRules.allowedDistance(direction.lengthSqr()))
            throw new IllegalArgumentException("Projectile heading must be finite, nonzero and within 256 blocks");
        shoot(direction.x, direction.y, direction.z, launchSpeed, (100 - accuracy) / 10f);
        hasImpulse = true;
        hurtMarked = true;
    }
    @Override public void tick() {
        if (!level().isClientSide()) {
            var beforeLevel = level();
            if (hasScriptEvents()) scriptEvent("projectileTick", new xenoapi.npcs.api.event.ProjectileEvent.UpdateEvent(
                    new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoItemProjectileAdapter(this)));
            if (isRemoved() || level() != beforeLevel) return;
            Vec3 next = position().add(getDeltaMovement());
            if (ItemProjectileRules.expired(flightAge++) || !Double.isFinite(next.x) || !Double.isFinite(next.y)
                    || !Double.isFinite(next.z) || !level().hasChunkAt(BlockPos.containing(next))
                    || !level().getWorldBorder().isWithinBounds(BlockPos.containing(next))) {
                discard(); return;
            }
        }
        super.tick();
    }
    @Override protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide() || !(result.getEntity() instanceof LivingEntity target)) return;
        var owner = getOwner();
        if (target.isRemoved() || !target.isAlive() || target.level() != level()
                || target == owner || owner != null && owner.isAlliedTo(target)) return;
        LivingEntity previousVictim = pendingVictim;
        float previousDamage = acceptedDamage;
        boolean connected;
        try {
            pendingVictim = target;
            acceptedDamage = 0;
            connected = target.hurt(damageSources().thrown(this, owner), damage) && acceptedDamage > 0;
        } finally {
            pendingVictim = previousVictim;
            acceptedDamage = previousDamage;
        }
        if (connected && !target.isRemoved() && target.level() == level() && target.isAlive() && knockback > 0) {
            Vec3 away = getDeltaMovement().multiply(1, 0, 1);
            if (away.lengthSqr() > 1e-8) CombatKnockback.add(target, away.normalize().scale(knockback * 0.1));
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAcceptedDamage(LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() instanceof XenoItemProjectileEntity projectile
                && projectile.pendingVictim == event.getEntity()
                && Float.isFinite(event.getNewDamage()) && event.getNewDamage() > 0) {
            projectile.acceptedDamage = Math.max(projectile.acceptedDamage, event.getNewDamage());
        }
    }
    @Override protected void onHit(HitResult result) {
        if (!level().isClientSide() && hasScriptEvents() && result.getType() != HitResult.Type.MISS) {
            var beforeLevel = level();
            Vec3 beforePosition = position();
            Vec3 beforeVelocity = getDeltaMovement();
            Object target;
            int type;
            net.minecraft.world.level.block.state.BlockState beforeBlock = null;
            net.minecraft.world.phys.AABB beforeTargetBox = null;
            Vec3 beforeTargetPosition = null;
            if (result instanceof EntityHitResult hit) {
                type = 0;
                beforeTargetBox = hit.getEntity().getBoundingBox();
                beforeTargetPosition = hit.getEntity().position();
                target = net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoApiAdapters.wrap(hit.getEntity());
            } else if (result instanceof net.minecraft.world.phys.BlockHitResult hit
                    && level() instanceof net.minecraft.server.level.ServerLevel server) {
                type = 1;
                beforeBlock = server.getBlockState(hit.getBlockPos());
                target = new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoBlockAdapter(server, hit.getBlockPos());
            } else return;
            if (scriptEvents != null) scriptEvent("projectileImpact", new xenoapi.npcs.api.event.ProjectileEvent.ImpactEvent(
                    new net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoItemProjectileAdapter(this), type, target));
            // A callback may move, remove or redirect the projectile; the old collision then no longer applies.
            if (isRemoved() || level() != beforeLevel || !position().equals(beforePosition)
                    || !getDeltaMovement().equals(beforeVelocity)) return;
            if (result instanceof EntityHitResult hit && (hit.getEntity().isRemoved()
                    || hit.getEntity().level() != level()
                    || !hit.getEntity().getBoundingBox().equals(beforeTargetBox)
                    || !hit.getEntity().position().equals(beforeTargetPosition))) return;
            if (result instanceof net.minecraft.world.phys.BlockHitResult hit
                    && (beforeBlock == null || !level().getBlockState(hit.getBlockPos()).equals(beforeBlock))) return;
        }
        super.onHit(result);
        if (!level().isClientSide()) discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("XenoItemDamage", damage);
        tag.putFloat("XenoItemKnockback", knockback);
        tag.putFloat("XenoItemSpeed", launchSpeed);
        tag.putInt("XenoItemAccuracy", accuracy);
        tag.putInt("XenoItemAge", flightAge);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = ItemProjectileRules.damage(tag.getFloat("XenoItemDamage"));
        float push = tag.getFloat("XenoItemKnockback");
        knockback = Float.isFinite(push) ? Math.clamp(push, 0, 10) : 0;
        launchSpeed = ItemProjectileRules.speed(tag.getFloat("XenoItemSpeed"));
        accuracy = Math.clamp(tag.getInt("XenoItemAccuracy"), 0, 100);
        flightAge = Math.clamp(tag.getInt("XenoItemAge"), 0, ItemProjectileRules.MAX_AGE);
    }
}
