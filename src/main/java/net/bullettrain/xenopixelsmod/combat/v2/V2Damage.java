package net.bullettrain.xenopixelsmod.combat.v2;

import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The one way a v2 move deals damage.
 *
 * <p>Every v2 hit is "the fighter's DragonMineZ melee damage, times a scale". DragonMineZ already
 * computes melee damage for any player attack, with the fighter's stats, form and weapon
 * multipliers, the target's defence, blocking and parries, and it lets a listener set the amount
 * before all of that through {@code DMZEvent.DamageModifyEvent}. So a v2 hit is an ordinary player
 * attack whose amount this class sets to {@code meleeDamage * scale}, and everything downstream
 * stays DragonMineZ's.
 *
 * <p>Three things about that pipeline are handled here, once, instead of at every call site:
 * <ul>
 *   <li><b>Hit cooldown.</b> Minecraft ignores a hit that lands within half a second of the last
 *       one unless it is bigger. A combo is several hits inside that half second, so without
 *       clearing it every second beat of a string silently did nothing. The pace of a string is
 *       the combo graph's timings, enforced by the server, not this cooldown.</li>
 *   <li><b>Stamina.</b> DragonMineZ charges the attacker stamina for each melee hit and empties
 *       the pool when it runs short. v2 spends stamina on defence, so unless
 *       {@code strikesDrainStamina} is on a v2 hit is flagged as a later hit of an already-paid
 *       swing, which DragonMineZ does not charge for.</li>
 *   <li><b>Momentum.</b> DragonMineZ turns a hit thrown at flying speed into a knockback of its
 *       own. A v2 hit's knockback is its reaction and nothing else, or the jab that ends a chase
 *       would throw the target away from the fighter who just caught them.</li>
 * </ul>
 *
 * <p>Server thread only: the "which strike is in flight" fields are read by the listener during
 * the {@code hurt} call that set them.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class V2Damage {

    private static final String FIRST_HIT = "dmz_first_hit";
    private static final String STAMINA_RATIO = "dmz_swing_stamina_ratio";
    private static final String SERVER_SPEED = "dmz_server_speed";

    private static ServerPlayer activeAttacker;
    private static LivingEntity activeVictim;
    private static float activeScale;

    private V2Damage() {}

    /**
     * Lands one hit of {@code scale} times the attacker's melee damage.
     *
     * @return true when the target was actually hurt; false when the hit was dodged, refused or
     *         cancelled by anything along the way
     */
    static boolean strike(ServerPlayer attacker, LivingEntity target, float scale) {
        if (target == null || !target.isAlive() || scale <= 0f) return false;
        StatsData stats = V2Support.stats(attacker);
        boolean dmzCharacter = stats != null && stats.getStatus() != null
                && stats.getStatus().isHasCreatedCharacter();
        // DragonMineZ adds whatever the raw amount exceeds 1 by on top of its own number, so its
        // characters pass exactly 1. Anyone else gets a plain scaled vanilla hit.
        float raw = dmzCharacter ? 1.0f : vanillaDamage(attacker) * scale;

        CompoundTag tag = attacker.getPersistentData();
        boolean skipStamina = dmzCharacter && !V2Config.get().strikesDrainStamina;
        boolean hadFirst = tag.contains(FIRST_HIT);
        boolean first = tag.getBoolean(FIRST_HIT);
        boolean hadRatio = tag.contains(STAMINA_RATIO);
        double ratio = tag.getDouble(STAMINA_RATIO);
        boolean hadSpeed = tag.contains(SERVER_SPEED);
        double speed = tag.getDouble(SERVER_SPEED);

        ServerPlayer outerAttacker = activeAttacker;
        LivingEntity outerVictim = activeVictim;
        float outerScale = activeScale;
        int cooldownBefore = target.invulnerableTime;
        try {
            if (skipStamina) {
                tag.putBoolean(FIRST_HIT, false);
                tag.putDouble(STAMINA_RATIO, 1.0);
            }
            if (dmzCharacter) tag.putDouble(SERVER_SPEED, 0.0);
            target.invulnerableTime = 0;
            activeAttacker = attacker;
            activeVictim = target;
            activeScale = scale;
            boolean hurt = target.hurt(attacker.damageSources().playerAttack(attacker), raw);
            // A hit that did not land must not leave the target open to everything else as well.
            if (!hurt) target.invulnerableTime = Math.max(target.invulnerableTime, cooldownBefore);
            return hurt;
        } finally {
            activeAttacker = outerAttacker;
            activeVictim = outerVictim;
            activeScale = outerScale;
            if (skipStamina) {
                restore(tag, FIRST_HIT, hadFirst, first);
                if (hadRatio) tag.putDouble(STAMINA_RATIO, ratio);
                else tag.remove(STAMINA_RATIO);
            }
            if (dmzCharacter) {
                if (hadSpeed) tag.putDouble(SERVER_SPEED, speed);
                else tag.remove(SERVER_SPEED);
            }
        }
    }

    /** What {@link #strike} aims to deal before defence, for events that report a number. */
    static float nominal(ServerPlayer attacker, float scale) {
        StatsData stats = V2Support.stats(attacker);
        if (stats != null && stats.getStatus() != null && stats.getStatus().isHasCreatedCharacter()) {
            return (float) (stats.getMeleeDamage() * scale);
        }
        return vanillaDamage(attacker) * scale;
    }

    private static float vanillaDamage(ServerPlayer attacker) {
        return (float) Math.max(1.0, attacker.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    private static void restore(CompoundTag tag, String key, boolean had, boolean value) {
        if (had) tag.putBoolean(key, value);
        else tag.remove(key);
    }

    /**
     * Sets the amount of the v2 strike in flight. Highest priority so every other listener, here
     * or in an addon, multiplies the v2 number rather than being overwritten by it.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamageModify(DMZEvent.DamageModifyEvent event) {
        if (activeAttacker == null || event.getAttacker() != activeAttacker
                || event.getVictim() != activeVictim
                || event.getSourceType() != DMZEvent.DamageSourceType.MELEE) {
            return;
        }
        StatsData stats = V2Support.stats(activeAttacker);
        if (stats == null) return;
        event.setAmount(Math.max(0.0, stats.getMeleeDamage() * activeScale));
    }
}
