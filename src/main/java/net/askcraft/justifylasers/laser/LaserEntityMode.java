package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.energy.LaserModule;

public enum LaserEntityMode {
    NONE, DAMAGE, HEAL, LIFT, LOWER, MINING;

    public static LaserEntityMode of(LaserModule module) {
        if (module == null) return NONE;
        return switch (module) {
            case ENTITY_DAMAGE -> DAMAGE;
            case ENTITY_HEAL -> HEAL;
            case ENTITY_LIFT -> LIFT;
            case ENTITY_LOWER -> LOWER;
            case BLOCK_DESTRUCTION -> MINING;
            default -> NONE;
        };
    }

    public boolean movesEntities() { return this == LIFT || this == LOWER; }
    public String translationKey() { return "gui.justifylasers.entity_mode." + name().toLowerCase(java.util.Locale.ROOT); }
}
