package net.askcraft.justifylasers.smoke;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

final class NativeInventoryProbe {
    static void verify(BlockEntity block, ItemStack sample, boolean blocked) {
        Inventory inventory = (Inventory) block;
        for (Direction side : Direction.values()) {
            var port = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(block.getWorld(), block.getPos(), side);
            if (port == null) throw new AssertionError("Missing native item port: " + block.getType() + "/" + side);
            var variant = net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(sample);
            try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                long inserted = port.insert(variant, 1, transaction);
                if (inserted != (blocked ? 0 : 1)) throw new AssertionError("Sided item insertion policy");
            }
            if (!inventory.isEmpty()) throw new AssertionError("Aborted item insertion changed inventory");
            if (blocked) continue;
            try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                if (port.insert(variant, 1, transaction) != 1) throw new AssertionError("Insert failed");
                transaction.commit();
            }
            if (block instanceof net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity) {
                try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    if (port.extract(variant, 1, transaction) != 0) throw new AssertionError("Machine input was extractable");
                }
                inventory.clear();
                inventory.setStack(net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity.OUTPUT, sample.copy());
            }
            try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                if (port.extract(variant, 1, transaction) != 1) throw new AssertionError("Simulated extraction failed");
            }
            if (inventory.isEmpty()) throw new AssertionError("Aborted extraction removed inventory");
            try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                if (port.extract(variant, 1, transaction) != 1) throw new AssertionError("Extraction failed");
                transaction.commit();
            }
            if (!inventory.isEmpty()) throw new AssertionError("Item round trip duplicated or retained items");
        }
    }
    private NativeInventoryProbe() { }

    static void verifyConfiguratorEnergy() {
        var inventory = new net.minecraft.inventory.SimpleInventory(new ItemStack(net.askcraft.justifylasers.registry.ModBlocks.CONFIGURATOR));
        var context = net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.ofSingleSlot(
                net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage.of(inventory, null).getSlot(0));
        var port = context.find(team.reborn.energy.api.EnergyStorage.ITEM);
        if (port == null || port.getCapacity() != 20_000) throw new AssertionError("Missing configurator energy capability");
        try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            if (port.insert(1000, transaction) != 256) throw new AssertionError("Tool transfer limit");
        }
        if (port.getAmount() != 0) throw new AssertionError("Aborted charging changed the tool");
        try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            if (port.insert(100, transaction) != 100 || port.extract(100, transaction) != 0) throw new AssertionError("Tool charge/extract");
            transaction.commit();
        }
        if (port.getAmount() != 100 || net.askcraft.justifylasers.energy.RechargeableItem.stored(inventory.getStack(0).copy()) != 100)
            throw new AssertionError("Tool charge does not survive item copy");
    }
}
