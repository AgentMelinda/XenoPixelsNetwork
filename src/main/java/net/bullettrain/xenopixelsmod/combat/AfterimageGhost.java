package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.AfterimageGhostPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Leaves a rendered copy of a fighter standing where their body was.
 *
 * <p>{@link VanishShadeFx} stamps the same moment out of particles and says in its own notes why:
 * a dust silhouette needs no client state and no renderer. A real body copy reads far better —
 * it wears the fighter's actual DragonMineZ appearance, hair, form and aura — so both exist and
 * {@code zanzokenGhostAfterimage} chooses between them. The silhouette stays the safe fallback
 * for anyone whose client cannot draw the copy.
 *
 * <p>Broadcast to everyone tracking the level rather than just the dodger: the whole point of the
 * technique is what the *attacker* sees.
 */
public final class AfterimageGhost {

    private AfterimageGhost() {
    }

    public static void leave(ServerPlayer owner, Vec3 origin) {
        if (owner == null || origin == null) return;
        if (!(owner.level() instanceof ServerLevel)) return;
        // Tracking-and-self: everyone who can already see this fighter is exactly the set that
        // should see the image they left behind.
        ModNetwork.sendToTrackingAndSelf(owner,
                new AfterimageGhostPacket(owner.getId(), origin, owner.getYRot(), owner.getXRot(),
                        Math.max(1, XenoServerConfig.zanzokenRingTicks)));
    }
}
