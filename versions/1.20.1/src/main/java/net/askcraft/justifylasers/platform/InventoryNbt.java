package net.askcraft.justifylasers.platform;

import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;

public record InventoryNbt() {
    public void write(NbtCompound nbt, DefaultedList<ItemStack> stacks) {
        Inventories.writeNbt(nbt, stacks);
    }

    public void read(NbtCompound nbt, DefaultedList<ItemStack> stacks) {
        Inventories.readNbt(nbt, stacks);
    }
}
