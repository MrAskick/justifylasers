package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.block.entity.PrintedModelBlockEntity;
import net.askcraft.justifylasers.client.render.PrintTextures;
import net.askcraft.justifylasers.client.render.SchematicBlockModels;
import net.askcraft.justifylasers.client.screen.ModelEncoderScreen;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.printing.*;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

final class SchematicWorldSmoke {
    private static final BlockPos ENCODER=new BlockPos(545,2,540);
    private static PrintDesign miniature,full;
    private static SchematicBlockModels.Model beforeReload;

    static void tick(MinecraftClient client,int tick){
        if(tick==0){checkMaterials();open(client);}
        if(tick==20)((ModelEncoderScreen)client.currentScreen).importFile(file(client,true));
        if(tick==60){checkSkipped(client,5);capture(client,"miniature-editor");press(client,"write");}
        if(tick==110){
            var screen=(ModelEncoderScreen)client.currentScreen;miniature=PrintData.read(screen.getScreenHandler().getSlot(1).getStack());
            if(miniature==null||miniature.partCount()!=2||!miniature.part(0).hasBlockModels())throw new AssertionError("WorldEdit multipart schematic lost block states during network write");
            if(miniature.json().contains("PRIVATE_TEST_CONTENT"))throw new AssertionError("Block entity data entered the schematic");
            for(int x:new int[]{11,13,15,17,19})if(miniature.part(x/16).voxelCells()[(x%16)+16*(2+16)]!=null)
                throw new AssertionError("Unsupported WorldEdit block was not left empty at X="+x);
            screen.close();client.getServer().execute(()->{
                place(client,miniature,new BlockPos(544,2,544));
                player(client).teleport(client.getServer().getOverworld(),545.8,2.1,541.6,12,28);
            });
        }
        if(tick==155)capture(client,"miniature-world");
        if(tick==160)open(client);
        if(tick==185){
            var screen=(ModelEncoderScreen)client.currentScreen;
            int x=(client.getWindow().getScaledWidth()-320)/2,y=(client.getWindow().getScaledHeight()-266)/2;
            screen.mouseClicked(x+20,y+238,0);screen.mouseReleased(x+20,y+238,0);
            screen.importFile(file(client,false));
        }
        if(tick==215){checkSkipped(client,2);capture(client,"full-editor");press(client,"write");}
        if(tick==255){
            var screen=(ModelEncoderScreen)client.currentScreen;full=PrintData.read(screen.getScreenHandler().getSlot(1).getStack());
            if(full==null||full.partCount()!=4||full.part(0).elements().get(0).to().x!=16)throw new AssertionError("Full-size schematic scale was not retained");
            if(full.assembly().size().getX()!=48)throw new AssertionError("Skipped blocks cropped the schematic's original dimensions");
            for(int i=0;i<full.partCount();i++)if(full.assembly().offset(i).getX()==2)throw new AssertionError("Unsupported block became a printable full-size part");
            screen.close();client.getServer().execute(()->{
                place(client,full,new BlockPos(537,2,537));
                String[] original={"minecraft:oak_log[axis=x]","minecraft:furnace[facing=east,lit=false]","minecraft:oak_slab[type=bottom,waterlogged=false]","justifylasers:solar_absorber"};
                for(int i=0;i<original.length;i++)client.getServer().getOverworld().setBlockState(new BlockPos(535+i%2,2,537+i/2),PrintBlockState.resolve(original[i]));
                player(client).teleport(client.getServer().getOverworld(),539,3.2,533.8,24,27);
            });
        }
        if(tick==280){capture(client,"full-world");beforeReload=SchematicBlockModels.model("minecraft:oak_log[axis=x]");}
        if(tick==285)client.reloadResources();
        if(tick==335){
            checkMaterials();
            if(beforeReload==SchematicBlockModels.model("minecraft:oak_log[axis=x]"))throw new AssertionError("Schematic mesh retained obsolete sprites after resource reload");
            capture(client,"after-reload");
        }
        if(tick==345){
            org.slf4j.LoggerFactory.getLogger("justifylasers-client-smoke").info("PRINTING_SMOKE_PASSED vox=true multipart=true asyncImportTicks=true boundedParticles=true palette=true typingE=true draftReopen=true hologram=true collectorBeamColors=true modTextures=true parentImport=true upload=true editor=true realBeam=true printedOutput=true reload=true worldEdit=true blockFaceUV=true schematicScales=true skippedUnsupported=true");
            client.scheduleStop();
        }
    }

