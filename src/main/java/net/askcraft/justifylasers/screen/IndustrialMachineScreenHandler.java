package net.askcraft.justifylasers.screen;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.IndustryRecipes;
import net.askcraft.justifylasers.industry.MachineKind;
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
import net.minecraft.util.math.MathHelper;

public final class IndustrialMachineScreenHandler extends ScreenHandler {
    private final IndustrialMachineBlockEntity machine;
    private final PropertyDelegate properties;
    private final net.minecraft.world.World world;
    private final Inventory inventory;
    public static final int PROPERTY_COUNT = 43;
    private boolean machineSlotsVisible = true;

    public IndustrialMachineScreenHandler(int id, PlayerInventory player, BlockPos pos) {
        this(id, player, null, new SimpleInventory(IndustrialMachineBlockEntity.SLOT_COUNT), new ArrayPropertyDelegate(PROPERTY_COUNT));
        if (world.getBlockEntity(pos) instanceof IndustrialMachineBlockEntity machine) properties.set(0, machine.kind().ordinal());
    }
    public IndustrialMachineScreenHandler(int id, PlayerInventory player, IndustrialMachineBlockEntity machine) {
        this(id, player, machine, machine, new PropertyDelegate() {
            @Override public int get(int index) {
                int value = switch (index) {
                    case 0 -> machine.kind().ordinal();
                    case 1, 2 -> machine.progress();
                    case 3, 4 -> machine.duration();
                    case 5, 6 -> machine.energy().stored();
                    case 7, 8 -> machine.energy().capacity();
                    case 9, 10 -> machine.fuel();
                    case 11, 12 -> machine.fuelTotal();
                    case 13 -> machine.status().ordinal();
                    case 14 -> machine.enabled() ? 1 : 0;
                    case 15, 16 -> machine.rate();
                    case 17 -> machine.color();
                    case 18 -> machine.calibration();
                    case 19, 20 -> machine.water();
                    case 21, 22 -> machine.tankCapacity();
                    case 23 -> machine.formed() ? 1 : 0;
                    case 24 -> machine.redstoneMode().ordinal();
                    case 25 -> machine.isPrivate() ? 1 : 0;
                    case 26 -> machine.canManageSecurity(player.player) ? 1 : 0;
                    default -> index >= 27 && index < 43 && index - 27 < machine.ownerName().length()
                            ? machine.ownerName().charAt(index - 27) : 0;
                };
                return index >= 1 && index <= 12 || index == 15 || index == 16 || index >= 19 && index <= 22
                        ? index % 2 == 0 ? value >>> 16 : value & 0xFFFF : value;
            }
            @Override public void set(int index, int value) { }
            @Override public int size() { return PROPERTY_COUNT; }
        });
    }

