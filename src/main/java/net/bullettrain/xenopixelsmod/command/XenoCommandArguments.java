package net.bullettrain.xenopixelsmod.command;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the region parser's serializer so the command tree can sync to clients. */
public final class XenoCommandArguments {
    private static final DeferredRegister<ArgumentTypeInfo<?, ?>> TYPES =
            DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, XenoPixelsMod.MOD_ID);

    static {
        TYPES.register("region_name", () -> ArgumentTypeInfos.registerByClass(
                RegionNameArgument.class, SingletonArgumentInfo.contextFree(RegionNameArgument::new)));
    }

    private XenoCommandArguments() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
}
