package net.bullettrain.xenopixelsmod.combat.clone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.UUID;

/**
 * One of a fighter's other bodies.
 *
 * <p>Serves both Shi Shin No Ken and Zanzoken, because they are the same idea used twice: a copy of
 * you standing somewhere you are not. A Multi-Form copy holds a slot in formation and turns as you
 * turn; a Zanzoken copy holds its ground in a ring around whoever swung at you.
 *
 * <p><b>A living body, not a prop.</b> An earlier version was a plain {@link Entity}, which was
 * enough for a decoy but wrong for what a copy has to be. Two things forced this: DragonMineZ's
 * deferred aura queue de-duplicates by entity id, so copies sharing the fighter's identity never
 * got an aura or an animation of their own; and the NPC combat dispatchers this mod already owns
 * are written against {@code LivingEntity}, so being living is what will let a copy throw DMZ ki
 * attacks and strikes rather than needing a second combat system written for it.
 *
 * <p>Health is a share of the fighter's, matching the power split — dividing spreads you thinner,
 * it does not hand out extra bodies for free.
 */
public class XenoCloneEntity extends LivingEntity {

    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(XenoCloneEntity.class, EntityDataSerializers.INT);
    /** Formation slot, or {@link #SLOT_STATIONARY} for a copy that holds its ground. */
    private static final EntityDataAccessor<Integer> SLOT =
            SynchedEntityData.defineId(XenoCloneEntity.class, EntityDataSerializers.INT);
    /** 0 while still bursting out of the fighter, 1 once the body has reached its place. */
    private static final EntityDataAccessor<Float> TRAVEL =
            SynchedEntityData.defineId(XenoCloneEntity.class, EntityDataSerializers.FLOAT);

    /** A Zanzoken image: it does not follow, it stays exactly where it was placed. */
    public static final int SLOT_STATIONARY = -1;

    /** How far a Multi-Form copy stands from its fighter. */
    public static final double FORMATION_RADIUS = 2.2;
    /** Ticks a body spends travelling out of the fighter before it settles into formation. */
    public static final int TRAVEL_TICKS = 6;

    private final CloneCombatBridge combat = new CloneCombatBridge();

    public net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile combatProfile() {
        return combat.profile(this);
    }

    private int lifetime = 20;
    private int age;
    private int travelTicks;
    /** Counts down while a body is flying home to be reabsorbed; it is discarded on arrival. */
    private int recallTicks;
    private UUID ownerUuid;
    private int formationBodies = 1;
    private float healthCapacity;
    private Vec3 travelOrigin = Vec3.ZERO;
    private Vec3 recallOrigin = Vec3.ZERO;

    public XenoCloneEntity(EntityType<? extends XenoCloneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Copies never wear or wield anything of their own; the renderer draws the fighter. */
    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    public void configure(Player owner, int slot, int lifetimeTicks, float health) {
        this.ownerUuid = owner == null ? null : owner.getUUID();
        this.travelOrigin = position();
        this.entityData.set(OWNER_ID, owner == null ? -1 : owner.getId());
        this.entityData.set(SLOT, slot);
        this.lifetime = Math.max(1, lifetimeTicks);
        this.travelTicks = slot == SLOT_STATIONARY ? 0 : TRAVEL_TICKS;
        this.entityData.set(TRAVEL, slot == SLOT_STATIONARY ? 1.0f : 0.0f);
        this.healthCapacity = Math.max(0f, health);
        // Vanilla max-health attributes have a floor of one; current health need not.
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(1f, healthCapacity));
        this.setHealth(healthCapacity);
        // A copy wears the fighter's name. Without one the renderer fell back to the entity type's
        // display name, which had no translation, so the raw key was drawn across the screen.
        if (owner != null) {
            this.setCustomName(owner.getName());
            this.setCustomNameVisible(true);
        }
    }

