package net.bullettrain.xenopixelsmod.peripheral;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ship.ShipWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShipRadarPeripheral implements IPeripheral {

    private final Level level;
    private final BlockPos position;
    private int scanRange = 64;
    private long lastScanTime = 0;
    private List<Map<String, Object>> lastContacts = new ArrayList<>();

    public ShipRadarPeripheral(Level level, BlockPos position) {
        this.level = level;
        this.position = position;
    }

    @Override
    public @NotNull String getType() {
        return "xenopixels_radar";
    }

    @Override
    public @NotNull String[] getMethodNames() {
        return new String[]{
            "scan",
            "getContacts",
            "getContactCount",
            "setRange",
            "getRange",
            "clearContacts"
        };
    }

    @LuaFunction
    public final int scan() throws LuaException {
        if (level.isClientSide) throw new LuaException("Cannot scan on client");

        long currentTime = level.getGameTime();
        if (currentTime - lastScanTime < 10) {
            return lastContacts.size(); // Rate limit: 10 tick cooldown
        }

        lastScanTime = currentTime;
        lastContacts.clear();

        // Scan for ships in range
        double rangeSquared = scanRange * scanRange;
        
        // Get all ships in the area via VS2 API
        Iterable<ShipWrapper> ships = VSGameUtilsKt.getAllShips(level);
        for (ShipWrapper ship : ships) {
            double distSq = position.distToCenterOf(ship.getShipData().getCenterOfMass());
            if (distSq <= rangeSquared) {
                Map<String, Object> contact = new HashMap<>();
                contact.put("type", "ship");
                contact.put("id", ship.getId());
                contact.put("distance", Math.sqrt(distSq));
                contact.put("velocity", ship.getVelocity().length());
                
                // Calculate relative position
                contact.put("x", ship.getShipData().getCenterOfMass().x - position.getX());
                contact.put("y", ship.getShipData().getCenterOfMass().y - position.getY());
                contact.put("z", ship.getShipData().getCenterOfMass().z - position.getZ());
                
                lastContacts.add(contact);
            }
        }

        // Also scan for entities (players, mobs)
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, 
            new net.minecraft.world.phys.AABB(
                position.getX() - scanRange, position.getY() - scanRange, position.getZ() - scanRange,
                position.getX() + scanRange, position.getY() + scanRange, position.getZ() + scanRange
            ));
        
        for (Entity entity : entities) {
            if (entity instanceof net.minecraft.world.player.Player) continue; // Skip local player if desired
            
            double distSq = position.distToSqr(entity.position());
            if (distSq <= rangeSquared) {
                Map<String, Object> contact = new HashMap<>();
                contact.put("type", "entity");
                contact.put("uuid", entity.getUUID().toString());
                contact.put("name", entity.getName().getString());
                contact.put("distance", Math.sqrt(distSq));
                contact.put("x", entity.getX() - position.getX());
                contact.put("y", entity.getY() - position.getY());
                contact.put("z", entity.getZ() - position.getZ());
                
                lastContacts.add(contact);
            }
        }

        return lastContacts.size();
    }

    @LuaFunction
    public final List<Map<String, Object>> getContacts() {
        return new ArrayList<>(lastContacts);
    }

    @LuaFunction
    public final int getContactCount() {
        return lastContacts.size();
    }

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
    public final void clearContacts() {
        lastContacts.clear();
        lastScanTime = 0;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        if (other instanceof ShipRadarPeripheral radarOther) {
            return this.level == radarOther.level && this.position.equals(radarOther.position);
        }
        return false;
    }
}
