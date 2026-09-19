package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.block.LaserPartBlock;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.laser.BeamBehavior;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserDamage;
import net.askcraft.justifylasers.laser.LaserEntityMode;
import net.askcraft.justifylasers.laser.LaserLootCollector;
import net.askcraft.justifylasers.laser.LaserMining;
import net.askcraft.justifylasers.laser.LaserStorage;
import net.askcraft.justifylasers.laser.LaserTargetFilter;
import net.askcraft.justifylasers.platform.InventoryNbt;
import net.askcraft.justifylasers.platform.LaserBlockEntity;
import net.askcraft.justifylasers.platform.LaserScreenFactory;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.askcraft.justifylasers.screen.LaserModuleScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class LaserPartBlockEntity extends LaserBlockEntity implements LaserScreenFactory, net.askcraft.justifylasers.platform.AutomatedInventory, LaserLootCollector {
    public static final int PROPERTY_COUNT = 13;
    public static final int STORAGE_SIZE = 9;
    public static final int DROPS_SLOT = 9, SILK_SLOT = 10, IGNITION_SLOT = 11, COLLECTION_SLOT = 12;
    public static final int INVENTORY_SIZE = 13;
    private boolean enabled = true;
    private int strength = LaserDamage.DEFAULT_DAMAGE_STEP;
    private int knockback = LaserDamage.DEFAULT_KNOCKBACK_STEP;
    private int frequency = 20;
    private int miningSpeed;
    private int spectrum = net.askcraft.justifylasers.laser.LaserSpectrum.DEFAULT;
    private UUID owner;
    private final LaserTargetFilter filter = new LaserTargetFilter();
    private final DefaultedList<ItemStack> storage = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

    public LaserPartBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.LASER_PART, pos, state); }
    public LaserModule module() { return getCachedState().getBlock() instanceof LaserPartBlock part ? part.module() : null; }
    public boolean enabled() { return enabled; }
    public LaserTargetFilter filter() { return filter; }
    public int spectrum() { return spectrum; }
    public void initializeSpectrum(ItemStack stack) {
        if (module() == LaserModule.SPECTRUM) { spectrum = net.askcraft.justifylasers.laser.LaserSpectrum.color(stack); sync(); }
    }
    public int rangeBonus() { return ((LaserPartBlock) getCachedState().getBlock()).partId().startsWith("advanced_") ? 8 : 1; }
    public void initializeOwner(PlayerEntity player) { if (owner == null) { owner = player.getUuid(); sync(); } }

    public BeamBehavior apply(BeamBehavior input) {
        LaserModule module = module();
        if (!enabled || module == null) return input;
        var entity = input.entity();
        var mining = input.mining();
        if (module.isEntityMode()) return input.plusEntity(new BeamBehavior.EntityEffect(LaserEntityMode.of(module), true,
                strength, knockback, frequency, entity.ignite() || hasUpgrade(IGNITION_SLOT, LaserModule.IGNITION)));
        return switch (module) {
            case BLOCK_DESTRUCTION -> input.withMining(new BeamBehavior.MiningEffect(true, Math.max(mining.enabled() ? mining.speed() : 0, miningSpeed),
                    mining.silk() || hasUpgrade(SILK_SLOT, LaserModule.SILK_TOUCH), mining.drops() || hasUpgrade(DROPS_SLOT, LaserModule.BLOCK_DROPS),
                    mining.collect() || hasUpgrade(COLLECTION_SLOT, LaserModule.BLOCK_COLLECTION), mining.smelt() || hasUpgrade(IGNITION_SLOT, LaserModule.IGNITION)).at(pos),
                    mining.collect() ? input.collector() : pos);
            case SILK_TOUCH -> input.withMining(new BeamBehavior.MiningEffect(mining.enabled(), mining.speed(), true, mining.drops(), mining.collect(), mining.smelt()), input.collector());
            case BLOCK_DROPS -> input.withMining(new BeamBehavior.MiningEffect(mining.enabled(), mining.speed(), mining.silk(), true, mining.collect(), mining.smelt()), input.collector());
            case BLOCK_COLLECTION -> input.withMining(new BeamBehavior.MiningEffect(mining.enabled(), mining.speed(), mining.silk(), mining.drops(), true, mining.smelt()), pos);
            case IGNITION -> input.igniting();
            case TARGET_FILTER -> input.withFilter(filter.copy(), owner);
            case THICKNESS -> input.withWidth(Math.min(100, input.widthMultiplier() * (float) Math.pow(1.075, rangeBonus())));
            case SCORCH_MARKS -> input.withScorch(true);
            case SPECTRUM -> input.withSpectrum(true);
            default -> input;
        };
    }

    public long operatingFlux() {
        LaserModule module = module();
        if (module == null || !enabled) return 0;
        int upgrades = 0;
        for (int slot = STORAGE_SIZE; slot < INVENTORY_SIZE; slot++) if (!storage.get(slot).isEmpty()) upgrades++;
        return net.askcraft.justifylasers.laser.BeamModuleCost.lumens(module, strength, knockback, frequency, miningSpeed,
                hasUpgrade(IGNITION_SLOT, LaserModule.IGNITION), upgrades,
                module == LaserModule.RANGE || module == LaserModule.THICKNESS ? rangeBonus() : 1);
    }

    public boolean hasUpgrade(int slot, LaserModule module) {
        return storage.get(slot).getItem() instanceof net.askcraft.justifylasers.item.LaserModuleItem item && item.module() == module;
    }

    public static boolean acceptsUpgrade(LaserModule type, int slot, ItemStack stack) {
        if (!(stack.getItem() instanceof net.askcraft.justifylasers.item.LaserModuleItem item)) return false;
        if (type == LaserModule.ENTITY_DAMAGE) return slot == IGNITION_SLOT && item.module() == LaserModule.IGNITION;
        if (type != LaserModule.BLOCK_DESTRUCTION) return false;
        return item.module() == switch (slot) {
            case DROPS_SLOT -> LaserModule.BLOCK_DROPS;
            case SILK_SLOT -> LaserModule.SILK_TOUCH;
            case IGNITION_SLOT -> LaserModule.IGNITION;
            case COLLECTION_SLOT -> LaserModule.BLOCK_COLLECTION;
            default -> null;
        };
    }

    public int property(int index) {
        return switch (index) {
            case 0 -> module() == null ? -1 : module().ordinal();
            case 1 -> enabled ? 1 : 0;
            case 2 -> strength;
            case 3 -> knockback;
            case 4 -> frequency;
            case 5 -> miningSpeed;
            case 6 -> hasUpgrade(COLLECTION_SLOT, LaserModule.BLOCK_COLLECTION) ? 1 : 0;
            case 7 -> filter.flags();
            case 8 -> rangeBonus();
            case 9 -> (int) Math.min(Integer.MAX_VALUE, operatingFlux()) & 0xFFFF;
            case 10 -> (int) (Math.min(Integer.MAX_VALUE, operatingFlux()) >>> 16);
            case 11 -> spectrum & 0xFFFF;
            case 12 -> spectrum >>> 16;
            default -> 0;
        };
    }

    public boolean setting(PlayerEntity player, int id, String argument) {
        if (!canPlayerUse(player)) return false;
        LaserModule module = module();
        if (id == LaserEmitterScreenHandler.BUTTON_ENABLED) enabled = !enabled;
        else if (module == LaserModule.SPECTRUM && id == LaserEmitterScreenHandler.BUTTON_SPECTRUM) {
            int rgb = net.askcraft.justifylasers.laser.LaserSpectrum.parse(argument);
            if (rgb < 0) return false;
            spectrum = rgb;
        }
        else if (module == LaserModule.TARGET_FILTER) {
            if (id == LaserEmitterScreenHandler.BUTTON_FILTER_MODE) filter.toggleMode();
            else if (id == LaserEmitterScreenHandler.BUTTON_FILTER_TYPE) { if (!filter.toggleType(argument)) return false; }
            else if (id >= LaserEmitterScreenHandler.FILTER_BUTTON_BASE && id < LaserEmitterScreenHandler.FILTER_BUTTON_BASE + 4)
                filter.setFlags(filter.flags() ^ 1 << (id - LaserEmitterScreenHandler.FILTER_BUTTON_BASE));
            else return false;
        } else if (module == LaserModule.BLOCK_DESTRUCTION) {
            if (id >= LaserEmitterScreenHandler.MINING_SPEED_BUTTON_BASE && id <= LaserEmitterScreenHandler.MINING_SPEED_BUTTON_MAX)
                miningSpeed = id - LaserEmitterScreenHandler.MINING_SPEED_BUTTON_BASE;
            else return false;
        } else if (module.isEntityMode() && module != LaserModule.ENTITY_HEAL) {
            if (id >= LaserEmitterScreenHandler.DAMAGE_BUTTON_MIN && id <= LaserEmitterScreenHandler.DAMAGE_BUTTON_MAX)
                strength = Math.min(LaserEntityMode.of(module).movesEntities() ? 50 : LaserDamage.MAX_DAMAGE_STEP, id - LaserEmitterScreenHandler.DAMAGE_BUTTON_BASE);
            else if (module == LaserModule.ENTITY_DAMAGE && id >= LaserEmitterScreenHandler.KNOCKBACK_BUTTON_BASE && id <= LaserEmitterScreenHandler.KNOCKBACK_BUTTON_MAX)
                knockback = id - LaserEmitterScreenHandler.KNOCKBACK_BUTTON_BASE;
            else if (!LaserEntityMode.of(module).movesEntities() && id >= LaserEmitterScreenHandler.HIT_RATE_BUTTON_MIN && id <= LaserEmitterScreenHandler.HIT_RATE_BUTTON_MAX)
                frequency = id - LaserEmitterScreenHandler.HIT_RATE_BUTTON_BASE;
            else return false;
        } else return false;
        sync();
        return true;
    }

    public boolean hasStorage() { return module() == LaserModule.BLOCK_DESTRUCTION || module() == LaserModule.BLOCK_COLLECTION; }
    @Override public int size() { return hasStorage() || module() == LaserModule.ENTITY_DAMAGE ? INVENTORY_SIZE : 0; }
    @Override public boolean isValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < STORAGE_SIZE ? hasStorage() : acceptsUpgrade(module(), slot, stack);
    }
    @Override public boolean isEmpty() { return storage.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return storage.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) { ItemStack result = Inventories.splitStack(storage, slot, amount); if (!result.isEmpty()) sync(); return result; }
    @Override public ItemStack removeStack(int slot) { ItemStack result = Inventories.removeStack(storage, slot); if (!result.isEmpty()) sync(); return result; }
    @Override public void setStack(int slot, ItemStack stack) {
        if (!stack.isEmpty() && (!isValid(slot, stack) || slot >= STORAGE_SIZE && stack.getCount() > 1)) throw new IllegalArgumentException("Invalid module slot " + slot);
        storage.set(slot, stack); sync();
    }
    @Override public void markDirty() { super.markDirty(); LaserBeamNetwork.invalidate(world); }
    @Override public void clear() { storage.clear(); sync(); }
    @Override public boolean canPlayerUse(PlayerEntity player) {
        return module() != null && world != null && player.getWorld() == world && world.getBlockEntity(pos) == this && !isRemoved()
                && player.isAlive() && !player.isSpectator() && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64;
    }
    @Override public ItemStack collect(ItemStack stack) {
        if (!hasStorage()) return stack;
        ItemStack result = LaserStorage.insert(storage, 0, 9, stack);
        if (result.getCount() != stack.getCount()) sync();
        return result;
    }
    private void sync() {
        markDirty();
        LaserBeamNetwork.invalidate(world);
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }
    @Override protected void writeLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        if (module() == null) return;
        nbt.putBoolean("Enabled", enabled); nbt.putInt("Strength", strength); nbt.putInt("Knockback", knockback);
        nbt.putInt("Frequency", frequency); nbt.putInt("MiningSpeed", miningSpeed);
        nbt.putInt("SpectrumRgb", spectrum);
        nbt.put("Filter", filter.write()); if (owner != null) nbt.putUuid("Owner", owner);
        if (size() > 0) inventory.write(nbt, storage);
    }
    @Override protected void readLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        enabled = !nbt.contains("Enabled") || nbt.getBoolean("Enabled");
        strength = nbt.contains("Strength") ? LaserDamage.clampDamageStep(nbt.getInt("Strength")) : LaserDamage.DEFAULT_DAMAGE_STEP;
        if (LaserEntityMode.of(module()).movesEntities()) strength = Math.min(50, strength);
        knockback = nbt.contains("Knockback") ? LaserDamage.clampKnockbackStep(nbt.getInt("Knockback")) : LaserDamage.DEFAULT_KNOCKBACK_STEP;
        frequency = nbt.contains("Frequency") ? LaserDamage.clampHitsPerSecond(nbt.getInt("Frequency")) : 20;
        miningSpeed = LaserMining.clampSpeedStep(nbt.getInt("MiningSpeed"));
        spectrum = nbt.contains("SpectrumRgb") ? nbt.getInt("SpectrumRgb") & 0xFFFFFF : net.askcraft.justifylasers.laser.LaserSpectrum.DEFAULT;
        filter.read(nbt.getCompound("Filter")); owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        storage.clear(); inventory.read(nbt, storage);
        LaserBeamNetwork.invalidate(world);
    }
    @Override public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public Text getDisplayName() { return Text.translatable(getCachedState().getBlock().getTranslationKey()); }
    @Override public BlockPos screenPosition() { return pos; }
    @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) { buf.writeBlockPos(pos); }
    @Override public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
        return canPlayerUse(player) ? new LaserModuleScreenHandler(syncId, inventory, this) : null;
    }
}
