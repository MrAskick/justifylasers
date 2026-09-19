package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.industry.IndustryRecipes;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class IndustrialMachineGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void opticalGrowthScalesBelowAndAboveTheReferenceWithoutRoundingLoss(TestContext context) {
        var grower = machine(context, MachineKind.CRYSTAL_GROWER, 2);
        context.assertTrue(LaserConfig.get().crystalGrowthFlux == 480_000, "Default 1x reference is 480 klm");
        for (int flux : new int[]{16_000, 120_000, 240_000, 480_000, 960_000, 1_920_000}) {
            grower.clear(); grower.readNbt(grower.createNbt());
            grower.setStack(0, new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL));
            grower.setStack(1, new ItemStack(Items.QUARTZ, 2));
            grower.restoreWater(1_000); grower.energy().restore(1234);
            grower.receiveLight(flux, 0x24FF91);
            int duration = (int)((long)grower.recipe().duration() * grower.recipe().rate() / flux);
            ticks(grower, duration - 1);
            context.assertTrue(grower.getStack(4).isEmpty() && grower.completion(0) > 0, "No early result at " + flux);
            ticks(grower, 1);
            context.assertTrue(grower.getStack(4).isOf(ModIndustry.PHOTONITE_CRYSTAL), "Exact scaled finish at " + flux + " lm / " + duration + " ticks");
            context.assertTrue(grower.water() == 0 && grower.getStack(0).isEmpty() && grower.getStack(1).isEmpty(), "One batch consumes exactly one set of inputs");
            context.assertTrue(grower.energy().stored() == 1234, "FE remains unrelated to growth");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fractionalGrowthSurvivesReloadAndHighFluxNeverDuplicatesABatch(TestContext context) {
        var grower = machine(context, MachineKind.CRYSTAL_GROWER, 2);
        grower.setStack(0, new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL, 2));
        grower.setStack(1, new ItemStack(Items.QUARTZ, 4)); grower.restoreWater(2_000);
        grower.receiveLight(1, 0xDD4422); ticks(grower, 1000);
        var saved = grower.createNbt();
        context.assertTrue(grower.progress() == 0 && saved.getLong("GrowthRemainder") == 1000, "Even 1 lm accumulates exact work");
        grower.readNbt(saved); ticks(grower, 10);
        context.assertTrue(grower.createNbt().getLong("GrowthRemainder") == 1000 && grower.lightFlux() == 0, "Reload preserves work, not spendable light");
        grower.receiveLight(LuminousFlux.MAX, 0xDD4422); ticks(grower, 1);
        context.assertTrue(grower.getStack(4).getCount() == 1 && grower.water() == 1000 && grower.getStack(0).getCount() == 1, "Huge input completes at most one paid batch per tick");
        grower.setStack(4, new ItemStack(ModIndustry.PHOTONITE_CRYSTAL, 64)); ticks(grower, 4);
        context.assertTrue(grower.water() == 1000 && grower.progress() == 0, "Full output cannot consume light-work materials");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oreLootUsesRawMaterialsAndRespectsSilkTouch(TestContext context) {
        var pickaxe = new ItemStack(Items.IRON_PICKAXE);
        var silk = new ItemStack(Items.DIAMOND_PICKAXE);
        silk.addEnchantment(net.minecraft.enchantment.Enchantments.SILK_TOUCH, 1);
        var pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
        ModIndustry.ORES.forEach((id, block) -> {
            var raw = id.contains("wolframite") ? ModIndustry.RAW_WOLFRAMITE : ModIndustry.RAW_PHOTONIC_CRYSTAL;
            var drops = net.minecraft.block.Block.getDroppedStacks(block.getDefaultState(), context.getWorld(), pos, null, null, pickaxe);
            context.assertTrue(drops.size() == 1 && drops.get(0).isOf(raw) && drops.get(0).getCount() == 1, "Raw drop for " + id);
            var preserved = net.minecraft.block.Block.getDroppedStacks(block.getDefaultState(), context.getWorld(), pos, null, null, silk);
            context.assertTrue(preserved.size() == 1 && preserved.get(0).isOf(block.asItem()), "Silk Touch preserves " + id);
        });
        context.complete();
    }

    private IndustrialMachineBlockEntity machine(TestContext context, MachineKind kind, int x) {
        context.setBlockState(x, 2, 2, ModIndustry.MACHINES.get(kind));
        var machine = (IndustrialMachineBlockEntity) context.getBlockEntity(new BlockPos(x, 2, 2));
        if (kind.multiblock()) {
            for (var pos : net.askcraft.justifylasers.industry.ChamberStructure.positions(machine.getPos()))
                context.getWorld().setBlockState(pos, ModIndustry.MACHINES.get(kind).getDefaultState());
            context.assertTrue(net.askcraft.justifylasers.industry.ChamberStructure.form(machine), "Eight matching casings form one chamber");
        }
        return machine;
    }
    private void ticks(IndustrialMachineBlockEntity machine, int count) {
        for (int i = 0; i < count; i++) IndustrialMachineBlockEntity.tick(machine.getWorld(), machine.getPos(), machine.getCachedState(), machine);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void generatorExportsRealEnergyAndReturnsFuelContainers(TestContext context) {
        var generator = machine(context, MachineKind.FUEL_GENERATOR, 1);
        var consumer = machine(context, MachineKind.ASSEMBLY_CHAMBER, 2);
        generator.setStack(0, new ItemStack(Items.LAVA_BUCKET));
        int generated = 0;
        for (int i = 0; i < 100; i++) { ticks(generator, 1); generated += generator.generatedRate(); }
        context.assertTrue(generated > 0 && consumer.energy().stored() == generated, "Adjacent machine receives exactly the dynamic thermal output");
        context.assertTrue(generator.getStack(4).isOf(Items.BUCKET) && generator.getStack(0).isEmpty(), "Fuel container is returned exactly once");
        generator.energy().restore(generator.energy().capacity());
        int fuel = generator.fuel();
        ticks(generator, 1);
        context.assertTrue(generator.fuel() == fuel, "Full buffer pauses fuel consumption");
        context.assertFalse(generator.acceptsEnergy(), "Generator cannot become an energy input loop");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void generatorChargesTabletWithoutCreatingEnergy(TestContext context) {
        var generator = machine(context, MachineKind.FUEL_GENERATOR, 2);
        var tablet = new ItemStack(ModIndustry.EXTRATERRESTRIAL_TABLET);
        generator.setStack(IndustrialMachineBlockEntity.WATER_INPUT, tablet);
        generator.setStack(0, new ItemStack(Items.COAL));
        int generated = 0;
        for (int i = 0; i < 100; i++) { ticks(generator, 1); generated += generator.generatedRate(); }
        context.assertTrue(generated > 0 && net.askcraft.justifylasers.item.ExtraterrestrialTabletItem.charge(tablet) == generated, "Tablet receives only paid thermal energy, including fractional production");
        context.assertTrue(generator.energy().stored() == 0 && generator.getStack(0).isEmpty(), "No duplicate buffer energy or repeated fuel item");
        context.assertFalse(net.minecraft.registry.Registries.BLOCK.containsId(JustifyLasers.id("electric_smelter")), "Electric smelter removed");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pausesOutputBlockingAndNbtReloadPreserveTheBatch(TestContext context) {
        var grower = machine(context, MachineKind.CRYSTAL_GROWER, 2);
        grower.setStack(0, new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL));
        grower.setStack(1, new ItemStack(Items.QUARTZ, 2));
        grower.fillWater(1000, false);
        grower.energy().restore(100_000);
        grower.receiveLight(grower.rate(), 0xFFFFFF);
        ticks(grower, 10);
        var saved = grower.createNbt();
        var restored = new IndustrialMachineBlockEntity(grower.getPos(), grower.getCachedState());
        restored.readNbt(saved);
        context.assertTrue(restored.progress() == 10 && restored.energy().stored() == grower.energy().stored() && restored.getStack(0).getCount() == 1, "Saved progress, energy and inventory survive reload");
        int energy = grower.energy().stored();
        grower.toggle(); ticks(grower, 5);
        context.assertTrue(grower.progress() == 10 && grower.energy().stored() == energy, "Pause consumes no energy");
        grower.toggle();
        grower.setStack(4, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.WHITE), 64));
        ticks(grower, 5);
        context.assertTrue(grower.progress() == 10 && grower.energy().stored() == energy, "Blocked output does not drain energy or consume inputs");
        grower.removeStack(4); grower.energy().restore(0);
        var withoutLight = grower.createNbt(); grower.readNbt(withoutLight); ticks(grower, 3);
        context.assertTrue(grower.progress() == 10 && grower.getStack(0).getCount() == 1, "No-power pause preserves the batch");
        grower.removeStack(0); ticks(grower, 1);
        context.assertTrue(grower.progress() == 10, "Removing an ingredient pauses paid progress");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rawCrystalMustBeGrownBeforeTheFirstLaserCanBeAssembled(TestContext context) {
        var grower = machine(context, MachineKind.CRYSTAL_GROWER, 1);
        grower.setStack(0, new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL)); grower.setStack(1, new ItemStack(Items.QUARTZ, 2));
        grower.fillWater(1000, false);
        grower.receiveLight(grower.rate(), 0xFFFFFF); ticks(grower, grower.kind().duration());
        context.assertTrue(grower.getStack(4).isOf(ModIndustry.PHOTONITE_CRYSTAL), "Grower produces a bare photonite crystal");
        context.assertTrue(grower.water() == 0, "Exactly one bucket of water was consumed");
        var assembler = machine(context, MachineKind.ASSEMBLY_CHAMBER, 4);
        assembler.setStack(IndustrialMachineBlockEntity.BLUEPRINT, new ItemStack(ModIndustry.BLUEPRINTS.get("powered_laser_emitter")));
        context.assertFalse(assembler.isValid(2, new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL)), "Raw crystals cannot bypass growth");
        assembler.setStack(0, new ItemStack(ModIndustry.COMPONENTS.get("reinforced_laser_housing"))); assembler.setStack(1, new ItemStack(ModIndustry.COMPONENTS.get("optical_resonator")));
        var crafting = new net.minecraft.inventory.CraftingInventory(context.createMockCreativeServerPlayerInWorld().playerScreenHandler, 2, 2);
        crafting.setStack(0, grower.removeStack(4)); crafting.setStack(1, new ItemStack(ModIndustry.CRYSTAL_MOUNT));
        var mounting = context.getWorld().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, crafting, context.getWorld()).orElseThrow();
        assembler.setStack(2, mounting.craft(crafting, context.getWorld().getRegistryManager())); assembler.setStack(3, new ItemStack(ModIndustry.COMPONENTS.get("energy_core")));
        var recipe = assembler.recipe();
        for (int tick = 0; tick < recipe.duration(); tick++) {
            assembler.energy().restore(assembler.energy().capacity());
            ticks(assembler, 1);
        }
        var output = assembler.getStack(4);
        context.assertTrue(output.isOf(ModBlocks.POWERED_LASER_EMITTER_ITEM), "Assembly creates a powered emitter");
        context.assertTrue(GameVersion.itemData(output).getInt(IndustryRecipes.INSTALLED_CRYSTAL) == LaserColor.WHITE.ordinal(), "Installed crystal survives as item data");
        context.assertTrue(assembler.calibration() > 0 && assembler.getStack(2).isEmpty(), "Crystal consumed once and calibration event started");
        context.assertTrue(assembler.getStack(IndustrialMachineBlockEntity.BLUEPRINT).getCount() == 1, "Blueprint is not consumed");
        context.setBlockState(7, 2, 2, ModBlocks.POWERED_LASER_EMITTER);
        var pos = context.getAbsolutePos(new BlockPos(7, 2, 2));
        ModBlocks.POWERED_LASER_EMITTER.onPlaced(context.getWorld(), pos, ModBlocks.POWERED_LASER_EMITTER.getDefaultState(), null, output);
        var emitter = (net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity) context.getBlockEntity(new BlockPos(7, 2, 2));
        context.assertTrue(emitter.getStack(0).isOf(ModLaserParts.CRYSTALS.get(LaserColor.WHITE)), "Placing installs the supplied crystal");
        context.assertFalse(emitter.isValid(0, new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL)), "Emitter rejects raw crystals too");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void automationAndRegistryDataExposeTheIntendedProgression(TestContext context) {
        var assembler = machine(context, MachineKind.ASSEMBLY_CHAMBER, 2);
        for (Direction side : Direction.values()) context.assertTrue(java.util.Arrays.stream(assembler.getAvailableSlots(side)).anyMatch(slot -> slot == 4), "Every face exposes output");
        context.assertFalse(assembler.canInsert(4, new ItemStack(ModBlocks.POWERED_LASER_EMITTER_ITEM), Direction.UP), "Output cannot be inserted");
        context.assertFalse(assembler.canExtract(0, new ItemStack(ModIndustry.LASER_CHASSIS), Direction.UP), "Automation cannot steal reserved inputs");
        context.assertTrue(context.getWorld().getRecipeManager().get(JustifyLasers.id("powered_laser_emitter")).isEmpty(), "No workbench shortcut for the powered emitter");
        var features = context.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.PLACED_FEATURE);
        context.assertTrue(features.get(JustifyLasers.id("ore_wolframite")) != null && features.get(JustifyLasers.id("ore_photonic_crystal")) != null, "Both ore generators decode and register");
        context.complete();
    }
}
