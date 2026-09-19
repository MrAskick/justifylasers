package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.energy.LaserModule;

public class LaserModuleItem extends LaserPartItem {
    private final LaserModule module;
    private final int rangePerItem;

    public LaserModuleItem(Settings settings, LaserModule module) {
        this(settings, module, module == LaserModule.RANGE ? 1 : 0);
    }

    public LaserModuleItem(Settings settings, LaserModule module, int rangePerItem) {
        super(settings, rangePerItem == 8 && (module == LaserModule.RANGE || module == LaserModule.THICKNESS) ? "advanced_" + module.id() : module.id());
        this.module = module;
        this.rangePerItem = rangePerItem;
    }

    public LaserModule module() {
        return module;
    }

    @Override public net.minecraft.text.Text getName(net.minecraft.item.ItemStack stack) {
        return super.getName(stack).copy().styled(style -> style.withColor(0x50DBD0));
    }

    public int rangePerItem() {
        return rangePerItem;
    }
    public int upgradeUnits() { return Math.max(1, rangePerItem); }
}
