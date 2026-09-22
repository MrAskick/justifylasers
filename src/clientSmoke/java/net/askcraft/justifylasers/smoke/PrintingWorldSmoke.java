package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.PrintedModelBlockEntity;
import net.askcraft.justifylasers.client.render.PrintTextures;
import net.askcraft.justifylasers.client.screen.ModelEncoderScreen;
import net.askcraft.justifylasers.industry.*;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.printing.*;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

final class PrintingWorldSmoke {
    private static final BlockPos PRINTER=new BlockPos(540,2,540), ENCODER=new BlockPos(545,2,540), LOOT=ENCODER.south(2);
    private static PrintDesign imported;
    private static String previousClipboard;
    private static String savedDraft;
    private static boolean closeStarted;
    private static final java.util.concurrent.CountDownLatch IMPORT_GATE=new java.util.concurrent.CountDownLatch(1);
    private static long importServerTime;
    private static int particlesBeforeBreak;
    static void tick(MinecraftClient client,int tick){
        if(tick>=620){SchematicWorldSmoke.tick(client,tick-620);return;}
        if(tick==30)client.getServer().execute(()->scene(client));
        if(tick==100){
            var machine=(IndustrialMachineBlockEntity)client.world.getBlockEntity(PRINTER);
            if(machine==null||machine.progress()==0||machine.lightFlux()==0)throw new AssertionError("Real violet beam did not reach printer");
            capture(client,"workshop");
        }
        if(tick==110)client.getServer().execute(()->{
            player(client).teleport(client.getServer().getOverworld(),542,2,538,0,0);
            Platform.openScreen(player(client),(IndustrialMachineBlockEntity)client.getServer().getOverworld().getBlockEntity(PRINTER));
        });
        if(tick==140){if(!(client.currentScreen instanceof net.askcraft.justifylasers.client.screen.IndustrialMachineScreen))throw new AssertionError("Printer menu did not stay open");capture(client,"printer-gui");}
        if(tick==150)client.player.closeHandledScreen();
        if(tick==160)client.getServer().execute(()->{
            player(client).teleport(client.getServer().getOverworld(),545.5,2,538,0,0);
            Platform.openScreen(player(client),(IndustrialMachineBlockEntity)client.getServer().getOverworld().getBlockEntity(ENCODER));
        });
        if(tick==190){
            if(!(client.currentScreen instanceof ModelEncoderScreen))throw new AssertionError("Encoder screen not registered/synced");
            if(!PrintTextures.catalog().contains("justifylasers:component/fuel_generator/base"))throw new AssertionError("Mod texture missing from catalog");
            imported=PrintTextures.importJson("{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"justifylasers:component/fuel_generator/base\"}}");
            if(imported.elements().size()!=1)throw new AssertionError("Parent resolution failed");
            previousClipboard=client.keyboard.getClipboard();client.keyboard.setClipboard(PrintExamples.pedestal().json());press(client,"paste");
            var screen=(ModelEncoderScreen)client.currentScreen;
            var field=screen.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance).map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast)
                    .filter(widget->widget.getMessage().equals(Text.translatable("gui.justifylasers.printing.name"))).findFirst().orElseThrow();
            field.setFocused(true);screen.setFocused(field);screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_E,0,0);screen.charTyped('e',0);
            if(client.currentScreen!=screen||!field.getText().endsWith("e"))throw new AssertionError("Typing E closed the model editor");field.setFocused(false);screen.setFocused(null);
        }
        if(tick==195)((ModelEncoderScreen)client.currentScreen).importVox(writeVox(client));
        if(tick==200)client.interactionManager.clickButton(client.player.currentScreenHandler.syncId,3);
        if(tick==210){capture(client,"encoder-import");press(client,"write");}
        if(tick==255){
            var screen=(ModelEncoderScreen)client.currentScreen;
            var written=PrintData.read(screen.getScreenHandler().getSlot(1).getStack());
            if(written==null||written.partCount()<2)throw new AssertionError("Networked write did not produce a multipart model card");
            press(client,"voxelize");
            int x=(client.getWindow().getScaledWidth()-320)/2,y=(client.getWindow().getScaledHeight()-266)/2;
            screen.mouseClicked(x+13,y+51,0);screen.mouseReleased(x+13,y+51,0);press(client,"undo");
        }
        if(tick==260)capture(client,"encoder-voxels");
        if(tick>=260&&tick<280&&!closeStarted){
            var screen=(ModelEncoderScreen)client.currentScreen;
            nameField(screen).setText("Autosave before closing");
            try {
                var save=ModelEncoderScreen.class.getDeclaredMethod("saveDraft");save.setAccessible(true);
                if(Boolean.TRUE.equals(save.invoke(screen))){
                    closeStarted=true;
                    client.keyboard.setClipboard(previousClipboard);
                    nameField(screen).setText("Edit made just before closing");
                    screen.close();
                    if(client.currentScreen!=screen)throw new AssertionError("Closing during draft cooldown did not wait for the last edit");
                }
            }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        }
        if(tick==298){
            if(!closeStarted)throw new AssertionError("Autosave cooldown never expired");
            if(client.currentScreen instanceof ModelEncoderScreen)throw new AssertionError("Editor never closed after draft cooldown");
            client.getServer().execute(()->{
            var encoder=(IndustrialMachineBlockEntity)client.getServer().getOverworld().getBlockEntity(ENCODER);
            if(encoder.encoderDraft()==null||encoder.encoderDraft().design()==null||!encoder.hologramVisible())throw new AssertionError("Server did not preserve or project VOX draft");
            if(!encoder.encoderDraft().name().equals("Edit made just before closing"))throw new AssertionError("Last edit was lost while closing during draft cooldown");
            org.slf4j.LoggerFactory.getLogger("justifylasers-client-smoke").info("ENCODER_CLOSE_COOLDOWN_PASSED final-edit-saved/reopen-pending");
            savedDraft=encoder.encoderDraft().json();
            });
        }
        if(tick==300&&Platform.isModLoaded("jei"))IndustryJeiSmoke.show("photopolymer_printer",PrintDesign.class,1);
        if(tick==305&&Platform.isModLoaded("jei"))capture(client,"printing-jei");
        if(tick==310){client.setScreen(null);client.getServer().execute(()->Platform.openScreen(player(client),(IndustrialMachineBlockEntity)client.getServer().getOverworld().getBlockEntity(ENCODER)));}
        if(tick==330){
            var screen=(ModelEncoderScreen)client.currentScreen;
            if(screen.getScreenHandler().draft()==null||!screen.getScreenHandler().draft().json().equals(savedDraft))throw new AssertionError("Reopened editor did not restore draft");
            capture(client,"encoder-restored");screen.close();
        }
        if(tick==345)client.reloadResources();
        if(tick==375){
            if(!PrintTextures.catalog().contains("minecraft:block/stone"))throw new AssertionError("Catalog lost after reload");
            client.getServer().execute(()->{
                var machine=(IndustrialMachineBlockEntity)client.getServer().getOverworld().getBlockEntity(PRINTER);
                if(PrintData.read(machine.getStack(IndustrialMachineBlockEntity.OUTPUT))==null)throw new AssertionError("Printing did not finish in native runtime");
                var encoder=(IndustrialMachineBlockEntity)client.getServer().getOverworld().getBlockEntity(ENCODER);
                var sample=new BlockPos(545,2,538);client.getServer().getOverworld().setBlockState(sample,ModIndustry.PRINTED_MODEL.getDefaultState());
                ((PrintedModelBlockEntity)client.getServer().getOverworld().getBlockEntity(sample)).setDesign(encoder.encoderDraft().design().part(0));
                player(client).teleport(client.getServer().getOverworld(),543.2,2,535.6,0,14);
            });
        }
        if(tick==415)capture(client,"prints-after-reload");
        if(tick==430){assertCollectorColor(client,LaserColor.RED);client.getServer().execute(()->player(client).teleport(client.getServer().getOverworld(),548,3.3,537,34,18));}
        if(tick==455){capture(client,"hologram-collector-red");client.getServer().execute(()->{
            var source=(LaserEmitterBlockEntity)client.getServer().getOverworld().getBlockEntity(LOOT.east(3));source.getPropertyDelegate().set(2,LaserColor.CYAN.ordinal());
            net.askcraft.justifylasers.laser.LaserBeamNetwork.invalidate(client.getServer().getOverworld());
        });}
        if(tick==480){assertCollectorColor(client,LaserColor.CYAN);capture(client,"hologram-collector-cyan");}
        if(tick==500)client.getServer().execute(()->Platform.openScreen(player(client),(IndustrialMachineBlockEntity)client.getServer().getOverworld().getBlockEntity(ENCODER)));
        if(tick==520){
            var screen=(ModelEncoderScreen)client.currentScreen;importServerTime=client.getServer().getOverworld().getTime();
            try{var method=ModelEncoderScreen.class.getDeclaredMethod("importAsync",java.util.function.Supplier.class);method.setAccessible(true);
                method.invoke(screen,(java.util.function.Supplier<PrintDesign>)()->{
                    try{if(!IMPORT_GATE.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Client stopped ticking during import");}
                    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                    return PrintExamples.pedestal();
                });
            }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        }
        if(tick==580){
            if(!((ModelEncoderScreen)client.currentScreen).importing()||client.getServer().getOverworld().getTime()-importServerTime<30)throw new AssertionError("World did not keep ticking while importer waited");
            IMPORT_GATE.countDown();
        }
        if(tick==590)press(client,"colors");
        if(tick==600){
            var screen=(ModelEncoderScreen)client.currentScreen;if(screen.importing())throw new AssertionError("Asynchronous import failed to finish");
            capture(client,"palette");
        }
        if(tick==601)press(client,"textures");
        if(tick==605){capture(client,"texture-mode");client.currentScreen.close();}
        if(tick==610){
            var pos=ENCODER.east(3);var state=ModIndustry.PRINTED_MODEL.getDefaultState();client.world.setBlockState(pos,state);
            var cells=new VoxelGrid.Material[4096];for(int i=0;i<cells.length;i++)if(((i&15)+(i>>4&15)+(i>>8))%2==0)cells[i]=VoxelGrid.Material.color(0xCE60DD);
            ((PrintedModelBlockEntity)client.world.getBlockEntity(pos)).setDesign(PrintDesign.voxels("Particle budget",cells));
            particlesBeforeBreak=Integer.parseInt(client.particleManager.getDebugString());client.particleManager.addBlockBreakParticles(pos,state);
        }
        if(tick==615&&Integer.parseInt(client.particleManager.getDebugString())-particlesBeforeBreak>256)throw new AssertionError("Voxel print emitted too many breaking particles");
    }
    private static void scene(MinecraftClient client){
        var world=client.getServer().getOverworld();world.setTimeOfDay(6000);
        for(int x=535;x<=550;x++)for(int z=534;z<=545;z++){
            world.setBlockState(new BlockPos(x,1,z),Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for(int y=2;y<8;y++)world.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
        }
        for(var pos:ChamberStructure.positions(PRINTER))world.setBlockState(pos,ModIndustry.MACHINES.get(MachineKind.PHOTOPOLYMER_PRINTER).getDefaultState());
        var printer=(IndustrialMachineBlockEntity)world.getBlockEntity(PRINTER);ChamberStructure.form(printer);printer.initializeOwner(player(client));
        printer.energy().restore(100_000);printer.fillFluid(ProcessFluid.PHOTOPOLYMER,8000,false);printer.setStack(0,PrintData.schematic(PrintExamples.pedestal()));
        world.setBlockState(ENCODER,ModIndustry.MACHINES.get(MachineKind.MODEL_ENCODER).getDefaultState());
        var encoder=(IndustrialMachineBlockEntity)world.getBlockEntity(ENCODER);encoder.initializeOwner(player(client));encoder.energy().restore(20000);encoder.setStack(0,new ItemStack(ModIndustry.BLANK_SCHEMATIC,2));
        world.setBlockState(LOOT,net.askcraft.justifylasers.registry.ModLaserParts.DECORATIONS.get("block_collection_module").getDefaultState());
        var lootSource=LOOT.east(3);world.setBlockState(lootSource,ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING,Direction.WEST));
        var lootEmitter=(LaserEmitterBlockEntity)world.getBlockEntity(lootSource);lootEmitter.getPropertyDelegate().set(2,LaserColor.RED.ordinal());lootEmitter.getPropertyDelegate().set(3,0);lootEmitter.getPropertyDelegate().set(4,0);lootEmitter.getPropertyDelegate().set(54,501);lootEmitter.getPropertyDelegate().set(13,6);
        var source=PRINTER.north(4);world.setBlockState(source,ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING,Direction.SOUTH));
        var emitter=(LaserEmitterBlockEntity)world.getBlockEntity(source);emitter.getPropertyDelegate().set(2,LaserColor.VIOLET.ordinal());emitter.getPropertyDelegate().set(3,0);emitter.getPropertyDelegate().set(4,0);emitter.getPropertyDelegate().set(54,501);emitter.getPropertyDelegate().set(13,10);
        for(int i=0;i<3;i++){
            var pos=new BlockPos(542+i,2,538);world.setBlockState(pos,ModIndustry.PRINTED_MODEL.getDefaultState());
            ((PrintedModelBlockEntity)world.getBlockEntity(pos)).setDesign(PrintExamples.pedestal());
        }
        net.askcraft.justifylasers.laser.LaserBeamNetwork.invalidate(world);
        player(client).teleport(world,545.8,3.4,534.5,30,14);
    }
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client){return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());}
    private static void press(MinecraftClient client,String key){
        String label=Text.translatable("gui.justifylasers.printing."+key).getString();
        client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(button->button.getMessage().getString().equals(label)).findFirst().orElseThrow().onPress();
    }
    private static void capture(MinecraftClient client,String name){ScreenshotRecorder.saveScreenshot(client.runDirectory,"printing-"+name+".png",client.getFramebuffer(),text->{});}
    private static void assertCollectorColor(MinecraftClient client,LaserColor expected){
        try{
            var method=Class.forName("net.askcraft.justifylasers.client.render.LootCollectorModel").getDeclaredMethod("beamColor",net.askcraft.justifylasers.block.entity.LaserPartBlockEntity.class);method.setAccessible(true);
            if(!Integer.valueOf(expected.rgb()).equals(method.invoke(null,client.world.getBlockEntity(LOOT))))throw new AssertionError("Collector did not inherit "+expected+" beam color");
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static java.nio.file.Path writeVox(MinecraftClient client){
        var voxels=new java.io.ByteArrayOutputStream();int count=0;
        for(int z=0;z<12;z++)for(int y=0;y<12;y++)for(int x=0;x<12;x++){
            double radius=Math.sqrt((x-5.5)*(x-5.5)+(y-5.5)*(y-5.5));
            if(z<2&&radius<5||z>=2&&z<8&&radius<2||z>=8&&radius<5&&radius>3){voxels.write(x);voxels.write(y);voxels.write(z);voxels.write(1+z%3);count++;}
        }
        voxels.write(39);voxels.write(5);voxels.write(5);voxels.write(1);count++;
        int childSize=24+16+voxels.size()+1036;
        var buffer=java.nio.ByteBuffer.allocate(20+childSize).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.put("VOX ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(200).put("MAIN".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(0).putInt(childSize);
        buffer.put("SIZE".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(12).putInt(0).putInt(40).putInt(12).putInt(12);
        buffer.put("XYZI".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(4+voxels.size()).putInt(0).putInt(count).put(voxels.toByteArray());
        buffer.put("RGBA".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(1024).putInt(0);
        for(int i=0;i<256;i++)buffer.putInt(new int[]{0xFFEA65B4,0xFF47D1EA,0xFFDFDDD1}[i%3]);
        var path=client.runDirectory.toPath().resolve("printing-test.vox");
        try{java.nio.file.Files.write(path,buffer.array());return path;}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
    private static net.minecraft.client.gui.widget.TextFieldWidget nameField(ModelEncoderScreen screen){
        return screen.children().stream().filter(net.minecraft.client.gui.widget.TextFieldWidget.class::isInstance)
                .map(net.minecraft.client.gui.widget.TextFieldWidget.class::cast)
                .filter(widget->widget.getMessage().equals(Text.translatable("gui.justifylasers.printing.name"))).findFirst().orElseThrow();
    }
    private PrintingWorldSmoke(){ }
}
