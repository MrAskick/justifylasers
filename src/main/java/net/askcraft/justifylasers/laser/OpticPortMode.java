package net.askcraft.justifylasers.laser;

public enum OpticPortMode {
    INPUT, OUTPUT, DISABLED;

    public OpticPortMode cycle(boolean reverse) {
        return values()[Math.floorMod(ordinal() + (reverse ? -1 : 1), values().length)];
    }

    public static OpticPortMode byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : DISABLED;
    }
}
