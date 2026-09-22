package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.block.LightBridgeBlock;
import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.askcraft.justifylasers.laser.LaserLightSink;
import net.askcraft.justifylasers.laser.LuminousFlux;
import net.askcraft.justifylasers.platform.InventoryNbt;
import net.askcraft.justifylasers.platform.LaserBlockEntity;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class LightBridgeBlockEntity extends LaserBlockEntity implements LaserLightSink {
    private long inputTick = Long.MIN_VALUE;
    private long lumens;
    private int rgb;

    public LightBridgeBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.LIGHT_BRIDGE, pos, state); }
    public Direction facing() { return getCachedState().get(LightBridgeBlock.FACING); }
    public Direction mount() { return getCachedState().get(LightBridgeBlock.MOUNT); }
    public boolean rolled() { return getCachedState().get(LightBridgeBlock.ROLLED); }
    public int rotation() { return getCachedState().get(LightBridgeBlock.ROTATION); }
    public boolean corner() { return ((LightBridgeBlock) getCachedState().getBlock()).corner(); }
    public net.askcraft.justifylasers.bridge.BridgeOrientation orientation() { return LightBridgeBlock.orientation(getCachedState()); }
    public long input(long tick) { return inputTick == tick ? lumens : 0; }
    public int rgb() { return rgb; }

    public net.minecraft.util.math.Box getRenderBoundingBox() {
        var span = world == null ? null : LightBridgeNetwork.at(world, pos);
        if (span != null) return span.hardwareBounds().expand(.08);
        var frame = orientation();
        return (corner() ? net.askcraft.justifylasers.bridge.BridgeGeometry.bounds(
                frame.cornerPoint(pos, -.5, -net.askcraft.justifylasers.bridge.BridgeOrientation.HALF_DEPTH, -.5),
                frame.acrossVector(), Vec3d.of(facing().getVector()).multiply(2 * net.askcraft.justifylasers.bridge.BridgeOrientation.HALF_DEPTH),
                frame.normalVector()) : frame.casing(pos, 1)).expand(.08);
    }

    @Override public boolean acceptsLaser(Direction side, Vec3d point) {
        return side != null && side != facing();
    }

    @Override public void receiveLight(long lumens, int rgb) {
        if (world == null || world.isClient || isRemoved()) return;
        this.lumens = LuminousFlux.clamp(lumens);
        this.rgb = rgb & 0xFFFFFF;
        inputTick = world.getTime();
    }

    @Override public void setWorld(World world) {
        super.setWorld(world);
        LightBridgeNetwork.register(world, pos);
    }
    @Override public void cancelRemoval() {
        super.cancelRemoval();
        if (world != null) LightBridgeNetwork.register(world, pos);
    }
    @Override public void markRemoved() {
        super.markRemoved();
        if (world != null) LightBridgeNetwork.unregister(world, pos);
    }

    // Optical power is live, not a saved battery: loading a chunk must never restore a ghost floor.
    @Override protected void writeLaserNbt(NbtCompound nbt, InventoryNbt inventory) { }
    @Override protected void readLaserNbt(NbtCompound nbt, InventoryNbt inventory) { }
}
