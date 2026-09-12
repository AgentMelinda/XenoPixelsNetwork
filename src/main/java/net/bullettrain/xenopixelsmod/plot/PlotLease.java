package net.bullettrain.xenopixelsmod.plot;

import net.bullettrain.xenopixelsmod.compat.mmoecon.MmoEconBridge;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
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
 * Rent on a claimed plot, charged one period at a time.
 *
 * <p><b>The renter pays, the owner receives.</b> A lease is keyed by the same
 * {@code (dimension, minX, minZ)} tuple that identifies a plot, so it never has to agree with
 * {@link PlotArea} about bounds. The renter must be online at the due tick to be charged; an
 * offline renter, a missing economy, or a balance short of the period price all end the lease the
 * same way — the claim is released and its YAWP region removed with it.</p>
 *
 * <p><b>Fails closed, like every other money path.</b> A lease that cannot be paid for is not
 * silently extended; it lapses. The one thing a lapse never does is move money.</p>
 *
 * <p>Persisted as {@link SavedData} on the overworld data storage, matching {@link PlotManager}
 * and {@link PlotSale}.</p>
 */
public final class PlotLease extends SavedData {

    private static final String FILE_NAME = "xenopixels_plot_leases";
    private static final String KEY_LEASES = "Leases";

    /** One running lease. {@code nextDueTick} is a server tick count, not a wall clock. */
    public record Lease(ResourceLocation dimension, int minX, int minZ,
                        UUID renter, UUID owner,
                        double pricePerPeriod, int periodTicks, long nextDueTick) {
    }

    /** Why a due lease was renewed or ended. */
    public enum Outcome {
        /** The period was paid and the next due tick moved forward. */
        RENEWED,
        /** The renter was not online at the due tick. */
        LAPSED_OFFLINE,
        /** The renter was online but could not pay. */
        LAPSED_UNFUNDED,
        /** MMO Econ is not installed, so nothing can be charged. */
        NO_ECONOMY
    }

    private final List<Lease> leases = new ArrayList<>();

