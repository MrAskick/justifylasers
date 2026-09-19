package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Per-source hit and mining limits also apply to rays split, redirected or modified in the world. */
public final class LaserBeamEffects {
    private static final Map<Entity, Long> SUPPORTED = new WeakHashMap<>();
    private static final Map<Entity, Long> HEALED = new WeakHashMap<>();
    private static final Map<ServerWorld, Map<BlockPos, Long>> MINING_TICKS = new WeakHashMap<>();
    private static final Map<ServerWorld, Map<MiningTarget, Heating>> MINING_HEAT = new WeakHashMap<>();
    private record MiningTarget(BlockPos module, BlockPos block) { }
    private final Map<BlockPos, Heating> heating = new HashMap<>();
    private long lastTick = Long.MIN_VALUE;
    private long lastMined = Long.MIN_VALUE;

    public void tick(LaserBeamSource source, LaserBeamPath path) {
        if (!(source.beamWorld() instanceof ServerWorld world) || lastTick == source.getTicks()) return;
        lastTick = source.getTicks();
        var hit = new HashSet<Hit>();
        var heated = new HashSet<BlockPos>();
        for (LaserBeamTrace ray : path.segments()) {
            affectEntities(world, source, ray, hit);
            if (!ray.behavior().mining().enabled() || !ray.hasBlockHit()) continue;
            BlockState state = world.getBlockState(ray.hitBlock());
            if (state.getBlock() instanceof LaserReceiverBlock && state.get(LaserReceiverBlock.FACING) == ray.hitSide()
                    || LaserBeamPath.isOpticalInput(world, state, ray)) continue;
            heated.add(ray.hitBlock());
            mine(world, source, ray, state);
        }
        heating.keySet().removeIf(pos -> {
            if (heated.contains(pos)) return false;
            world.setBlockBreakingInfo(breakerId(source, pos), pos, -1);
            return true;
        });
    }

    private record Hit(UUID target, int index, BeamBehavior.EntityEffect effect) { }
    private static final Map<Entity, Motion> MOTION = new WeakHashMap<>();
    private static final class Motion {
        final long tick;
        final Vec3d initial;
        double desired;
        Motion(long tick, Vec3d initial) { this.tick = tick; this.initial = initial; }
    }

