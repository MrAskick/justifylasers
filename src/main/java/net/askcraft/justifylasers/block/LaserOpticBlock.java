package net.askcraft.justifylasers.block;

import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.platform.LaserBlock;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class LaserOpticBlock extends LaserBlock {
    public enum Kind { MIRROR, SPLITTER, ENERGY_RECEIVER, COMBINER }
    public static final DirectionProperty FACING = Properties.FACING;
    public static final BooleanProperty LIT = Properties.LIT;
    private static final VoxelShape SPLITTER_SHAPE = net.minecraft.util.shape.VoxelShapes.union(
            createCuboidShape(0, 5, 5, 16, 11, 11), createCuboidShape(5, 0, 5, 11, 16, 11), createCuboidShape(5, 5, 0, 11, 11, 16));
    private final Kind kind;

    public LaserOpticBlock(Settings settings, Kind kind) {
        super(settings, value -> new LaserOpticBlock(value, kind));
        this.kind = kind;
        setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH).with(LIT, false));
    }

    public Kind kind() { return kind; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, kind == Kind.MIRROR ? context.getSide() : context.getPlayerLookDirection().getOpposite());
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
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (kind == Kind.MIRROR && world.getBlockEntity(pos) instanceof LaserOpticBlockEntity mirror) return mirror.mirrorShape();
        return kind == Kind.SPLITTER || kind == Kind.COMBINER ? SPLITTER_SHAPE : net.minecraft.util.shape.VoxelShapes.fullCube();
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LaserOpticBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : laserTicker(type, ModBlockEntities.LASER_OPTIC, LaserOpticBlockEntity::serverTick);
    }

    @Override
    protected ActionResult useLaser(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && player.canModifyBlocks() && world.getBlockEntity(pos) instanceof LaserOpticBlockEntity optic) {
            optic.interact(player);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        super.onStateReplaced(state, world, pos, next, moved);
        LaserBeamNetwork.invalidate(world);
    }
}
