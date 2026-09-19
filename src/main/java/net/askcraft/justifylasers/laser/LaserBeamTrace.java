package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public record LaserBeamTrace(
        Vec3d start,
        Vec3d end,
        Direction direction,
        @Nullable BlockPos hitBlock,
        @Nullable Direction hitSide,
        int rgb,
        double power,
        @Nullable BlockPos combinedBy,
        BeamBehavior behavior,
        double workPower
) {
    public LaserBeamTrace(Vec3d start, Vec3d end, Direction direction, BlockPos hitBlock, Direction hitSide,
                          int rgb, double power, BlockPos combinedBy, BeamBehavior behavior) {
        this(start, end, direction, hitBlock, hitSide, rgb, power, combinedBy, behavior, power);
    }
    public LaserBeamTrace(Vec3d start, Vec3d end, Direction direction, @Nullable BlockPos hitBlock,
                          @Nullable Direction hitSide, int rgb, double power, @Nullable BlockPos combinedBy) {
        this(start, end, direction, hitBlock, hitSide, rgb, power, combinedBy, BeamBehavior.NONE);
    }
    public LaserBeamTrace(Vec3d start, Vec3d end, Direction direction, @Nullable BlockPos hitBlock,
                          @Nullable Direction hitSide, int rgb, double power) {
        this(start, end, direction, hitBlock, hitSide, rgb, power, null);
    }
    public LaserBeamTrace(Vec3d start, Vec3d end, Direction direction, @Nullable BlockPos hitBlock,
                          @Nullable Direction hitSide) {
        this(start, end, direction, hitBlock, hitSide, -1, 1);
    }

    public LaserBeamTrace(Vec3d start, Vec3d end, Direction direction, @Nullable BlockPos hitBlock) {
        this(start, end, direction, hitBlock, hitBlock == null ? null : direction.getOpposite());
    }

    public LaserBeamTrace withOptics(int color, double fraction) {
        return new LaserBeamTrace(start, end, direction, hitBlock, hitSide, color & 0xFFFFFF, fraction, combinedBy, behavior);
    }

    public LaserBeamTrace combinedBy(@Nullable BlockPos combiner) {
        return new LaserBeamTrace(start, end, direction, hitBlock, hitSide, rgb, power, combiner, behavior, workPower);
    }

    public LaserBeamTrace withBehavior(BeamBehavior value) {
        return new LaserBeamTrace(start, end, direction, hitBlock, hitSide, rgb, power, combinedBy, value, workPower);
    }
    public LaserBeamTrace paid(double fraction) {
        return new LaserBeamTrace(start, end, direction, hitBlock, hitSide, rgb, power * fraction, combinedBy, behavior, workPower);
    }
    public LaserBeamTrace withWorkPower(double value) {
        return new LaserBeamTrace(start, end, direction, hitBlock, hitSide, rgb, power, combinedBy, behavior, value);
    }

    public Vec3d axis() {
        Vec3d delta = end.subtract(start);
        // Vec3d.normalize() zeroes vectors shorter than 1e-4, including the gap
        // between touching optical ports. Coalescing must keep that segment's axis.
        double squaredLength = delta.lengthSquared();
        return squaredLength > 1.0E-12D ? delta.multiply(1 / Math.sqrt(squaredLength)) : Vec3d.of(direction.getVector());
    }

    public double length() {
        return start.distanceTo(end);
    }

    public boolean hasBlockHit() {
        return hitBlock != null;
    }

    public static LaserBeamTrace trace(World world, BlockPos emitterPos, BlockState emitterState, double maxRange) {
        Direction direction = emitterState.get(LaserEmitterBlock.FACING);
        Vec3d axis = Vec3d.of(direction.getVector());
        Vec3d center = Vec3d.ofCenter(emitterPos);
        Vec3d start = center.add(axis.multiply(LaserEmitterBlock.BEAM_ORIGIN_OFFSET));
        return traceFrom(world, start, direction, maxRange);
    }

    public static LaserBeamTrace traceFrom(World world, Vec3d start, Direction direction, double maxRange) {
        return traceFrom(world, start, Vec3d.of(direction.getVector()), maxRange);
    }

    public static LaserBeamTrace traceFrom(World world, Vec3d start, Vec3d directionVector, double maxRange) {
        return traceFrom(world, start, directionVector, maxRange, null);
    }

    public static LaserBeamTrace traceFrom(World world, Vec3d start, Vec3d directionVector, double maxRange,
                                           @Nullable BlockPos transparentBlock) {
        Vec3d axis = directionVector.normalize();
        Direction direction = Direction.getFacing(axis.x, axis.y, axis.z);
        Vec3d maxEnd = start.add(axis.multiply(maxRange));

        BlockHitResult hit = BlockView.raycast(
                start,
                maxEnd,
                world,
                (view, currentPos) -> {
                    // A beam must never force distant chunks to load just to inspect them.
                    if (!view.isChunkLoaded(currentPos)) {
                        Vec3d boundary = new Box(currentPos).raycast(start, maxEnd).orElse(start);
                        return BlockHitResult.createMissed(boundary, direction, currentPos.toImmutable());
                    }
                    BlockState state = view.getBlockState(currentPos);
                    if (state.isAir() || currentPos.equals(transparentBlock)) {
                        return null;
                    }
                    if (state.getBlock() instanceof LaserOpticBlock optic && optic.kind() == LaserOpticBlock.Kind.MIRROR
                            && view.getBlockEntity(currentPos) instanceof LaserOpticBlockEntity mirror) {
                        BlockHitResult surface = mirror.mirrorHit(start, maxEnd);
                        BlockHitResult support = mirror.supportShape().raycast(start, maxEnd, currentPos);
                        return support != null && (surface == null || support.getPos().squaredDistanceTo(start) < surface.getPos().squaredDistanceTo(start))
                                ? support : surface;
                    }
                    VoxelShape shape = state.getCollisionShape(view, currentPos, ShapeContext.absent());
                    if (shape.isEmpty()) {
                        return null;
                    }
                    if (state.getBlock() instanceof net.askcraft.justifylasers.block.LaserPartBlock part && part.module() != null) {
                        // Hollow module frames still process the light travelling through their aperture.
                        BlockHitResult surface = Box.raycast(java.util.List.of(shape.getBoundingBox()), start, maxEnd, currentPos);
                        if (surface != null) return surface;
                    }
                    if (state.getBlock() instanceof LaserOpticBlock || view.getBlockEntity(currentPos) instanceof LaserLightSink) {
                        // Vanilla's inside-shape probe advances by 0.1% of the entire ray, which
                        // can skip an adjacent optical input on long beams. Use the exact face first.
                        BlockHitResult surface = Box.raycast(shape.getBoundingBoxes(), start, maxEnd, currentPos);
                        if (surface != null) return surface;
                    }
                    return view.raycastBlock(start, maxEnd, currentPos, shape, state);
                },
                ignored -> BlockHitResult.createMissed(maxEnd, direction, BlockPos.ofFloored(maxEnd))
        );

        if (hit.getType() == HitResult.Type.BLOCK) {
            return new LaserBeamTrace(start, hit.getPos(), direction, hit.getBlockPos().toImmutable(), hit.getSide());
        }
        return new LaserBeamTrace(start, hit.getPos(), direction, null);
    }
}
