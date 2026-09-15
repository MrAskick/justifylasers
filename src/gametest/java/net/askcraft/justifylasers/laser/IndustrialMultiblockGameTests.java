package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.ChamberStructure;
import net.askcraft.justifylasers.industry.IndustryRecipe;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class IndustrialMultiblockGameTests implements FabricGameTest {
    private IndustrialMachineBlockEntity chamber(TestContext context, MachineKind kind) {
        BlockPos origin = context.getAbsolutePos(new BlockPos(2, 2, 2));
        var positions = ChamberStructure.positions(origin);
        for (var pos : positions) context.getWorld().setBlockState(pos, ModIndustry.MACHINES.get(kind).getDefaultState());
        var controller = (IndustrialMachineBlockEntity)context.getWorld().getBlockEntity(origin);
        context.assertTrue(ChamberStructure.form(controller), "Eight casing blocks form a chamber");
        return controller;
    }

    private void tick(IndustrialMachineBlockEntity machine, int amount) {
        for (int i = 0; i < amount; i++) IndustrialMachineBlockEntity.tick(machine.getWorld(), machine.getPos(), machine.getCachedState(), machine);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void partialStructuresRejectPortsAndMixedCasings(TestContext context) {
        var pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
        var state = ModIndustry.MACHINES.get(MachineKind.CRYSTAL_GROWER).getDefaultState();
        for (var part : ChamberStructure.positions(pos)) context.getWorld().setBlockState(part, state);
        context.getWorld().setBlockState(pos.add(1, 1, 1), ModIndustry.MACHINES.get(MachineKind.ASSEMBLY_CHAMBER).getDefaultState());
        var machine = (IndustrialMachineBlockEntity)context.getWorld().getBlockEntity(pos);
        context.assertFalse(ChamberStructure.form(machine), "Different casings cannot form a chamber");
        context.assertFalse(machine.acceptsEnergy(), "Partial structure cannot accept energy");
        context.assertTrue(machine.fillWater(1000, false) == 0 && machine.getAvailableSlots(Direction.UP).length == 0, "Partial structure has no fluid or item ports");
        context.getWorld().setBlockState(pos.add(1, 1, 1), state);
        context.assertTrue(ChamberStructure.form(machine), "Completing the structure enables it");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativeFluidAndItemTransactionsShareOneController(TestContext context) {
        var master = chamber(context, MachineKind.CRYSTAL_GROWER);
        var member = (IndustrialMachineBlockEntity)context.getWorld().getBlockEntity(master.getPos().add(1,1,1));
        context.assertTrue(member.energy() == master.energy(), "Parts share one energy buffer");
        var fluid = FluidStorage.SIDED.find(context.getWorld(), member.getPos(), Direction.EAST);
        context.assertTrue(fluid != null, "Native fluid port is discoverable on a non-controller part");
        try (var transaction = Transaction.openOuter()) {
            context.assertTrue(fluid.insert(FluidVariant.of(Fluids.WATER), 81_000, transaction) == 81_000, "One bucket accepted");
            context.assertTrue(fluid.insert(FluidVariant.of(Fluids.LAVA), 81_000, transaction) == 0, "Tank rejects lava");
        }
        context.assertTrue(master.water() == 0, "Aborted fluid transaction restores the tank");
        try (var transaction = Transaction.openOuter()) { fluid.insert(FluidVariant.of(Fluids.WATER), 81_000, transaction); transaction.commit(); }
        context.assertTrue(master.water() == 1000 && member.water() == 1000, "Committed bucket reaches the shared tank");
        var items = ItemStorage.SIDED.find(context.getWorld(), member.getPos(), Direction.UP);
        context.assertTrue(items != null, "Native item port is discoverable");
        try (var transaction = Transaction.openOuter()) {
            context.assertTrue(items.insert(ItemVariant.of(ModIndustry.RAW_PHOTONIC_CRYSTAL), 2, transaction) == 2, "Raw material accepted");
            context.assertTrue(items.insert(ItemVariant.of(Items.QUARTZ), 4, transaction) == 4, "Quartz routed to its ingredient slot");
            transaction.commit();
        }
        context.assertTrue(master.getStack(0).getCount() == 2 && master.getStack(1).getCount() == 4, "Native item automation reaches the master");
        try (var transaction = Transaction.openOuter()) {
            context.assertTrue(items.extract(ItemVariant.of(ModIndustry.RAW_PHOTONIC_CRYSTAL), 2, transaction) == 0, "Inputs remain reserved");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dismantlingPausesWithoutDuplicatingState(TestContext context) {
        var machine = chamber(context, MachineKind.CRYSTAL_GROWER);
        machine.setStack(0, new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL)); machine.setStack(1, new ItemStack(Items.QUARTZ, 2));
        machine.energy().restore(100_000); machine.fillWater(2000, false); tick(machine, 12);
        int energy = machine.energy().stored(), water = machine.water();
        context.assertTrue(machine.progress() == 12 && water < 2000, "Growth consumes water gradually");
        var saved = machine.createNbt();
        var restored = new IndustrialMachineBlockEntity(machine.getPos(), machine.getCachedState()); restored.readNbt(saved);
        context.assertTrue(restored.water() == water && restored.progress() == 12 && machine.origin().equals(restored.origin()), "NBT retains tank, origin and progress");
        var removed = machine.getPos().add(1, 1, 1);
        context.getWorld().breakBlock(removed, false);
        tick(machine, 30);
        context.assertFalse(machine.formed(), "Breaking any member dismantles the chamber");
        context.assertTrue(machine.progress() == 12 && machine.energy().stored() == energy && machine.water() == water, "Disassembled chamber consumes nothing");
        context.getWorld().setBlockState(removed, machine.getCachedState());
        context.assertTrue(ChamberStructure.form(machine), "Replacing the casing repairs the chamber");
        tick(machine, machine.duration() - 12);
        context.assertTrue(machine.water() == 1000 && machine.getStack(4).isOf(ModIndustry.PHOTONITE_CRYSTAL), "Repair resumes the same batch and charges water once");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void waterBucketsAndDryPausesCannotCreateFreeCrystals(TestContext context) {
        var machine = chamber(context, MachineKind.CRYSTAL_GROWER);
        machine.setStack(0,new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL)); machine.setStack(1,new ItemStack(Items.QUARTZ,2));
        machine.energy().restore(100_000); tick(machine, 5);
        context.assertTrue(machine.status() == IndustrialMachineBlockEntity.Status.NO_WATER && machine.energy().stored() == 100_000, "Dry chamber does not use power");
        machine.setStack(IndustrialMachineBlockEntity.WATER_INPUT,new ItemStack(Items.WATER_BUCKET)); tick(machine, 1);
        context.assertTrue(machine.getStack(6).isEmpty() && machine.getStack(7).isOf(Items.BUCKET), "Exactly one empty bucket is returned");
        tick(machine, machine.duration() - 1);
        context.assertTrue(machine.water() == 0 && machine.getStack(4).getCount() == 1 && machine.getStack(7).getCount() == 1, "Water and inputs are consumed exactly once");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blueprintsAndFurnaceRecipesDescribeTheNewProgression(TestContext context) {
        var recipes = IndustryRecipe.all(context.getWorld());
        for (var entry : ModIndustry.BLUEPRINTS.entrySet()) {
            context.assertTrue(recipes.stream().anyMatch(recipe -> recipe.blueprint().equals(entry.getKey())), "Blueprint has a real machine recipe: " + entry.getKey());
            context.assertTrue(context.getWorld().getRecipeManager().get(JustifyLasers.id(entry.getKey())).isEmpty(), "No workbench bypass: " + entry.getKey());
        }
        for (String id : new String[]{"fuel_generator", "electric_smelter"})
            context.assertTrue(context.getWorld().getRecipeManager().get(JustifyLasers.id(id)).isEmpty(), "WIP machine has no recipe");
        var smelting = (net.minecraft.recipe.SmeltingRecipe)context.getWorld().getRecipeManager().get(JustifyLasers.id("wolframite_ingot")).orElseThrow();
        context.assertTrue(smelting.getCookTime() == 3000, "Normal furnace takes 150 seconds per ingot");
        var assembler = chamber(context,MachineKind.ASSEMBLY_CHAMBER);
        assembler.energy().restore(100_000); assembler.setStack(0,new ItemStack(ModIndustry.LASER_CHASSIS)); tick(assembler,20);
        context.assertTrue(assembler.status() == IndustrialMachineBlockEntity.Status.NO_BLUEPRINT && assembler.energy().stored() == 100_000, "No work without a schematic");
        context.complete();
    }
}
