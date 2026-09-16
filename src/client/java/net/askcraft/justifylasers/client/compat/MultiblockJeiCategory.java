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
import net.askcraft.justifylasers.client.render.MultiblockPreviewRenderer;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.industry.MachineKind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MultiblockJeiCategory implements IRecipeCategory<MultiblockConstruction> {
    static final RecipeType<MultiblockConstruction> TYPE = new RecipeType<>(JustifyLasers.id("multiblock_construction"), MultiblockConstruction.class);
    private final IDrawable icon, background;
    private final Map<String, Integer> layers = new HashMap<>();

    MultiblockJeiCategory(IJeiHelpers helpers) {
        icon = helpers.getGuiHelper().createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModIndustry.MACHINES.get(MachineKind.ASSEMBLY_CHAMBER)));
        background = helpers.getGuiHelper().createBlankDrawable(238, 166);
    }

    @Override public RecipeType<MultiblockConstruction> getRecipeType() { return TYPE; }
    @Override public Text getTitle() { return Text.translatable("gui.justifylasers.jei.construction"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public IDrawable getBackground() { return background; }
    @Override public int getWidth() { return 238; }
    @Override public int getHeight() { return 166; }
    @Override public Identifier getRegistryName(MultiblockConstruction recipe) { return JustifyLasers.id("construction/" + recipe.id()); }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, MultiblockConstruction recipe, IFocusGroup focuses) {
        var ingredients = recipe.ingredients();
        for (int i = 0; i < ingredients.size(); i++)
            builder.addSlot(RecipeIngredientRole.INPUT, 8 + i * 24, 122).addItemStack(ingredients.get(i)).setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.OUTPUT, 211, 122).addItemStack(recipe.result()).setOutputSlotBackground();
    }

    @Override public void draw(MultiblockConstruction recipe, IRecipeSlotsView slots, DrawContext context, double mx, double my) {
        var font = MinecraftClient.getInstance().textRenderer;
        context.fillGradient(0, 0, 238, 166, 0xF0163348, 0xF0081621);
        context.fill(0, 0, 238, 1, 0xFF4DB8D1);
        String title = recipe.id().equals("solar_concentrator") ? "gui.justifylasers.solar.title" : "block.justifylasers." + recipe.id();
        text(context, Text.translatable(title), 8, 7, 220, 0xBBECF1);
        int layer = layers.getOrDefault(recipe.id(), -1);
        MultiblockPreviewRenderer.render(context, recipe, layer, 153, 66);
        text(context, Text.literal(recipe.width() + " × " + recipe.layers() + " × " + recipe.width()
                + (recipe.id().equals("solar_concentrator") ? " + 4" : "")), 8, 24, 85, 0x6BC9D7);
        text(context, Text.translatable("gui.justifylasers.jei.top_view"), 8, 38, 86, 0x839DAF);
        int shownLayer = layer < 0 ? 0 : layer;
        for (var cell : recipe.cells()) if (cell.pos().getY() == shownLayer) {
            int px = gridX(recipe,cell.pos().getX()), py = gridY(recipe,cell.pos().getZ()), size=gridSize(recipe);
            context.fill(px - 1, py - 1, px + size + 1, py + size + 1, 0xFF23526A);
            context.getMatrices().push();context.getMatrices().translate(px,py,0);context.getMatrices().scale(size/16F,size/16F,1);
            context.drawItem(new ItemStack(cell.block()), 0, 0);context.getMatrices().pop();
        }
        context.fill(106, 98, 229, 113, mx >= 106 && mx < 229 && my >= 98 && my < 113 ? 0xFF315F75 : 0xFF17384B);
        text(context, Text.literal("‹ ").append(layer < 0 ? Text.translatable("gui.justifylasers.jei.all_layers")
                : Text.translatable("gui.justifylasers.jei.layer", layer + 1, recipe.layers())).append(" ›"), 112, 101, 111, 0xB1F5FF);
        text(context, Text.translatable("gui.justifylasers.jei." + (recipe.id().equals("solar_concentrator") ? "solar_assembly" : "chamber_assembly")),
                8, 146, 222, 0x839DAF);
    }

    @Override public boolean handleInput(MultiblockConstruction recipe, double mx, double my, InputUtil.Key input) {
        if (input.getCategory() != InputUtil.Type.MOUSE || input.getCode() != 0 || mx < 106 || mx >= 229 || my < 98 || my >= 113) return false;
        int current = layers.getOrDefault(recipe.id(), -1);
        layers.put(recipe.id(), Math.floorMod(current + 1 + (mx < 166 ? -1 : 1), recipe.layers() + 1) - 1);
        return true;
    }

    @Override public List<Text> getTooltipStrings(MultiblockConstruction recipe, IRecipeSlotsView slots, double mx, double my) {
        int layer = Math.max(0, layers.getOrDefault(recipe.id(), -1));
        for (var cell : recipe.cells()) {
            int px = gridX(recipe,cell.pos().getX()), py = gridY(recipe,cell.pos().getZ()), size=gridSize(recipe);
            if (cell.pos().getY() == layer && mx >= px && mx < px + size && my >= py && my < py + size)
                return List.of(cell.block().getName(), Text.translatable("gui.justifylasers.jei.layer", layer + 1, recipe.layers()));
        }
        return List.of();
    }

    private static void text(DrawContext context, Text text, int x, int y, int width, int color) {
        var font = MinecraftClient.getInstance().textRenderer;
        float scale = Math.min(1, width / (float) Math.max(1, font.getWidth(text)));
        context.getMatrices().push(); context.getMatrices().translate(x, y, 0); context.getMatrices().scale(scale, scale, 1);
        context.drawText(font, text, 0, 0, color, false); context.getMatrices().pop();
    }
    private static int gridSize(MultiblockConstruction recipe){return recipe.id().equals("solar_concentrator")?12:16;}
    private static int gridX(MultiblockConstruction recipe,int x){return recipe.id().equals("solar_concentrator")?12+(x+1)*16:12+x*22;}
    private static int gridY(MultiblockConstruction recipe,int z){return recipe.id().equals("solar_concentrator")?46+(z+1)*14:64+z*18;}
}
