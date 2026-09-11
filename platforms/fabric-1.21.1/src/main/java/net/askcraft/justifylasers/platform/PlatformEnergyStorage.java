package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

public final class PlatformEnergyStorage extends SnapshotParticipant<Integer> implements EnergyStorage {
    private final LaserEmitterBlockEntity emitter;

    public PlatformEnergyStorage(LaserEmitterBlockEntity emitter) {
        this.emitter = emitter;
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("Negative energy transfer");
        if (!emitter.acceptsEnergy()) return 0;
        int amount = emitter.energy().receive(maxAmount, true);
        if (amount > 0) {
            updateSnapshots(transaction);
            emitter.energy().receive(amount, false);
        }
        return amount;
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
        if (maxAmount < 0) throw new IllegalArgumentException("Negative energy transfer");
        return 0;
    }

    @Override
    public boolean supportsExtraction() {
        return false;
    }

    @Override
    public long getAmount() {
        return emitter.energy().stored();
    }

    @Override
    public long getCapacity() {
        return emitter.energy().capacity();
    }

    @Override
    protected Integer createSnapshot() {
        return emitter.energy().stored();
    }

    @Override
    protected void readSnapshot(Integer snapshot) {
        emitter.energy().restore(snapshot);
    }
}
