package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserPartBlock;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.energy.AmplifierTier;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.industry.*;
import net.askcraft.justifylasers.item.LaserAmplifierItem;
import net.askcraft.justifylasers.item.LaserConfiguratorItem;
import net.askcraft.justifylasers.registry.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Prompt7GameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void removedSeedPausesPaidWorkAndReloadResumesWithoutChargingTwice(TestContext c) {
        var machine=machine(c); var crystal=CrystalGrowth.DIAMOND;
        machine.setStack(0,crystal.seed(1)); machine.fillFluid(crystal.nutrient(),1000,false);
        light(machine,crystal.reference()+1,crystal.spectrum());
        for(int i=0;i<100;i++) tick(machine);
        var paid=machine.createNbt(); int remaining=machine.water();
        var seed=machine.removeStack(2);
        for(int i=0;i<30;i++) tick(machine);
        c.assertTrue(machine.status()==IndustrialMachineBlockEntity.Status.MISSING_INPUT && machine.progress()==100 && machine.water()==remaining,"Removing an active seed pauses rather than resets");
        machine.readNbt(machine.createNbt());
        c.assertTrue(machine.createNbt().getLong("GrowthRemainder")==paid.getLong("GrowthRemainder"),"Fractional light work persists");
        machine.setStack(0,CrystalGrowth.EMERALD.seed(0)); light(machine,crystal.reference(),crystal.spectrum()); tick(machine);
        c.assertTrue(machine.progress()==100 && machine.water()==remaining,"A different ingredient cannot steal paid work");
        machine.setStack(0,crystal.seed(0)); tick(machine);
        c.assertTrue(machine.progress()==100,"A fresh seed cannot steal a worn seed's progress");
        machine.setStack(0,seed); tick(machine);
        c.assertTrue(machine.progress()==101,"The original condition resumes");
        for(int i=101;i<1600;i++) tick(machine);
        c.assertTrue(machine.water()==0 && machine.progress()==0,"Exactly one bucket is paid across the pause");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void legacySeedsMigrateWithoutChangingVanillaGemIdentity(TestContext c) {
        var machine=machine(c);
        for(var crystal:CrystalGrowth.values()) for(int stage=1;stage<4;stage++) {
            machine.setStack(0,new ItemStack(ModNutrients.SEEDS.get(crystal).get(stage-1),3));
            var seed=machine.getStack(0);
            c.assertTrue(seed.isOf(crystal.natural()) && seed.getCount()==3 && crystal.stage(seed)==stage,"Hidden legacy IDs migrate to natural items with wear data");
            c.assertTrue(crystal.stage(CrystalSeed.syntheticResult(seed.copy()))==-1,"Synthetic origin takes precedence over wear");
        }
        var grown=CrystalSeed.syntheticResult(new ItemStack(Items.DIAMOND));
        c.assertFalse(machine.isValid(0,grown),"Automation rejects artificial seeds");
        c.assertTrue(net.minecraft.recipe.Ingredient.ofItems(Items.DIAMOND).test(grown),"Normal crafting ingredients still accept artificial vanilla diamonds");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void amplifierCraftRequiresEightEqualTiersAndStopsAtTheCap(TestContext c) {
        var stacks=net.minecraft.util.collection.DefaultedList.ofSize(9,ItemStack.EMPTY);
        var player=c.createMockCreativeServerPlayerInWorld();
        for(int tier=1;tier<=AmplifierTier.MAX;tier++) {
            for(int i=0;i<9;i++) stacks.set(i,i==4?new ItemStack(ModLaserParts.CONTROL_CIRCUIT):LaserAmplifierItem.stack(tier));
            c.assertTrue(LaserAmplifierItem.upgradeTier(3,3,stacks::get)==(tier<AmplifierTier.MAX?tier+1:0),"Exact upgrade tier");
            if(tier<AmplifierTier.MAX) {
                var input=new net.minecraft.inventory.CraftingInventory(player.playerScreenHandler,3,3);
                for(int i=0;i<9;i++) input.setStack(i,stacks.get(i).copy());
                var recipe=c.getWorld().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,input,c.getWorld()).orElseThrow();
                c.assertTrue(LaserAmplifierItem.tier(recipe.craft(input,c.getWorld().getRegistryManager()))==tier+1,"Registered workbench recipe preserves tier data");
                stacks.set(7,LaserAmplifierItem.stack(tier+1));
                c.assertTrue(LaserAmplifierItem.upgradeTier(3,3,stacks::get)==0,"Mixed tiers cannot be upgraded");
            }
        }
        c.assertTrue(LaserAmplifierItem.upgradeTier(2,2,stacks::get)==0,"Needs a crafting table");
        c.assertFalse((Object)ModLaserParts.AMPLIFIER instanceof net.minecraft.item.BlockItem,"Amplifier is not a block");
        player.discard();
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void craftingKeepsSyntheticOriginThroughDiamondCompression(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld();
        var input=new net.minecraft.inventory.CraftingInventory(player.playerScreenHandler,3,3);
        for(int i=0;i<9;i++) input.setStack(i,CrystalSeed.syntheticResult(new ItemStack(Items.DIAMOND)));
        var recipe=c.getWorld().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,input,c.getWorld()).orElseThrow();
        var compressed=recipe.craft(input,c.getWorld().getRegistryManager());
        c.assertTrue(compressed.isOf(Items.DIAMOND_BLOCK) && CrystalSeed.synthetic(compressed),"Compression keeps origin");
        input.clear(); input.setStack(0,compressed);
        var unpack=c.getWorld().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING,input,c.getWorld()).orElseThrow();
        var gems=unpack.craft(input,c.getWorld().getRegistryManager());
        c.assertTrue(gems.getCount()==9 && CrystalSeed.synthetic(gems) && CrystalGrowth.DIAMOND.stage(gems)<0,"Unpacking cannot restart growth");
        player.discard(); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void emitterAddsExactAmplifierFluxAndPaysForHighTiers(TestContext c) {
        var pos=c.getAbsolutePos(new BlockPos(2,2,2));
        for(int tier : new int[]{1,5,10,15}) {
            c.getWorld().setBlockState(pos,ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
            var emitter=(LaserEmitterBlockEntity)c.getWorld().getBlockEntity(pos);
            emitter.setStack(0,new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
            emitter.setStack(LaserModule.RANGE.slot(),new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE,2));
            emitter.setStack(LaserEmitterBlockEntity.AMPLIFIER_SLOT,LaserAmplifierItem.stack(tier));
            long base=(net.askcraft.justifylasers.config.LaserConfig.get().basePerTick+16L)*net.askcraft.justifylasers.config.LaserConfig.get().lumensPerEnergyUnit;
            emitter.energy().restore(emitter.energy().capacity());
            emitter.readNbt(emitter.createNbt()); int before=emitter.energy().stored();
            LaserEmitterBlockEntity.serverTick(c.getWorld(),pos,emitter.getCachedState(),emitter);
            c.assertTrue(emitter.isBeamActive() && emitter.opticalBudget()==base+AmplifierTier.lumens(tier),"Amplifier adds exact lm, without rounding up the FE conversion");
            c.assertTrue(before-emitter.energy().stored()==emitter.energyCost(),"Full powered tick paid");
            c.assertTrue(emitter.energy().capacity()>=emitter.energyCost() && emitter.energy().receive(emitter.energyCost(),true)==emitter.energyCost(),"High-tier buffer and ports can sustain operation");
            c.getWorld().removeBlock(pos,false);
        }
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void configuratorCopiesAllSixPortsOnSplittersAndCombiners(TestContext c) {
        var player=c.createMockSurvivalPlayer(); var tool=(LaserConfiguratorItem)ModBlocks.CONFIGURATOR;
        var stack=new ItemStack(tool); tool.writeEnergy(stack,1000); player.setStackInHand(Hand.MAIN_HAND,stack);
        for(var block : new net.minecraft.block.Block[]{ModBlocks.BEAM_SPLITTER,ModBlocks.BEAM_COMBINER}) {
            var from=c.getAbsolutePos(new BlockPos(1,2,1)); var to=from.east(2);
            c.getWorld().setBlockState(from,block.getDefaultState()); c.getWorld().setBlockState(to,block.getDefaultState());
            var a=(LaserOpticBlockEntity)c.getWorld().getBlockEntity(from); var b=(LaserOpticBlockEntity)c.getWorld().getBlockEntity(to);
            for(var side:Direction.values()) a.setPortMode(side,side==Direction.UP?OpticPortMode.OUTPUT:side==Direction.WEST?OpticPortMode.DISABLED:OpticPortMode.INPUT);
            LaserConfiguratorItem.select(stack,1);
            c.assertTrue(tool.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(from),Direction.UP,from,false)))==ActionResult.SUCCESS,"Copy succeeds");
            LaserConfiguratorItem.select(stack,2);
            c.assertTrue(tool.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(to),Direction.UP,to,false)))==ActionResult.SUCCESS,"Paste succeeds");
            for(var side:Direction.values()) c.assertTrue(a.portMode(side)==b.portMode(side),"Port copied: "+side);
            var corrupt=a.copySettings(); corrupt.putIntArray("Ports",new int[]{1,1,1});
            c.assertFalse(b.pasteSettings(corrupt),"Malformed clipboard rejected atomically");
            c.getWorld().removeBlock(from,false); c.getWorld().removeBlock(to,false);
        }
        c.assertTrue(tool.readEnergy(stack)==920,"Successful copy and paste charge normally");
        player.discard(); c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE, batchId="prompt7_fluid", tickLimit=160)
    public void nutrientFlowMatchesWaterAndEntitiesAreImmersed(TestContext c) {
        var origin=c.getAbsolutePos(new BlockPos(2,2,2));
        for(int x=-9;x<=9;x++) for(int z=-9;z<=9;z++) c.getWorld().setBlockState(origin.add(x,-1,z),Blocks.STONE.getDefaultState());
        c.getWorld().setBlockState(origin,ProcessFluid.DIAMOND.fluid().getDefaultState().getBlockState());
        c.runAtTick(55,()->{
            for(int distance=0;distance<=7;distance++) {
                var fluid=c.getWorld().getFluidState(origin.east(distance));
                c.assertTrue(fluid.isIn(FluidTags.WATER) && fluid.getLevel()==8-distance,"Water-like level at "+distance);
                c.assertTrue(ProcessFluid.ofFluid(fluid.getFluid())==ProcessFluid.DIAMOND,"Still and flowing variants retain nutrient identity");
            }
            c.assertTrue(c.getWorld().getFluidState(origin.east(8)).isEmpty(),"Water-like finite flow distance");
            var mob=EntityType.PIG.create(c.getWorld()); mob.setPosition(Vec3d.ofBottomCenter(origin)); c.getWorld().spawnEntity(mob); mob.tick();
            c.assertTrue(mob.isTouchingWater(),"Entity uses water movement in nutrients"); mob.discard();
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void grownCrystalsArePlaceableButNotBeamRecoloringCartridges(TestContext c) {
        for(var crystal:CrystalGrowth.values()) {
            c.assertTrue(crystal.grown() instanceof net.minecraft.item.BlockItem,"Grown crystal has a decorative block");
            var block=(LaserPartBlock)((net.minecraft.item.BlockItem)crystal.grown()).getBlock();
            c.assertFalse(block.isCrystal(),"Growth output is not a finished laser cartridge");
            for(var side:Direction.values()) {
                var state=block.getDefaultState().with(LaserPartBlock.MOUNT,side);
                c.assertFalse(state.getOutlineShape(c.getWorld(),BlockPos.ORIGIN).isEmpty(),"Mounted crystal has a selection shape");
            }
        }
        c.complete();
    }
    private static void light(IndustrialMachineBlockEntity machine,long flux,int rgb) { machine.receiveLight(flux,rgb,flux,rgb); }
    private static void tick(IndustrialMachineBlockEntity machine) { IndustrialMachineBlockEntity.tick(machine.getWorld(),machine.getPos(),machine.getCachedState(),machine); }
    private static IndustrialMachineBlockEntity machine(TestContext c) {
        var pos=c.getAbsolutePos(new BlockPos(2,2,2));
        for(var part:ChamberStructure.positions(pos)) c.getWorld().setBlockState(part,ModIndustry.MACHINES.get(MachineKind.CRYSTAL_GROWER).getDefaultState());
        var machine=(IndustrialMachineBlockEntity)c.getWorld().getBlockEntity(pos);
        c.assertTrue(ChamberStructure.form(machine),"Grower forms"); return machine;
    }
}
