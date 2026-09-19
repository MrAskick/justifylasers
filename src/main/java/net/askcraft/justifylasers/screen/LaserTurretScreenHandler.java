package net.askcraft.justifylasers.screen;

import net.askcraft.justifylasers.block.entity.LaserTurretBlockEntity;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.item.LaserGunItem;
import net.askcraft.justifylasers.registry.ModLaserParts;
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
import net.minecraft.util.math.BlockPos;

public final class LaserTurretScreenHandler extends ScreenHandler {
    private final LaserTurretBlockEntity turret;
    private final Inventory inventory;
    private final PropertyDelegate properties;
    private final BlockPos pos;
    private boolean controlsVisible = true;

    public LaserTurretScreenHandler(int syncId, PlayerInventory player, BlockPos pos) {
        this(syncId, player, pos, null, new SimpleInventory(2), new ArrayPropertyDelegate(20));
    }

    public LaserTurretScreenHandler(int syncId, PlayerInventory player, LaserTurretBlockEntity turret) {
        this(syncId, player, turret.getPos(), turret, turret, new PropertyDelegate() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> turret.enabled() ? 1 : 0;
                    case 1 -> turret.filter().flags();
                    case 2 -> turret.firing() ? 1 : 0;
                    case 3 -> turret.filter().exclusions().size();
                    default -> index - 4 < turret.ownerName().length() ? turret.ownerName().charAt(index - 4) : 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int size() { return 20; }
        });
    }

    private LaserTurretScreenHandler(int syncId, PlayerInventory player, BlockPos pos, LaserTurretBlockEntity turret, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.LASER_TURRET, syncId);
        this.turret = turret; this.inventory = inventory; this.properties = properties; this.pos = pos;
        for (int index = 0; index < 2; index++) {
            int slot = index;
            addSlot(new Slot(inventory, slot, 104 + slot * 96, 70) {
                @Override public boolean canInsert(ItemStack stack) {
                    return slot == 0 ? stack.getItem() instanceof LaserGunItem : stack.isOf(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER));
                }
                @Override public int getMaxItemCount() { return 1; }
                @Override public boolean isEnabled() { return turret != null || controlsVisible; }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(player, column + row * 9 + 9, 44 + column * 27, 136 + row * 20));
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 44 + column * 27, 198));
        addProperties(properties);
    }

    public boolean enabled() { return properties.get(0) != 0; }
    public BlockPos pos() { return pos; }
    public void showControls(boolean value) { controlsVisible = value; }
    public boolean editEntityFilter(PlayerEntity player, String id, boolean mode) {
        if (turret == null || !turret.editEntityFilter(player, id, mode)) return false;
        sendContentUpdates(); return true;
    }
    public boolean firing() { return properties.get(2) != 0; }
    public int flags() { return properties.get(1); }
    public boolean hasFilter() { return !inventory.getStack(1).isEmpty(); }
    public int exclusionCount() { return properties.get(3); }
    public String ownerName() {
        StringBuilder name = new StringBuilder();
        for (int index = 4; index < 20 && properties.get(index) != 0; index++) name.append((char) properties.get(index));
        return name.toString();
    }
    public boolean toggleExcludedPlayer(PlayerEntity player, String name) {
        if (turret == null || !canUse(player)) return false;
        boolean changed = turret.toggleExcludedPlayer(player, name);
        if (changed) sendContentUpdates();
        return changed;
    }

    @Override public boolean onButtonClick(PlayerEntity player, int id) {
        if (turret == null || !canUse(player) || id < 0 || id > 5 || id >= 1 && id <= 4 && !turret.hasFilter()) return false;
        turret.toggle(id); sendContentUpdates(); return true;
    }

    @Override public boolean canUse(PlayerEntity player) { return turret == null || turret.canPlayerUse(player); }

    @Override public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType action, PlayerEntity player) {
        if (canUse(player) && !player.isSpectator()) super.onSlotClick(slot, button, action, player);
    }

    @Override public ItemStack quickMove(PlayerEntity player, int index) {
        if (!canUse(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack(), original = stack.copy();
        if (index < 2) {
            if (!insertItem(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof LaserGunItem) {
            if (!insertItem(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (stack.isOf(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER))) {
            if (!insertItem(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        slot.onTakeItem(player, stack);
        return original;
    }
}
