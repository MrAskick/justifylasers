package net.askcraft.justifylasers.block;

import net.askcraft.justifylasers.block.entity.LightBridgeBlockEntity;
import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.askcraft.justifylasers.bridge.BridgeGeometry;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.platform.LaserBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.BlockView;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LightBridgeBlock extends LaserBlock {
    public static final DirectionProperty FACING = Properties.FACING;
    public static final DirectionProperty MOUNT = DirectionProperty.of("mount");
    public static final net.minecraft.state.property.BooleanProperty ROLLED = net.minecraft.state.property.BooleanProperty.of("rolled");
    public static final net.minecraft.state.property.IntProperty ROTATION = net.minecraft.state.property.IntProperty.of("rotation", 0, 7);
    private final boolean corner;
    private final Map<BlockState, VoxelShape> shapes = new ConcurrentHashMap<>();
    private final Map<List<Box>, VoxelShape> geometries = new ConcurrentHashMap<>();
    private static final Comparator<Box> BOX_ORDER = Comparator.comparingDouble((Box box) -> box.minX)
            .thenComparingDouble(box -> box.minY).thenComparingDouble(box -> box.minZ)
            .thenComparingDouble(box -> box.maxX).thenComparingDouble(box -> box.maxY).thenComparingDouble(box -> box.maxZ);

    public LightBridgeBlock(Settings settings) {
        this(settings, false);
    }

    public LightBridgeBlock(Settings settings, boolean corner) {
        super(settings, value -> new LightBridgeBlock(value, corner));
        this.corner = corner;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(MOUNT, Direction.UP).with(ROLLED, false).with(ROTATION, 0));
    }

    public boolean corner() { return corner; }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, MOUNT, ROLLED, ROTATION); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        var world = context.getWorld();
        var pos = context.getBlockPos();
        boolean independent = context.getPlayer() != null && context.getPlayer().isSneaking();
        Direction mount = context.getSide();
        // Floors/ceilings project along the player's horizontal heading; walls project out of the wall.
        // Looking down to place a floor fixture must never turn its beam upwards.
        Direction facing = placementFacing(mount, context.getHorizontalPlayerFacing(), independent);
        var state = getDefaultState().with(FACING, facing).with(MOUNT, mount);
        if (corner) {
            var hit = context.getHitPos().subtract(net.minecraft.util.math.Vec3d.ofCenter(pos));
            var frame = orientation(state);
            var preferredNormal = net.minecraft.util.math.Vec3d.of((mount.getAxis() == facing.getAxis() ? Direction.UP : mount).getVector());
            var preferredAcross = frame.acrossVector().multiply(hit.dotProduct(frame.acrossVector()) > 0 ? -1 : 1);
            double best = -Double.MAX_VALUE;
            for (int rotation = 0; rotation < 8; rotation += 2) {
                var candidate = getDefaultState().with(FACING, facing).with(MOUNT, mount).with(ROTATION, rotation);
                var basis = orientation(candidate);
                double score = Math.max(basis.normalVector().dotProduct(preferredNormal) + basis.acrossVector().dotProduct(preferredAcross),
                        basis.acrossVector().dotProduct(preferredNormal) + basis.normalVector().dotProduct(preferredAcross));
                if (score > best) { best = score; state = candidate; }
            }
        }
        if (!independent) {
            var support = world.getBlockState(pos.offset(context.getSide().getOpposite()));
            if (support.getBlock() instanceof LightBridgeBlock)
                state = state.with(FACING, support.get(FACING)).with(MOUNT, support.get(MOUNT))
                        .with(ROLLED, support.get(ROLLED)).with(ROTATION, support.get(ROTATION));
            return net.askcraft.justifylasers.bridge.BridgePlacement.align(state, world, pos);
        }
        return state;
    }
    public static Direction placementFacing(Direction mount, Direction horizontalLook, boolean perpendicular) {
        return mount.getAxis().isHorizontal() || perpendicular ? mount : horizontalLook;
    }
    @Override public BlockState rotate(BlockState state, BlockRotation rotation) {
        return transform(state, rotation::rotate);
    }
    @Override public BlockState mirror(BlockState state, BlockMirror mirror) {
        return transform(state, mirror::apply);
    }

    private BlockState transform(BlockState state, java.util.function.UnaryOperator<Direction> turn) {
        var frame = orientation(state);
        var across = transform(frame.acrossVector(), turn);
        var normal = transform(frame.normalVector(), turn);
        var rotated = state.with(FACING, turn.apply(state.get(FACING))).with(MOUNT, turn.apply(state.get(MOUNT)));
        for (boolean rolled : new boolean[]{state.get(ROLLED), !state.get(ROLLED)}) for (int rotation = 0; rotation < 8; rotation++) {
            var next = rotated.with(ROLLED, rolled).with(ROTATION, rotation);
            var basis = orientation(next);
            boolean direct = basis.acrossVector().squaredDistanceTo(across) < 1e-8 && basis.normalVector().squaredDistanceTo(normal) < 1e-8;
            boolean equivalent = corner ? basis.acrossVector().squaredDistanceTo(normal) < 1e-8 && basis.normalVector().squaredDistanceTo(across) < 1e-8
                    : Math.abs(basis.acrossVector().dotProduct(across)) > .999999 && basis.normalVector().squaredDistanceTo(normal) < 1e-8;
            if (direct || equivalent) return next;
        }
        throw new IllegalStateException("Unable to transform bridge basis");
    }

    private static net.minecraft.util.math.Vec3d transform(net.minecraft.util.math.Vec3d vector, java.util.function.UnaryOperator<Direction> turn) {
        return net.minecraft.util.math.Vec3d.of(turn.apply(Direction.EAST).getVector()).multiply(vector.x)
                .add(net.minecraft.util.math.Vec3d.of(turn.apply(Direction.UP).getVector()).multiply(vector.y))
                .add(net.minecraft.util.math.Vec3d.of(turn.apply(Direction.SOUTH).getVector()).multiply(vector.z));
    }
    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapes.computeIfAbsent(state, this::shape);
    }

    private VoxelShape shape(BlockState state) {
        var frame = orientation(state);
        var boxes = new ArrayList<Box>();
        var forward = net.minecraft.util.math.Vec3d.of(frame.forward().getVector()).multiply(2 * net.askcraft.justifylasers.bridge.BridgeOrientation.HALF_DEPTH);
        var across = frame.acrossVector();
        var normal = frame.normalVector();
        double height = 2 * net.askcraft.justifylasers.bridge.BridgeOrientation.HALF_HEIGHT;
        if (corner) {
            var start = frame.cornerPoint(BlockPos.ORIGIN, -.5, -net.askcraft.justifylasers.bridge.BridgeOrientation.HALF_DEPTH, -.5);
            boxes.addAll(BridgeGeometry.boxes(start, across, forward, normal.multiply(height)));
            boxes.addAll(BridgeGeometry.boxes(start.add(normal.multiply(height)), normal.multiply(1 - height), forward, across.multiply(height)));
            var intake = net.askcraft.justifylasers.bridge.BridgeGeometry.bounds(
                    frame.cornerPoint(BlockPos.ORIGIN, -.12, -net.askcraft.justifylasers.bridge.BridgeOrientation.HALF_DEPTH, -.12),
                    across.multiply(.24), net.minecraft.util.math.Vec3d.of(frame.forward().getVector()).multiply(.13), normal.multiply(.24));
            boxes.add(intake);
        } else {
            boxes.addAll(BridgeGeometry.boxes(frame.point(BlockPos.ORIGIN, -.5,
                    -net.askcraft.justifylasers.bridge.BridgeOrientation.HALF_DEPTH, -net.askcraft.justifylasers.bridge.BridgeOrientation.HALF_HEIGHT), across, forward, normal.multiply(height)));
            if (frame.hasFeed()) boxes.add(frame.feed(BlockPos.ORIGIN));
        }
        // Several facing/mount/rolled combinations describe identical local geometry.
        boxes.sort(BOX_ORDER);
        return geometries.computeIfAbsent(List.copyOf(boxes), BridgeGeometry::union);
    }
    public static net.askcraft.justifylasers.bridge.BridgeOrientation orientation(BlockState state) {
        return new net.askcraft.justifylasers.bridge.BridgeOrientation(state.get(FACING), state.get(MOUNT), state.get(ROLLED), state.get(ROTATION));
    }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new LightBridgeBlockEntity(pos, state); }
    @Override protected ActionResult useLaser(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return ActionResult.PASS;
    }

    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!state.equals(next)) LightBridgeNetwork.removeFieldsAt(world, pos);
        super.onStateReplaced(state, world, pos, next, moved);
        LaserBeamNetwork.invalidate(world);
    }
}
