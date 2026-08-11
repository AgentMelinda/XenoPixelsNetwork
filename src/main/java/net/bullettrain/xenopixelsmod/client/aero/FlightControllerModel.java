package net.bullettrain.xenopixelsmod.client.aero;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Asset bindings for the flight controller rig. */
public class FlightControllerModel extends GeoModel<ShipVlsGuidanceBlockEntity> {
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "geo/flight_controller.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "textures/block/flight_controller.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "animations/flight_controller.animation.json");

    @Override
    public ResourceLocation getModelResource(ShipVlsGuidanceBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ShipVlsGuidanceBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ShipVlsGuidanceBlockEntity animatable) {
        return ANIMATION;
    }
}