    private void affectEntities(ServerWorld world, LaserBeamSource source, LaserBeamTrace ray, HashSet<Hit> hit) {
        var settings = ray.behavior().entities();
        if (settings.isEmpty() || ray.power() <= 0) return;
        double radius = LaserEmitterBlockEntity.BEAM_HIT_RADIUS * source.getBeamWidthScale()
                * ray.behavior().widthMultiplier() * Math.sqrt(ray.power());
        var filter = ray.behavior().filter();
        var damageSource = LaserDamage.source(world, ray.start());
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(ray.start(), ray.end()).expand(radius + .35),
                entity -> entity.isAlive() && !entity.isSpectator() && (filter == null || filter.allows(entity, ray.behavior().owner())))) {
            Box bounds = target.getBoundingBox().expand(radius);
            if (!(bounds.contains(ray.start()) || bounds.raycast(ray.start(), ray.end()).isPresent())) continue;
            double vertical = 0;
            boolean motion = false;
            for (int index = 0; index < settings.size(); index++) {
                var effect = settings.get(index);
                if (effect.intensity() <= 0 || !effect.mode().movesEntities() && effect.mode() != LaserEntityMode.HEAL
                        && !LaserDamage.isHitTick(source.getTicks() - 1, effect.frequency())
                        || !hit.add(new Hit(target.getUuid(), index, effect))) continue;
                double intensity = ray.power() * effect.intensity();
                float amount = (float) (LaserDamage.damageForStep(effect.strength()) * intensity);
                switch (effect.mode()) {
                    case DAMAGE -> {
                        if (LaserDamage.hit(target, damageSource, ray.axis(), amount,
                                LaserDamage.knockbackForStep(effect.knockback()) * intensity) && effect.ignite() && !target.isFireImmune()) {
                            int previousFire = Math.max(0, target.getFireTicks());
                            target.setOnFireFor(4);
                            if (intensity < 1) target.setFireTicks(Math.max(previousFire, (int) (target.getFireTicks() * intensity)));
                        }
                    }
                    case HEAL -> {
                        // Combined rays have several source contributions, not several healing operations.
                        Long previous = HEALED.get(target);
                        if (previous == null || world.getTime() - previous >= 20) {
                            target.heal(.1F);
                            HEALED.put(target, world.getTime());
                        }
                    }
                    case LIFT, LOWER -> {
                        vertical += movementSpeed(effect.strength()) * intensity * (effect.mode() == LaserEntityMode.LIFT ? 1 : -1);
                        motion = true;
                    }
                    default -> { }
                }
            }
            if (motion) move(world, target, vertical);
        }
    }

    private static void move(ServerWorld world, LivingEntity target, double desired) {
        Motion movement = MOTION.get(target);
        if (movement == null || movement.tick != world.getTime()) {
            movement = new Motion(world.getTime(), target.getVelocity());
            MOTION.put(target, movement);
        }
        movement.desired += desired;
        Vec3d initial = movement.initial;
        if (Math.abs(movement.desired) < 1e-8) {
            target.setVelocity(initial);
            SUPPORTED.remove(target);
        } else {
            double change = MathHelper.clamp(MathHelper.clamp(movement.desired, -1, 1) - initial.y, -.06, .12);
            target.setVelocity(initial.x, initial.y + change + (target.hasNoGravity() ? 0 : .08), initial.z);
            target.fallDistance = 0;
            SUPPORTED.put(target, world.getTime());
        }
        target.velocityModified = true;
    }

    public static double movementSpeed(int strength) { return Math.min(.5, LaserDamage.clampDamageStep(strength) / 100.0); }

    public static boolean supports(Entity entity) {
        Long tick = SUPPORTED.get(entity);
        long now = entity.getWorld().getTime();
        return tick != null && tick <= now && now - tick <= 1;
    }

    private void mine(ServerWorld world, LaserBeamSource source, LaserBeamTrace ray, BlockState state) {
        BlockPos pos = ray.hitBlock();
        if (lastMined == world.getTime()) return;
        float hardness = state.getHardness(world, pos);
        if (state.isAir() || hardness < 0 || state.isIn(ModBlocks.LASER_PROOF)) return;
        var settings = ray.behavior().mining();
        BlockPos origin = settings.origin() == null ? source.beamPosition() : settings.origin();
        boolean shared = !origin.equals(source.beamPosition());
        var limits = MINING_TICKS.computeIfAbsent(world, ignored -> new HashMap<>());
        limits.entrySet().removeIf(entry -> entry.getValue() < world.getTime());
        if (shared && limits.getOrDefault(origin, Long.MIN_VALUE) == world.getTime()) return;
        var targets = MINING_HEAT.computeIfAbsent(world, ignored -> new HashMap<>());
        targets.values().removeIf(value -> value.lastHeated < world.getTime() - 1);
        var key = new MiningTarget(origin, pos.toImmutable());
        Heating target = shared ? targets.computeIfAbsent(key, ignored -> new Heating(state))
                : heating.computeIfAbsent(pos.toImmutable(), ignored -> new Heating(state));
        if (target.state != state) { target = new Heating(state); if (shared) targets.put(key, target); }
        heating.put(pos.toImmutable(), target);
        if (target.lastHeated == world.getTime() && (!source.beamPosition().equals(target.lastSource) || target.lastSourceTick == source.getTicks())) return;
        target.lastHeated = world.getTime();
        target.lastSource = source.beamPosition();
        target.lastSourceTick = source.getTicks();
        // The operation is paid in full. Residual flux must not impose a second speed penalty.
        target.heat += ray.workPower();
        int required = LaserMining.ticksToBreak(hardness, settings.speed());
        int stage = Math.min(9, (int) (target.heat / required * 10));
        if (stage != target.stage) { world.setBlockBreakingInfo(breakerId(source, pos), pos, stage); target.stage = stage; }
        if (target.heat + 1e-9 < required) return;
        world.setBlockBreakingInfo(breakerId(source, pos), pos, -1);
        BlockPos destination = ray.behavior().collector();
        LaserLootCollector collector = settings.collect() && destination != null && world.isChunkLoaded(destination)
                && world.getBlockEntity(destination) instanceof LaserLootCollector storage ? storage : null;
        if (MiningCollection.breakBlock(world, pos, settings.drops(), settings.silk(), settings.smelt(), collector)) {
            lastMined = world.getTime();
            if (shared) limits.put(origin, world.getTime());
            LaserBeamNetwork.invalidate(world);
            Vec3d impact = ray.end();
            world.spawnParticles(ParticleTypes.FLAME, impact.x, impact.y, impact.z, 9, .16, .16, .16, .035);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, impact.x, impact.y, impact.z, 5, .12, .12, .12, .025);
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, .55F, 1.65F);
        }
        heating.remove(pos);
        targets.remove(key);
    }

    public void clear(LaserBeamSource source) {
        if (source.beamWorld() != null) heating.keySet().forEach(pos -> source.beamWorld().setBlockBreakingInfo(breakerId(source, pos), pos, -1));
        heating.clear();
    }

    private static int breakerId(LaserBeamSource source, BlockPos target) {
        return (31 * source.beamPosition().hashCode() + target.hashCode()) | Integer.MIN_VALUE;
    }
    private static final class Heating {
        final BlockState state;
        double heat;
        int stage = -1;
        long lastHeated = Long.MIN_VALUE;
        BlockPos lastSource;
        long lastSourceTick;
        Heating(BlockState state) { this.state = state; }
    }
}
