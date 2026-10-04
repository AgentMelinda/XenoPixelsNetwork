package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.anim.CombatStateAnim;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.DmzAnimHelper;
import net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charged punch / kick for NPCs: a hold clock, a live percent, and a cone release.
 *
 * <p>Player charge lives on {@code Bt3CombatPacket} and is typed to {@code ServerPlayer}.
 * This is the NPC clock, not a wrapper. Damage and kick launch reuse the same config
 * and the public kick helpers; stamina goes through {@link NpcResources}.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcChargeMoves {
    public enum Style {
        PUNCH,
        KICK
    }

    private record Charge(Style style, int startTick, int holdTicks, int verticalBias) {}

    private static final Map<UUID, Charge> CHARGES = new ConcurrentHashMap<>();

    private NpcChargeMoves() {}

    public static int clampBias(int bias) {
        return Math.max(-1, Math.min(1, bias));
    }

    public static int holdTicks(int durationTicks, int maxTicks) {
        int max = Math.max(1, maxTicks);
        if (durationTicks <= 0) {
            return max;
        }
        return Math.max(1, Math.min(max, durationTicks));
    }

    public static int percentOf(int chargeTicks, int maxTicks) {
        int max = Math.max(1, maxTicks);
        int ticks = Math.max(0, chargeTicks);
        return Math.round(Math.min(1.0f, ticks / (float) max) * 100.0f);
    }

    public static float damageCharge(int percent) {
        return Math.max(0.25f, Math.min(1.0f, percent / 100.0f));
    }

    public static boolean fullyCharged(float charge) {
        return charge >= 0.95f;
    }

    public static boolean startPunch(LivingEntity npc, int durationTicks) {
        return start(npc, Style.PUNCH, durationTicks, 0);
    }

    public static boolean startKick(LivingEntity npc, int durationTicks, int verticalBias) {
        return start(npc, Style.KICK, durationTicks, verticalBias);
    }

    public static boolean start(LivingEntity npc, Style style, int durationTicks, int verticalBias) {
        if (!canStart(npc) || style == null) {
            return false;
        }
        NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
        if (profile.combatBrain && !profile.brainCharge) {
            return false;
        }
        int max = XenoServerConfig.chargeMaxTicks;
        int now = serverTick(npc);
        int hold = holdTicks(durationTicks, max);
        Charge previous = CHARGES.put(npc.getUUID(), new Charge(
                style, now, hold, clampBias(verticalBias)));
        if (previous != null) {
            net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.stopClip(npc);
        }
        NpcEntityLookup.remember(npc);
        CombatStateAnim.play(npc, style == Style.KICK
                ? TechniqueAnimSlot.CHARGE_KICK : TechniqueAnimSlot.CHARGE_PUNCH);
        if (!NpcDmzAnim.canAnimate(npc)) {
            NpcKiAim.playMelee(npc);
        }
        glowStart(npc, style == Style.KICK, hold);
        return true;
    }

    public static boolean release(LivingEntity npc) {
        if (npc == null || !(npc.level() instanceof ServerLevel)) {
            return false;
        }
        Charge charge = CHARGES.remove(npc.getUUID());
        if (charge == null) {
            return false;
        }
        glowEnd(npc);
        int elapsed = Math.max(1, serverTick(npc) - charge.startTick());
        return fire(npc, charge, elapsed);
    }

    public static boolean cancel(LivingEntity npc) {
        if (npc == null) {
            return false;
        }
        if (CHARGES.remove(npc.getUUID()) == null) {
            return false;
        }
        glowEnd(npc);
        net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.stopClip(npc);
        return true;
    }

    public static void forget(UUID npcId) {
        if (npcId != null) {
            CHARGES.remove(npcId);
        }
    }

    public static boolean isCharging(LivingEntity npc) {
        return npc != null && CHARGES.containsKey(npc.getUUID());
    }

    public static int getPercent(LivingEntity npc) {
        Charge charge = npc == null ? null : CHARGES.get(npc.getUUID());
        if (charge == null) {
            return 0;
        }
        return percentOf(serverTick(npc) - charge.startTick(), XenoServerConfig.chargeMaxTicks);
    }

    public static String getStyle(LivingEntity npc) {
        Charge charge = npc == null ? null : CHARGES.get(npc.getUUID());
        if (charge == null) {
            return "";
        }
        return charge.style() == Style.KICK ? "kick" : "punch";
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (CHARGES.isEmpty()) {
            return;
        }
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Charge> entry = it.next();
            LivingEntity npc = NpcEntityLookup.findAlive(event.getServer(), entry.getKey());
            if (npc == null) {
                continue;
            }
            Charge charge = entry.getValue();
            int elapsed = now - charge.startTick();
            if (elapsed >= charge.holdTicks()) {
                it.remove();
                glowEnd(npc);
                fire(npc, charge, elapsed);
            }
        }
    }

    private static boolean fire(LivingEntity npc, Charge charge, int elapsedTicks) {
        if (!(npc.level() instanceof ServerLevel level) || !npc.isAlive()) {
            return false;
        }
        NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
        int percent = percentOf(elapsedTicks, XenoServerConfig.chargeMaxTicks);
        float amount = damageCharge(percent);
        boolean kick = charge.style() == Style.KICK;
        int bias = kick ? charge.verticalBias() : 0;
        boolean full = fullyCharged(amount);
        net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.stopClip(npc);
        playRelease(npc, kick, full, bias);
        float stam = kick
                ? XenoServerConfig.kickReleaseStamina(amount, bias)
                : XenoServerConfig.fistReleaseStamina(amount);
        if (!NpcResources.spendStamina(npc, profile, stam)) {
            return true;
        }
        LivingEntity aimed = preferredTarget(npc);
        if (aimed != null && aimed.isAlive() && aimed != npc) {
            hitOne(npc, profile, aimed, kick, amount, bias, full);
            return true;
        }
        cone(npc, profile, kick, amount, bias, full);
        return true;
    }

    private static void playRelease(LivingEntity npc, boolean kick, boolean full, int bias) {
        float speed = full ? 1.2f : 1.05f;
        if (kick) {
            if (CombatStateAnim.hasCustom(npc, TechniqueAnimSlot.CHARGE_KICK_FIRE)) {
                CombatStateAnim.play(npc, TechniqueAnimSlot.CHARGE_KICK_FIRE);
                return;
            }
            String primary = bias < 0 ? DmzAnimHelper.KICK_LOW_R : DmzAnimHelper.KICK_GUT_R;
            NpcDmzAnim.broadcast(npc, primary, speed, 0);
            return;
        }
        if (CombatStateAnim.hasCustom(npc, TechniqueAnimSlot.CHARGE_PUNCH_FIRE)) {
            CombatStateAnim.play(npc, TechniqueAnimSlot.CHARGE_PUNCH_FIRE);
            return;
        }
        NpcDmzAnim.broadcast(npc,
                full ? DmzAnimHelper.CHARGE_HEAVY_FIRE : DmzAnimHelper.CHARGE_LIGHT_FIRE,
                speed, 0);
    }

    private static void glowStart(LivingEntity npc, boolean kick, int holdTicks) {
        int flags = kick ? NpcDmzAnim.FLAG_CHARGE_KICK : NpcDmzAnim.FLAG_CHARGE_PUNCH;
        NpcDmzAnim.broadcast(npc, NpcDmzAnim.CHARGE_GLOW, holdTicks, flags);
    }

    private static void glowEnd(LivingEntity npc) {
        NpcDmzAnim.broadcast(npc, NpcDmzAnim.CHARGE_GLOW, 1.0f, NpcDmzAnim.FLAG_STOP);
    }

    private static void cone(LivingEntity npc, NpcCombatProfile profile, boolean kick,
                             float charge, int bias, boolean full) {
        Vec3 look = npc.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0e-4) {
            flat = new Vec3(0, 0, 1);
        }
        flat = flat.normalize();
        double range = kick ? net.bullettrain.xenopixelsmod.combat.Bt3Landing.kickHitRange(charge, bias)
                : XenoServerConfig.chargeAttackRange;
        double inflate = kick && bias < 0 ? 1.85 : 1.35;
        var box = npc.getBoundingBox().expandTowards(flat.scale(range)).inflate(inflate);
        for (LivingEntity living : npc.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != npc && e.isAlive())) {
            Vec3 to = living.position().add(0, living.getBbHeight() * 0.4, 0)
                    .subtract(npc.getEyePosition());
            if (to.lengthSqr() > range * range) {
                continue;
            }
            Vec3 flatTo = new Vec3(to.x, 0, to.z);
            if (flatTo.lengthSqr() < 1.0e-4) {
                continue;
            }
            if (flat.dot(flatTo.normalize()) < 0.25) {
                continue;
            }
            applyHit(npc, profile, living, flat, kick, charge, bias, full);
        }
    }

    private static void hitOne(LivingEntity npc, NpcCombatProfile profile, LivingEntity target,
                               boolean kick, float charge, int bias, boolean full) {
        double range = kick ? net.bullettrain.xenopixelsmod.combat.Bt3Landing.kickHitRange(charge, bias)
                : XenoServerConfig.chargeAttackRange;
        if (npc.distanceTo(target) > range + 1.5) {
            cone(npc, profile, kick, charge, bias, full);
            return;
        }
        Vec3 dir = target.position().subtract(npc.position());
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0e-4) {
            flat = npc.getLookAngle();
            flat = new Vec3(flat.x, 0, flat.z);
        }
        if (flat.lengthSqr() > 1.0e-4) {
            flat = flat.normalize();
        }
        applyHit(npc, profile, target, flat, kick, charge, bias, full);
    }

    private static void applyHit(LivingEntity npc, NpcCombatProfile profile, LivingEntity target,
                                 Vec3 flat, boolean kick, float charge, int bias, boolean full) {
        float mult = XenoServerConfig.chargeDamageScale * (0.55f + 0.7f * charge);
        if (kick) {
            mult *= XenoServerConfig.kickDamageScale;
        }
        float dmzDamage = (float) Math.max(2.0, NpcDmzStats.meleeDamage(npc, profile) * mult);
        float damage = switch (XenoServerConfig.normalizedNpcDamageMode()) {
            case "numeric" -> XenoServerConfig.npcNumericDamage;
            case "mynpc" -> dmzDamage;
            default -> dmzDamage;
        };
        target.hurt(npc.damageSources().mobAttack(npc), damage);
        if (flat.lengthSqr() > 1.0e-4) {
            if (kick) {
                CombatKnockback.set(target, net.bullettrain.xenopixelsmod.combat.Bt3Landing.kickTargetLaunch(flat, charge, bias));
            } else {
                CombatKnockback.add(target, flat.scale(0.85 * (0.6 + charge))
                        .add(0, 0.18 + charge * 0.15, 0));
            }
        }
        if (npc.level() instanceof ServerLevel level) {
            CombatFx.impact(level, npc, target, flat,
                    full ? CombatFx.Weight.HEAVY : CombatFx.Weight.LIGHT);
        }
    }

    private static LivingEntity preferredTarget(LivingEntity npc) {
        LivingEntity locked = NpcKiAim.lockedTarget(npc);
        if (locked != null) {
            return locked;
        }
        if (npc instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            return mob.getTarget();
        }
        return null;
    }

    private static boolean canStart(LivingEntity npc) {
        return npc != null && npc.isAlive() && !npc.level().isClientSide()
                && NpcCombatProfile.hasProfile(npc)
                && !NpcTransformSystem.isHolding(npc.getUUID());
    }

    private static int serverTick(LivingEntity npc) {
        return npc.level() instanceof ServerLevel level
                ? level.getServer().getTickCount() : npc.tickCount;
    }
}
