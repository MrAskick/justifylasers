package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.energy.RechargeableItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

public record PlatformTabletEnergy(ItemStack stack) implements IEnergyStorage {
    @Override public int receiveEnergy(int amount, boolean simulate) { return RechargeableItem.receive(stack, amount, simulate); }
    @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
    @Override public int getEnergyStored() { return RechargeableItem.stored(stack); }
    @Override public int getMaxEnergyStored() { return RechargeableItem.capacity(stack); }
    @Override public boolean canExtract() { return false; }
    @Override public boolean canReceive() { return stack.getCount() == 1; }
}
