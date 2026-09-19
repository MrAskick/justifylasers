package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.slf4j.LoggerFactory;

final class Prompt5WorldSmoke {
    private static final BlockPos MIRROR = new BlockPos(706, 4, 704);
    private static float cameraYaw, cameraPitch;
    private static double mirrorYaw;
    private static double redX, blueX;
    static void tick(MinecraftClient client, int tick) {
        if (tick > 550) { TabletWorldSmoke.tick(client,tick-550); return; }
        if (tick == 30) client.getServer().execute(() -> {
            var world = client.getServer().getOverworld();
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
            world.setTimeOfDay(6000);
            for (BlockPos pos : BlockPos.iterate(698, 0, 680, 717, 12, 716))
                world.setBlockState(pos, (pos.getY() == 0 || pos.getY()==3 ? Blocks.SMOOTH_STONE : Blocks.AIR).getDefaultState());
            world.setBlockState(MIRROR.down(), Blocks.SMOOTH_STONE.getDefaultState());
            world.setBlockState(MIRROR, ModBlocks.LASER_MIRROR.getDefaultState().with(LaserOpticBlock.FACING, Direction.UP));
            ((LaserOpticBlockEntity)world.getBlockEntity(MIRROR)).aim(new Vec3d(0, 0, -1));
            // A room BEHIND the viewer with targets at three depths, not a flat wall of colored stripes.
            world.setBlockState(new BlockPos(705,4,699), Blocks.RED_CONCRETE.getDefaultState());
            world.setBlockState(new BlockPos(707,4,691), Blocks.BLUE_CONCRETE.getDefaultState());
            world.setBlockState(new BlockPos(706,4,682), Blocks.GOLD_BLOCK.getDefaultState());
            for(int z=683;z<=699;z+=4) for(int x:new int[]{703,709}) {
                for(int y=4;y<=6;y++) world.setBlockState(new BlockPos(x,y,z),Blocks.QUARTZ_PILLAR.getDefaultState());
                for(int px=703;px<=709;px++) world.setBlockState(new BlockPos(px,7,z),Blocks.SMOOTH_QUARTZ.getDefaultState());
            }
            var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            player.changeGameMode(GameMode.SPECTATOR);
            player.teleport(world,706.5,2.88,702.5,0,0);
        });
        if (tick == 100) {
            ClientSettings.get().mirrorReflections = false;
            capture(client,"transparent");
        }
        if (tick == 110) {
            ClientSettings.get().mirrorReflections = true;
            ClientSettings.get().mirrorShaders = Boolean.getBoolean("justifylasers.smokeMirrorShaders");
        }
        if (tick == 175) capture(client,"reflection");
        if (tick == 180) {
            double[] positions=MirrorDepthSmoke.inspect(client,MIRROR,"front");
            MirrorDepthSmoke.assertComposite(client,false);
            redX=positions[0]; blueX=positions[1];
            capture(client,"depth-front");
        }
        if (tick == 185) client.getServer().execute(() -> client.getServer().getPlayerManager().getPlayer(client.player.getUuid())
                .teleport(client.getServer().getOverworld(),706.8,2.88,702.5,0,0));
        if (tick == 198) {
            double[] positions=MirrorDepthSmoke.inspect(client,MIRROR,"side");
            double near=Math.abs(positions[0]-redX), far=Math.abs(positions[1]-blueX);
            if(near<.01 || near<far*1.5) throw new AssertionError("Mirror lost depth-dependent parallax: near="+near+" far="+far);
            capture(client,"depth-side");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("MIRROR_DEPTH_SMOKE_PASSED nearParallax={} farParallax={}",near,far);
        }
        if (tick == 200) client.getServer().execute(() -> {
            var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            player.teleport(client.getServer().getOverworld(),707.0,3.2,702.8,20,8);
        });
        if (tick == 210) client.getWindow().setWindowedSize(1184,740);
        if (tick == 250) { capture(client,"oblique"); MirrorDepthSmoke.inspect(client,MIRROR,"resized"); }
        if (tick == 255) client.getServer().execute(() -> client.getServer().getOverworld().setBlockState(MIRROR.north(),Blocks.STONE.getDefaultState()));
        if (tick == 264) { capture(client,"occluded"); MirrorDepthSmoke.assertComposite(client,true); }
        if (tick == 267) client.getServer().execute(() -> client.getServer().getOverworld().setBlockState(MIRROR.north(),Blocks.AIR.getDefaultState()));
        if (tick == 270) client.reloadResources();
        if (tick == 330) capture(client,"reload");
        if (tick == 340) client.getServer().execute(() -> {
            var world=client.getServer().getOverworld();
            var kinds=new net.askcraft.justifylasers.industry.MachineKind[]{net.askcraft.justifylasers.industry.MachineKind.ASSEMBLY_CHAMBER,net.askcraft.justifylasers.industry.MachineKind.CRYSTAL_GROWER};
            for(int i=0;i<2;i++) {
                var origin=new BlockPos(710+i*4,4,706);
                for(var pos:net.askcraft.justifylasers.industry.ChamberStructure.positions(origin))
                    world.setBlockState(pos,net.askcraft.justifylasers.registry.ModIndustry.MACHINES.get(kinds[i]).getDefaultState());
                net.askcraft.justifylasers.industry.ChamberStructure.form((net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity)world.getBlockEntity(origin));
            }
            var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            player.teleport(world,713,7,701.5,0,28);
        });
        if (tick == 395) capture(client,"chambers-top");
        if (tick == 400) client.getServer().execute(() -> client.getServer().getPlayerManager().getPlayer(client.player.getUuid()).teleport(client.getServer().getOverworld(),713,1.4,703.3,0,-32));
        if (tick == 430) capture(client,"chambers-bottom");
        if (tick == 435) client.getServer().execute(() -> {
            var world=client.getServer().getOverworld();
            for(int i=0;i<4;i++) world.setBlockState(new BlockPos(706+i%2,4+i/2,708),ModBlocks.CORNER_LIGHT_BRIDGE.getDefaultState()
                    .with(net.askcraft.justifylasers.block.LightBridgeBlock.FACING,Direction.NORTH)
                    .with(net.askcraft.justifylasers.block.LightBridgeBlock.ROTATION,new int[]{0,6,2,4}[i]));
            client.getServer().getPlayerManager().getPlayer(client.player.getUuid()).teleport(world,707,6.0,704,0,24);
        });
        if (tick == 475) capture(client,"bridge-seams");
        if (tick == 490) {
            LoggerFactory.getLogger("justifylasers-client-smoke").info("PROMPT5_WORLD_SMOKE_PASSED shader={}",IrisCompatibility.isShaderPackInUse());
            client.getServer().execute(() -> {
                var player=client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.changeGameMode(GameMode.CREATIVE); player.getAbilities().flying=true; player.sendAbilitiesUpdate();
                player.teleport(client.getServer().getOverworld(),706.5,2.88,702.5,0,0);
                var tool=new net.minecraft.item.ItemStack(ModBlocks.CONFIGURATOR);
                net.askcraft.justifylasers.item.LaserConfiguratorItem.select(tool,3);
                player.getInventory().selectedSlot=0; player.getInventory().setStack(0,tool);
            });
        }
        if(tick==513) {
            client.player.getInventory().selectedSlot=0;
            mirrorYaw=((LaserOpticBlockEntity)client.world.getBlockEntity(MIRROR)).yaw();
            net.askcraft.justifylasers.platform.ClientPlatform.sendMirrorAim(new net.askcraft.justifylasers.network.MirrorAimPacket(MIRROR,net.minecraft.util.Hand.MAIN_HAND,0,mirrorYaw+5,0));
        }
        if(tick==523) {
            var optic=(LaserOpticBlockEntity)client.world.getBlockEntity(MIRROR);
            if(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(optic.yaw()-mirrorYaw-5))>.01) throw new AssertionError("Mirror aim packet failed to synchronize");
            mirrorYaw=optic.yaw(); cameraYaw=client.player.getYaw(); cameraPitch=client.player.getPitch();
            client.crosshairTarget=new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(MIRROR),Direction.NORTH,MIRROR,false);
            client.options.useKey.setPressed(true);
            if(!net.askcraft.justifylasers.client.MirrorControls.begin(client)) throw new AssertionError("Mirror drag did not start");
        }
        if(tick==530) net.askcraft.justifylasers.client.MirrorControls.mouse(40,20);
        if(tick==535) client.options.useKey.setPressed(false);
        if(tick==547) {
            var optic=(LaserOpticBlockEntity)client.world.getBlockEntity(MIRROR);
            if(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(optic.yaw()-mirrorYaw-6))>.01 || Math.abs(optic.pitch()+3)>.01)
                throw new AssertionError("Held-mouse rotation did not reach the server");
            if(client.player.getYaw()!=cameraYaw || client.player.getPitch()!=cameraPitch) throw new AssertionError("Mirror drag moved the camera");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("MIRROR_CONTROLS_SMOKE_PASSED packet=true drag=true cameraLocked=true");
        }
    }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory,"prompt5-"+name+".png",client.getFramebuffer(),text -> { });
    }
    private Prompt5WorldSmoke() { }
}
