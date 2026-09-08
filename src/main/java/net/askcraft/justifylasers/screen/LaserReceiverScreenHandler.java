package net.askcraft.justifylasers.screen;

import net.askcraft.justifylasers.block.entity.LaserReceiverBlockEntity;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

public class LaserReceiverScreenHandler extends ScreenHandler {
    public static final int BUTTON_ENABLED = 0;
    public static final int BUTTON_INVERTED = 1;
    public static final int BUTTON_COLOR_FILTER = 2;
    public static final int BUTTON_LIGHT_EMISSION = 3;
    public static final int BUTTON_SIGNAL_DOWN = 4;
    public static final int BUTTON_SIGNAL_UP = 5;

    private final PropertyDelegate properties;
    private final ScreenHandlerContext context;
    @Nullable
    private final LaserReceiverBlockEntity blockEntity;

    public LaserReceiverScreenHandler(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
        this(syncId, new ArrayPropertyDelegate(LaserReceiverBlockEntity.PROPERTY_COUNT), ScreenHandlerContext.EMPTY, null);
        buf.readBlockPos();
    }

    public LaserReceiverScreenHandler(int syncId, PlayerInventory inventory, LaserReceiverBlockEntity blockEntity) {
        this(syncId, blockEntity.getPropertyDelegate(),
                ScreenHandlerContext.create(blockEntity.getWorld(), blockEntity.getPos()), blockEntity);
    }

    private LaserReceiverScreenHandler(int syncId, PropertyDelegate properties, ScreenHandlerContext context,
                                       @Nullable LaserReceiverBlockEntity blockEntity) {
        super(ModScreenHandlers.LASER_RECEIVER, syncId);
        checkDataCount(properties, LaserReceiverBlockEntity.PROPERTY_COUNT);
        this.properties = properties;
        this.context = context;
        this.blockEntity = blockEntity;
        addProperties(properties);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (blockEntity == null || !canUse(player) || player.isSpectator()
                || id < BUTTON_ENABLED || id > BUTTON_SIGNAL_UP) {
            return false;
        }
        blockEntity.handleButton(id);
        sendContentUpdates();
        return true;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return canUse(context, player, ModBlocks.LASER_RECEIVER);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    public boolean isEnabled() {
        return properties.get(0) != 0;
    }

    public int getSignalStrength() {
        return MathHelper.clamp(properties.get(1), 0, 15);
    }

    public boolean isInverted() {
        return properties.get(2) != 0;
    }

    public int getColorFilter() {
        return MathHelper.clamp(properties.get(3), 0, LaserColor.values().length);
    }

    public boolean hasBeam() {
        return properties.get(4) != 0;
    }

    public LaserColor getReceivedColor() {
        return LaserColor.byIndex(properties.get(5));
    }

    public int getOutputPower() {
        return MathHelper.clamp(properties.get(6), 0, 15);
    }

    public boolean isLightEmissionEnabled() {
        return properties.get(7) != 0;
    }
}
