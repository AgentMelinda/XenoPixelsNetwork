package net.bullettrain.xenopixelsmod.plot;

import net.bullettrain.xenopixelsmod.compat.mmoecon.MmoEconBridge;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Listings for plots put up for sale, and settlement of those sales.
 *
 * <p><b>Money first, then ownership.</b> A purchase calls
 * {@link MmoEconBridge#transfer(UUID, UUID, long)} and only reassigns the plot — in
 * {@link PlotManager} and in YAWP via {@link PlotYaWP#reassign} — when the transfer reports
 * success. A plot is therefore never handed over without the money having moved.</p>
 *
 * <p>Fails closed. With MMO Econ absent a sale cannot settle and reports
 * {@link Result#NO_ECONOMY}, exactly as a sign shop does. The listing stays in place so it can be
 * bought once the economy is available again.</p>
 *
 * <p>Persisted as {@link SavedData} on the overworld data storage, matching {@link PlotManager}.
 * Listings are keyed by the same {@code (dimension, minX, minZ)} tuple that identifies a plot.</p>
 */
public final class PlotSale extends SavedData {

    private static final String FILE_NAME = "xenopixels_plot_sales";
    private static final String KEY_LISTINGS = "Listings";

    /** One plot offered for sale. Bounds are copied so a listing survives a plot store reload. */
    public record Listing(ResourceLocation dimension, int minX, int minZ, int maxX, int maxZ,
                          UUID seller, double price) {

        /** The plot bounds this listing describes, owned by {@link #seller()}. */
        public PlotArea asPlot() {
            return new PlotArea(dimension, minX, minZ, maxX, maxZ, seller, PlotFlags.DEFAULT);
        }
    }

    /** Outcome of a purchase, shaped like {@code SignShopPurchase.Result}. */
    public enum Result {
        /** Payment settled and the plot was reassigned. */
        SUCCESS,
        /** MMO Econ is not installed, so no balance exists to charge. */
        NO_ECONOMY,
        /** The buyer's balance is below the listing price. */
        INSUFFICIENT_FUNDS,
        /** The transfer itself failed after funds were confirmed. */
        TRANSFER_FAILED,
        /** No listing exists for that plot. */
        NOT_FOR_SALE,
        /** The plot is not claimed, or the buyer was not on a server level. */
        NO_PLOT,
        /** The buyer already owns the plot. */
        ALREADY_OWNER
    }

    private final List<Listing> listings = new ArrayList<>();

    public static PlotSale get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new Factory<>(PlotSale::new, PlotSale::load), FILE_NAME);
    }

    public static PlotSale load(CompoundTag tag, HolderLookup.Provider registries) {
        PlotSale result = new PlotSale();
        ListTag list = tag.getList(KEY_LISTINGS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null || !entry.hasUUID("Seller")) {
                continue;
            }
            result.listings.add(new Listing(dimension,
                    entry.getInt("MinX"), entry.getInt("MinZ"),
                    entry.getInt("MaxX"), entry.getInt("MaxZ"),
                    entry.getUUID("Seller"), entry.getDouble("Price")));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Listing listing : listings) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", listing.dimension().toString());
            entry.putInt("MinX", listing.minX());
            entry.putInt("MinZ", listing.minZ());
            entry.putInt("MaxX", listing.maxX());
            entry.putInt("MaxZ", listing.maxZ());
            entry.putUUID("Seller", listing.seller());
            entry.putDouble("Price", listing.price());
            list.add(entry);
        }
        tag.put(KEY_LISTINGS, list);
        return tag;
    }

    /** All current listings, in listing order. */
    public List<Listing> all() {
        return List.copyOf(listings);
    }

    /** The listing covering the plot that starts at {@code (minX, minZ)}, or {@code null}. */
    @Nullable
    public Listing at(ResourceLocation dimension, int minX, int minZ) {
        for (Listing listing : listings) {
            if (listing.dimension().equals(dimension)
                    && listing.minX() == minX && listing.minZ() == minZ) {
                return listing;
            }
        }
        return null;
    }

    /** Offers {@code plot} for {@code price}. Replaces any existing listing for the same plot. */
    public boolean list(PlotArea plot, double price) {
        if (plot == null || plot.owner() == null || price < 0.0D) {
            return false;
        }
        unlistInternal(plot.dimension(), plot.minX(), plot.minZ());
        listings.add(new Listing(plot.dimension(), plot.minX(), plot.minZ(),
                plot.maxX(), plot.maxZ(), plot.owner(), price));
        setDirty();
        return true;
    }

    /** Withdraws the listing for {@code plot}. Returns false when it was not listed. */
    public boolean unlist(PlotArea plot) {
        if (plot == null) {
            return false;
        }
        boolean removed = unlistInternal(plot.dimension(), plot.minX(), plot.minZ());
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /**
     * Buys the listed plot {@code plot} for {@code buyer}.
     *
     * @return the outcome; only {@link Result#SUCCESS} moves money or ownership.
     */
    public static Result buy(ServerPlayer buyer, PlotArea plot) {
        if (buyer == null || plot == null || !(buyer.level() instanceof ServerLevel level)) {
            return Result.NO_PLOT;
        }
        MinecraftServer server = level.getServer();
        PlotManager manager = PlotManager.get(server);
        PlotArea claimed = manager.at(plot.dimension(), plot.minX(), plot.minZ());
        if (claimed == null) {
            return Result.NO_PLOT;
        }
        Listing listing = get(server).at(plot.dimension(), plot.minX(), plot.minZ());
        if (listing == null) {
            return Result.NOT_FOR_SALE;
        }
        if (listing.seller().equals(buyer.getUUID())) {
            return Result.ALREADY_OWNER;
        }
        if (!MmoEconBridge.available()) {
            return Result.NO_ECONOMY;
        }
        long units = MmoEconBridge.toUnits(listing.price());
        if (!MmoEconBridge.hasFunds(buyer.getUUID(), units)) {
            return Result.INSUFFICIENT_FUNDS;
        }
        if (!MmoEconBridge.transfer(buyer.getUUID(), listing.seller(), units)) {
            return Result.TRANSFER_FAILED;
        }
        // Paid. Ownership moves now; a failure past this point leaves the money moved, so the
        // reassign is the one step that must not be skipped.
        if (!manager.setOwner(claimed, buyer.getUUID())) {
            return Result.NO_PLOT;
        }
        PlotArea updated = new PlotArea(claimed.dimension(), claimed.minX(), claimed.minZ(),
                claimed.maxX(), claimed.maxZ(), buyer.getUUID(), claimed.flags());
        PlotYaWP.reassign(level, updated, buyer);
        get(server).unlist(updated);
        return Result.SUCCESS;
    }

    private boolean unlistInternal(ResourceLocation dimension, int minX, int minZ) {
        return listings.removeIf(listing -> listing.dimension().equals(dimension)
                && listing.minX() == minX && listing.minZ() == minZ);
    }
}