    /**
     * Starts this body flying home to be reabsorbed.
     *
     * <p>The reverse of the split: bodies are drawn back into the fighter and vanish on arrival,
     * rather than being deleted where they stand.
     */
    public void recall() {
        if (slot() != SLOT_STATIONARY && this.recallTicks <= 0 && isAlive()) {
            combat.cancel(this);
            this.noPhysics = true;
            this.recallOrigin = position();
            this.travelTicks = 0;
            this.recallTicks = TRAVEL_TICKS;
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    public boolean isRecalling() {
        return this.recallTicks > 0;
    }

    UUID ownerUuid() {
        return ownerUuid;
    }

    void setFormationBodies(int bodies) {
        formationBodies = Math.max(1, bodies);
    }

    @Override
    public void setHealth(float health) {
        super.setHealth(healthCapacity > 0f ? Math.min(healthCapacity, health) : health);
    }

    public int ownerId() {
        return this.entityData.get(OWNER_ID);
    }

    public int slot() {
        return this.entityData.get(SLOT);
    }

    /** 0 while bursting out of the fighter, 1 once settled — the renderer can lean on this too. */
    public float travelProgress() {
        return this.entityData.get(TRAVEL);
    }

    @Override
    public void tick() {
        if (!level().isClientSide()) setDeltaMovement(Vec3.ZERO);
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (!isAlive() || ++this.age >= this.lifetime) {
            this.discard();
            return;
        }
        Entity raw = this.level().getEntity(ownerId());
        if (!(raw instanceof Player owner) || !owner.isAlive()
                || !owner.getUUID().equals(ownerUuid)) {
            this.discard();
            return;
        }

        int slot = slot();
        if (slot == SLOT_STATIONARY) {
            // A ring copy keeps the facing it was spawned with — inward, at the target it has
            // surrounded. Turning with the fighter would break the encirclement it exists to sell.
            return;
        }

        if (!XenoCloneSystem.owns(this)) {
            discard();
            return;
        }

        // Copies look at whoever the fighter has locked on, not merely where the fighter happens
        // to be facing. Turning with the fighter's head made them stare at nothing whenever he
        // glanced away mid-fight.
        LivingEntity focus = XenoCloneSystem.lockedTarget(owner);
        float aimYaw;
        float aimPitch;
        if (focus != null) {
            double dx = focus.getX() - this.getX();
            double dz = focus.getZ() - this.getZ();
            double dy = focus.getEyeY() - this.getEyeY();
            double flat = Math.sqrt(dx * dx + dz * dz);
            if (flat > 1.0e-4 || Math.abs(dy) > 1.0e-4) {
                aimYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                aimPitch = (float) (-Math.toDegrees(Math.atan2(dy, flat)));
            } else {
                aimYaw = owner.getYRot();
                aimPitch = owner.getXRot();
            }
        } else {
            // No locked target: face outward from the formation center so clones don't all
            // stare inward at the owner.
            double dx = this.getX() - owner.getX();
            double dz = this.getZ() - owner.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            if (flat > 1.0e-4) {
                aimYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            } else {
                aimYaw = owner.getYRot();
            }
            aimPitch = 0f;
        }
        this.setYRot(aimYaw);
        this.setYHeadRot(aimYaw);
        this.yBodyRot = aimYaw;
        this.setXRot(aimPitch);

        if (this.recallTicks > 0) {
            this.recallTicks--;
            float progress = 1f - this.recallTicks / (float) TRAVEL_TICKS;
            this.entityData.set(TRAVEL, 1f - progress);
            Vec3 want = recallOrigin.lerp(owner.position(), CloneFormation.travelEase(progress));
            this.setPos(want.x, want.y, want.z);
            if (this.recallTicks <= 0) {
                XenoCloneSystem.onRecallArrived(this, owner);
                this.discard();
            }
            return;
        }

        if (this.travelTicks > 0) {
            this.travelTicks--;
            this.entityData.set(TRAVEL, 1.0f - this.travelTicks / (float) TRAVEL_TICKS);
        }

        // The owner already occupies the back slot. Subtract that offset to recover the common
        // formation center; interpolate from the actual split origin, not the displaced owner.
        double[] off = CloneFormation.offsetFromOwner(owner.getYRot(), slot,
                formationBodies, FORMATION_RADIUS);
        Vec3 target = owner.position().add(off[0], 0.0, off[1]);
        if (travelProgress() >= 1f && owner instanceof net.minecraft.server.level.ServerPlayer serverOwner) {
            if (!combat.tick(this, serverOwner, focus)) CloneCombatBridge.move(this, target, 0.4);
        } else {
            Vec3 want = travelOrigin.lerp(target, CloneFormation.travelEase(travelProgress()));
            this.setPos(want.x, want.y, want.z);
        }
    }

    /** These bodies belong to a live session, never to a saved world or a reused entity id. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        combat.cancel(this);
        net.bullettrain.xenopixelsmod.compat.npc.NpcKiCooldowns.clear(getUUID());
        XenoCloneSystem.onClonePopped(this);
        super.remove(reason);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        XenoCloneSystem.onClonePopped(this);
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        // A copy carries nothing; the renderer draws the fighter's own equipment.
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_ID, -1);
        builder.define(SLOT, SLOT_STATIONARY);
        builder.define(TRAVEL, 1.0f);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.lifetime = Math.max(1, tag.getInt("XenoCloneLifetime"));
        this.age = tag.getInt("XenoCloneAge");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("XenoCloneLifetime", this.lifetime);
        tag.putInt("XenoCloneAge", this.age);
    }

}
