package net.askcraft.justifylasers.screen;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserRedstoneMode;
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
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class LaserEmitterScreenHandler extends ScreenHandler {
    public static final int BUTTON_ENABLED = 0;
    public static final int BUTTON_REDSTONE = 1;
    public static final int BUTTON_COLOR = 2;
    public static final int BUTTON_BREAK_BLOCKS = 3;
    public static final int BUTTON_DAMAGE_ENTITIES = 4;
    public static final int BUTTON_LIGHT_EMISSION = 5;
    public static final int BUTTON_MINECRAFT_LIGHTING = 6;
    public static final int BEAM_WIDTH_BUTTON_BASE = 1_000;
    public static final int BEAM_WIDTH_BUTTON_MAX = BEAM_WIDTH_BUTTON_BASE
            + LaserEmitterBlockEntity.BEAM_WIDTH_STEPS;

    private final PropertyDelegate properties;
    private final ScreenHandlerContext context;
    private final BlockPos blockPos;
    @Nullable
    private final LaserEmitterBlockEntity blockEntity;

    public LaserEmitterScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(
                syncId,
                new ArrayPropertyDelegate(LaserEmitterBlockEntity.PROPERTY_COUNT),
                ScreenHandlerContext.EMPTY,
                buf.readBlockPos(),
                null
        );
    }

    public LaserEmitterScreenHandler(
            int syncId,
            PlayerInventory playerInventory,
            LaserEmitterBlockEntity blockEntity
    ) {
        this(
                syncId,
                blockEntity.getPropertyDelegate(),
                ScreenHandlerContext.create(blockEntity.getWorld(), blockEntity.getPos()),
                blockEntity.getPos(),
                blockEntity
        );
    }

    private LaserEmitterScreenHandler(
            int syncId,
            PropertyDelegate properties,
            ScreenHandlerContext context,
            BlockPos blockPos,
            @Nullable LaserEmitterBlockEntity blockEntity
    ) {
        super(ModScreenHandlers.LASER_EMITTER, syncId);
        checkDataCount(properties, LaserEmitterBlockEntity.PROPERTY_COUNT);
        this.properties = properties;
        this.context = context;
        this.blockPos = blockPos;
        this.blockEntity = blockEntity;
        addProperties(properties);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (blockEntity == null || !canUse(player)) {
            return false;
        }
        boolean regularButton = id >= BUTTON_ENABLED && id <= BUTTON_MINECRAFT_LIGHTING;
        boolean beamWidthButton = id >= BEAM_WIDTH_BUTTON_BASE && id <= BEAM_WIDTH_BUTTON_MAX;
        if (!regularButton && !beamWidthButton) {
            return false;
        }
        blockEntity.handleButton(id);
        sendContentUpdates();
        return true;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return canUse(context, player, ModBlocks.LASER_EMITTER);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public boolean isEnabled() {
        return properties.get(0) != 0;
    }

    public LaserRedstoneMode getRedstoneMode() {
        return LaserRedstoneMode.byIndex(properties.get(1));
    }

    public LaserColor getColor() {
        return LaserColor.byIndex(properties.get(2));
    }

    public boolean breaksBlocks() {
        return properties.get(3) != 0;
    }

    public boolean damagesEntities() {
        return properties.get(4) != 0;
    }

    public boolean isActive() {
        return properties.get(5) != 0;
    }

    public int getBeamWidthStep() {
        return LaserEmitterBlockEntity.clampBeamWidthStep(properties.get(6));
    }

    public float getBeamWidthScale() {
        return LaserEmitterBlockEntity.beamWidthScale(getBeamWidthStep());
    }

    public boolean isLightEmissionEnabled() {
        return properties.get(7) != 0;
    }

    public boolean isMinecraftLightingEnabled() {
        return properties.get(8) != 0;
    }
}