    public static PlotLease get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new Factory<>(PlotLease::new, PlotLease::load), FILE_NAME);
    }

    public static PlotLease load(CompoundTag tag, HolderLookup.Provider registries) {
        PlotLease result = new PlotLease();
        ListTag list = tag.getList(KEY_LEASES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null || !entry.hasUUID("Renter") || !entry.hasUUID("Owner")) {
                continue;
            }
            result.leases.add(new Lease(dimension,
                    entry.getInt("MinX"), entry.getInt("MinZ"),
                    entry.getUUID("Renter"), entry.getUUID("Owner"),
                    entry.getDouble("Price"), Math.max(1, entry.getInt("Period")),
                    entry.getLong("NextDue")));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Lease lease : leases) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", lease.dimension().toString());
            entry.putInt("MinX", lease.minX());
            entry.putInt("MinZ", lease.minZ());
            entry.putUUID("Renter", lease.renter());
            entry.putUUID("Owner", lease.owner());
            entry.putDouble("Price", lease.pricePerPeriod());
            entry.putInt("Period", lease.periodTicks());
            entry.putLong("NextDue", lease.nextDueTick());
            list.add(entry);
        }
        tag.put(KEY_LEASES, list);
        return tag;
    }

    /** All running leases, in creation order. */
    public List<Lease> all() {
        return List.copyOf(leases);
    }

    /** The lease covering the plot at {@code (minX, minZ)}, or {@code null}. */
    @Nullable
    public Lease at(ResourceLocation dimension, int minX, int minZ) {
        for (Lease lease : leases) {
            if (lease.dimension().equals(dimension)
                    && lease.minX() == minX && lease.minZ() == minZ) {
                return lease;
            }
        }
        return null;
    }

    /**
     * Starts or replaces the lease on {@code plot}.
     *
     * @param now the current server tick, used as the base for the first due tick.
     * @return false when the terms are unusable, so a caller never stores a lease it cannot charge.
     */
    public boolean lease(PlotArea plot, UUID renter, double pricePerPeriod, int periodTicks, long now) {
        if (plot == null || plot.owner() == null || renter == null) {
            return false;
        }
        if (!(pricePerPeriod >= 0.0D) || periodTicks <= 0) {
            return false;
        }
        unlease(plot.dimension(), plot.minX(), plot.minZ());
        leases.add(new Lease(plot.dimension(), plot.minX(), plot.minZ(),
                renter, plot.owner(), pricePerPeriod, periodTicks, nextDue(now, periodTicks)));
        setDirty();
        return true;
    }

    /** Ends the lease on the plot at {@code (minX, minZ)}. Returns false when none ran there. */
    public boolean unlease(ResourceLocation dimension, int minX, int minZ) {
        if (dimension == null) {
            return false;
        }
        boolean removed = leases.removeIf(lease -> lease.dimension().equals(dimension)
                && lease.minX() == minX && lease.minZ() == minZ);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** Every lease whose period has come due at {@code now}. */
    public List<Lease> due(long now) {
        List<Lease> result = new ArrayList<>();
        for (Lease lease : leases) {
            if (isDue(lease.nextDueTick(), now)) {
                result.add(lease);
            }
        }
        return result;
    }

    /** Moves a renewed lease's due tick forward. Returns false when it is no longer running. */
    public boolean renew(Lease lease, long nextDueTick) {
        int index = leases.indexOf(lease);
        if (index < 0) {
            return false;
        }
        leases.set(index, new Lease(lease.dimension(), lease.minX(), lease.minZ(),
                lease.renter(), lease.owner(), lease.pricePerPeriod(),
                lease.periodTicks(), nextDueTick));
        setDirty();
        return true;
    }

    /** True when a period due at {@code nextDueTick} has arrived at {@code now}. */
    public static boolean isDue(long nextDueTick, long now) {
        return now >= nextDueTick;
    }

    /** The tick the period after {@code now} falls due. A period is never shorter than one tick. */
    public static long nextDue(long now, int periodTicks) {
        return now + Math.max(1, periodTicks);
    }

    /**
     * The one renewal decision, kept pure so the policy can be tested without a server.
     *
     * <p>Order matters: an offline renter is reported as offline rather than unfunded, because the
     * two are different operational facts and only one of them is the renter's fault.</p>
     */
    public static Outcome decide(boolean renterOnline, boolean economyAvailable, boolean funded) {
        if (!renterOnline) {
            return Outcome.LAPSED_OFFLINE;
        }
        if (!economyAvailable) {
            return Outcome.NO_ECONOMY;
        }
        return funded ? Outcome.RENEWED : Outcome.LAPSED_UNFUNDED;
    }

    /**
     * Charges every lease due at {@code now}.
     *
     * <p>Called from the server tick. A renewed period debits the renter; anything else ends the
     * lease, which releases the claim and removes its YAWP region.</p>
     */
    public static void settleDue(MinecraftServer server, long now) {
        if (server == null) {
            return;
        }
        PlotLease leases = get(server);
        List<Lease> due = leases.due(now);
        if (due.isEmpty()) {
            return;
        }
        boolean economy = MmoEconBridge.available();
        for (Lease lease : due) {
            ServerPlayer renter = server.getPlayerList().getPlayer(lease.renter());
            long units = MmoEconBridge.toUnits(lease.pricePerPeriod());
            Outcome outcome = decide(renter != null, economy,
                    renter != null && economy && MmoEconBridge.hasFunds(renter.getUUID(), units));
            if (outcome == Outcome.RENEWED && !MmoEconBridge.withdraw(lease.renter(), units)) {
                // Funds were confirmed a moment ago; a failure here is still a failure to pay.
                outcome = Outcome.LAPSED_UNFUNDED;
            }
            if (outcome == Outcome.RENEWED) {
                leases.renew(lease, nextDue(now, lease.periodTicks()));
            } else {
                lapse(server, leases, lease);
            }
        }
    }

    /**
     * Ends a lease and releases the plot behind it.
     *
     * <p>The claim is removed through {@link PlotManager#remove} with the YAWP region taken out
     * first, rather than through {@link PlotManager#release(MinecraftServer, UUID)}, which would
     * also drop every other plot the same owner holds.</p>
     */
    private static void lapse(MinecraftServer server, PlotLease leases, Lease lease) {
        ServerLevel level = server.getLevel(
                ResourceKey.create(Registries.DIMENSION, lease.dimension()));
        PlotArea plot = level == null ? null
                : PlotManager.get(server).at(lease.dimension(), lease.minX(), lease.minZ());
        if (plot != null) {
            if (level != null) {
                PlotYaWP.remove(level, plot);
            }
            PlotManager.get(server).remove(plot);
        }
        leases.unlease(lease.dimension(), lease.minX(), lease.minZ());
    }
}