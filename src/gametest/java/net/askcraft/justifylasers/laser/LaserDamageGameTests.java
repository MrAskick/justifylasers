package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class LaserDamageGameTests implements FabricGameTest {
    private static final BlockPos EMITTER_POS = new BlockPos(1, 2, 2);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void damageTypePreservesVanillaDefenses(TestContext context) {
        DamageSource source = source(context);
        context.assertTrue(source.isOf(LaserDamage.TYPE), "Laser damage type must be loaded from the data pack");
        context.assertTrue(source.isIn(DamageTypeTags.BYPASSES_COOLDOWN), "Laser must bypass hit cooldown");
        context.assertTrue(source.isIn(DamageTypeTags.NO_IMPACT), "Knockback must be handled by the beam");
        context.assertFalse(source.isIn(DamageTypeTags.BYPASSES_ARMOR), "Armor must apply");
        context.assertFalse(source.isIn(DamageTypeTags.BYPASSES_ENCHANTMENTS), "Protection must apply");
        context.assertFalse(source.isIn(DamageTypeTags.BYPASSES_EFFECTS), "Status effects must apply");
        context.assertFalse(source.isIn(DamageTypeTags.BYPASSES_RESISTANCE), "Resistance must apply");
        context.assertFalse(source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY), "Invulnerability must apply");
        context.assertFalse(source.isIn(DamageTypeTags.BYPASSES_SHIELD), "Shields must apply");
        context.assertFalse(source.isScaledWithDifficulty(), "Base damage must stay the same across difficulties");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void emitterDamagesEveryTick(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context, true);
        VillagerEntity target = target(context, new Vec3d(3.5D, 2.0D, 2.5D));
        for (int tick = 1; tick <= 20; tick++) {
            tick(context, emitter);
            close(context, 20.0D - tick * 0.5D, target.getHealth(), "Health after emitter tick " + tick);
        }
        context.assertFalse(target.isOnFire(), "Beam hits must not add fire damage");
        context.assertTrue(target.getVelocity().x > 0.0D, "Beam must push away from the emitter");
        complete(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void worldTickerAppliesDamageAtTwentyTicksPerSecond(TestContext context) {
        emitter(context, true);
        VillagerEntity target = target(context, new Vec3d(3.5D, 2.0D, 2.5D));
        float[] initialHealth = new float[1];
        context.runAtEveryTick(() -> target.setVelocity(Vec3d.ZERO));
        context.runAtTick(2, () -> initialHealth[0] = target.getHealth());
        context.runAtTick(12, () -> {
            close(context, 5.0D, initialHealth[0] - target.getHealth(), "Damage over ten real server ticks");
            complete(context);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void walkingAcrossBeamStopsDamageAfterContact(TestContext context) {
        emitter(context, true);
        VillagerEntity target = target(context, new Vec3d(3.5D, 2.0D, 1.2D));
        // NoAI also disables movement physics; spawnMob has already cleared the AI tasks.
        target.setAiDisabled(false);
        float[] healthAfterCrossing = new float[1];
        context.runAtEveryTick(() -> target.setVelocity(target.getVelocity().x, 0.0D, 0.215D));
        context.runAtTick(12, () -> {
            context.assertTrue(context.getRelative(target.getPos()).z > 3.0D,
                    "Target must pass through the beam; position=" + context.getRelative(target.getPos())
                            + ", velocity=" + target.getVelocity() + ", health=" + target.getHealth());
            context.assertTrue(target.getHealth() < 20.0F, "Crossing the beam must deal damage");
            context.assertTrue(target.getHealth() > 15.0F, "A normal crossing must not trap the target in the beam");
            healthAfterCrossing[0] = target.getHealth();
        });
        context.runAtTick(20, () -> {
            close(context, healthAfterCrossing[0], target.getHealth(), "Damage must stop after leaving the beam");
            context.assertFalse(target.isOnFire(), "Crossing must not leave a burning effect");
            complete(context);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void damageToggleDisablesDamageAndPush(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context, false);
        VillagerEntity target = target(context, new Vec3d(3.5D, 2.0D, 2.5D));
        for (int tick = 0; tick < 20; tick++) {
            tick(context, emitter);
        }
        close(context, 20.0D, target.getHealth(), "Damage-disabled emitter must not hurt entities");
        close(context, 0.0D, target.getVelocity().lengthSquared(), "Damage-disabled emitter must not push entities");
        complete(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inactiveAndRedstoneGatedEmittersDoNotHurt(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context, true);
        VillagerEntity target = target(context, new Vec3d(3.5D, 2.0D, 2.5D));
        emitter.getPropertyDelegate().set(0, 0);
        tick(context, emitter);
        close(context, 20.0D, target.getHealth(), "Disabled emitter must not hurt entities");
        emitter.getPropertyDelegate().set(0, 1);
        emitter.getPropertyDelegate().set(1, LaserRedstoneMode.HIGH.ordinal());
        tick(context, emitter);
        close(context, 20.0D, target.getHealth(), "Redstone-gated emitter must wait for a signal");
        close(context, 0.0D, target.getVelocity().lengthSquared(), "Inactive emitter must not push entities");
        complete(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void solidBlocksStopEntityDamage(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context, true);
        context.setBlockState(2, 2, 2, Blocks.OBSIDIAN);
        VillagerEntity target = target(context, new Vec3d(3.5D, 2.0D, 2.5D));
        tick(context, emitter);
        close(context, 20.0D, target.getHealth(), "A block between the emitter and target must stop damage");
        close(context, 0.0D, target.getVelocity().lengthSquared(), "An occluded target must not be pushed");
        complete(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shortBeamInsideTargetStillHits(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context, true);
        context.setBlockState(2, 2, 2, Blocks.OBSIDIAN);
        LaserBeamTrace trace = LaserBeamTrace.trace(context.getWorld(), emitter.getPos(),
                emitter.getCachedState(), LaserEmitterBlockEntity.MAX_RANGE);
        Vec3d midpoint = trace.start().add(trace.end()).multiply(0.5D).add(0.0D, -0.5D, 0.0D);
        VillagerEntity target = target(context, context.getRelative(midpoint));
        context.assertTrue(target.getBoundingBox().contains(trace.start()), "Test target must contain the beam origin");
        context.assertTrue(target.getBoundingBox().contains(trace.end()), "Test target must contain the beam end");
        tick(context, emitter);
        close(context, 19.5D, target.getHealth(), "A beam enclosed by the target hitbox must still hit");
        complete(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void collisionRadiusStillFollowsBeamThickness(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context, true);
        VillagerEntity target = target(context, new Vec3d(3.5D, 2.0D, 3.3D));
        tick(context, emitter);
        close(context, 20.0D, target.getHealth(), "Default beam must miss the offset target");
        emitter.getPropertyDelegate().set(6, LaserEmitterBlockEntity.BEAM_WIDTH_STEPS);
        tick(context, emitter);
        close(context, 19.5D, target.getHealth(), "Thick beam must hit the offset target");
        complete(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void diamondArmorUsesVanillaReductionAndDurability(TestContext context) {
        PlayerEntity unarmored = context.createMockSurvivalPlayer();
        PlayerEntity armored = armoredPlayer(context, false);
        context.assertTrue(armored.getArmor() == 20, "Diamond equipment must supply its real armor attributes");
        close(context, 8.0D, armored.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS), "Diamond toughness");
        for (int tick = 0; tick < 20; tick++) {
            hitDefault(unarmored, source(context), Direction.EAST);
            hitDefault(armored, source(context), Direction.EAST);
        }
        close(context, 10.0D, unarmored.getHealth(), "Unarmored damage over twenty ticks");
        float reducedDamage = DamageUtil.getDamageLeft(0.5F, 20.0F, 8.0F);
        close(context, 20.0D - reducedDamage * 20.0D, armored.getHealth(), "Diamond damage over twenty ticks");
        context.assertTrue(armored.getEquippedStack(EquipmentSlot.CHEST).getDamage() > 0,
                "Armor must take normal durability damage");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void protectionEnchantmentsReduceDamage(TestContext context) {
        PlayerEntity armored = armoredPlayer(context, false);
        PlayerEntity protectedPlayer = armoredPlayer(context, true);
        for (int tick = 0; tick < 20; tick++) {
            hitDefault(armored, source(context), Direction.EAST);
            hitDefault(protectedPlayer, source(context), Direction.EAST);
        }
        context.assertTrue(protectedPlayer.getHealth() > armored.getHealth(), "Protection must reduce laser damage");
        context.assertTrue(protectedPlayer.getHealth() < 20.0F, "Protection must not grant complete immunity");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void resistanceAndAbsorptionReduceDamage(TestContext context) {
        PlayerEntity resistant = context.createMockSurvivalPlayer();
        resistant.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0));
        hitDefault(resistant, source(context), Direction.EAST);
        close(context, 19.6D, resistant.getHealth(), "Resistance I must reduce damage by twenty percent");

        PlayerEntity absorbed = context.createMockSurvivalPlayer();
        absorbed.setAbsorptionAmount(1.0F);
        hitDefault(absorbed, source(context), Direction.EAST);
        close(context, 20.0D, absorbed.getHealth(), "Absorption must protect health");
        close(context, 0.5D, absorbed.getAbsorptionAmount(), "Absorption must consume incoming damage");
        hitDefault(absorbed, source(context), Direction.EAST);
        hitDefault(absorbed, source(context), Direction.EAST);
        close(context, 19.5D, absorbed.getHealth(), "Health must take damage after absorption runs out");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void invulnerabilityAndCreativeModeAreRespected(TestContext context) {
        PlayerEntity creative = context.createMockCreativePlayer();
        GameMode.CREATIVE.setAbilities(creative.getAbilities());
        PlayerEntity invulnerable = context.createMockSurvivalPlayer();
        invulnerable.setInvulnerable(true);
        for (PlayerEntity target : new PlayerEntity[]{creative, invulnerable}) {
            target.setVelocity(Vec3d.ZERO);
            hitDefault(target, source(context), Direction.EAST);
            close(context, 20.0D, target.getHealth(), "Invulnerable targets must keep their health");
            close(context, 0.0D, target.getVelocity().lengthSquared(), "Rejected hits must not push targets");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void otherDamageKeepsItsNormalCooldown(TestContext context) {
        PlayerEntity player = context.createMockSurvivalPlayer();
        DamageSource cactus = context.getWorld().getDamageSources().cactus();
        context.assertTrue(player.damage(cactus, 0.5F), "Initial cactus hit must succeed");
        hitDefault(player, source(context), Direction.EAST);
        close(context, 19.0D, player.getHealth(), "Laser must hit through the existing cooldown");
        context.assertFalse(player.damage(cactus, 0.5F), "Laser must not disable cooldown for other damage types");
        close(context, 19.0D, player.getHealth(), "Cooldown must still reject the second cactus hit");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shieldCanBlockBeamFromTheFront(TestContext context) {
        PlayerEntity player = context.createMockSurvivalPlayer();
        Vec3d position = context.getAbsolute(new Vec3d(3.5D, 2.0D, 2.5D));
        player.setPosition(position);
        player.setYaw(90.0F);
        player.setHeadYaw(90.0F);
        player.setPitch(0.0F);
        player.setNoGravity(true);
        player.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.SHIELD));
        player.setCurrentHand(Hand.MAIN_HAND);
        for (int tick = 0; tick < 6; tick++) {
            player.tick();
        }
        context.assertTrue(player.isBlocking(), "Test shield must be raised");
        player.setVelocity(Vec3d.ZERO);
        hitDefault(player, source(context), Direction.EAST);
        close(context, 20.0D, player.getHealth(), "A shield facing the emitter must block the beam");
        close(context, 0.0D, player.getVelocity().lengthSquared(), "Blocked beam must not push the player");
        player.setYaw(-90.0F);
        player.setHeadYaw(-90.0F);
        hitDefault(player, source(context), Direction.EAST);
        close(context, 19.5D, player.getHealth(), "A shield facing away from the emitter must not block the beam");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pushIsDirectionalAndCapped(TestContext context) {
        for (Direction direction : Direction.values()) {
            PlayerEntity player = context.createMockSurvivalPlayer();
            player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000.0D);
            player.setHealth(1000.0F);
            player.setVelocity(Vec3d.ZERO);
            for (int tick = 0; tick < 100; tick++) {
                hitDefault(player, source(context), direction);
            }
            Vec3d axis = Vec3d.of(direction.getVector());
            double axialSpeed = player.getVelocity().dotProduct(axis);
            close(context, 0.12D, axialSpeed, "Maximum push speed for " + direction);
            close(context, 0.0D, player.getVelocity().subtract(axis.multiply(axialSpeed)).lengthSquared(),
                    "Push must stay on the beam axis for " + direction);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pushPreservesCrossingAndExistingMomentum(TestContext context) {
        PlayerEntity player = context.createMockSurvivalPlayer();
        player.setVelocity(0.0D, 0.0D, 0.215D);
        hitDefault(player, source(context), Direction.EAST);
        close(context, 0.215D, player.getVelocity().z, "Crossing movement must not be damped");
        close(context, 0.0D, player.getVelocity().y, "Horizontal beams must not launch the player upward");
        close(context, 0.025D, player.getVelocity().x, "Initial push must be gentle");

        player.setVelocity(-0.215D, 0.0D, 0.0D);
        hitDefault(player, source(context), Direction.EAST);
        context.assertTrue(player.getVelocity().x < 0.0D, "The player must be able to walk against the beam");

        player.setVelocity(0.3D, 0.0D, 0.0D);
        hitDefault(player, source(context), Direction.EAST);
        close(context, 0.3D, player.getVelocity().x, "The push cap must not clamp existing movement");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void knockbackResistanceReducesPush(TestContext context) {
        PlayerEntity player = context.createMockSurvivalPlayer();
        player.setVelocity(Vec3d.ZERO);
        player.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(0.6D);
        hitDefault(player, source(context), Direction.EAST);
        close(context, 0.01D, player.getVelocity().x, "Knockback resistance must scale the impulse");
        player.setVelocity(Vec3d.ZERO);
        player.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        hitDefault(player, source(context), Direction.EAST);
        close(context, 0.0D, player.getVelocity().lengthSquared(), "Full knockback resistance must prevent push");
        close(context, 19.0D, player.getHealth(), "Knockback resistance must not grant damage resistance");
        context.complete();
    }

    private static LaserEmitterBlockEntity emitter(TestContext context, boolean damageEnabled) {
        BlockState state = ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST);
        context.setBlockState(EMITTER_POS, state);
        context.setBlockState(6, 2, 2, Blocks.OBSIDIAN);
        LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) context.getBlockEntity(EMITTER_POS);
        emitter.getPropertyDelegate().set(4, damageEnabled ? 1 : 0);
        return emitter;
    }

    private static VillagerEntity target(TestContext context, Vec3d position) {
        VillagerEntity target = context.spawnMob(EntityType.VILLAGER, position);
        target.setAiDisabled(true);
        target.setNoGravity(true);
        return target;
    }

    private static void tick(TestContext context, LaserEmitterBlockEntity emitter) {
        LaserEmitterBlockEntity.serverTick(context.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
    }

    private static DamageSource source(TestContext context) {
        return LaserDamage.source(context.getWorld(), context.getAbsolute(Vec3d.ofCenter(EMITTER_POS)));
    }

    private static void hitDefault(LivingEntity target, DamageSource source, Direction direction) {
        LaserDamage.hit(target, source, direction, 0.5F, 1.0D);
    }

    private static PlayerEntity armoredPlayer(TestContext context, boolean protection) {
        PlayerEntity player = context.createMockSurvivalPlayer();
        ItemStack[] armor = {new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.DIAMOND_LEGGINGS),
                new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_HELMET)};
        EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        for (int index = 0; index < armor.length; index++) {
            if (protection) {
                armor[index].addEnchantment(Enchantments.PROTECTION, 4);
            }
            player.equipStack(slots[index], armor[index]);
        }
        player.tick();
        return player;
    }

    private static void close(TestContext context, double expected, double actual, String message) {
        context.assertTrue(Math.abs(expected - actual) < 0.0001D,
                message + ": expected " + expected + ", got " + actual);
    }

    private static void complete(TestContext context) {
        context.removeBlock(EMITTER_POS);
        context.complete();
    }
}
