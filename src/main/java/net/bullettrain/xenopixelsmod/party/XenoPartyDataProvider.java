package net.bullettrain.xenopixelsmod.party;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Capability provider for party data.
 */
public final class XenoPartyDataProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    private final XenoPartyData data = new XenoPartyData();
    private final LazyOptional<XenoPartyData> optional = LazyOptional.of(() -> data);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return XenoPartyCapabilities.PARTY_DATA.orEmpty(cap, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        data.serializeNBT(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        data.deserializeNBT(nbt);
    }

    public void invalidate() {
        optional.invalidate();
    }
}
