package net.askcraft.justifylasers.platform;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

public abstract class LaserBlockEntity extends BlockEntity {
    protected LaserBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract void writeLaserNbt(NbtCompound nbt, InventoryNbt inventory);
    protected abstract void readLaserNbt(NbtCompound nbt, InventoryNbt inventory);

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        writeLaserNbt(nbt, new InventoryNbt());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        readLaserNbt(nbt, new InventoryNbt());
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }
}
