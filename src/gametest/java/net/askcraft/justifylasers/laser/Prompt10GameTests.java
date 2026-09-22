package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.LightBridgeBlock;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.block.entity.PrintedModelBlockEntity;
import net.askcraft.justifylasers.industry.*;
import net.askcraft.justifylasers.printing.PrintDesign;
import net.askcraft.justifylasers.printing.VoxelGrid;
import net.askcraft.justifylasers.registry.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class Prompt10GameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE, tickLimit=30)
    public void ordinaryPlacementJoinsBothCornerArmsAndSharesTheirLight(TestContext c) {
        var world=c.getWorld();
        var corner=c.getAbsolutePos(new BlockPos(2,2,2));
        var block=(LightBridgeBlock)ModBlocks.LIGHT_BRIDGE;
        world.setBlockState(corner,ModBlocks.CORNER_LIGHT_BRIDGE.getDefaultState());
        world.setBlockState(corner.south(2),ModBlocks.LASER_EMITTER.getDefaultState()
                .with(net.askcraft.justifylasers.block.LaserEmitterBlock.FACING,Direction.NORTH));
        var player=c.createMockCreativeServerPlayerInWorld();
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(block));
        for(var side:new Direction[]{Direction.UP,Direction.EAST}) {
            var hit=new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(corner)
                    .add(net.minecraft.util.math.Vec3d.of(side.getVector()).multiply(.5)),side,corner,false);
            var placement=new net.minecraft.item.ItemPlacementContext(new net.minecraft.item.ItemUsageContext(player,net.minecraft.util.Hand.MAIN_HAND,hit));
            world.setBlockState(corner.offset(side),block.getPlacementState(placement));
        }
        player.discard();
        c.runAtTick(8,()->{
            for(var side:new Direction[]{Direction.UP,Direction.EAST}) {
                var pos=corner.offset(side);
                var field=net.askcraft.justifylasers.bridge.LightBridgeNetwork.at(world,pos);
                c.assertTrue(field!=null&&field.active(),"Placed panel receives light through the shared corner aperture: "+side);
                var bridge=(net.askcraft.justifylasers.block.entity.LightBridgeBlockEntity)world.getBlockEntity(pos);
                var bounds=bridge.getRenderBoundingBox();
                c.assertTrue(bounds.contains(field.hardwareBounds().getCenter()),"Culling bounds cover the oriented hardware");
            }
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void printedPartialBlocksNeverBlackOutTheCameraOrSuffocate(TestContext c) {
        var pos=c.getAbsolutePos(new BlockPos(2,2,2));
        c.getWorld().setBlockState(pos,ModIndustry.PRINTED_MODEL.getDefaultState());
        var cells=new VoxelGrid.Material[4096];
        for(int i=0;i<2048;i++)cells[i]=VoxelGrid.Material.color(0x555555);
        var model=(PrintedModelBlockEntity)c.getWorld().getBlockEntity(pos);
        model.setDesign(PrintDesign.voxels("Half print",cells));
        var state=c.getWorld().getBlockState(pos);
        c.assertFalse(state.shouldBlockVision(c.getWorld(),pos),"Empty parts of the model do not apply the in-block overlay");
        c.assertFalse(state.shouldSuffocate(c.getWorld(),pos),"Printed models cannot suffocate at their cell boundary");
        c.assertTrue(state.getCollisionShape(c.getWorld(),pos).getBoundingBox().maxY==.5,"Collision uses the actual model rather than a cached full cube");
        cells[4095]=VoxelGrid.Material.color(0x555555);model.setDesign(PrintDesign.voxels("Tall print",cells));
        c.assertTrue(state.getCollisionShape(c.getWorld(),pos).getBoundingBox().maxY==1,"Changing model data updates collision without changing blockstate");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void mountingAndSpectrumCraftsUseTheNewProgression(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld();
        var grid=new CraftingInventory(player.playerScreenHandler,3,3);
        grid.setStack(0,new ItemStack(ModIndustry.PHOTONITE_CRYSTAL));grid.setStack(1,new ItemStack(ModIndustry.CRYSTAL_MOUNT));
        c.assertTrue(craft(c,grid).isOf(ModLaserParts.CRYSTALS.get(LaserColor.MAGENTA)),"Mounted photonite is magenta");
        var colors=new LaserColor[]{LaserColor.RED,LaserColor.ORANGE,LaserColor.YELLOW,LaserColor.GREEN,LaserColor.CYAN,LaserColor.BLUE,LaserColor.VIOLET,LaserColor.MAGENTA};
        int[] slots={0,1,2,3,5,6,7,8};grid.clear();
        for(int i=0;i<8;i++)grid.setStack(slots[i],new ItemStack(ModLaserParts.CRYSTALS.get(colors[i])));
        grid.setStack(4,new ItemStack(ModLaserParts.CONTROL_CIRCUIT));
        c.assertTrue(craft(c,grid).isOf(ModLaserParts.MODULES.get(net.askcraft.justifylasers.energy.LaserModule.SPECTRUM)),"Eight distinct colors around the circuit produce a spectrum module");
        grid.setStack(8,new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.WHITE)));
        c.assertTrue(craft(c,grid).isEmpty(),"White cannot replace a spectral color");
        c.assertFalse(ModIndustry.BLUEPRINTS.containsKey("spectrum_module"),"Retired plan is absent from tablet and creative variants");
        c.assertTrue(IndustryRecipe.all(c.getWorld()).stream().noneMatch(r->r.blueprint().equals("spectrum_module")),"No obsolete assembler shortcut");
        for(var color:LaserColor.values()) {
            c.assertTrue(c.getWorld().getRecipeManager().get(JustifyLasers.id(color.asString()+"_crystal")).isEmpty(),"Dyes no longer bypass chemical synthesis");
            c.assertTrue(c.getWorld().getRecipeManager().get(JustifyLasers.id("legacy_"+color.asString()+"_crystal")).isEmpty(),"Legacy crystal shortcuts removed");
        }
        player.discard();c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void everyRecolorAcceptsAllNineCrystalsAndChargesWaterAndPowerOnce(TestContext c) {
        var recipes=IndustryRecipe.all(c.getWorld());
        var reagents=new net.minecraft.item.Item[]{Items.REDSTONE,Items.COPPER_INGOT,Items.GOLD_INGOT,Items.EMERALD,Items.DIAMOND,Items.LAPIS_LAZULI,Items.AMETHYST_SHARD,Items.QUARTZ};
        var targets=new LaserColor[]{LaserColor.RED,LaserColor.ORANGE,LaserColor.YELLOW,LaserColor.GREEN,LaserColor.CYAN,LaserColor.BLUE,LaserColor.VIOLET,LaserColor.WHITE};
        for(int i=0;i<targets.length;i++) {
            String key="recolor_"+targets[i].asString();
            var recipe=recipes.stream().filter(r->r.key().equals(key)).findFirst().orElseThrow();
            c.assertTrue(recipe.kind()==MachineKind.CHEMICAL_SYNTHESIZER&&recipe.waterCost()==1000&&recipe.rate()==32,"Recoloring pays water and FE");
            for(var source:LaserColor.values()) {
                var input=new SimpleInventory(new ItemStack(ModLaserParts.CRYSTALS.get(source)),new ItemStack(reagents[i]),ItemStack.EMPTY,ItemStack.EMPTY);
                c.assertTrue(recipe.matches(input)&&recipe.output(input).isOf(ModLaserParts.CRYSTALS.get(targets[i])),"All source colors accept reagent: "+source+" -> "+targets[i]);
            }
        }
        var pos=c.getAbsolutePos(new BlockPos(2,2,2));
        c.getWorld().setBlockState(pos,ModIndustry.MACHINES.get(MachineKind.CHEMICAL_SYNTHESIZER).getDefaultState());
        var machine=(IndustrialMachineBlockEntity)c.getWorld().getBlockEntity(pos);
        machine.setStack(0,new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.MAGENTA)));machine.setStack(1,new ItemStack(Items.REDSTONE));
        machine.fillWater(1000,false);machine.energy().restore(100_000);
        for(int i=0;i<160;i++)IndustrialMachineBlockEntity.tick(c.getWorld(),pos,machine.getCachedState(),machine);
        c.assertTrue(machine.getStack(4).isOf(ModLaserParts.CRYSTALS.get(LaserColor.RED))&&machine.water()==0&&machine.energy().stored()==100_000-160*32,"Real synthesizer converts one crystal for one paid batch");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void photopolymerIsARegisteredSourceAndUsesPhotonite(TestContext c) {
        var recipe=IndustryRecipe.all(c.getWorld()).stream().filter(r->r.key().equals("photopolymer")).findFirst().orElseThrow();
        c.assertTrue(recipe.inputs().get(2).test(new ItemStack(ModIndustry.PHOTONITE_CRYSTAL)),"Photonite polymerization catalyst");
        c.assertFalse(recipe.inputs().get(2).test(new ItemStack(Items.AMETHYST_SHARD)),"Old catalyst removed");
        for(var fluid:ProcessFluid.values())if(fluid!=ProcessFluid.WATER) {
            c.assertTrue(fluid.fluid().getDefaultState().isStill(),"Creative fluid catalogs recognize the registered source");
            c.assertFalse(ModNutrients.FLOWING.get(fluid).getDefaultState().isStill(),"Flowing variants are not listed as additional source liquids");
            c.assertTrue(fluid.fluid().getBucketItem()==fluid.bucket(),"Source exposes the matching filled bucket");
        }
        c.complete();
    }
    private static ItemStack craft(TestContext c,CraftingInventory grid) {
        return c.getWorld().getRecipeManager().getFirstMatch(RecipeType.CRAFTING,grid,c.getWorld())
                .map(r->r.craft(grid,c.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }
}
