package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.ChamberStructure;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.screen.TabletScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import team.reborn.energy.api.EnergyStorage;

public class IndustryRefinementGameTests implements FabricGameTest {
    private IndustrialMachineBlockEntity chamber(TestContext context) {
        var origin = context.getAbsolutePos(new BlockPos(2,2,2));
        for (var pos : ChamberStructure.positions(origin)) context.getWorld().setBlockState(pos, ModIndustry.MACHINES.get(MachineKind.CRYSTAL_GROWER).getDefaultState());
        var machine = (IndustrialMachineBlockEntity)context.getWorld().getBlockEntity(origin);
        context.assertTrue(ChamberStructure.form(machine), "Chamber forms");
        return machine;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyChamberFaceInsertsAndExtracts(TestContext context) {
        var machine = chamber(context);
        for (var pos : ChamberStructure.positions(machine.getPos())) for (Direction side : Direction.values()) {
            var port = ItemStorage.SIDED.find(context.getWorld(), pos, side);
            context.assertTrue(port != null, "Every member has an item port");
            try (var tx = Transaction.openOuter()) {
                context.assertTrue(port.insert(ItemVariant.of(ModIndustry.RAW_PHOTONIC_CRYSTAL),1,tx) == 1, "Insert from " + side);
                tx.commit();
            }
            machine.removeStack(0);
            machine.setStack(4, new ItemStack(ModIndustry.PHOTONITE_CRYSTAL));
            try (var tx = Transaction.openOuter()) {
                context.assertTrue(port.extract(ItemVariant.of(ModIndustry.PHOTONITE_CRYSTAL),1,tx) == 1, "Extract from " + side);
                tx.commit();
            }
            context.assertTrue(machine.getStack(4).isEmpty(), "Output extracted exactly once");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ownershipAndPrivacyProtectAllMembersAndSurviveDismantling(TestContext context) {
        var machine = chamber(context);
        var owner = context.createMockSurvivalPlayer(); var guest = context.createMockSurvivalPlayer(); guest.setUuid(java.util.UUID.randomUUID());
        owner.setPosition(Vec3d.ofCenter(machine.getPos())); guest.setPosition(owner.getPos());
        machine.initializeOwner(owner);
        context.assertFalse(machine.togglePrivacy(guest), "Guests cannot take control of public machinery");
        context.assertTrue(machine.togglePrivacy(owner), "Owner locks chamber");
        for (var pos : ChamberStructure.positions(machine.getPos())) {
            var member = (IndustrialMachineBlockEntity)context.getWorld().getBlockEntity(pos);
            context.assertFalse(member.canAccess(guest), "Member blocks are protected");
            context.assertTrue(member.canAccess(owner), "Owner retains access");
            context.assertTrue(member.getAvailableSlots(Direction.DOWN).length == 0, "No hopper bypass");
        }
        var restored = new IndustrialMachineBlockEntity(machine.getPos(), machine.getCachedState()); restored.readNbt(machine.createNbt());
        context.assertTrue(restored.isPrivate() && restored.canManageSecurity(owner), "Ownership survives NBT");
        ChamberStructure.dismantle(machine);
        context.assertFalse(machine.canAccess(guest), "Unformed casing remains private");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void redstoneOnNonControllerMemberPausesAndResumes(TestContext context) {
        var machine = chamber(context);
        machine.setStack(0, new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL)); machine.setStack(1, new ItemStack(Items.QUARTZ,2));
        machine.energy().restore(100_000); machine.fillWater(1000,false);
        machine.cycleRedstone();
        IndustrialMachineBlockEntity.tick(machine.getWorld(), machine.getPos(), machine.getCachedState(), machine);
        context.assertTrue(machine.progress() == 0 && machine.status() == IndustrialMachineBlockEntity.Status.REDSTONE, "High mode waits without consuming resources");
        var input = machine.getPos().add(1,2,1);
        context.getWorld().setBlockState(input,Blocks.REDSTONE_BLOCK.getDefaultState());
        IndustrialMachineBlockEntity.tick(machine.getWorld(), machine.getPos(), machine.getCachedState(), machine);
        context.assertTrue(machine.progress() == 1, "Signal on upper far corner powers controller");
        machine.cycleRedstone();
        IndustrialMachineBlockEntity.tick(machine.getWorld(), machine.getPos(), machine.getCachedState(), machine);
        context.assertTrue(machine.progress() == 1, "Low mode pauses while signal is high");
        context.getWorld().setBlockState(input,Blocks.AIR.getDefaultState());
        IndustrialMachineBlockEntity.tick(machine.getWorld(), machine.getPos(), machine.getCachedState(), machine);
        context.assertTrue(machine.progress() == 2, "Low mode resumes unchanged batch");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tabletChargeTransactionsRollbackAndRejectExtraction(TestContext context) {
        ItemStack tablet = new ItemStack(ModIndustry.EXTRATERRESTRIAL_TABLET);
        context.assertTrue(ExtraterrestrialTabletItem.charge(tablet) == 0, "Found tablet starts depleted");
        var inventory = new SimpleInventory(tablet);
        var holder = ContainerItemContext.ofSingleSlot(InventoryStorage.of(inventory, null).getSlot(0));
        var energy = holder.find(EnergyStorage.ITEM);
        context.assertTrue(energy != null, "Tablet exposes native Fabric item energy");
        try (var tx = Transaction.openOuter()) { context.assertTrue(energy.insert(100,tx) == 100, "Charge accepted"); }
        context.assertTrue(ExtraterrestrialTabletItem.charge(inventory.getStack(0)) == 0, "Aborted charge is rolled back");
        try (var tx = Transaction.openOuter()) { energy.insert(100,tx); tx.commit(); }
        context.assertTrue(ExtraterrestrialTabletItem.charge(inventory.getStack(0)) == 100, "Committed charge persists");
        try (var tx = Transaction.openOuter()) { context.assertTrue(energy.extract(100,tx) == 0, "Tablet cannot power external machines"); }
        inventory.setStack(0,new ItemStack(Items.STONE));
        try (var tx = Transaction.openOuter()) { context.assertTrue(energy.insert(100,tx) == 0, "Stale port cannot charge unrelated items"); }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tabletWritesExactlyOneCardForEnergyAndCannotWriteWhenSwapped(TestContext context) {
        var player = context.createMockSurvivalPlayer(); var inventory = player.getInventory();
        inventory.selectedSlot = 0;
        ItemStack tablet = new ItemStack(ModIndustry.EXTRATERRESTRIAL_TABLET); inventory.setStack(0,tablet);
        inventory.setStack(1,new ItemStack(ModIndustry.BLANK_SCHEMATIC,2));
        var menu = new TabletScreenHandler(1,inventory,BlockPos.ORIGIN);
        context.assertFalse(menu.onButtonClick(player,TabletScreenHandler.RECORD), "Cannot record while discharged");
        ExtraterrestrialTabletItem.setCharge(tablet,2000);
        context.assertTrue(menu.onButtonClick(player,TabletScreenHandler.RECORD), "Charged tablet records a design");
        context.assertTrue(inventory.count(ModIndustry.BLANK_SCHEMATIC) == 1 && ExtraterrestrialTabletItem.charge(tablet) == 1000, "One card and exact energy consumed");
        context.assertTrue(inventory.count(menu.blueprint()) == 1, "Exactly one blueprint created");
        context.assertFalse(menu.onButtonClick(player,9999), "Unknown commands rejected");
        inventory.setStack(0,new ItemStack(Items.STONE));
        context.assertFalse(menu.onButtonClick(player,TabletScreenHandler.RECORD), "Swapping tablet invalidates existing screen");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oreLootSupportsSilkTouchAndFortune(TestContext context) {
        ItemStack normal = new ItemStack(Items.DIAMOND_PICKAXE), silk = normal.copy(), fortune = normal.copy();
        silk.addEnchantment(Enchantments.SILK_TOUCH,1); fortune.addEnchantment(Enchantments.FORTUNE,3);
        var player = context.createMockSurvivalPlayer(); var pos = context.getAbsolutePos(new BlockPos(2,2,2));
        for (var ore : ModIndustry.ORES.values()) {
            var state = ore.getDefaultState();
            var dropped = Block.getDroppedStacks(state, context.getWorld(), pos, null, player, silk);
            context.assertTrue(dropped.size() == 1 && dropped.get(0).isOf(ore.asItem()) && dropped.get(0).getCount() == 1, "Silk preserves " + ore);
            int baseline = 0, boosted = 0;
            for (int attempt = 0; attempt < 96; attempt++) {
                baseline += Block.getDroppedStacks(state,context.getWorld(),pos,null,player,normal).stream().mapToInt(ItemStack::getCount).sum();
                boosted += Block.getDroppedStacks(state,context.getWorld(),pos,null,player,fortune).stream().mapToInt(ItemStack::getCount).sum();
            }
            context.assertTrue(baseline == 96 && boosted > baseline, "Fortune increases the actual raw ore yield");
        }
        context.complete();
    }
}
