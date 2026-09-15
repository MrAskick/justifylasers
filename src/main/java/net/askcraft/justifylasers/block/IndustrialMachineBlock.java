package net.askcraft.justifylasers.block;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.platform.LaserBlock;
import net.askcraft.justifylasers.platform.Platform;
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
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class IndustrialMachineBlock extends LaserBlock {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = createCuboidShape(0, 0, 0, 16, 16, 16);
    private final MachineKind kind;

    public IndustrialMachineBlock(Settings settings, MachineKind kind) {
        super(settings, copy -> new IndustrialMachineBlock(copy, kind));
        this.kind = kind;
        setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH));
    }
    public MachineKind kind() { return kind; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) { return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite()); }
    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new IndustrialMachineBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return laserTicker(type, ModBlockEntities.INDUSTRIAL_MACHINE, IndustrialMachineBlockEntity::tick);
    }
    @Override protected ActionResult useLaser(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof IndustrialMachineBlockEntity machine && machine.canPlayerUse(player)) {
            net.askcraft.justifylasers.industry.ChamberStructure.form(machine);
            var controller = machine.controller();
            if (controller == null) controller = machine;
            if (!controller.canAccess(player)) return ActionResult.FAIL;
            controller.initializeOwner(player);
            var held = player.getMainHandStack();
            if (held.isOf(net.minecraft.item.Items.WATER_BUCKET) && controller.fillWater(1_000, true) == 1_000) {
                controller.fillWater(1_000, false);
                if (!player.getAbilities().creativeMode) player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.BUCKET));
            }
            Platform.openScreen(player, controller);
        }
        return ActionResult.SUCCESS;
    }
    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!state.isOf(next.getBlock()) && world.getBlockEntity(pos) instanceof IndustrialMachineBlockEntity machine) {
            if (!world.isClient) ItemScatterer.spawn(world, pos, machine.localInventory());
            net.askcraft.justifylasers.industry.ChamberStructure.dismantle(machine);
        }
        super.onStateReplaced(state, world, pos, next, moved);
    }

    @Override public void onPlaced(World world, BlockPos pos, BlockState state, net.minecraft.entity.LivingEntity placer, net.minecraft.item.ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && world.getBlockEntity(pos) instanceof IndustrialMachineBlockEntity machine) {
            if (placer instanceof PlayerEntity player) machine.initializeOwner(player);
            net.askcraft.justifylasers.industry.ChamberStructure.form(machine);
        }
    }

}
