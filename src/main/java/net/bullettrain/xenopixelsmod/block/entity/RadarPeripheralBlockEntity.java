package net.bullettrain.xenopixelsmod.block.entity;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.bullettrain.valkyrienskies.extension.VS2API;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.Ship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Radar Peripheral for VS2 Ship Detection
 * Based on Some-Peripherals radar implementation
 * Detects ships and entities within range
 */
public class RadarPeripheralBlockEntity extends BlockEntity implements IPeripheral {
    
    private int scanRange = 64; // blocks
    private boolean showEntities = true;
    private boolean showShips = true;
    private int updateInterval = 10; // ticks
    
    public RadarPeripheralBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super(ModBlockEntities.RADAR_PERIPHERAL.get(), pos, state);
    }
    
    @Override
    public @NotNull String getType() {
        return "xenopixels_radar";
    }
    
    @Override
    public @NotNull String[] getMethodNames() {
        return new String[]{
            "scan",
            "getShips",
            "getEntities",
            "setRange",
            "getRange",
            "toggleEntityScan",
            "toggleShipScan",
            "getContactCount"
        };
    }
    
    /**
     * Perform a radar scan and return all detected objects
     */
    @LuaFunction
    public final Object[] scan() throws LuaException {
        if (level == null || level.isClientSide) {
            return new Object[]{};
        }
        
        List<Map<String, Object>> contacts = new ArrayList<>();
        BlockPos center = worldPosition;
        
        // Scan for ships
        if (showShips) {
            contacts.addAll(scanForShips(center));
        }
        
        // Scan for entities
        if (showEntities) {
            contacts.addAll(scanForEntities(center));
        }
        
        return new Object[]{contacts.toArray()};
    }
    
    /**
     * Get only ship contacts
     */
    @LuaFunction
    public final Object[] getShips() throws LuaException {
        if (level == null || level.isClientSide) {
            return new Object[]{};
        }
        
        List<Map<String, Object>> ships = scanForShips(worldPosition);
        return new Object[]{ships.toArray()};
    }
    
    /**
     * Get only entity contacts
     */
    @LuaFunction
    public final Object[] getEntities() throws LuaException {
        if (level == null || level.isClientSide) {
            return new Object[]{};
        }
        
        List<Map<String, Object>> entities = scanForEntities(worldPosition);
        return new Object[]{entities.toArray()};
    }
    
    /**
     * Set scan range (16-256 blocks)
     */
    @LuaFunction
    public final void setRange(int range) throws LuaException {
        if (range < 16 || range > 256) {
            throw new LuaException("Range must be between 16 and 256 blocks");
        }
        this.scanRange = range;
    }
    
    @LuaFunction
    public final int getRange() {
        return scanRange;
    }
    
    @LuaFunction
    public final void toggleEntityScan(boolean enable) {
        this.showEntities = enable;
    }
    
    @LuaFunction
    public final void toggleShipScan(boolean enable) {
        this.showShips = enable;
    }
    
    @LuaFunction
    public final int getContactCount() {
        if (level == null || level.isClientSide) return 0;
        
        int count = 0;
        if (showShips) {
            count += countShipsInRange();
        }
        if (showEntities) {
            count += countEntitiesInRange();
        }
        return count;
    }
    
    private List<Map<String, Object>> scanForShips(BlockPos center) {
        List<Map<String, Object>> ships = new ArrayList<>();
        
        if (level == null) return ships;
        
        // Use VS2 API to get ships in area
        try {
            // Get all ships and filter by distance
            for (Ship ship : VS2API.getAllShips()) {
                double shipX = ship.getShipData().getCenterOfMass().x();
                double shipY = ship.getShipData().getCenterOfMass().y();
                double shipZ = ship.getShipData().getCenterOfMass().z();
                
                double dist = Math.sqrt(
                    Math.pow(shipX - center.getX(), 2) +
                    Math.pow(shipY - center.getY(), 2) +
                    Math.pow(shipZ - center.getZ(), 2)
                );
                
                if (dist <= scanRange) {
                    Map<String, Object> shipData = new HashMap<>();
                    shipData.put("type", "ship");
                    shipData.put("id", ship.getId());
                    shipData.put("distance", Math.round(dist * 10.0) / 10.0);
                    shipData.put("x", Math.round(shipX * 10.0) / 10.0);
                    shipData.put("y", Math.round(shipY * 10.0) / 10.0);
                    shipData.put("z", Math.round(shipZ * 10.0) / 10.0);
                    shipData.put("velocity_x", Math.round(ship.getVelocity().x() * 100.0) / 100.0);
                    shipData.put("velocity_y", Math.round(ship.getVelocity().y() * 100.0) / 100.0);
                    shipData.put("velocity_z", Math.round(ship.getVelocity().z() * 100.0) / 100.0);
                    ships.add(shipData);
                }
            }
        } catch (Exception e) {
            // VS2 not available or error
        }
        
        return ships;
    }
    
    private List<Map<String, Object>> scanForEntities(BlockPos center) {
        List<Map<String, Object>> entities = new ArrayList<>();
        
        if (level == null) return entities;
        
        var AABB = new net.minecraft.world.phys.AABB(
            center.getX() - scanRange,
            center.getY() - scanRange,
            center.getZ() - scanRange,
            center.getX() + scanRange,
            center.getY() + scanRange,
            center.getZ() + scanRange
        );
        
        level.getEntities(null, AABB).forEach(entity -> {
            double dist = entity.position().distanceTo(center.getCenter());
            if (dist <= scanRange) {
                Map<String, Object> entityData = new HashMap<>();
                entityData.put("type", "entity");
                entityData.put("name", entity.getName().getString());
                entityData.put("distance", Math.round(dist * 10.0) / 10.0);
                entityData.put("x", Math.round(entity.getX() * 10.0) / 10.0);
                entityData.put("y", Math.round(entity.getY() * 10.0) / 10.0);
                entityData.put("z", Math.round(entity.getZ() * 10.0) / 10.0);
                entities.add(entityData);
            }
        });
        
        return entities;
    }
    
    private int countShipsInRange() {
        try {
            int count = 0;
            for (Ship ship : VS2API.getAllShips()) {
                double shipX = ship.getShipData().getCenterOfMass().x();
                double shipY = ship.getShipData().getCenterOfMass().y();
                double shipZ = ship.getShipData().getCenterOfMass().z();
                
                double dist = Math.sqrt(
                    Math.pow(shipX - worldPosition.getX(), 2) +
                    Math.pow(shipY - worldPosition.getY(), 2) +
                    Math.pow(shipZ - worldPosition.getZ(), 2)
                );
                
                if (dist <= scanRange) count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }
    
    private int countEntitiesInRange() {
        if (level == null) return 0;
        
        var AABB = new net.minecraft.world.phys.AABB(
            worldPosition.getX() - scanRange,
            worldPosition.getY() - scanRange,
            worldPosition.getZ() - scanRange,
            worldPosition.getX() + scanRange,
            worldPosition.getY() + scanRange,
            worldPosition.getZ() + scanRange
        );
        
        return level.getEntities(null, AABB).size();
    }
    
    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other == this;
    }
    
    @Override
    public void attach(dan200.computercraft.api.lua.ILuaContext context) {
        // Called when peripheral is attached to a computer
    }
    
    @Override
    public void detach(dan200.computercraft.api.lua.ILuaContext context) {
        // Called when peripheral is detached from a computer
    }
    
    public static void serverTick(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, RadarPeripheralBlockEntity entity) {
        // Radar updates on demand via scan() method
        // No continuous tick needed
    }
}
