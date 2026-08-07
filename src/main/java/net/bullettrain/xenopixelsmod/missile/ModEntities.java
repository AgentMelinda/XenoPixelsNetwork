package net.bullettrain.xenopixelsmod.missile;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, XenoPixelsMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<BallisticMissileEntity>> BALLISTIC_MISSILE =
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
