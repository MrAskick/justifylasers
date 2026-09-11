package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.screen.LaserReceiverScreenHandler;
import net.askcraft.justifylasers.platform.LaserScreenFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.askcraft.justifylasers.platform.LaserBlockEntity;
import net.askcraft.justifylasers.platform.InventoryNbt;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class LaserReceiverBlockEntity extends LaserBlockEntity implements LaserScreenFactory {
    public static final int PROPERTY_COUNT = 8;

    private boolean enabled = true;
    private int signalStrength = 15;
    private boolean inverted;
    private int colorFilter;
    private boolean beamPresent;
    private LaserColor receivedColor = LaserColor.RED;
    private boolean lightEmission = true;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> enabled ? 1 : 0;
                case 1 -> signalStrength;
                case 2 -> inverted ? 1 : 0;
                case 3 -> colorFilter;
                case 4 -> beamPresent ? 1 : 0;
                case 5 -> receivedColor.ordinal();
                case 6 -> getCachedState().get(LaserReceiverBlock.POWER);
                case 7 -> lightEmission ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> enabled = value != 0;
                case 1 -> signalStrength = MathHelper.clamp(value, 0, 15);
                case 2 -> inverted = value != 0;
                case 3 -> colorFilter = MathHelper.clamp(value, 0, LaserColor.values().length);
                case 7 -> lightEmission = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    };

    public LaserReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_RECEIVER, pos, state);
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, LaserReceiverBlockEntity receiver) {
        receiver.refreshInput();
    }

    private void refreshInput() {
        if (world == null || world.isClient) {
            return;
        }
        LaserColor incoming = enabled ? findIncomingBeam() : null;
        beamPresent = incoming != null;
        if (incoming != null) {
            receivedColor = incoming;
        }
        boolean accepted = beamPresent && (colorFilter == 0 || colorFilter == receivedColor.ordinal() + 1);
        int power = enabled && (accepted != inverted) ? signalStrength : 0;
        BlockState state = getCachedState();
        BlockState updated = state.with(LaserReceiverBlock.POWER, power)
                .with(LaserReceiverBlock.COLOR, receivedColor)
                .with(LaserReceiverBlock.LIT, beamPresent && lightEmission);
        if (updated != state) {
            world.setBlockState(pos, updated, Block.NOTIFY_LISTENERS);
            if (power != state.get(LaserReceiverBlock.POWER)) {
                ((LaserReceiverBlock) state.getBlock()).updateOutputNeighbors(world, pos);
            }
            markDirty();
        }
    }

    @Nullable
    private LaserColor findIncomingBeam() {
        Direction front = getCachedState().get(LaserReceiverBlock.FACING);
        return LaserBeamNetwork.receivedColor(world, pos, front);
    }

    public void handleButton(int id) {
        switch (id) {
            case LaserReceiverScreenHandler.BUTTON_ENABLED -> enabled = !enabled;
            case LaserReceiverScreenHandler.BUTTON_INVERTED -> inverted = !inverted;
            case LaserReceiverScreenHandler.BUTTON_COLOR_FILTER -> colorFilter = (colorFilter + 1) % (LaserColor.values().length + 1);
            case LaserReceiverScreenHandler.BUTTON_LIGHT_EMISSION -> lightEmission = !lightEmission;
            case LaserReceiverScreenHandler.BUTTON_SIGNAL_DOWN -> signalStrength = Math.max(0, signalStrength - 1);
            case LaserReceiverScreenHandler.BUTTON_SIGNAL_UP -> signalStrength = Math.min(15, signalStrength + 1);
            default -> {
                return;
            }
        }
        refreshInput();
        markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    public PropertyDelegate getPropertyDelegate() {
        return properties;
    }

    @Override
    protected void writeLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        nbt.putBoolean("Enabled", enabled);
        nbt.putInt("SignalStrength", signalStrength);
        nbt.putBoolean("Inverted", inverted);
        nbt.putInt("ColorFilter", colorFilter);
        nbt.putInt("ReceivedColor", receivedColor.ordinal());
        nbt.putBoolean("LightEmission", lightEmission);
    }

    @Override
    protected void readLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        enabled = !nbt.contains("Enabled") || nbt.getBoolean("Enabled");
        signalStrength = nbt.contains("SignalStrength") ? MathHelper.clamp(nbt.getInt("SignalStrength"), 0, 15) : 15;
        inverted = nbt.getBoolean("Inverted");
        colorFilter = MathHelper.clamp(nbt.getInt("ColorFilter"), 0, LaserColor.values().length);
        receivedColor = LaserColor.byIndex(nbt.getInt("ReceivedColor"));
        lightEmission = !nbt.contains("LightEmission") || nbt.getBoolean("LightEmission");
        beamPresent = false;
    }

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.justifylasers.laser_receiver");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
        return new LaserReceiverScreenHandler(syncId, inventory, this);
    }
}
