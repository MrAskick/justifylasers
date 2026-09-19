package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.entity.LaserTurretBlockEntity;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class LaserWeaponGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void controlPacketsRequireAHeldGunAndDoNotDealPacketRateDamage(TestContext context) {
        var player = context.createMockSurvivalPlayer();
        var start = new net.askcraft.justifylasers.network.LaserGunControlPacket(net.minecraft.util.Hand.MAIN_HAND, true);
        context.assertFalse(start.apply(player), "A forged start packet cannot fire an absent gun");
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(ModBlocks.LASER_GUN));
        float health = player.getHealth();
        for (int i = 0; i < 100; i++) context.assertTrue(start.apply(player), "Held gun accepts its input");
        context.assertTrue(player.isUsingItem() && player.getHealth() == health, "Packets change input state, never apply hit-rate damage");
        new net.askcraft.justifylasers.network.LaserGunControlPacket(net.minecraft.util.Hand.OFF_HAND, false).apply(player);
        context.assertTrue(player.isUsingItem(), "A stop for the other hand cannot cancel this weapon");
        new net.askcraft.justifylasers.network.LaserGunControlPacket(net.minecraft.util.Hand.MAIN_HAND, false).apply(player);
        context.assertFalse(player.isUsingItem(), "Release stops immediately");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 50)
    public void missingGunInputHeartbeatStopsFire(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(ModBlocks.LASER_GUN));
        net.askcraft.justifylasers.item.LaserGunItem.startFiring(player, net.minecraft.util.Hand.MAIN_HAND);
        // The mock connection is not in ServerNetworkIo, which normally calls playerTick.
        for (int tick = 1; tick <= 34; tick++) context.runAtTick(tick, player::playerTick);
        context.runAtTick(35, () -> {
            context.assertFalse(player.isUsingItem(), "Stale network input must stop within 31 ticks");
            player.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gunStopsAtNearestEntityOrOpaqueWall(TestContext context) {
        var world = context.getWorld();
        Vec3d start = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 2)));
        var first = context.spawnMob(EntityType.VILLAGER, new Vec3d(3.5, 2, 2.5));
        var second = context.spawnMob(EntityType.VILLAGER, new Vec3d(5.5, 2, 2.5));
        var shot = LaserWeapon.trace(world, start, new Vec3d(1, 0, 0), 8, null);
        context.assertTrue(shot.target() == first, "The front entity stops the shot");
        context.assertTrue(LaserWeapon.trace(world, start, new Vec3d(1, 0, 0), 8, first).target() == second, "The shooter is excluded from its own ray");
        context.setBlockState(2, 2, 2, Blocks.OBSIDIAN);
        shot = LaserWeapon.trace(world, start, new Vec3d(1, 0, 0), 8, null);
        context.assertTrue(shot.target() == null && shot.beam().hasBlockHit(), "No damage through a wall");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void weaponDamageRetainsArmorResistanceAndAttacker(TestContext context) {
        var unarmored = context.createMockSurvivalPlayer();
        var armored = context.createMockSurvivalPlayer();
        var resistant = context.createMockSurvivalPlayer();
        armored.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(20);
        armored.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS).setBaseValue(8);
        resistant.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 1));
        var shooter = context.createMockSurvivalPlayer();
        var ray = LaserBeamTrace.traceFrom(context.getWorld(), context.getAbsolute(new Vec3d(1, 2, 1)), new Vec3d(1, 0, 0), 1);
        for (var target : new net.minecraft.entity.LivingEntity[]{unarmored, armored, resistant})
            LaserWeapon.damage(context.getWorld(), new LaserWeapon.Hit(ray, target), shooter);
        context.assertTrue(unarmored.getHealth() < 20 && armored.getHealth() > unarmored.getHealth(), "Armor reduces gun damage");
        context.assertTrue(resistant.getHealth() > unarmored.getHealth(), "Resistance reduces gun damage");
        context.assertTrue(unarmored.getAttacker() == shooter, "Combat credit remains attached to the shooter");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void turretDefaultsToHostilesAndRequiresTheFilterForOtherCategories(TestContext context) {
        var turret = turret(context);
        var hostile = context.spawnMob(EntityType.ZOMBIE, new Vec3d(5, 2, 2));
        var passive = context.spawnMob(EntityType.COW, new Vec3d(5, 2, 4));
        context.assertTrue(turret.allows(hostile) && !turret.allows(passive), "Safe default: hostile mobs only");
        turret.toggle(2);
        context.assertFalse(turret.allows(passive), "Cannot enable passive targets without a module");
        turret.setStack(1, new ItemStack(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER)));
        turret.toggle(2);
        context.assertTrue(turret.allows(passive), "Installed filter enables its category controls");
        turret.removeStack(1);
        context.assertFalse(turret.allows(passive), "Removing the filter restores hostile-only behavior");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void turretInventoryAndOwnershipSurviveSaveLoad(TestContext context) {
        var turret = turret(context);
        var owner = context.createMockSurvivalPlayer();
        owner.setPosition(Vec3d.ofCenter(turret.getPos()));
        turret.setOwner(owner);
        turret.setStack(0, new ItemStack(ModBlocks.LASER_GUN));
        turret.setStack(1, new ItemStack(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER)));
        turret.toggle(5); turret.toggle(0); turret.toggle(2);
        var saved = turret.createNbt();
        turret.clear(); turret.toggle(0);
        turret.readNbt(saved);
        context.assertTrue(turret.hasGun() && turret.hasFilter() && !turret.enabled(), "Inventory and power saved");
        context.assertTrue(turret.canPlayerUse(owner) && (turret.filter().flags() & 2) != 0, "Owner and filter saved");
        context.assertTrue(GameVersion.cubeColor(turret.getStack(0)) == 1, "Weapon color saved");
        owner.setPosition(owner.getPos().add(20, 0, 0));
        context.assertFalse(turret.canPlayerUse(owner), "Opening a screen remotely is not allowed");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 50)
    public void turretAimsAndFiresWithoutEnergyButStopsAfterGunRemoval(TestContext context) {
        // A reused plot can contain passive mobs that legitimately block the turret's line of fire.
        var plot = new Box(context.getAbsolute(Vec3d.ZERO), context.getAbsolute(new Vec3d(8, 8, 8)));
        context.getWorld().getOtherEntities(null, plot, entity -> !(entity instanceof net.minecraft.entity.player.PlayerEntity))
                .forEach(net.minecraft.entity.Entity::discard);
        var turret = turret(context);
        turret.setStack(0, new ItemStack(ModBlocks.LASER_GUN));
        var zombie = context.spawnMob(EntityType.ZOMBIE, new Vec3d(5.5, 2, 2.5));
        zombie.setAiDisabled(true);
        zombie.setNoGravity(true);
        zombie.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
        zombie.setFireTicks(0);
        context.setBlockState(5, 4, 2, Blocks.STONE);
        context.runAtTick(20, () -> {
            context.assertTrue(turret.firing() && zombie.getHealth() < 20, "Turret rotates and damages without an energy inventory: health=" + zombie.getHealth()
                    + ", position=" + zombie.getPos() + ", firing=" + turret.firing() + ", ray="
                    + LaserWeapon.trace(context.getWorld(), turret.pivot(), turret.direction(1), 32, null));
            turret.removeStack(0);
        });
        context.runAtTick(22, () -> {
            context.assertFalse(turret.firing(), "A stand without a gun cannot fire");
            context.removeBlock(new BlockPos(2, 2, 2));
            zombie.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void turretSnapshotsReplaceRemovedSlotsInsteadOfMergingInventories(TestContext context) {
        var server = turret(context);
        var client = new LaserTurretBlockEntity(server.getPos(), server.getCachedState());
        server.setStack(0, new ItemStack(ModBlocks.LASER_GUN));
        server.setStack(1, new ItemStack(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER)));
        client.readNbt(server.createNbt());
        context.assertTrue(client.hasGun() && client.hasFilter(), "Initial snapshot installs both visible items");
        server.removeStack(0, 1);
        client.readNbt(server.createNbt());
        context.assertFalse(client.hasGun(), "A removed gun must disappear on an already loaded client turret");
        context.assertTrue(client.hasFilter(), "Clearing the gun must preserve the other slot");
        server.removeStack(1);
        client.readNbt(server.createNbt());
        context.assertTrue(client.isEmpty(), "An empty snapshot clears every old slot");
        server.setStack(0, new ItemStack(ModBlocks.LASER_GUN));
        client.readNbt(server.createNbt());
        context.assertTrue(client.hasGun(), "Reinserting the gun restores its model");
        server.clear();
        client.readNbt(server.createNbt());
        context.assertTrue(client.isEmpty(), "Inventory.clear also propagates to the rendered copy");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void turretDropsInstalledItemsExactlyOnce(TestContext context) {
        var turret = turret(context);
        turret.setStack(0, new ItemStack(ModBlocks.LASER_GUN));
        turret.setStack(1, new ItemStack(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER)));
        BlockPos absolute = turret.getPos();
        context.removeBlock(new BlockPos(2, 2, 2));
        var drops = context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(absolute).expand(1), item -> true);
        context.assertTrue(drops.stream().filter(item -> item.getStack().isOf(ModBlocks.LASER_GUN)).mapToInt(item -> item.getStack().getCount()).sum() == 1, "Exactly one gun drops");
        context.assertTrue(drops.stream().filter(item -> item.getStack().isOf(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER))).mapToInt(item -> item.getStack().getCount()).sum() == 1, "Exactly one filter drops");
        context.complete();
    }

    private static LaserTurretBlockEntity turret(TestContext context) {
        context.setBlockState(2, 2, 2, ModBlocks.LASER_TURRET);
        return (LaserTurretBlockEntity) context.getBlockEntity(new BlockPos(2, 2, 2));
    }
}
