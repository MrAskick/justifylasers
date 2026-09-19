package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.ChamberStructure;
import net.askcraft.justifylasers.industry.IndustryRecipe;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.screen.TabletScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

public class IndustryBalanceGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everySchematicHasItsOwnTimeAndEnergyCost(TestContext context) {
        var recipes = IndustryRecipe.all(context.getWorld()).stream().filter(r -> r.kind() == MachineKind.ASSEMBLY_CHAMBER).toList();
        context.assertTrue(recipes.size() == 40, "Previous designs, synthesizer, cutter and spectrum module are available");
        context.assertTrue(recipes.stream().map(r -> r.duration()).distinct().count() == recipes.size(), "Each design has its own assembly duration");
        context.assertTrue(recipes.stream().map(r -> r.rate()).distinct().count() == recipes.size(), "Each design has its own FE/t");
        var housing = recipes.stream().filter(r -> r.blueprint().equals("reinforced_laser_housing")).findFirst().orElseThrow();
        var emitter = recipes.stream().filter(r -> r.blueprint().equals("powered_laser_emitter")).findFirst().orElseThrow();
        context.assertTrue(housing.duration() < emitter.duration() && housing.rate() < emitter.rate(), "A complete emitter is more expensive than its casing");
        for (var recipe : recipes) for (var input : recipe.inputs()) {
            context.assertFalse(input.test(new ItemStack(ModIndustry.LASER_CHASSIS)), "No recipe requires the retired chassis");
            context.assertFalse(input.test(new ItemStack(ModIndustry.OPTICAL_ASSEMBLY)), "No recipe requires retired optics");
        }
        verifyTabletCosts(context);
        context.complete();
    }

    private void verifyTabletCosts(TestContext context) {
        var config = net.askcraft.justifylasers.config.LaserConfig.get();
        int oldTicks = config.laserAssemblyTicks, oldRate = config.assemblyPerTick;
        try {
            config.laserAssemblyTicks = 72000;
            config.assemblyPerTick = 80000;
            var player = context.createMockSurvivalPlayer();
            player.getInventory().selectedSlot = 0;
            player.getInventory().setStack(0, new ItemStack(ModIndustry.EXTRATERRESTRIAL_TABLET));
            var menu = new TabletScreenHandler(1, player.getInventory(), BlockPos.ORIGIN);
            int[] properties = new int[8];
            menu.addListener(new net.minecraft.screen.ScreenHandlerListener() {
                @Override public void onSlotUpdate(net.minecraft.screen.ScreenHandler handler, int slot, ItemStack stack) { }
                @Override public void onPropertyUpdate(net.minecraft.screen.ScreenHandler handler, int property, int value) {
                    properties[property] = (short)value;
                }
            });
            int index = menu.blueprints().indexOf(ModIndustry.BLUEPRINTS.get("powered_laser_emitter"));
            context.assertTrue(menu.onButtonClick(player, index), "Tablet selection accepted");
            menu.sendContentUpdates();
            int ticks = (properties[4] & 0xFFFF) | ((properties[5] & 0xFFFF) << 16);
            int rate = (properties[6] & 0xFFFF) | ((properties[7] & 0xFFFF) << 16);
            context.assertTrue(ticks == menu.assemblyTicks() && rate == menu.assemblyRate(), "Tablet transmits actual server-configured recipe costs");
            context.assertTrue(ticks > 65535 && rate > 65535, "Large custom costs survive the 16-bit property wire format");
        } finally {
            config.laserAssemblyTicks = oldTicks;
            config.assemblyPerTick = oldRate;
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oldComponentsMigrateWithoutLosingQuantityOrData(TestContext context) {
        var player = context.createMockSurvivalPlayer();
        for (var old : new net.minecraft.item.Item[]{ModIndustry.LASER_CHASSIS, ModIndustry.OPTICAL_ASSEMBLY}) {
            var stack = new ItemStack(old, 12);
            var data = GameVersion.itemData(stack); data.putString("OwnerNote", "preserve me"); GameVersion.setItemData(stack, data);
            player.getInventory().setStack(9, stack);
            old.inventoryTick(stack, context.getWorld(), player, 9, false);
            var replacement = player.getInventory().getStack(9);
            String kind = ((net.askcraft.justifylasers.item.LaserComponentItem)old).component();
            context.assertTrue(replacement.isOf(ModIndustry.COMPONENTS.get(kind)) && replacement.getCount() == 12, "Canonical item and count restored");
            context.assertTrue(GameVersion.itemData(replacement).getString("OwnerNote").equals("preserve me"), "Custom data survives migration");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tabletOnlyDrainsWhileItsOwnScreenIsOpen(TestContext context) {
        var player = context.createMockSurvivalPlayer();
        player.getInventory().selectedSlot = 0;
        var tablet = new ItemStack(ModIndustry.EXTRATERRESTRIAL_TABLET);
        player.getInventory().setStack(0, tablet);
        ExtraterrestrialTabletItem.setCharge(tablet, 30);
        for (int i = 0; i < 20; i++) tablet.getItem().inventoryTick(tablet, context.getWorld(), player, 0, true);
        context.assertTrue(ExtraterrestrialTabletItem.charge(tablet) == 30, "Closed display does not drain while held");
        player.currentScreenHandler = new TabletScreenHandler(1, player.getInventory(), BlockPos.ORIGIN);
        for (int i = 0; i < 20; i++) tablet.getItem().inventoryTick(tablet, context.getWorld(), player, 0, true);
        context.assertTrue(ExtraterrestrialTabletItem.charge(tablet) == 10, "An open display consumes one FE each tick without recording");
        for (int i = 0; i < 20; i++) tablet.getItem().inventoryTick(tablet, context.getWorld(), player, 0, true);
        context.assertTrue(ExtraterrestrialTabletItem.charge(tablet) == 0, "Battery never underflows");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chamberRenderBoundsCoverEveryMember(TestContext context) {
        var origin = context.getAbsolutePos(new BlockPos(2, 2, 2));
        for (var pos : ChamberStructure.positions(origin)) context.getWorld().setBlockState(pos, ModIndustry.MACHINES.get(MachineKind.ASSEMBLY_CHAMBER).getDefaultState());
        var machine = (IndustrialMachineBlockEntity)context.getWorld().getBlockEntity(origin);
        context.assertTrue(ChamberStructure.form(machine), "Chamber forms");
        for (var pos : ChamberStructure.positions(origin)) {
            var bounds = ((IndustrialMachineBlockEntity)context.getWorld().getBlockEntity(pos)).getRenderBoundingBox();
            context.assertTrue(bounds.contains(net.minecraft.util.math.Vec3d.of(origin))
                    && bounds.contains(net.minecraft.util.math.Vec3d.of(origin.add(2,2,2))), "Any member's renderer covers the entire chamber");
        }
        context.complete();
    }
}
