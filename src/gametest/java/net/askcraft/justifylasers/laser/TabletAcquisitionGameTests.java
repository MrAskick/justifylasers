package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.ChamberStructure;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.industry.TabletLoot;
import net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.screen.TabletScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class TabletAcquisitionGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tablet_acquisition")
    public void realStructureLootContainsOneReusableTabletSchematicWithoutReplacingLoot(TestContext context) {
        var world = context.getWorld();
        var params = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, Vec3d.ofCenter(context.getAbsolutePos(BlockPos.ORIGIN)))
                .build(LootContextTypes.CHEST);
        for (var chest : TabletLoot.CHESTS.entrySet()) {
            var table = world.getServer().getLootManager().getLootTable(new Identifier("minecraft", chest.getKey()));
            int found = 0, vanilla = 0;
            for (int seed = 1; seed <= 300; seed++) {
                var drops = table.generateLoot(params, seed);
                int count = drops.stream().filter(stack -> stack.isOf(ModIndustry.BLUEPRINTS.get("extraterrestrial_tablet"))).mapToInt(ItemStack::getCount).sum();
                context.assertTrue(count <= 1, "No duplicate loot pool in " + chest.getKey());
                found += count;
                vanilla += drops.stream().filter(stack -> net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getNamespace().equals("minecraft")).mapToInt(ItemStack::getCount).sum();
            }
            context.assertTrue(found > 0 && found < 200, "Schematic has a real, non-guaranteed loot chance: " + chest.getKey());
            context.assertTrue(vanilla > 0, "Vanilla treasure remains intact");
        }
        context.assertTrue(TabletLoot.pool(JustifyLasers.id("chests/simple_dungeon")) == null, "Other namespaces are not modified");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tablet_acquisition")
    public void assemblyCraftsADepletedTabletAndRetainsTheFoundSchematic(TestContext context) {
        var origin = context.getAbsolutePos(new BlockPos(1, 2, 1));
        for (var pos : ChamberStructure.positions(origin)) context.getWorld().setBlockState(pos, ModIndustry.MACHINES.get(MachineKind.ASSEMBLY_CHAMBER).getDefaultState());
        var machine = (IndustrialMachineBlockEntity) context.getWorld().getBlockEntity(origin);
        context.assertTrue(ChamberStructure.form(machine), "Assembly chamber forms");
        var ingredients = java.util.List.of(ModLaserParts.CONTROL_CIRCUIT, Items.ENDER_EYE, ModIndustry.WOLFRAMITE_INGOT, Items.GOLD_INGOT);
        for (int slot = 0; slot < ingredients.size(); slot++) machine.setStack(slot, new ItemStack(ingredients.get(slot)));
        machine.energy().restore(100_000);
        IndustrialMachineBlockEntity.tick(machine.getWorld(), origin, machine.getCachedState(), machine);
        context.assertTrue(machine.progress() == 0 && machine.getStack(4).isEmpty(), "No tablet without the found schematic");
        var schematic = ModIndustry.BLUEPRINTS.get("extraterrestrial_tablet");
        machine.setStack(IndustrialMachineBlockEntity.BLUEPRINT, new ItemStack(schematic));
        var recipe = machine.recipe();
        for (int tick = 0; tick < recipe.duration(); tick++) IndustrialMachineBlockEntity.tick(machine.getWorld(), origin, machine.getCachedState(), machine);
        var tablet = machine.getStack(IndustrialMachineBlockEntity.OUTPUT);
        context.assertTrue(tablet.isOf(ModIndustry.EXTRATERRESTRIAL_TABLET) && tablet.getCount() == 1, "Crafts exactly one tablet");
        context.assertTrue(ExtraterrestrialTabletItem.charge(tablet) == 0, "Crafted tablet must be charged");
        for (int slot = 0; slot < 4; slot++) context.assertTrue(machine.getStack(slot).isEmpty(), "Ingredient consumed once");
        context.assertTrue(machine.getStack(IndustrialMachineBlockEntity.BLUEPRINT).isOf(schematic), "Schematic is reusable");
        context.assertTrue(machine.energy().stored() == 100_000 - recipe.duration() * recipe.rate(), "Exact assembly energy cost");
        var player = context.createMockSurvivalPlayer(); player.getInventory().setStack(0, tablet.copy());
        var menu = new TabletScreenHandler(1, player.getInventory(), BlockPos.ORIGIN);
        context.assertTrue(menu.blueprints().contains(schematic), "Players can reproduce tablet schematics for other players");
        context.assertTrue(menu.blueprints().contains(ModIndustry.BLUEPRINTS.get("beam_combiner")), "Combiner is recordable after finding a tablet");
        context.complete();
    }
}
