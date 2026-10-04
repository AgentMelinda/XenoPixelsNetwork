package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;
import xenoapi.npcs.api.constants.PotionEffectType;
import xenoapi.npcs.api.entity.IEntity;
import xenoapi.npcs.api.entity.IEntityLiving;
import xenoapi.npcs.api.entity.data.IMark;
import xenoapi.npcs.api.item.IItemStack;

/** A living entity as XenoAPI's {@link IEntityLiving}; health, hands, armor and effects. */
public class XenoLivingAdapter<T extends LivingEntity> extends XenoEntityAdapter<T> implements IEntityLiving<T> {
    static final float MAX_HEALTH_LIMIT = 1_000_000.0f;
    static final int MAX_EFFECT_SECONDS = 1_000_000;

    public XenoLivingAdapter(T entity) {
        super(entity);
    }

    // ------------------------------------------------------------------ health

    @Override public float getHealth() { return entity.getHealth(); }

    /** Clamped to 0..max health, as the native {@code npc.setHealth}. */
    @Override
    public void setHealth(float health) {
        XenoApiAdapters.requireFinite("IEntityLiving.setHealth", health);
        serverThread();
        entity.setHealth(Math.max(0.0f, Math.min(entity.getMaxHealth(), health)));
    }

    @Override public float getMaxHealth() { return entity.getMaxHealth(); }

    @Override
    public void setMaxHealth(float health) {
        XenoApiAdapters.requireFinite("IEntityLiving.setMaxHealth", health);
        if (health < 1.0f || health > MAX_HEALTH_LIMIT) {
            throw new IllegalArgumentException("IEntityLiving.setMaxHealth: expected 1.." + MAX_HEALTH_LIMIT);
        }
        AttributeInstance attribute = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) throw XenoApiAdapters.unsupported("IEntityLiving.setMaxHealth for this entity");
        serverThread();
        attribute.setBaseValue(health);
        if (entity.getHealth() > entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
    }

    // ------------------------------------------------------------------ combat

    private Mob mob(String method) {
        if (entity instanceof Mob mob) return mob;
        throw XenoApiAdapters.unsupported(method + " (only mobs have an attack target)");
    }

    @Override public boolean isAttacking() { return mob("IEntityLiving.isAttacking").getTarget() != null; }

    @Override
    public void setAttackTarget(IEntityLiving living) {
        Mob mob = mob("IEntityLiving.setAttackTarget");
        LivingEntity target = XenoApiAdapters.unwrapLiving(living);
        if (target == entity) throw new IllegalArgumentException("IEntityLiving.setAttackTarget: cannot target itself");
        serverThread();
        mob.setTarget(target);
    }

    @Override
    public IEntityLiving getAttackTarget() {
        return (IEntityLiving) XenoApiAdapters.wrap(mob("IEntityLiving.getAttackTarget").getTarget());
    }

    /** The entity this one most recently hurt, per vanilla's last-hurt-mob record. */
    @Override
    public IEntityLiving getLastAttacked() {
        return (IEntityLiving) XenoApiAdapters.wrap(entity.getLastHurtMob());
    }

    @Override public int getLastAttackedTime() { return entity.getLastHurtMobTimestamp(); }

    @Override
    public boolean canSeeEntity(IEntity other) {
        var target = XenoApiAdapters.unwrap(other);
        return target != null && entity.hasLineOfSight(target);
    }

    @Override
    public void swingMainhand() {
        serverThread();
        entity.swing(InteractionHand.MAIN_HAND, true);
    }

    @Override
    public void swingOffhand() {
        serverThread();
        entity.swing(InteractionHand.OFF_HAND, true);
    }

    // ------------------------------------------------------------------ equipment

    /** The live held stack; changing the returned item changes what the entity holds. */
    @Override public IItemStack getMainhandItem() { return XenoApiAdapters.wrap(entity.getMainHandItem()); }
    @Override public IItemStack getOffhandItem() { return XenoApiAdapters.wrap(entity.getOffhandItem()); }

    @Override
    public void setMainhandItem(IItemStack item) {
        var stack = XenoApiAdapters.unwrap(item).copy();
        serverThread();
        entity.setItemInHand(InteractionHand.MAIN_HAND, stack);
    }

    @Override
    public void setOffhandItem(IItemStack item) {
        var stack = XenoApiAdapters.unwrap(item).copy();
        serverThread();
        entity.setItemInHand(InteractionHand.OFF_HAND, stack);
    }

