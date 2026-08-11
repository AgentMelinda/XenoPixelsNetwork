package net.bullettrain.xenopixelsmod.client.aero;

import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renders the flight controller rig.
 *
 * <p>Deliberately thin: everything animated is decided by
 * {@link ShipVlsGuidanceBlockEntity#aeroAnimState()} from synced server state, so there is no
 * renderer-local animation logic to drift out of sync after a patch.
 */
public class FlightControllerRenderer extends GeoBlockRenderer<ShipVlsGuidanceBlockEntity> {
    public FlightControllerRenderer() {
        super(new FlightControllerModel());
        // Emissive pass. GeckoLib resolves flight_controller_glowmask.png by naming
        // convention; only the screen, indicator buttons and console lip are opaque in that
        // mask, so the hull stays correctly unlit.
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
