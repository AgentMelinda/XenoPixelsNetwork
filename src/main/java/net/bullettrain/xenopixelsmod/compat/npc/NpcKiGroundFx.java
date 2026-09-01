package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.init.MainParticles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

/** DMZ rock/dust at the feet while an NPC's ki aura is on and they are on the ground. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcKiGroundFx {
    private NpcKiGroundFx() {}

    public static void pulse(ServerLevel level, LivingEntity npc) {
        if (level == null || npc == null || !npc.onGround()) {
            return;
        }
        double x = npc.getX();
        double y = npc.getY() + 0.08;
        double z = npc.getZ();
        NpcTransformSystem.particle(level, MainParticles.ROCK.get(), x, y, z, 6, 0.55, 0.12, 0.55, 0.18);
        NpcTransformSystem.particle(level, MainParticles.DUST.get(), x, y, z, 8, 0.7, 0.06, 0.7, 0.08);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        for (UUID id : NpcAuraFx.activeIds()) {
            LivingEntity npc = find(server, id);
            if (npc != null && npc.isAlive() && npc.onGround()
                    && NpcCombatProfile.read(npc).auraRocks
                    && npc.level() instanceof ServerLevel level) {
                pulse(level, npc);
            }
        }
    }

    private static LivingEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }
}
