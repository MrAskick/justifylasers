package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.fluid.Fluids;

/** The tank is persisted in mB; Fabric transfer units are 81 per mB. */
public final class PlatformWaterStorage extends SnapshotParticipant<Integer> implements SingleSlotStorage<FluidVariant> {
    private final IndustrialMachineBlockEntity machine;
    public PlatformWaterStorage(IndustrialMachineBlockEntity machine) { this.machine = machine; }
    @Override public long insert(FluidVariant resource, long maximum, TransactionContext transaction) {
        if (maximum < 0) throw new IllegalArgumentException("Negative fluid transfer");
        if (machine.isPrivate() || !resource.equals(FluidVariant.of(Fluids.WATER))) return 0;
        int amount = machine.fillWater((int)Math.min(Integer.MAX_VALUE, maximum / 81), true);
        if (amount > 0) { updateSnapshots(transaction); machine.restoreWater(machine.water() + amount); }
        return amount * 81L;
    }
    @Override public long extract(FluidVariant resource, long maximum, TransactionContext transaction) {
        if (maximum < 0) throw new IllegalArgumentException("Negative fluid transfer");
        if (machine.isPrivate() || !resource.equals(FluidVariant.of(Fluids.WATER))) return 0;
        int amount = machine.drainWater((int)Math.min(Integer.MAX_VALUE, maximum / 81), true);
        if (amount > 0) { updateSnapshots(transaction); machine.restoreWater(machine.water() - amount); }
        return amount * 81L;
    }
    @Override public boolean isResourceBlank() { return machine.water() == 0; }
    @Override public FluidVariant getResource() { return isResourceBlank() ? FluidVariant.blank() : FluidVariant.of(Fluids.WATER); }
    @Override public long getAmount() { return machine.water() * 81L; }
    @Override public long getCapacity() { return machine.tankCapacity() * 81L; }
    @Override protected Integer createSnapshot() { return machine.water(); }
    @Override protected void readSnapshot(Integer value) { machine.restoreWater(value); }
    @Override protected void onFinalCommit() { var controller = machine.controller(); if (controller != null) controller.sync(); }
}
