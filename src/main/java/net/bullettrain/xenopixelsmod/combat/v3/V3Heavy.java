package net.bullettrain.xenopixelsmod.combat.v3;

import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.MeleeAnimationS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.combat.FistInputPolicy;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * The V3 heavy attack: right tap while locked on.
 *
 * <p>The attacker pays stamina once when the action is admitted, whether or not it lands. The
 * victim is drained only when the hit is accepted as positive damage after every guard, dodge and
 * protection rule has had its say. The reaction is a fixed directional shove; nothing here counts
 * hits or launches anyone upward.
 *
 * <p>Server thread only: the in-flight fields are read by the listeners during the {@code hurt}
 * call that set them.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class V3Heavy {
    /** Plan balancing values, not BT3 constants. */
    static final float DAMAGE_SCALE = 1.6f;
    static final double REACH = 4.5;
    static final int COOLDOWN_TICKS = 14;
    static final double SHOVE = 0.9;
    static final double SHOVE_LIFT = 0.22;

    private static final String FIRST_HIT = "dmz_first_hit";
    private static final String STAMINA_RATIO = "dmz_swing_stamina_ratio";

    private static ServerPlayer activeAttacker;
    private static LivingEntity activeVictim;
    private static float acceptedDamage;
    private static float activeScale = DAMAGE_SCALE;

    private V3Heavy() {}

    public enum Start { STARTED, DUPLICATE, UNAFFORDABLE, REFUSED }

    /** One fighter's heavy bookkeeping. Identity is (session, sequence); pure and Minecraft-free. */
    public static final class Transaction {
        private UUID session;
        private int sequence = -1;
        private boolean settled = true;

        public Start start(UUID suppliedSession, int suppliedSequence, boolean targetAllowed,
                           V3Resources.Pool attacker, float cost) {
            if (!targetAllowed || suppliedSession == null || suppliedSequence < 0) return Start.REFUSED;
            if (suppliedSession.equals(session) && suppliedSequence <= sequence) return Start.DUPLICATE;
            if (cost > 0f) {
                if (attacker == null || !(attacker.get() >= cost)) return Start.UNAFFORDABLE;
                attacker.set(attacker.get() - cost);
            }
            session = suppliedSession;
            sequence = suppliedSequence;
            settled = false;
            return Start.STARTED;
        }

        /** @return stamina actually removed from the victim; zero unless this hit was accepted */
        public float accept(UUID suppliedSession, int suppliedSequence, float damage,
                            V3Resources.Pool victim, float drain) {
            if (settled || suppliedSession == null || !suppliedSession.equals(session)
                    || suppliedSequence != sequence || !(damage > 0f)) return 0f;
            settled = true;
            if (victim == null || !(drain > 0f)) return 0f;
            float before = victim.get();
            float taken = Math.min(Math.max(0f, before), drain);
            if (taken > 0f) victim.set(before - taken);
            return taken;
        }
    }

    /** The shove for a direction, relative to the attacker's horizontal facing ({@code lookX, lookZ}). */
    static Vec3 reaction(V3Direction direction, double lookX, double lookY, double lookZ) {
        double length = Math.hypot(lookX, lookZ);
        double fx = length < 1.0e-6 ? 0 : lookX / length;
        double fz = length < 1.0e-6 ? 1 : lookZ / length;
        return switch (direction == null ? V3Direction.NONE : direction) {
            // Minecraft's right of a +Z facing is -X.
            case RIGHT -> new Vec3(-fz * SHOVE, SHOVE_LIFT, fx * SHOVE);
            case LEFT -> new Vec3(fz * SHOVE, SHOVE_LIFT, -fx * SHOVE);
            // A pulled heavy drags the target back toward the attacker, shorter than a shove.
            case BACK -> new Vec3(-fx * SHOVE * 0.5, SHOVE_LIFT, -fz * SHOVE * 0.5);
            case UP -> new Vec3(fx * SHOVE, 0.3, fz * SHOVE);
            case FORWARD, NONE -> new Vec3(fx * SHOVE, SHOVE_LIFT, fz * SHOVE);
        };
    }

    /**
     * Starts one admitted heavy against the approved target.
     *
     * @return true when the action started (and was paid for), whether or not it connected
     */
    public static boolean tap(ServerPlayer player, LivingEntity target, V3Direction direction, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || now < fighter.heavyReadyTick) return false;
        if (!emptyHands(player) || stunned(player)) return false;
        V3Config.Values config = V3Config.get();
        float cost = player.isCreative() ? 0f : (float) config.heavyAttackerStaminaCost();
        Start start = fighter.heavy.start(fighter.session(), fighter.acknowledgedSequence(), target != null,
                V3Resources.stamina(player), cost);
        if (start != Start.STARTED) return false;
        fighter.heavyReadyTick = (long) now + COOLDOWN_TICKS;

        pose(player);
        V3AttackSounds.heavySwing(player);
        if (!inReach(player, target)) {
            return true;
        }

        float accepted = strike(player, target, DAMAGE_SCALE);
        float drained = fighter.heavy.accept(fighter.session(), fighter.acknowledgedSequence(), accepted,
                V3Resources.victimStamina(target), (float) config.heavyVictimStaminaDrain());
        if (accepted > 0f) V3AttackSounds.heavyHit(player, target);
        else V3AttackSounds.contact(player, target);
        if (accepted > 0f) {
            Vec3 look = player.getLookAngle();
            Vec3 shove = reaction(direction, look.x, look.y, look.z);
            CombatKnockback.set(target, shove, player);
            if (player.level() instanceof ServerLevel level) {
                CombatFx.impact(level, player, target, shove, CombatFx.Weight.HEAVY);
            }
        }
        XenoPixelsMod.LOGGER.debug("V3 heavy {} -> {} accepted={} drained={}", player.getUUID(), target.getUUID(),
                accepted, drained);
        return true;
    }

    static boolean emptyHands(ServerPlayer player) {
        boolean kiWeapon;
        try {
            kiWeapon = PlayerAttackHelper.isKiWeaponActive(player);
        } catch (RuntimeException | LinkageError unavailable) {
            kiWeapon = false;
        }
        return FistInputPolicy.emptyHands(player.getMainHandItem().isEmpty(), player.getOffhandItem().isEmpty(), kiWeapon);
    }

    private static StatsData stats(ServerPlayer player) {
        try {
            return StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    static boolean stunned(ServerPlayer player) {
        StatsData data = stats(player);
        return data != null && data.getStatus() != null && data.getStatus().isStunned();
    }

    static boolean inReach(ServerPlayer player, LivingEntity target) {
        Vec3 eye = player.getEyePosition();
        AABB box = target.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - eye.x, 0), eye.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eye.y, 0), eye.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eye.z, 0), eye.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }

    /**
     * Owner: plain right-click heavy uses the realistic charged-kick fire clip
     * ({@code combat.xeno_charge_kick_fire}) instead of the short gut-kick.
     */
    private static void pose(ServerPlayer player) {
        try {
            NetworkHandler.sendToTrackingEntityAndSelf(
                    new MeleeAnimationS2C(player.getId(),
                            net.bullettrain.xenopixelsmod.combat.v2.V2ChargeRules.KICK_FIRE, false, 1.0f), player);
        } catch (RuntimeException | LinkageError unavailable) {
            // Animation is presentation only; the action itself already started.
        }
    }

    /**
     * Lands the prepaid hit through the ordinary player-attack pipeline.
     *
     * @return the positive damage DragonMineZ and every other listener finally accepted, else 0
     */
    static float strike(ServerPlayer attacker, LivingEntity target, float scale) {
        StatsData stats = stats(attacker);
        boolean dmzCharacter = stats != null && stats.getStatus() != null && stats.getStatus().isHasCreatedCharacter();
        float raw = dmzCharacter ? 1.0f
                : (float) Math.max(1.0, attacker.getAttributeValue(Attributes.ATTACK_DAMAGE)) * scale;

        CompoundTag tag = attacker.getPersistentData();
        boolean hadFirst = tag.contains(FIRST_HIT);
        boolean first = tag.getBoolean(FIRST_HIT);
        boolean hadRatio = tag.contains(STAMINA_RATIO);
        double ratio = tag.getDouble(STAMINA_RATIO);
        ServerPlayer outerAttacker = activeAttacker;
        LivingEntity outerVictim = activeVictim;
        float outerAccepted = acceptedDamage;
        float outerScale = activeScale;
        int cooldownBefore = target.invulnerableTime;
        try {
            // Already paid from V3Config: mark this as a later hit of a paid swing so DragonMineZ
            // does not charge its own per-hit stamina on top.
            if (dmzCharacter) {
                tag.putBoolean(FIRST_HIT, false);
                tag.putDouble(STAMINA_RATIO, 1.0);
            }
            target.invulnerableTime = 0;
            activeAttacker = attacker;
            activeVictim = target;
            acceptedDamage = 0f;
            activeScale = scale;
            boolean hurt = target.hurt(attacker.damageSources().playerAttack(attacker), raw);
            if (!hurt) target.invulnerableTime = Math.max(target.invulnerableTime, cooldownBefore);
            // A true return alone is not acceptance; only positive post-mitigation damage is.
            return hurt ? acceptedDamage : 0f;
        } finally {
            activeAttacker = outerAttacker;
            activeVictim = outerVictim;
            acceptedDamage = outerAccepted;
            activeScale = outerScale;
            if (dmzCharacter) {
                if (hadFirst) tag.putBoolean(FIRST_HIT, first);
                else tag.remove(FIRST_HIT);
                if (hadRatio) tag.putDouble(STAMINA_RATIO, ratio);
                else tag.remove(STAMINA_RATIO);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamageModify(DMZEvent.DamageModifyEvent event) {
        if (activeAttacker == null || event.getAttacker() != activeAttacker || event.getVictim() != activeVictim
                || event.getSourceType() != DMZEvent.DamageSourceType.MELEE) return;
        StatsData stats = stats(activeAttacker);
        if (stats != null) event.setAmount(Math.max(0.0, stats.getMeleeDamage() * activeScale));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (activeVictim == null || event.getEntity() != activeVictim
                || event.getSource().getEntity() != activeAttacker) return;
        float dealt = event.getNewDamage();
        if (dealt > 0f && Float.isFinite(dealt)) acceptedDamage = Math.max(acceptedDamage, dealt);
    }
}
