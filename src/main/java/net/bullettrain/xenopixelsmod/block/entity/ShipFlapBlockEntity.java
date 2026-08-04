package net.bullettrain.xenopixelsmod.block.entity;

import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetwork;
import net.bullettrain.xenopixelsmod.init.XenoBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.mod.common.entity.ship.ShipWrapper;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.joml.Vector3d;

public class ShipFlapBlockEntity extends BlockEntity {

    private final LinkBehaviour linkBehaviour;
    private float targetDeflection = 0.0f;
    private float currentDeflection = 0.0f;
    private boolean isAutoStabilizing = false;
    private int updateTimer = 0;

    public ShipFlapBlockEntity(BlockPos pos, BlockState state) {
        super(XenoBlockEntities.SHIP_FLAP.get(), pos, state);
        this.linkBehaviour = LinkBehaviour.pairConnected(this, this::onRedstoneUpdate);
    }

    private void onRedstoneUpdate(RedstoneLinkNetwork network, boolean receivedPower) {
        // Receive deflection value via redstone link frequency
        // In a real implementation, we might encode the float into the signal or use a specific channel
        // For now, power = extend flaps (airbrake), no power = retract
        this.targetDeflection = receivedPower ? 45.0f : 0.0f;
        setChanged();
    }

    @Override
    public void tick() {
        if (level == null || level.isClientSide) return;

        updateTimer++;
        if (updateTimer % 5 == 0) {
            // Smooth interpolation
            if (Math.abs(currentDeflection - targetDeflection) > 0.1f) {
                currentDeflection += Math.signum(targetDeflection - currentDeflection) * 2.0f;
            } else {
                currentDeflection = targetDeflection;
            }

            // Apply to VS2 Ship Physics if attached
            applyToShip();
        }
    }

    private void applyToShip() {
        ShipWrapper ship = org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectManagingPos(level, worldPosition);
        if (ship != null) {
            // Convert block position to ship local coordinates
            Vector3d localPos = VectorConversionsMCKt.toJOMLD(getBlockPos().subtract(ship.getShipData().getCenterOfMass()));
            
            // Apply torque based on flap deflection
            // Simple model: Deflection creates drag/lift force on that side of the ship
            float torqueStrength = currentDeflection / 45.0f;
            
            // Yaw control (rudder)
            if (isAutoStabilizing) {
                // Auto-level logic would go here reading ship velocity
                torqueStrength *= 0.5f; 
            }

            // Apply angular impulse to the ship physics
            // Note: Direct manipulation requires VS2 API access to the physics body
            // This is a placeholder for the actual VS2 force application
            ship.getVelocity().mul(1.0f - (Math.abs(torqueStrength) * 0.01f)); // Simple drag simulation
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("TargetDeflection", targetDeflection);
        tag.putFloat("CurrentDeflection", currentDeflection);
        tag.putBoolean("AutoStabilize", isAutoStabilizing);
        linkBehaviour.write(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        targetDeflection = tag.getFloat("TargetDeflection");
        currentDeflection = tag.getFloat("CurrentDeflection");
        isAutoStabilizing = tag.getBoolean("AutoStabilize");
        linkBehaviour.read(tag);
    }

    public void setDeflection(float deflection) {
        this.targetDeflection = deflection;
        setChanged();
    }

    public float getCurrentDeflection() {
        return currentDeflection;
    }
}
