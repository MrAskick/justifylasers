package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.LaserComponentBlock;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.industry.ChamberStructure;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.industry.SolarStructure;
import net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.screen.TabletScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class SolarIndustryGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar_industry")
    public void registeredFactoryRestoresSmallCollectorRatherThanPlainComponent(TestContext context) {
        var source = small(context, new BlockPos(2,2,2), Direction.WEST);
        source.selectOutput(Direction.SOUTH);
        var restored = net.askcraft.justifylasers.registry.ModBlockEntities.LASER_COMPONENT.instantiate(source.getPos(), source.getCachedState());
        context.assertTrue(restored instanceof SolarConcentratorBlockEntity, "Chunk deserialization must instantiate the solar subclass");
        restored.readNbt(source.createNbt()); restored.setWorld(context.getWorld());
        var solar = (SolarConcentratorBlockEntity) restored;
        context.assertTrue(solar.small() && solar.outputSide() == Direction.SOUTH && solar.operationalStructure(), "Settings survive restoration");
        context.assertTrue(solar.opticalBudget() == 0, "Saved flux is never a new spendable allocation");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar_industry")
    public void workbenchBootstrapNeedsNoSchematicOrExternalMod(TestContext context) {
        var inventory = new CraftingInventory(context.createMockCreativeServerPlayerInWorld().playerScreenHandler, 3, 3);
        for (int slot : new int[]{0, 2, 6, 8}) inventory.setStack(slot, new ItemStack(Items.IRON_INGOT));
        for (int slot : new int[]{1, 3, 5, 7}) inventory.setStack(slot, new ItemStack(Items.COPPER_INGOT));
        inventory.setStack(4, new ItemStack(Items.REDSTONE));
        context.assertTrue(craft(context, inventory).isOf(ModIndustry.ELECTRIC_MOTOR), "Workbench motor bootstraps the generator");
        inventory.clear();
        inventory.setStack(1, new ItemStack(Items.BLAST_FURNACE));
        for (int slot : new int[]{3, 5, 7}) inventory.setStack(slot, new ItemStack(ModIndustry.WOLFRAMITE_INGOT));
        inventory.setStack(4, new ItemStack(ModIndustry.ELECTRIC_MOTOR));
        context.assertTrue(craft(context, inventory).isOf(ModIndustry.MACHINES.get(MachineKind.FUEL_GENERATOR).asItem()), "Requested generator pattern");
        inventory.clear();
        for (int slot : new int[]{1, 3, 5, 7}) inventory.setStack(slot, new ItemStack(Items.GLASS));
        for (int slot : new int[]{6, 8}) inventory.setStack(slot, new ItemStack(ModIndustry.WOLFRAMITE_INGOT));
        inventory.setStack(4, new ItemStack(ModIndustry.PHOTONITE_CRYSTAL));
        context.assertTrue(craft(context, inventory).isOf(ModIndustry.LASER_ABSORBING_GLASS.asItem()), "Same glass ingredients, now in a workbench");
        context.assertFalse(ModIndustry.BLUEPRINTS.containsKey("laser_absorbing_glass"), "No obsolete glass schematic");
        context.assertTrue(new LaserConfig().enablesEnergy(id -> false), "No technical mods required");
        context.assertTrue(context.getWorld().getRecipeManager().get(JustifyLasers.id("industry/wolframite_smelting")).isEmpty(), "Smelter recipe removed");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar_industry")
    public void growthMenuSynchronizesHugeOpticalFluxWithoutIntegerOverflow(TestContext context) {
        var grower = chamber(context, MachineKind.CRYSTAL_GROWER, new BlockPos(2,2,2));
        grower.receiveLight(LuminousFlux.MAX, 0xFFFFFF);
        var player = context.createMockSurvivalPlayer();
        var server = new net.askcraft.justifylasers.screen.IndustrialMachineScreenHandler(1, player.getInventory(), grower);
        var client = new net.askcraft.justifylasers.screen.IndustrialMachineScreenHandler(1, player.getInventory(), grower.getPos());
        server.addListener(new net.minecraft.screen.ScreenHandlerListener() {
            @Override public void onSlotUpdate(net.minecraft.screen.ScreenHandler handler, int slot, ItemStack stack) { }
            @Override public void onPropertyUpdate(net.minecraft.screen.ScreenHandler handler, int property, int value) { client.setProperty(property, (short)value); }
        });
        server.sendContentUpdates();
        context.assertTrue(client.lightFlux() == LuminousFlux.MAX, "Four signed network shorts preserve the complete LM value");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar_industry")
    public void coalBuildsAndChargesATabletWhichCanRecordItsOwnSchematic(TestContext context) {
        var assembler = chamber(context, MachineKind.ASSEMBLY_CHAMBER, new BlockPos(3, 2, 2));
        var generatorPos = assembler.getPos().west();
        context.getWorld().setBlockState(generatorPos, ModIndustry.MACHINES.get(MachineKind.FUEL_GENERATOR).getDefaultState());
        var generator = (IndustrialMachineBlockEntity) context.getWorld().getBlockEntity(generatorPos);
        generator.setStack(0, new ItemStack(Items.COAL, 4));
        assembler.setStack(IndustrialMachineBlockEntity.BLUEPRINT, new ItemStack(ModIndustry.BLUEPRINTS.get("extraterrestrial_tablet")));
        assembler.setStack(0, new ItemStack(ModLaserParts.CONTROL_CIRCUIT));
        assembler.setStack(1, new ItemStack(Items.ENDER_EYE));
        assembler.setStack(2, new ItemStack(ModIndustry.WOLFRAMITE_INGOT));
        assembler.setStack(3, new ItemStack(Items.GOLD_INGOT));
        for (int tick = 0; tick < 3000 && assembler.getStack(4).isEmpty(); tick++) { tick(generator); tick(assembler); }
        var tablet = assembler.removeStack(4);
        context.assertTrue(tablet.isOf(ModIndustry.EXTRATERRESTRIAL_TABLET), "Coal generator can warm up and fund the first tablet without prefilled buffers");
        context.assertTrue(ExtraterrestrialTabletItem.charge(tablet) == 0, "New tablet is depleted");
        generator.setStack(IndustrialMachineBlockEntity.WATER_INPUT, tablet);
        for (int tick = 0; tick < 80; tick++) tick(generator);
        int charge = ExtraterrestrialTabletItem.charge(tablet);
        context.assertTrue(charge >= ExtraterrestrialTabletItem.WRITE_COST * 2, "Charging slot works directly from fuel");
        var player = context.createMockSurvivalPlayer();
        player.getInventory().setStack(0, generator.removeStack(IndustrialMachineBlockEntity.WATER_INPUT));
        player.getInventory().setStack(1, new ItemStack(ModIndustry.BLANK_SCHEMATIC, 2));
        var menu = new TabletScreenHandler(1, player.getInventory(), BlockPos.ORIGIN);
        for (String id : new String[]{"extraterrestrial_tablet", "small_solar_concentrator"}) {
            var schematic = ModIndustry.BLUEPRINTS.get(id);
            context.assertTrue(menu.onButtonClick(player, menu.blueprints().indexOf(schematic)), "Select " + id);
            context.assertTrue(menu.onButtonClick(player, TabletScreenHandler.RECORD), "Record " + id);
            context.assertTrue(player.getInventory().count(schematic) == 1, "Exactly one tradable copy");
        }
        context.assertTrue(player.getInventory().count(ModIndustry.BLANK_SCHEMATIC) == 0, "Two blanks consumed");
        context.assertTrue(menu.charge() == charge - 2 * ExtraterrestrialTabletItem.WRITE_COST, "Two paid recordings, no free duplication");
        context.assertTrue(assembler.getStack(IndustrialMachineBlockEntity.BLUEPRINT).getCount() == 1, "Original found schematic remains reusable");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar_industry")
    public void smallCollectorUsesTheRequestedAssemblyInputs(TestContext context) {
        var machine = chamber(context, MachineKind.ASSEMBLY_CHAMBER, new BlockPos(2, 2, 2));
        machine.setStack(IndustrialMachineBlockEntity.BLUEPRINT, new ItemStack(ModIndustry.BLUEPRINTS.get("small_solar_concentrator")));
        machine.setStack(0, new ItemStack(ModIndustry.SOLAR_ABSORBER, 3));
        machine.setStack(1, new ItemStack(ModIndustry.ELECTRIC_MOTOR));
        machine.setStack(2, new ItemStack(Items.IRON_INGOT, 3));
        var recipe = machine.recipe();
        machine.energy().restore(100_000);
        for (int tick = 0; tick < recipe.duration(); tick++) tick(machine);
        context.assertTrue(machine.getStack(4).isOf(ModIndustry.SMALL_SOLAR_CONCENTRATOR.asItem()), "Assembly creates the single-block collector");
        context.assertTrue(machine.energy().stored() == 100_000 - 56_320, "Deliberately inexpensive early assembly cost");
        for (int slot = 0; slot < 3; slot++) context.assertTrue(machine.getStack(slot).isEmpty(), "Exact ingredient count consumed");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar_industry")
    public void growthPortsAcceptAllLowerSidesButNeverForgeEnergy(TestContext context) {
        var grower = chamber(context, MachineKind.CRYSTAL_GROWER, new BlockPos(2, 2, 2));
        var origin = Vec3d.of(grower.origin());
        for (var pos : ChamberStructure.positions(grower.origin())) {
            var member = (IndustrialMachineBlockEntity) context.getWorld().getBlockEntity(pos);
            context.assertFalse(member.acceptsEnergy(), "Every casing rejects FE");
        }
        for (Direction side : Direction.Type.HORIZONTAL) for (double across : new double[]{.5, 1.5}) {
            double plane = side.getDirection() == Direction.AxisDirection.POSITIVE ? 2 : 0;
            var point = origin.add(side.getAxis() == Direction.Axis.X ? new Vec3d(plane, .5, across) : new Vec3d(across, .5, plane));
            context.assertTrue(grower.acceptsLaser(side, point), "Lower port accepts " + side + "/" + across);
            context.assertFalse(grower.acceptsLaser(side, point.add(0, 1, 0)), "Upper casing is not a lens");
        }
        context.assertFalse(grower.acceptsLaser(Direction.UP, origin.add(1, 2, 1)), "Top is not an optical input");
        grower.setStack(0, new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL));
        grower.setStack(1, new ItemStack(Items.QUARTZ, 2));
        grower.fillWater(1000, false); grower.energy().restore(100_000);
        tick(grower);
        context.assertTrue(grower.progress() == 0 && grower.water() == 1000, "Stored legacy FE cannot grow crystals");
        grower.receiveLight(grower.rate() / 2, 0xFF0000); grower.receiveLight(grower.rate() / 2, 0x0000FF); tick(grower);
        context.assertTrue(grower.progress() == 1 && grower.energy().stored() == 100_000, "Two beams aggregate as LM; no FE consumed");
        var mixed = new LightMixture(); mixed.add(0xFF0000, 1); mixed.add(0x0000FF, 1);
        context.assertTrue(grower.lightRgb() == mixed.rgb(), "Chamber illumination retains the mixed input color");
        var saved = grower.createNbt(); grower.readNbt(saved); tick(grower);
        context.assertTrue(grower.progress() == 1 && grower.lightFlux() == 0, "NBT cannot restore spendable optical power");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar_growth_live", tickLimit = 850)
    public void realSunlightGrowsACrystalAndNightPausesTheProcess(TestContext context) {
        var world = context.getWorld(); long originalTime = world.getTimeOfDay();
        world.setTimeOfDay(6000); world.setWeather(0, 0, false, false);
        var grower = chamber(context, MachineKind.CRYSTAL_GROWER, new BlockPos(4, 2, 2));
        var source = small(context, new BlockPos(1, 2, 2), Direction.EAST);
        grower.setStack(0, new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL));
        grower.setStack(1, new ItemStack(Items.QUARTZ, 2)); grower.fillWater(1000, false);
        int[] paused = {0};
        context.runAtTick(30, () -> {
            context.assertTrue(source.luminousFlux() > 15_000 && grower.lightFlux() > 15_000, "Actual network delivers small solar output to a lower lens");
            context.assertTrue(grower.completion(0) > 0 && grower.progress() < 2 && grower.energy().stored() == 0, "Small solar input grows slowly instead of hitting a threshold");
            world.setTimeOfDay(18000);
        });
        context.runAtTick(34, () -> paused[0] = grower.progress());
        context.runAtTick(60, () -> {
            context.assertTrue(grower.progress() == paused[0] && grower.lightFlux() == 0 && !source.isBeamActive(), "Night gives no ghost beam or free progress");
            world.setTimeOfDay(6000);
        });
        context.runAtTick(670, () -> {
            try {
                context.assertTrue(grower.progress() > 18 && grower.progress() < 23 && grower.getStack(4).isEmpty(), "Real solar growth follows the 16/480 klm speed ratio after pause/resume");
                context.assertTrue(grower.water() > 950 && grower.water() < 1000 && grower.getStack(0).getCount() == 1, "Partial growth spends proportional water and preserves the unfinished batch");
            } finally { world.setTimeOfDay(originalTime); }
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "solar_industry")
    public void largeCollectorSuppressesItsComponentsAndPersistsAllFourOutputChoices(TestContext context) {
        var origin = context.getAbsolutePos(new BlockPos(2, 2, 2));
        for (var cell : SolarStructure.BODY) context.getWorld().setBlockState(origin.add(cell.offset()), ModIndustry.COMPONENT_BLOCKS.get(cell.component()).getDefaultState());
        for (var cell : SolarStructure.RESONATORS) context.getWorld().setBlockState(origin.add(cell.offset()),
                ModIndustry.COMPONENT_BLOCKS.get(cell.component()).getDefaultState().with(LaserComponentBlock.FACING, SolarStructure.resonatorFacing(cell)));
        var sourcePos = origin.add(1, 0, -1);
        context.getWorld().setBlockState(sourcePos, ModIndustry.COMPONENT_BLOCKS.get("optical_resonator").getDefaultState().with(LaserComponentBlock.FACING, Direction.NORTH));
        var large = (SolarConcentratorBlockEntity) context.getWorld().getBlockEntity(sourcePos);
        var small = (SolarConcentratorBlockEntity) context.getWorld().getBlockEntity(origin);
        var privateData = small.createNbt();
        var owner = java.util.UUID.randomUUID();
        privateData.putUuid("Owner", owner); privateData.putBoolean("PrivateAccess", true); small.readNbt(privateData);
        context.assertFalse(SolarStructure.form(large), "Cannot absorb another owner's private collector");
        var ownerData = large.createNbt(); ownerData.putUuid("Owner", owner); large.readNbt(ownerData);
        context.assertTrue(SolarStructure.form(large), "New lower-resonator layout forms");
        int children = 0;
        for (var cell : SolarStructure.BODY) if (context.getWorld().getBlockEntity(origin.add(cell.offset())) instanceof SolarConcentratorBlockEntity member) {
            children++; member.solarTick();
            context.assertFalse(member.operationalStructure() || member.isBeamActive(), "No double generation by incorporated collectors");
        }
        context.assertTrue(children == 17, "Exactly seventeen small collectors replace absorbers");
        for (Direction side : Direction.Type.HORIZONTAL) {
            large.selectOutput(side);
            context.assertTrue(large.beamOrigin().y == sourcePos.getY() + .5, "Every output stays on the bottom layer");
            var tip=Vec3d.of(origin).add(1.5,.5,1.5).add(Vec3d.of(side.getVector()).multiply(2.499));
            context.assertTrue(large.beamOrigin().squaredDistanceTo(tip)<1e-12,"Beam begins at the matching external nozzle");
            var ray=LaserBeamTrace.traceFrom(context.getWorld(),large.beamOrigin(),large.beamDirection(),.25,large.beamExitBlock());
            context.assertTrue(ray.start().distanceTo(ray.end())>.2,"Inactive solid resonator cannot block the chosen output: "+side);
            var copy = new SolarConcentratorBlockEntity(sourcePos, large.getCachedState()); copy.readNbt(large.createNbt());
            context.assertTrue(copy.outputSide() == side, "Output survives save: " + side);
        }
        for(var cell:SolarStructure.RESONATORS){
            var port=(SolarConcentratorBlockEntity)context.getWorld().getBlockEntity(origin.add(cell.offset()));
            context.assertTrue(port.controllerPos().equals(large.getPos()),"All four ports share the source");
            if(port!=large){port.solarTick();context.assertFalse(port.operationalStructure()||port.isBeamActive()||port.opticalBudget()>0,"Extra nozzles cannot multiply optical energy");}
        }
        var data=large.createNbt();data.putBoolean("PrivateAccess",true);large.readNbt(data);large.shareSettings();
        SolarStructure.dismantle(large);
        for(var cell:SolarStructure.RESONATORS){
            var port=(SolarConcentratorBlockEntity)context.getWorld().getBlockEntity(origin.add(cell.offset()));
            context.assertTrue(port.isPrivate()&&port.createNbt().getUuid("Owner").equals(owner),"Reassembly cannot reset privacy through another resonator");
        }
        for (var cell : SolarStructure.BODY) if (context.getWorld().getBlockEntity(origin.add(cell.offset())) instanceof SolarConcentratorBlockEntity member)
            context.assertTrue(member.operationalStructure(), "Dismantling returns standalone collectors");
        context.complete();
    }

    private static IndustrialMachineBlockEntity chamber(TestContext context, MachineKind kind, BlockPos relative) {
        var origin = context.getAbsolutePos(relative);
        for (var pos : ChamberStructure.positions(origin)) context.getWorld().setBlockState(pos, ModIndustry.MACHINES.get(kind).getDefaultState());
        var machine = (IndustrialMachineBlockEntity) context.getWorld().getBlockEntity(origin);
        context.assertTrue(ChamberStructure.form(machine), "Chamber forms"); return machine;
    }
    private static SolarConcentratorBlockEntity small(TestContext context, BlockPos relative, Direction facing) {
        context.setBlockState(relative, ModIndustry.SMALL_SOLAR_CONCENTRATOR.getDefaultState().with(LaserComponentBlock.FACING, facing));
        return (SolarConcentratorBlockEntity) context.getBlockEntity(relative);
    }
    private static void tick(IndustrialMachineBlockEntity machine) {
        IndustrialMachineBlockEntity.tick(machine.getWorld(), machine.getPos(), machine.getCachedState(), machine);
    }
    private static ItemStack craft(TestContext context, CraftingInventory inventory) {
        return context.getWorld().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, inventory, context.getWorld()).orElseThrow()
                .craft(inventory, context.getWorld().getRegistryManager());
    }
}
