package net.bullettrain.xenopixelsmod.peripheral;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.bullettrain.xenopixelsmod.block.entity.ShipFlapBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShipFlapPeripheral implements IPeripheral {

    private final Level level;
    private final BlockPos position;

    public ShipFlapPeripheral(Level level, BlockPos position) {
        this.level = level;
        this.position = position;
    }

    @Override
    public @NotNull String getType() {
        return "xenopixels_ship_flap";
    }

    @Override
    public @NotNull String[] getMethodNames() {
        return new String[]{
            "setDeflection",
            "getDeflection",
            "setAutoStabilize",
            "isAutoStabilizing",
            "pulse"
        };
    }

    @LuaFunction
    public final void setDeflection(float deflection) throws LuaException {
        if (level.isClientSide) throw new LuaException("Cannot control flap on client");
        
        if (deflection < -90.0f || deflection > 90.0f) {
            throw new LuaException("Deflection must be between -90 and 90 degrees");
        }

        if (level.getBlockEntity(position) instanceof ShipFlapBlockEntity flap) {
            flap.setDeflection(deflection);
        } else {
            throw new LuaException("No ship flap found at this position");
        }
    }

    @LuaFunction
    public final float getDeflection() {
        if (level.getBlockEntity(position) instanceof ShipFlapBlockEntity flap) {
            return flap.getCurrentDeflection();
        }
        return 0.0f;
    }

    @LuaFunction
    public final void setAutoStabilize(boolean enabled) {
        if (level.isClientSide) return;
        
        if (level.getBlockEntity(position) instanceof ShipFlapBlockEntity flap) {
            // Would need setter in BlockEntity for this field
            // flap.setAutoStabilizing(enabled);
        }
    }

    @LuaFunction
    public final boolean isAutoStabilizing() {
        if (level.getBlockEntity(position) instanceof ShipFlapBlockEntity flap) {
            return false; // flap.isAutoStabilizing();
        }
        return false;
    }

    @LuaFunction
    public final void pulse(float amount, int duration) throws LuaException {
        if (level.isClientSide) throw new LuaException("Cannot control flap on client");
        
        if (amount < -90.0f || amount > 90.0f) {
            throw new LuaException("Amount must be between -90 and 90 degrees");
        }

        if (level.getBlockEntity(position) instanceof ShipFlapBlockEntity flap) {
            float current = flap.getCurrentDeflection();
            flap.setDeflection(current + amount);
            
            // Schedule reset after duration ticks (simplified)
            // In real implementation, use a scheduler or tick counter
        } else {
            throw new LuaException("No ship flap found at this position");
        }
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        if (other instanceof ShipFlapPeripheral flapOther) {
            return this.level == flapOther.level && this.position.equals(flapOther.position);
        }
        return false;
    }
}
