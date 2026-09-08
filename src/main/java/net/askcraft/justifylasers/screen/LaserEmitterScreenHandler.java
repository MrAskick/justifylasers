package net.askcraft.justifylasers.screen;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserDamage;
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
    public static final int BUTTON_IGNITE_ENTITIES = 7;
    public static final int BUTTON_RESET_DAMAGE_SETTINGS = 8;
    public static final int BEAM_WIDTH_BUTTON_BASE = 1_000;
    public static final int BEAM_WIDTH_BUTTON_MAX = BEAM_WIDTH_BUTTON_BASE
            + LaserEmitterBlockEntity.BEAM_WIDTH_STEPS;
    public static final int DAMAGE_BUTTON_BASE = 2_000;
    public static final int DAMAGE_BUTTON_MIN = DAMAGE_BUTTON_BASE + LaserDamage.MIN_DAMAGE_STEP;
    public static final int DAMAGE_BUTTON_MAX = DAMAGE_BUTTON_BASE + LaserDamage.MAX_DAMAGE_STEP;
    public static final int KNOCKBACK_BUTTON_BASE = 3_000;
    public static final int KNOCKBACK_BUTTON_MAX = KNOCKBACK_BUTTON_BASE + LaserDamage.MAX_KNOCKBACK_STEP;
    public static final int HIT_RATE_BUTTON_BASE = 4_000;
    public static final int HIT_RATE_BUTTON_MIN = HIT_RATE_BUTTON_BASE + 1;
    public static final int HIT_RATE_BUTTON_MAX = HIT_RATE_BUTTON_BASE + LaserDamage.MAX_HITS_PER_SECOND;
    public static final int RANGE_BUTTON_BASE = 5_000;
    public static final int RANGE_BUTTON_MIN = RANGE_BUTTON_BASE + LaserEmitterBlockEntity.MIN_RANGE;
    public static final int RANGE_BUTTON_MAX = RANGE_BUTTON_BASE + LaserEmitterBlockEntity.MAX_RANGE;

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
        boolean regularButton = id >= BUTTON_ENABLED && id <= BUTTON_RESET_DAMAGE_SETTINGS;
        boolean beamWidthButton = id >= BEAM_WIDTH_BUTTON_BASE && id <= BEAM_WIDTH_BUTTON_MAX;
        boolean damageButton = id >= DAMAGE_BUTTON_MIN && id <= DAMAGE_BUTTON_MAX;
        boolean knockbackButton = id >= KNOCKBACK_BUTTON_BASE && id <= KNOCKBACK_BUTTON_MAX;
        boolean hitRateButton = id >= HIT_RATE_BUTTON_MIN && id <= HIT_RATE_BUTTON_MAX;
        boolean rangeButton = id >= RANGE_BUTTON_MIN && id <= RANGE_BUTTON_MAX;
        if (!regularButton && !beamWidthButton && !damageButton && !knockbackButton && !hitRateButton && !rangeButton) {
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

    public int getDamageStep() {
        return LaserDamage.clampDamageStep(properties.get(9));
    }

    public int getKnockbackStep() {
        return LaserDamage.clampKnockbackStep(properties.get(10));
    }

    public int getHitsPerSecond() {
        return LaserDamage.clampHitsPerSecond(properties.get(11));
    }

    public boolean ignitesEntities() {
        return properties.get(12) != 0;
    }

    public int getBeamRange() {
        return LaserEmitterBlockEntity.clampBeamRange(properties.get(13));
    }
}
