package net.askcraft.justifylasers.smoke;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

final class NativeInventoryProbe {
    static void verify(BlockEntity block, ItemStack sample, boolean blocked) {
        Inventory inventory = (Inventory) block;
        for (Direction side : Direction.values()) {
            var port = block.getWorld().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, block.getPos(), side);
            if (port == null) throw new AssertionError("Missing native item port: " + block.getType() + "/" + side);
            int accepted = -1;
            for (int slot = 0; slot < port.getSlots(); slot++) {
                if (port.insertItem(slot, sample.copy(), true).isEmpty()) { accepted = slot; break; }
            }
            if (!inventory.isEmpty()) throw new AssertionError("Simulation changed inventory");
            if (blocked) {
                if (accepted != -1) throw new AssertionError("Private inventory accepted automated items");
                continue;
            }
            if (accepted < 0 || !port.insertItem(accepted, sample.copy(), false).isEmpty()) throw new AssertionError("Native insertion failed");
            if (block instanceof net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity) {
                if (!port.extractItem(accepted, 1, true).isEmpty()) throw new AssertionError("Machine input was extractable");
                inventory.clear();
                inventory.setStack(net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity.OUTPUT, sample.copy());
                accepted = -1;
                for (int slot = 0; slot < port.getSlots(); slot++) if (!port.getStackInSlot(slot).isEmpty()) { accepted = slot; break; }
                if (accepted < 0) throw new AssertionError("Missing machine output port");
            }
            if (port.extractItem(accepted, 1, true).getCount() != 1 || inventory.isEmpty()) throw new AssertionError("Extraction simulation failed");
            if (port.extractItem(accepted, 1, false).getCount() != 1) throw new AssertionError("Native extraction failed");
            if (!inventory.isEmpty()) throw new AssertionError("Item round trip duplicated or retained items");
        }
    }
    private NativeInventoryProbe() { }

    static void verifyConfiguratorEnergy() {
        var stack = new ItemStack(net.askcraft.justifylasers.registry.ModBlocks.CONFIGURATOR);
        var port = stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        if (port == null || port.getMaxEnergyStored() != 20_000) throw new AssertionError("Missing configurator energy capability");
        if (port.receiveEnergy(1000, true) != 256 || port.getEnergyStored() != 0) throw new AssertionError("Tool charge simulation/limit");
        if (port.receiveEnergy(100, false) != 100 || port.extractEnergy(100, false) != 0 || port.getEnergyStored() != 100)
            throw new AssertionError("Native configurator charging");
        if (net.askcraft.justifylasers.energy.RechargeableItem.stored(stack.copy()) != 100) throw new AssertionError("Tool charge does not survive item copy");
    }
}
