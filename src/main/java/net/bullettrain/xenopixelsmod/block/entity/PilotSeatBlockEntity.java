package net.bullettrain.xenopixelsmod.block.entity;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.aero.AeroAction;
import net.bullettrain.xenopixelsmod.aero.AeroActionDispatcher;
import net.bullettrain.xenopixelsmod.aero.AeroBus;
import net.bullettrain.xenopixelsmod.aero.AeroControlHost;
import net.bullettrain.xenopixelsmod.aero.AeroLinkManager;
import net.bullettrain.xenopixelsmod.aero.AeroPowerBudget;
import net.bullettrain.xenopixelsmod.aero.AeroSubsystem;
import net.bullettrain.xenopixelsmod.aero.ControllerMode;
import net.bullettrain.xenopixelsmod.aero.control.AeroFlightCore;
import net.bullettrain.xenopixelsmod.aero.control.VectorMixer;
import net.bullettrain.xenopixelsmod.aero.power.AeroEnergyStorage;
import net.bullettrain.xenopixelsmod.block.custom.PilotSeatBlock;
import net.bullettrain.xenopixelsmod.missile.BallisticFlightPlan;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A pilot seat that can fly a ship on its own.
 *
 * <p>A seat next to a flight controller simply operates that controller. This block entity is
 * the other case: a small craft with a seat, some engines and no console. It is a second
 * {@link AeroControlHost}, so it owns a bus, a power buffer and its own engine pairings, and the
 * pilot's input reaches it through the same dispatcher with the same validation.
 *
 * <p><b>What it deliberately cannot do:</b> autopilot, routes and targets. Those belong to the
 * flight controller, which has the planner, the waypoints and the missile stack behind them. A
 * standalone seat is manual flight only — the setpoint always comes from a live pilot — which is
 * also why it engages when someone sits down and disengages when they leave. An unattended seat
 * should not be flying.
 */
public class PilotSeatBlockEntity extends BlockEntity implements AeroControlHost {

    /** How far the seat looks for engines to claim. */
    private static final int ENGINE_SEARCH_RADIUS = 12;

    private final AeroBus aeroBus = new AeroBus();
    private final AeroEnergyStorage aeroEnergy = new AeroEnergyStorage();
    private final AeroFlightCore aeroCore = new AeroFlightCore();

    private final Set<BlockPos> pairedThrusters = new LinkedHashSet<>();
    private final Set<BlockPos> linkedPanels = new LinkedHashSet<>();
    private List<AeroLinkManager.Link> aeroLinks = new ArrayList<>();
    private int aeroLinkCooldown;
    private boolean aeroCommanding;

    private long cachedShipId = -1L;
    private int shipCacheCooldown;

    private final transient Vector3d bodyNose = new Vector3d();
    private final transient Vector3d bodyUp = new Vector3d(0.0, 1.0, 0.0);

