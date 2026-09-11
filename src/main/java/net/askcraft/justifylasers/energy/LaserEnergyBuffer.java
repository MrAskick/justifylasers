package net.askcraft.justifylasers.energy;

import java.util.function.IntSupplier;

public final class LaserEnergyBuffer {
    private final IntSupplier capacity;
    private final IntSupplier inputLimit;
    private final Runnable changed;
    private int stored;

    public LaserEnergyBuffer(IntSupplier capacity, IntSupplier inputLimit, Runnable changed) {
        this.capacity = capacity;
        this.inputLimit = inputLimit;
        this.changed = changed;
    }

    public int stored() {
        return Math.min(stored, capacity());
    }

    public int capacity() {
        return Math.max(0, capacity.getAsInt());
    }

    public int receive(long requested, boolean simulate) {
        int accepted = (int) Math.max(0L, Math.min(requested, Math.min((long) capacity() - stored(), inputLimit.getAsInt())));
        if (!simulate && accepted > 0) {
            stored = stored() + accepted;
            changed.run();
        }
        return accepted;
    }

    public boolean consume(int amount) {
        if (amount < 0 || stored() < amount) return false;
        if (amount > 0) {
            stored = stored() - amount;
            changed.run();
        }
        return true;
    }

    public void restore(long amount) {
        stored = (int) Math.max(0, Math.min(amount, capacity()));
    }
}
