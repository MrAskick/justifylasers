package net.askcraft.justifylasers.block;

import net.askcraft.justifylasers.block.entity.LaserReceiverBlockEntity;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class LaserReceiverBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.FACING;
    public static final IntProperty POWER = Properties.POWER;
    public static final BooleanProperty LIT = Properties.LIT;
    public static final EnumProperty<LaserColor> COLOR = EnumProperty.of("color", LaserColor.class);

    private static final Map<Direction, VoxelShape> SHAPES = createShapes();

    public LaserReceiverBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH)
                .with(POWER, 0).with(LIT, false).with(COLOR, LaserColor.RED));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWER, LIT, COLOR);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getPlayerLookDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        // Minecraft passes the direction from the querying neighbor toward this block.
        return direction == state.get(FACING).getOpposite() ? 0 : state.get(POWER);
    }

    @Override
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return getWeakRedstonePower(state, world, pos, direction);
    }

    public void updateOutputNeighbors(World world, BlockPos pos) {
        world.updateNeighborsAlways(pos, this);
        for (Direction direction : Direction.values()) {
            world.updateNeighborsAlways(pos.offset(direction), this);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (!world.isClient && state.get(POWER) > 0
                && (!state.isOf(newState.getBlock()) || state.get(FACING) != newState.get(FACING))) {
            updateOutputNeighbors(world, pos);
        }
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LaserReceiverBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, ModBlockEntities.LASER_RECEIVER, LaserReceiverBlockEntity::serverTick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);
            if (factory != null) {
                player.openHandledScreen(factory);
            }
        }
        return ActionResult.SUCCESS;
    }

    private static Map<Direction, VoxelShape> createShapes() {
        VoxelShape north = VoxelShapes.union(
                createCuboidShape(1, 1, 12.3, 15, 15, 16),
                createCuboidShape(1, 1, 10.8, 4, 4, 13),
                createCuboidShape(12, 1, 10.8, 15, 4, 13),
                createCuboidShape(1, 12, 10.8, 4, 15, 13),
                createCuboidShape(12, 12, 10.8, 15, 15, 13),
                createCuboidShape(1.5, 4, 11.4, 3.2, 12, 13),
                createCuboidShape(12.8, 4, 11.4, 14.5, 12, 13),
                createCuboidShape(4, 1.5, 11.4, 12, 3.2, 13),
                createCuboidShape(4, 12.8, 11.4, 12, 14.5, 13),
                createCuboidShape(3, 3, 11.8, 13, 13, 12.3),
                createCuboidShape(4, 4, 11, 12, 12, 11.8),
                createCuboidShape(5.5, 5.5, 9.5, 6.6, 10.5, 11),
                createCuboidShape(9.4, 5.5, 9.5, 10.5, 10.5, 11),
                createCuboidShape(6.6, 5.5, 9.5, 9.4, 6.6, 11),
                createCuboidShape(6.6, 9.4, 9.5, 9.4, 10.5, 11),
                createCuboidShape(6.6, 6.6, 10.2, 9.4, 9.4, 11),
                createCuboidShape(7.15, 7.15, 10.15, 8.85, 8.85, 10.2)
        );
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            VoxelShape[] result = {VoxelShapes.empty()};
            north.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
                Box box = new Box(rotatePoint(minX, minY, minZ, direction), rotatePoint(maxX, maxY, maxZ, direction));
                result[0] = VoxelShapes.union(result[0], VoxelShapes.cuboid(box));
            });
            shapes.put(direction, result[0]);
        }
        return shapes;
    }

    private static Vec3d rotatePoint(double x, double y, double z, Direction direction) {
        return switch (direction) {
            case NORTH -> new Vec3d(x, y, z);
            case SOUTH -> new Vec3d(1.0D - x, y, 1.0D - z);
            case EAST -> new Vec3d(1.0D - z, y, x);
            case WEST -> new Vec3d(z, y, 1.0D - x);
            case UP -> new Vec3d(x, 1.0D - z, y);
            case DOWN -> new Vec3d(x, z, 1.0D - y);
        };
    }
}
