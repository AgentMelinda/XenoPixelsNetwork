package net.bullettrain.xenopixelsmod.fx.effek;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Sends one effect to the players in range. AAA Particles in the game, a fake in tests. */
public interface EffekSender {
    void send(ServerLevel level, Request request);

    /**
     * {@code pos} is always set and is what the range check uses. {@code boundEntity} is -1 for a
     * positional effect; otherwise AAA moves the effect with that entity and turns its local +Z
     * along the entity's velocity every frame ({@code forward} is then unused).
     */
    record Request(ResourceLocation id, Vec3 pos, Vec3 forward, float scale, double range, int boundEntity,
                   Follow follow, double lookAnchor) {
        Request(ResourceLocation id, Vec3 pos, Vec3 forward, float scale, double range) {
            this(id, pos, forward, scale, range, -1, Follow.NONE, 0.0);
        }
    }

    /** How a bound effect follows its entity every frame (AAA 2.3.1 bound-emitter modes). */
    enum Follow {
        /** Not bound: stays where it was played. */
        NONE,
        /** Moves with the entity and never turns (upright effects: the Sparking aura). */
        POSITION,
        /** Moves with the entity, local +Z along its velocity (the missile plume). */
        VELOCITY,
        /** Moves with the entity's eyes, local +Z along its look (the Sparking flight aura). */
        LOOK
    }
}
