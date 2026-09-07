package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Keeps negative effects across player and CustomNPC save/load and respawn resets.
 *
 * <p>CustomNPCs owns its own effect serialization and can discard non-beneficial effects while
 * rebuilding an NPC. The snapshot lives in the entity persistent data, which is serialized by the
 * game independently of CustomNPCs' effect list.
 */
public final class NpcNegativeEffectPersistence {
    private static final String TAG_EFFECTS = "xenopixels:custom_npc_negative_effects";

    private NpcNegativeEffectPersistence() {
    }

    public static void restore(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !shouldPersist(living)) return;
        ListTag effects = consume(living.getPersistentData());
        for (int i = 0; i < effects.size(); i++) {
            MobEffectInstance effect = MobEffectInstance.load(effects.getCompound(i));
            if (effect != null && !effect.getEffect().value().isBeneficial()) {
                living.addEffect(effect);
            }
        }
    }

    public static void capture(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !shouldPersist(living)) return;
        capture(living.getPersistentData(), living.getActiveEffects());
    }

    static void capture(CompoundTag persistent, java.util.Collection<MobEffectInstance> activeEffects) {
        ListTag effects = new ListTag();
        for (MobEffectInstance effect : activeEffects) {
            if (!effect.getEffect().value().isBeneficial()) {
                effects.add(effect.save());
            }
        }
        if (effects.isEmpty()) {
            persistent.remove(TAG_EFFECTS);
        } else {
            persistent.put(TAG_EFFECTS, effects);
        }
    }

    public static void copyOnClone(Player original, Player replacement) {
        if (original == null || replacement == null) return;
        capture(original);
        ListTag saved = consume(original.getPersistentData());
        discard(replacement);
        if (!saved.isEmpty()) {
            replacement.getPersistentData().put(TAG_EFFECTS, saved.copy());
        }
        restore(replacement);
    }

    static ListTag consume(CompoundTag persistent) {
        ListTag saved = persistent.getList(TAG_EFFECTS, 10);
        persistent.remove(TAG_EFFECTS);
        return saved;
    }

    public static void discard(Entity entity) {
        entity.getPersistentData().remove(TAG_EFFECTS);
    }

    /** Called before vanilla serializes persistent data, not after PlayerList has saved. */
    public static void beforeSave(Entity entity) {
        if (entity instanceof Player) {
            // Vanilla already serializes player effects. Only Clone needs a transfer snapshot.
            discard(entity);
        } else if (entity instanceof LivingEntity living && living.isAlive()) {
            capture(living);
        }
    }

    public static void beforeReset(Entity entity) {
        if (!entity.level().isClientSide() && entity instanceof LivingEntity living && living.isAlive()) {
            capture(living);
        }
    }

    public static void afterReset(Entity entity) {
        if (!entity.level().isClientSide()) restore(entity);
    }

    static boolean shouldPersist(Entity entity) {
        return entity instanceof Player || NpcCounterpartSync.isCustomNpc(entity);
    }
}
