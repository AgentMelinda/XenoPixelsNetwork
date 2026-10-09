package net.bullettrain.xenopixelsmod.combat.v2;

import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.combat.Bt3KickAndPunchRules;
import net.bullettrain.xenopixelsmod.combat.Bt3Landing;
import net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.FistInputPolicy;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.event.DmzMasterProtection;
import net.bullettrain.xenopixelsmod.features.party.PartyManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The few things every v2 move needs: the clock, the resource pools, who may be hit, and how a
 * reaction is put on a body. Kept in one place so no move grows its own copy.
 */
final class V2Support {

    private V2Support() {}

    /** The one clock v2 uses: the global server tick, never a per-entity counter. */
    static int tick(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? 0 : server.getTickCount();
    }

    static StatsData stats(ServerPlayer player) {
        try {
            return StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Spends ki and stamina together or not at all.
     *
     * <p>A missing DragonMineZ capability means "cannot act", not "free": a player mid-join has no
     * pools yet and must not get every move for nothing.
     */
    static boolean spend(ServerPlayer player, float ki, float stamina) {
        if (ki <= 0f && stamina <= 0f) return true;
        if (player.isCreative()) return true;
        StatsData data = stats(player);
        Resources res = data == null ? null : data.getResources();
        if (res == null) return false;
        if (ki > 0f && res.getCurrentEnergy() < ki) return false;
        if (stamina > 0f && res.getCurrentStamina() < stamina) return false;
        if (ki > 0f) res.removeEnergy(ki);
        if (stamina > 0f) res.removeStamina(stamina);
        return true;
    }

    /** The same "fists are out" rule the client uses, so a forged packet cannot swing a sword. */
    static boolean emptyHands(ServerPlayer player) {
        boolean kiWeapon;
        try {
            kiWeapon = PlayerAttackHelper.isKiWeaponActive(player);
        } catch (Throwable t) {
            kiWeapon = false;
        }
        return FistInputPolicy.emptyHands(player.getMainHandItem().isEmpty(),
                player.getOffhandItem().isEmpty(), kiWeapon);
    }

    /**
     * DragonMineZ's own hard stun: its stun effect, a knockdown, or being held in one of its
     * strike techniques. Nothing of v2's may start while it lasts.
     */
    static boolean dmzStunned(ServerPlayer player) {
        StatsData data = stats(player);
        return data != null && data.getStatus() != null && data.getStatus().isStunned();
    }

    /** Whether attacks pass through {@code player} right now: a v2 vanish, or Sparking's. */
    static boolean invulnerable(ServerPlayer player, int now) {
        V2Fighter f = V2FighterStore.peek(player);
        return (f != null && f.invulnerable(now)) || Bt3SparkingSystem.hasIFrames(player);
    }

    /** A living, same-level entity for a client-supplied id, or null. Never trusts the id. */
    static LivingEntity living(ServerPlayer player, int entityId) {
        if (entityId < 0) return null;
        Entity raw = player.level().getEntity(entityId);
        return raw instanceof LivingEntity living && living.isAlive() && living != player ? living : null;
    }

    /**
     * Why {@code target} may not be attacked by {@code player}, or null when it may.
     *
     * <p>DragonMineZ masters are never fair game, and a party with friendly fire off cannot be
     * hit by its own members. Damage would be refused for both anyway; checking here keeps the
     * moves that are not damage (a grab's hold, a travel's arrival) under the same rule.
     */
    static String refusal(ServerPlayer player, LivingEntity target) {
        if (target == null) return "No target";
        if (DmzMasterProtection.isDmzMaster(target)) return "Masters cannot be attacked";
        if (target instanceof ServerPlayer other) {
            if (other.isSpectator() || other.isCreative()) return "That player cannot be attacked";
            // The server's PvP switch and team friendly-fire rule. Damage already obeys them; a
            // grab's hold and a throw's launch are not damage and would otherwise ignore both.
            if (!player.canHarmPlayer(other)) return "PvP is off here";
            try {
                if (PartyManager.sameParty(player, other)
                        && !com.dragonminez.common.quest.PartyManager.isPartyPvpEnabled(player)) {
                    return "Party friendly fire is disabled";
                }
            } catch (Throwable ignored) {
                // No party data: treat as not in a party.
            }
        }
        return null;
    }

    // ---- reactions ----

    /**
     * Puts a reaction on a victim, pushing away from the attacker.
     *
     * @param charge 0..1, how long the attack was held; shapes a kick's arc
     */
    static void react(ServerPlayer attacker, LivingEntity victim, HitReaction reaction, float charge) {
        if (!reaction.moves()) return;
        double awayX = victim.getX() - attacker.getX();
        double awayZ = victim.getZ() - attacker.getZ();
        if (awayX * awayX + awayZ * awayZ < 1.0e-6) {
            // Standing inside the victim: away is wherever the attacker is facing.
            Vec3 look = attacker.getLookAngle();
            awayX = look.x;
            awayZ = look.z;
        }
        if (reaction.isKick()) {
            CombatKnockback.set(victim, kickLaunch(reaction, awayX, awayZ, charge));
            return;
        }
        apply(victim, reaction, reaction.impulse(awayX, awayZ, V2Config.get().reactionScale));
    }

    /** Puts a reaction on a victim along the attacker's facing, as a throw does. */
    static void reactAlongLook(ServerPlayer attacker, LivingEntity victim, HitReaction reaction) {
        Vec3 look = attacker.getLookAngle();
        apply(victim, reaction, reaction.impulse(look.x, look.z, V2Config.get().reactionScale));
    }

    /**
     * A kick's launch, from the same functions and the same server tuning v1 kicks use, so a v2
     * kick sends a target exactly where the server owner already tuned kicks to send it.
     */
    private static Vec3 kickLaunch(HitReaction reaction, double awayX, double awayZ, float charge) {
        Vec3 awayFlat = new Vec3(awayX, 0.0, awayZ);
        float c = Bt3KickAndPunchRules.kickCharge(
                Math.max(0f, Math.min(1f, charge)), XenoServerConfig.kickTapCharge);
        return Bt3Landing.kickTargetLaunch(awayFlat, c, reaction.kick().verticalBias());
    }

    private static void apply(LivingEntity victim, HitReaction reaction, double[] v) {
        Vec3 impulse = new Vec3(v[0], v[1], v[2]);
        if (reaction.replacesVelocity()) {
            CombatKnockback.set(victim, impulse);
        } else {
            CombatKnockback.add(victim, impulse);
        }
    }

    // ---- feedback ----

    static CombatFx.Weight weight(HitReaction reaction) {
        return reaction == HitReaction.HIT_LIGHT ? CombatFx.Weight.LIGHT : CombatFx.Weight.HEAVY;
    }

    static void impactFx(ServerPlayer attacker, LivingEntity victim, HitReaction reaction) {
        Vec3 blow = victim.position().subtract(attacker.position());
        Vec3 flat = new Vec3(blow.x, 0.0, blow.z);
        if (flat.lengthSqr() < 1.0e-4) flat = attacker.getLookAngle();
        CombatFx.impact(attacker.serverLevel(), attacker, victim, flat, weight(reaction));
        hitSound(attacker, victim, reaction != HitReaction.HIT_LIGHT);
    }

    /** DragonMineZ's own connect sounds, so a v2 hit does not sound like a vanilla sword. */
    static void hitSound(ServerPlayer attacker, LivingEntity victim, boolean heavy) {
        SoundEvent sound;
        try {
            if (heavy) {
                sound = (attacker.tickCount & 1) == 0
                        ? com.dragonminez.common.init.MainSounds.CRITICO1.get()
                        : com.dragonminez.common.init.MainSounds.CRITICO2.get();
            } else {
                sound = switch (Math.floorMod(attacker.tickCount, 3)) {
                    case 1 -> com.dragonminez.common.init.MainSounds.GOLPE2.get();
                    case 2 -> com.dragonminez.common.init.MainSounds.GOLPE3.get();
                    default -> com.dragonminez.common.init.MainSounds.GOLPE1.get();
                };
            }
        } catch (Throwable t) {
            return;
        }
        if (sound == null) return;
        attacker.level().playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() * 0.4,
                victim.getZ(), sound, SoundSource.PLAYERS, heavy ? 1.15f : 0.95f, heavy ? 0.9f : 1.05f);
    }

    static float yawToward(Entity from, double x, double z) {
        return (float) (Math.toDegrees(Math.atan2(z - from.getZ(), x - from.getX())) - 90.0);
    }

    /** Turns the body other players see toward a point, without touching the owner's camera. */
    static void faceBody(ServerPlayer player, double x, double z) {
        float yaw = yawToward(player, x, z);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
    }

    static void hint(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal("§7" + message), true);
    }
}
