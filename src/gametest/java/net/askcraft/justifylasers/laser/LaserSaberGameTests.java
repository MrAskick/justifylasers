package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class LaserSaberGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void returningStaffBladeAddsHorizontalCoverage(TestContext context) {
        var player = player(context, true);
        var target = context.spawnMob(EntityType.VILLAGER, new Vec3d(5.0, 2, 4.3));
        var staff = (LaserSaberItem) player.getMainHandStack().getItem();
        context.assertFalse(SaberCombat.targets(player, staff, 0).contains(target), "Descending stroke does not reach the lateral target");
        context.assertTrue(SaberCombat.targets(player, staff, 2).contains(target), "The returning blade's horizontal cut reaches the lateral target");
        context.complete();
    }

    private PlayerEntity player(TestContext context, boolean staff) {
        var player = context.createMockSurvivalPlayer();
        player.setPosition(context.getAbsolute(new Vec3d(3.5, 2, 2.5)));
        player.setYaw(0);
        player.setPitch(0);
        ItemStack stack = new ItemStack(staff ? ModBlocks.LIGHT_STAFF : ModBlocks.LASER_SABER);
        LaserSaberItem.setActive(stack, true);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return player;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oneSwingCannotDealPacketRateDamage(TestContext context) {
        var player = player(context, false);
        var target = context.spawnMob(EntityType.VILLAGER, new Vec3d(3.5, 2, 4.5));
        target.setNoGravity(true); target.setAiDisabled(true);
        target.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
        context.assertTrue(SaberCombat.swing(player, Hand.MAIN_HAND), "First swing responds immediately");
        context.assertTrue(target.getHealth() == 20, "Windup cannot damage the target");
        for (int i = 0; i < 100; i++) context.assertFalse(SaberCombat.swing(player, Hand.MAIN_HAND), "No packet spam attacks");
        for (int tick = 1; tick <= 14; tick++) context.runAtTick(tick, () -> SaberCombat.tick(player));
        context.runAtTick(15, () -> {
            context.assertTrue(target.getHealth() == 20 - ((LaserSaberItem)player.getMainHandStack().getItem()).damage(),
                    "Exactly one damage application during the active phase; health=" + target.getHealth());
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inactiveOffhandAndActivationSwingsCannotAttack(TestContext context) {
        var player = player(context, false);
        context.assertFalse(SaberCombat.swing(player, Hand.OFF_HAND), "An empty offhand cannot attack");
        LaserSaberItem.setActive(player.getMainHandStack(), false);
        context.assertFalse(SaberCombat.swing(player, Hand.MAIN_HAND), "Retracted blade cannot cut");
        player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
        context.assertTrue(LaserSaberItem.active(player.getMainHandStack()), "Use ignites the blade");
        context.assertTrue(SaberCombat.state(player).guarding(), "Use enters guard, not an attack");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void wallsRangeAndTheBackOfThePlayerAreProtected(TestContext context) {
        var player = player(context, true);
        var behind = context.spawnMob(EntityType.VILLAGER, new Vec3d(3.5, 2, 0.5));
        var distant = context.spawnMob(EntityType.VILLAGER, new Vec3d(3.5, 2, 7));
        var covered = context.spawnMob(EntityType.VILLAGER, new Vec3d(3.5, 2, 4.5));
        for (var mob : new net.minecraft.entity.mob.MobEntity[]{behind, distant, covered}) { mob.setNoGravity(true); mob.setAiDisabled(true); }
        for (int x = 0; x <= 7; x++) for (int y = 2; y <= 5; y++) context.setBlockState(x, y, 3, Blocks.OBSIDIAN);
        SaberCombat.swing(player, Hand.MAIN_HAND);
        for (int tick = 1; tick <= 16; tick++) context.runAtTick(tick, () -> SaberCombat.tick(player));
        context.runAtTick(17, () -> {
            context.assertTrue(behind.getHealth() == 20 && distant.getHealth() == 20 && covered.getHealth() == 20,
                    "The active arc cannot hit behind the player, outside reach, or through a wall");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void swordAndStaffRespectArmorAndResistance(TestContext context) {
        for (boolean staff : new boolean[]{false, true}) {
            var attacker = player(context, staff);
            float[] health = new float[3];
            for (int i = 0; i < 3; i++) {
                var target = context.spawnMob(EntityType.VILLAGER, new Vec3d(3.5, 2, 4.5));
                if (i == 1) {
                    target.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(20);
                    target.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS).setBaseValue(8);
                }
                if (i == 2) target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 1));
                var source = new net.minecraft.entity.damage.DamageSource(context.getWorld().getRegistryManager()
                        .get(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).entryOf(LaserDamage.TYPE), attacker);
                LaserDamage.hit(target, source, new Vec3d(0, 0, 1), ((LaserSaberItem) attacker.getMainHandStack().getItem()).damage(), 4);
                health[i] = target.getHealth();
                context.assertTrue(target.getAttacker() == attacker, "Kill credit stays with the wielder");
                target.discard();
            }
            context.assertTrue(health[0] < 20 && health[1] > health[0] && health[2] > health[0], "Armor and resistance mitigate saber damage");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void colorCyclesAndBladeStateSurviveStackCopies(TestContext context) {
        var player = player(context, false);
        player.setSneaking(true);
        for (int i = 1; i <= LaserColor.values().length; i++) {
            player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
            var copy = player.getMainHandStack().copy();
            context.assertTrue(GameVersion.cubeColor(copy) == i % LaserColor.values().length, "Color cycles through the laser palette");
            context.assertTrue(LaserSaberItem.active(copy), "Color changes do not retract the blade");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void staffHasTwoOppositeSurfaceContacts(TestContext context) {
        var player = player(context, true);
        var item = (LaserSaberItem) player.getMainHandStack().getItem();
        var frame = new SaberPose.Frame(context.getAbsolute(new Vec3d(3.5, 3.5, 3.5)), new Vec3d(1, 0, 0),
                context.getAbsolute(new Vec3d(3.5, 3.5, 3.5)), new Vec3d(0, 0, 1), new Vec3d(0, 1, 0), new Vec3d(1, 0, 0));
        context.setBlockState(5, 3, 3, Blocks.STONE);
        context.setBlockState(1, 3, 3, Blocks.STONE);
        var a = frame.blade(player, item, 1, 1);
        var b = frame.blade(player, item, -1, 1);
        context.assertTrue(a.hasBlockHit() && b.hasBlockHit(), "Both blades stop at surfaces");
        var marks = new LaserScorchMarks();
        marks.tick(context.getWorld(), java.util.List.of(new LaserScorchMarks.WeaponContact(player.getUuid(), 1, a, 0.026),
                new LaserScorchMarks.WeaponContact(player.getUuid(), 2, b, 0.026)));
        context.assertTrue(marks.marks().size() >= 2, "Both staff ends produce scorch patches");
        context.complete();
    }
}
