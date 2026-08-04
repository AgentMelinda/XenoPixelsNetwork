package net.bullettrain.xenopixelsmod.block.entity;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.valkyrienskies.extension.control.VS2ControlSurfaceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ComputerCraft/Tweak Peripheral for VS2 Ship Control
 * Provides Lua API for controlling flaps, thrusters, and guidance systems
 */
public class TweakPeripheralBlockEntity extends BlockEntity implements IPeripheral {
    
    private float flapDeflection = 0.0f;
    private boolean autoStabilize = true;
    private int updateRate = 5; // ticks between updates
    
    public TweakPeripheralBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super(ModBlockEntities.TWEAK_PERIPHERAL.get(), pos, state);
    }
    
    @Override
    public @NotNull String getType() {
        return "xenopixels_tweak";
    }
    
    @Override
    public @NotNull String[] getMethodNames() {
        return new String[]{
            "setFlapDeflection",
            "getFlapDeflection",
            "setAutoStabilize",
            "isAutoStabilizing",
            "setUpdateRate",
            "getUpdateRate",
            "pingShip",
            "getShipStats",
            "emergencyStop"
        };
    }
    
    /**
     * Set flap deflection (-1.0 to 1.0)
     * -1.0 = full left/down, 1.0 = full right/up
     */
    @LuaFunction
    public final void setFlapDeflection(double deflection) throws LuaException {
        if (deflection < -1.0 || deflection > 1.0) {
            throw new LuaException("Flap deflection must be between -1.0 and 1.0");
        }
        this.flapDeflection = (float) deflection;
        applyFlapToNearbyShips();
    }
    
    @LuaFunction
    public final double getFlapDeflection() {
        return flapDeflection;
    }
    
    /**
     * Enable/disable automatic stabilization
     */
    @LuaFunction
    public final void setAutoStabilize(boolean stabilize) {
        this.autoStabilize = stabilize;
    }
    
    @LuaFunction
    public final boolean isAutoStabilizing() {
        return autoStabilize;
    }
    
    /**
     * Set update rate in ticks (1-20)
     */
    @LuaFunction
    public final void setUpdateRate(int rate) throws LuaException {
        if (rate < 1 || rate > 20) {
            throw new LuaException("Update rate must be between 1 and 20 ticks");
        }
        this.updateRate = rate;
    }
    
    @LuaFunction
    public final int getUpdateRate() {
        return updateRate;
    }
    
    /**
     * Ping nearby ships and return count
     */
    @LuaFunction
    public final int pingShip() {
        if (level == null || level.isClientSide) return 0;
        
        int shipCount = 0;
        // Scan for ships within 64 blocks
        for (int x = -64; x <= 64; x += 16) {
            for (int y = -64; y <= 64; y += 16) {
                for (int z = -64; z <= 64; z += 16) {
                    BlockPos scanPos = worldPosition.offset(x, y, z);
                    if (level.hasChunkAt(scanPos)) {
                        BlockEntity be = level.getBlockEntity(scanPos);
                        if (be instanceof ShipVlsGuidanceBlockEntity) {
                            shipCount++;
                        }
                    }
                }
            }
        }
        return shipCount;
    }
    
    /**
     * Get basic ship stats (placeholder for future implementation)
     */
    @LuaFunction
    public final Object[] getShipStats() {
        return new Object[]{
            "ships_detected", pingShip(),
            "flap_deflection", flapDeflection,
            "auto_stabilize", autoStabilize,
            "update_rate", updateRate
        };
    }
    
    /**
     * Emergency stop - zero all controls
     */
    @LuaFunction
    public final void emergencyStop() {
        flapDeflection = 0.0f;
        autoStabilize = true;
        applyFlapToNearbyShips();
    }
    
    private void applyFlapToNearbyShips() {
        if (level == null || level.isClientSide) return;
        
        // Find and apply flap settings to nearby VS2 control surfaces
        for (int x = -32; x <= 32; x += 8) {
            for (int y = -32; y <= 32; y += 8) {
                for (int z = -32; z <= 32; z += 8) {
                    BlockPos scanPos = worldPosition.offset(x, y, z);
                    if (level.hasChunkAt(scanPos)) {
                        var state = level.getBlockState(scanPos);
                        if (state.getBlock() instanceof VS2ControlSurfaceBlock) {
                            // Apply redstone-like signal based on flap deflection
                            int signalStrength = (int) ((flapDeflection + 1.0f) * 7.5f);
                            level.updateNeighbourForOutputSignal(scanPos, state.getBlock());
                        }
                    }
                }
            }
        }
    }
    
    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other == this;
    }
    
    @Override
    public void attach(ILuaContext context) {
        // Called when peripheral is attached to a computer
    }
    
    @Override
    public void detach(ILuaContext context) {
        // Called when peripheral is detached from a computer
    }
    
    public static void serverTick(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, TweakPeripheralBlockEntity entity) {
        if (level.isClientSide) return;
        
        if (level.getGameTime() % entity.updateRate == 0) {
            entity.applyFlapToNearbyShips();
            
            if (entity.autoStabilize && Math.abs(entity.flapDeflection) > 0.1f) {
                // Gradually return to center when auto-stabilizing
                entity.flapDeflection *= 0.9f;
                if (Math.abs(entity.flapDeflection) < 0.01f) {
                    entity.flapDeflection = 0.0f;
                }
            }
        }
    }
}
