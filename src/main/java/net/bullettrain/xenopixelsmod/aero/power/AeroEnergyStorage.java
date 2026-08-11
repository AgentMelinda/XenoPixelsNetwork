package net.bullettrain.xenopixelsmod.aero.power;

import net.bullettrain.xenopixelsmod.aero.AeroConfig;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The flight controller's FE buffer.
 *
 * <p><b>Receive-only.</b> {@link #canExtract()} is false and {@link #extractEnergy} is a
 * no-op, so no adjacent machine can siphon the controller's reserve. A ship whose flight
 * authority can be drained by a neighbouring cable is a griefing vector, and the buffer exists
 * to survive load spikes rather than to act as a battery for the rest of the base.
 *
 * <p>Capacity and input rate come from {@link AeroConfig} and are read live, so
 * {@code /reload}-style config edits take effect without rebuilding the block entity. Stored
 * energy is clamped on every read of the limits in case an operator lowers capacity below the
 * current charge.
 */
public final class AeroEnergyStorage implements IEnergyStorage {
    private int stored;

    public AeroEnergyStorage() {
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (toReceive <= 0) return 0;
        int capacity = getMaxEnergyStored();
        int accepted = Math.min(capacity - clampedStored(), Math.min(maxReceive(), toReceive));
        if (accepted <= 0) return 0;
        if (!simulate) stored = clampedStored() + accepted;
        return accepted;
    }

    /** Always zero — see the class javadoc. */
    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return clampedStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return Math.max(1_000, AeroConfig.energyCapacity);
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    private int maxReceive() {
        return Math.max(1, AeroConfig.maxReceiveFePerTick);
    }

    private int clampedStored() {
        int capacity = Math.max(1_000, AeroConfig.energyCapacity);
        if (stored > capacity) stored = capacity;
        if (stored < 0) stored = 0;
        return stored;
    }

    /**
     * Internal draw for our own tick. Not reachable through the capability, so other mods
     * cannot call it.
     *
     * @return how much was actually consumed, which may be less than requested
     */
    public int consume(int amount) {
        if (amount <= 0) return 0;
        int available = clampedStored();
        int used = Math.min(available, amount);
        stored = available - used;
        return used;
    }

    /** True when the buffer could sustain {@code amount} this tick. */
    public boolean canAfford(int amount) {
        return amount <= 0 || clampedStored() >= amount;
    }

    public void setStored(int value) {
        stored = Math.max(0, value);
        clampedStored();
    }
}
