package net.askcraft.justifylasers.integration;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.minecraft.util.math.Direction;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public final class IntegrationEnergy {
    public static net.minecraft.server.network.ServerPlayerEntity serverPlayer(net.minecraft.test.TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        player.setUuid(java.util.UUID.randomUUID());
        return player;
    }

    public static net.minecraft.entity.player.PlayerEntity player(net.minecraft.test.TestContext context) {
        return context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
    }

    public static long receive(LaserEmitterBlockEntity emitter, int amount, boolean simulate) {
        var port = emitter.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(() -> new AssertionError("Missing energy capability"));
        return port.receiveEnergy(amount, simulate);
    }

    public static java.util.function.LongUnaryOperator extractor(
            net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity receiver, Direction side) {
        var port = receiver.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
        if (port == null) return null;
        return amount -> port.extractEnergy((int) amount, false);
    }


    public static void industrialPorts(net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity member, Direction side) {
        var fluid = member.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
        var items = member.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
        if (fluid == null || items == null) throw new AssertionError("Missing native industrial ports");
        var simulate = net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
        var execute = net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        var water = new net.minecraftforge.fluids.FluidStack(net.minecraft.fluid.Fluids.WATER, 1000);
        if (fluid.fill(water, simulate) != 1000 || member.water() != 0) throw new AssertionError("Simulated fill mutated the tank");
        if (fluid.fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.fluid.Fluids.LAVA, 1000), execute) != 0)
            throw new AssertionError("Grower accepted lava");
        if (fluid.fill(water, execute) != 1000 || member.water() != 1000) throw new AssertionError("Native water fill failed");
        if (fluid.drain(250, simulate).getAmount() != 250 || member.water() != 1000) throw new AssertionError("Simulated drain mutated the tank");
        if (fluid.drain(250, execute).getAmount() != 250) throw new AssertionError("Native drain failed");
        var raw = new net.minecraft.item.ItemStack(net.askcraft.justifylasers.registry.ModIndustry.RAW_PHOTONIC_CRYSTAL);
        if (!items.insertItem(0, raw, true).isEmpty() || !member.getStack(0).isEmpty()) throw new AssertionError("Simulated insertion mutated inventory");
        if (!items.insertItem(0, raw, false).isEmpty()) throw new AssertionError("Native item insertion failed");
        if (!items.extractItem(0, 1, false).isEmpty()) throw new AssertionError("Input face allowed extraction");
    }

    public static void tablet() {
        var stack = new net.minecraft.item.ItemStack(net.askcraft.justifylasers.registry.ModIndustry.EXTRATERRESTRIAL_TABLET);
        var port = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        if (port == null || port.getEnergyStored() != 0) throw new AssertionError("Missing/discharged native tablet storage");
        if (port.receiveEnergy(1000, true) != 256 || port.getEnergyStored() != 0) throw new AssertionError("Tablet transfer simulation/limit");
        if (port.receiveEnergy(100, false) != 100 || port.extractEnergy(100, false) != 0 || port.getEnergyStored() != 100)
            throw new AssertionError("Native tablet charge/extraction");
        if (net.askcraft.justifylasers.item.ExtraterrestrialTabletItem.charge(stack.copy()) != 100)
            throw new AssertionError("Native tablet charge did not persist in the item");
    }

    public static void roundTrip(net.askcraft.justifylasers.platform.LaserBlockEntity emitter) {
        var saved = emitter.createNbt(emitter.getWorld().getRegistryManager());
        emitter.readNbt(saved, emitter.getWorld().getRegistryManager());
    }
}
