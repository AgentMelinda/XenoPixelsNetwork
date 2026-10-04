package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/** Recreates native Xeno NPCs from persistent death anchors without duplicating loaded entities. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoNpcRespawnHandler {
    private static final String ANCHOR_KEY = "XenoNpcRespawnAnchor";
    private static final double DUPLICATE_RADIUS = 2.0;

    private XenoNpcRespawnHandler() {}

    public static void markAnchor(Entity entity, UUID anchor) {
        entity.getPersistentData().putUUID(ANCHOR_KEY, anchor);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        XenoNpcRespawnData data = XenoNpcRespawnData.get(event.getServer());
        // The overworld's game time is the persisted server clock; getTickCount() restarts at 0
        // every launch, which is what used to strand every entry written before a restart.
        long now = event.getServer().overworld().getGameTime();
        // One comparison in the overwhelmingly common case. This used to prune and then walk every
        // entry - up to MAX_ENTRIES of them - twenty times a second, for the sake of the few that
        // were actually due. Pruning moves in here too: expiry only matters once something is due,
        // and a set that nothing is waiting on has not changed since the last time it was pruned.
        if (now < data.earliestDue()) {
            return;
        }
        data.prune(now);
        // Tracks whether this pass leaves anything due-but-unconsumable behind. An entry only
        // respawns when its chunk happens to be loaded, so one in an empty corner of the world is
        // due forever - and would hold the gate open, rescanning every tick, which is precisely
        // the cost the gate removes.
        boolean blocked = false;
        for (XenoNpcRespawnData.Entry entry : data.entries()) {
            if (now < entry.respawnTick()) continue;
            ServerLevel level = event.getServer().getLevel(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                            entry.dimension()));
            if (level == null || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(entry.x(), entry.y(), entry.z()))) {
                blocked = true;
                continue;
            }
            if (hasAnchor(level, entry)) {
                data.remove(entry);
                continue;
            }
            XenoNpcEntity npc = ModEntities.xenoNpcType(entry.role()).create(level);
            if (npc == null) continue;
            npc.moveTo(entry.x(), entry.y(), entry.z(), entry.yaw(), entry.pitch());
            CompoundTag restored = entry.npcData().copy();
            npc.npcData().restoreFromTag(restored);
            npc.refreshNameplate();

            // Stats, appearance and behaviour live in the combat profile, not in npcData, so
            // without this a respawned NPC came back as a default one wearing its old name.
            if (restored.contains("Profile")) {
                var profile = net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile
                        .fromTag(restored.getCompound("Profile"));
                profile.write(npc);
                net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour.apply(npc, profile);
            }
            markAnchor(npc, entry.anchor());
            level.addFreshEntity(npc);
            data.remove(entry);
        }
        if (blocked) {
            // Something is due and cannot be acted on. Hold the gate for a moment rather than
            // rescanning every tick until whoever owns that chunk happens to walk back.
            data.deferUntil(now + XenoNpcRespawnData.UNLOADED_RETRY_TICKS);
        }
    }

    private static boolean hasAnchor(ServerLevel level, XenoNpcRespawnData.Entry entry) {
        var box = new AABB(entry.x() - DUPLICATE_RADIUS, entry.y() - DUPLICATE_RADIUS,
                entry.z() - DUPLICATE_RADIUS, entry.x() + DUPLICATE_RADIUS,
                entry.y() + DUPLICATE_RADIUS, entry.z() + DUPLICATE_RADIUS);
        return !level.getEntitiesOfClass(XenoNpcEntity.class, box,
                npc -> npc.getPersistentData().hasUUID(ANCHOR_KEY)
                        && npc.getPersistentData().getUUID(ANCHOR_KEY).equals(entry.anchor())).isEmpty();
    }
}
