package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.energy.LaserEnergyHost;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

public final class PlatformEnergyStorage extends SnapshotParticipant<Integer> implements EnergyStorage {
    private final LaserEnergyHost host;
    private final java.util.function.BooleanSupplier available;

    public PlatformEnergyStorage(LaserEnergyHost host) {
        this(host, () -> true);
    }

    public PlatformEnergyStorage(LaserEnergyHost host, java.util.function.BooleanSupplier available) {
        this.host = host;
        this.available = available;
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("Negative energy transfer");
        if (!(available.getAsBoolean() && host.acceptsEnergy())) return 0;
        int amount = host.energy().receive(maxAmount, true);
        if (amount > 0) {
            updateSnapshots(transaction);
            host.energy().receive(amount, false);
        }
        return amount;
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("Negative energy transfer");
        if (!(available.getAsBoolean() && host.exportsEnergy())) return 0;
        int amount = host.energy().extract(maxAmount, true);
        if (amount > 0) {
            updateSnapshots(transaction);
            host.energy().extract(amount, false);
        }
        return amount;
    }

    @Override
    public boolean supportsExtraction() {
        return available.getAsBoolean() && host.exportsEnergy();
    }

    @Override
    public boolean supportsInsertion() {
        return available.getAsBoolean() && host.acceptsEnergy();
    }

    @Override
    public long getAmount() {
        return host.energy().stored();
    }

    @Override
    public long getCapacity() {
        return host.energy().capacity();
    }

    @Override
    protected Integer createSnapshot() {
        return host.energy().stored();
    }

    @Override
    protected void readSnapshot(Integer snapshot) {
        host.energy().restore(snapshot);
    }
}
