package net.bullettrain.xenopixelsmod.combat.clone;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import net.bullettrain.xenopixelsmod.compat.npc.*;
import net.bullettrain.xenopixelsmod.event.DmzMasterProtection;
import net.bullettrain.xenopixelsmod.features.party.PartyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Server-only adapter. Copies never receive generic NPC profiles or independent resource pools. */
public final class CloneCombatBridge {
    private NpcCombatProfile profile;
    private int refreshAt;
    private int cooldown;
    private java.util.UUID attackTarget;

    public static ServerPlayer owner(XenoCloneEntity clone) {
        Entity raw = clone.level().getEntity(clone.ownerId());
        return raw instanceof ServerPlayer player && player.isAlive()
                && player.getUUID().equals(clone.ownerUuid()) ? player : null;
    }

    public static XenoCloneEntity sourceClone(Entity source) {
        if (source instanceof XenoCloneEntity clone) return clone;
        if (source instanceof Projectile projectile && projectile.getOwner() instanceof XenoCloneEntity clone) return clone;
        return null;
    }

    public static boolean validTarget(ServerPlayer owner, LivingEntity target) {
        return target != null && target.isAlive() && permittedTarget(owner, target);
    }

    public static boolean permittedTarget(ServerPlayer owner, LivingEntity target) {
        if (owner == null || target == null || target == owner
                || target.isSpectator() || target.level() != owner.level()
                || target instanceof XenoCloneEntity || owner.isAlliedTo(target)
                || DmzMasterProtection.isDmzMaster(target)) return false;
        if (target instanceof net.minecraft.world.entity.TamableAnimal pet
                && pet.isOwnedBy(owner)) return false;
        if (target instanceof ServerPlayer player) {
            return !player.isCreative() && owner.getServer().isPvpAllowed() && owner.canHarmPlayer(player)
                    && (!PartyManager.sameParty(owner, player)
                    || com.dragonminez.common.quest.PartyManager.isPartyPvpEnabled(owner));
        }
        return true;
    }

    public static boolean active(XenoCloneEntity clone) {
        return clone.isAlive() && !clone.isRemoved() && !clone.isRecalling()
                && clone.slot() != XenoCloneEntity.SLOT_STATIONARY
                && clone.travelProgress() >= 1 && XenoCloneSystem.owns(clone);
    }

    private static StatsData stats(ServerPlayer player) {
        return player == null ? null : StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
    }

    public static com.dragonminez.common.stats.techniques.StrikeAttackData strike(XenoCloneEntity clone, String id) {
        StatsData data = stats(owner(clone));
        if (data == null || id == null
                || net.bullettrain.xenopixelsmod.combat.technique.XenoRushTechniques.isRushId(id)) return null;
        var technique = data.getTechniques().getUnlockedTechniques().get(id);
        return technique instanceof com.dragonminez.common.stats.techniques.StrikeAttackData strike
                && PredefinedTechniqueLookup.findStrike(id) != null ? strike : null;
    }

    public static double strikeCost(XenoCloneEntity clone,
            com.dragonminez.common.stats.techniques.StrikeAttackData strike) {
        StatsData data = stats(owner(clone));
        return data == null ? Double.POSITIVE_INFINITY : strike.getCalculatedCost(data);
    }

    public static NpcResources.Snapshot resources(XenoCloneEntity clone) {
        StatsData data = stats(owner(clone));
        return data == null ? new NpcResources.Snapshot(0, 0, 0, 0)
                : new NpcResources.Snapshot(data.getResources().getCurrentEnergy(), data.getMaxEnergy(),
                        data.getResources().getCurrentStamina(), data.getMaxStamina());
    }

    public static boolean spend(XenoCloneEntity clone, double energy, double stamina) {
        if (!active(clone)) return false;
        StatsData data = stats(owner(clone));
        if (data == null) return false;
        var resources = data.getResources();
        if (!CloneCombatPolicy.canSpend(resources.getCurrentEnergy(), resources.getCurrentStamina(), energy, stamina)) return false;
        // The logical server serializes all bodies' actions. Never mirror/refill an NPC pool.
        resources.removeEnergy((float) energy);
        resources.removeStamina((float) stamina);
        return true;
    }

