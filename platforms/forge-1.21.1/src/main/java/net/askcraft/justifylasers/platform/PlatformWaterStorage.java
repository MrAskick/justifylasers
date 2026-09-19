package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.ProcessFluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public final class PlatformWaterStorage implements IFluidHandler {
    private final IndustrialMachineBlockEntity machine;
    public PlatformWaterStorage(IndustrialMachineBlockEntity machine) { this.machine = machine; }
    @Override public int getTanks() { return machine.tankCount(); }
    @Override public FluidStack getFluidInTank(int tank) {
        return tank >= 0 && tank < getTanks() && machine.fluidAmount(tank) > 0
                ? new FluidStack(machine.fluid(tank).fluid(), machine.fluidAmount(tank)) : FluidStack.EMPTY;
    }
    @Override public int getTankCapacity(int tank) { return tank >= 0 && tank < getTanks() ? machine.tankCapacity() : 0; }
    @Override public boolean isFluidValid(int tank, FluidStack stack) {
        var fluid = ProcessFluid.ofFluid(stack.getFluid());
        return tank == 0 && fluid != null && machine.acceptsFluid(fluid);
    }
    @Override public int fill(FluidStack stack, FluidAction action) {
        return !machine.isPrivate() && isFluidValid(0, stack) ? machine.fillFluid(ProcessFluid.ofFluid(stack.getFluid()), stack.getAmount(), action.simulate()) : 0;
    }
    @Override public FluidStack drain(FluidStack stack, FluidAction action) {
        int tank = getTanks() - 1;
        return tank >= 0 && machine.fluid(tank).fluid() == stack.getFluid() ? drain(stack.getAmount(), action) : FluidStack.EMPTY;
    }
    @Override public FluidStack drain(int maximum, FluidAction action) {
        int tank = getTanks() - 1;
        if (tank < 0 || machine.isPrivate()) return FluidStack.EMPTY;
        var fluid = machine.fluid(tank).fluid();
        int amount = machine.drainFluid(tank, maximum, action.simulate());
        return amount > 0 ? new FluidStack(fluid, amount) : FluidStack.EMPTY;
    }
}
