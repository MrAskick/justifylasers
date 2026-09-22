package net.askcraft.justifylasers.screen;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.printing.PrintData;
import net.askcraft.justifylasers.printing.PrintDesign;
import net.askcraft.justifylasers.registry.ModIndustry;
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

public final class ModelEncoderScreenHandler extends ScreenHandler {
    public static final int BEGIN = 8100, CHUNK = 8101, COMMIT = 8102, CHUNK_SIZE = 8000;
    public static final int DRAFT_BEGIN = 8103, DRAFT_COMMIT = 8104;
    public static final int UPLOAD_INTERVAL = 10;
    private final IndustrialMachineBlockEntity machine;
    private final PropertyDelegate properties;
    private StringBuilder upload;
    private int expectedLength, sequence;
    private long lastBegin = Long.MIN_VALUE, lastDraftBegin = Long.MIN_VALUE, started;
    private int editingRevision;
    private int draftResult;
    private boolean draftUpload;
    private BlockPos encoderPos;
    private net.minecraft.world.World world;

    public ModelEncoderScreenHandler(int id, PlayerInventory player, BlockPos pos) {
        this(id, player, null, new SimpleInventory(8), new ArrayPropertyDelegate(26));
        encoderPos=pos.toImmutable();
    }
    public ModelEncoderScreenHandler(int id, PlayerInventory player, IndustrialMachineBlockEntity machine) {
        this(id, player, machine, machine, new PropertyDelegate() {
            @Override public int size() { return 26; }
            @Override public void set(int index, int value) { }
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> machine.energy().stored() & 0xFFFF;
                    case 1 -> machine.energy().stored() >>> 16;
                    case 2 -> machine.status().ordinal();
                    case 3 -> machine.enabled() ? 1 : 0;
                    case 4 -> machine.redstoneMode().ordinal();
                    case 5 -> machine.isPrivate() ? 1 : 0;
                    case 6 -> machine.canManageSecurity(player.player) ? 1 : 0;
                    case 7 -> machine.hologramEnabled() ? 1 : 0;
                    default -> index >= 8 && index < 24 && index - 8 < machine.ownerName().length() ? machine.ownerName().charAt(index - 8) : 0;
                };
            }
        });
        encoderPos=machine.getPos();editingRevision=machine.draftRevision();
    }
    private ModelEncoderScreenHandler(int id, PlayerInventory player, IndustrialMachineBlockEntity machine, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.MODEL_ENCODER, id); this.machine = machine;
        this.properties = machine==null?properties:new PropertyDelegate(){
            public int size(){return properties.size();}
            public int get(int index){return index==24?draftResult:index==25?cooldown(false)|(cooldown(true)<<8):properties.get(index);}
            public void set(int index,int value){properties.set(index,value);}
        };
        world=player.player.getWorld();
        addSlot(new Slot(inventory, 0, 15, 195) {
            @Override public boolean canInsert(ItemStack stack) { return stack.isOf(ModIndustry.BLANK_SCHEMATIC) || stack.isOf(ModIndustry.MODEL_SCHEMATIC); }
        });
        addSlot(new Slot(inventory, IndustrialMachineBlockEntity.OUTPUT, 51, 195) { @Override public boolean canInsert(ItemStack stack) { return false; } });
        for (int row=0; row<3; row++) for (int col=0; col<9; col++) addSlot(new Slot(player,9+row*9+col,79+col*18,171+row*18));
        for (int col=0; col<9; col++) addSlot(new Slot(player,col,79+col*18,229));
        addProperties(this.properties);
    }
    public int energy() { return (properties.get(0) & 0xFFFF) | properties.get(1) << 16; }
    public boolean enabled() { return properties.get(3) != 0; }
    public boolean hologramEnabled() { return properties.get(7)!=0; }
    public int draftResult() { return properties.get(24); }
    public int uploadCooldown(boolean draft) { return properties.get(25) >>> (draft ? 8 : 0) & 0xFF; }
    private int cooldown(boolean draft) {
        long last = draft ? lastDraftBegin : lastBegin;
        return last == Long.MIN_VALUE ? 0 : (int)Math.max(0, Math.min(UPLOAD_INTERVAL, UPLOAD_INTERVAL - (world.getTime() - last)));
    }
    public BlockPos encoderPos() { return encoderPos; }
    public net.askcraft.justifylasers.printing.EncoderDraft draft() {
        var block=machine!=null?machine:world.getBlockEntity(encoderPos);
        return block instanceof IndustrialMachineBlockEntity encoder?encoder.encoderDraft():null;
    }
    public net.askcraft.justifylasers.laser.LaserRedstoneMode redstone() { return net.askcraft.justifylasers.laser.LaserRedstoneMode.byIndex(properties.get(4)); }
    public boolean isPrivate() { return properties.get(5) != 0; }
    public boolean canManageSecurity() { return properties.get(6) != 0; }
    public String ownerName() { StringBuilder text = new StringBuilder(); for (int i=8;i<24 && properties.get(i)!=0;i++) text.append((char)properties.get(i)); return text.toString(); }
    public IndustrialMachineBlockEntity.Status status() { return IndustrialMachineBlockEntity.Status.values()[Math.max(0,Math.min(IndustrialMachineBlockEntity.Status.values().length-1,properties.get(2)))]; }
    public PrintDesign design() { var input = PrintData.read(getSlot(0).getStack()); return input == null ? PrintData.read(getSlot(1).getStack()) : input; }
    @Override public boolean canUse(PlayerEntity player) { return machine == null || machine.kind() == MachineKind.MODEL_ENCODER && machine.canPlayerUse(player); }
    @Override public boolean onButtonClick(PlayerEntity player, int id) {
        if (machine == null || !canUse(player) || player.isSpectator()) return false;
        switch (id) {
            case 0 -> machine.toggle();
            case 1 -> machine.cycleRedstone();
            case 2 -> { if (!machine.togglePrivacy(player)) return false; }
            case 3 -> machine.toggleHologram();
            default -> { return false; }
        }
        sendContentUpdates(); return true;
    }
    public boolean receive(PlayerEntity player, int action, String value) {
        if (machine == null || !player.isAlive() || player.isSpectator() || !canUse(player)) { upload = null; return false; }
        long now = player.getWorld().getTime();
        if (action == BEGIN || action == DRAFT_BEGIN) {
            boolean draft = action == DRAFT_BEGIN;
            if (cooldown(draft) > 0 || upload != null && now - started <= 600) return false;
            int length;
            try { length = Integer.parseInt(value); } catch (NumberFormatException failure) { return false; }
            if (length <= 0 || length > (draft ? net.askcraft.justifylasers.printing.EncoderDraft.MAX_JSON : PrintDesign.MAX_DOCUMENT_JSON)) return false;
            // Draft autosaves and card writes have separate budgets so one cannot starve the other.
            if (draft) lastDraftBegin = now; else lastBegin = now;
            if (draft) draftResult = 0;
            draftUpload = draft;
            expectedLength = length;
            upload = new StringBuilder(Math.min(length, CHUNK_SIZE));
            sequence = 0;
            started = now;
            sendContentUpdates();
            return true;
        }
        if (upload == null || now - started > 600) { upload = null; return false; }
        if (action == CHUNK) {
            int colon = value.indexOf(':');
            try {
                if (colon < 1 || Integer.parseInt(value.substring(0,colon)) != sequence || value.length()-colon-1 > CHUNK_SIZE
                        || upload.length()+value.length()-colon-1 > expectedLength) { upload = null; return false; }
            } catch (NumberFormatException failure) { upload = null; return false; }
            upload.append(value,colon+1,value.length()); sequence++; return true;
        }
        if (action == COMMIT || action == DRAFT_COMMIT) {
            String json = upload.toString(); upload = null;
            if (json.length() != expectedLength || draftUpload!=(action==DRAFT_COMMIT)) return false;
            if(draftUpload){boolean saved=machine.saveDraft(player,json,editingRevision);if(saved)editingRevision=machine.draftRevision();draftResult=saved?1:2;sendContentUpdates();return saved;}
            boolean encoded = machine.encodeModel(player,json); sendContentUpdates(); return encoded;
        }
        upload = null; return false;
    }
    @Override public void onClosed(PlayerEntity player) { upload = null; super.onClosed(player); }
    @Override public void onSlotClick(int slot, int button, net.minecraft.screen.slot.SlotActionType action, PlayerEntity player) {
        if (canUse(player) && !player.isSpectator()) super.onSlotClick(slot,button,action,player);
    }
    @Override public ItemStack quickMove(PlayerEntity player, int index) {
        if (!canUse(player) || player.isSpectator() || index<0 || index>=slots.size()) return ItemStack.EMPTY;
        var slot=slots.get(index); if (!slot.hasStack()) return ItemStack.EMPTY;
        var stack=slot.getStack(); var original=stack.copy();
        if (index<2 ? !insertItem(stack,2,slots.size(),true) : !insertItem(stack,0,1,false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty(); slot.onTakeItem(player,stack); return original;
    }
}
