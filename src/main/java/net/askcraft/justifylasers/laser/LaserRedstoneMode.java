package net.askcraft.justifylasers.laser;

public enum LaserRedstoneMode {
    IGNORE("gui.justifylasers.redstone.ignore"),
    HIGH("gui.justifylasers.redstone.high"),
    LOW("gui.justifylasers.redstone.low");

    private static final LaserRedstoneMode[] VALUES = values();

    private final String translationKey;

    LaserRedstoneMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public boolean allows(boolean powered) {
        return switch (this) {
            case IGNORE -> true;
            case HIGH -> powered;
            case LOW -> !powered;
        };
    }

    public String translationKey() {
        return translationKey;
    }

    public LaserRedstoneMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static LaserRedstoneMode byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }
}
