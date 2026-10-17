package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidField;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidFieldSnapshot;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidPresence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Client copy of the server's asteroid field description (S11 B1) for the far renderer (B2). Written on the client
 * main thread by the packet handlers; {@link #current()} is null until the first snapshot and after logout.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class AsteroidFieldClient {
    private static volatile AsteroidFieldSnapshot current;
    private static volatile Set<AsteroidField.ObjectId> present = Set.of();

    private AsteroidFieldClient() {}

    public static AsteroidFieldSnapshot current() { return current; }

    /** Asteroids that are physically present (Sable draws them); the far renderer skips these ids. */
    public static Set<AsteroidField.ObjectId> present() { return present; }

    public static void apply(AsteroidFieldSnapshot snapshot) { current = snapshot; }

    public static void apply(AsteroidPresence packet) {
        var ids = new HashSet<AsteroidField.ObjectId>();
        for (var entry : packet.entries()) ids.add(entry.id());
        present = Set.copyOf(ids);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        current = null;
        present = Set.of();
    }
}
