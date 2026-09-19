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
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.industry.MachineRecipeData;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Locale;

final class IndustryJeiCategory implements IRecipeCategory<MachineRecipeData> {
    private final MachineKind kind;
    private final IDrawable icon, background;
    private final long bucketVolume;

    IndustryJeiCategory(MachineKind kind, IJeiHelpers helpers) {
        this.kind = kind;
        icon = helpers.getGuiHelper().createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModIndustry.MACHINES.get(kind)));
        background = helpers.getGuiHelper().createBlankDrawable(174, 136);
        bucketVolume = helpers.getPlatformFluidHelper().bucketVolume();
    }

    static RecipeType<MachineRecipeData> type(MachineKind kind) { return new RecipeType<>(JustifyLasers.id(kind.id()), MachineRecipeData.class); }
    @Override public RecipeType<MachineRecipeData> getRecipeType() { return type(kind); }
    @Override public Text getTitle() { return Text.translatable("block.justifylasers." + kind.id()); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public IDrawable getBackground() { return background; }
    @Override public int getWidth() { return 174; }
    @Override public int getHeight() { return 136; }
    @Override public Identifier getRegistryName(MachineRecipeData recipe) { return JustifyLasers.id("industry/" + recipe.key()); }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipeData recipe, IFocusGroup focus) {
        for (int slot = 0; slot < recipe.inputs().size(); slot++) {
            int amount = recipe.counts().get(slot);
            var alternatives = Arrays.stream(recipe.inputs().get(slot).getMatchingStacks()).map(stack -> {
                var copy = stack.copy(); copy.setCount(amount); return copy;
            }).toList();
            builder.addSlot(RecipeIngredientRole.INPUT, 8 + slot * 24, 38).addItemStacks(alternatives).setStandardSlotBackground();
        }
        if (recipe.process().outputFluid() == null) {
            var result = recipe.result().copy();
            if (recipe.process().cuttingFlux() > 0) net.askcraft.justifylasers.industry.CrystalSeed.syntheticResult(result);
            var output = builder.addSlot(RecipeIngredientRole.OUTPUT, 149, 38).addItemStack(result).setOutputSlotBackground();
            if (recipe.process().crystal() != null) output.addTooltipCallback((slot, tooltip) -> tooltip.add(Text.translatable("gui.justifylasers.industry.seed_yield")));
        } else builder.addSlot(RecipeIngredientRole.OUTPUT, 149, 10).addFluidStack(recipe.process().outputFluid().fluid(), recipe.waterCost() * bucketVolume / 1000)
                .setFluidRenderer(bucketVolume, false, 16, 44);
        if (!recipe.blueprint().isBlank() && ModIndustry.BLUEPRINTS.containsKey(recipe.blueprint()))
            builder.addSlot(RecipeIngredientRole.CATALYST, 8, 10).addItemStack(new ItemStack(ModIndustry.BLUEPRINTS.get(recipe.blueprint())))
                    .setStandardSlotBackground().addTooltipCallback((slot, tooltip) -> tooltip.add(Text.translatable("gui.justifylasers.industry.blueprint")));
        if (recipe.waterCost() > 0) {
            long amount = recipe.waterCost() * bucketVolume / 1000;
            // Fluid inputs remain searchable without being transferred into reagent slots.
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addFluidStack(recipe.process().inputFluid().fluid(), amount);
            builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 111, 10).addFluidStack(recipe.process().inputFluid().fluid(), amount)
                    .setFluidRenderer(Math.max(bucketVolume, amount), false, 16, 44);
        }
    }

    @Override public void draw(MachineRecipeData recipe, IRecipeSlotsView slots, DrawContext context, double mx, double my) {
        var font = MinecraftClient.getInstance().textRenderer;
        context.fillGradient(0, 0, 174, 136, 0xED163348, 0xEE081621);
        context.fill(0, 0, 174, 1, 0xFF4DB8D1);
        if (!recipe.blueprint().isBlank()) text(context, Text.translatable("gui.justifylasers.industry.blueprint_short"), 31, 15, 75, 0x9BCCDA);
        context.drawText(font, Text.literal("→"), 133, 43, 0xB1F5FF, false);
        text(context, Text.translatable("gui.justifylasers.industry.recipe_time", String.format(Locale.ROOT, "%.1f", recipe.duration() / 20d)), 8, 66, 158, 0xC4E6EB);
        text(context, kind == MachineKind.CRYSTAL_GROWER
                ? Text.translatable("gui.justifylasers.industry.growth_reference", net.askcraft.justifylasers.laser.LuminousFlux.format(recipe.rate()))
                : Text.literal(recipe.rate() + " " + Platform.ENERGY_UNIT + "/t · " + (long)recipe.rate() * recipe.duration() + " " + Platform.ENERGY_UNIT), 8, 79, 158, 0x6BC9D7);
        if (recipe.waterCost() > 0) text(context, Text.translatable("gui.justifylasers.industry.fluid_amount",
                recipe.process().inputFluid() == net.askcraft.justifylasers.industry.ProcessFluid.WATER ? Text.translatable("block.minecraft.water")
                        : Text.translatable("block.justifylasers." + recipe.process().inputFluid().fluidId()), recipe.waterCost()), 8, 92, 158, 0x839BEF);
        else if (!recipe.blueprint().isBlank()) text(context, Text.translatable("gui.justifylasers.industry.blueprint_reusable"), 8, 92, 158, 0x839DAF);
        if (recipe.process().crystal() != null) {
            text(context, Text.translatable("gui.justifylasers.industry.growth_requirements", Text.translatable("gui.justifylasers.spectrum." + recipe.process().growth()),
                    net.askcraft.justifylasers.laser.LuminousFlux.format(recipe.process().minimumFlux())), 8, 105, 158, 0xB5DEE8);
            text(context, Text.translatable("gui.justifylasers.industry.hue_range", recipe.process().crystal().hueMin(), recipe.process().crystal().hueMax()), 8, 118, 158, 0x91ACBD);
        } else if (recipe.process().cuttingFlux() > 0) text(context, Text.translatable("gui.justifylasers.industry.cutting_flux",
                net.askcraft.justifylasers.laser.LuminousFlux.format(recipe.process().cuttingFlux())), 8, 105, 158, 0xB5DEE8);
    }

    private static void text(DrawContext context, Text text, int x, int y, int width, int rgb) {
        var font = MinecraftClient.getInstance().textRenderer;
        float scale = Math.min(1, width / (float)Math.max(1, font.getWidth(text)));
        var matrices = context.getMatrices(); matrices.push(); matrices.translate(x, y, 0); matrices.scale(scale, scale, 1);
        context.drawText(font, text, 0, 0, rgb, false); matrices.pop();
    }
}
