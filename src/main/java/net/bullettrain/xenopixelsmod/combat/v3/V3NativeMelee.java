package net.bullettrain.xenopixelsmod.combat.v3;

import com.dragonminez.common.combat.logic.knockback.ConfigurableKnockback;
import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.combat.logic.player.PlayerAttackProperties;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.combat.player.AttackHand;
import com.dragonminez.common.combat.util.SoundHelper;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.MeleeAnimationS2C;
import com.dragonminez.common.util.AttributeMods;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Executes an admitted V3 light tap with DragonMineZ's verified native melee primitives. */
final class V3NativeMelee {
    private static final String LAST_ATTACK = "dmz_last_melee_attack_time";
    private static final String FIRST_HIT = "dmz_first_hit";
    private static final UUID SWEEPING_MODIFIER = UUID.fromString("99435a26-9fa8-48b4-a8eb-8438bf228eb3");

    private V3NativeMelee() {}

    static boolean cooldownElapsed(long gameTime, long lastAttack, int minInterval) {
        return lastAttack <= 0L || gameTime - lastAttack >= minInterval;
    }

    static boolean tap(ServerPlayer player, LivingEntity target, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || target == null || !V3CombatServer.freeToAct(player) || !V3Heavy.emptyHands(player)) {
            return false;
        }
        LivingEntity approved = V3Targeting.resolve(player);
        if (approved == null || approved != target) return false;

        CompoundTag tag = player.getPersistentData();
        boolean hadLastAttack = tag.contains(LAST_ATTACK);
        long lastAttack = tag.getLong(LAST_ATTACK);
        long gameTime = player.level().getGameTime();
        int minInterval = Math.max(0, (int) Math.floor(player.getCurrentItemAttackStrengthDelay()) - 2);
        if (!cooldownElapsed(gameTime, lastAttack, minInterval)) return false;

        int comboBefore = fighter.meleeCombo;
        long comboTickBefore = fighter.meleeComboLastTick;
        int resetWindow = (int) Math.ceil(PlayerAttackHelper.getAttackCooldownTicksCapped(player)) + 30;
        if (fighter.meleeComboLastTick >= 0 && now - fighter.meleeComboLastTick > resetWindow) fighter.meleeCombo = 0;
        int combo = Math.max(0, fighter.meleeCombo);
        AttackHand hand = PlayerAttackHelper.getCurrentAttack(player, combo);
        if (hand == null) {
            combo = 0;
            hand = PlayerAttackHelper.getCurrentAttack(player, combo);
        }
        if (hand == null) return false;

        double maxRange = PlayerAttackHelper.getEffectiveAttackRange(player, hand.attributes().attackRange());
        TargetHelper.Relation relation = TargetHelper.getRelation(player, target);
        if (!TargetHelper.canAttack(player, target, maxRange + 4.0)
                || player.distanceToSqr(target) > maxRange * maxRange + 16.0) return false;

        tag.putLong(LAST_ATTACK, gameTime);
        fighter.meleeCombo = combo + 1;
        fighter.meleeComboLastTick = now;
        try {
            return execute(player, target, relation, hand, combo);
        } catch (RuntimeException | LinkageError failure) {
            fighter.meleeCombo = comboBefore;
            fighter.meleeComboLastTick = comboTickBefore;
            if (hadLastAttack) tag.putLong(LAST_ATTACK, lastAttack);
            else tag.remove(LAST_ATTACK);
            throw failure;
        }
    }

    private static boolean execute(ServerPlayer player, LivingEntity target, TargetHelper.Relation relation,
                                   AttackHand hand, int combo) {
        PlayerAttackProperties properties = (PlayerAttackProperties) player;
        int nativeComboBefore = properties.getComboCount();
        CompoundTag tag = player.getPersistentData();
        boolean hadFirst = tag.contains(FIRST_HIT);
        boolean firstBefore = tag.getBoolean(FIRST_HIT);
        Multimap<Holder<Attribute>, AttributeModifier> comboAttributes = null;
        Multimap<Holder<Attribute>, AttributeModifier> dualAttributes = null;
        Multimap<Holder<Attribute>, AttributeModifier> sweepingAttributes = HashMultimap.create();
        boolean offhand = false;
        boolean attacked = false;
        try {
            properties.setComboCount(combo);
            if (player.level() instanceof ServerLevel level) {
                SoundHelper.playSound(level, player, player, hand.attack().swingSound());
            }
            float cooldown = PlayerAttackHelper.getAttackCooldownTicksCapped(player);
            float speed = Math.max(0.55f, Math.min(1.35f, 12.0f / Math.max(cooldown, 0.001f)));
            NetworkHandler.sendToTrackingEntityAndSelf(new MeleeAnimationS2C(player.getId(), hand.attack().animation(),
                    hand.isOffHand(), speed), player);
            if (hand.isOffHand()) {
                PlayerAttackHelper.setAttributesForOffHandAttack(player, true);
                offhand = true;
            }
            if (hand.attack().damageMultiplier() != 1.0) {
                comboAttributes = HashMultimap.create();
                double base = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
                comboAttributes.put(Attributes.ATTACK_DAMAGE, AttributeMods.of(UUID.randomUUID(),
                        "Combo damage multiplier", base * hand.attack().damageMultiplier() - base,
                        AttributeModifier.Operation.ADD_VALUE));
                player.getAttributes().addTransientAttributeModifiers(comboAttributes);
            }
            float dual = PlayerAttackHelper.getDualWieldingAttackDamageMultiplier(player, hand);
            if (dual != 1.0f) {
                dualAttributes = HashMultimap.create();
                double base = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
                dualAttributes.put(Attributes.ATTACK_DAMAGE, AttributeMods.of(UUID.randomUUID(),
                        "Dual wielding damage multiplier", base * dual - base,
                        AttributeModifier.Operation.ADD_VALUE));
                player.getAttributes().addTransientAttributeModifiers(dualAttributes);
            }
            int sweepingLevel = 0;
            if (sweepingLevel > 0) {
                sweepingAttributes.put(Attributes.ATTACK_SPEED, AttributeMods.of(SWEEPING_MODIFIER,
                        "Disable sweeping during attack", -100.0, AttributeModifier.Operation.ADD_VALUE));
                player.getAttributes().addTransientAttributeModifiers(sweepingAttributes);
            }
            tag.putBoolean(FIRST_HIT, true);
            TargetHelper.onSuccessfulAttack(player, target, relation);
            player.attack(target);
            attacked = true;
            return true;
        } finally {
            if (target instanceof ConfigurableKnockback knockback) knockback.setKnockbackMultiplier(1.0f);
            if (hadFirst) tag.putBoolean(FIRST_HIT, firstBefore);
            else tag.remove(FIRST_HIT);
            player.resetLastActionTime();
            if (comboAttributes != null) player.getAttributes().removeAttributeModifiers(comboAttributes);
            if (dualAttributes != null) player.getAttributes().removeAttributeModifiers(dualAttributes);
            if (offhand) PlayerAttackHelper.setAttributesForOffHandAttack(player, false);
            if (!sweepingAttributes.isEmpty()) player.getAttributes().removeAttributeModifiers(sweepingAttributes);
            properties.setComboCount(nativeComboBefore);
            if (!attacked) tag.remove(LAST_ATTACK);
        }
    }
}
