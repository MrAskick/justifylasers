package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.JustifyLasers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class LaserDamage {
    public static final RegistryKey<DamageType> TYPE = RegistryKey.of(
            RegistryKeys.DAMAGE_TYPE, JustifyLasers.id("laser"));
    public static final int MIN_DAMAGE_STEP = 1;
    public static final int MAX_DAMAGE_STEP = 200;
    public static final int DEFAULT_DAMAGE_STEP = 5;
    public static final int MAX_KNOCKBACK_STEP = 100;
    public static final int DEFAULT_KNOCKBACK_STEP = 10;
    public static final int MAX_HITS_PER_SECOND = 20;

    private static final double BASE_PUSH = 0.025D;
    private static final double MAX_PUSH_SPEED = 0.12D;

    private LaserDamage() {
    }

    public static DamageSource source(World world, Vec3d origin) {
        return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(TYPE), origin);
    }

    public static int clampDamageStep(int step) {
        return MathHelper.clamp(step, MIN_DAMAGE_STEP, MAX_DAMAGE_STEP);
    }

    public static float damageForStep(int step) {
        return clampDamageStep(step) / 10.0F;
    }

    public static int clampKnockbackStep(int step) {
        return MathHelper.clamp(step, 0, MAX_KNOCKBACK_STEP);
    }

    public static double knockbackForStep(int step) {
        return clampKnockbackStep(step) / 10.0D;
    }

    public static int clampHitsPerSecond(int hits) {
        return MathHelper.clamp(hits, 1, MAX_HITS_PER_SECOND);
    }

    public static boolean isHitTick(long tick, int hitsPerSecond) {
        int rate = clampHitsPerSecond(hitsPerSecond);
        // Distribute hits over 20 ticks without rounding rates such as 7/s to a fixed interval.
        int phase = (int) Math.floorMod(tick, 20L);
        return phase * rate % 20 < rate;
    }

    public static boolean hit(LivingEntity target, DamageSource source, Direction direction,
                           float damage, double knockbackMultiplier) {
        return hit(target, source, Vec3d.of(direction.getVector()), damage, knockbackMultiplier);
    }

    public static boolean hit(LivingEntity target, DamageSource source, Vec3d direction,
                              float damage, double knockbackMultiplier) {
        // The damage type bypasses only hit cooldown, leaving vanilla damage reduction intact.
        if (!target.damage(source, damage)) {
            return false;
        }

        double strength = BASE_PUSH * knockbackMultiplier
                * (1.0D - target.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE));
        Vec3d axis = direction.normalize();
        double impulse = Math.min(strength,
                MAX_PUSH_SPEED * knockbackMultiplier - target.getVelocity().dotProduct(axis));
        if (impulse > 0.0D) {
            // Add a bounded impulse without halving movement or adding vanilla's upward kick.
            target.addVelocity(axis.x * impulse, axis.y * impulse, axis.z * impulse);
            target.velocityModified = true;
        }
        return true;
    }
}
