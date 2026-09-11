package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public final class LaserPartBlockEntity extends BlockEntity {
    public LaserPartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_PART, pos, state);
    }
}
