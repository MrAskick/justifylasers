package net.askcraft.justifylasers.item;

import net.minecraft.item.Item;

public final class AssemblyBlueprintItem extends Item {
    private final String recipe;
    public AssemblyBlueprintItem(Settings settings, String recipe) { super(settings); this.recipe = recipe; }
    public String recipe() { return recipe; }
    public net.minecraft.item.ItemStack output(net.minecraft.world.World world) {
        if (world != null) {
            var match = net.askcraft.justifylasers.industry.IndustryRecipe.all(world).stream()
                    .filter(value -> recipe.equals(value.blueprint())).findFirst();
            if (match.isPresent()) return match.get().result().copy();
        }
        return net.minecraft.registry.Registries.ITEM.get(net.askcraft.justifylasers.JustifyLasers.id(recipe)).getDefaultStack();
    }
}
