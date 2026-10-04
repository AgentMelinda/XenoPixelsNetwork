package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.npc.spawn.ClientNaturalSpawns;
import net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawn;
import net.bullettrain.xenopixelsmod.npc.spawn.NpcNaturalSpawns;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C: the natural spawn rules, so the client editor can show and edit them.
 *
 * <p>The store index already tells the client that a rule called {@code plains_wanderer} exists, but
 * it carries a label and a revision and nothing else. The Natural Spawns page has to show which
 * biomes a rule answers to, which clone it spawns, and how often — and those live in the entry's NBT,
 * which only the server reads. Same gap {@link SyncBanksPacket} was written for, same shape.
 *
 * <p>Sent on join, after {@code /reload}, and after any write to the SPAWNS store.
 *
 * <p>A full replacement rather than a delta: a client that missed one write converges on the next.
 */
public record SyncNaturalSpawnsPacket(List<NpcNaturalSpawn> spawns) {

    /**
     * Below the store's own cap of 512 on purpose. A store may legitimately outgrow what one packet
     * carries, and a truncated list the client can still read beats a payload the vanilla size cap
     * rejects and the stuck connection behind it.
     */
    static final int MAX_SPAWNS = 256;

    private static final int MAX_TEXT = 128;
    private static final int MAX_BIOME = 192;

    /** Everything the server's store currently holds. */
    public static SyncNaturalSpawnsPacket current() {
        List<NpcNaturalSpawn> list = NpcNaturalSpawns.all();
        return new SyncNaturalSpawnsPacket(list.size() <= MAX_SPAWNS ? list : list.subList(0, MAX_SPAWNS));
    }

    public SyncNaturalSpawnsPacket {
        spawns = List.copyOf(spawns == null ? List.of() : spawns);
    }

    public SyncNaturalSpawnsPacket(FriendlyByteBuf buf) {
        this(read(buf));
    }

    private static List<NpcNaturalSpawn> read(FriendlyByteBuf buf) {
        int count = Math.min(MAX_SPAWNS, Math.max(0, buf.readVarInt()));
        List<NpcNaturalSpawn> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(MAX_TEXT);
            String title = buf.readUtf(MAX_TEXT);
            int biomeCount = Math.min(NpcNaturalSpawn.MAX_BIOMES, Math.max(0, buf.readVarInt()));
            List<String> biomes = new ArrayList<>(biomeCount);
            for (int b = 0; b < biomeCount; b++) {
                biomes.add(buf.readUtf(MAX_BIOME));
            }
            int weight = buf.readVarInt();
            int tab = buf.readVarInt();
            String cloneId = buf.readUtf(MAX_TEXT);
            String time = buf.readUtf(16);
            out.add(new NpcNaturalSpawn(id, title, biomes, weight, tab, cloneId, time));
        }
        return out;
    }

    public void encode(FriendlyByteBuf buf) {
        List<NpcNaturalSpawn> list = spawns == null ? List.of() : spawns;
        int count = Math.min(MAX_SPAWNS, list.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            NpcNaturalSpawn spawn = list.get(i);
            buf.writeUtf(spawn.id(), MAX_TEXT);
            buf.writeUtf(spawn.title(), MAX_TEXT);
            int biomes = Math.min(NpcNaturalSpawn.MAX_BIOMES, spawn.biomes().size());
            buf.writeVarInt(biomes);
            for (int b = 0; b < biomes; b++) {
                buf.writeUtf(spawn.biomes().get(b), MAX_BIOME);
            }
            buf.writeVarInt(spawn.weight());
            buf.writeVarInt(spawn.cloneTab());
            buf.writeUtf(spawn.cloneId(), MAX_TEXT);
            buf.writeUtf(spawn.time(), 16);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientNaturalSpawns.accept(spawns));
        ctx.setPacketHandled(true);
    }
}
