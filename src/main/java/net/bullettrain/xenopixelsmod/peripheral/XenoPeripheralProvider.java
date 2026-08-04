package net.bullettrain.xenopixelsmod.peripheral;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import net.bullettrain.xenopixelsmod.block.entity.ShipFlapBlockEntity;
import net.bullettrain.xenopixelsmod.block.XenoRadarBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XenoPeripheralProvider implements IPeripheralProvider {

    @Override
    public @Nullable IPeripheral getPeripheral(@NotNull Level level, @NotNull BlockPos pos, @NotNull net.minecraft.core.Direction side) {
        BlockEntity entity = level.getBlockEntity(pos);
        
        if (entity instanceof ShipFlapBlockEntity) {
            return new ShipFlapPeripheral(level, pos);
        }
        
        if (level.getBlockState(pos).getBlock() instanceof XenoRadarBlock) {
            return new ShipRadarPeripheral(level, pos);
        }
        
        return null;
    }
}
