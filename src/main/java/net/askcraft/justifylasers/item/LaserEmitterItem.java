package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.config.LaserConfig;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class LaserEmitterItem extends BlockItem {
    private final boolean powered;

    public LaserEmitterItem(Block block, Settings settings, boolean powered) {
        super(block, settings);
        this.powered = powered;
    }

    @Override
    public Text getName(ItemStack stack) {
        return !powered && LaserConfig.technicalMode()
                ? Text.translatable("block.justifylasers.creative_laser_emitter") : super.getName(stack);
    }
}
