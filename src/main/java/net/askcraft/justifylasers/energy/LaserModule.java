package net.askcraft.justifylasers.energy;

import java.util.Locale;

public enum LaserModule {
    // Slot order is persisted in existing worlds.
    SILK_TOUCH, BLOCK_DROPS, SCORCH_MARKS, IGNITION,
    BLOCK_DESTRUCTION, ENTITY_DAMAGE, RANGE, THICKNESS;

    public int maxCount() {
        return this == RANGE || this == THICKNESS ? 64 : 1;
    }

    public int slot() {
        return ordinal() + 1;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT) + "_module";
    }
}
