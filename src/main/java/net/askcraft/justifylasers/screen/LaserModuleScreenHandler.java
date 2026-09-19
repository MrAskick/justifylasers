package net.askcraft.justifylasers.screen;

import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

public final class LaserModuleScreenHandler extends ScreenHandler {
    private final LaserPartBlockEntity block;
    private final BlockPos pos;
    private final PropertyDelegate properties;
    private boolean storageVisible;

    public LaserModuleScreenHandler(int syncId, PlayerInventory player, BlockPos pos) {
        this(syncId, player, pos, null, new SimpleInventory(LaserPartBlockEntity.INVENTORY_SIZE), new ArrayPropertyDelegate(LaserPartBlockEntity.PROPERTY_COUNT));
    }
    public LaserModuleScreenHandler(int syncId, PlayerInventory player, LaserPartBlockEntity block) {
        this(syncId, player, block.getPos(), block, block.size() > 0 ? block : new SimpleInventory(LaserPartBlockEntity.INVENTORY_SIZE), new PropertyDelegate() {
            @Override public int get(int index) { return block.property(index); }
            @Override public void set(int index, int value) { }
            @Override public int size() { return LaserPartBlockEntity.PROPERTY_COUNT; }
        });
    }
    private LaserModuleScreenHandler(int syncId, PlayerInventory player, BlockPos pos, LaserPartBlockEntity block, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.LASER_MODULE, syncId);
        this.pos = pos; this.block = block; this.properties = properties;
        addProperties(properties);
        for (int i = 0; i < 9; i++) addSlot(new Slot(inventory, i, 130 + i % 3 * 22, 49 + i / 3 * 22) {
            @Override public boolean isEnabled() { return hasStorage() && (block != null || storageVisible); }
            @Override public boolean canInsert(ItemStack stack) { return hasStorage(); }
        });
        int[] upgradeX = {158, 112, 204, 66};
        for (int i = 0; i < 4; i++) {
            int index = LaserPartBlockEntity.STORAGE_SIZE + i;
            addSlot(new Slot(inventory, index, upgradeX[i], 83) {
                @Override public boolean isEnabled() {
                    return (block != null || !storageVisible) && (module() == LaserModule.BLOCK_DESTRUCTION
                            || module() == LaserModule.ENTITY_DAMAGE && index == LaserPartBlockEntity.IGNITION_SLOT);
                }
                @Override public boolean canInsert(ItemStack stack) { return LaserPartBlockEntity.acceptsUpgrade(module(), index, stack); }
                @Override public int getMaxItemCount() { return 1; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(player, 9 + row * 9 + col, 44 + col * 27, 136 + row * 20));
        for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 44 + col * 27, 198));
    }
    public BlockPos pos() { return pos; }
    public LaserModule module() { int index = properties.get(0); return index >= 0 && index < LaserModule.values().length ? LaserModule.values()[index] : null; }
    public int value(int index) { return properties.get(index); }
    public long cost() { return (properties.get(9) & 0xFFFFL) | (properties.get(10) & 0x7FFFL) << 16; }
    public void showStorage(boolean value) { storageVisible = value; }
    public boolean hasStorage() { return module() == LaserModule.BLOCK_DESTRUCTION || module() == LaserModule.BLOCK_COLLECTION; }
    @Override public boolean canUse(PlayerEntity player) { return block == null || block.canPlayerUse(player); }
    public boolean setting(PlayerEntity player, int id, String argument) {
        if (block == null || !block.setting(player, id, argument)) return false;
        sendContentUpdates(); return true;
    }
    @Override public boolean onButtonClick(PlayerEntity player, int id) { return setting(player, id, ""); }
    @Override public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
        if (canUse(player) && !player.isSpectator()) super.onSlotClick(slot, button, action, player);
    }
    @Override public ItemStack quickMove(PlayerEntity player, int index) {
        if (!canUse(player) || player.isSpectator() || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = source.getStack(), original = stack.copy();
        int boundary = LaserPartBlockEntity.INVENTORY_SIZE;
        if (index < boundary) {
            if (!insertItem(stack, boundary, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!insertItem(stack, LaserPartBlockEntity.STORAGE_SIZE, boundary, false)
                && !(hasStorage() && insertItem(stack, 0, LaserPartBlockEntity.STORAGE_SIZE, false))) return ItemStack.EMPTY;
        if (stack.isEmpty()) source.setStack(ItemStack.EMPTY); else source.markDirty();
        source.onTakeItem(player, stack); return original;
    }
}