    private static void checkMaterials(){
        var log=SchematicBlockModels.model("minecraft:oak_log[axis=x]");
        if(log.faces().stream().noneMatch(face->face.cull()==Direction.EAST&&face.material().texture().equals("minecraft:block/oak_log_top")))throw new AssertionError("Rotated log end texture is on the wrong face");
        var furnace=SchematicBlockModels.model("minecraft:furnace[facing=east,lit=false]");
        if(furnace.faces().stream().noneMatch(face->face.cull()==Direction.EAST&&face.material().texture().equals("minecraft:block/furnace_front")))throw new AssertionError("Furnace facing/UV lost");
        var slab=SchematicBlockModels.model("minecraft:oak_slab[type=bottom,waterlogged=false]");
        if(slab.faces().stream().flatMap(face->face.vertices().stream()).mapToDouble(v->v.position().y).max().orElseThrow()!=.5)throw new AssertionError("Schematic slab turned into a full cube");
        var mod=SchematicBlockModels.model("justifylasers:solar_absorber");
        if(mod.faces().stream().noneMatch(face->face.material().texture().startsWith("justifylasers:")))throw new AssertionError("Modded block textures not retained");
        var grass=SchematicBlockModels.model("minecraft:grass_block[snowy=false]");
        if(grass.faces().stream().noneMatch(face->face.material().tint()!=0xFFFFFF))throw new AssertionError("Tinted block lost its grass color");
        for(var face:log.faces())for(var vertex:face.vertices())if(vertex.u()<-.01||vertex.u()>16.01||vertex.v()<-.01||vertex.v()>16.01)throw new AssertionError("Sprite atlas coordinates were not converted back to local UVs");
        try{SchematicBlockModels.material("missing_example_mod:machine");throw new AssertionError("Unknown blocks silently replaced");}catch(PrintBlockState.ImportFailure expected){ }
        try{SchematicBlockModels.material("minecraft:chest");throw new AssertionError("Block entity renderer silently flattened");}catch(PrintBlockState.ImportFailure expected){ }
        if(!PrintTextures.material("minecraft:block/oak_log_top").sprite().getContents().getId().getPath().equals("block/oak_log_top"))throw new AssertionError("Wrong atlas sprite");
    }
    private static void checkSkipped(MinecraftClient client,int count){
        try{
            var field=ModelEncoderScreen.class.getDeclaredField("importWarning");field.setAccessible(true);
            var warning=(Text)field.get(client.currentScreen);
            if(warning==null||!warning.getString().equals(Text.translatable("gui.justifylasers.printing.schematic_skipped",count).getString()))
                throw new AssertionError("Missing skipped-block report after import/autosave: "+warning);
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }

    private static void open(MinecraftClient client){client.getServer().execute(()->{
        var encoder=(IndustrialMachineBlockEntity)client.getServer().getOverworld().getBlockEntity(ENCODER);
        encoder.energy().restore(20000);encoder.setStack(0,new ItemStack(ModIndustry.BLANK_SCHEMATIC,2));encoder.setStack(IndustrialMachineBlockEntity.OUTPUT,ItemStack.EMPTY);
        player(client).teleport(client.getServer().getOverworld(),545.5,2,538,0,0);Platform.openScreen(player(client),encoder);
    });}
    private static void place(MinecraftClient client,PrintDesign design,BlockPos origin){
        var world=client.getServer().getOverworld();
        for(int i=0;i<design.partCount();i++){
            var at=origin.add(design.assembly().offset(i));world.setBlockState(at,ModIndustry.PRINTED_MODEL.getDefaultState());
            ((PrintedModelBlockEntity)world.getBlockEntity(at)).setDesign(design.part(i));
        }
    }
    private static java.nio.file.Path file(MinecraftClient client,boolean large){
        int w=large?20:3,h=large?6:1,l=large?8:2;byte[] data=new byte[w*h*l];
        if(large){
            for(int z=0;z<l;z++)for(int x=0;x<w;x++)data[x+w*z]=1;
            for(int y=1;y<h;y++)for(int x=0;x<w;x++)if(x%4==0||y==h-1)data[x+w*(7+l*y)]=2;
            data[3+w*(2+l)]=3;data[5+w*(2+l)]=4;data[7+w*(2+l)]=5;data[9+w*(2+l)]=6;
            data[11+w*(2+l)]=7;data[13+w*(2+l)]=7;data[15+w*(2+l)]=8;data[17+w*(2+l)]=9;data[19+w*(2+l)]=10;
        }else{data[0]=2;data[1]=3;data[2]=7;data[3]=4;data[4]=6;data[5]=8;}
        String[] states={"minecraft:air","minecraft:grass_block[snowy=false]","minecraft:oak_log[axis=x]","minecraft:furnace[facing=east,lit=false]","minecraft:oak_slab[type=bottom,waterlogged=false]","minecraft:glass","justifylasers:solar_absorber","minecraft:chest","minecraft:water[level=0]","missing_example_mod:machine","minecraft:oak_log[axis=diagonal]"};
        var palette=new NbtCompound();for(int i=0;i<states.length;i++)palette.putInt(states[i],i);
        var blocks=new NbtCompound();blocks.put("Palette",palette);blocks.putByteArray("Data",data);
        var root=new NbtCompound();root.putInt("Version",3);root.putInt("DataVersion",3465);root.putShort("Width",(short)w);root.putShort("Height",(short)h);root.putShort("Length",(short)l);root.put("Blocks",blocks);
        var ignored=new NbtCompound();ignored.putString("Items","PRIVATE_TEST_CONTENT");root.put("BlockEntities",ignored);
        var wrapper=new NbtCompound();wrapper.put("Schematic",root);
        var path=client.runDirectory.toPath().resolve(large?"worldedit-miniature.schem":"worldedit-full.schem");
        try(var out=new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(path)))){NbtIo.write(wrapper,out);return path;}
        catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static void press(MinecraftClient client,String key){
        String label=Text.translatable("gui.justifylasers.printing."+key).getString();
        client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(button->button.getMessage().getString().equals(label)).findFirst().orElseThrow().onPress();
    }
    private static void capture(MinecraftClient client,String name){ScreenshotRecorder.saveScreenshot(client.runDirectory,"schematic-"+name+".png",client.getFramebuffer(),text->{});}
    private SchematicWorldSmoke(){ }
}
