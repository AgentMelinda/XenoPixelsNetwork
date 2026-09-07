package net.bullettrain.xenopixelsmod.missile;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
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

    /**
     * The rideable pilot seat. Tiny, invisible and never tracked far — it exists so a player can
     * be "the pilot" of a flight controller, not to be seen.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<XenoPilotSeatEntity>> PILOT_SEAT =
            ENTITIES.register("pilot_seat", () ->
                    EntityType.Builder.<XenoPilotSeatEntity>of(XenoPilotSeatEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(10)
                            .updateInterval(2)
                            .fireImmune()
                            .build(XenoPixelsMod.MOD_ID + ":pilot_seat"));

    /**
     * A fighter's other body, used by Shi Shin No Ken and by Zanzoken's afterimage. Sized like a
     * player because that is what the renderer draws, and tracked closely so a copy standing a
     * couple of blocks away never lags behind the fighter it mirrors.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity>> CLONE =
            ENTITIES.register("clone", () ->
                    EntityType.Builder.<net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity>of(
                                    net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .fireImmune()
                            .build(XenoPixelsMod.MOD_ID + ":clone"));

    private ModEntities() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        // Copies are living bodies, so they need attributes. This is the mod's only such
        // registration; nothing else it spawns is a LivingEntity.
        bus.addListener(ModEntities::registerAttributes);
    }

    private static void registerAttributes(
            net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(CLONE.get(),
                net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity.createAttributes().build());
    }
}
