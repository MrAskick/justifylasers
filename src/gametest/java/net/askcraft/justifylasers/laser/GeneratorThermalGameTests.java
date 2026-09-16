package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.GeneratorFuel;
import net.askcraft.justifylasers.industry.IndustryRecipes;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

public class GeneratorThermalGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE, batchId="thermal")
    public void lavaWarmsSlowlyAndReachesItsExactRatedPeak(TestContext context) {
        var machine=generator(context,new ItemStack(Items.LAVA_BUCKET));
        for(int i=0;i<5899;i++){machine.energy().restore(0);tick(machine);}
        context.assertTrue(machine.temperature()<1200 && machine.generatedRate()<=128,"No instant full output or overheating");
        machine.energy().restore(0);tick(machine);
        context.assertTrue(machine.temperature()==1200 && machine.efficiency()==95 && machine.generatedRate()==128,"1200 C gives net 128 FE/t at 95%");
        context.assertTrue(machine.fuel()==IndustryRecipes.fuelTicks(new ItemStack(Items.LAVA_BUCKET))-5900,"Vanilla fuel duration is retained");
        context.assertTrue(machine.getStack(4).isOf(Items.BUCKET) && machine.getStack(4).getCount()==1,"Exactly one bucket returned");
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="thermal")
    public void sticksHaveTheirOwnLowCeilingAndCannotSustainLavaHeat(TestContext context) {
        var machine=generator(context,new ItemStack(Items.STICK,64));
        for(int i=0;i<1500;i++){machine.energy().restore(0);tick(machine);}
        context.assertTrue(machine.temperature()==300 && machine.efficiency()==45 && machine.generatedRate()<=15,"Sticks plateau at 300 C");
        var saved=machine.createNbt();saved.putInt("Temperature",12_000);saved.putInt("Fuel",0);machine.readNbt(saved);
        machine.energy().restore(0);tick(machine);
        context.assertTrue(machine.temperature()<1200 && machine.fuelMaxTemperature()==300 && machine.generatedRate()<=15,"Switching from hot lava cannot turn sticks into lava");
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="thermal")
    public void pauseCoolsThreeTimesFasterAndNeverSpendsPausedFuel(TestContext context) {
        var machine=generator(context,new ItemStack(Items.COAL));
        for(int i=0;i<1000;i++){machine.energy().restore(0);tick(machine);}
        int fuel=machine.fuel(),temperature=machine.createNbt().getInt("Temperature");
        machine.toggle();
        for(int i=0;i<100;i++)tick(machine);
        context.assertTrue(machine.createNbt().getInt("Temperature")==temperature-600,"Cooling is 0.6 C/t versus 0.2 C/t heating");
        context.assertTrue(machine.fuel()==fuel && machine.generatedRate()==0,"No paused fuel or phantom generation");
        machine.toggle();machine.energy().restore(machine.energy().capacity());tick(machine);
        context.assertTrue(machine.fuel()==fuel && machine.status()==IndustrialMachineBlockEntity.Status.OUTPUT_FULL,"Full buffer also pauses combustion");
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="thermal")
    public void activeFuelAndFractionalEnergySurviveReloadWithDifferentQueuedFuel(TestContext context) {
        var original=generator(context,new ItemStack(Items.LAVA_BUCKET));
        for(int i=0;i<123;i++)tick(original);
        original.setStack(0,new ItemStack(Items.STICK,8));
        var saved=original.createNbt();
        var restored=new IndustrialMachineBlockEntity(original.getPos(),original.getCachedState());
        restored.setWorld(context.getWorld());restored.readNbt(saved);
        for(int i=0;i<100;i++){tick(original);tick(restored);}
        context.assertTrue(original.energy().stored()==restored.energy().stored() && original.fuel()==restored.fuel(),"Reload neither loses nor duplicates output");
        context.assertTrue(restored.fuelMaxTemperature()==1200 && restored.temperature()==original.temperature(),"Consumed lava profile is not replaced by queued sticks");
        context.assertTrue(original.createNbt().getLong("HeatRemainder")==restored.createNbt().getLong("HeatRemainder"),"Fractional FE survives exactly");
        context.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="thermal")
    public void commonFuelsHaveDistinctProfilesAndVanillaBurnTimes(TestContext context) {
        context.assertTrue(GeneratorFuel.of(new ItemStack(Items.LAVA_BUCKET)).equals(GeneratorFuel.LAVA),"Lava profile");
        context.assertTrue(GeneratorFuel.of(new ItemStack(Items.STICK)).equals(GeneratorFuel.KINDLING),"Stick profile");
        context.assertTrue(GeneratorFuel.of(new ItemStack(Items.COAL_BLOCK)).equals(GeneratorFuel.COAL),"Coal block stores more fuel, not more temperature");
        context.assertTrue(GeneratorFuel.of(new ItemStack(Items.CHARCOAL)).maxTemperature()<GeneratorFuel.COAL.maxTemperature(),"Charcoal differs from coal");
        context.assertTrue(GeneratorFuel.of(new ItemStack(Items.OAK_PLANKS)).equals(GeneratorFuel.WOOD),"Wood profile");
        context.assertTrue(IndustryRecipes.fuelTicks(new ItemStack(Items.DIAMOND))==0,"Nonfuel remains rejected");
        context.complete();
    }

    private static IndustrialMachineBlockEntity generator(TestContext context,ItemStack fuel){
        var pos=new BlockPos(2,2,2);context.setBlockState(pos,ModIndustry.MACHINES.get(MachineKind.FUEL_GENERATOR));
        var machine=(IndustrialMachineBlockEntity)context.getBlockEntity(pos);machine.setStack(0,fuel);return machine;
    }
    private static void tick(IndustrialMachineBlockEntity machine){IndustrialMachineBlockEntity.tick(machine.getWorld(),machine.getPos(),machine.getCachedState(),machine);}
}
