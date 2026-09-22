package net.askcraft.justifylasers.block;

import net.askcraft.justifylasers.block.entity.PrintedModelBlockEntity;
import net.askcraft.justifylasers.platform.LaserBlock;
import net.askcraft.justifylasers.printing.PrintData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class PrintedModelBlock extends LaserBlock {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public PrintedModelBlock(Settings settings) {
        super(settings, PrintedModelBlock::new);
        setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) { return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite()); }
    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new PrintedModelBlockEntity(pos, state); }
    @Override protected ActionResult useLaser(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) { return ActionResult.PASS; }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return world.getBlockEntity(pos) instanceof PrintedModelBlockEntity print ? print.shape(state.get(FACING)) : VoxelShapes.fullCube();
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return getOutlineShape(state, world, pos, context); }
    @Override public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world.getBlockEntity(pos) instanceof PrintedModelBlockEntity print) print.setDesign(PrintData.read(stack));
    }
    // The view type differs between the supported Minecraft versions.
    public ItemStack getPickStack(net.minecraft.world.WorldView world, BlockPos pos, BlockState state) { return getPickStack((BlockView)world,pos,state); }
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        return world.getBlockEntity(pos) instanceof PrintedModelBlockEntity print && print.design() != null
                ? PrintData.printed(print.design()) : ItemStack.EMPTY;
    }
}
