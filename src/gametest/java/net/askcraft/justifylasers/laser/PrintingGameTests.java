package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.PrintedModelBlock;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.block.entity.PrintedModelBlockEntity;
import net.askcraft.justifylasers.industry.*;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.printing.*;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.screen.ModelEncoderScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class PrintingGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void oneNutrientBucketYieldsThreeToSixResourcesForEveryCrystal(TestContext c) {
        for (var crystal : CrystalGrowth.values()) for (int expected = 3; expected <= 6; expected++) {
            var grower = machine(c, MachineKind.CRYSTAL_GROWER);
            grower.setStack(0, crystal.seed(0));
            grower.fillFluid(crystal.nutrient(), 1000, false);
            grower.receiveLight(LuminousFlux.MAX, crystal.spectrum(), LuminousFlux.MAX, crystal.spectrum());
            step(c, grower);
            var cluster = grower.removeStack(IndustrialMachineBlockEntity.OUTPUT);
            c.assertTrue(cluster.isOf(crystal.grown()) && cluster.getCount() == 1 && grower.water() == 0,
                    crystal + " spends one bucket on one cluster");
            for (int i = 0; i < 4; i++) step(c, grower);
            c.assertTrue(grower.getStack(IndustrialMachineBlockEntity.OUTPUT).isEmpty(), "Empty tank cannot create more clusters");
            var cutter = machine(c, MachineKind.LASER_CUTTER);
            cutter.setStack(0, cluster); cutter.energy().restore(100_000);
            var recipe = cutter.recipe();
            long seed = 0;
            while (CrystalGrowth.cuttingYield(net.minecraft.util.math.random.Random.create(seed).nextInt(1000)) != expected) seed++;
            for (int tick = 0; tick < recipe.duration(); tick++) {
                if (tick == recipe.duration() / 2) cutter.readNbt(cutter.createNbt());
                cutter.receiveLight(crystal.minimum(), crystal.spectrum());
                if (tick == recipe.duration() - 1) c.getWorld().random.setSeed(seed);
                step(c, cutter);
            }
            var result = cutter.getStack(IndustrialMachineBlockEntity.OUTPUT);
            c.assertTrue(result.isOf(crystal.natural()) && result.getCount() == expected && cutter.getStack(0).isEmpty(),
                    crystal + " has exactly " + expected + " resources after one complete paid chain");
            c.assertTrue(cutter.energy().stored() == 100_000 - recipe.rate() * recipe.duration(), "Random yield does not change the FE cost");
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void schematicPartsKeepTheirStateAndSlabCollisionAfterReload(TestContext c){
        var material=new VoxelGrid.Material("minecraft:block/oak_planks",0xFFFFFF,"minecraft:oak_slab[type=bottom,waterlogged=false]",16);
        var cells=new VoxelGrid.Material[4096];java.util.Arrays.fill(cells,material);
        var model=PrintDesign.voxels("Slab print",cells);var pos=c.getAbsolutePos(new BlockPos(2,2,2));
        c.getWorld().setBlockState(pos,ModIndustry.PRINTED_MODEL.getDefaultState().with(PrintedModelBlock.FACING,Direction.EAST));
        var block=(PrintedModelBlockEntity)c.getWorld().getBlockEntity(pos);block.setDesign(model);
        block.readNbt(block.createNbt());
        c.assertTrue(block.design().json().equals(model.json()),"Exact source state survives block NBT");
        var bounds=block.shape(Direction.EAST).getBoundingBox();
        c.assertTrue(bounds.maxY==.5&&bounds.maxX==1&&bounds.maxZ==1,"Full-scale slab keeps its half-height collision");
        c.assertTrue(PrintData.read(PrintData.printed(model)).voxelCells()[0].blockState().equals(material.blockState()),"Item data preserves state and scale");
        c.assertTrue(!c.getWorld().getBlockState(pos).isOf(net.minecraft.block.Blocks.OAK_SLAB),"The result stays a decorative print, not a real copied block");
        try{PrintBlockState.resolve("minecraft:oak_log[axis=invalid]");throw new AssertionError("Unknown property accepted");}catch(PrintBlockState.ImportFailure expected){ }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void printerKeepsSchematicMaterialsThroughPaidWorkAndReload(TestContext c){
        var machine=machine(c,MachineKind.PHOTOPOLYMER_PRINTER);
        var cells=new VoxelGrid.Material[4096];cells[0]=new VoxelGrid.Material("minecraft:block/oak_log",0xFFFFFF,"minecraft:oak_log[axis=x]",1);
        cells[1]=new VoxelGrid.Material("minecraft:block/furnace_side",0xFFFFFF,"minecraft:furnace[facing=east,lit=false]",1);
        var model=PrintDesign.voxels("Schematic print",cells);var cost=model.cost();
        machine.setStack(0,PrintData.schematic(model));machine.energy().restore(100_000);machine.fillFluid(ProcessFluid.PHOTOPOLYMER,8000,false);
        for(int i=0;i<cost.ticks();i++){if(i==10)machine.readNbt(machine.createNbt());machine.receiveLight(cost.lumens(),0xB64EFF);step(c,machine);}
        var printed=PrintData.read(machine.getStack(IndustrialMachineBlockEntity.OUTPUT));
        c.assertTrue(printed!=null&&printed.json().equals(model.json()),"Cut faces, facing and textures are not flattened during printing");
        c.assertTrue(machine.water()==8000-cost.polymer()&&machine.energy().stored()==100_000-cost.energyPerTick()*cost.ticks(),"Schematic models pay ordinary per-part FE, LM and resin costs");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void crystalCutterReservesSixSpacesBeforeSpendingResources(TestContext c){
        var machine=machine(c,MachineKind.LASER_CUTTER);var crystal=CrystalGrowth.DIAMOND;
        machine.setStack(0,new ItemStack(crystal.grown()));machine.setStack(IndustrialMachineBlockEntity.OUTPUT,new ItemStack(crystal.natural(),59));
        machine.energy().restore(100_000);machine.receiveLight(crystal.minimum(),crystal.spectrum());step(c,machine);
        c.assertTrue(machine.progress()==0&&machine.energy().stored()==100_000&&machine.getStack(0).getCount()==1,"Maximum output must fit before a batch is paid");
        machine.removeStack(IndustrialMachineBlockEntity.OUTPUT);var recipe=machine.recipe();
        for(int i=0;i<recipe.duration()-1;i++)step(c,machine);machine.readNbt(machine.createNbt());machine.receiveLight(crystal.minimum(),crystal.spectrum());step(c,machine);
        c.assertTrue(machine.getStack(0).isEmpty()&&machine.getStack(IndustrialMachineBlockEntity.OUTPUT).getCount()>=3&&machine.getStack(IndustrialMachineBlockEntity.OUTPUT).getCount()<=6,"Reload does not consume two grown crystals or reroll unfinished output");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void advancedGrowthGuaranteesClustersWhileBasicGrowthCanFail(TestContext c){
        var machine=machine(c,MachineKind.CRYSTAL_GROWER);var crystal=CrystalGrowth.DIAMOND;
        machine.setStack(0,crystal.seed(0));machine.fillFluid(crystal.nutrient(),1000,false);machine.energy().restore(100);
        machine.receiveLight(LuminousFlux.MAX,crystal.spectrum(),LuminousFlux.MAX,crystal.spectrum());
        long seed=0;while(CrystalGrowth.basicYield(net.minecraft.util.math.random.Random.create(seed).nextInt(1000))!=0)seed++;
        c.getWorld().random.setSeed(seed);step(c,machine);
        c.assertTrue(machine.getStack(IndustrialMachineBlockEntity.OUTPUT).getCount()==1&&machine.water()==0&&machine.progress()==0,"One bucket grows exactly one cluster");
        c.assertTrue(machine.getStack(0).isEmpty()&&(machine.getStack(IndustrialMachineBlockEntity.ACTIVE_SEED).isEmpty()||crystal.stage(machine.getStack(IndustrialMachineBlockEntity.ACTIVE_SEED))>0),"Seed wears after a productive batch");
        c.assertTrue(machine.energy().stored()==100,"The grower still consumes only light, never FE");
        machine.clear();machine.restoreFluids(new IndustrialMachineBlockEntity.Fluids(ProcessFluid.WATER,1000,ProcessFluid.WATER,0));
        machine.setStack(0,new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL));machine.setStack(1,new ItemStack(Items.QUARTZ,2));
        machine.receiveLight(LuminousFlux.MAX,0xFFFFFF);c.getWorld().random.setSeed(seed);step(c,machine);
        c.assertTrue(machine.getStack(IndustrialMachineBlockEntity.OUTPUT).isEmpty()&&machine.getStack(0).isEmpty()&&machine.getStack(1).isEmpty()&&machine.water()==0,"Basic growth uses the same failure chance and consumes exactly one batch");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void printedPartsSnapWithMatchingOrientationWithoutOverwritingBlocks(TestContext c){
        var player=c.createMockCreativeServerPlayerInWorld();var at=c.getAbsolutePos(new BlockPos(3,2,3));
        var cells=new VoxelGrid.Material[4096];cells[0]=VoxelGrid.Material.color(0xB54EFF);
        var assembly=PrintAssembly.of(new BlockPos(32,16,16),java.util.Map.of(BlockPos.ORIGIN,cells,new BlockPos(1,0,0),cells));
        player.setPosition(Vec3d.of(at).add(.5,0,-2));
        for(var facing:Direction.Type.HORIZONTAL){
            c.getWorld().setBlockState(at,ModIndustry.PRINTED_MODEL.getDefaultState().with(PrintedModelBlock.FACING,facing));
            ((PrintedModelBlockEntity)c.getWorld().getBlockEntity(at)).setDesign(assembly.part(0,"Snap"));
            var target=at.add(PrintPart.rotate(new BlockPos(1,0,0),facing));c.getWorld().removeBlock(target,false);
            var stack=PrintData.printed(assembly.part(1,"Snap"));
            var hit=new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(at).add(0,.5,0),Direction.UP,at,false);
            var context=new net.minecraft.item.ItemPlacementContext(player,net.minecraft.util.Hand.MAIN_HAND,stack,hit);
            var item=(net.askcraft.justifylasers.item.PrintedModelItem)stack.getItem();
            c.assertTrue(item.place(context).isAccepted(),"Neighboring part snaps for "+facing);
            c.assertTrue(c.getWorld().getBlockState(target).get(PrintedModelBlock.FACING)==facing,"Snapped part inherits orientation");
            c.assertTrue(!item.place(context).isAccepted(),"An occupied target is never replaced");
            c.getWorld().removeBlock(target,false);
        }
        var composite=PrintData.printed(PrintDesign.assembly("Only schematic",assembly));
        var hit=new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(at),Direction.UP,at,false);
        c.assertTrue(!((net.askcraft.justifylasers.item.PrintedModelItem)composite.getItem()).place(new net.minecraft.item.ItemPlacementContext(player,net.minecraft.util.Hand.MAIN_HAND,composite,hit)).isAccepted(),"A whole assembly cannot bypass printing its pieces");
        player.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void multipartPrintingWaitsForExtractionAndResumesAtTheCorrectPart(TestContext c){
        var machine=machine(c,MachineKind.PHOTOPOLYMER_PRINTER);
        var a=new VoxelGrid.Material[4096];a[0]=VoxelGrid.Material.color(0xB64EFF);
        var b=a.clone();b[1]=VoxelGrid.Material.color(0xE04060);
        var root=PrintDesign.assembly("Two parts",PrintAssembly.of(new BlockPos(32,16,16),java.util.Map.of(BlockPos.ORIGIN,a,new BlockPos(1,0,0),b)));
        machine.setStack(0,PrintData.schematic(root));machine.energy().restore(100_000);machine.fillFluid(ProcessFluid.PHOTOPOLYMER,8000,false);
        int totalEnergy=0,totalFluid=0;
        for(int part=0;part<2;part++){
            var cost=root.part(part).cost();totalEnergy+=cost.energyPerTick()*cost.ticks();totalFluid+=cost.polymer();
            for(int i=0;i<cost.ticks();i++){if(i==2)machine.readNbt(machine.createNbt());machine.receiveLight(cost.lumens(),0xB64EFF);step(c,machine);}
            var printed=PrintData.read(machine.getStack(IndustrialMachineBlockEntity.OUTPUT));
            c.assertTrue(printed!=null&&printed.partInfo().index()==part&&printed.json().equals(root.part(part).json()),"Exact geometry, colors and coordinates of each part");
            int energy=machine.energy().stored();machine.readNbt(machine.createNbt());machine.receiveLight(100_000,0xB64EFF);step(c,machine);
            c.assertTrue(machine.energy().stored()==energy&&machine.progress()==0,"A different occupied output pauses without spending anything");
            machine.removeStack(IndustrialMachineBlockEntity.OUTPUT);
        }
        c.assertTrue(!machine.printRequested()&&machine.energy().stored()==100_000-totalEnergy&&machine.water()==8000-totalFluid,"One operation prints one complete set at exact per-part cost");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void multipartNbtUsesSafeChunksAndWorldPacketsDoNotRepeatLargeSchematics(TestContext c){
        var parts=new java.util.HashMap<BlockPos,VoxelGrid.Material[]>();var random=new java.util.Random(19);
        for(int x=0;x<16;x++){
            var cells=new VoxelGrid.Material[4096];for(int i=0;i<cells.length;i++)cells[i]=VoxelGrid.Material.color(random.nextInt(256)*0x010101);
            parts.put(new BlockPos(x,0,0),cells);
        }
        var model=PrintDesign.assembly("Networked",PrintAssembly.of(new BlockPos(256,16,16),parts));
        c.assertTrue(model.json().length()>65_535,"Fixture exceeds a single NBT UTF string");
        var stack=PrintData.schematic(model);c.assertTrue(PrintData.read(stack).json().equals(model.json()),"Large item data round-trips");
        var machine=machine(c,MachineKind.MODEL_ENCODER);var player=c.createMockCreativeServerPlayerInWorld();player.setPosition(Vec3d.ofCenter(machine.getPos()));machine.initializeOwner(player);
        var draft=new EncoderDraft(model,"Networked",true,VoxelGrid.Material.color(0xFFFFFF),0,1,1,false,15);
        c.assertTrue(machine.saveDraft(player,draft.json(),0),"Large draft accepted");machine.setStack(0,stack);machine.setStack(IndustrialMachineBlockEntity.OUTPUT,stack.copy());
        machine.readNbt(machine.createNbt());c.assertTrue(machine.encoderDraft().partIndex()==15&&PrintData.read(machine.getStack(0)).partCount()==16,"Full data persists on disk");
        var packet=machine.toInitialChunkDataNbt();c.assertTrue(packet.getList("Items",10).isEmpty(),"World packets omit copies already carried by menu slots");
        c.assertTrue(EncoderDraft.parse(PrintData.getText(packet,"EncoderDraft",EncoderDraft.MAX_JSON)).design().partCount()==16,"Editor draft still reaches the client");
        player.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void detailedVoxelsCanPrintWithAnInProcessTankRefill(TestContext c){
        var machine=machine(c,MachineKind.PHOTOPOLYMER_PRINTER);var cells=new VoxelGrid.Material[4096];
        for(int i=0;i<cells.length;i++)cells[i]=VoxelGrid.Material.color(i%255*0x010101);
        var model=PrintDesign.voxels("Detailed VOX",cells);var cost=model.cost();
        c.assertTrue(cost.polymer()>machine.tankCapacity(),"Fixture requires more than one full tank");
        machine.setStack(0,PrintData.schematic(model));machine.energy().restore(100_000);machine.fillFluid(ProcessFluid.PHOTOPOLYMER,machine.tankCapacity(),false);
        machine.receiveLight(cost.lumens(),0xB64EFF);step(c,machine);
        c.assertTrue(machine.progress()==1,"A full tank starts a job whose total cost exceeds its capacity");
        machine.restoreWater(0);int energy=machine.energy().stored();step(c,machine);
        c.assertTrue(machine.progress()==1&&machine.energy().stored()==energy&&machine.status()==IndustrialMachineBlockEntity.Status.NO_WATER,"Dry printing pauses without charging FE");
        machine.fillFluid(ProcessFluid.PHOTOPOLYMER,1000,false);step(c,machine);machine.readNbt(machine.createNbt());
        c.assertTrue(machine.progress()==2&&machine.printModel().json().equals(model.json()),"Refilling resumes the same detailed model; reload preserves it");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void printOnceStopsAndContinuousModeIsExplicit(TestContext c){
        var machine=machine(c,MachineKind.PHOTOPOLYMER_PRINTER);var model=PrintExamples.pedestal();
        machine.setStack(0,PrintData.schematic(model));machine.energy().restore(100000);machine.fillFluid(ProcessFluid.PHOTOPOLYMER,8000,false);
        for(int i=0;i<model.cost().ticks()*2;i++){machine.receiveLight(model.cost().lumens(),0xB64EFF);step(c,machine);}
        c.assertTrue(machine.getStack(IndustrialMachineBlockEntity.OUTPUT).getCount()==1,"Default prints only once even with unlimited resources");
        int energy=machine.energy().stored(),fluid=machine.water();machine.readNbt(machine.createNbt());
        machine.receiveLight(model.cost().lumens(),0xB64EFF);step(c,machine);
        c.assertTrue(energy==machine.energy().stored()&&fluid==machine.water()&&!machine.printRequested(),"Completed state survives reload");
        machine.requestPrint();for(int i=0;i<model.cost().ticks();i++){machine.receiveLight(model.cost().lumens(),0xB64EFF);step(c,machine);}
        c.assertTrue(machine.getStack(IndustrialMachineBlockEntity.OUTPUT).getCount()==2,"Explicit request prints another item");
        machine.cyclePrintMode();for(int i=0;i<model.cost().ticks()*2;i++){machine.receiveLight(model.cost().lumens(),0xB64EFF);step(c,machine);}
        c.assertTrue(machine.getStack(IndustrialMachineBlockEntity.OUTPUT).getCount()==4,"Continuous mode repeats");
        machine.cancelPrint();step(c,machine);c.assertTrue(!machine.printRepeat()&&machine.progress()==0,"Stop cancels continuous mode too");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void encoderDraftSurvivesCloseReloadAndProtectsConcurrentEditors(TestContext c){
        var machine=machine(c,MachineKind.MODEL_ENCODER);var player=c.createMockCreativeServerPlayerInWorld();player.setPosition(Vec3d.ofCenter(machine.getPos()));machine.initializeOwner(player);
        var draft=new EncoderDraft(PrintExamples.pedestal(),"Saved draft",true,VoxelGrid.Material.color(0xE045AC),7,2,8,true);
        c.assertTrue(machine.saveDraft(player,draft.json(),0),"Draft can be saved without FE or a blank card");
        c.assertFalse(machine.saveDraft(player,new EncoderDraft(null,"Stale",true,draft.selected(),0,1,1,false).json(),0),"A stale concurrent session cannot overwrite work");
        machine.readNbt(machine.createNbt());c.assertTrue(machine.encoderDraft().json().equals(draft.json()),"Saved name, geometry and brush survive reload");
        var menu=new ModelEncoderScreenHandler(92,player.getInventory(),machine);player.currentScreenHandler=menu;
        String empty=new EncoderDraft(null,"Intentionally empty",true,draft.selected(),0,1,1,false).json();
        c.assertTrue(menu.receive(player,ModelEncoderScreenHandler.DRAFT_BEGIN,Integer.toString(empty.length())),"Draft upload begins");
        int seq=0;for(int i=0;i<empty.length();i+=ModelEncoderScreenHandler.CHUNK_SIZE)c.assertTrue(menu.receive(player,ModelEncoderScreenHandler.CHUNK,seq+++":"+empty.substring(i,Math.min(empty.length(),i+ModelEncoderScreenHandler.CHUNK_SIZE))),"Draft chunk accepted");
        c.assertTrue(menu.receive(player,ModelEncoderScreenHandler.DRAFT_COMMIT,""),"Draft commit accepted");menu.onClosed(player);
        c.assertTrue(machine.encoderDraft().design()==null&&machine.encoderDraft().name().equals("Intentionally empty"),"Clearing a model is persisted, not confused with no draft");
        var cells=new VoxelGrid.Material[4096];for(int i=0;i<cells.length;i++)cells[i]=new VoxelGrid.Material("test:"+"p".repeat(62),i%512);
        String large=new EncoderDraft(PrintDesign.voxels("n".repeat(48),cells),"n".repeat(48),true,new VoxelGrid.Material("test:"+"p".repeat(155),0xFFFFFF),15,2,16,true).json();
        menu=new ModelEncoderScreenHandler(93,player.getInventory(),machine);player.currentScreenHandler=menu;
        c.assertTrue(large.length()>PrintDesign.MAX_JSON&&menu.receive(player,ModelEncoderScreenHandler.DRAFT_BEGIN,Integer.toString(large.length())),"Metadata has a separate allowance around a maximum-size model");
        seq=0;for(int i=0;i<large.length();i+=ModelEncoderScreenHandler.CHUNK_SIZE)c.assertTrue(menu.receive(player,ModelEncoderScreenHandler.CHUNK,seq+++":"+large.substring(i,Math.min(large.length(),i+ModelEncoderScreenHandler.CHUNK_SIZE))),"Large draft chunk accepted");
        c.assertTrue(menu.receive(player,ModelEncoderScreenHandler.DRAFT_COMMIT,""),"Large draft commits");machine.readNbt(machine.createNbt());
        c.assertTrue(machine.encoderDraft().json().equals(large),"Largest drafts survive reload without losing editor metadata");menu.onClosed(player);
        player.currentScreenHandler=player.playerScreenHandler;player.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void hologramConsumesOnlyItsOwnEnabledPower(TestContext c){
        var machine=machine(c,MachineKind.MODEL_ENCODER);var player=c.createMockCreativeServerPlayerInWorld();player.setPosition(Vec3d.ofCenter(machine.getPos()));machine.initializeOwner(player);
        machine.saveDraft(player,new EncoderDraft(PrintExamples.pedestal(),"Hologram",true,VoxelGrid.Material.color(0xFFFFFF),0,1,1,false).json(),0);machine.energy().restore(100);
        step(c,machine);c.assertTrue(machine.energy().stored()==100&&!machine.hologramVisible(),"Disabled hologram is free");machine.toggleHologram();
        for(int i=0;i<10;i++)step(c,machine);c.assertTrue(machine.energy().stored()==80&&machine.hologramVisible(),"Hologram costs exactly 2 FE per tick");
        machine.toggle();step(c,machine);c.assertTrue(machine.energy().stored()==80&&!machine.hologramVisible(),"Machine power switch hides hologram and stops cost");
        machine.toggle();machine.energy().restore(1);step(c,machine);c.assertTrue(!machine.hologramVisible()&&machine.energy().stored()==1,"No unpaid projection below the required power");
        player.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void polymerHasSlowFiniteFlowAndStableOpacity(TestContext c){
        var polymer=(NutrientFluid)ProcessFluid.PHOTOPOLYMER.fluid();var nutrient=(NutrientFluid)ProcessFluid.AMETHYST.fluid();
        c.assertTrue(polymer.getTickRate(c.getWorld())==30&&nutrient.getTickRate(c.getWorld())==5,"Polymer flows more slowly than nutrients");
        c.assertTrue(polymer.getLevelDecreasePerBlock(c.getWorld())==2,"Thick polymer has a shorter spread");
        c.assertTrue((ProcessFluid.PHOTOPOLYMER.tint()>>>24)==255&&ProcessFluid.PHOTOPOLYMER.texture(false).getNamespace().equals("justifylasers"),"Dedicated alpha texture is not attenuated a second time");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void printerConsumesExactResourcesAndResumesAfterReload(TestContext c){
        var machine=machine(c,MachineKind.PHOTOPOLYMER_PRINTER);var model=PrintExamples.pedestal();var cost=model.cost();
        machine.setStack(0,PrintData.schematic(model));machine.energy().restore(100_000);machine.fillFluid(ProcessFluid.PHOTOPOLYMER,4000,false);
        for(int tick=0;tick<cost.ticks();tick++){
            if(tick==cost.ticks()/2){machine.readNbt(machine.createNbt());c.assertTrue(machine.progress()==tick,"Saved paid work is retained");}
            machine.receiveLight(cost.lumens(),0xB64EFF);step(c,machine);
        }
        c.assertTrue(machine.getStack(IndustrialMachineBlockEntity.OUTPUT).getCount()==1,"Exactly one output");
        c.assertTrue(model.json().equals(PrintData.read(machine.getStack(IndustrialMachineBlockEntity.OUTPUT)).json()),"Full model survives printing");
        c.assertTrue(machine.energy().stored()==100_000-cost.energyPerTick()*cost.ticks(),"Exact FE cost across reload");
        c.assertTrue(machine.water()==4000-cost.polymer(),"Exact polymer cost across reload");
        c.assertTrue(machine.getStack(0).getCount()==1,"Reusable schematic");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void missingInputsAndWrongSpectrumDoNotConsumeAnything(TestContext c){
        var machine=machine(c,MachineKind.PHOTOPOLYMER_PRINTER);var model=PrintExamples.pedestal();
        machine.setStack(0,PrintData.schematic(model));machine.fillFluid(ProcessFluid.PHOTOPOLYMER,2000,false);machine.energy().restore(10000);
        step(c,machine);c.assertTrue(machine.status()==IndustrialMachineBlockEntity.Status.LOW_FLUX,"No beam pauses printing");
        machine.receiveLight(model.cost().lumens(),0xFF0000);step(c,machine);
        c.assertTrue(machine.status()==IndustrialMachineBlockEntity.Status.WRONG_SPECTRUM,"Red light is not ultraviolet");
        c.assertTrue(machine.water()==2000&&machine.energy().stored()==10000&&machine.progress()==0,"No resources spent on rejected light");
        machine.readNbt(machine.createNbt());machine.receiveLight(model.cost().lumens()-1,0xB64EFF);step(c,machine);
        c.assertTrue(machine.status()==IndustrialMachineBlockEntity.Status.LOW_FLUX,"Required flux is inclusive and enforced");
        machine.readNbt(machine.createNbt());machine.energy().restore(0);machine.receiveLight(model.cost().lumens(),0xB64EFF);step(c,machine);
        c.assertTrue(machine.status()==IndustrialMachineBlockEntity.Status.NO_POWER&&machine.water()==2000,"Light cannot substitute motor power");
        c.assertTrue(machine.fillFluid(ProcessFluid.WATER,1000,true)==0,"Only photopolymer enters the vat");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void changingModelsCannotTransferPaidWorkAndFullOutputPauses(TestContext c){
        var machine=machine(c,MachineKind.PHOTOPOLYMER_PRINTER);var model=PrintExamples.pedestal();
        machine.setStack(0,PrintData.schematic(model));machine.energy().restore(10000);machine.fillFluid(ProcessFluid.PHOTOPOLYMER,2000,false);
        machine.receiveLight(model.cost().lumens(),0xB64EFF);step(c,machine);int fe=machine.energy().stored(),fluid=machine.water();
        var changed=PrintDesign.readObject(model.json());changed.addProperty("name","Different design");machine.setStack(0,PrintData.schematic(PrintDesign.parse(changed.toString())));
        step(c,machine);c.assertTrue(machine.status()==IndustrialMachineBlockEntity.Status.MISSING_INPUT&&machine.progress()==1,"Job remains tied to original design");
        c.assertTrue(fe==machine.energy().stored()&&fluid==machine.water(),"Pause is free but cannot skip work");
        machine.setStack(0,PrintData.schematic(model));machine.setStack(IndustrialMachineBlockEntity.OUTPUT,new ItemStack(Items.DIRT));step(c,machine);
        c.assertTrue(machine.status()==IndustrialMachineBlockEntity.Status.OUTPUT_FULL&&machine.progress()==1,"Blocked output pauses");
        machine.cancelPrint();c.assertTrue(machine.progress()==0&&machine.water()==fluid,"Cancel does not refund paid material");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void encoderValidatesChunksAccessAndChargesOnlySuccessfulWrites(TestContext c){
        var machine=machine(c,MachineKind.MODEL_ENCODER);var player=c.createMockCreativeServerPlayerInWorld();
        player.setPosition(Vec3d.ofCenter(machine.getPos()));machine.initializeOwner(player);machine.energy().restore(10000);
        machine.setStack(0,new ItemStack(ModIndustry.BLANK_SCHEMATIC,2));
        var menu=new ModelEncoderScreenHandler(74,player.getInventory(),machine);player.currentScreenHandler=menu;
        c.assertFalse(new LaserSettingsPacket(73,ModelEncoderScreenHandler.BEGIN,"50").apply(player),"Stale window is rejected");
        c.assertTrue(new LaserSettingsPacket(74,ModelEncoderScreenHandler.BEGIN,"50").apply(player),"Upload begins");
        c.assertFalse(new LaserSettingsPacket(74,ModelEncoderScreenHandler.CHUNK,"1:bad").apply(player),"Out-of-order chunk aborts upload");
        c.assertFalse(new LaserSettingsPacket(74,ModelEncoderScreenHandler.COMMIT,"").apply(player),"Aborted upload cannot write");
        c.assertTrue(machine.getStack(0).getCount()==2&&machine.energy().stored()==10000,"Bad uploads consume nothing");
        menu=new ModelEncoderScreenHandler(75,player.getInventory(),machine);player.currentScreenHandler=menu;
        String json=PrintExamples.pedestal().json();
        c.assertTrue(menu.receive(player,ModelEncoderScreenHandler.BEGIN,Integer.toString(json.length())),"Valid upload starts");
        int sequence=0;for(int i=0;i<json.length();i+=ModelEncoderScreenHandler.CHUNK_SIZE)
            c.assertTrue(menu.receive(player,ModelEncoderScreenHandler.CHUNK,sequence+++":"+json.substring(i,Math.min(json.length(),i+ModelEncoderScreenHandler.CHUNK_SIZE))),"Chunk accepted");
        c.assertTrue(menu.receive(player,ModelEncoderScreenHandler.COMMIT,""),"One valid upload writes a schematic");
        c.assertTrue(machine.energy().stored()==8000&&machine.getStack(0).getCount()==1,"Exactly one blank and 2000 FE");
        c.assertTrue(json.equals(PrintData.read(machine.getStack(IndustrialMachineBlockEntity.OUTPUT)).json()),"Data survives networking");
        c.assertFalse(menu.receive(player,ModelEncoderScreenHandler.COMMIT,""),"Replay cannot duplicate a schematic");
        machine.togglePrivacy(player);var stranger=c.createMockSurvivalPlayer();stranger.setPosition(Vec3d.ofCenter(machine.getPos()));
        c.assertFalse(machine.encodeModel(stranger,json),"Private encoder rejects another player");
        player.currentScreenHandler=player.playerScreenHandler;player.discard();c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void printedBlocksRetainDataOnPlacementPickLootAndSave(TestContext c){
        var pos=c.getAbsolutePos(new BlockPos(2,2,2));var item=PrintData.printed(PrintExamples.pedestal());var block=ModIndustry.PRINTED_MODEL;
        var state=block.getDefaultState().with(PrintedModelBlock.FACING,Direction.EAST);c.getWorld().setBlockState(pos,state);
        var player=c.createMockSurvivalPlayer();block.onPlaced(c.getWorld(),pos,state,player,item);
        var entity=(PrintedModelBlockEntity)c.getWorld().getBlockEntity(pos);entity.readNbt(entity.createNbt());
        var picked=block.getPickStack(c.getWorld(),pos,state);
        c.assertTrue(PrintData.read(picked).json().equals(PrintData.read(item).json()),"Pick preserves design");
        var drops=net.minecraft.block.Block.getDroppedStacks(state,c.getWorld(),pos,entity,player,new ItemStack(Items.DIAMOND_PICKAXE));
        c.assertTrue(drops.size()==1&&PrintData.read(drops.get(0))!=null,"Loot copies model data");
        c.assertTrue(PrintData.read(drops.get(0)).json().equals(PrintData.read(item).json()),"Loot preserves exact UVs and geometry");
        var shape=entity.shape(Direction.EAST);c.assertFalse(shape.isEmpty(),"Solid model collision");
        c.assertTrue(shape.getBoundingBox().maxY==.75&&shape.getBoundingBox().minX>.0,"Not a full-block collision");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void printerInventoryAndFluidPortsAreSharedAndProtected(TestContext c){
        var machine=machine(c,MachineKind.PHOTOPOLYMER_PRINTER);
        for(var pos:ChamberStructure.positions(machine.getPos()))for(var side:Direction.values()){
            var storage=ItemStorage.SIDED.find(c.getWorld(),pos,side);c.assertTrue(storage!=null,"All members expose automation");
            machine.setStack(IndustrialMachineBlockEntity.OUTPUT,PrintData.printed(PrintExamples.pedestal()));
            try(var tx=Transaction.openOuter()){
                c.assertTrue(storage.extract(ItemVariant.of(machine.getStack(IndustrialMachineBlockEntity.OUTPUT)),1,tx)==1,"Model-aware extraction");tx.commit();
            }
            c.assertTrue(machine.getStack(IndustrialMachineBlockEntity.OUTPUT).isEmpty(),"No duplicated member inventory");
        }
        var player=c.createMockCreativeServerPlayerInWorld();machine.initializeOwner(player);machine.togglePrivacy(player);
        var fluids=net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(c.getWorld(),machine.getPos(),Direction.UP);
        c.assertTrue(fluids!=null,"Fluid port registered");
        try(var tx=Transaction.openOuter()) {
            c.assertTrue(fluids.insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(ProcessFluid.PHOTOPOLYMER.fluid()),81000,tx)==0,"Private chamber blocks fluid automation");
        }
        player.discard();c.complete();
    }
    private static void step(TestContext c,IndustrialMachineBlockEntity machine){IndustrialMachineBlockEntity.tick(c.getWorld(),machine.getPos(),machine.getCachedState(),machine);}
    private static IndustrialMachineBlockEntity machine(TestContext c,MachineKind kind){
        var origin=c.getAbsolutePos(new BlockPos(2,2,2));var positions=kind.multiblock()?ChamberStructure.positions(origin):java.util.List.of(origin);
        for(var pos:positions)c.getWorld().setBlockState(pos,ModIndustry.MACHINES.get(kind).getDefaultState());
        var machine=(IndustrialMachineBlockEntity)c.getWorld().getBlockEntity(origin);
        if(kind.multiblock())c.assertTrue(ChamberStructure.form(machine),"2×2×2 printer forms");return machine;
    }
}
