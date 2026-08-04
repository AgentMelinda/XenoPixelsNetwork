package net.bullettrain.xenopixelsmod.block.entity;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import net.bullettrain.xenopixelsmod.init.XenoBlockEntities;
import net.bullettrain.xenopixelsmod.peripheral.ShipRadarPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShipRadarBlockEntity extends BlockEntity implements IPeripheralProvider {

    private ShipRadarPeripheral peripheral;

    public ShipRadarBlockEntity(BlockPos pos, BlockState state) {
        super(XenoBlockEntities.SHIP_RADAR.get(), pos, state);
    }

    @Override
    public @Nullable IPeripheral getPeripheral(@NotNull net.minecraft.core.Direction side) {
        if (peripheral == null || level == null) {
            peripheral = new ShipRadarPeripheral(level, worldPosition);
        }
        return peripheral;
    }

    public void tick() {
        // Radar scanning is on-demand via peripheral methods
        // Could add automatic periodic scanning here if desired
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (peripheral != null) {
            peripheral = null;
        }
    }
}
