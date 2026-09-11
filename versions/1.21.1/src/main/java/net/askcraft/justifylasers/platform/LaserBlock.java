package net.askcraft.justifylasers.platform;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Function;

public abstract class LaserBlock extends BlockWithEntity {
    private final MapCodec<? extends BlockWithEntity> codec;
    protected LaserBlock(Settings settings, Function<Settings, ? extends BlockWithEntity> factory) {
        super(settings);
        codec = createCodec(factory);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return codec;
    }

    protected abstract ActionResult useLaser(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit);

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return useLaser(state, world, pos, player, hit);
    }

    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> laserTicker(
            BlockEntityType<A> given, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return validateTicker(given, expected, ticker);
    }
}
