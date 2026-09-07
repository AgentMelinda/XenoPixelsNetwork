package net.bullettrain.xenopixelsmod.block.custom;

import net.minecraft.util.StringRepresentable;

/**
 * What a {@link WingPanelBlock} visually responds to. Assigned per-panel with the panel
 * configurator item; see {@link net.bullettrain.xenopixelsmod.item.PanelConfiguratorItem}.
 *
 * <p>This is purely which control input the panel's deflection angle is driven from —
 * it does not change the panel's real aerodynamic contribution, which is Sable's own per-block
 * lift/drag pass and is identical for every role. A hull with panels assigned realistic roles
 * simply <i>looks</i> like it is flying the way it is being flown.
 */
public enum PanelRole implements StringRepresentable {
    /** Does not move. The default for a newly placed panel. */
    NONE("none"),
    /** Deflects with the controller's flap setting, like the original flap behavior. */
    FLAP("flap"),
    /** Deflects with commanded pitch (an elevator). */
    PITCH("pitch"),
    /** Deflects with commanded roll (an aileron). */
    ROLL("roll"),
    /** Deflects with commanded yaw (a rudder). */
    YAW("yaw"),
    /** Deflects while the air brake is held. */
    BRAKE("brake");

    private final String id;

    PanelRole(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public PanelRole next() {
        PanelRole[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
