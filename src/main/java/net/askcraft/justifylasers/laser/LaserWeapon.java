package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.config.LaserConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class LaserWeapon {
    public record Hit(LaserBeamTrace beam, @Nullable LivingEntity target) { }

    public static Hit trace(World world, Vec3d start, Vec3d axis, double range, @Nullable Entity shooter) {
        LaserBeamTrace beam = LaserBeamTrace.traceFrom(world, start, axis, range);
        Vec3d end = beam.end();
        LivingEntity closest = null;
        double distance = start.squaredDistanceTo(end);
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, new Box(start, end).expand(0.15),
                entity -> entity != shooter && entity.isAlive() && !entity.isSpectator()
                        && !(entity instanceof PlayerEntity player && player.isCreative()))) {
            Box bounds = entity.getBoundingBox().expand(0.025);
            var intersection = bounds.contains(start) ? java.util.Optional.of(start) : bounds.raycast(start, end);
            if (intersection.isPresent() && start.squaredDistanceTo(intersection.get()) < distance) {
                distance = start.squaredDistanceTo(intersection.get());
                end = intersection.get();
                closest = entity;
            }
        }
        if (closest != null) beam = new LaserBeamTrace(start, end, beam.direction(), null);
        return new Hit(beam, closest);
    }

    public static boolean damage(World world, Hit hit, @Nullable Entity shooter) {
        if (world.isClient || hit.target() == null || !LaserDamage.isHitTick(world.getTime(), LaserConfig.get().laserGunHitsPerSecond)) return false;
        var type = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(LaserDamage.TYPE);
        DamageSource source = shooter == null ? LaserDamage.source(world, hit.beam().start()) : new DamageSource(type, shooter);
        return LaserDamage.hit(hit.target(), source, hit.beam().axis(), LaserConfig.get().laserGunDamage, LaserConfig.get().laserGunKnockback);
    }

    private LaserWeapon() { }
}
