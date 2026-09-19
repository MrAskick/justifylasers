package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserEnergyBuffer;
import net.askcraft.justifylasers.energy.LaserEnergyHost;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserLightSink;
import net.askcraft.justifylasers.laser.LuminousFlux;
import net.askcraft.justifylasers.laser.OpticalGeometry;
import net.askcraft.justifylasers.laser.MirrorGeometry;
import net.askcraft.justifylasers.laser.OpticPortMode;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.platform.InventoryNbt;
import net.askcraft.justifylasers.platform.LaserBlockEntity;
import net.askcraft.justifylasers.platform.PlatformEnergyStorage;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class LaserOpticBlockEntity extends LaserBlockEntity implements LaserEnergyHost, LaserLightSink {
    private final LaserEnergyBuffer energy = new LaserEnergyBuffer(() -> LaserConfig.get().capacity,
            () -> Integer.MAX_VALUE, this::markDirty);
    public static final int IDLE_COLOR = 0x65717A;
    public static final double PORT_DEPTH = 0.5;
    private final OpticPortMode[] ports = new OpticPortMode[6];
    private final PlatformEnergyStorage[] energyPorts = new PlatformEnergyStorage[6];
    private double yaw;
    private double pitch;
    private BlockPos linkedMirror;
    private boolean aimNeedsSync;
    private long lastManualAim = Long.MIN_VALUE;
    private int rgb = IDLE_COLOR;
    private boolean emission;
    private long lastInputTick = Long.MIN_VALUE;
    private int lastInput;
    private long lastFlux;
    private double syncedCombinerEfficiency = .95;

    public LaserOpticBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_OPTIC, pos, state);
        Direction face = state.get(LaserOpticBlock.FACING);
        yaw = face.getAxis().isHorizontal() ? face.asRotation() : 0;
        pitch = face == Direction.DOWN ? -45 : 45;
        defaultPorts(false);
        for (Direction side : Direction.values()) energyPorts[side.ordinal()] = new PlatformEnergyStorage(this, () -> isEnergyOutput(side));
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        LaserBeamNetwork.registerOptic(world, pos);
    }

    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        if (world != null) LaserBeamNetwork.registerOptic(world, pos);
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        if (world != null) LaserBeamNetwork.removeOptic(world, pos);
    }

    private void defaultPorts(boolean legacy) {
        if (kind() == LaserOpticBlock.Kind.COMBINER) {
            Arrays.fill(ports, OpticPortMode.INPUT);
            ports[facing().ordinal()] = OpticPortMode.OUTPUT;
            return;
        }
        Arrays.fill(ports, OpticPortMode.OUTPUT);
        ports[facing().ordinal()] = OpticPortMode.INPUT;
        if (legacy && kind() == LaserOpticBlock.Kind.SPLITTER) {
            Direction.Axis unusedAxis = facing().getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
            for (Direction side : Direction.values()) if (side.getAxis() == unusedAxis) ports[side.ordinal()] = OpticPortMode.DISABLED;
        }
    }

    public OpticPortMode portMode(Direction side) { return ports[side.ordinal()]; }
    public boolean hasConfigurablePorts() { return kind() != LaserOpticBlock.Kind.MIRROR; }
    public List<Direction> outputPorts() { return Arrays.stream(Direction.values()).filter(side -> portMode(side) == OpticPortMode.OUTPUT).toList(); }
    public boolean isEnergyOutput(@Nullable Direction side) {
        return side != null && kind() == LaserOpticBlock.Kind.ENERGY_RECEIVER && portMode(side) == OpticPortMode.OUTPUT;
    }

    public boolean acceptsLaser(@Nullable Direction side, Vec3d point) {
        if (side == null || !hasConfigurablePorts() || portMode(side) != OpticPortMode.INPUT) return false;
        Vec3d relative = point.subtract(Vec3d.ofCenter(pos));
        Vec3d normal = Vec3d.of(side.getVector());
        double depth = relative.dotProduct(normal);
        return Math.abs(depth - PORT_DEPTH) < 1e-5 && relative.subtract(normal.multiply(depth)).lengthSquared() <= 0.21 * 0.21;
    }

    public void setPortMode(Direction side, OpticPortMode mode) {
        if (!hasConfigurablePorts() || portMode(side) == mode) return;
        if (kind() == LaserOpticBlock.Kind.COMBINER && mode == OpticPortMode.OUTPUT)
            for (Direction other : Direction.values()) if (ports[other.ordinal()] == OpticPortMode.OUTPUT)
                ports[other.ordinal()] = OpticPortMode.INPUT;
        ports[side.ordinal()] = mode;
        sync();
        if (world != null && !world.isClient) Platform.opticPortsChanged(this);
    }

    public void cyclePort(Direction side, PlayerEntity player) {
        setPortMode(side, portMode(side).cycle(player.isSneaking()));
        String key = portMode(side) == OpticPortMode.OUTPUT && kind() == LaserOpticBlock.Kind.ENERGY_RECEIVER
                ? "energy_output" : portMode(side).name().toLowerCase(java.util.Locale.ROOT);
        player.sendMessage(Text.translatable("message.justifylasers.port_mode",
                Text.translatable("direction.justifylasers." + side.getName()), Text.translatable("port.justifylasers." + key)), true);
    }

    public NbtCompound copySettings() {
        var data = new NbtCompound();
        data.putString("OpticKind", kind().name());
        data.putIntArray("Ports", Arrays.stream(ports).mapToInt(Enum::ordinal).toArray());
        return data;
    }

    public boolean pasteSettings(NbtCompound data) {
        if (!hasConfigurablePorts() || !kind().name().equals(data.getString("OpticKind"))) return false;
        int[] values = data.getIntArray("Ports");
        if (values.length != 6 || Arrays.stream(values).anyMatch(value -> value < 0 || value >= OpticPortMode.values().length)
                || kind() == LaserOpticBlock.Kind.COMBINER && Arrays.stream(values).filter(value -> value == OpticPortMode.OUTPUT.ordinal()).count() > 1) return false;
        for (int i = 0; i < ports.length; i++) ports[i] = OpticPortMode.values()[values[i]];
        sync();
        if (world != null && !world.isClient) Platform.opticPortsChanged(this);
        return true;
    }

    public LaserOpticBlock.Kind kind() { return ((LaserOpticBlock) getCachedState().getBlock()).kind(); }
    public Direction facing() { return getCachedState().get(LaserOpticBlock.FACING); }
    public Vec3d normal() { return OpticalGeometry.normal(yaw, pitch); }
    public double yaw() { return yaw; }
    public double pitch() { return pitch; }
    public BlockPos linkedMirror() { return linkedMirror; }
    public void linkTo(BlockPos target) {
        if (kind() != LaserOpticBlock.Kind.MIRROR || pos.equals(target)) return;
        linkedMirror = target.toImmutable();
        orient(Vec3d.ofCenter(target).subtract(Vec3d.ofCenter(pos)));
        sync();
    }
    public boolean aimManually(double yaw, double pitch) {
        if (world == null || world.isClient || world.getTime() - lastManualAim < 2 && lastManualAim != Long.MIN_VALUE
                || !Double.isFinite(yaw) || !Double.isFinite(pitch)) return false;
        if (Math.abs(MathHelper.wrapDegrees(yaw - this.yaw)) > 45 || Math.abs(pitch - this.pitch) > 45) return false;
        if (Math.abs(MathHelper.wrapDegrees(yaw - this.yaw)) < .01 && Math.abs(pitch - this.pitch) < .01) return false;
        lastManualAim = world.getTime();
        aim(OpticalGeometry.normal(yaw, MathHelper.clamp(pitch, -89.5, 89.5)));
        return true;
    }
    public int rgb() { return rgb; }
    public double combiningEfficiency() { return world != null && world.isClient ? syncedCombinerEfficiency : LaserConfig.get().beamCombinerEfficiency; }
    public boolean emitsShaderLight() { return emission && getCachedState().get(LaserOpticBlock.LIT); }
    public int lastInput() { return lastInput; }
    public long lastFlux() { return lastFlux; }
    @Override public LaserEnergyBuffer energy() { return energy; }
    public PlatformEnergyStorage energyPort(Direction side) { return side == null ? null : energyPorts[side.ordinal()]; }
    @Override public boolean acceptsEnergy() { return false; }
    @Override public boolean exportsEnergy() {
        return kind() == LaserOpticBlock.Kind.ENERGY_RECEIVER && LaserConfig.technicalMode()
                && world != null && !world.isClient && !isRemoved();
    }

    @Nullable
    public BlockHitResult mirrorHit(Vec3d start, Vec3d end) {
        if (linkedMirror != null && world != null && world.isChunkLoaded(linkedMirror)
                && world.getBlockEntity(linkedMirror) instanceof LaserOpticBlockEntity target && target.kind() == LaserOpticBlock.Kind.MIRROR) {
            Vec3d aimed = OpticalGeometry.aimMirror(Vec3d.ofCenter(pos), start, end.subtract(start), Vec3d.ofCenter(linkedMirror), MirrorGeometry.HALF * Math.sqrt(2));
            if (aimed != null && Math.abs(aimed.dotProduct(normal())) < 1 - 1e-10) {
                orient(aimed);
                aimNeedsSync = !world.isClient;
            }
        }
        Vec3d normal = normal();
        Vec3d delta = end.subtract(start);
        double denominator = delta.dotProduct(normal);
        if (Math.abs(denominator) < 1.0E-9) return null;
        Vec3d center = Vec3d.ofCenter(pos);
        double t = center.subtract(start).dotProduct(normal) / denominator;
        if (t < 0 || t > 1) return null;
        Vec3d point = start.add(delta.multiply(t));
        if (!MirrorGeometry.contains(point.subtract(center), MirrorGeometry.frame(normal, facing()))) return null;
        return new BlockHitResult(point, Direction.getFacing(normal.x, normal.y, normal.z), pos, false);
    }

    public boolean reflectingSurface(Vec3d point) {
        Vec3d relative = point.subtract(Vec3d.ofCenter(pos));
        return MirrorGeometry.contains(relative, MirrorGeometry.frame(normal(), facing())) && Math.abs(relative.dotProduct(normal())) < 1e-7;
    }

    public VoxelShape supportShape() {
        return MirrorGeometry.supportShape(normal(),facing());
    }

    public VoxelShape mirrorShape() {
        Vec3d n = normal();
        var frame=MirrorGeometry.frame(n,facing());
        Vec3d extent = new Vec3d(panelExtent(frame.right().x,frame.up().x,n.x),
                panelExtent(frame.right().y,frame.up().y,n.y),panelExtent(frame.right().z,frame.up().z,n.z));
        Vec3d center = new Vec3d(0.5, 0.5, 0.5);
        return VoxelShapes.union(supportShape(), VoxelShapes.cuboid(new Box(center.subtract(extent), center.add(extent))));
    }

    private static double panelExtent(double right,double up,double normal) {
        return .375*(Math.abs(right)+Math.abs(up))+.041*Math.abs(normal);
    }

    public void aim(Vec3d normal) {
        if (normal.lengthSquared() < 1.0E-12 || !Double.isFinite(normal.lengthSquared())) return;
        linkedMirror = null;
        orient(normal);
        sync();
    }
    private void orient(Vec3d normal) {
        Vec3d n = normal.normalize();
        yaw = Math.toDegrees(Math.atan2(-n.x, n.z));
        pitch = Math.toDegrees(Math.asin(MathHelper.clamp(n.y, -1, 1)));
    }

    public void interact(PlayerEntity player) {
        if (kind() == LaserOpticBlock.Kind.MIRROR) {
            linkedMirror = null;
            if (player.isSneaking()) pitch = pitch >= 90 ? -90 : Math.min(90, pitch + 15);
            else yaw = (yaw + 15) % 360;
            sync();
            player.sendMessage(Text.translatable("message.justifylasers.mirror_angles", Math.round(yaw), Math.round(pitch)), true);
        } else if (kind() == LaserOpticBlock.Kind.SPLITTER) {
            player.sendMessage(Text.translatable("message.justifylasers.splitter"), true);
        } else if (kind() == LaserOpticBlock.Kind.COMBINER) {
            player.sendMessage(Text.translatable("message.justifylasers.combiner",
                    Math.round((1 - LaserConfig.get().beamCombinerEfficiency) * 100)), true);
        } else {
            player.sendMessage(Text.translatable("message.justifylasers.energy_receiver", energy.stored(), energy.capacity(), lastInput,
                    LuminousFlux.format(lastFlux)), true);
        }
    }

    @Override public void receiveLight(long lumens, int color) {
        if (!exportsEnergy() || lumens <= 0) return;
        if (lastInputTick != world.getTime()) { lastFlux = 0; lastInput = 0; }
        lastFlux = LuminousFlux.clamp(lastFlux + LuminousFlux.clamp(lumens));
        int amount = LuminousFlux.toEnergyRate(lumens, LaserConfig.get().lumensPerEnergyUnit, LaserConfig.get().energyTransmissionEfficiency);
        receiveBeam(amount, color);
        lastInputTick = world.getTime();
    }

    public void receiveBeam(int amount, int color) {
        if (world == null || !exportsEnergy() || amount <= 0) return;
        int accepted = energy.receive(amount, false);
        if (lastInputTick != world.getTime()) lastInput = 0;
        lastInputTick = world.getTime();
        lastInput = (int) Math.min(Integer.MAX_VALUE, (long) lastInput + accepted);
    }

    public void setBeamInput(@Nullable LaserBeamNetwork.OpticInput input) {
        if (world == null || world.isClient) return;
        int nextColor = input == null ? IDLE_COLOR : input.rgb();
        boolean lit = input != null;
        boolean nextEmission = lit && input.emission();
        if (rgb == nextColor && emission == nextEmission && getCachedState().get(LaserOpticBlock.LIT) == lit) return;
        rgb = nextColor;
        emission = nextEmission;
        world.setBlockState(pos, getCachedState().with(LaserOpticBlock.LIT, lit), Block.NOTIFY_LISTENERS);
        sync();
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, LaserOpticBlockEntity optic) {
        if (optic.aimNeedsSync) { optic.aimNeedsSync = false; optic.sync(); }
        if (optic.kind() != LaserOpticBlock.Kind.ENERGY_RECEIVER) return;
        net.askcraft.justifylasers.platform.Platform.exportEnergy(optic);
        if (optic.lastInputTick < world.getTime() - 1) {
            optic.lastInput = 0;
            optic.lastFlux = 0;
        }
    }

    private void sync() {
        markDirty();
        LaserBeamNetwork.invalidate(world);
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }

    @Override
    protected void writeLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        nbt.putDouble("Yaw", yaw);
        nbt.putDouble("Pitch", pitch);
        if (linkedMirror != null) nbt.putLong("LinkedMirror", linkedMirror.asLong());
        nbt.putInt("Rgb", rgb);
        nbt.putInt("Energy", energy.stored());
        nbt.putIntArray("Ports", Arrays.stream(ports).mapToInt(Enum::ordinal).toArray());
        nbt.putBoolean("Emission", emission);
        if (kind() == LaserOpticBlock.Kind.COMBINER) nbt.putDouble("CombinerEfficiency", combiningEfficiency());
    }

    @Override
    protected void readLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        linkedMirror = nbt.contains("LinkedMirror") ? BlockPos.fromLong(nbt.getLong("LinkedMirror")) : null;
        if (linkedMirror != null && (linkedMirror.equals(pos) || linkedMirror.getSquaredDistance(pos) > 64 * 64)) linkedMirror = null;
        if (nbt.contains("Yaw") && Double.isFinite(nbt.getDouble("Yaw"))) yaw = nbt.getDouble("Yaw") % 360;
        if (nbt.contains("Pitch") && Double.isFinite(nbt.getDouble("Pitch"))) pitch = MathHelper.clamp(nbt.getDouble("Pitch"), -90, 90);
        if (nbt.contains("Rgb")) rgb = nbt.getInt("Rgb") & 0xFFFFFF;
        energy.restore(nbt.getLong("Energy"));
        defaultPorts(!nbt.contains("Ports"));
        if (nbt.contains("Ports")) {
            int[] saved = nbt.getIntArray("Ports");
            for (int i = 0; i < ports.length; i++) ports[i] = i < saved.length ? OpticPortMode.byId(saved[i]) : OpticPortMode.DISABLED;
        }
        if (kind() == LaserOpticBlock.Kind.COMBINER) {
            boolean output = false;
            for (int i = 0; i < ports.length; i++) if (ports[i] == OpticPortMode.OUTPUT) {
                if (output) ports[i] = OpticPortMode.INPUT;
                output = true;
            }
        }
        emission = nbt.getBoolean("Emission");
        double efficiency = nbt.getDouble("CombinerEfficiency");
        syncedCombinerEfficiency = Double.isFinite(efficiency) && efficiency > 0 && efficiency <= 1 ? efficiency : .95;
        LaserBeamNetwork.invalidate(world);
    }

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
}
