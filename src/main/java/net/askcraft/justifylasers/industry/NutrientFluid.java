package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.registry.ModNutrients;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.item.Item;
import net.minecraft.state.StateManager;
import net.minecraft.world.World;

/** Water-like transport without infinite-source conversion or mixing between nutrient types. */
public class NutrientFluid extends WaterFluid {
    protected final ProcessFluid nutrient;
    private final boolean flowing;

    public NutrientFluid(ProcessFluid nutrient, boolean flowing) {
        this.nutrient = nutrient; this.flowing = flowing;
    }
    @Override protected void appendProperties(StateManager.Builder<Fluid, FluidState> builder) { super.appendProperties(builder); builder.add(LEVEL); }
    @Override public Fluid getStill() { return ModNutrients.STILL.get(nutrient); }
    @Override public Fluid getFlowing() { return ModNutrients.FLOWING.get(nutrient); }
    @Override public Item getBucketItem() { return nutrient.bucket(); }
    @Override public boolean matchesType(Fluid fluid) { return fluid == getStill() || fluid == getFlowing(); }
    @Override protected boolean isInfinite(World world) { return false; }
    @Override public boolean isStill(FluidState state) { return !flowing; }
    @Override public int getLevel(FluidState state) { return flowing ? state.get(LEVEL) : 8; }
    @Override public BlockState toBlockState(FluidState state) { return ModNutrients.BLOCKS.get(nutrient).getDefaultState().with(FluidBlock.LEVEL, getBlockStateLevel(state)); }
}
