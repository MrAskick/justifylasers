package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;

public record PlatformTabletEnergy(ContainerItemContext context) implements EnergyStorage {
    private boolean valid() {
        return !context.getItemVariant().isBlank() && context.getItemVariant().getItem() instanceof ExtraterrestrialTabletItem && context.getAmount() == 1;
    }
    @Override public long insert(long maximum, TransactionContext transaction) {
        StoragePreconditions.notNegative(maximum);
        if (!valid()) return 0;
        var stack = context.getItemVariant().toStack();
        int accepted = ExtraterrestrialTabletItem.receive(stack, (int)Math.min(maximum, Integer.MAX_VALUE), false);
        return accepted > 0 && context.exchange(ItemVariant.of(stack), 1, transaction) == 1 ? accepted : 0;
    }
    @Override public long extract(long maximum, TransactionContext transaction) { StoragePreconditions.notNegative(maximum); return 0; }
    @Override public boolean supportsExtraction() { return false; }
    @Override public long getAmount() { return valid() ? ExtraterrestrialTabletItem.charge(context.getItemVariant().toStack()) : 0; }
    @Override public long getCapacity() { return valid() ? ExtraterrestrialTabletItem.CAPACITY : 0; }
}
