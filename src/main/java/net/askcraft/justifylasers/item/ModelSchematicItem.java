package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.printing.PrintData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class ModelSchematicItem extends Item {
    public ModelSchematicItem(Settings settings) { super(settings.maxCount(1)); }
    @Override public Text getName(ItemStack stack) {
        var model = PrintData.read(stack);
        return model == null || model.name().isBlank() ? super.getName(stack)
                : Text.translatable("item.justifylasers.model_schematic.named", model.name());
    }
}
