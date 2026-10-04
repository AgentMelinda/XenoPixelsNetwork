package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * XenoAPI world data. Stored data persists in one overworld SavedData. Temp data is one
 * server-wide map, "the same cross dimension" as the reference documents, cleared at server stop.
 * Per-block data ({@code IBlock.getStoreddata/getTempdata}) lives here too, keyed by dimension and
 * position, because most blocks have no block entity to carry it.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoWorldData extends SavedData {
    static final String NAME = "xenopixelsmod_xenoapi_world";
    /** Blocks that may carry stored or temp script data at once. */
    static final int MAX_BLOCKS = 4096;
    private static final Map<String, Object> TEMP = new HashMap<>();
    private static final Map<String, Map<String, Object>> BLOCK_TEMP = new HashMap<>();
    private CompoundTag stored = new CompoundTag();
    private CompoundTag blocks = new CompoundTag();

    static XenoWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(XenoWorldData::new, XenoWorldData::load), NAME);
    }

    private static XenoWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        XenoWorldData data = new XenoWorldData();
        data.stored = tag.getCompound("Stored").copy();
        data.blocks = tag.getCompound("Blocks").copy();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Stored", stored.copy());
        if (!blocks.isEmpty()) tag.put("Blocks", blocks.copy());
        return tag;
    }

    CompoundTag stored() { return stored; }

    void storedChanged(CompoundTag tag) {
        stored = tag;
        setDirty();
    }

    /** One block's stored script data; empty when it has none. */
    CompoundTag block(String key) { return blocks.getCompound(key); }

    void blockChanged(String key, CompoundTag tag) {
        if (tag.isEmpty()) {
            blocks.remove(key);
        } else {
            if (!blocks.contains(key) && blocks.size() >= MAX_BLOCKS) {
                throw new IllegalStateException("IBlock.getStoreddata: more than " + MAX_BLOCKS + " blocks hold stored data");
            }
            blocks.put(key, tag);
        }
        setDirty();
    }

    static Map<String, Object> temp() { return TEMP; }

    static Map<String, Object> blockTemp(String key) {
        Map<String, Object> existing = BLOCK_TEMP.get(key);
        if (existing != null) return existing;
        if (BLOCK_TEMP.size() >= MAX_BLOCKS) {
            throw new IllegalStateException("IBlock.getTempdata: more than " + MAX_BLOCKS + " blocks hold temp data");
        }
        Map<String, Object> created = new HashMap<>();
        BLOCK_TEMP.put(key, created);
        return created;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TEMP.clear();
        BLOCK_TEMP.clear();
    }
}
