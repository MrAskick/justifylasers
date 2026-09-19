package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.ProcessFluid;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import java.util.Iterator;
import java.util.stream.IntStream;

/** The tanks are persisted in mB; Fabric transfer units are 81 per mB. */
public final class PlatformWaterStorage extends SnapshotParticipant<IndustrialMachineBlockEntity.Fluids> implements Storage<FluidVariant> {
    private final IndustrialMachineBlockEntity machine;
    public PlatformWaterStorage(IndustrialMachineBlockEntity machine) { this.machine = machine; }

    @Override public long insert(FluidVariant resource, long maximum, TransactionContext transaction) {
        if (maximum < 0) throw new IllegalArgumentException("Negative fluid transfer");
        ProcessFluid fluid = ProcessFluid.ofFluid(resource.getFluid());
        if (machine.isPrivate() || fluid == null || !resource.equals(FluidVariant.of(fluid.fluid()))) return 0;
        int amount = machine.fillFluid(fluid, (int)Math.min(Integer.MAX_VALUE, maximum / 81), true);
        if (amount > 0) {
            updateSnapshots(transaction);
            var state = machine.fluids();
            machine.restoreFluids(new IndustrialMachineBlockEntity.Fluids(fluid, state.amount() + amount, state.product(), state.productAmount()));
        }
        return amount * 81L;
    }
    @Override public long extract(FluidVariant resource, long maximum, TransactionContext transaction) {
        if (maximum < 0) throw new IllegalArgumentException("Negative fluid transfer");
        int tank = machine.tankCount() - 1;
        if (tank < 0 || machine.isPrivate() || !resource.equals(FluidVariant.of(machine.fluid(tank).fluid()))) return 0;
        int amount = machine.drainFluid(tank, (int)Math.min(Integer.MAX_VALUE, maximum / 81), true);
        if (amount > 0) {
            updateSnapshots(transaction);
            var state = machine.fluids();
            machine.restoreFluids(new IndustrialMachineBlockEntity.Fluids(state.input(), state.amount() - (tank == 0 ? amount : 0),
                    state.product(), state.productAmount() - (tank == 1 ? amount : 0)));
        }
        return amount * 81L;
    }
    @Override public Iterator<StorageView<FluidVariant>> iterator() {
        return IntStream.range(0, machine.tankCount()).<StorageView<FluidVariant>>mapToObj(Tank::new).iterator();
    }
    private final class Tank implements SingleSlotStorage<FluidVariant> {
        private final int index;
        Tank(int index) { this.index = index; }
        @Override public boolean isResourceBlank() { return machine.fluidAmount(index) == 0; }
        @Override public FluidVariant getResource() { return isResourceBlank() ? FluidVariant.blank() : FluidVariant.of(machine.fluid(index).fluid()); }
        @Override public long getAmount() { return machine.fluidAmount(index) * 81L; }
        @Override public long getCapacity() { return machine.tankCapacity() * 81L; }
        @Override public long insert(FluidVariant resource, long maximum, TransactionContext transaction) {
            return index == 0 ? PlatformWaterStorage.this.insert(resource, maximum, transaction) : 0;
        }
        @Override public long extract(FluidVariant resource, long maximum, TransactionContext transaction) {
            return index == machine.tankCount() - 1 ? PlatformWaterStorage.this.extract(resource, maximum, transaction) : 0;
        }
    }
    @Override protected IndustrialMachineBlockEntity.Fluids createSnapshot() { return machine.fluids(); }
    @Override protected void readSnapshot(IndustrialMachineBlockEntity.Fluids value) { machine.restoreFluids(value); }
    @Override protected void onFinalCommit() { var controller = machine.controller(); if (controller != null) controller.sync(); }
}
