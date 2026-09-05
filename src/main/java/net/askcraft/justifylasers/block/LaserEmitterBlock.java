package net.askcraft.justifylasers.block;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
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
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class LaserEmitterBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.FACING;
    public static final BooleanProperty LIT = Properties.LIT;
    public static final BooleanProperty EMITTING_LIGHT = BooleanProperty.of("emitting_light");
    public static final BooleanProperty MINECRAFT_LIGHTING = BooleanProperty.of("minecraft_lighting");
    public static final EnumProperty<LaserColor> COLOR = EnumProperty.of("color", LaserColor.class);
    public static final double BEAM_ORIGIN_OFFSET = 0.147D;

    private static final double EDGE_MIN = 1.0D / 16.0D;
    private static final double EDGE_MAX = 15.0D / 16.0D;
    private static final double BODY_DEPTH = 9.0D / 16.0D;
    private static final double NOZZLE_MIN = 5.0D / 16.0D;
    private static final double NOZZLE_MAX = 11.0D / 16.0D;
    private static final double NOZZLE_FRONT = 5.7D / 16.0D;
    private static final double OPPOSITE_NOZZLE_FRONT = 1.0D - NOZZLE_FRONT;

    private static final VoxelShape NORTH_SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(EDGE_MIN, EDGE_MIN, BODY_DEPTH, EDGE_MAX, EDGE_MAX, 1.0D),
            VoxelShapes.cuboid(NOZZLE_MIN, NOZZLE_MIN, NOZZLE_FRONT, NOZZLE_MAX, NOZZLE_MAX, BODY_DEPTH)
    );
    private static final VoxelShape SOUTH_SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(EDGE_MIN, EDGE_MIN, 0.0D, EDGE_MAX, EDGE_MAX, 1.0D - BODY_DEPTH),
            VoxelShapes.cuboid(NOZZLE_MIN, NOZZLE_MIN, 1.0D - BODY_DEPTH, NOZZLE_MAX, NOZZLE_MAX, OPPOSITE_NOZZLE_FRONT)
    );
    private static final VoxelShape WEST_SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(BODY_DEPTH, EDGE_MIN, EDGE_MIN, 1.0D, EDGE_MAX, EDGE_MAX),
            VoxelShapes.cuboid(NOZZLE_FRONT, NOZZLE_MIN, NOZZLE_MIN, BODY_DEPTH, NOZZLE_MAX, NOZZLE_MAX)
    );
    private static final VoxelShape EAST_SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(0.0D, EDGE_MIN, EDGE_MIN, 1.0D - BODY_DEPTH, EDGE_MAX, EDGE_MAX),
            VoxelShapes.cuboid(1.0D - BODY_DEPTH, NOZZLE_MIN, NOZZLE_MIN, OPPOSITE_NOZZLE_FRONT, NOZZLE_MAX, NOZZLE_MAX)
    );
    private static final VoxelShape UP_SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(EDGE_MIN, 0.0D, EDGE_MIN, EDGE_MAX, 1.0D - BODY_DEPTH, EDGE_MAX),
            VoxelShapes.cuboid(NOZZLE_MIN, 1.0D - BODY_DEPTH, NOZZLE_MIN, NOZZLE_MAX, OPPOSITE_NOZZLE_FRONT, NOZZLE_MAX)
    );
    private static final VoxelShape DOWN_SHAPE = VoxelShapes.union(
            VoxelShapes.cuboid(EDGE_MIN, BODY_DEPTH, EDGE_MIN, EDGE_MAX, 1.0D, EDGE_MAX),
            VoxelShapes.cuboid(NOZZLE_MIN, NOZZLE_FRONT, NOZZLE_MIN, NOZZLE_MAX, BODY_DEPTH, NOZZLE_MAX)
    );

    public LaserEmitterBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(LIT, false)
                .with(COLOR, LaserColor.RED)
                .with(MINECRAFT_LIGHTING, false)
                .with(EMITTING_LIGHT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, EMITTING_LIGHT, MINECRAFT_LIGHTING, COLOR);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState()
                .with(FACING, context.getPlayerLookDirection().getOpposite())
                .with(LIT, false)
                .with(MINECRAFT_LIGHTING, false)
                .with(EMITTING_LIGHT, false);
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
        return getShape(state.get(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getShape(state.get(FACING));
    }

    private static VoxelShape getShape(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            case UP -> UP_SHAPE;
            case DOWN -> DOWN_SHAPE;
        };
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LaserEmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return world.isClient
                ? checkType(type, ModBlockEntities.LASER_EMITTER, LaserEmitterBlockEntity::clientTick)
                : checkType(type, ModBlockEntities.LASER_EMITTER, LaserEmitterBlockEntity::serverTick);
    }

    @Override
    public ActionResult onUse(
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            Hand hand,
            BlockHitResult hit
    ) {
        if (!world.isClient) {
            NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);
            if (factory != null) {
                player.openHandledScreen(factory);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return state.get(LIT) ? 15 : 0;
    }
}
