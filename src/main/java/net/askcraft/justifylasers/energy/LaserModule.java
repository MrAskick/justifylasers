package net.askcraft.justifylasers.energy;

import java.util.Locale;

public enum LaserModule {
    // Slot order is persisted in existing worlds.
    SILK_TOUCH, BLOCK_DROPS, SCORCH_MARKS, IGNITION,
    BLOCK_DESTRUCTION, ENTITY_DAMAGE, RANGE, THICKNESS, TARGET_FILTER,
    ENTITY_HEAL, ENTITY_LIFT, ENTITY_LOWER, BLOCK_COLLECTION, SPECTRUM;

    public int maxCount() {
        return this == RANGE || this == THICKNESS ? 64 : 1;
    }

    public int slot() {
        return this == SPECTRUM ? 0 : this == BLOCK_COLLECTION ? 19 : isEffect() ? ENTITY_DAMAGE.ordinal() + 1 : ordinal() + 1;
    }

    public boolean isEffect() { return isEntityMode() || this == BLOCK_DESTRUCTION; }

    public boolean isEntityMode() {
        return this == ENTITY_DAMAGE || this == ENTITY_HEAL || this == ENTITY_LIFT || this == ENTITY_LOWER;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT) + "_module";
    }
}
