package net.askcraft.justifylasers.platform;

import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

/** Native item ports share the same slot policy as hoppers and Fabric's inventory fallback. */
public interface AutomatedInventory extends SidedInventory {
    @Override default int[] getAvailableSlots(Direction side) { return java.util.stream.IntStream.range(0, size()).toArray(); }
    @Override default boolean canInsert(int slot, ItemStack stack, Direction side) { return isValid(slot, stack); }
    @Override default boolean canExtract(int slot, ItemStack stack, Direction side) { return slot >= 0 && slot < size(); }
}
