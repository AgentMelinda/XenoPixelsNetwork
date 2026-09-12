package net.bullettrain.xenopixelsmod.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-authoritative registry of sign shops.
 *
 * <p>Persisted as {@link SavedData} on the overworld data storage, matching {@code PlotManager}.
 * A sign's own block-entity text is still the source of truth for what it sells; this store only
 * records who created it and keeps a stable list so listings survive a chunk unload without
 * needing the block entity to be loaded.</p>
 *
 * <p>Y is part of a sign's identity (unlike plots), because a sign is a specific block. The key is
 * therefore {@code (dimension, BlockPos)}.</p>
 */
public final class SignShopManager extends SavedData {

    private static final String FILE_NAME = "xenopixels_sign_shops";
    private static final String KEY_SHOPS = "Shops";

    private final List<Entry> shops = new ArrayList<>();

    /** A registered sign shop: its position, owner, and the listing as last parsed. */
    public record Entry(ResourceLocation dimension, BlockPos pos, UUID owner, SignShopData data) {

        public Entry {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(data, "data");
        }

        public boolean at(ResourceLocation otherDimension, BlockPos otherPos) {
            return dimension.equals(otherDimension) && pos.equals(otherPos);
        }
    }

    public static SignShopManager get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new Factory<>(SignShopManager::new, SignShopManager::load), FILE_NAME);
    }

    public static SignShopManager load(CompoundTag tag, HolderLookup.Provider registries) {
        SignShopManager result = new SignShopManager();
        ListTag list = tag.getList(KEY_SHOPS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            ResourceLocation target = ResourceLocation.tryParse(entry.getString("Target"));
            if (dimension == null || target == null) {
                continue;
            }
            int quantity = entry.getInt("Quantity");
            double price = entry.getDouble("Price");
            if (quantity < 1 || quantity > SignShopSyntax.MAX_QUANTITY || !(price >= 0.0)) {
                continue;
            }
            result.shops.add(new Entry(dimension,
                    new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")),
                    entry.hasUUID("Owner") ? entry.getUUID("Owner") : null,
                    new SignShopData(target, quantity, price)));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry shop : shops) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", shop.dimension().toString());
            entry.putInt("X", shop.pos().getX());
            entry.putInt("Y", shop.pos().getY());
            entry.putInt("Z", shop.pos().getZ());
            if (shop.owner() != null) {
                entry.putUUID("Owner", shop.owner());
            }
            entry.putString("Target", shop.data().targetId().toString());
            entry.putInt("Quantity", shop.data().quantity());
            entry.putDouble("Price", shop.data().price());
            list.add(entry);
        }
        tag.put(KEY_SHOPS, list);
        return tag;
    }

    /** All registered shops, in registration order. */
    public List<Entry> all() {
        return List.copyOf(shops);
    }

    /** The shop at {@code (dimension, pos)}, or {@code null}. */
    @Nullable
    public Entry at(ResourceLocation dimension, BlockPos pos) {
        for (Entry shop : shops) {
            if (shop.at(dimension, pos)) {
                return shop;
            }
        }
        return null;
    }

    /**
     * Records or replaces the shop at {@code (dimension, pos)}.
     *
     * @return true when the store changed.
     */
    public boolean put(ResourceLocation dimension, BlockPos pos, @Nullable UUID owner, SignShopData data) {
        if (dimension == null || pos == null || data == null) {
            return false;
        }
        Entry replacement = new Entry(dimension, pos, owner, data);
        for (int i = 0; i < shops.size(); i++) {
            if (shops.get(i).at(dimension, pos)) {
                if (shops.get(i).data().equals(data) && Objects.equals(shops.get(i).owner(), owner)) {
                    return false;
                }
                shops.set(i, replacement);
                setDirty();
                return true;
            }
        }
        shops.add(replacement);
        setDirty();
        return true;
    }

    /**
     * Removes the shop at {@code (dimension, pos)}. Called when a sign stops being a valid shop,
     * for example when the marker line is cleared or the target no longer resolves.
     *
     * @return true when a shop was removed.
     */
    public boolean remove(ResourceLocation dimension, BlockPos pos) {
        boolean removed = shops.removeIf(shop -> shop.at(dimension, pos));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** Removes every shop created by {@code owner}. Returns how many were removed. */
    public int release(UUID owner) {
        if (owner == null) {
            return 0;
        }
        int before = shops.size();
        shops.removeIf(shop -> owner.equals(shop.owner()));
        int removed = before - shops.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }
}