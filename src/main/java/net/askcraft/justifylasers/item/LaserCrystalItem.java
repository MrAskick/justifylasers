package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.laser.LaserColor;

public class LaserCrystalItem extends LaserPartItem {
    private final LaserColor color;

    public LaserCrystalItem(Settings settings, LaserColor color) {
        super(settings, color.asString() + "_crystal");
        this.color = color;
    }

    public LaserColor color() {
        return color;
    }
}
