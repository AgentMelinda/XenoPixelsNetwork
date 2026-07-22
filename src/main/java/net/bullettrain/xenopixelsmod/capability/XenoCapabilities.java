package net.bullettrain.xenopixelsmod.capability;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public class XenoCapabilities {
    public static final Capability<XenoPlayerData> XENO_DATA = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation KEY = new ResourceLocation(XenoPixelsMod.MOD_ID, "xeno_data");

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(KEY, new XenoDataProvider());
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(XENO_DATA).ifPresent(oldData ->
                event.getEntity().getCapability(XENO_DATA).ifPresent(newData -> newData.copyFrom(oldData)));
    }

    public static class XenoDataProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        private final XenoPlayerData data = new XenoPlayerData();
        private final LazyOptional<XenoPlayerData> optional = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return XENO_DATA.orEmpty(cap, optional);
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
    }
}