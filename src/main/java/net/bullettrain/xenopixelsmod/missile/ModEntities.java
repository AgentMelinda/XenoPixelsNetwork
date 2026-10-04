package net.bullettrain.xenopixelsmod.missile;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
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
                            .clientTrackingRange(64)
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

    public static final DeferredHolder<EntityType<?>, EntityType<XenoPilotSeatEntity>> PILOT_SEAT_FORK =
            ENTITIES.register("pilot_seat_fork", () ->
                    EntityType.Builder.<XenoPilotSeatEntity>of(XenoPilotSeatEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(10)
                            .updateInterval(2)
                            .fireImmune()
                            .build(XenoPixelsMod.MOD_ID + ":pilot_seat_fork"));

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

    public static final DeferredHolder<EntityType<?>, EntityType<XenoNpcEntity>> XENO_NPC_HUMANOID = npc("xeno_npc_humanoid", XenoNpcRole.HUMANOID);
    public static final DeferredHolder<EntityType<?>, EntityType<XenoNpcEntity>> XENO_NPC_CREATURE = npc("xeno_npc_creature", XenoNpcRole.CREATURE);
    public static final DeferredHolder<EntityType<?>, EntityType<XenoNpcEntity>> XENO_NPC_TRADER = npc("xeno_npc_trader", XenoNpcRole.TRADER);
    public static final DeferredHolder<EntityType<?>, EntityType<XenoNpcEntity>> XENO_NPC_GUARD = npc("xeno_npc_guard", XenoNpcRole.GUARD);
    public static final DeferredHolder<EntityType<?>, EntityType<XenoNpcEntity>> XENO_NPC_COMPANION = npc("xeno_npc_companion", XenoNpcRole.COMPANION);
    public static final DeferredHolder<EntityType<?>, EntityType<XenoNpcEntity>> XENO_NPC_QUEST = npc("xeno_npc_quest", XenoNpcRole.QUEST);
    // A new registry id is additive: no existing world names it, so nothing is reclassified.
    public static final DeferredHolder<EntityType<?>, EntityType<XenoNpcEntity>> XENO_NPC_TRANSPORTER = npc("xeno_npc_transporter", XenoNpcRole.TRANSPORTER);
    public static final DeferredHolder<EntityType<?>, EntityType<XenoNpcEntity>> XENO_NPC_BANK = npc("xeno_npc_bank", XenoNpcRole.BANK);

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
        var attributes = XenoNpcEntity.createAttributes().build();
        event.put(XENO_NPC_HUMANOID.get(), attributes);
        event.put(XENO_NPC_CREATURE.get(), attributes);
        event.put(XENO_NPC_TRADER.get(), attributes);
        event.put(XENO_NPC_GUARD.get(), attributes);
        event.put(XENO_NPC_COMPANION.get(), attributes);
        event.put(XENO_NPC_QUEST.get(), attributes);
        event.put(XENO_NPC_TRANSPORTER.get(), attributes);
        event.put(XENO_NPC_BANK.get(), attributes);
    }

    private static DeferredHolder<EntityType<?>, EntityType<XenoNpcEntity>> npc(String id, XenoNpcRole role) {
        return ENTITIES.register(id, () -> EntityType.Builder
                .of((EntityType<XenoNpcEntity> type, net.minecraft.world.level.Level level) ->
                                new XenoNpcEntity(type, level, role), MobCategory.CREATURE)
                .sized(role.creature() ? 0.9f : 0.6f, role.creature() ? 1.4f : 1.8f)
                .clientTrackingRange(10)
                .updateInterval(3)
                .build(XenoPixelsMod.MOD_ID + ":" + id));
    }

    public static EntityType<XenoNpcEntity> xenoNpcType(XenoNpcRole role) {
        return switch (role == null ? XenoNpcRole.HUMANOID : role) {
            case HUMANOID -> XENO_NPC_HUMANOID.get();
            case CREATURE -> XENO_NPC_CREATURE.get();
            case TRADER -> XENO_NPC_TRADER.get();
            case GUARD -> XENO_NPC_GUARD.get();
            case COMPANION -> XENO_NPC_COMPANION.get();
            case QUEST -> XENO_NPC_QUEST.get();
            case TRANSPORTER -> XENO_NPC_TRANSPORTER.get();
            case BANK -> XENO_NPC_BANK.get();
        };
    }
}
