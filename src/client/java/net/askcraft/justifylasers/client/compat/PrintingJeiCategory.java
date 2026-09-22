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
import net.askcraft.justifylasers.client.render.PrintedModelRenderer;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.industry.ProcessFluid;
import net.askcraft.justifylasers.printing.PrintData;
import net.askcraft.justifylasers.printing.PrintDesign;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

final class PrintingJeiCategory implements IRecipeCategory<PrintDesign> {
    private final MachineKind kind;
    private final IDrawable icon,background;
    private final long bucket;
    PrintingJeiCategory(MachineKind kind,IJeiHelpers helpers){
        this.kind=kind;icon=helpers.getGuiHelper().createDrawableIngredient(VanillaTypes.ITEM_STACK,new ItemStack(ModIndustry.MACHINES.get(kind)));
        background=helpers.getGuiHelper().createBlankDrawable(174,136);bucket=helpers.getPlatformFluidHelper().bucketVolume();
    }
    static RecipeType<PrintDesign> type(MachineKind kind){return new RecipeType<>(JustifyLasers.id(kind.id()),PrintDesign.class);}
    @Override public RecipeType<PrintDesign> getRecipeType(){return type(kind);}
    @Override public Text getTitle(){return Text.translatable("block.justifylasers."+kind.id());}
    @Override public IDrawable getIcon(){return icon;}
    @Override public IDrawable getBackground(){return background;}
    @Override public int getWidth(){return 174;}
    @Override public int getHeight(){return 136;}
    @Override public void setRecipe(IRecipeLayoutBuilder builder,PrintDesign model,IFocusGroup focus){
        boolean encoder=kind==MachineKind.MODEL_ENCODER;
        builder.addSlot(encoder?RecipeIngredientRole.INPUT:RecipeIngredientRole.CATALYST,8,18)
                .addItemStack(encoder?new ItemStack(ModIndustry.BLANK_SCHEMATIC):PrintData.schematic(model)).setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.OUTPUT,149,18).addItemStack(encoder?PrintData.schematic(model):PrintData.printed(model)).setOutputSlotBackground();
        if(!encoder)builder.addSlot(RecipeIngredientRole.INPUT,8,45).addFluidStack(ProcessFluid.PHOTOPOLYMER.fluid(),model.cost().polymer()*bucket/1000)
                .setFluidRenderer(bucket,false,16,30);
    }
    @Override public void draw(PrintDesign model,IRecipeSlotsView slots,DrawContext context,double mx,double my){
        context.fill(0,0,174,136,0xEF081B27);PrintedModelRenderer.preview(context,model,51,0,75,35,25,false);
        var font=MinecraftClient.getInstance().textRenderer;
        var cost=model.cost();int y=80;
        String stats=kind==MachineKind.MODEL_ENCODER?"2 000 FE":cost.energyPerTick()+" FE/t · "+cost.polymer()+" mB · "+cost.ticks()/20d+" s";
        context.drawText(font,Text.literal(stats),7,y,0xA1D1DA,false);
        if(kind!=MachineKind.MODEL_ENCODER)context.drawText(font,Text.literal(String.format(java.util.Locale.ROOT,"%.1f klm",cost.lumens()/1000d)+" · 260–305°"),7,y+12,0xC6A3F0,false);
        for(var line:font.wrapLines(Text.translatable("gui.justifylasers.printing.example"),160)){context.drawText(font,line,7,y+25,0x7296AB,false);y+=10;}
    }
}
