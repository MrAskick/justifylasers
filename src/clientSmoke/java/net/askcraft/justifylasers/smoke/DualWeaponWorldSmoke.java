package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.client.LaserGunControls;
import net.askcraft.justifylasers.client.render.LaserGunRenderer;
import net.askcraft.justifylasers.item.LaserGunItem;
import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.SaberCombat;
import net.askcraft.justifylasers.laser.WeaponHands;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.slf4j.LoggerFactory;

final class DualWeaponWorldSmoke {
    private static volatile boolean serverChecked;
    private static int mainSequence, offSequence;

    static void tick(MinecraftClient client, int tick) {
        if (tick == 30) {
            client.options.hudHidden = false;
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                for (int x=415;x<430;x++) for (int z=415;z<432;z++) {
                    world.setBlockState(new BlockPos(x,1,z),Blocks.POLISHED_DEEPSLATE.getDefaultState());
                    for (int y=2;y<9;y++) world.setBlockState(new BlockPos(x,y,z),z==430 ? Blocks.IRON_BLOCK.getDefaultState() : Blocks.AIR.getDefaultState());
                }
                var player = player(client); player.getInventory().clear(); player.getInventory().selectedSlot=0;
                player.teleport(world,422.5,2,420,0,0);
                equip(player,false,false);
            });
            client.player.getInventory().selectedSlot=0;
        }
        if (tick >= 75 && tick < 120) client.options.useKey.setPressed(true);
        if (tick == 110) {
            verifyGuns(client,true,false); capture(client,"right-gun");
            client.getServer().execute(() -> { verifyServerGuns(client,true,false); serverChecked=true; });
        }
        if (tick == 120) { client.options.useKey.setPressed(false); if (!serverChecked) throw new AssertionError("Server input check did not execute"); }
        if (tick >= 130 && tick < 175) client.options.attackKey.setPressed(true);
        if (tick == 163) { verifyGuns(client,false,true); capture(client,"left-gun"); }
        if (tick == 175) client.options.attackKey.setPressed(false);
        if (tick >= 185 && tick < 230) { client.options.useKey.setPressed(true); client.options.attackKey.setPressed(true); }
        if (tick == 215) { verifyGuns(client,true,true); capture(client,"both-guns"); }
        if (tick == 230) { client.options.attackKey.setPressed(false); client.options.useKey.setPressed(false); }
        if (tick == 242) { verifyGuns(client,false,false); client.getServer().execute(() -> equip(player(client),true,true)); }
        if (tick == 275) {
            capture(client,"two-sabers-rest");
            mainSequence=SaberCombat.state(client.player,Hand.MAIN_HAND).sequence();
            offSequence=SaberCombat.state(client.player,Hand.OFF_HAND).sequence();
        }
        if (tick >= 285 && tick < 315) client.options.useKey.setPressed(true);
        if (tick == 303) capture(client,"right-saber-cut");
        if (tick == 315) client.options.useKey.setPressed(false);
        if (tick == 330) {
            if (SaberCombat.state(client.player,WeaponHands.hand(client.player,Arm.RIGHT)).sequence() == mainSequence
                    || SaberCombat.state(client.player,WeaponHands.hand(client.player,Arm.LEFT)).sequence() != offSequence)
                throw new AssertionError("Right-click moved the wrong saber");
            offSequence=SaberCombat.state(client.player,Hand.OFF_HAND).sequence();
        }
        if (tick >= 340 && tick < 370) client.options.attackKey.setPressed(true);
        if (tick == 358) capture(client,"left-saber-cut");
        if (tick == 370) client.options.attackKey.setPressed(false);
        if (tick == 385) {
            if (SaberCombat.state(client.player,Hand.OFF_HAND).sequence() <= offSequence) throw new AssertionError("Left-click did not attack with the offhand");
            mainSequence=SaberCombat.state(client.player,Hand.MAIN_HAND).sequence();
            offSequence=SaberCombat.state(client.player,Hand.OFF_HAND).sequence();
        }
        if (tick >= 390 && tick < 425) { client.options.attackKey.setPressed(true); client.options.useKey.setPressed(true); }
        if (tick == 408) capture(client,"both-sabers-cut");
        if (tick == 425) { client.options.attackKey.setPressed(false); client.options.useKey.setPressed(false); }
        if (tick == 440) {
            if (SaberCombat.state(client.player,Hand.MAIN_HAND).sequence() <= mainSequence || SaberCombat.state(client.player,Hand.OFF_HAND).sequence() <= offSequence)
                throw new AssertionError("Both buttons must advance both saber sequences");
            client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        }
        if (tick == 460) { capture(client,"two-sabers-third-person"); client.options.setPerspective(Perspective.FIRST_PERSON); }
        if (tick == 470) client.getServer().execute(() -> equip(player(client),false,true));
        if (tick >= 500 && tick < 540) { client.options.attackKey.setPressed(true); client.options.useKey.setPressed(true); }
        if (tick == 525) {
            if (!LaserGunItem.isFiring(client.player,Hand.MAIN_HAND) || SaberCombat.state(client.player,Hand.OFF_HAND).sequence() <= 0)
                throw new AssertionError("Mixed gun/saber control failed");
            capture(client,"gun-and-saber");
        }
        if (tick == 540) { client.options.attackKey.setPressed(false); client.options.useKey.setPressed(false); client.reloadResources(); }
        if (tick == 575) {
            capture(client,"reloaded");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("DUAL_WEAPON_SMOKE_PASSED independentGunTriggers=true independentSaberSwings=true mixed=true shader={}",
                    net.askcraft.justifylasers.client.compat.IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    private static void equip(ServerPlayerEntity player, boolean mainSaber, boolean offSaber) {
        for (Hand hand : Hand.values()) {
            boolean saber=hand==Hand.MAIN_HAND ? mainSaber : offSaber;
            var stack=new ItemStack(saber ? ModBlocks.LASER_SABER : ModBlocks.LASER_GUN);
            if (saber) LaserSaberItem.setActive(stack,true);
            GameVersion.setCubeColor(stack,(hand==Hand.MAIN_HAND ? LaserColor.RED : LaserColor.CYAN).ordinal());
            player.setStackInHand(hand,stack);
        }
    }

    private static void verifyGuns(MinecraftClient client, boolean right, boolean left) {
        for (Arm arm : Arm.values()) if (LaserGunItem.isFiring(client.player,WeaponHands.hand(client.player,arm)) != (arm==Arm.RIGHT ? right : left))
            throw new AssertionError("Wrong gun trigger: "+arm);
        if (LaserGunControls.aiming(client) || LaserGunRenderer.aim(1) != 0) throw new AssertionError("Dual guns must not share an ADS pose");
    }
    private static void verifyServerGuns(MinecraftClient client, boolean right, boolean left) {
        var player=player(client);
        for (Arm arm : Arm.values()) if (LaserGunItem.isFiring(player,WeaponHands.hand(player,arm)) != (arm==Arm.RIGHT ? right : left))
            throw new AssertionError("Wrong server trigger: "+arm);
    }
    private static ServerPlayerEntity player(MinecraftClient client) { return client.getServer().getPlayerManager().getPlayer(client.player.getUuid()); }
    private static void capture(MinecraftClient client, String name) { ScreenshotRecorder.saveScreenshot(client.runDirectory,"dual-"+name+".png",client.getFramebuffer(),text -> { }); }
}
