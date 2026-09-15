package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModEntities;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Paired final-frame captures check actual magnification and foreground occlusion, not just shader compilation. */
final class CubeLensSmoke {
    private static final BlockPos EMITTER = new BlockPos(337,4,345);

    static void tick(MinecraftClient client, int tick) {
        if(tick==0) {
            net.askcraft.justifylasers.client.ClientSettings.reset();
            client.options.hudHidden=true;
            setLens(false);
            client.getServer().execute(()->scene(client));
        }
        if (tick > 0 && tick < 135) {
            client.options.hudHidden = true;
            client.player.setYaw(0); client.player.prevYaw = 0;
            client.player.setPitch(0); client.player.prevPitch = 0;
        }
        if(tick==45) { capture(client,"reference"); setLens(true); }
        if(tick==80) capture(client,"magnified");
        if(tick==95) {
            verifyMagnification(client);
            client.getServer().execute(()->emitter(client).getPropertyDelegate().set(0,1));
        }
        if(tick==120) capture(client,"red-core");
        if(tick==135) {
            verifyCore(client);
            client.getServer().execute(()-> {
                emitter(client).getPropertyDelegate().set(2,LaserColor.CYAN.ordinal());
                player(client).teleport(client.getServer().getOverworld(),339.5,3.5,342, -20, 10);
            });
        }
        if(tick==155) {
            capture(client,"cyan-oblique");
            setLens(false);
            client.options.hudHidden=false;
            client.getServer().execute(()-> {
                var world=client.getServer().getOverworld();
                emitter(client).getPropertyDelegate().set(0,0);
                player(client).teleport(world,340.75,2.88,341.8,0,0);
                player(client).getInventory().setStack(0,new ItemStack(Blocks.OBSIDIAN));
                player(client).getInventory().selectedSlot=0;
                for(int x=339;x<=342;x++) for(int y=3;y<=6;y++)
                    world.setBlockState(new BlockPos(x,y,343),Blocks.BOOKSHELF.getDefaultState());
            });
        }
        if(tick==195) { capture(client,"wall-reference"); setLens(true); }
        if(tick==230) capture(client,"wall-occluded");
        if(tick==245) {
            verifyOcclusion(client);
            client.reloadResources();
        }
        if(tick==275) {
            capture(client,"reloaded");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("CUBE_LENS_SMOKE_PASSED shader={}",IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    private static void scene(MinecraftClient client) {
        var world=client.getServer().getOverworld();
        for(int x=333;x<=348;x++) for(int z=338;z<=353;z++) {
            world.setBlockState(new BlockPos(x,1,z),Blocks.STONE.getDefaultState());
            for(int y=2;y<=10;y++) world.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
        }
        world.getEntitiesByClass(RefocusingCubeEntity.class,new Box(333,1,338,349,12,354),e->true).forEach(RefocusingCubeEntity::discard);
        var colors=new net.minecraft.block.Block[]{Blocks.RED_CONCRETE,Blocks.CYAN_CONCRETE,Blocks.YELLOW_CONCRETE,Blocks.BLUE_CONCRETE};
        for(int x=335;x<=346;x++) for(int y=2;y<=10;y++)
            world.setBlockState(new BlockPos(x,y,350),colors[Math.floorMod(x-340,4)].getDefaultState());
        world.setTimeOfDay(9000);
        var cube=new RefocusingCubeEntity(ModEntities.REFOCUSING_CUBE,world);
        cube.orient(0,0); cube.setPosition(340.75,4.5-cube.getHeight()/2,345.5); cube.setNoGravity(true);
        world.spawnEntity(cube);
        world.setBlockState(EMITTER,ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING,Direction.EAST));
        var emitter=emitter(client);
        emitter.getPropertyDelegate().set(13,12); emitter.getPropertyDelegate().set(2,LaserColor.RED.ordinal());
        emitter.getPropertyDelegate().set(8,0); emitter.getPropertyDelegate().set(0,0);
        player(client).getInventory().clear();
        player(client).changeGameMode(GameMode.CREATIVE);
        player(client).getAbilities().flying=true; player(client).sendAbilitiesUpdate();
        player(client).setVelocity(Vec3d.ZERO);
        player(client).teleport(world,340.75,2.88,341.8,0,0);
    }

    private static void verifyMagnification(MinecraftClient client) {
        var reference=read(client,"reference"); var actual=read(client,"magnified");
        double expected=0,unchanged=0; int samples=0;
        int cx=actual.getWidth()/2, cy=actual.getHeight()/2;
        for(int y=-10;y<=10;y++) for(int x=-34;x<=34;x++) {
            int pixel=actual.getRGB(cx+x,cy+y);
            int enlarged=reference.getRGB(cx+Math.round(x/1.8F),cy+Math.round(y/1.8F));
            int plain=reference.getRGB(cx+x,cy+y);
            expected+=error(pixel,enlarged); unchanged+=error(pixel,plain); samples+=3;
        }
        expected/=samples; unchanged/=samples;
        if(unchanged<2 || expected>unchanged*0.8)
            throw new AssertionError("Cube lens did not magnify the scene: expected="+expected+", unchanged="+unchanged);
        LoggerFactory.getLogger("justifylasers-client-smoke").info("CUBE_LENS_MAGNIFICATION_PASSED lensError={} unchangedError={}",expected,unchanged);
    }

    private static void verifyCore(MinecraftClient client) {
        var image=read(client,"red-core"); int white=0;
        for(int y=image.getHeight()/2-20;y<image.getHeight()/2+20;y++)
            for(int x=image.getWidth()/2-20;x<image.getWidth()/2+20;x++) {
                int p=image.getRGB(x,y);
                if((p>>16&255)>210 && (p>>8&255)>210 && (p&255)>210) white++;
            }
        if(white<20) throw new AssertionError("Red cube lost its white-hot interior: "+white+" white pixels");
        LoggerFactory.getLogger("justifylasers-client-smoke").info("CUBE_WHITE_CORE_PASSED pixels={}",white);
    }

    private static void verifyOcclusion(MinecraftClient client) {
        var before=read(client,"wall-reference"); var after=read(client,"wall-occluded");
        double difference=0; int samples=0;
        for(int y=after.getHeight()/2-80;y<after.getHeight()/2+80;y++)
            for(int x=after.getWidth()/2-80;x<after.getWidth()/2+80;x++) {
                difference+=error(before.getRGB(x,y),after.getRGB(x,y)); samples+=3;
            }
        difference/=samples;
        if(difference>3) throw new AssertionError("Lens affected an opaque foreground wall: "+difference);
        LoggerFactory.getLogger("justifylasers-client-smoke").info("CUBE_LENS_OCCLUSION_PASSED error={}",difference);
    }

    private static int error(int a,int b) {
        return Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));
    }

