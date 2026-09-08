package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class LaserIgnitionGameTests implements FabricGameTest {
    private static final BlockPos EMITTER = new BlockPos(1, 2, 2);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ignitionIsOptionalAndRequiresDamage(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context);
        VillagerEntity target = target(context);
        tick(context, emitter);
        context.assertFalse(target.isOnFire(), "Ignition must default to off");
        emitter.getPropertyDelegate().set(12, 1);
        tick(context, emitter);
        context.assertTrue(target.isOnFire(), "Enabled ignition must set the target on fire");
        context.assertTrue(target.getFireTicks() <= 80 && target.getFireTicks() > 0, "Burn duration must be at most four seconds");
        target.extinguish();
        emitter.getPropertyDelegate().set(4, 0);
        tick(context, emitter);
        context.assertFalse(target.isOnFire(), "Damage toggle must also disable ignition");
        context.removeBlock(EMITTER);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void invulnerableAndFireImmuneEntitiesDoNotIgnite(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context);
        emitter.getPropertyDelegate().set(12, 1);
        VillagerEntity invulnerable = target(context);
        invulnerable.setInvulnerable(true);
        BlazeEntity immune = context.spawnMob(EntityType.BLAZE, new Vec3d(4.5D, 2.0D, 2.5D));
        immune.setAiDisabled(true);
        immune.setNoGravity(true);
        tick(context, emitter);
        context.assertFalse(invulnerable.isOnFire(), "Rejected damage must not ignite a target");
        context.assertTrue(immune.isFireImmune(), "Blaze must be fire immune");
        context.assertTrue(immune.getFireTicks() <= 0, "Fire-immune target must not be ignited");
        context.removeBlock(EMITTER);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fireResistanceProtectsFromFireButNotTheBeam(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context);
        emitter.getPropertyDelegate().set(12, 1);
        LivingEntity target = target(context);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 0));
        tick(context, emitter);
        context.assertTrue(target.getHealth() == 19.5F, "Fire resistance must not turn laser damage off");
        context.assertFalse(target.damage(context.getWorld().getDamageSources().onFire(), 1.0F), "Fire resistance must reject fire damage");
        context.removeBlock(EMITTER);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ignitionSettingSurvivesSavingWithoutChangingOldSaves(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context);
        emitter.getPropertyDelegate().set(12, 1);
        NbtCompound saved = emitter.createNbt();
        LaserEmitterBlockEntity restored = new LaserEmitterBlockEntity(EMITTER, emitter.getCachedState());
        restored.readNbt(saved);
        context.assertTrue(restored.getPropertyDelegate().get(12) == 1, "Saved ignition setting");
        saved.remove("IgniteEntities");
        restored.readNbt(saved);
        context.assertTrue(restored.getPropertyDelegate().get(12) == 0, "Old saves must keep ignition disabled");
        context.removeBlock(EMITTER);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void resetRestoresAllDamageDefaultsAtomically(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context);
        emitter.getPropertyDelegate().set(9, 120);
        emitter.getPropertyDelegate().set(10, 80);
        emitter.getPropertyDelegate().set(11, 3);
        emitter.getPropertyDelegate().set(12, 1);
        emitter.handleButton(LaserEmitterScreenHandler.BUTTON_RESET_DAMAGE_SETTINGS);
        context.assertTrue(emitter.getPropertyDelegate().get(9) == 5, "Default damage after reset");
        context.assertTrue(emitter.getPropertyDelegate().get(10) == 10, "Default knockback after reset");
        context.assertTrue(emitter.getPropertyDelegate().get(11) == 20, "Default hit rate after reset");
        context.assertTrue(emitter.getPropertyDelegate().get(12) == 0, "Ignition must be off after reset");
        context.assertTrue(emitter.getPropertyDelegate().get(4) == 1, "Reset must not toggle entity damage");
        context.removeBlock(EMITTER);
        context.complete();
    }

    private static LaserEmitterBlockEntity emitter(TestContext context) {
        context.setBlockState(EMITTER, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        context.setBlockState(6, 2, 2, Blocks.OBSIDIAN);
        LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) context.getBlockEntity(EMITTER);
        emitter.getPropertyDelegate().set(4, 1);
        return emitter;
    }

    private static VillagerEntity target(TestContext context) {
        VillagerEntity target = context.spawnMob(EntityType.VILLAGER, new Vec3d(3.5D, 2.0D, 2.5D));
        target.setAiDisabled(true);
        target.setNoGravity(true);
        return target;
    }

    private static void tick(TestContext context, LaserEmitterBlockEntity emitter) {
        LaserEmitterBlockEntity.serverTick(context.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
    }
}
