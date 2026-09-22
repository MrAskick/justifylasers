package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.industry.IndustryRecipe;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public final class AssemblyBlueprintItem extends Item {
    public static final String RECIPE = "AssemblyRecipe";
    // Non-empty only on load-only aliases for saves made before alpha.38.
    private final String recipe;
    public AssemblyBlueprintItem(Settings settings, String recipe) { super(settings); this.recipe = recipe; }
    public String recipe() { return recipe; }
    public static String recipe(ItemStack stack) {
        if (!(stack.getItem() instanceof AssemblyBlueprintItem item)) return "";
        String value = item.recipe.isEmpty() ? GameVersion.itemData(stack).getString(RECIPE) : item.recipe;
        return ModIndustry.BLUEPRINTS.containsKey(value) ? value : "";
    }
    public static ItemStack stack(String recipe) {
        if (!ModIndustry.BLUEPRINTS.containsKey(recipe)) throw new IllegalArgumentException("Unknown assembly schematic: " + recipe);
        var stack = new ItemStack(ModIndustry.ASSEMBLY_BLUEPRINT);
        var data = GameVersion.itemData(stack);
        data.putString(RECIPE, recipe);
        GameVersion.setItemData(stack, data);
        return stack;
    }
    public static ItemStack migrate(ItemStack stack) {
        if (!(stack.getItem() instanceof AssemblyBlueprintItem item) || item.recipe.isEmpty()) return stack;
        var result = GameVersion.replaceItem(stack, ModIndustry.ASSEMBLY_BLUEPRINT);
        var data = GameVersion.itemData(result);
        data.putString(RECIPE, item.recipe);
        GameVersion.setItemData(result, data);
        return result;
    }
    @Override public ItemStack getDefaultStack() {
        return recipe.isEmpty() ? super.getDefaultStack() : ModIndustry.BLUEPRINTS.containsKey(recipe)
                ? stack(recipe) : new ItemStack(ModIndustry.BLANK_SCHEMATIC);
    }
    @Override public Text getName(ItemStack stack) {
        String value = recipe(stack);
        return Text.translatable(value.isEmpty() ? "item.justifylasers.assembly_blueprint" : "item.justifylasers." + value + "_blueprint");
    }
    public ItemStack output(World world) { return output(recipe, world); }
    public static ItemStack output(ItemStack stack, World world) { return output(recipe(stack), world); }
    private static ItemStack output(String recipe, World world) {
        if (recipe.isEmpty()) return ItemStack.EMPTY;
        if (world != null) {
            var match = IndustryRecipe.all(world).stream()
                    .filter(value -> recipe.equals(value.blueprint())).findFirst();
            if (match.isPresent()) return match.get().result().copy();
        }
        return Registries.ITEM.get(JustifyLasers.id(recipe)).getDefaultStack();
    }
    @Override public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (recipe.isEmpty() || world.isClient || !(entity instanceof PlayerEntity player)) return;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i) == stack) {
            inventory.setStack(i, migrate(stack)); inventory.markDirty(); break;
        }
    }
}
