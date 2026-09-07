package net.bullettrain.xenopixelsmod.client.flight;

import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;

/**
 * The conflict context every cockpit binding uses: active only while the local player is
 * actually riding a {@link XenoPilotSeatEntity}.
 *
 * <p><b>Why this exists.</b> The seat bindings deliberately sit on keys a pilot expects — Q/E
 * for roll, G for flaps, middle mouse to lock — and several of those are already spoken for by
 * this mod's own ground-combat bindings (charge fist on left click, charge kick on middle mouse,
 * sparking on Y). With every binding in {@link net.neoforged.neoforge.client.settings.KeyConflictContext#IN_GAME}
 * those pairs are live at the same time, so one key press fires both actions and the Controls
 * screen flags them as conflicting even though the two can never sensibly be used together.
 *
 * <p>Scoping the cockpit bindings to "seated" makes them genuinely inert on foot:
 * {@link net.minecraft.client.KeyMapping#isDown()} consults the context, so a held key reports
 * released outside the seat. Queued clicks are <i>not</i> context-filtered by
 * {@link net.minecraft.client.KeyMapping#consumeClick()}, which is why the input classes still
 * drain their own mappings every tick they are not flying — a press made on foot must not be
 * banked and replayed the moment the player sits down.
 *
 * <p>{@link #conflicts(IKeyConflictContext)} matches only other seat contexts, so a cockpit
 * binding is never reported as conflicting with an on-foot one. Bindings that share a key
 * <i>within</i> the cockpit still conflict with each other, which is the case worth warning
 * about.
 */
public final class XenoSeatKeyContext implements IKeyConflictContext {

    public static final XenoSeatKeyContext INSTANCE = new XenoSeatKeyContext();

    private XenoSeatKeyContext() {
    }

    @Override
    public boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() instanceof XenoPilotSeatEntity;
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return other instanceof XenoSeatKeyContext;
    }
}