    /** Slot numbering from the contract: 0 boots, 1 pants, 2 body, 3 head. */
    static EquipmentSlot armorSlot(int slot) {
        return switch (slot) {
            case 0 -> EquipmentSlot.FEET;
            case 1 -> EquipmentSlot.LEGS;
            case 2 -> EquipmentSlot.CHEST;
            case 3 -> EquipmentSlot.HEAD;
            default -> throw new IllegalArgumentException("Armor slot must be 0-3, got " + slot);
        };
    }

    @Override public IItemStack getArmor(int slot) { return XenoApiAdapters.wrap(entity.getItemBySlot(armorSlot(slot))); }

    @Override
    public void setArmor(int slot, IItemStack item) {
        EquipmentSlot equipment = armorSlot(slot);
        var stack = XenoApiAdapters.unwrap(item).copy();
        serverThread();
        entity.setItemSlot(equipment, stack);
    }

    // ------------------------------------------------------------------ effects

    private static Holder<MobEffect> effect(int effect) {
        Holder<MobEffect> holder = PotionEffectType.getMCType(effect);
        if (holder == null) throw new IllegalArgumentException("Unknown PotionEffectType " + effect);
        return holder;
    }

    /**
     * {@code duration} is in seconds and {@code strength} is the amplifier. The last flag hides
     * the particles: XenoAPI names it {@code showParticles}, but its javadoc and the reference
     * both say "Whether you want to hide potion particles".
     */
    @Override
    public void addPotionEffect(int effect, int duration, int strength, boolean hideParticles) {
        Holder<MobEffect> holder = effect(effect);
        if (duration < 1 || duration > MAX_EFFECT_SECONDS) {
            throw new IllegalArgumentException("Effect duration must be 1-" + MAX_EFFECT_SECONDS + " seconds");
        }
        int amplifier = Math.max(0, Math.min(255, strength));
        serverThread();
        entity.addEffect(new MobEffectInstance(holder, duration * 20, amplifier, false, !hideParticles));
    }

    @Override
    public void clearPotionEffects() {
        serverThread();
        entity.removeAllEffects();
    }

    /** The amplifier of an active effect, or -1 when it is not active. */
    @Override
    public int getPotionEffect(int effect) {
        MobEffectInstance active = entity.getEffect(effect(effect));
        return active == null ? -1 : active.getAmplifier();
    }

    @Override public boolean isChild() { return entity.isBaby(); }

    // ------------------------------------------------------------------ movement input

    @Override public float getMoveForward() { return entity.zza; }
    @Override public float getMoveStrafing() { return entity.xxa; }
    @Override public float getMoveVertical() { return entity.yya; }

    @Override
    public void setMoveForward(float move) {
        XenoApiAdapters.requireFinite("IEntityLiving.setMoveForward", move);
        serverThread();
        entity.zza = Math.max(-1.0f, Math.min(1.0f, move));
    }

    @Override
    public void setMoveStrafing(float move) {
        XenoApiAdapters.requireFinite("IEntityLiving.setMoveStrafing", move);
        serverThread();
        entity.xxa = Math.max(-1.0f, Math.min(1.0f, move));
    }

    @Override
    public void setMoveVertical(float move) {
        XenoApiAdapters.requireFinite("IEntityLiving.setMoveVertical", move);
        serverThread();
        entity.yya = Math.max(-1.0f, Math.min(1.0f, move));
    }

    // ------------------------------------------------------------------ marks (native NPCs)

    private net.bullettrain.xenopixelsmod.npc.XenoNpcEntity markable(String method) {
        if (entity instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc) return npc;
        throw XenoApiAdapters.unsupported(method + " (only native Xeno NPCs carry a mark)");
    }

    /** Native NPCs carry one mark, so adding one replaces the one shown. */
    @Override
    public IMark addMark(int type) {
        var npc = markable("IEntityLiving.addMark");
        XenoMark mark = new XenoMark(npc);
        mark.setType(type);
        return mark;
    }

    @Override
    public void removeMark(IMark mark) {
        var npc = markable("IEntityLiving.removeMark");
        if (mark == null) return;
        new XenoMark(npc).setType(xenoapi.npcs.api.constants.MarkType.NONE);
    }

    @Override
    public IMark[] getMarks() {
        if (!(entity instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity npc)) return new IMark[0];
        XenoMark mark = new XenoMark(npc);
        return mark.getType() == xenoapi.npcs.api.constants.MarkType.NONE ? new IMark[0] : new IMark[] {mark};
    }

    // ------------------------------------------------------------------ unsupported


    @Override
    public T getMCEntity() {
        throw XenoApiAdapters.unsupported("IEntityLiving.getMCEntity (raw handles are not exposed)");
    }
}
