package net.askcraft.justifylasers.block;

import net.askcraft.justifylasers.block.entity.LaserComponentBlockEntity;
import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.askcraft.justifylasers.industry.SolarStructure;
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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class LaserComponentBlock extends LaserBlock {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty FORMED = BooleanProperty.of("formed");
    private final String component;

    public LaserComponentBlock(Settings settings, String component) {
        super(settings, copy -> new LaserComponentBlock(copy, component));
        this.component = component;
        setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH).with(FORMED, false));
    }

    public String component() { return component; }
    public boolean solarMaterial() { return component.equals("solar_absorber") || component.equals("laser_absorbing_glass"); }
    @Override public String getTranslationKey() { return (solarMaterial() || component.equals("small_solar_concentrator") ? "block." : "item.") + "justifylasers." + component; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, FORMED); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, BlockRotation rotation) { return state.with(FACING, rotation.rotate(state.get(FACING))); }
    @Override public BlockState mirror(BlockState state, BlockMirror mirror) { return rotate(state, mirror.getRotation(state.get(FACING))); }
    @Override public BlockRenderType getRenderType(BlockState state) {
        return solarMaterial() && !state.get(FORMED) ? BlockRenderType.MODEL : BlockRenderType.ENTITYBLOCK_ANIMATED;
    }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (solarMaterial() || component.equals("small_solar_concentrator") || state.get(FORMED)) return VoxelShapes.fullCube();
        double depth = component.equals("energy_core") || component.equals("beam_controller") ? 5.5 : 3;
        return state.get(FACING).getAxis() == Direction.Axis.Z ? createCuboidShape(1.4, 1, 8-depth, 14.6, 15, 8+depth)
                : createCuboidShape(8-depth, 1, 1.4, 8+depth, 15, 14.6);
    }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return component.equals("optical_resonator") || component.equals("small_solar_concentrator") ? new SolarConcentratorBlockEntity(pos, state) : new LaserComponentBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : laserTicker(type, ModBlockEntities.LASER_COMPONENT, LaserComponentBlockEntity::tick);
    }
    @Override public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient) {
            if (placer instanceof PlayerEntity player && world.getBlockEntity(pos) instanceof SolarConcentratorBlockEntity source)
                source.initializeOwner(player);
            SolarStructure.formNearby(world, pos);
        }
    }
    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!world.isClient && (!state.isOf(next.getBlock()) || state.get(FACING) != next.get(FACING))
                && world.getBlockEntity(pos) instanceof LaserComponentBlockEntity part) SolarStructure.dismantle(part);
        super.onStateReplaced(state, world, pos, next, moved);
    }
    @Override protected ActionResult useLaser(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player.isSneaking() || player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem
                || player.getOffHandStack().getItem() instanceof net.minecraft.item.BlockItem) return ActionResult.PASS;
        if (!world.isClient && player.canModifyBlocks() && world.canPlayerModifyAt(player, pos)) {
            if (world.getBlockEntity(pos) instanceof LaserComponentBlockEntity part && !part.canAccess(player)) return ActionResult.FAIL;
            SolarStructure.formNearby(world, pos);
            var part = world.getBlockEntity(pos) instanceof LaserComponentBlockEntity entity ? entity : null;
            var source = part instanceof SolarConcentratorBlockEntity small && small.small() && !small.formed() ? small : part == null ? null : part.controller();
            if (source == null) return ActionResult.PASS;
            if (!source.canPlayerUse(player)) return ActionResult.FAIL;
            source.initializeOwner(player);
            Platform.openScreen(player, source);
        }
        return state.get(FORMED) || component.equals("small_solar_concentrator") ? ActionResult.SUCCESS : ActionResult.PASS;
    }
}
