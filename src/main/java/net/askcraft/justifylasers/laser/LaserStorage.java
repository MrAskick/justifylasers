package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.platform.GameVersion;
import net.minecraft.item.ItemStack;

import java.util.List;

public final class LaserStorage {
    /** Never mutates the incoming stack; components/NBT remain attached to both stored and overflow items. */
    public static ItemStack insert(List<ItemStack> slots, int from, int to, ItemStack incoming) {
        ItemStack remaining = incoming.copy();
        for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
            for (int i = from; i < to && !remaining.isEmpty(); i++) {
                ItemStack stored = slots.get(i);
                if (pass == 0 && !stored.isEmpty() && GameVersion.canStack(stored, remaining)) {
                    int count = Math.min(remaining.getCount(), Math.min(64, stored.getMaxCount()) - stored.getCount());
                    if (count > 0) { stored.increment(count); remaining.decrement(count); }
                } else if (pass == 1 && stored.isEmpty()) {
                    slots.set(i, remaining.split(Math.min(64, remaining.getMaxCount())));
                }
            }
        }
        return remaining;
    }
    private LaserStorage() { }
}
