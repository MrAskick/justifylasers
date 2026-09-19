package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.*;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class Prompt6GameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void emitterSpectrumReplacesTheCrystalAndIsSecurePersistentAndPaid(TestContext c) {
        var pos = c.getAbsolutePos(new BlockPos(2, 3, 2));
        c.getWorld().setBlockState(pos, net.askcraft.justifylasers.registry.ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
        var emitter = (net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity)c.getWorld().getBlockEntity(pos);
        var player = c.createMockSurvivalPlayer(); player.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos)); emitter.initializeOwner(player);
        var stack = new ItemStack(net.askcraft.justifylasers.registry.ModLaserParts.MODULES.get(net.askcraft.justifylasers.energy.LaserModule.SPECTRUM));
        LaserSpectrum.color(stack, CrystalGrowth.PHOTONITE.spectrum()); emitter.setStack(0, stack);
        c.assertTrue(emitter.isValid(0, stack) && emitter.spectral() && emitter.beamRgb() == CrystalGrowth.PHOTONITE.spectrum(), "Spectrum replaces the crystal without losing its exact RGB");
        c.assertTrue(emitter.beamBehavior().spectral() && emitter.moduleFluxCost() > 0, "Installed conditioning pays the shared optical price");
        emitter.readNbt(emitter.createNbt());
        c.assertTrue(emitter.spectral() && emitter.beamRgb() == CrystalGrowth.PHOTONITE.spectrum(), "Spectrum survives inventory NBT");
        c.assertFalse(emitter.setSpectrum(player, "oops"), "Reject malformed RGB packets");
        c.assertTrue(emitter.setSpectrum(player, "35DFFF") && emitter.beamRgb() == 0x35DFFF, "Owner can choose a mixed color");
        player.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos).add(100,0,0));
        c.assertFalse(emitter.setSpectrum(player, "FFFFFF"), "A remote settings packet cannot change the emitter");
        c.getWorld().removeBlock(pos, false); player.discard(); c.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fourMixturesConsumeExactReagentsWaterAndDistinctEnergy(TestContext c) {
        var machine = machine(c, MachineKind.CHEMICAL_SYNTHESIZER);
        long previous = 0;
        for (var fluid : new ProcessFluid[]{ProcessFluid.AMETHYST, ProcessFluid.PHOTONITE, ProcessFluid.EMERALD, ProcessFluid.DIAMOND}) {
            machine.clear(); machine.restoreFluids(new IndustrialMachineBlockEntity.Fluids(ProcessFluid.WATER, 1000, ProcessFluid.WATER, 0));
            var recipe = IndustryRecipe.all(c.getWorld()).stream().filter(r -> r.kind() == machine.kind() && r.process().outputFluid() == fluid).findFirst().orElseThrow();
            for (int i = 0; i < recipe.inputs().size(); i++) { var stack = recipe.inputs().get(i).getMatchingStacks()[0].copy(); stack.setCount(recipe.counts().get(i)); machine.setStack(i, stack); }
            long energy = 0;
            for (int tick = 0; tick < recipe.duration(); tick++) { machine.energy().restore(1000); tick(machine); energy += 1000 - machine.energy().stored(); }
            c.assertTrue(energy == (long)recipe.rate() * recipe.duration() && energy > previous, "Exact FE cost rises with mixture value"); previous = energy;
            c.assertTrue(machine.water() == 0 && machine.fluid(1) == fluid && machine.fluidAmount(1) == 1000, "1000 mB in / 1000 mB out: " + fluid);
            for (int slot = 0; slot < 3; slot++) c.assertTrue(machine.getStack(slot).isEmpty(), "Every reagent consumed once");
            machine.setStack(IndustrialMachineBlockEntity.WATER_INPUT, new ItemStack(Items.BUCKET)); tick(machine);
            c.assertTrue(machine.getStack(IndustrialMachineBlockEntity.BUCKET_OUTPUT).isOf(fluid.bucket()) && machine.fluidAmount(1) == 0, "Product can be bottled");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void growingConsumesOneSeedAcrossWearStagesAndNeverOutputsVanillaGems(TestContext c) {
        var machine = machine(c, MachineKind.CRYSTAL_GROWER);
        for (var crystal : CrystalGrowth.values()) {
            machine.clear(); machine.readNbt(machine.createNbt());
            machine.restoreFluids(new IndustrialMachineBlockEntity.Fluids(crystal.nutrient(), 4000, ProcessFluid.WATER, 0));
            machine.setStack(0, new ItemStack(crystal.natural(), 2)); machine.energy().restore(1234);
            machine.receiveLight(LuminousFlux.MAX, crystal.spectrum(), LuminousFlux.MAX, crystal.spectrum());
            int cycles=0, previous=-1;
            do {
                tick(machine);
                cycles++;
                int stage=machine.getStack(2).isEmpty()?4:crystal.stage(machine.getStack(2));
                c.assertTrue(stage>previous && stage<=4, "A completed cycle wears or destroys the natural seed"); previous=stage;
                c.assertTrue(machine.getStack(0).getCount() == 1, "Stock does not replace a reusable active seed");
                var output = machine.getStack(4);
                c.assertTrue(output.isEmpty() || output.isOf(crystal.grown()), "Growth only produces an uncut intermediate");
                c.assertTrue(output.getCount() <= cycles * 3, "Maximum three crystals per paid cycle");
            } while (!machine.getStack(2).isEmpty() && cycles<4);
            c.assertTrue(machine.getStack(2).isEmpty() && machine.water() == 4000-cycles*1000 && machine.energy().stored() == 1234, "Only completed cycles spend fluid; no FE");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spectrumModuleAndMinimumFluxAreRequiredAndPausesSurviveReload(TestContext c) {
        var machine = machine(c, MachineKind.CRYSTAL_GROWER); var crystal = CrystalGrowth.DIAMOND;
        machine.setStack(0, new ItemStack(crystal.natural()));
        machine.fillFluid(crystal.nutrient(), 1000, false);
        machine.receiveLight(crystal.reference(), crystal.spectrum()); tick(machine);
        c.assertTrue(machine.status() == IndustrialMachineBlockEntity.Status.WRONG_SPECTRUM && machine.water() == 1000, "A matching color alone is not a conditioned spectrum");
        setLight(machine, crystal.minimum() - 1, crystal.spectrum()); tick(machine);
        c.assertTrue(machine.status() == IndustrialMachineBlockEntity.Status.LOW_FLUX && machine.progress() == 0, "Below the minimum, growth pauses");
        setLight(machine, crystal.reference(), crystal.spectrum()); for (int i = 0; i < 100; i++) tick(machine);
        c.assertTrue(machine.progress() == 100, "Reference flux gives exactly 1x speed");
        int remaining = machine.water(); var saved = machine.createNbt(); machine.readNbt(saved); tick(machine);
        c.assertTrue(machine.progress() == 100 && machine.water() == remaining && machine.lightFlux() == 0, "Reload preserves paid progress but not usable light");
        setLight(machine, crystal.reference(), 0xFF0000); tick(machine);
        c.assertTrue(machine.progress() == 100 && machine.water() == remaining, "Wrong spectrum pauses without loss");
        setLight(machine, crystal.reference(), crystal.spectrum()); tick(machine);
        c.assertTrue(machine.progress() == 101, "Correct spectrum resumes the same batch");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cutterRequiresBothIndependentEnergySupplies(TestContext c) {
        var machine = machine(c, MachineKind.LASER_CUTTER);
        for (var crystal : CrystalGrowth.values()) {
            machine.clear(); machine.readNbt(machine.createNbt()); machine.setStack(0, new ItemStack(crystal.grown()));
            var recipe = machine.recipe(); machine.energy().restore(1000); tick(machine);
            c.assertTrue(machine.progress() == 0 && machine.energy().stored() == 1000, "FE alone cannot cut");
            machine.energy().restore(0); machine.receiveLight(recipe.process().cuttingFlux(), crystal.spectrum()); tick(machine);
            c.assertTrue(machine.progress() == 0, "LM alone cannot move the cutting head");
            long energy = 0;
            for (int i = 0; i < recipe.duration(); i++) { machine.energy().restore(1000); tick(machine); energy += 1000 - machine.energy().stored(); }
            c.assertTrue(machine.getStack(4).isOf(crystal.natural()) && machine.getStack(4).getCount() == 1 && machine.getStack(0).isEmpty(), "One grown crystal gives one finished resource");
            c.assertTrue(CrystalSeed.synthetic(machine.getStack(4)) && crystal.stage(machine.getStack(4))<0, "Artificial gems cannot seed another growth cycle");
            c.assertTrue(energy == (long)recipe.rate() * recipe.duration(), "All motion ticks are paid");
        }
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fluidPortsRollbackNestedTransfersAndRejectMixing(TestContext c) {
        var machine = machine(c, MachineKind.CHEMICAL_SYNTHESIZER);
        for (Direction side : Direction.values()) {
            var port = FluidStorage.SIDED.find(c.getWorld(), machine.getPos(), side);
            c.assertTrue(port != null, "Fluid capability on every face");
            var before = machine.fluids();
            try (var outer = Transaction.openOuter()) {
                c.assertTrue(port.insert(FluidVariant.of(ProcessFluid.WATER.fluid()), 81_000, outer) == 81_000, "One bucket accepted");
                try (var nested = outer.openNested()) { port.insert(FluidVariant.of(ProcessFluid.WATER.fluid()), 81_000, nested); }
                c.assertTrue(machine.water() == 1000, "Nested abort restores the input amount");
                c.assertTrue(port.insert(FluidVariant.of(ProcessFluid.DIAMOND.fluid()), 81_000, outer) == 0, "Product cannot be inserted into the input tank");
            }
            c.assertTrue(machine.fluids().equals(before), "Outer abort restores the complete fluid state");
        }
        machine.restoreFluids(new IndustrialMachineBlockEntity.Fluids(ProcessFluid.WATER, 2000, ProcessFluid.AMETHYST, 1000));
        var port = FluidStorage.SIDED.find(c.getWorld(), machine.getPos(), Direction.UP);
        try (var tx = Transaction.openOuter()) { c.assertTrue(port.extract(FluidVariant.of(ProcessFluid.AMETHYST.fluid()), 81_000, tx) == 81_000, "Product is extractable"); tx.commit(); }
        c.assertTrue(machine.water() == 2000 && machine.fluidAmount(1) == 0, "Output extraction never drains process water");
        machine.readNbt(machine.createNbt()); c.assertTrue(machine.water() == 2000 && machine.fluid(1) == ProcessFluid.AMETHYST, "Fluid identity persists even when emptied");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void newAssemblySchematicsAndFiniteFluidBlocksAreRegistered(TestContext c) {
        for (String id : new String[]{"chemical_synthesizer", "laser_cutter", "spectrum_module"}) {
            c.assertTrue(ModIndustry.BLUEPRINTS.containsKey(id), "Tablet schematic exists: " + id);
            c.assertTrue(IndustryRecipe.all(c.getWorld()).stream().anyMatch(r -> r.kind() == MachineKind.ASSEMBLY_CHAMBER && r.blueprint().equals(id)), "Assembler recipe exists: " + id);
        }
        for (var fluid : ProcessFluid.values()) if (fluid != ProcessFluid.WATER) {
            var pos = new BlockPos(2, 2, 2); c.getWorld().setBlockState(c.getAbsolutePos(pos), fluid.fluid().getDefaultState().getBlockState());
            c.assertTrue(c.getWorld().getFluidState(c.getAbsolutePos(pos)).getFluid().matchesType(fluid.fluid()), "Placeable registered liquid: " + fluid);
        }
        c.complete();
    }

    private static void setLight(IndustrialMachineBlockEntity machine, long amount, int rgb) { machine.readNbt(machine.createNbt()); machine.receiveLight(amount, rgb, amount, rgb); }
    private static void tick(IndustrialMachineBlockEntity machine) { IndustrialMachineBlockEntity.tick(machine.getWorld(), machine.getPos(), machine.getCachedState(), machine); }
    private static IndustrialMachineBlockEntity machine(TestContext c, MachineKind kind) {
        var pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
        if (kind.multiblock()) for (var part : ChamberStructure.positions(pos)) c.getWorld().setBlockState(part, ModIndustry.MACHINES.get(kind).getDefaultState());
        else c.getWorld().setBlockState(pos, ModIndustry.MACHINES.get(kind).getDefaultState());
        var machine = (IndustrialMachineBlockEntity)c.getWorld().getBlockEntity(pos);
        if (kind.multiblock()) c.assertTrue(ChamberStructure.form(machine), "Machine forms");
        return machine;
    }
}
