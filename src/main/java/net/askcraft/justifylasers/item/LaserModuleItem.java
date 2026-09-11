package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.energy.LaserModule;

public class LaserModuleItem extends LaserPartItem {
    private final LaserModule module;
    private final int rangePerItem;

    public LaserModuleItem(Settings settings, LaserModule module) {
        this(settings, module, module == LaserModule.RANGE ? 1 : 0);
    }

    public LaserModuleItem(Settings settings, LaserModule module, int rangePerItem) {
        super(settings, module == LaserModule.RANGE && rangePerItem == 8 ? "advanced_range_module" : module.id());
        this.module = module;
        this.rangePerItem = rangePerItem;
    }

    public LaserModule module() {
        return module;
    }

    public int rangePerItem() {
        return rangePerItem;
    }
}
