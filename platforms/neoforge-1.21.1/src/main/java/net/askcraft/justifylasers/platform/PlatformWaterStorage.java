package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.minecraft.fluid.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class PlatformWaterStorage implements IFluidHandler {
    private final IndustrialMachineBlockEntity machine;
    public PlatformWaterStorage(IndustrialMachineBlockEntity machine) { this.machine = machine; }
    @Override public int getTanks() { return 1; }
    @Override public FluidStack getFluidInTank(int tank) { return tank == 0 && machine.water() > 0 ? new FluidStack(Fluids.WATER, machine.water()) : FluidStack.EMPTY; }
    @Override public int getTankCapacity(int tank) { return tank == 0 ? machine.tankCapacity() : 0; }
    @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && stack.getFluid() == Fluids.WATER; }
    @Override public int fill(FluidStack stack, FluidAction action) { return !machine.isPrivate() && stack.getFluid() == Fluids.WATER ? machine.fillWater(stack.getAmount(), action.simulate()) : 0; }
    @Override public FluidStack drain(FluidStack stack, FluidAction action) { return stack.getFluid() == Fluids.WATER ? drain(stack.getAmount(), action) : FluidStack.EMPTY; }
    @Override public FluidStack drain(int maximum, FluidAction action) {
        int amount = machine.isPrivate() ? 0 : machine.drainWater(maximum, action.simulate());
        return amount > 0 ? new FluidStack(Fluids.WATER, amount) : FluidStack.EMPTY;
    }
}
