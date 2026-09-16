package net.askcraft.justifylasers.screen;

import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.askcraft.justifylasers.laser.LaserRedstoneMode;
import net.askcraft.justifylasers.laser.LuminousFlux;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public final class SolarConcentratorScreenHandler extends ScreenHandler {
    public static final int PROPERTY_COUNT = 35;
    private final SolarConcentratorBlockEntity source;
    private final PropertyDelegate properties;

    public SolarConcentratorScreenHandler(int id, PlayerInventory inventory, BlockPos pos) {
        this(id, inventory, null, new ArrayPropertyDelegate(PROPERTY_COUNT));
    }

    public SolarConcentratorScreenHandler(int id, PlayerInventory inventory, SolarConcentratorBlockEntity source) {
        this(id, inventory, source, new PropertyDelegate() {
            @Override public int get(int index) {
                if (index >= 6 && index < 14) {
                    long value = index < 10 ? source.luminousFlux() : source.peakFlux();
                    return (int) (value >>> ((index < 10 ? index - 6 : index - 10) * 16)) & 0xFFFF;
                }
                return switch (index) {
                    case 0 -> source.enabled() ? 1 : 0;
                    case 1 -> source.redstoneMode().ordinal();
                    case 2 -> source.hasRedstoneSignal() ? 1 : 0;
                    case 3 -> source.isPrivate() ? 1 : 0;
                    case 4 -> source.canManageSecurity(inventory.player) ? 1 : 0;
                    case 5 -> source.status().ordinal();
                    case 14 -> source.convertedEnergyRate() & 0xFFFF;
                    case 15 -> source.convertedEnergyRate() >>> 16;
                    case 16 -> source.getBeamRange();
                    case 17 -> (int) (1000.0 * source.luminousFlux() / source.peakFlux());
                    case 34 -> source.small() ? 1 : 0;
                    default -> index >= 18 && index < 34 && index - 18 < source.ownerName().length()
                            ? source.ownerName().charAt(index - 18) : 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int size() { return PROPERTY_COUNT; }
        });
    }

    private SolarConcentratorScreenHandler(int id, PlayerInventory inventory, SolarConcentratorBlockEntity source, PropertyDelegate properties) {
        super(ModScreenHandlers.SOLAR_CONCENTRATOR, id);
        this.source = source;
        this.properties = properties;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, 9 + row * 9 + column, 44 + column * 27, 136 + row * 20));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 44 + column * 27, 198));
        addProperties(properties);
    }

    private long flux(int low) {
        long value = 0;
        for (int part = 0; part < 4; part++) value |= (long) (properties.get(low + part) & 0xFFFF) << (part * 16);
        return LuminousFlux.clamp(value);
    }
    public long luminousFlux() { return flux(6); }
    public long peakFlux() { return flux(10); }
    public int convertedEnergyRate() { return (properties.get(14) & 0xFFFF) | (properties.get(15) & 0x7FFF) << 16; }
    public int beamRange() { return properties.get(16); }
    public boolean small() { return properties.get(34) != 0; }
    public int utilization() { return MathHelper.clamp(properties.get(17), 0, 1000); }
    public boolean enabled() { return properties.get(0) != 0; }
    public LaserRedstoneMode redstoneMode() { return LaserRedstoneMode.byIndex(properties.get(1)); }
    public boolean hasRedstoneSignal() { return properties.get(2) != 0; }
    public boolean isPrivate() { return properties.get(3) != 0; }
    public boolean canManageSecurity() { return properties.get(4) != 0; }
    public SolarConcentratorBlockEntity.Status status() {
        var values = SolarConcentratorBlockEntity.Status.values();
        return values[MathHelper.clamp(properties.get(5), 0, values.length - 1)];
    }
    public String ownerName() {
        var name = new StringBuilder();
        for (int index = 18; index < 34 && properties.get(index) != 0; index++) name.append((char) properties.get(index));
        return name.toString();
    }
    @Override public boolean canUse(PlayerEntity player) { return source == null || source.canPlayerUse(player); }
    @Override public boolean onButtonClick(PlayerEntity player, int id) {
        if (source == null || !canUse(player) || !player.canModifyBlocks() || !player.getWorld().canPlayerModifyAt(player, source.getPos())) return false;
        switch (id) {
            case 0 -> source.toggle();
            case 1 -> source.cycleRedstone();
            case 2 -> { if (!source.togglePrivacy(player)) return false; }
            default -> { return false; }
        }
        sendContentUpdates(); return true;
    }
    @Override public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
        if (canUse(player) && !player.isSpectator()) super.onSlotClick(slot, button, action, player);
    }
    @Override public ItemStack quickMove(PlayerEntity player, int index) {
        if (!canUse(player) || player.isSpectator() || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack(), original = stack.copy();
        if (index < 27 ? !insertItem(stack, 27, 36, false) : !insertItem(stack, 0, 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        slot.onTakeItem(player, stack);
        return original;
    }
}
