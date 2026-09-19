package net.askcraft.justifylasers.laser;

import net.minecraft.item.ItemStack;

public interface LaserLootCollector {
    /** Inserts what fits and returns the remainder; never discards overflow. */
    ItemStack collect(ItemStack stack);
}
