package net.bullettrain.xenopixelsmod.client.compat.xaero;

import net.bullettrain.xenopixelsmod.client.gui.FlightPlannerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.WorldMapSession;
import xaero.map.gui.GuiMap;
import xaero.map.mods.SupportMods;
import xaero.map.mods.gui.Waypoint;

import java.lang.ref.WeakReference;

/**
 * Optional client-only bridge to Xaero's managed minimap waypoints shown by World Map.
 *
 * <p>Lives under {@code client} rather than beside the other {@code compat} packages because it
 * touches {@code Minecraft.getInstance()} and Xaero's GUI types. Its position in the tree is the
 * guard: a dedicated server must never classload this, and keeping it here means a common-side
 * call is visibly wrong at the import rather than only discovered as a {@code NoClassDefFoundError}
 * on a server.
 */
public final class XaeroWaypointPicker {
    private static WeakReference<FlightPlannerScreen> pending = new WeakReference<>(null);

    private XaeroWaypointPicker() {
    }

    public static void open(FlightPlannerScreen planner) {
        Minecraft minecraft = Minecraft.getInstance();
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable() || session.getMapProcessor() == null
                || minecraft.getCameraEntity() == null) {
            planner.rejectXaeroWaypoint("Xaero World Map is not ready");
            return;
        }
        // Xaero World Map renders and manages waypoints through its official
        // Xaero Minimap compatibility bridge. Without it there is no waypoint
        // manager to select from, so do not fall back to an arbitrary map click.
        if (!SupportMods.minimap()) {
            planner.rejectXaeroWaypoint("Xaero Minimap waypoint manager is not available");
            return;
        }
        pending = new WeakReference<>(planner);
        minecraft.setScreen(new GuiMap(planner, planner, session.getMapProcessor(),
                minecraft.getCameraEntity()));
    }

    public static boolean isPicking() {
        return pending.get() != null;
    }

    public static void accept(Waypoint waypoint, ResourceKey<Level> dimension) {
        FlightPlannerScreen planner = pending.get();
        pending.clear();
        if (planner == null || waypoint == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && dimension != null
                && !minecraft.level.dimension().equals(dimension)) {
            planner.rejectXaeroWaypoint("Select a Xaero waypoint in the current dimension");
        } else {
            planner.acceptXaeroWaypoint(waypoint.getX(), waypoint.getY(), waypoint.getZ(),
                    waypoint.isyIncluded(), waypoint.getName());
        }
        minecraft.setScreen(planner);
    }

    public static void cancel() {
        FlightPlannerScreen planner = pending.get();
        pending.clear();
        if (planner != null) {
            planner.rejectXaeroWaypoint("Xaero waypoint selection cancelled");
        }
    }
}
