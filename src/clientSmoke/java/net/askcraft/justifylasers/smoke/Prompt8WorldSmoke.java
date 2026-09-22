package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.industry.CrystalGrowth;
import net.askcraft.justifylasers.item.LaserAmplifierItem;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

final class Prompt8WorldSmoke {
    private static boolean previousCore;
    static void tick(MinecraftClient client, int tick) {
        if (tick <= 380) Prompt6WorldSmoke.tick(client, tick);
        if (tick == 390) client.getServer().execute(() -> {
            var world = client.getServer().getOverworld();
            var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            var chest = new BlockPos(456, 2, 396);
            world.setBlockState(chest, ModLaserParts.MODULES.get(LaserModule.BLOCK_COLLECTION).getBlock().getDefaultState());
            var storage = (LaserPartBlockEntity)world.getBlockEntity(chest);
            var items = java.util.List.of(Items.DIAMOND,Items.EMERALD,Items.AMETHYST_SHARD,ModIndustry.WOLFRAMITE_INGOT,
                    ModIndustry.PHOTONITE_CRYSTAL,Items.REDSTONE,Items.GOLD_INGOT,Items.IRON_INGOT,Items.QUARTZ);
            for (int slot = 0; slot < 9; slot++) storage.setStack(slot, new ItemStack(items.get(slot), slot + 1));
            world.setBlockState(chest.east(2), ModLaserParts.MODULES.get(LaserModule.SPECTRUM).getBlock().getDefaultState());
            player.teleport(world, 457.25, 2.55, 393.6, 0, 13);
        });
        if (tick == 420) capture(client,"loot-and-spectrum");
        if (tick == 430) client.setScreen(new Gallery());
        if (tick == 455) capture(client,"inventory-models");
        if (tick == 465 && Platform.isModLoaded("jei")) IndustryJeiSmoke.showAmplifiers();
        if (tick == 485 && Platform.isModLoaded("jei")) capture(client,"amplifiers-jei");
        if (tick == 490 && Platform.isModLoaded("jei")) IndustryJeiSmoke.show("growth_seeds",CrystalGrowth.class,4);
        if (tick == 510 && Platform.isModLoaded("jei")) capture(client,"seeds-jei");
        if (tick == 520) {
            client.setScreen(null);
            boolean old=ClientSettings.get().cubeCore;
            ClientSettings.get().cubeCore=!old;
            ClientSettings.save();
            ClientSettings.initialize();
            if (ClientSettings.get().cubeCore==old) throw new AssertionError("Cube core visibility does not persist");
            ClientSettings.get().cubeCore=old; ClientSettings.save();
            previousCore=old;
            ClientSettings.get().cubeCore=true;
            if (Platform.isModLoaded("jei")) IndustryJeiSmoke.verifyPrompt8Variants();
            client.getServer().execute(() -> coreScene(client));
        }
        if (tick == 550) {
            var cubes=client.world.getEntitiesByClass(net.askcraft.justifylasers.entity.RefocusingCubeEntity.class,
                    new net.minecraft.util.math.Box(474,2,398,475,5,399), cube -> true);
            if(cubes.size()!=1 || !cubes.get(0).isLit()) throw new AssertionError("Cube visibility fixture is not powered");
            capture(client,"cube-core-on");
        }
        if (tick == 560) ClientSettings.get().cubeCore=false;
        if (tick == 590) capture(client,"cube-core-off");
        if (tick == 600) {
            ClientSettings.get().cubeCore=previousCore;
            org.slf4j.LoggerFactory.getLogger("justifylasers-client-smoke").info("PROMPT8_ASSETS_AND_VARIANTS_PASSED cubeToggle=true icons={} loot=true spectrum=true jei={}", net.askcraft.justifylasers.energy.AmplifierTier.AVAILABLE.size(), Platform.isModLoaded("jei"));
        }
        if (tick > 610) TabletWorldSmoke.tick(client,tick-610);
    }
    private static void coreScene(MinecraftClient client) {
        var world=client.getServer().getOverworld();
        world.getEntitiesByClass(net.askcraft.justifylasers.entity.RefocusingCubeEntity.class,
                new net.minecraft.util.math.Box(470,1,393,480,8,404), cube -> true).forEach(net.minecraft.entity.Entity::discard);
        for(int x=470;x<=479;x++) for(int z=394;z<=402;z++) {
            world.setBlockState(new BlockPos(x,1,z),net.minecraft.block.Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for(int y=2;y<=6;y++) world.setBlockState(new BlockPos(x,y,z),net.minecraft.block.Blocks.AIR.getDefaultState());
        }
        var cube=new net.askcraft.justifylasers.entity.RefocusingCubeEntity(net.askcraft.justifylasers.registry.ModEntities.REFOCUSING_CUBE,world);
        cube.orient(0,0); cube.setPosition(474.5,3.5-cube.getHeight()/2,398.5); cube.setNoGravity(true); world.spawnEntity(cube);
        var source=new BlockPos(470,3,398);
        world.setBlockState(source,net.askcraft.justifylasers.registry.ModBlocks.LASER_EMITTER.getDefaultState()
                .with(net.askcraft.justifylasers.block.LaserEmitterBlock.FACING,net.minecraft.util.math.Direction.EAST));
        var emitter=(net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity)world.getBlockEntity(source);
        emitter.getPropertyDelegate().set(2,net.askcraft.justifylasers.laser.LaserColor.CYAN.ordinal());
        emitter.getPropertyDelegate().set(3,0); emitter.getPropertyDelegate().set(4,0);
        emitter.getPropertyDelegate().set(8,0); emitter.getPropertyDelegate().set(13,12);
        net.askcraft.justifylasers.laser.LaserBeamNetwork.invalidate(world);
        client.getServer().getPlayerManager().getPlayer(client.player.getUuid()).teleport(world,472.2,2.6,395.2,-33,10);
    }
    private static final class Gallery extends Screen {
        Gallery() { super(Text.literal("Alpha 38 assets")); }
        @Override public boolean shouldPause() { return false; }
        @Override public void render(DrawContext context, int mx, int my, float delta) {
            context.fill(0,0,width,height,0xFF172633);
            var items=new java.util.ArrayList<ItemStack>();
            for(int tier:net.askcraft.justifylasers.energy.AmplifierTier.AVAILABLE) items.add(LaserAmplifierItem.stack(tier));
            items.add(new ItemStack(ModLaserParts.MODULES.get(LaserModule.SPECTRUM)));
            items.add(new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_COLLECTION)));
            for(var crystal:CrystalGrowth.values()) items.add(crystal.seed(0));
            for(int i=0;i<items.size();i++) {
                int x=25+i%6*75,y=30+i/6*110;
                context.fill(x-1,y-1,x+17,y+17,0xFF596774);
                context.drawItem(items.get(i),x,y);
                context.getMatrices().push(); context.getMatrices().translate(x,y+26,0); context.getMatrices().scale(3,3,3);
                context.drawItem(items.get(i),0,0); context.getMatrices().pop();
            }
            context.draw();
        }
    }
    private static void capture(MinecraftClient client,String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory,"prompt8-"+name+".png",client.getFramebuffer(),text->{});
    }
    private Prompt8WorldSmoke() { }
}
