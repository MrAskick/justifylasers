package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.item.LaserGunItem;
import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.network.LaserGunControlPacket;
import net.askcraft.justifylasers.network.SaberStatePacket;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class DualWeaponGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mouseButtonsAddressPhysicalHandsIncludingLeftHandedPlayers(TestContext context) {
        var player = context.createMockSurvivalPlayer();
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModBlocks.LASER_GUN));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModBlocks.LASER_SABER));
        for (Arm arm : Arm.values()) {
            player.setMainArm(arm);
            context.assertTrue(WeaponHands.arm(player, WeaponHands.attackHand(player, true)) == Arm.RIGHT, "RMB controls physical right hand");
            context.assertTrue(WeaponHands.arm(player, WeaponHands.attackHand(player, false)) == Arm.LEFT, "LMB controls physical left hand");
        }
        player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        context.assertTrue(WeaponHands.attackHand(player, false) == Hand.MAIN_HAND, "Single weapon keeps its old attack control");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gunTriggersStartAndStopIndependently(TestContext context) {
        var player = context.createMockSurvivalPlayer();
        for (Hand hand : Hand.values()) player.setStackInHand(hand, new ItemStack(ModBlocks.LASER_GUN));
        for (Hand hand : Hand.values()) context.assertTrue(new LaserGunControlPacket(hand, true).apply(player), "Both triggers accepted");
        context.assertTrue(LaserGunItem.isFiring(player, Hand.MAIN_HAND) && LaserGunItem.isFiring(player, Hand.OFF_HAND), "Both barrels fire");
        context.assertFalse(player.isUsingItem(), "Dual weapons do not share vanilla's one active item");
        new LaserGunControlPacket(Hand.MAIN_HAND, false).apply(player);
        context.assertTrue(!LaserGunItem.isFiring(player, Hand.MAIN_HAND) && LaserGunItem.isFiring(player, Hand.OFF_HAND), "Releasing main hand keeps offhand firing");
        player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        LaserGunItem.tick(player, Hand.OFF_HAND);
        context.assertFalse(LaserGunItem.isFiring(player, Hand.OFF_HAND), "Removing a weapon cancels its trigger");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 50)
    public void missingHeartbeatExpiresOnlyTheStaleHand(TestContext context) {
        var player = context.createMockSurvivalPlayer();
        player.setPosition(context.getAbsolute(new Vec3d(3.5,40,2.5))); player.setPitch(-90);
        for (Hand hand : Hand.values()) {
            player.setStackInHand(hand, new ItemStack(ModBlocks.LASER_GUN));
            LaserGunItem.startFiring(player, hand);
        }
        for (int tick = 1; tick <= 34; tick++) context.runAtTick(tick, () -> {
            LaserGunItem.startFiring(player, Hand.OFF_HAND);
            for (Hand hand : Hand.values()) LaserGunItem.tick(player, hand);
        });
        context.runAtTick(35, () -> {
            context.assertTrue(!LaserGunItem.isFiring(player, Hand.MAIN_HAND) && LaserGunItem.isFiring(player, Hand.OFF_HAND), "Each input has its own timeout");
            LaserGunItem.stopFiring(player, Hand.OFF_HAND);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void copiedFiringTagCannotStartAnUncontrolledGun(TestContext context) {
        var player = context.createMockSurvivalPlayer();
        var stack = new ItemStack(ModBlocks.LASER_GUN);
        var data = GameVersion.itemData(stack); data.putBoolean("WeaponFiring", true); GameVersion.setItemData(stack, data);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        context.assertFalse(LaserGunItem.isFiring(player, Hand.MAIN_HAND), "Server never trusts a saved visual flag");
        stack.getItem().inventoryTick(stack, context.getWorld(), player, 0, true);
        context.assertFalse(GameVersion.itemData(stack).getBoolean("WeaponFiring"), "Stale visual flags are cleared");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bothGunsDealTickRateDamageWithoutPacketRateDuplication(TestContext context) {
        var player = context.createMockSurvivalPlayer();
        player.setPosition(context.getAbsolute(new Vec3d(3.5,30,2.5))); player.setYaw(0); player.setPitch(0);
        var target = context.spawnMob(EntityType.VILLAGER, new Vec3d(3.5,30,5));
        target.setNoGravity(true); target.setAiDisabled(true);
        target.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
        for (Hand hand : Hand.values()) { player.setStackInHand(hand, new ItemStack(ModBlocks.LASER_GUN)); LaserGunItem.startFiring(player, hand); }
        int[] hits = {0};
        for (int tick = 1; tick <= 5; tick++) context.runAtTick(tick, () -> {
            context.assertTrue(LaserWeapon.trace(context.getWorld(), player.getEyePos(), player.getRotationVec(1),
                    net.askcraft.justifylasers.config.LaserConfig.get().laserGunRange, player).target() == target,
                    "Both barrels have an unobstructed ray to the intended target");
            if (LaserDamage.isHitTick(context.getWorld().getTime(), net.askcraft.justifylasers.config.LaserConfig.get().laserGunHitsPerSecond)) hits[0] += 2;
            for (Hand hand : Hand.values()) for (int spam = 0; spam < 10; spam++) {
                LaserGunItem.startFiring(player, hand); LaserGunItem.tick(player, hand);
            }
        });
        context.runAtTick(6, () -> {
            float expected = 20 - hits[0] * net.askcraft.justifylasers.config.LaserConfig.get().laserGunDamage;
            context.assertTrue(hits[0] > 0 && Math.abs(target.getHealth() - expected) < .001, "Two barrels, one hit per scheduled tick each: " + target.getHealth() + " vs " + expected);
            for (Hand hand : Hand.values()) LaserGunItem.stopFiring(player, hand);
            target.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bothSabersHaveIndependentValidatedActivePhases(TestContext context) {
        var player = context.createMockSurvivalPlayer();
        // Other suites connect players at the world's ground-level spawn during the same batch.
        player.setPosition(context.getAbsolute(new Vec3d(3.5,20,2.5))); player.setYaw(0); player.setPitch(0);
        var target = context.spawnMob(EntityType.IRON_GOLEM, new Vec3d(3.5,20,4.5));
        target.setNoGravity(true); target.setAiDisabled(true);
        target.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
        for (Hand hand : Hand.values()) {
            var stack = new ItemStack(ModBlocks.LASER_SABER); LaserSaberItem.setActive(stack, true); player.setStackInHand(hand, stack);
            context.assertTrue(SaberCombat.targets(player, hand, (LaserSaberItem)ModBlocks.LASER_SABER, 0).equals(java.util.Set.of(target)), "Only the intended target intersects this hand's full arc");
            context.assertTrue(SaberCombat.swing(player, hand), "Both hands accept a swing in the same tick");
            for (int spam = 0; spam < 10; spam++) context.assertFalse(SaberCombat.swing(player, hand), "No duplicate windup from packet spam");
        }
        for (int tick = 1; tick <= 14; tick++) context.runAtTick(tick, () -> SaberCombat.tick(player));
        context.runAtTick(15, () -> {
            float expected = 100 - 2 * ((LaserSaberItem)ModBlocks.LASER_SABER).damage();
            context.assertTrue(Math.abs(target.getHealth() - expected) < .001, "Each blade deals its own damage exactly once: " + target.getHealth());
            context.assertTrue(SaberCombat.state(player, Hand.MAIN_HAND).sequence() == 1 && SaberCombat.state(player, Hand.OFF_HAND).sequence() == 1, "Separate server sequences");
            target.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void removingOffhandSaberDoesNotCancelMainHandSwing(TestContext context) {
        var player = context.createMockSurvivalPlayer();
        for (Hand hand : Hand.values()) {
            var stack = new ItemStack(ModBlocks.LASER_SABER); LaserSaberItem.setActive(stack, true); player.setStackInHand(hand, stack);
            SaberCombat.swing(player, hand);
        }
        player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        SaberCombat.tick(player);
        context.assertTrue(SaberCombat.state(player, Hand.OFF_HAND).action() == SaberState.Action.IDLE, "Removed offhand cannot deal invisible damage");
        context.assertTrue(SaberCombat.state(player, Hand.MAIN_HAND).action() == SaberState.Action.ATTACK, "Main hand keeps its current cut");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void saberStatePacketRoundTripsItsHand(TestContext context) {
        var buffer = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var packet = new SaberStatePacket(5, 17, SaberState.idle(true, 10), SaberCombat.Contact.CLASH, new Vec3d(1,2,3), 0x77FFFF, Hand.OFF_HAND);
            packet.write(buffer);
            context.assertTrue(packet.equals(new SaberStatePacket(buffer)), "Offhand state and contact survive network serialization");
        } finally { buffer.release(); }
        context.complete();
    }
}
