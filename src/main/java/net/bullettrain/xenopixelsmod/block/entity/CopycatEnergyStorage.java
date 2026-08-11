package net.bullettrain.xenopixelsmod.block.entity;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Receive-only, server-configurable FE buffer for copycat glowstone. */
public final class CopycatEnergyStorage implements IEnergyStorage {
    private final Runnable changed;
    private int stored;

    CopycatEnergyStorage(Runnable changed) {
        this.changed = changed;
    }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        if (!XenoServerConfig.copycatForgeEnergyEnabled || amount <= 0) return 0;
        int accepted = Math.min(amount, Math.min(XenoServerConfig.copycatMaxReceiveFePerTick,
                getMaxEnergyStored() - getEnergyStored()));
        if (!simulate && accepted > 0) {
            stored += accepted;
            changed.run();
        }
        return Math.max(0, accepted);
    }

    @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
    @Override public int getEnergyStored() { return Math.max(0, Math.min(stored, getMaxEnergyStored())); }
    @Override public int getMaxEnergyStored() { return Math.max(1_000, XenoServerConfig.copycatEnergyCapacity); }
    @Override public boolean canExtract() { return false; }
    @Override public boolean canReceive() { return XenoServerConfig.copycatForgeEnergyEnabled; }

    boolean consume(int amount) {
        if (amount <= 0) return true;
        int available = getEnergyStored();
        if (available < amount) return false;
        stored = available - amount;
        changed.run();
        return true;
    }

    void setStored(int amount) {
        stored = Math.max(0, amount);
        if (stored > getMaxEnergyStored()) stored = getMaxEnergyStored();
    }
}
