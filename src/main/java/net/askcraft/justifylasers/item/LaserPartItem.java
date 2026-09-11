package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.item.BlockItem;

public class LaserPartItem extends BlockItem {
    private final String partId;

    public LaserPartItem(Settings settings, String partId) {
        super(ModLaserParts.DECORATIONS.get(partId), settings);
        this.partId = partId;
    }

    public String partId() {
        return partId;
    }

    @Override
    public String getTranslationKey() {
        return "item.justifylasers." + partId;
    }
}
