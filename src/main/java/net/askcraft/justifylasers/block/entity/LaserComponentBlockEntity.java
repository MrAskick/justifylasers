package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.block.LaserComponentBlock;
import net.askcraft.justifylasers.industry.SolarStructure;
import net.askcraft.justifylasers.platform.InventoryNbt;
import net.askcraft.justifylasers.platform.LaserBlockEntity;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class LaserComponentBlockEntity extends LaserBlockEntity {
    private BlockPos controllerPos;

    public LaserComponentBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.LASER_COMPONENT, pos, state); }
    @Nullable public BlockPos controllerPos() { return controllerPos; }
    public boolean formed() { return controllerPos != null && getCachedState().get(LaserComponentBlock.FORMED); }
    @Nullable public SolarConcentratorBlockEntity controller() {
        return controllerPos != null && world != null && world.isChunkLoaded(controllerPos)
                && world.getBlockEntity(controllerPos) instanceof SolarConcentratorBlockEntity source ? source : null;
    }
    public boolean canAccess(PlayerEntity player) {
        if (controllerPos == null) return true;
        SolarConcentratorBlockEntity source = controller();
        return source != null && source.canAccess(player);
    }
    public void assignController(@Nullable BlockPos position) {
        controllerPos = position == null ? null : position.toImmutable();
        if (world != null && !world.isClient) {
            var state = world.getBlockState(pos);
            // onStateReplaced runs after the new state is installed. Never resurrect a removed member.
            if (state.isOf(getCachedState().getBlock()) && state.get(LaserComponentBlock.FORMED) != (position != null))
                world.setBlockState(pos, state.with(LaserComponentBlock.FORMED, position != null), Block.NOTIFY_LISTENERS);
            sync();
        }
    }
    public static void tick(World world, BlockPos pos, BlockState state, LaserComponentBlockEntity part) {
        if (part instanceof SolarConcentratorBlockEntity source) source.solarTick();
        else if (part.controllerPos != null && Math.floorMod(world.getTime() + pos.asLong(), 20) == 0
                && world.isChunkLoaded(part.controllerPos) && part.controller() == null) part.assignController(null);
    }
    protected void sync() {
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override protected void writeLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        if (controllerPos != null) nbt.putLong("SolarController", controllerPos.asLong());
    }
    @Override protected void readLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        controllerPos = nbt.contains("SolarController") ? BlockPos.fromLong(nbt.getLong("SolarController")) : null;
    }
    public Box getRenderBoundingBox() {
        if (this instanceof SolarConcentratorBlockEntity source && !source.small() && formed())
            return new Box(SolarStructure.origin(source).add(-1, 0, -1)).union(new Box(SolarStructure.origin(source).add(3, 3, 3)));
        return new Box(pos);
    }
}