    public NpcCombatProfile profile(XenoCloneEntity clone) {
        if (profile == null || clone.tickCount >= refreshAt) {
            StatsData data = stats(owner(clone));
            profile = new NpcCombatProfile();
            if (data != null) {
                profile.setEffectiveDamage(data.getMeleeDamage(), data.getStrikeDamage(),
                        data.getKiDamage() * data.getKiAttackDamageModifier());
                // Raw PWR affects projectile geometry, not the effective damage override.
                profile.kiPower = Math.min(100, Math.max(0, data.getStats().getKiPower()));
                profile.techniques.addAll(data.getTechniques().getUnlockedTechniques().keySet());
            }
            refreshAt = clone.tickCount + 10;
        }
        return profile;
    }

    public void cancel(XenoCloneEntity clone) {
        NpcStrikeDispatcher.cancel(clone.getUUID());
        NpcKiAim.cancel(clone);
        NpcMeleeDamage.clearAnimation(clone.getUUID());
        clone.setDeltaMovement(Vec3.ZERO);
    }

    /** Returns true when combat owns movement this tick. */
    public boolean tick(XenoCloneEntity clone, ServerPlayer owner, LivingEntity target) {
        if (cooldown > 0) cooldown--;
        boolean valid = validTarget(owner, target) && owner.distanceToSqr(target) <= CloneCombatPolicy.LEASH * CloneCombatPolicy.LEASH;
        var action = CloneCombatPolicy.decide(active(clone), valid, clone.distanceTo(owner),
                valid ? clone.distanceTo(target) : Double.POSITIVE_INFINITY,
                valid && clone.hasLineOfSight(target), cooldown);
        if (action == CloneCombatPolicy.Action.FORMATION) {
            cancel(clone);
            return false;
        }
        if (!target.getUUID().equals(attackTarget)) {
            cancel(clone);
            attackTarget = target.getUUID();
        }
        NpcCombatProfile current = profile(clone);
        if (action == CloneCombatPolicy.Action.MELEE) {
            // The dispatcher owns spending and delayed impact; never pre-charge its action.
            boolean strike = false;
            for (String id : current.techniques) {
                var ownedStrike = strike(clone, id);
                if (ownedStrike != null && NpcStrikeDispatcher.fire(id, clone, current, target)) {
                    strike = true;
                    cooldown = Math.max(20, ownedStrike.getActualCastTime() + 20);
                    NpcDmzAnim.play(clone, net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent.BODY_PUNCH_RIGHT);
                    break;
                }
            }
            if (!strike) {
                StatsData data = stats(owner);
                if (data != null && spend(clone, 0, Math.max(1, data.getStaminaPerHit()))) {
                    clone.swing(InteractionHand.MAIN_HAND, true);
                    NpcDmzAnim.play(clone, net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent.BODY_PUNCH_RIGHT);
                    target.hurt(clone.damageSources().mobAttack(clone), current.meleeDamage());
                }
            }
            if (!strike) cooldown = 20;
        } else if (action == CloneCombatPolicy.Action.RANGED) {
            if (current.kiDamage() > 0 && spend(clone, Math.max(1, current.kiDamage() * 0.1), 0)) {
                NpcKiAttackDispatcher.fireKiBlast(clone, current, 40, target, 0);
            }
            cooldown = 30;
        }
        // Keep closing even during ranged recovery; no simultaneous formation teleports.
        if (action == CloneCombatPolicy.Action.APPROACH
                || clone.distanceTo(target) > CloneCombatPolicy.MELEE_RANGE) move(clone, target.position(), 0.32);
        else clone.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    public static void move(XenoCloneEntity clone, Vec3 destination, double speed) {
        Vec3 delta = destination.subtract(clone.position());
        if (delta.lengthSqr() > speed * speed) delta = delta.normalize().scale(speed);
        clone.noPhysics = false;
        if (!clone.level().noCollision(clone, clone.getBoundingBox().move(delta))) {
            // Bounded local steering, not a teleport: try side steps and then a step upward.
            Vec3 side = new Vec3(-delta.z, 0, delta.x);
            if (side.lengthSqr() > 1e-8) side = side.normalize().scale(speed);
            Vec3[] alternatives = {side, side.scale(-1), new Vec3(0, speed, 0)};
            for (Vec3 alternative : alternatives) {
                if (alternative.lengthSqr() > 1e-8
                        && clone.level().noCollision(clone, clone.getBoundingBox().move(alternative))) {
                    delta = alternative;
                    break;
                }
            }
        }
        clone.move(MoverType.SELF, delta);
        clone.setDeltaMovement(delta);
    }
}
