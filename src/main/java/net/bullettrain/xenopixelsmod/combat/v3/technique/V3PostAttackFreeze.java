package net.bullettrain.xenopixelsmod.combat.v3.technique;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.event.TechniqueControlEvent;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.v3.V3CombatServer;
import net.bullettrain.xenopixelsmod.combat.v3.V3Motion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** A completed cinematic's victim hold, independent of the caster's camera lifetime. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class V3PostAttackFreeze {
    private record Hold(ServerPlayer caster, LivingEntity target, Vec3 position, long until,
                        UUID token, boolean noGravity, boolean noAi, boolean motionOwned,
                        net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind kind) {}
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private V3PostAttackFreeze() {}

    public static boolean isFrozen(LivingEntity target) {
        return target != null && HOLDS.containsKey(target.getUUID());
    }
    public record DashTransfer(UUID token,boolean noGravity,boolean noAi) {}
    public static boolean transferable(UUID caster,UUID holder,net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind kind,boolean motionOwned){
        return caster!=null && caster.equals(holder) && kind==net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.DASH && !motionOwned;
    }
    public static DashTransfer captureDash(ServerPlayer caster,LivingEntity target){
        Hold hold=HOLDS.get(target.getUUID());
        return hold!=null && transferable(caster.getUUID(),hold.caster().getUUID(),hold.kind(),hold.motionOwned())
            ?new DashTransfer(hold.token(),hold.noGravity(),hold.noAi()):null;
    }
    public static boolean matchesDash(ServerPlayer caster,LivingEntity target,DashTransfer expected){
        return expected!=null && expected.equals(captureDash(caster,target));
    }
    /** Only the successful new cast commits this transfer; refused casts retain the entire recovery ticket. */
    public static boolean transferDash(ServerPlayer caster,LivingEntity target,DashTransfer expected){
        if(!matchesDash(caster,target,expected))return false;
        Hold hold=HOLDS.get(target.getUUID());finish(hold);return true;
    }

    /** Dash recovery suppresses actions, while ordinary physics/knockback continue. */
    public static boolean allowsKnockback(LivingEntity target) {
        Hold hold = target == null ? null : HOLDS.get(target.getUUID());
        return hold != null && !pinsPosition(hold.kind());
    }
    public static boolean pinsPosition(net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind kind) {
        return kind != net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.DASH;
    }

    /** DASH recovery may replace a pinned rush/finisher hold so the dash hit can move its target. */
    public static boolean replaces(net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind current,
                                   net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind incoming) {
        return incoming == net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.DASH
                && current != net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.DASH;
    }

    /** Exclusive end tick; zero disables the effect rather than pinning once. */
    public static long until(long completedAt, int ticks) {
        return completedAt + Math.clamp(ticks, 0, 1200);
    }

    public static void start(ServerPlayer caster, LivingEntity target, String techniqueId, long now, int ticks) {
        start(caster,target,techniqueId,now,ticks,net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.RUSH);
    }
    public static void start(ServerPlayer caster, LivingEntity target, String techniqueId, long now, int ticks,
                              net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind kind) {
        if (ticks <= 0 || target == caster || !target.isAlive() || target.isRemoved()
                || caster.level() != target.level() || !CombatKnockback.canKnockBack(target)) return;
        Hold existing = HOLDS.get(target.getUUID());
        if (existing != null && !replaces(existing.kind(), kind)) return;
        if (NeoForge.EVENT_BUS.post(new TechniqueControlEvent(caster, target, techniqueId)).isCanceled()) return;
        if (!caster.isAlive() || !target.isAlive() || target.isRemoved() || caster.level() != target.level()
                || !CombatKnockback.canKnockBack(target) || HOLDS.get(target.getUUID()) != existing) return;
        if (existing != null) finish(existing);
        UUID token = UUID.randomUUID();
        boolean gravity = target.isNoGravity(), ai = target instanceof Mob mob && mob.isNoAi();
        boolean motionOwned = false;
        if (target instanceof ServerPlayer player) {
            // Do not steal another action's movement owner.
            if (V3Motion.owns(player)) return;
            if (pinsPosition(kind)) {
                motionOwned = V3Motion.acquire(player, V3Motion.Owner.CINEMATIC, token);
                if (!motionOwned) return;
            }
            V3CombatServer.setCinematic(player, true);
        } else if (target instanceof Mob mob) {
            // Any Mob, not just XenoNpcEntity: DMZ NPCs, MyNPCs/CustomNPCs and vanilla mobs reach
            // this hold too, and each one used to be frozen with no restore path at all.
            net.bullettrain.xenopixelsmod.combat.FreezeOwnership.acquire(
                    mob, net.bullettrain.xenopixelsmod.combat.FreezeOwnership.Reason.CINEMATIC);
        }
        Hold hold = new Hold(caster, target, target.position(), until(now, ticks), token, gravity, ai, motionOwned, kind);
        HOLDS.put(target.getUUID(), hold);
        if (pinsPosition(kind)) {
            target.setNoGravity(true);
            V3CombatServer.techniqueFreeze(caster, target, hold.position());
        }
        if(target instanceof ServerPlayer player)
            net.bullettrain.xenopixelsmod.combat.v3.CombatRecovery.begin(player,token,now,ticks,kind,()->finish(hold));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();
        for (Hold hold : List.copyOf(HOLDS.values())) {
            LivingEntity target = hold.target();
            if (now >= hold.until() || !hold.caster().isAlive() || !target.isAlive() || target.isRemoved()
                    || target.level() != hold.caster().level() || !CombatKnockback.canKnockBack(target)
                    || target.isPassenger() || target.isVehicle()) {
                finish(hold); continue;
            }
            if (hold.motionOwned() && target instanceof ServerPlayer player
                    && !V3Motion.holdCinematic(player, hold.token())) {
                if (V3Motion.owns(player) || !V3Motion.acquire(player, V3Motion.Owner.CINEMATIC, hold.token())) {
                    finish(hold); continue;
                }
                V3CombatServer.setCinematic(player, true);
            }
            if (pinsPosition(hold.kind())) {
                target.setNoGravity(true);
                V3CombatServer.techniqueFreeze(hold.caster(), target, hold.position());
            }
        }
    }

    private static void finish(Hold hold) {
        if (!HOLDS.remove(hold.target().getUUID(), hold)) return;
        if(hold.target() instanceof ServerPlayer player)
            net.bullettrain.xenopixelsmod.combat.v3.CombatRecovery.cancel(player,hold.token());
        if (hold.target() instanceof ServerPlayer player) {
            if (hold.motionOwned()) V3Motion.releaseCinematic(player, hold.token());
            if (V3Motion.owns(player)) return; // a newer owner restores its own gravity/state
            V3CombatServer.setCinematic(player, false);
        } else if (hold.target() instanceof Mob mob) {
            // FreezeOwnership restores the value it captured at acquire time, so an editor-placed
            // NoAI NPC goes back to NoAI and a normal one goes back to awake. It also no-ops when
            // another reason is still holding, which a bare setNoAi(hold.noAi()) could not know.
            net.bullettrain.xenopixelsmod.combat.FreezeOwnership.release(
                    mob, net.bullettrain.xenopixelsmod.combat.FreezeOwnership.Reason.CINEMATIC);
        }
        if (pinsPosition(hold.kind())) hold.target().setNoGravity(hold.noGravity());
        hold.target().resetFallDistance();
    }

    /**
     * Ends every hold this entity is part of, as target or caster.
     *
     * <p>Public and widened to {@code Entity} so {@code FreezeLifecycle} can call it when an entity
     * leaves a level. It used to run only on player logout, clone and server stop, which left a mob
     * that chunk-unloaded mid-hold holding a strong reference here forever — and holding its
     * {@code NoAI} freeze with it.
     */
    public static void forget(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return;
        for (Hold hold : List.copyOf(HOLDS.values()))
            if (hold.target() == entity || hold.caster() == entity) finish(hold);
    }

    /** Number of live holds. Bounded by concurrently frozen targets, exposed so a leak is visible. */
    public static int holdCount() {
        return HOLDS.size();
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { forget(event.getEntity()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void clonePlayer(PlayerEvent.Clone event) { forget(event.getOriginal()); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Hold hold : List.copyOf(HOLDS.values())) finish(hold);
    }
}
