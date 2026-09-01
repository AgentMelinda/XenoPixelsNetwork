package net.bullettrain.xenopixelsmod.aero.seat;

import net.bullettrain.xenopixelsmod.aero.AeroAction;
import net.bullettrain.xenopixelsmod.aero.AeroActionDispatcher;
import net.bullettrain.xenopixelsmod.aero.AeroBus;
import net.bullettrain.xenopixelsmod.aero.AeroControlHost;
import net.bullettrain.xenopixelsmod.aero.AeroLinkManager;
import net.bullettrain.xenopixelsmod.aero.AeroStateSnapshot;
import net.bullettrain.xenopixelsmod.aero.AeroSubsystem;
import net.bullettrain.xenopixelsmod.aero.ControllerMode;
import net.bullettrain.xenopixelsmod.block.custom.PilotSeatBlock;
import net.bullettrain.xenopixelsmod.block.entity.PilotSeatBlockEntity;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.AeroStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The thing a pilot actually sits on.
 *
 * <p>It is deliberately almost inert: no physics, no collision, no gravity, no AI. Its whole job
 * is to be a mount whose rider is recognisably "the pilot of that controller", and to forward
 * that pilot's control frames to {@link AeroActionDispatcher} — the same choke point the GUI,
 * the physical panel and the ComputerCraft peripheral all go through. It never touches a physics
 * body and never moves a ship itself.
 *
 * <p><b>Why an entity and not just a piloting flag.</b> Riding is what makes the player's body
 * move with the vessel, third-person and first-person cameras behave, and other players see who
 * is flying. Sable carries and rotates entities on moving sub-levels, so the seat needs no
 * transform code of its own.
 *
 * <p><b>The seat must be tagged {@code sable:retain_in_sub_level}</b> — see
 * {@code data/sable/tags/entity_type/}. Sable treats any entity type outside that tag as
 * something to eject from a sub-level, so without the tag the seat is pushed out into world
 * space: it stops moving with the hull and the rider is drawn world-upright no matter how the
 * ship is banked or inverted. This is the same integration Create's own seat uses, which is why
 * {@code create:seat} appears in Sable's copy of the tag. The seat is also tagged
 * {@code sable:destroy_with_sub_level} so it does not outlive the craft it belongs to.
 *
 * <p>Because the seat lives in the sub-level's own coordinate space, its rotation is stored
 * hull-relative (the seat block's facing) and Sable applies the hull's orientation at render
 * time. Do not add world-space rotation here — it would be applied twice.
 *
 * <p><b>Safety.</b> The seat commands idle whenever its input goes stale (see
 * {@link AeroSeatInput#STALE_TICKS}) or its rider leaves, so a pilot who disconnects mid-throttle
 * does not leave a ship climbing forever.
 */
public class XenoPilotSeatEntity extends Entity {

    /** How often the pilot is sent the authoritative controller snapshot for their HUD. */
    private static final int STATE_PUSH_INTERVAL = 4;
    /** Ticks with no rider before the seat cleans itself up. */
    private static final int EMPTY_GRACE_TICKS = 20;
    /** How far from the seat block a flight controller may be and still be flyable from it. */
    private static final int CONTROLLER_SEARCH_RADIUS = 8;

    private @Nullable BlockPos seatBlock;
    private @Nullable BlockPos controllerPos;

    private final AeroSeatInput input = new AeroSeatInput();
    private int emptyTicks;
    private int statePushTicks;
    /** Set while the seat is holding the controller at idle, so it stops re-sending it. */
    private boolean idleCommanded;
    /**
     * Set when this seat itself was the one that engaged a bound controller's flight subsystem
     * on mount, so it knows to disengage it again on dismount. An operator's own engagement
     * (from the GUI, mid-autopilot) must never be touched by someone merely sitting down or
     * getting up — only what this seat turned on, this seat turns back off.
     */
    private boolean engagedByThisSeat;

    public XenoPilotSeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void bind(BlockPos seatBlock, @Nullable BlockPos controllerPos) {
        this.seatBlock = seatBlock == null ? null : seatBlock.immutable();
        this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
    }

    public @Nullable BlockPos controllerPos() {
        return controllerPos;
    }

    public AeroSeatInput input() {
        return input;
    }

    /**
     * Whoever this seat is flying: the linked flight controller if there is one, otherwise the
     * seat block itself, which is a control host in its own right.
     *
     * <p>Resolved fresh each time rather than cached. On a ship the controller can be broken,
     * replaced or unloaded underneath us, and a stale reference would be a reference to a block
     * entity that is no longer part of the world.
     */
    public @Nullable AeroControlHost resolveHost() {
        if (level().isClientSide) return null;
        if (controllerPos != null && level().isLoaded(controllerPos)
                && level().getBlockEntity(controllerPos) instanceof ShipVlsGuidanceBlockEntity console) {
            return console;
        }
        return standaloneHost();
    }

    /** The seat block's own control host, used when there is no console to fly. */
    private @Nullable PilotSeatBlockEntity standaloneHost() {
        if (seatBlock == null || level().isClientSide || !level().isLoaded(seatBlock)) return null;
        BlockEntity be = level().getBlockEntity(seatBlock);
        return be instanceof PilotSeatBlockEntity seat ? seat : null;
    }

    /**
     * Nearest flight controller to a seat block, or null when there is none in range.
     *
     * <p>Only a real console counts. The seat block is itself a control host, so accepting any
     * host would match the seat under the pilot and no seat would ever be considered linked.
     */
    public static @Nullable BlockPos findController(Level level, BlockPos seatBlock) {
        BlockPos[] best = new BlockPos[1];
        double[] bestDistance = {Double.MAX_VALUE};
        AeroLinkManager.forEachNearbyBlockEntity(level, seatBlock, CONTROLLER_SEARCH_RADIUS,
                (pos, blockEntity) -> {
                    if (!(blockEntity instanceof ShipVlsGuidanceBlockEntity)) return;
                    double distance = pos.distSqr(seatBlock);
                    if (distance < bestDistance[0]) {
                        bestDistance[0] = distance;
                        best[0] = pos.immutable();
                    }
                });
        return best[0];
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        // The seat exists only for its block. If that is gone, so is the seat.
        if (seatBlock != null && !(level().getBlockState(seatBlock).getBlock() instanceof PilotSeatBlock)) {
            ejectPassengers();
            discard();
            return;
        }

        LivingEntity pilot = getControllingPassenger();
        if (pilot == null) {
            if (++emptyTicks > EMPTY_GRACE_TICKS) {
                commandIdle();
                discard();
            }
            return;
        }
        emptyTicks = 0;

        input.tick();
        AeroControlHost host = resolveHost();
        if (host == null) return;

        if (input.isStale()) {
            commandIdle(host);
        } else {
            applyInput(host, pilot instanceof ServerPlayer serverPilot ? serverPilot : null);
        }

        if (pilot instanceof ServerPlayer serverPilot && ++statePushTicks >= STATE_PUSH_INTERVAL) {
            statePushTicks = 0;
            // Addressed by the host's own position, not the link: a standalone seat has no
            // controller position to name, and the HUD only needs to know whose state this is.
            ModNetwork.sendToPlayer(serverPilot,
                    new AeroStatePacket(AeroStateSnapshot.of(host.hostPos(), host.aeroBus())));
        }
    }

    /**
     * Hand the pilot's control frame to the dispatcher, one action per axis group.
     *
     * <p>{@code dispatchQuiet} is used because this runs every tick a pilot is flying; the seat
     * pushes the authoritative snapshot back on its own slower cadence instead of making each
     * axis trigger a block update.
     */
    private void applyInput(AeroControlHost host, @Nullable ServerPlayer actor) {
        idleCommanded = false;
        AeroActionDispatcher.dispatchQuiet(host,
                new AeroAction.SetAttitude(input.yawDeg(), input.pitchDeg(), input.rollDeg(),
                        input.pitchStick(), input.rollStick(), input.yawStick(), input.mouseAim()), actor);
        AeroActionDispatcher.dispatchQuiet(host,
                new AeroAction.SetThrottle(input.airBrake() ? 0.0 : input.throttle()), actor);
        AeroActionDispatcher.dispatchQuiet(host, new AeroAction.SetAirBrake(input.airBrake()), actor);
        // Only when the pilot actually moved the control: setting flaps by hand cancels
        // auto-flap, so a per-tick push would make auto-flap impossible to keep on.
        if (input.flapCommanded()) {
            AeroActionDispatcher.dispatchQuiet(host, new AeroAction.SetFlap(input.flap()), actor);
        }
    }

    private void commandIdle() {
        AeroControlHost host = resolveHost();
        if (host != null) commandIdle(host);
    }

    /** Cut thrust exactly once when the pilot stops flying, then leave the controller alone. */
    private void commandIdle(AeroControlHost host) {
        if (idleCommanded) return;
        idleCommanded = true;
        input.release();
        AeroActionDispatcher.dispatch(host, new AeroAction.SetThrottle(0.0), null);
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (level().isClientSide) return;
        if (controllerPos == null) {
            // A seat flying itself only comes alive with a pilot on it.
            PilotSeatBlockEntity host = standaloneHost();
            if (host != null) host.onPilotMounted();
        } else {
            engageBoundController();
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                SoundSource.BLOCKS, 0.55f, 1.35f);
        if (passenger instanceof ServerPlayer pilot) reportMountState(pilot);
    }

    /**
     * Tell the pilot what they just sat down in.
     *
     * <p>Without this the seat is silent about everything that decides whether the controls will
     * do anything: whether it found a console or is flying itself, whether that console is in
     * missile mode (in which case the dispatcher refuses every control frame — correctly, but
     * invisibly), and how many engines are actually paired. A pilot pressing W on a seat with no
     * engines and a seat that is fundamentally broken look identical, which is most of why "the
     * thruster doesn't connect to the chair" was so hard to pin down. The mode is reported, never
     * silently changed: switching a shared console out of missile mode is an operator's call.
     */
    private void reportMountState(ServerPlayer pilot) {
        AeroControlHost host = resolveHost();
        if (host == null) {
            pilot.displayClientMessage(Component.literal(
                    "\u00a7cNo control host — this seat is not on a flight controller or a powered hull"), true);
            return;
        }
        int engines = host.getPairedThrusters().size();
        String where = controllerPos == null ? "standalone seat"
                : "controller " + controllerPos.getX() + ", " + controllerPos.getY() + ", " + controllerPos.getZ();
        if (host.aeroBus().mode() != ControllerMode.FLIGHT) {
            pilot.displayClientMessage(Component.literal(
                    "\u00a7e" + where + " is in MISSILE mode — switch it to flight mode to fly from here"), true);
            return;
        }
        String engineText = engines == 0
                ? "\u00a7cno engines paired — link one with the ship target tool"
                : "\u00a7f" + engines + " engine" + (engines == 1 ? "" : "s");
        pilot.displayClientMessage(Component.literal(
                "\u00a7bFlying \u00a7f" + where + " \u00a77| " + engineText
                        + " \u00a77| sneak to exit"), true);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (level().isClientSide) return;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                SoundSource.BLOCKS, 0.45f, 0.95f);
        commandIdle();
        // Leaving the cockpit must not leave a stale combat lock tracking whatever this pilot
        // was aiming at.
        if (passenger instanceof ServerPlayer serverPilot) {
            net.bullettrain.xenopixelsmod.combat.targeting.TargetLockManager.clearForSeatExit(serverPilot);
        }
        if (controllerPos == null) {
            PilotSeatBlockEntity host = standaloneHost();
            if (host != null) host.onPilotDismounted();
        } else if (engagedByThisSeat) {
            disengageBoundController();
        }
    }

    /**
     * Get a bound controller ready to fly, without ever deciding anything the operator did not
     * already decide.
     *
     * <p>A controller's {@link ControllerMode} is left alone entirely — switching a bound
     * console from missile to flight mode is an operator's call, not something sitting down
     * should do silently. Within flight mode, the seat is allowed to (a) claim nearby engines if
     * none are paired yet, exactly as the GUI's own "pair nearby" button would, and (b) engage
     * the flight subsystem if it is not already engaged, remembering that it did so.
     */
    private void engageBoundController() {
        engagedByThisSeat = false;
        AeroControlHost host = resolveHost();
        if (host == null) return;
        AeroBus bus = host.aeroBus();
        if (bus.mode() != ControllerMode.FLIGHT) return;
        if (host.getPairedThrusters().isEmpty()) host.pairNearbyThrusters();
        if (!bus.isFlightEngaged()) {
            AeroActionDispatcher.Result result = AeroActionDispatcher.dispatch(host,
                    new AeroAction.ToggleSubsystem(AeroSubsystem.FLIGHT, true), null);
            engagedByThisSeat = result.accepted();
        }
    }

    /** Undo exactly what {@link #engageBoundController} did, and only if it did it. */
    private void disengageBoundController() {
        engagedByThisSeat = false;
        AeroControlHost host = resolveHost();
        if (host == null) return;
        AeroActionDispatcher.dispatch(host,
                new AeroAction.ToggleSubsystem(AeroSubsystem.FLIGHT, false), null);
    }

    /**
     * Explicitly bind this seat to the nearest flight controller, or drop back to standalone.
     *
     * <p>Mounting already resolves a link automatically, but only once, at sit-down — a seat
     * mounted before a controller existed nearby stays standalone until the pilot gets up and
     * sits again. This lets an already-seated pilot pick up a controller that just appeared, or
     * shed one that broke, going through exactly the same engage/disengage path a fresh mount
     * would take.
     */
    public void toggleBinding(ServerPlayer requester) {
        if (level().isClientSide || seatBlock == null) return;

        if (controllerPos != null) {
            // Bound -> standalone.
            if (engagedByThisSeat) disengageBoundController();
            controllerPos = null;
            PilotSeatBlockEntity host = standaloneHost();
            if (host != null) host.onPilotMounted();
            reportMountState(requester);
            return;
        }

        // Standalone -> bound, only if a controller is actually in range.
        BlockPos found = findController(level(), seatBlock);
        if (found == null) {
            requester.displayClientMessage(Component.literal("§cNo flight controller nearby"), true);
            return;
        }
        PilotSeatBlockEntity standalone = standaloneHost();
        if (standalone != null) standalone.onPilotDismounted();
        controllerPos = found;
        engageBoundController();
        reportMountState(requester);
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof LivingEntity living ? living : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        // Sit exactly where the seat is: the block model, not an offset, decides how it looks.
        return Vec3.ZERO;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Nothing: the pilot's HUD data arrives as an authoritative controller snapshot instead.
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        seatBlock = tag.contains("SeatBlock") ? BlockPos.of(tag.getLong("SeatBlock")) : null;
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (seatBlock != null) tag.putLong("SeatBlock", seatBlock.asLong());
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
    }
}
