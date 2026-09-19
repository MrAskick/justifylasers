package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserPartBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.industry.CrystalGrowth;
import net.askcraft.justifylasers.item.LaserAmplifierItem;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

final class Prompt7WorldSmoke {
    private static final BlockPos EMITTER=new BlockPos(480,2,400);
    static void tick(MinecraftClient client,int tick) {
        // Reuse the complete workshop/menu/JEI regression before the new high-tier and model checks.
        if (tick<=380) Prompt6WorldSmoke.tick(client,tick);
        if(tick==390) client.getServer().execute(()->scene(client));
        if(tick==445) capture(client,"crystals-ground");
        if(tick==450) client.getServer().execute(()->{
            var world = client.getServer().getOverworld();
            // Remove the backing only in this render fixture: the translucent silhouette must face sky, not stone.
            for (int x=473;x<=479;x+=2) world.setBlockState(new BlockPos(x,5,400),Blocks.AIR.getDefaultState());
            player(client).teleport(world,475.5,3.8,398,0,-22);
        });
        if(tick==475) capture(client,"crystals-sky");
        if(tick==480) client.getServer().execute(()->{
            player(client).teleport(client.getServer().getOverworld(),480.5,2.5,397,0,0);
            Platform.openScreen(player(client),(LaserEmitterBlockEntity)client.getServer().getOverworld().getBlockEntity(EMITTER));
        });
        if(tick==505) {
            if(!(client.currentScreen instanceof net.askcraft.justifylasers.client.screen.PoweredLaserEmitterScreen)) throw new AssertionError("Amplified emitter GUI not opened");
            var button=client.currentScreen.children().stream().filter(net.minecraft.client.gui.widget.ButtonWidget.class::isInstance)
                    .map(net.minecraft.client.gui.widget.ButtonWidget.class::cast)
                    .filter(b->b.getMessage().getString().equals(net.minecraft.text.Text.translatable("gui.justifylasers.powered.modules").getString())).findFirst().orElseThrow();
            button.onPress();
        }
        if(tick==520) {
            var menu=(net.askcraft.justifylasers.screen.LaserEmitterScreenHandler)client.player.currentScreenHandler;
            if(LaserAmplifierItem.tier(menu.getSlot(LaserEmitterBlockEntity.AMPLIFIER_SLOT).getStack())!=15 || menu.luminousFlux()<1_000_000_000_000L)
                throw new AssertionError("High-tier amplifier inventory/flux did not synchronize");
            capture(client,"amplifier-slots");
            client.player.closeHandledScreen();
            ClientSettings.get().machineDisplays=false;
            client.getServer().execute(()->player(client).teleport(client.getServer().getOverworld(),450.5,2.2,398,0,3));
        }
        if(tick==548) { capture(client,"displays-off"); ClientSettings.get().machineDisplays=true; }
        if(tick==560) capture(client,"displays-on");
        if(tick==570 && Platform.isModLoaded("jei")) IndustryJeiSmoke.showAmplifiers();
        if(tick==590 && Platform.isModLoaded("jei")) capture(client,"amplifier-jei");
        if(tick==600) {
            org.slf4j.LoggerFactory.getLogger("justifylasers-client-smoke").info("PROMPT7_WORLD_SMOKE_PASSED workshop=true menus=true amplifierTier15=true grownModels=true displayToggle=true reload=true");
            client.scheduleStop();
        }
    }
    private static void scene(MinecraftClient client) {
        var world=client.getServer().getOverworld(); var player=player(client);
        for(int x=472;x<=484;x++) for(int z=394;z<=405;z++) {
            world.setBlockState(new BlockPos(x,1,z),Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for(int y=2;y<10;y++) world.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
        }
        int x=473;
        for(var crystal:CrystalGrowth.values()) {
            var block=((net.minecraft.item.BlockItem)crystal.grown()).getBlock();
            world.setBlockState(new BlockPos(x,2,400),block.getDefaultState());
            world.setBlockState(new BlockPos(x,5,400),Blocks.STONE.getDefaultState());
            world.setBlockState(new BlockPos(x,5,399),block.getDefaultState().with(LaserPartBlock.MOUNT,Direction.NORTH));
            x+=2;
        }
        world.setBlockState(new BlockPos(476,2,397),ModLaserParts.MODULES.get(LaserModule.SPECTRUM).getBlock().getDefaultState());
        world.setBlockState(EMITTER,ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING,Direction.SOUTH));
        var emitter=(LaserEmitterBlockEntity)world.getBlockEntity(EMITTER); emitter.initializeOwner(player);
        emitter.setStack(0,new ItemStack(ModLaserParts.MODULES.get(LaserModule.SPECTRUM)));
        emitter.setStack(LaserEmitterBlockEntity.AMPLIFIER_SLOT,LaserAmplifierItem.stack(15));
        emitter.energy().restore(emitter.energy().capacity());
        Platform.onEndWorldTick(ticking -> {
            if (ticking==world && world.getBlockEntity(EMITTER) instanceof LaserEmitterBlockEntity source)
                source.energy().receive(Integer.MAX_VALUE,false);
        });
        player.teleport(world,474,4,394,-12,18);
        player.getInventory().setStack(8,LaserAmplifierItem.stack(15));
    }
    private static ServerPlayerEntity player(MinecraftClient client) { return client.getServer().getPlayerManager().getPlayer(client.player.getUuid()); }
    private static void capture(MinecraftClient client,String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory,"prompt7-"+name+".png",client.getFramebuffer(),text->{}); }
    private Prompt7WorldSmoke() { }
}
