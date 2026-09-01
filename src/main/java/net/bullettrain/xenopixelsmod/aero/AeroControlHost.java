package net.bullettrain.xenopixelsmod.aero;

import net.bullettrain.xenopixelsmod.missile.BallisticFlightPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Whatever owns an {@link AeroBus} and can act on the intents the dispatcher accepts.
 *
 * <p>This exists so {@link AeroActionDispatcher} — the single validation choke point every
 * control surface funnels through — is not tied to one block entity. The flight-controller
 * block is the original host; the pilot seat is the second one. Widening the dispatcher's
 * parameter type is deliberately the <i>only</i> change that required: validation, clamping and
 * power gating stay in one place and neither host gets to reimplement them.
 *
 * <p>Every method here is server-side. Implementations must tolerate being asked about a level
 * that is gone or a ship that has unloaded, because a host can outlive both.
 */
public interface AeroControlHost {

    /** The authoritative state this host owns. Only the dispatcher may write to it. */
    AeroBus aeroBus();

    /** Null on a host that has been removed from the world. */
    @Nullable Level getLevel();

    /** Where this host sits, used for reach checks and as the thruster ownership key. */
    BlockPos hostPos();

    /** Mark persistent state dirty. */
    void setChanged();

    /** Push controller state to tracking clients. */
    void syncAero();

    /** True while a launch is in progress; mode changes are refused during one. */
    boolean isCommandingFlight();

    /** Engines this host commands. */
    List<BlockPos> getPairedThrusters();

    /** @return number of engines newly paired */
    int pairNearbyThrusters();

    /** @return number of engines released */
    int clearPairedThrusters();

    /** Link exactly one engine, the ship target tool's explicit per-block linker. */
    boolean pairOneThruster(BlockPos pos);

    /** Unlink exactly one engine. */
    boolean unpairOneThruster(BlockPos pos);

    /** Re-survey paired engines and publish their health onto the bus. */
    void refreshAeroLinks();

    /** Autopilot destination, or null if none is set. */
    @Nullable BlockPos getTarget();

    /** Route waypoints for {@link AeroAutopilotMode#ROUTE}. */
    BallisticFlightPlan.Settings getPlannerSettings();

    /**
     * Wing panels explicitly linked to this host via the ship target tool
     * ({@code net.bullettrain.xenopixelsmod.item.custom.TargetToolItem}).
     *
     * <p>Only linked panels may have their role changed with the panel configurator from this
     * host's seat — a panel a hull happens to have somewhere is not automatically this chair's
     * to reconfigure. Linking is a bookkeeping/authority concept only; a panel's actual
     * aerodynamic contribution (Sable's own per-block lift/drag pass) is identical whether or
     * not it is linked to anything.
     */
    Set<BlockPos> getLinkedPanels();

    /** @return true if the panel was newly linked (false if it was already linked) */
    boolean linkPanel(BlockPos pos);

    /** @return true if the panel was linked and is now unlinked */
    boolean unlinkPanel(BlockPos pos);
}
