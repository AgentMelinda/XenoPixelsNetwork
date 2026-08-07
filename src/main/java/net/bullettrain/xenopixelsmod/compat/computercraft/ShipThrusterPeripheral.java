package net.bullettrain.xenopixelsmod.compat.computercraft;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.GenericPeripheral;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.entity.ShipThrusterBlockEntity;
import net.minecraft.resources.ResourceLocation;

/**
 * CC methods for {@link ShipThrusterBlockEntity} (Create thruster-style control).
 *
 * <pre>
 * peripheral.setPower(0.0-1.0)
 * peripheral.getPower()
 * peripheral.setMaxForce(n)
 * peripheral.getMaxForce()
 * peripheral.isActive()
 * peripheral.release()  -- return to redstone control
 * </pre>
 */
public final class ShipThrusterPeripheral implements GenericPeripheral {
    @Override
    public String id() {
        return ResourceLocation.fromNamespaceAndPath(XenoPixelsMod.MOD_ID, "ship_thruster").toString();
    }

    @LuaFunction(mainThread = true)
    public final void setPower(ShipThrusterBlockEntity thruster, double power) {
        thruster.setCcPower(power);
    }

    @LuaFunction(mainThread = true)
    public final double getPower(ShipThrusterBlockEntity thruster) {
        return thruster.getPower();
    }

    @LuaFunction(mainThread = true)
    public final void setMaxForce(ShipThrusterBlockEntity thruster, double force) {
        thruster.setMaxForce(force);
    }

    @LuaFunction(mainThread = true)
    public final double getMaxForce(ShipThrusterBlockEntity thruster) {
        return thruster.getMaxForce();
    }

    @LuaFunction(mainThread = true)
    public final boolean isActive(ShipThrusterBlockEntity thruster) {
        return thruster.isActive();
    }

    /** Clear CC override; thruster follows redstone again. */
    @LuaFunction(mainThread = true)
    public final void release(ShipThrusterBlockEntity thruster) {
        thruster.setCcPower(-1);
    }
}