    private IndustrialMachineScreenHandler(int id, PlayerInventory player, IndustrialMachineBlockEntity machine, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.INDUSTRIAL_MACHINE, id);
        this.machine = machine; this.properties = properties; this.world = player.player.getWorld(); this.inventory = inventory;
        for (int slot = 0; slot < IndustrialMachineBlockEntity.SLOT_COUNT; slot++) {
            int input = slot;
            int sx = slot < 4 ? 64 + slot * 24 : switch (slot) { case 4 -> 237; case 5 -> 182; default -> 213; };
            int sy = slot < 6 ? 70 : slot == 6 ? 39 : 69;
            addSlot(new Slot(inventory, slot, sx, sy) {
                @Override public boolean canInsert(ItemStack stack) { return accepts(input, stack); }
                @Override public int getMaxItemCount() { return input == IndustrialMachineBlockEntity.BLUEPRINT ? 1 : super.getMaxItemCount(); }
                @Override public boolean isEnabled() {
                    return machineSlotsVisible && (input < kind().inputs() || input == IndustrialMachineBlockEntity.OUTPUT
                            || input == IndustrialMachineBlockEntity.BLUEPRINT && kind() == MachineKind.ASSEMBLY_CHAMBER
                            || input >= IndustrialMachineBlockEntity.WATER_INPUT && kind() == MachineKind.CRYSTAL_GROWER);
                }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(player, 9 + row * 9 + column, 44 + column * 27, 136 + row * 20));
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 44 + column * 27, 198));
        addProperties(properties);
    }

    public MachineKind kind() { return MachineKind.values()[MathHelper.clamp(properties.get(0), 0, MachineKind.values().length - 1)]; }
    private int value(int low) { return (properties.get(low) & 0xFFFF) | properties.get(low + 1) << 16; }
    public int progress() { return value(1); }
    public int duration() { return value(3); }
    public int energy() { return value(5); }
    public int capacity() { return value(7); }
    public int fuel() { return value(9); }
    public int fuelTotal() { return value(11); }
    public int rate() { return value(15); }
    public int water() { return value(19); }
    public int tankCapacity() { return value(21); }
    public boolean formed() { return properties.get(23) != 0; }
    public void setMachineSlotsVisible(boolean visible) { machineSlotsVisible = visible; }
    public boolean isPrivate() { return properties.get(25) != 0; }
    public boolean canManageSecurity() { return properties.get(26) != 0; }
    public net.askcraft.justifylasers.laser.LaserRedstoneMode redstoneMode() { return net.askcraft.justifylasers.laser.LaserRedstoneMode.byIndex(properties.get(24)); }
    public String ownerName() {
        StringBuilder name = new StringBuilder();
        for (int index = 27; index < 43 && properties.get(index) != 0; index++) name.append((char)properties.get(index));
        return name.toString();
    }
    public net.askcraft.justifylasers.industry.MachineRecipeData recipe() {
        String blueprint = inventory.getStack(IndustrialMachineBlockEntity.BLUEPRINT).getItem() instanceof net.askcraft.justifylasers.item.AssemblyBlueprintItem item ? item.recipe() : "";
        return net.askcraft.justifylasers.industry.IndustryRecipe.all(world).stream().filter(recipe -> recipe.kind() == kind()
                && (kind() != MachineKind.ASSEMBLY_CHAMBER || recipe.blueprint().equals(blueprint))).findFirst().orElse(null);
    }
    private boolean accepts(int slot, ItemStack stack) {
        if (machine != null) return machine.isValid(slot, stack);
        if (slot == IndustrialMachineBlockEntity.BLUEPRINT) return kind() == MachineKind.ASSEMBLY_CHAMBER && stack.getItem() instanceof net.askcraft.justifylasers.item.AssemblyBlueprintItem;
        if (slot == IndustrialMachineBlockEntity.WATER_INPUT) return kind() == MachineKind.CRYSTAL_GROWER && stack.isOf(net.minecraft.item.Items.WATER_BUCKET);
        if (slot >= kind().inputs()) return false;
        var recipe = recipe();
        return recipe != null ? slot < recipe.inputs().size() && recipe.inputs().get(slot).test(stack)
                : kind() != MachineKind.ASSEMBLY_CHAMBER && IndustryRecipes.accepts(kind(), slot, stack);
    }
    public boolean enabled() { return properties.get(14) != 0; }
    public IndustrialMachineBlockEntity.Status status() { return IndustrialMachineBlockEntity.Status.values()[MathHelper.clamp(properties.get(13), 0, IndustrialMachineBlockEntity.Status.values().length - 1)]; }

    @Override public boolean canUse(PlayerEntity player) { return machine == null || machine.canPlayerUse(player); }
    @Override public boolean onButtonClick(PlayerEntity player, int id) {
        if (machine == null || !canUse(player)) return false;
        switch (id) {
            case 0 -> machine.toggle();
            case 1 -> machine.cycleRedstone();
            case 2 -> { if (!machine.togglePrivacy(player)) return false; }
            default -> { return false; }
        }
        sendContentUpdates(); return true;
    }
    @Override public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType action, PlayerEntity player) {
        if (canUse(player) && !player.isSpectator()) super.onSlotClick(slot, button, action, player);
    }
    @Override public ItemStack quickMove(PlayerEntity player, int index) {
        if (!canUse(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack(), original = stack.copy();
        int firstPlayer = IndustrialMachineBlockEntity.SLOT_COUNT, hotbar = firstPlayer + 27;
        if (index < firstPlayer) {
            if (!insertItem(stack, firstPlayer, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!insertItem(stack, 0, firstPlayer, false)) {
            if (index < hotbar ? !insertItem(stack, hotbar, slots.size(), false) : !insertItem(stack, firstPlayer, hotbar, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        slot.onTakeItem(player, stack);
        return original;
    }
}
