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
import net.askcraft.justifylasers.item.LaserAmplifierItem;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class AmplifierJeiCategory implements IRecipeCategory<AmplifierJeiCategory.Upgrade> {
    record Upgrade(int tier) { }
    static final RecipeType<Upgrade> TYPE = new RecipeType<>(JustifyLasers.id("amplifier_upgrades"), Upgrade.class);
    private final IDrawable icon, background;

    AmplifierJeiCategory(IJeiHelpers helpers) {
        icon = helpers.getGuiHelper().createDrawableIngredient(VanillaTypes.ITEM_STACK, LaserAmplifierItem.stack(1));
        background = helpers.getGuiHelper().createBlankDrawable(156,88);
    }
    @Override public RecipeType<Upgrade> getRecipeType() { return TYPE; }
    @Override public Text getTitle() { return Text.translatable("gui.justifylasers.amplifier_upgrades"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public IDrawable getBackground() { return background; }
    @Override public int getWidth() { return 156; }
    @Override public int getHeight() { return 88; }
    @Override public Identifier getRegistryName(Upgrade upgrade) { return JustifyLasers.id("amplifier/level_"+upgrade.tier); }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, Upgrade upgrade, IFocusGroup focus) {
        for(int i=0;i<9;i++) builder.addSlot(RecipeIngredientRole.INPUT,8+i%3*18,8+i/3*18)
                .addItemStack(i==4?new ItemStack(ModLaserParts.CONTROL_CIRCUIT):LaserAmplifierItem.stack(upgrade.tier-1)).setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.OUTPUT,123,26).addItemStack(LaserAmplifierItem.stack(upgrade.tier)).setOutputSlotBackground();
    }
    @Override public void draw(Upgrade upgrade, IRecipeSlotsView slots, DrawContext context, double mx, double my) {
        var font=MinecraftClient.getInstance().textRenderer;
        context.drawText(font,Text.literal("→"),87,30,0xB1F5FF,false);
        context.drawText(font,Text.literal("+"+net.askcraft.justifylasers.laser.LuminousFlux.format(net.askcraft.justifylasers.energy.AmplifierTier.lumens(upgrade.tier))),8,69,0xB1F5FF,false);
    }
}
