package net.bullettrain.xenopixelsmod.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Top-level provider (not an inner class) so classloaders never fail on
 * {@code XenoCapabilities$XenoDataProvider}.
 */
public final class XenoDataProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    private final XenoPlayerData data = new XenoPlayerData();
    private final LazyOptional<XenoPlayerData> optional = LazyOptional.of(() -> data);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return XenoCapabilities.XENO_DATA.orEmpty(cap, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        data.saveNBT(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        data.loadNBT(nbt);
    }

    public void invalidate() {
        optional.invalidate();
    }
}
