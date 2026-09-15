package net.askcraft.justifylasers.screen;

import net.askcraft.justifylasers.industry.IndustryRecipe;
import net.askcraft.justifylasers.item.AssemblyBlueprintItem;
import net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
import net.askcraft.justifylasers.platform.LaserScreenFactory;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public final class TabletScreenHandler extends ScreenHandler {
    public static final int RECORD = 1000;
    public enum Status { READY, RECORDED, NO_BLANK, NO_ENERGY, INVENTORY_FULL, UNAVAILABLE }
    private final PlayerInventory inventory;
    private final ItemStack tablet;
    private final int slot;
    private final List<AssemblyBlueprintItem> blueprints = List.copyOf(ModIndustry.BLUEPRINTS.values());
    private int selected, syncedCharge, syncedBlanks;
    private int syncedAssemblyTicks, syncedAssemblyRate;
    private Status status = Status.READY;

    public TabletScreenHandler(int id, PlayerInventory inventory, BlockPos data) {
        super(ModScreenHandlers.TABLET, id);
        this.inventory = inventory;
        slot = data.getX();
        tablet = slot >= 0 && slot < inventory.size() ? inventory.getStack(slot) : ItemStack.EMPTY;
        addProperty(new Property() {
            @Override public int get() { return inventory.player.getWorld().isClient ? syncedCharge : ExtraterrestrialTabletItem.charge(tablet); }
            @Override public void set(int value) { syncedCharge = value & 0xFFFF; }
        });
        addProperty(new Property() {
            @Override public int get() { return selected; }
            @Override public void set(int value) { selected = Math.max(0, Math.min(value, blueprints.size() - 1)); }
        });
        addProperty(new Property() {
            @Override public int get() { return status.ordinal(); }
            @Override public void set(int value) { status = Status.values()[Math.max(0, Math.min(value, Status.values().length - 1))]; }
        });
        addProperty(new Property() {
            @Override public int get() { return inventory.player.getWorld().isClient ? syncedBlanks : blankCount(); }
            @Override public void set(int value) { syncedBlanks = value & 0xFFFF; }
        });
        for (int index = 0; index < 4; index++) {
            boolean energy = index >= 2;
            int shift = (index & 1) * 16;
            addProperty(new Property() {
                @Override public int get() { return ((energy ? assemblyRate() : assemblyTicks()) >>> shift) & 0xFFFF; }
                @Override public void set(int value) {
                    if (energy) syncedAssemblyRate = (syncedAssemblyRate & ~(0xFFFF << shift)) | ((value & 0xFFFF) << shift);
                    else syncedAssemblyTicks = (syncedAssemblyTicks & ~(0xFFFF << shift)) | ((value & 0xFFFF) << shift);
                }
            });
        }
    }

    public List<AssemblyBlueprintItem> blueprints() { return blueprints; }
    public int selected() { return selected; }
    public Status status() { return status; }
    public int charge() { return inventory.player.getWorld().isClient ? syncedCharge : ExtraterrestrialTabletItem.charge(tablet); }
    public int blanks() { return inventory.player.getWorld().isClient ? syncedBlanks : blankCount(); }
    public ItemStack tablet() { return tablet; }
    public int tabletSlot() { return slot; }
    public AssemblyBlueprintItem blueprint() { return blueprints.get(selected); }
    public int assemblyTicks() { return inventory.player.getWorld().isClient ? syncedAssemblyTicks : recipeCost(false); }
    public int assemblyRate() { return inventory.player.getWorld().isClient ? syncedAssemblyRate : recipeCost(true); }
    private int recipeCost(boolean energy) {
        return IndustryRecipe.all(inventory.player.getWorld()).stream().filter(recipe -> blueprint().recipe().equals(recipe.blueprint()))
                .mapToInt(recipe -> energy ? recipe.rate() : recipe.duration()).findFirst().orElse(0);
    }
    private int blankCount() { return inventory.count(ModIndustry.BLANK_SCHEMATIC); }

    @Override public boolean canUse(PlayerEntity player) {
        return player == inventory.player && player.isAlive() && !player.isSpectator() && slot >= 0 && slot < inventory.size()
                && inventory.getStack(slot) == tablet && tablet.getItem() instanceof ExtraterrestrialTabletItem
                && tablet.getCount() == 1 && (slot == inventory.selectedSlot || slot == 40);
    }
    @Override public boolean onButtonClick(PlayerEntity player, int id) {
        if (!canUse(player) || player.getWorld().isClient) return false;
        if (id >= 0 && id < blueprints.size()) { selected = id; status = Status.READY; sendContentUpdates(); return true; }
        if (id != RECORD) return false;
        status = record(player);
        inventory.markDirty(); sendContentUpdates(); return status == Status.RECORDED;
    }

    private Status record(PlayerEntity player) {
        if (charge() < ExtraterrestrialTabletItem.WRITE_COST) return Status.NO_ENERGY;
        if (IndustryRecipe.all(player.getWorld()).stream().noneMatch(recipe -> blueprint().recipe().equals(recipe.blueprint()))) return Status.UNAVAILABLE;
        int blankSlot = -1;
        for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i).isOf(ModIndustry.BLANK_SCHEMATIC)) { blankSlot = i; break; }
        if (blankSlot < 0) return Status.NO_BLANK;
        ItemStack blanks = inventory.getStack(blankSlot);
        int destination = blanks.getCount() == 1 ? blankSlot : inventory.getEmptySlot();
        if (destination < 0) return Status.INVENTORY_FULL;
        // Validate every prerequisite before mutating the card, battery, or inventory.
        blanks.decrement(1);
        inventory.setStack(destination, new ItemStack(blueprint()));
        ExtraterrestrialTabletItem.setCharge(tablet, charge() - ExtraterrestrialTabletItem.WRITE_COST);
        return Status.RECORDED;
    }

    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    @Override public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType action, PlayerEntity player) { }

    public record Factory(int slot) implements LaserScreenFactory {
        @Override public BlockPos screenPosition() { return new BlockPos(slot, 0, 0); }
        @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buffer) { buffer.writeBlockPos(screenPosition()); }
        @Override public Text getDisplayName() { return Text.translatable("item.justifylasers.extraterrestrial_tablet"); }
        @Override public ScreenHandler createMenu(int id, PlayerInventory inventory, PlayerEntity player) { return new TabletScreenHandler(id, inventory, screenPosition()); }
    }
}
