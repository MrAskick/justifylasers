package net.askcraft.justifylasers.client.compat;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.industry.CrystalGrowth;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class GrowthSeedJeiCategory implements IRecipeCategory<CrystalGrowth> {
    static final RecipeType<CrystalGrowth> TYPE = new RecipeType<>(JustifyLasers.id("growth_seeds"), CrystalGrowth.class);
    private final IDrawable icon, background;

    GrowthSeedJeiCategory(IJeiHelpers helpers) {
        icon = helpers.getGuiHelper().createDrawableIngredient(VanillaTypes.ITEM_STACK, CrystalGrowth.DIAMOND.seed(0));
        background = helpers.getGuiHelper().createBlankDrawable(138, 54);
    }
    @Override public RecipeType<CrystalGrowth> getRecipeType() { return TYPE; }
    @Override public Text getTitle() { return Text.translatable("gui.justifylasers.growth_seeds"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public IDrawable getBackground() { return background; }
    @Override public int getWidth() { return 138; }
    @Override public int getHeight() { return 54; }
    @Override public Identifier getRegistryName(CrystalGrowth crystal) { return JustifyLasers.id("growth_seed/" + crystal.id()); }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, CrystalGrowth crystal, IFocusGroup focus) {
        builder.addSlot(RecipeIngredientRole.INPUT, 8, 8).addItemStack(new ItemStack(crystal.natural())).setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.INPUT, 40, 8).addItemStack(new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL)).setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.OUTPUT, 110, 8).addItemStack(crystal.seed(0)).setOutputSlotBackground();
    }
    @Override public void draw(CrystalGrowth crystal, IRecipeSlotsView slots, DrawContext context, double mx, double my) {
        var font = MinecraftClient.getInstance().textRenderer;
        context.drawText(font, Text.literal("+"), 28, 12, 0x245A66, false);
        context.drawText(font, Text.literal("→"), 78, 12, 0x245A66, false);
        context.drawText(font, Text.translatable("gui.justifylasers.seed_shapeless"), 8, 37, 0x384653, false);
    }
}
