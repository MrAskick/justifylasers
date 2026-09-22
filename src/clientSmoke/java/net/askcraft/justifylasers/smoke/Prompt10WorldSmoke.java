package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LightBridgeBlock;
import net.askcraft.justifylasers.bridge.BridgePlacement;
import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.industry.CrystalGrowth;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

final class Prompt10WorldSmoke {
    private static final BlockPos CORNER=new BlockPos(700,3,700), STRAIGHT=CORNER.up();
    private static int particlesBefore;
    static void tick(MinecraftClient client,int tick) {
        if(tick==30)client.getServer().execute(()->{
            var world=client.getServer().getOverworld();
            var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            for(int x=697;x<=706;x++)for(int z=690;z<=704;z++) {
                world.setBlockState(new BlockPos(x,2,z),net.minecraft.block.Blocks.POLISHED_DEEPSLATE.getDefaultState());
                for(int y=3;y<9;y++)world.setBlockState(new BlockPos(x,y,z),net.minecraft.block.Blocks.AIR.getDefaultState());
            }
            world.setBlockState(CORNER,ModBlocks.CORNER_LIGHT_BRIDGE.getDefaultState());
            world.setBlockState(STRAIGHT,BridgePlacement.align(ModBlocks.LIGHT_BRIDGE.getDefaultState(),world,STRAIGHT));
            world.setBlockState(CORNER.east(),BridgePlacement.align(ModBlocks.LIGHT_BRIDGE.getDefaultState(),world,CORNER.east()));
            world.setBlockState(CORNER.east(2),BridgePlacement.align(ModBlocks.LIGHT_BRIDGE.getDefaultState(),world,CORNER.east(2)));
            world.setBlockState(CORNER.east(4),ModLaserParts.MODULES.get(LaserModule.BLOCK_DESTRUCTION).getBlock().getDefaultState());
            world.setBlockState(CORNER.south(2),ModBlocks.LASER_EMITTER.getDefaultState()
                    .with(net.askcraft.justifylasers.block.LaserEmitterBlock.FACING,Direction.NORTH));
            player.teleport(world,703.5,4.5,695.5,27,18);
            world.setTimeOfDay(6000);
        });
        if(tick==100) {
            var joined=LightBridgeNetwork.at(client.world,STRAIGHT);
            if(joined==null||!joined.active()||Math.abs(joined.normal().x)<.99)
                throw new AssertionError("Placed straight panel does not form the vertical tunnel wall: "+joined+" state="+client.world.getBlockState(STRAIGHT));
            capture(client,"tunnel-junction");
            particlesBefore=Integer.parseInt(client.particleManager.getDebugString());
            client.particleManager.addBlockBreakParticles(CORNER,client.world.getBlockState(CORNER).with(LightBridgeBlock.ROTATION,1));
        }
        if(tick==101) {
            int emitted=Integer.parseInt(client.particleManager.getDebugString())-particlesBefore;
            if(emitted<1||emitted>64)throw new AssertionError("Bridge debris count: "+emitted);
            org.slf4j.LoggerFactory.getLogger("justifylasers-client-smoke").info("BRIDGE_DEBRIS_PASSED particles={}",emitted);
        }
        if(tick==135)client.getServer().execute(()->client.getServer().getPlayerManager().getPlayer(client.player.getUuid())
                .teleport(client.getServer().getOverworld(),703.5,4.5,695.5,63,18));
        if(tick==165)capture(client,"tunnel-screen-edge");
        if(tick==180)client.setScreen(new Gallery());
        if(tick==205)capture(client,"mining-and-seeds");
        if(tick==215) {
            for(String prefix:new String[]{"block","fluid","fluid_type"}) {
                String key=prefix+".justifylasers.photopolymer";
                if(Text.translatable(key).getString().equals(key))throw new AssertionError("Missing polymer translation: "+key);
            }
            if(Platform.isModLoaded("jei")) {
                IndustryJeiSmoke.verifyPrompt8Variants();
                verifyTanks();
                IndustryJeiSmoke.show("chemical_synthesizer",13);
            }
        }
        if(tick==235)capture(client,"synthesizer-recipes");
        if(tick==245)client.setScreen(null);
        if(tick==260) {
            org.slf4j.LoggerFactory.getLogger("justifylasers-client-smoke").info("PROMPT10_SMOKE_PASSED tunnel=true particles=true seeds=true polymer=true recipes=true");
            client.scheduleStop();
        }
    }

    private static void verifyTanks() {
        if(!Platform.isModLoaded("mekanism"))return;
        try {
            var runtime=(mezz.jei.api.runtime.IJeiRuntime)Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
            var stacks=runtime.getIngredientManager().getAllIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
            var tank=Registries.ITEM.get(GameVersion.id("mekanism","creative_fluid_tank"));
            var filled=stacks.stream().filter(s->s.isOf(tank)&&GameVersion.itemData(s).toString().contains("justifylasers:photopolymer")).toList();
            if(filled.size()!=1)throw new AssertionError("Expected one Mekanism photopolymer tank in JEI, got "+filled.size());
            org.slf4j.LoggerFactory.getLogger("justifylasers-client-smoke").info("MEKANISM_PHOTOPOLYMER_TANK_PASSED {}",GameVersion.itemData(filled.get(0)));
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }

    private static final class Gallery extends Screen {
        Gallery(){super(Text.literal("Alpha 46 materials"));}
        @Override public boolean shouldPause(){return false;}
        @Override public void render(DrawContext context,int mx,int my,float delta){
            context.fill(0,0,width,height,0xFF172633);
            int column=0;
            for(var crystal:CrystalGrowth.values()) {
                int x=30+column++*90;
                context.drawText(textRenderer,Text.literal(crystal.id()),x,20,0xFFFFFF,false);
                for(int stage=0;stage<4;stage++) {
                    context.getMatrices().push();context.getMatrices().translate(x,40+stage*55,0);context.getMatrices().scale(3,3,3);
                    context.drawItem(crystal.seed(stage),0,0);context.getMatrices().pop();
                }
            }
            context.getMatrices().push();context.getMatrices().translate(385,60,0);context.getMatrices().scale(5,5,5);
            context.drawItem(new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_DESTRUCTION)),0,0);context.getMatrices().pop();
            context.draw();
        }
    }
    private static void capture(MinecraftClient client,String name){ScreenshotRecorder.saveScreenshot(client.runDirectory,"prompt10-"+name+".png",client.getFramebuffer(),t->{});}
    private Prompt10WorldSmoke(){ }
}
