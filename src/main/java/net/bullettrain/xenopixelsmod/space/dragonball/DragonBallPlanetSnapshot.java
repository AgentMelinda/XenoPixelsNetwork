package net.bullettrain.xenopixelsmod.space.dragonball;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Clientbound list of planet-form Super Dragon Balls the client should draw as smooth spheres (channel
 * {@code xenopixelsmod:dragon_ball_planets_v1}). Empty list = draw nothing (voxel bodies are drawn by Sable itself).
 */
public record DragonBallPlanetSnapshot(List<Ball> balls) {
    public static final int MAX = 64;

    public record Ball(int stars, double x, double y, double z, float radius) {
        public Ball {
            if (stars < 1 || stars > 7 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !(radius > 0) || radius > 100_000) throw new IllegalArgumentException("invalid dragon ball planet");
        }
    }

    public DragonBallPlanetSnapshot {
        if (balls == null || balls.size() > MAX) throw new IllegalArgumentException("invalid dragon ball planet list");
        balls = List.copyOf(balls);
    }

    public static void encode(DragonBallPlanetSnapshot packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.balls.size());
        for (Ball b : packet.balls) { buf.writeByte(b.stars); buf.writeDouble(b.x); buf.writeDouble(b.y); buf.writeDouble(b.z); buf.writeFloat(b.radius); }
    }

    public static DragonBallPlanetSnapshot decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX) throw new IllegalArgumentException("dragon ball planet list too large");
        var balls = new ArrayList<Ball>(size);
        for (int i = 0; i < size; i++) balls.add(new Ball(buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()));
        return new DragonBallPlanetSnapshot(balls);
    }
}