    private static void setLens(boolean enabled) {
        net.askcraft.justifylasers.client.ClientSettings.get().cubeLenses = enabled;
    }

    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient client) {
        return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
    }
    private static LaserEmitterBlockEntity emitter(MinecraftClient client) {
        return (LaserEmitterBlockEntity)client.getServer().getOverworld().getBlockEntity(EMITTER);
    }
    private static String name(String suffix) { return "cube-lens-"+(IrisCompatibility.isShaderPackInUse()?"kappa-":"vanilla-")+suffix+".png"; }
    private static void capture(MinecraftClient client,String suffix) {
        if (suffix.equals("reference") || suffix.equals("magnified")) {
            if (client.player.getPos().distanceTo(new Vec3d(340.75, 2.88, 341.8)) > 0.02
                    || !client.world.getBlockState(new BlockPos(340, 4, 343)).isAir())
                throw new AssertionError("Cube lens fixture is not ready: position=" + client.player.getPos()
                        + ", foreground=" + client.world.getBlockState(new BlockPos(340, 4, 343)));
        }
        ScreenshotRecorder.saveScreenshot(client.runDirectory,name(suffix),client.getFramebuffer(),text->{});
    }
    private static BufferedImage read(MinecraftClient client,String suffix) {
        try { return ImageIO.read(new File(client.runDirectory,"screenshots/"+name(suffix))); }
        catch(java.io.IOException error) { throw new AssertionError(error); }
    }
    private CubeLensSmoke() { }
}
