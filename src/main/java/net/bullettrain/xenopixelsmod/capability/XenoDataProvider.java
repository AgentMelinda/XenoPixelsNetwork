package net.bullettrain.xenopixelsmod.capability;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Serializable value stored by the Xeno player data attachment. */
public final class XenoDataProvider implements INBTSerializable<CompoundTag> {
    private final XenoPlayerData data = new XenoPlayerData();

    public XenoPlayerData data() {
        return data;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        data.saveNBT(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        data.loadNBT(nbt);
    }
}
