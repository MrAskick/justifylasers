package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.item.LaserGunItem;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserTargetFilter;
import net.askcraft.justifylasers.laser.LaserWeapon;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.platform.InventoryNbt;
import net.askcraft.justifylasers.platform.LaserBlockEntity;
import net.askcraft.justifylasers.platform.LaserScreenFactory;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.screen.LaserTurretScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.UUID;

public final class LaserTurretBlockEntity extends LaserBlockEntity implements Inventory, LaserScreenFactory {
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);
    private final LaserTargetFilter filter = new LaserTargetFilter();
    private UUID owner;
    private String ownerName = "";
    private boolean enabled = true, firing;
    private int targetId = -1;
    private int beamRange = 32;
    private float yaw, pitch, previousYaw, previousPitch, syncedYaw, syncedPitch;

    public LaserTurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_TURRET, pos, state);
        filter.setFlags(LaserTargetFilter.HOSTILE | LaserTargetFilter.EXCLUDE_OWNER);
    }

    public void setOwner(PlayerEntity player) {
        owner = player.getUuid(); ownerName = player.getGameProfile().getName();
        yaw = player.getYaw(); syncedYaw = yaw; sync();
    }

    public LaserTargetFilter filter() { return filter; }
    public boolean hasFilter() { return items.get(1).isOf(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER)); }
    public boolean hasGun() { return items.get(0).getItem() instanceof LaserGunItem; }
    public boolean enabled() { return enabled; }
    public boolean firing() { return firing && hasGun(); }
    public String ownerName() { return ownerName; }
    public int beamRange() { return beamRange; }
    public float yaw(float delta) { return MathHelper.lerpAngleDegrees(delta, previousYaw, yaw); }
    public float pitch(float delta) { return MathHelper.lerp(delta, previousPitch, pitch); }
    public int color() { return LaserColor.byIndex(GameVersion.cubeColor(items.get(0))).rgb(); }
    public Vec3d pivot() { return Vec3d.of(pos).add(0.5, 1.2, 0.5); }
    public Vec3d direction(float delta) { return Vec3d.fromPolar(pitch(delta), yaw(delta)); }
    public Vec3d muzzle(float delta) { return pivot().add(direction(delta).multiply(0.82)); }

    public boolean allows(LivingEntity entity) {
        if (entity instanceof PlayerEntity player && (player.isSpectator() || player.isCreative())) return false;
        if (hasFilter()) return filter.allows(entity, owner);
        return entity.getType().getSpawnGroup() == net.minecraft.entity.SpawnGroup.MONSTER;
    }

    public static void tick(World world, BlockPos pos, BlockState state, LaserTurretBlockEntity turret) {
        turret.previousYaw = turret.yaw; turret.previousPitch = turret.pitch;
        if (world.isClient) {
            turret.yaw = MathHelper.stepUnwrappedAngleTowards(turret.yaw, turret.syncedYaw, 18);
            turret.pitch = MathHelper.stepTowards(turret.pitch, turret.syncedPitch, 12);
            return;
        }
        boolean wasFiring = turret.firing;
        turret.beamRange = LaserConfig.get().turretRange;
        turret.firing = false;
        if (!turret.enabled || !turret.hasGun()) {
            turret.targetId = -1;
            if (wasFiring) turret.sync();
            return;
        }
        LivingEntity target = world.getEntityById(turret.targetId) instanceof LivingEntity entity ? entity : null;
        if (target == null || !turret.validTarget(target)) target = null;
        if (target == null && world.getTime() % 5 == Math.floorMod(pos.asLong(), 5)) {
            target = world.getEntitiesByClass(LivingEntity.class, new Box(turret.pivot(), turret.pivot()).expand(LaserConfig.get().turretRange), turret::validTarget)
                    .stream().sorted(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(turret.pivot())))
                    .filter(turret::visible).findFirst().orElse(null);
        }
        turret.targetId = target == null ? -1 : target.getId();
        if (target != null) {
            Vec3d delta = target.getBoundingBox().getCenter().subtract(turret.pivot());
            float desiredYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float desiredPitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
            turret.yaw = MathHelper.stepUnwrappedAngleTowards(turret.yaw, desiredYaw, 12);
            turret.pitch = MathHelper.stepTowards(turret.pitch, MathHelper.clamp(desiredPitch, -70, 70), 10);
            if (Math.abs(MathHelper.wrapDegrees(desiredYaw - turret.yaw)) < 2 && Math.abs(desiredPitch - turret.pitch) < 2) {
                var hit = LaserWeapon.trace(world, turret.pivot(), turret.direction(1), LaserConfig.get().turretRange, null);
                // Never shoot through a friendly entity just because the selected target is hostile.
                turret.firing = hit.target() != null && turret.allows(hit.target());
                if (turret.firing) LaserWeapon.damage(world, hit, turret.owner == null ? null : world.getPlayerByUuid(turret.owner));
            }
            if (!turret.visible(target)) turret.targetId = -1;
        }
        if (wasFiring != turret.firing || world.getTime() % 2 == 0
                && (Math.abs(turret.yaw - turret.previousYaw) > 0.01 || Math.abs(turret.pitch - turret.previousPitch) > 0.01)) turret.sync();
    }

    private boolean validTarget(LivingEntity entity) {
        return entity.isAlive() && !entity.isSpectator() && allows(entity)
                && entity.squaredDistanceTo(pivot()) <= (double) LaserConfig.get().turretRange * LaserConfig.get().turretRange;
    }

    private boolean visible(LivingEntity entity) {
        return LaserWeapon.trace(world, pivot(), entity.getBoundingBox().getCenter().subtract(pivot()),
                LaserConfig.get().turretRange, null).target() == entity;
    }

    public void toggle(int id) {
        if (id == 0) enabled = !enabled;
        else if (id >= 1 && id <= 4 && hasFilter()) filter.setFlags(filter.flags() ^ (1 << (id - 1)));
        else if (id == 5 && hasGun()) GameVersion.setCubeColor(items.get(0), (GameVersion.cubeColor(items.get(0)) + 1) % LaserColor.values().length);
        targetId = -1; sync();
    }

    public boolean toggleExcludedPlayer(PlayerEntity player, String name) {
        if (!canPlayerUse(player) || !player.isAlive() || !hasFilter() || player.getServer() == null
                || !name.matches("[A-Za-z0-9_]{1,16}")) return false;
        boolean changed = filter.removeName(name);
        if (!changed) {
            var target = player.getServer().getPlayerManager().getPlayer(name);
            if (target != null) changed = filter.exclude(target.getUuid(), target.getGameProfile().getName());
        }
        if (changed) sync();
        else player.sendMessage(Text.translatable("message.justifylasers.filter_player_unavailable", LaserTargetFilter.MAX_EXCLUSIONS), true);
        return changed;
    }

    public void sync() {
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }

    @Override protected void writeLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        inventory.write(nbt, items); nbt.put("Filter", filter.write());
        nbt.putInt("BeamRange", beamRange);
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putString("OwnerName", ownerName); nbt.putBoolean("Enabled", enabled);
        nbt.putBoolean("Firing", firing); nbt.putFloat("Yaw", yaw); nbt.putFloat("Pitch", pitch);
    }

    @Override protected void readLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        // Update packets contain a complete inventory; empty slots are omitted by vanilla NBT.
        items.clear();
        inventory.read(nbt, items);
        if (nbt.contains("Filter")) filter.read(nbt.getCompound("Filter"));
        beamRange = nbt.contains("BeamRange") ? MathHelper.clamp(nbt.getInt("BeamRange"), 1, 128) : 32;
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null; ownerName = nbt.getString("OwnerName");
        enabled = !nbt.contains("Enabled") || nbt.getBoolean("Enabled"); firing = nbt.getBoolean("Firing");
        syncedYaw = Float.isFinite(nbt.getFloat("Yaw")) ? nbt.getFloat("Yaw") : 0;
        syncedPitch = Float.isFinite(nbt.getFloat("Pitch")) ? MathHelper.clamp(nbt.getFloat("Pitch"), -70, 70) : 0;
        if (world == null || !world.isClient) { yaw = previousYaw = syncedYaw; pitch = previousPitch = syncedPitch; }
    }

    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public BlockPos screenPosition() { return pos; }
    @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buffer) { buffer.writeBlockPos(pos); }
    @Override public Text getDisplayName() { return Text.translatable("block.justifylasers.laser_turret"); }
    @Override public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
        return canPlayerUse(player) ? new LaserTurretScreenHandler(syncId, inventory, this) : null;
    }
    @Override public int size() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) { ItemStack result = Inventories.splitStack(items, slot, amount); sync(); return result; }
    @Override public ItemStack removeStack(int slot) { ItemStack result = Inventories.removeStack(items, slot); sync(); return result; }
    @Override public void setStack(int slot, ItemStack stack) { items.set(slot, stack); stack.setCount(Math.min(1, stack.getCount())); targetId = -1; sync(); }
    @Override public void clear() { items.clear(); targetId = -1; sync(); }
    @Override public int getMaxCountPerStack() { return 1; }
    @Override public boolean isValid(int slot, ItemStack stack) {
        return slot == 0 ? stack.getItem() instanceof LaserGunItem : slot == 1 && stack.isOf(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER));
    }
    @Override public boolean canPlayerUse(PlayerEntity player) {
        return world != null && world.getBlockEntity(pos) == this && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64
                && !player.isSpectator() && (owner == null || owner.equals(player.getUuid()) || player.isCreative());
    }
}
