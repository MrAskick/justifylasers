package net.askcraft.justifylasers.block;

import net.askcraft.justifylasers.block.entity.LaserTurretBlockEntity;
import net.askcraft.justifylasers.item.LaserGunItem;
import net.askcraft.justifylasers.platform.LaserBlock;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class LaserTurretBlock extends LaserBlock {
    private static final VoxelShape SHAPE = VoxelShapes.union(createCuboidShape(1, 0, 1, 15, 4, 15),
            createCuboidShape(5, 4, 5, 11, 16, 11));

    public LaserTurretBlock(Settings settings) { super(settings, LaserTurretBlock::new); }
    @Override public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new LaserTurretBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return laserTicker(type, ModBlockEntities.LASER_TURRET, LaserTurretBlockEntity::tick);
    }

    @Override public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && placer instanceof PlayerEntity player && world.getBlockEntity(pos) instanceof LaserTurretBlockEntity turret)
            turret.setOwner(player);
    }

    @Override protected ActionResult useLaser(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof LaserTurretBlockEntity turret && turret.canPlayerUse(player)) {
            ItemStack held = player.getMainHandStack();
            if (held.getItem() instanceof LaserGunItem && turret.getStack(0).isEmpty()) {
                turret.setStack(0, held.copyWithCount(1));
                if (!player.isCreative()) held.decrement(1);
            } else Platform.openScreen(player, turret);
        }
        return ActionResult.SUCCESS;
    }

    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState next, boolean moved) {
        if (!state.isOf(next.getBlock()) && world.getBlockEntity(pos) instanceof LaserTurretBlockEntity turret)
            ItemScatterer.spawn(world, pos, turret);
        super.onStateReplaced(state, world, pos, next, moved);
    }
}
