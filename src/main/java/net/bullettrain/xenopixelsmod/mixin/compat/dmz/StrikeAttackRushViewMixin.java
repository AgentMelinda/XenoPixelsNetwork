package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import net.bullettrain.xenopixelsmod.combat.technique.XenoRushStrikeView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the attacker's view to their lock-on camera for the four Xeno rush strikes, when the
 * strike is thrown at the target they are locked on.
 *
 * <p>DragonMineZ aims a striking player from the server in three places, all of them a
 * {@code ServerPlayer.lookAt}, which snaps the client's camera: after the opening teleport
 * ({@code teleportToTargetFront}, {@code teleportToPartFront}) and on every tick of the strike
 * ({@code faceStrikeTarget}). For a Xeno rush strike at the caster's lock those three are
 * replaced by {@link XenoRushStrikeView}: an arrival that does not need the view turned, and a
 * facing that only turns the body other players see. Why is told there.
 *
 * <p>{@code requestStrike} is only read, never changed: its second argument is what
 * DragonMineZ's client sends with every strike, the id of the entity it is locked on or -1, and
 * it is how the server knows whether there is a lock-on camera to leave the view to.
 *
 * <p>Scope is the four Xeno ids thrown at a lock, and nothing else. For any other strike, and
 * for a rush strike thrown with nothing locked, each handler returns without acting and
 * DragonMineZ's own code runs untouched. These are Xeno's own entries in DragonMineZ's strike
 * registry, so this changes how Xeno's moves are staged, not how DragonMineZ's are.
 *
 * <p>Signatures checked with {@code javap -p -s} against {@code dragonminez-2.1.3.jar}
 * (sha256 {@code 5a6e33ef...d203b581}):
 * <pre>
 * public  static void requestStrike(ServerPlayer, int)
 * private static void teleportToTargetFront(ServerPlayer, LivingEntity, boolean)
 * private static void teleportToPartFront(ServerPlayer, PartEntity, LivingEntity, boolean)
 * private static void faceStrikeTarget(ServerPlayer, LivingEntity)
 * </pre>
 *
 * <p>{@code require = 0} throughout: if a later DragonMineZ renames one of these, that handler
 * quietly stops applying and the strike is aimed the stock way again, rather than the mod
 * refusing to load.
 */
@Mixin(targets = "com.dragonminez.server.events.players.combat.StrikeAttackHandler", remap = false)
public abstract class StrikeAttackRushViewMixin {

    @Inject(method = "requestStrike", at = @At("HEAD"), require = 0)
    private static void xenopixels$noteLockAtCast(ServerPlayer player, int lockedTargetId, CallbackInfo ci) {
        if (player != null && !player.level().isClientSide) {
            XenoRushStrikeView.noteCast(player, lockedTargetId);
        }
    }

    @Inject(method = "teleportToTargetFront", at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$rushArrival(ServerPlayer player, LivingEntity target,
                                               boolean faceTarget, CallbackInfo ci) {
        if (player == null || target == null) return;
        if (XenoRushStrikeView.selectedIsRush(player) && XenoRushStrikeView.arrive(player, target)) {
            ci.cancel();
        }
    }

    @Inject(method = "teleportToPartFront", at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$rushArrivalAtPart(ServerPlayer player, PartEntity<?> part,
                                                     LivingEntity parent, boolean faceTarget,
                                                     CallbackInfo ci) {
        if (player == null || part == null || parent == null) return;
        if (XenoRushStrikeView.selectedIsRush(player)
                && XenoRushStrikeView.arriveAtPart(player, part, parent)) {
            ci.cancel();
        }
    }

    @Inject(method = "faceStrikeTarget", at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$rushFacing(ServerPlayer player, LivingEntity target, CallbackInfo ci) {
        if (player == null || target == null) return;
        if (XenoRushStrikeView.face(player, target)) ci.cancel();
    }
}
