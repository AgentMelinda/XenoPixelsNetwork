package net.bullettrain.xenopixelsmod.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Clientbound (channel {@code xenopixelsmod:hakai_sweep_ghosts_v1}): blocks area Hakai just erased in sweep style. The
 * server has already removed them; the client keeps drawing each as a "ghost" cut by the descending plane
 * (top Y -> bottom Y over the duration, smoothstep, from {@code startTick} in level game time) until the plane passes
 * below it. Positions are raw block positions: Sable ship blocks are plot positions and the client places them with the
 * ship's live pose.
 */
public record HakaiSweepGhosts(int jobId, long startTick, int durationTicks, float topY, float bottomY,
                               List<Ghost> ghosts) {
    public static final int MAX = 2048;

    public record Ghost(BlockPos pos, int stateId) {}

    public HakaiSweepGhosts {
        if (ghosts == null || ghosts.size() > MAX || durationTicks < 1 || !Float.isFinite(topY) || !Float.isFinite(bottomY))
            throw new IllegalArgumentException("invalid hakai sweep ghosts");
        ghosts = List.copyOf(ghosts);
    }

    public static void encode(HakaiSweepGhosts p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.jobId); buf.writeLong(p.startTick); buf.writeVarInt(p.durationTicks);
        buf.writeFloat(p.topY); buf.writeFloat(p.bottomY);
        buf.writeVarInt(p.ghosts.size());
        for (Ghost g : p.ghosts) { buf.writeBlockPos(g.pos); buf.writeVarInt(g.stateId); }
    }

    public static HakaiSweepGhosts decode(FriendlyByteBuf buf) {
        int job = buf.readVarInt();
        long start = buf.readLong();
        int duration = buf.readVarInt();
        float top = buf.readFloat(), bottom = buf.readFloat();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX) throw new IllegalArgumentException("too many hakai ghosts");
        var ghosts = new ArrayList<Ghost>(n);
        for (int i = 0; i < n; i++) ghosts.add(new Ghost(buf.readBlockPos(), buf.readVarInt()));
        return new HakaiSweepGhosts(job, start, duration, top, bottom, ghosts);
    }

    /** The plane's world Y at a (fractional) tick, shared by server erase timing and client drawing. */
    public static float cutY(long startTick, int durationTicks, float topY, float bottomY, double now) {
        double t = Math.max(0, Math.min(1, (now - startTick) / durationTicks));
        double eased = t * t * (3 - 2 * t);
        return (float) (topY - (topY - bottomY) * eased);
    }
}
