package net.bullettrain.xenopixelsmod.client.aero;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.bullettrain.xenopixelsmod.client.guidance.GuidanceV2Client;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renders the flight controller rig.
 *
 * <p>GeckoLib's default {@code rotateBlock} pitches UP/DOWN around the block floor
 * ({@code translate(0.5, 0, 0.5)}), which swings the console — and its small screen —
 * out of the cell on vertical facings. This subclass pitches around the block centre
 * so the screen stays on the facing side. Stock and fork computers share this renderer.
 */
public class FlightControllerRenderer extends GeoBlockRenderer<ShipVlsGuidanceBlockEntity> {
    public FlightControllerRenderer() {
        super(new FlightControllerModel());
        // Stock glowmask is flight_controller_glowmask.png (GeckoLib: texture + "_glowmask").
        // Fork dest-name rule wants *_fork, so the fork mask is flight_controller_glowmask_fork.png
        // and this layer points AutoGlowing at that file instead of flight_controller_fork_glowmask.png.
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public ResourceLocation getTextureResource(ShipVlsGuidanceBlockEntity animatable) {
                if (FlightControllerModel.isForkComputer(animatable)) {
                    return FlightControllerModel.GLOW_FORK;
                }
                return super.getTextureResource(animatable);
            }

            @Override
            protected RenderType getRenderType(ShipVlsGuidanceBlockEntity animatable) {
                if (FlightControllerModel.isForkComputer(animatable)) {
                    return RenderType.eyes(FlightControllerModel.GLOW_FORK);
                }
                return super.getRenderType(animatable);
            }
        });
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        // preRender already moved to (0.5, 0, 0.5). Lift to the cell centre, rotate, drop
        // back — vertex order is the reverse of these calls.
        poseStack.translate(0.0, 0.5, 0.0);
        if (GuidanceV2Client.isV2()) {
            int z = FlightControllerDebugRotation.extraZ;
            int y = FlightControllerDebugRotation.extraY;
            int x = FlightControllerDebugRotation.extraX;
            if (z != 0) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(z));
            }
            if (y != 0) {
                poseStack.mulPose(Axis.YP.rotationDegrees(y));
            }
            if (x != 0) {
                poseStack.mulPose(Axis.XP.rotationDegrees(x));
            }
        }
        super.rotateBlock(facing, poseStack);
        poseStack.translate(0.0, -0.5, 0.0);
    }
}