    public PilotSeatBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PILOT_SEAT.get(), pos, state);
    }

    @Override
    public AeroBus aeroBus() {
        return aeroBus;
    }

    @Override
    public BlockPos hostPos() {
        return worldPosition;
    }

    /** Exposed for the {@code Capabilities.EnergyStorage.BLOCK} provider. */
    public AeroEnergyStorage aeroEnergy() {
        return aeroEnergy;
    }

    @Override
    public void syncAero() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public boolean isCommandingFlight() {
        return false;
    }

    @Override
    public List<BlockPos> getPairedThrusters() {
        return new ArrayList<>(pairedThrusters);
    }

    @Override
    public int pairNearbyThrusters() {
        int added = AeroLinkManager.pairNearby(level, worldPosition, ENGINE_SEARCH_RADIUS, pairedThrusters);
        setChanged();
        syncAero();
        return added;
    }

    @Override
    public int clearPairedThrusters() {
        int cleared = AeroLinkManager.clearPaired(level, worldPosition, pairedThrusters);
        setChanged();
        syncAero();
        return cleared;
    }

    @Override
    public boolean pairOneThruster(BlockPos pos) {
        boolean added = AeroLinkManager.pairOne(level, worldPosition, pos, pairedThrusters);
        if (added) {
            setChanged();
            // Without this, VectorMixer only sees the new thruster once the periodic 40-tick
            // refresh happens to land — up to 2 seconds of a freshly-linked engine producing no
            // thrust at all, which reads as "linking doesn't work" even though it did.
            refreshAeroLinks();
            syncAero();
        }
        return added;
    }

    @Override
    public boolean unpairOneThruster(BlockPos pos) {
        boolean removed = AeroLinkManager.unpairOne(level, worldPosition, pos, pairedThrusters);
        if (removed) {
            setChanged();
            refreshAeroLinks();
            syncAero();
        }
        return removed;
    }

    @Override
    public void refreshAeroLinks() {
        aeroLinks = AeroLinkManager.refresh(level, pairedThrusters, aeroBus);
    }

    @Override
    public @Nullable BlockPos getTarget() {
        // No target: a standalone seat has no autopilot to point at one.
        return null;
    }

    @Override
    public BallisticFlightPlan.Settings getPlannerSettings() {
        return BallisticFlightPlan.Settings.defaults();
    }

    @Override
    public Set<BlockPos> getLinkedPanels() {
        return linkedPanels;
    }

    @Override
    public boolean linkPanel(BlockPos pos) {
        boolean added = linkedPanels.add(pos.immutable());
        if (added) {
            setChanged();
            syncAero();
        }
        return added;
    }

    @Override
    public boolean unlinkPanel(BlockPos pos) {
        boolean removed = linkedPanels.remove(pos);
        if (removed) {
            setChanged();
            syncAero();
        }
        return removed;
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                                  PilotSeatBlockEntity seat) {
        if (level instanceof ServerLevel serverLevel) seat.tick(serverLevel);
    }

    private void tick(ServerLevel serverLevel) {
        // A seat's bus is always a flight bus; the missile mode a controller starts in is
        // meaningless here, and the dispatcher refuses flight commands outside FLIGHT mode.
        if (aeroBus.mode() != ControllerMode.FLIGHT) {
            AeroActionDispatcher.dispatch(this,
                    new AeroAction.SetMode(ControllerMode.FLIGHT), null);
        }

        AeroBus.PowerTier previousTier = aeroBus.powerTier();
        AeroPowerBudget.tick(aeroBus, aeroEnergy);
        if (aeroBus.powerTier() == AeroBus.PowerTier.OFFLINE
                && previousTier != AeroBus.PowerTier.OFFLINE) {
            AeroLinkManager.releaseAll(level, pairedThrusters);
            aeroBus.disengageRuntime("power lost — flight disengaged");
            setChanged();
            syncAero();
        }

        if (--aeroLinkCooldown <= 0) {
            aeroLinkCooldown = 40;
            refreshAeroLinks();
        }

        boolean active = aeroBus.isFlightEngaged()
                && aeroBus.powerTier() != AeroBus.PowerTier.OFFLINE
                && aeroBus.powerTier() != AeroBus.PowerTier.CRITICAL;

        ServerSubLevel ship = resolveShip(serverLevel, active);
        if (!active || ship == null) {
            if (aeroCommanding) {
                VectorMixer.shutdown(level, aeroLinks);
                AeroFlightCore.release(ship);
                aeroCommanding = false;
                if (ship == null && active) {
                    aeroBus.disengageRuntime("ship unavailable — flight disengaged");
                    syncAero();
                }
            }
            return;
        }
        aeroCommanding = true;

        Vector3dc worldVelocity = VsShipHelper.velocity(serverLevel, ship);
        Vector3dc nose = noseVector();
        boolean stalled = aeroCore.tick(aeroBus, ship, level, aeroLinks,
                nose, bodyUp, nose,
                aeroBus.yawDeg(), aeroBus.pitchDeg(), aeroBus.rollDeg(), aeroBus.throttle(),
                worldVelocity, 0.05, linkedPanels);
        aeroBus.reportAutopilot(0, 0, 0, worldVelocity.length(),
                stalled ? "seat flight — STALL" : "seat flight");
    }

    /**
     * Which way the craft points, in ship-local space.
     *
     * <p>The seat's own facing is the nose: you fly the way the pilot is sitting. That is both
     * the obvious reading for a builder and the only ship-local direction the seat actually has.
     */
    private Vector3dc noseVector() {
        Direction facing = getBlockState().hasProperty(PilotSeatBlock.FACING)
                ? getBlockState().getValue(PilotSeatBlock.FACING) : Direction.NORTH;
        return bodyNose.set(facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    /** Cached ship lookup; the scan is not cheap enough to run unconditionally every tick. */
    private @Nullable ServerSubLevel resolveShip(ServerLevel serverLevel, boolean force) {
        if (cachedShipId >= 0) {
            ServerSubLevel byId = VsShipHelper.getLoadedShipById(serverLevel, cachedShipId);
            if (byId != null) return byId;
            cachedShipId = -1L;
        }
        if (!force && --shipCacheCooldown > 0) return null;
        shipCacheCooldown = force ? 5 : 20;
        ServerSubLevel loaded = VsShipHelper.getLoadedShipAtFast(serverLevel, worldPosition);
        if (loaded == null) loaded = VsShipHelper.getLoadedShipAt(serverLevel, worldPosition);
        cachedShipId = loaded != null ? VsShipHelper.getShipId(loaded) : -1L;
        return loaded;
    }

    /** Called by the seat entity when a pilot sits down on a seat with no controller of its own. */
    public void onPilotMounted() {
        if (level == null || level.isClientSide) return;
        // A freshly placed seat may not have ticked yet, and the dispatcher refuses flight
        // commands outside flight mode — so establish the mode before engaging, not after.
        if (aeroBus.mode() != ControllerMode.FLIGHT) {
            AeroActionDispatcher.dispatch(this,
                    new AeroAction.SetMode(ControllerMode.FLIGHT), null);
        }
        if (pairedThrusters.isEmpty()) pairNearbyThrusters();
        AeroActionDispatcher.dispatch(this,
                new AeroAction.ToggleSubsystem(AeroSubsystem.FLIGHT, true),
                null);
    }

    /**
     * Called when the pilot leaves. An unattended standalone seat must not keep flying.
     *
     * <p>{@link AeroFlightCore#release} is called directly here rather than left for {@link #tick}
     * to notice {@code active=false} on its own next pass — an abrupt dismount (a forced teleport,
     * not a normal shift-key exit) leaves a one-tick window where this ship's continuous
     * force/torque systems could still write a force computed from now-stale, discontinuous
     * inputs into its rigid body. Sable deletes a sub-level outright if its physics pipeline ever
     * reports a NaN pose for it, so that window is not just a cosmetic stutter — closing it here,
     * synchronously, is what actually stops a teleport from being able to destroy the ship.
     */
    public void onPilotDismounted() {
        if (level == null || level.isClientSide) return;
        AeroActionDispatcher.dispatch(this,
                new AeroAction.EmergencyStop(), null);
        if (level instanceof ServerLevel serverLevel) {
            AeroFlightCore.release(resolveShip(serverLevel, true));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Aero", aeroBus.save());
        tag.putInt("AeroEnergy", aeroEnergy.getEnergyStored());
        ListTag list = new ListTag();
        for (BlockPos pos : pairedThrusters) list.add(LongTag.valueOf(pos.asLong()));
        tag.put("PairedThrusters", list);
        ListTag panelList = new ListTag();
        for (BlockPos pos : linkedPanels) panelList.add(LongTag.valueOf(pos.asLong()));
        tag.put("LinkedPanels", panelList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        aeroBus.load(tag.contains("Aero", Tag.TAG_COMPOUND) ? tag.getCompound("Aero") : null);
        aeroEnergy.setStored(tag.contains("AeroEnergy") ? tag.getInt("AeroEnergy") : 0);
        pairedThrusters.clear();
        if (tag.contains("PairedThrusters", Tag.TAG_LIST)) {
            ListTag list = tag.getList("PairedThrusters", Tag.TAG_LONG);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof LongTag entry) {
                    pairedThrusters.add(BlockPos.of(entry.getAsLong()));
                }
            }
        }
        linkedPanels.clear();
        if (tag.contains("LinkedPanels", Tag.TAG_LIST)) {
            ListTag panelList = tag.getList("LinkedPanels", Tag.TAG_LONG);
            for (int i = 0; i < panelList.size(); i++) {
                if (panelList.get(i) instanceof LongTag entry) {
                    linkedPanels.add(BlockPos.of(entry.getAsLong()));
                }
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
