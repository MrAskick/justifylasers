package net.askcraft.justifylasers.screen;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.item.LaserModuleItem;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserDamage;
import net.askcraft.justifylasers.laser.LaserMining;
import net.askcraft.justifylasers.laser.LaserRedstoneMode;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
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
    public static final int BUTTON_SILK_TOUCH = 9;
    public static final int BUTTON_DROP_BLOCKS = 10;
    public static final int BUTTON_SCORCH_MARKS = 11;
    public static final int BUTTON_RESET_MINING_SETTINGS = 12;
    public static final int BUTTON_SECURITY = 13;
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
    public static final int MINING_SPEED_BUTTON_BASE = 6_000;
    public static final int MINING_SPEED_BUTTON_MAX = MINING_SPEED_BUTTON_BASE + LaserMining.MAX_SPEED_STEP;

    private final PropertyDelegate properties;
    private final ScreenHandlerContext context;
    private final BlockPos blockPos;
    @Nullable
    private final LaserEmitterBlockEntity blockEntity;
    private boolean inventoryVisible;
    private boolean securityEditable;

    private static final int[] MODULE_X = {67, 109, 151, 193, 109, 157, 211, 235, 67};
    private static final int[] MODULE_Y = {53, 53, 53, 53, 95, 95, 95, 53, 95};

    public LaserEmitterScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, pos, false);
    }

    public LaserEmitterScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos, boolean powered) {
        this(
                syncId,
                clientProperties(powered),
                ScreenHandlerContext.EMPTY,
                pos,
                null,
                playerInventory,
                powered
        );
    }

    private static PropertyDelegate clientProperties(boolean powered) {
        PropertyDelegate properties = new ArrayPropertyDelegate(LaserEmitterBlockEntity.PROPERTY_COUNT);
        properties.set(18, powered ? 1 : 0);
        return properties;
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
                blockEntity,
                playerInventory,
                blockEntity.isPoweredEmitter()
        );
    }

    private LaserEmitterScreenHandler(
            int syncId,
            PropertyDelegate properties,
            ScreenHandlerContext context,
            BlockPos blockPos,
            @Nullable LaserEmitterBlockEntity blockEntity,
            PlayerInventory playerInventory,
            boolean powered
    ) {
        super(powered ? ModScreenHandlers.POWERED_LASER_EMITTER : ModScreenHandlers.LASER_EMITTER, syncId);
        checkDataCount(properties, LaserEmitterBlockEntity.PROPERTY_COUNT);
        this.properties = properties;
        this.context = context;
        this.blockPos = blockPos;
        this.blockEntity = blockEntity;
        addProperties(properties);
        addProperty(new Property() {
            @Override
            public int get() {
                return (blockEntity == null ? securityEditable : blockEntity.canManageSecurity(playerInventory.player)) ? 1 : 0;
            }

            @Override
            public void set(int value) {
                securityEditable = value != 0;
            }
        });
        Inventory inventory = blockEntity != null && blockEntity.isPoweredEmitter()
                ? blockEntity : new SimpleInventory(LaserEmitterBlockEntity.MODULE_SLOT_COUNT);
        for (int slot = 0; slot < LaserEmitterBlockEntity.MODULE_SLOT_COUNT; slot++) {
            final int index = slot;
            addSlot(new Slot(inventory, slot, MODULE_X[slot], MODULE_Y[slot]) {
                @Override
                public boolean canInsert(ItemStack stack) {
                    return isPoweredEmitter() && (index == 0 ? stack.getItem() instanceof LaserCrystalItem
                            : stack.getItem() instanceof LaserModuleItem item && item.module().slot() == index);
                }

                @Override
                public int getMaxItemCount() {
                    return index == LaserModule.RANGE.slot() || index == LaserModule.THICKNESS.slot() ? 64 : 1;
                }

                @Override
                public void markDirty() {
                    super.markDirty();
                    if (blockEntity != null) blockEntity.inventoryChanged();
                }

                @Override
                public boolean isEnabled() {
                    return (blockEntity != null || inventoryVisible) && isPoweredEmitter();
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) addPlayerSlot(playerInventory, column + row * 9 + 9, 44 + column * 27, 136 + row * 20);
        }
        for (int column = 0; column < 9; column++) addPlayerSlot(playerInventory, column, 44 + column * 27, 198);
    }

    private void addPlayerSlot(PlayerInventory inventory, int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override
            public boolean isEnabled() {
                return isPoweredEmitter();
            }
        });
    }

    public void setInventoryVisible(boolean visible) {
        inventoryVisible = visible;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (blockEntity == null || !player.isAlive() || player.isSpectator() || !canUse(player) || !blockEntity.allowsSetting(id)) {
            return false;
        }
        if (id == BUTTON_SECURITY) {
            boolean changed = blockEntity.togglePrivacy(player);
            if (changed) sendContentUpdates();
            return changed;
        }
        boolean regularButton = id >= BUTTON_ENABLED && id <= BUTTON_RESET_MINING_SETTINGS;
        boolean beamWidthButton = id >= BEAM_WIDTH_BUTTON_BASE && id <= BEAM_WIDTH_BUTTON_MAX;
        boolean damageButton = id >= DAMAGE_BUTTON_MIN && id <= DAMAGE_BUTTON_MAX;
        boolean knockbackButton = id >= KNOCKBACK_BUTTON_BASE && id <= KNOCKBACK_BUTTON_MAX;
        boolean hitRateButton = id >= HIT_RATE_BUTTON_MIN && id <= HIT_RATE_BUTTON_MAX;
        boolean rangeButton = id >= RANGE_BUTTON_MIN && id <= RANGE_BUTTON_MAX;
        boolean miningSpeedButton = id >= MINING_SPEED_BUTTON_BASE && id <= MINING_SPEED_BUTTON_MAX;
        if (!regularButton && !beamWidthButton && !damageButton && !knockbackButton && !hitRateButton && !rangeButton && !miningSpeedButton) {
            return false;
        }
        blockEntity.handleButton(id);
        sendContentUpdates();
        return true;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return blockEntity != null ? blockEntity.canPlayerUse(player)
                : canUse(context, player, ModBlocks.LASER_EMITTER) || canUse(context, player, ModBlocks.POWERED_LASER_EMITTER);
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
        if (canUse(player) && !player.isSpectator()) super.onSlotClick(slot, button, action, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        if (!isPoweredEmitter() || !canUse(player) || player.isSpectator() || slot < 0 || slot >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(slot);
        if (!source.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = source.getStack();
        ItemStack original = stack.copy();
        if (slot < LaserEmitterBlockEntity.MODULE_SLOT_COUNT) {
            if (!insertItem(stack, LaserEmitterBlockEntity.MODULE_SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int target = stack.getItem() instanceof LaserCrystalItem ? 0
                    : stack.getItem() instanceof LaserModuleItem item ? item.module().slot() : -1;
            if (target < 0 || !insertItem(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) source.setStack(ItemStack.EMPTY);
        else source.markDirty();
        source.onTakeItem(player, stack);
        return original;
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

    public int getMiningSpeedStep() {
        return LaserMining.clampSpeedStep(properties.get(14));
    }

    public boolean hasSilkTouch() {
        return properties.get(15) != 0;
    }

    public boolean dropsBlocks() {
        return properties.get(16) != 0;
    }

    public boolean showsScorchMarks() {
        return properties.get(17) != 0;
    }

    public boolean isPoweredEmitter() {
        return properties.get(18) != 0;
    }

    public boolean isTechnicalMode() {
        return properties.get(19) != 0;
    }

    public int getEnergy() {
        return (properties.get(20) & 0xFFFF) | (properties.get(21) & 0x7FFF) << 16;
    }

    public int getEnergyCapacity() {
        return (properties.get(22) & 0xFFFF) | (properties.get(23) & 0x7FFF) << 16;
    }

    public int getEnergyCost() {
        return (properties.get(24) & 0xFFFF) | (properties.get(25) & 0x7FFF) << 16;
    }

    public boolean hasModule(LaserModule module) {
        return !isPoweredEmitter() || (properties.get(26) & 1 << module.ordinal()) != 0;
    }

    public boolean hasCrystal() {
        return properties.get(27) != 0;
    }

    public boolean isPrivate() {
        return properties.get(28) != 0;
    }

    public boolean hasRedstoneSignal() {
        return properties.get(29) != 0;
    }

    public boolean canManageSecurity() {
        return securityEditable;
    }

    public String getOwnerName() {
        StringBuilder name = new StringBuilder(16);
        for (int index = 30; index < 46 && properties.get(index) != 0; index++) name.append((char) properties.get(index));
        return name.toString();
    }
}
