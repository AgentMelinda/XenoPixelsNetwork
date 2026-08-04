package net.bullettrain.xenopixelsmod.missile;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, XenoPixelsMod.MOD_ID);

    public static final RegistryObject<EntityType<BallisticMissileEntity>> BALLISTIC_MISSILE =
            ENTITIES.register("ballistic_missile", () ->
                    EntityType.Builder.<BallisticMissileEntity>of(BallisticMissileEntity::new, MobCategory.MISC)
                            .sized(0.55f, 0.55f)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .fireImmune()
                            .build(XenoPixelsMod.MOD_ID + ":ballistic_missile"));

    private ModEntities() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
