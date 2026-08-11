package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.network.packet.PartySyncPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.UUID;

/**
 * Client mirror of the server's party membership.
 *
 * <p>A plain snapshot with no logic: the server decides who is in a party and this only remembers
 * what it was last told, so the HUD and the server can never disagree about membership.
 *
 * <p>Written on the network thread's main-thread hand-off and read every frame by
 * {@code XenoPartyOverlay}, hence the volatile field and the immutable list — the HUD must never
 * see a half-updated roster mid-render.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientParty {

    private static volatile List<UUID> members = List.of();

    private ClientParty() {
    }

    public static void accept(PartySyncPacket packet) {
        members = List.copyOf(packet.members());
    }

    /** Every member including the local player; empty when unpartied. */
    public static List<UUID> members() {
        return members;
    }

    public static boolean active() {
        return !members.isEmpty();
    }

    public static boolean contains(UUID id) {
        return members.contains(id);
    }

    /** Clear on disconnect so a party does not survive into the next world. */
    public static void clear() {
        members = List.of();
    }
}
