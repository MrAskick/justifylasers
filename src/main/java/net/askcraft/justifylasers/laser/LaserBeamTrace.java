package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
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
        @Nullable Direction hitSide
) {
    public LaserBeamTrace(Vec3d start, Vec3d end, Direction direction, @Nullable BlockPos hitBlock) {
        this(start, end, direction, hitBlock, hitBlock == null ? null : direction.getOpposite());
    }

    public Vec3d axis() {
        Vec3d delta = end.subtract(start);
        return delta.lengthSquared() > 1.0E-12D ? delta.normalize() : Vec3d.of(direction.getVector());
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
                    if (state.isAir()) {
                        return null;
                    }
                    VoxelShape shape = state.getCollisionShape(view, currentPos, ShapeContext.absent());
                    if (shape.isEmpty()) {
                        return null;
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
