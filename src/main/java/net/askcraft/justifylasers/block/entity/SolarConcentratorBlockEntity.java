package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.block.LaserComponentBlock;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.industry.SolarExposure;
import net.askcraft.justifylasers.industry.SolarStructure;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserBeamSource;
import net.askcraft.justifylasers.laser.LaserDamage;
import net.askcraft.justifylasers.laser.LaserRedstoneMode;
import net.askcraft.justifylasers.laser.LuminousFlux;
import net.askcraft.justifylasers.platform.InventoryNbt;
import net.askcraft.justifylasers.platform.LaserScreenFactory;
import net.askcraft.justifylasers.screen.SolarConcentratorScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.UUID;

public final class SolarConcentratorBlockEntity extends LaserComponentBlockEntity implements LaserBeamSource, LaserScreenFactory {
    public enum Status { INCOMPLETE, DISABLED, NO_SUN, OBSTRUCTED, WORKING, OFF, REDSTONE }
    public static final int BEAM_RGB = 0xFFF2CC;
    private long flux;
    private int range = 256;
    private float width = 3;
    private long panels;
    private long powerTick = Long.MIN_VALUE;
    private long damageTick = Long.MIN_VALUE;
    private long nextExposure = Long.MIN_VALUE;
    private Status status = Status.INCOMPLETE;
    private boolean enabled = true;
    private LaserRedstoneMode redstoneMode = LaserRedstoneMode.IGNORE;
    private boolean redstoneSignal;
    private UUID owner;
    private String ownerName = "";
    private boolean privateAccess;
    private Direction outputSide;

    public SolarConcentratorBlockEntity(BlockPos pos, BlockState state) { super(pos, state); }
    public Direction facing() { return getCachedState().get(LaserComponentBlock.FACING); }
    public boolean small() { return ((LaserComponentBlock)getCachedState().getBlock()).component().equals("small_solar_concentrator"); }
    public boolean operationalStructure() { return small() ? controllerPos() == null : formed() && pos.equals(controllerPos()); }
    public Direction outputSide() { return outputSide == null ? facing() : outputSide; }
    public void selectOutput(Direction side) {
        if (side == null || !side.getAxis().isHorizontal()) return;
        outputSide = side;
        LaserBeamNetwork.invalidate(world);
        shareSettings();
        sync();
    }
    public Status status() { return status; }
    public boolean enabled() { return enabled; }
    public LaserRedstoneMode redstoneMode() { return redstoneMode; }
    public boolean hasRedstoneSignal() { return redstoneSignal; }
    public int output() { return LuminousFlux.toEnergyRate(flux, LaserConfig.get().lumensPerEnergyUnit, 1); }
    public int convertedEnergyRate() { return LuminousFlux.toEnergyRate(flux, LaserConfig.get().lumensPerEnergyUnit, LaserConfig.get().energyTransmissionEfficiency); }
    public long peakFlux() { return small() ? LaserConfig.get().smallSolarPeakFlux : LaserConfig.get().solarPeakFlux; }
    public long visiblePanels() { return panels; }
    @Override public void setWorld(World world) { super.setWorld(world); LaserBeamNetwork.registerEmitter(world, pos); }
    @Override public void cancelRemoval() { super.cancelRemoval(); if (world != null) LaserBeamNetwork.registerEmitter(world, pos); }
    @Override public void markRemoved() { super.markRemoved(); if (world != null) LaserBeamNetwork.removeEmitter(world, pos); }

    public void solarTick() {
        if (world == null || world.isClient) return;
        if (controllerPos() != null && !pos.equals(controllerPos())) {
            if (world.isChunkLoaded(controllerPos()) && controller() == null) assignController(null);
            if (controllerPos() != null) { if (flux > 0) stop(); return; }
        }
        if (!small() && !formed() && Math.floorMod(world.getTime() + pos.asLong(), 20) == 0) SolarStructure.form(this);
        if (!small() && formed() && !SolarStructure.matches(this)) SolarStructure.dismantle(this);
        long previous = flux;
        long previousPanels = panels;
        Status previousStatus = status;
        range = small() ? LaserConfig.get().smallSolarBeamRange : LaserConfig.get().solarBeamRange;
        width = small() ? .65F : LaserConfig.get().solarBeamWidth;
        redstoneSignal = operationalStructure() && receivesRedstone();
        flux = 0;
        if (!operationalStructure()) { panels = 0; status = Status.INCOMPLETE; }
        else if (!LaserConfig.technicalMode()) status = Status.DISABLED;
        else if (!enabled) status = Status.OFF;
        else if (!redstoneMode.allows(redstoneSignal)) status = Status.REDSTONE;
        else if (!SolarExposure.hasSun(world)) { panels = 0; status = Status.NO_SUN; nextExposure = Long.MIN_VALUE; }
        else {
            if (world.getTime() >= nextExposure || world.getTime() < nextExposure - 10) {
                panels = small() ? (SolarExposure.seesSun(world, Vec3d.of(pos).add(.5, 1.01, .5), SolarExposure.sunDirection(world, 1)) ? 1 : 0)
                        : SolarExposure.measure(world, SolarStructure.origin(this)).visiblePanels();
                nextExposure = world.getTime() + 10;
            }
            // Ray probes are cached; the sun's altitude and output still advance every tick.
            flux = LuminousFlux.share(peakFlux(), small() ? panels == 0 ? 0 : SolarExposure.activity(SolarExposure.sunDirection(world, 1).y)
                    : SolarExposure.fraction(SolarExposure.sunDirection(world, 1), panels));
            status = flux > 0 ? Status.WORKING : Status.OBSTRUCTED;
        }
        powerTick = world.getTime();
        if (flux > 0 || previous > 0) LaserBeamNetwork.invalidate(world);
        if (panels != previousPanels || status != previousStatus || flux != previous && world.getTime() % 5 == 0) sync();
        if (flux > 0 && damageTick != world.getTime()) {
            damageTick = world.getTime();
            damageEntities();
        }
    }

    private boolean receivesRedstone() {
        if (world.isReceivingRedstonePower(pos)) return true;
        if (small()) return false;
        BlockPos origin = SolarStructure.origin(this);
        for (var cell : SolarStructure.PARTS) {
            BlockPos member = origin.add(cell.offset());
            if (world.isChunkLoaded(member) && world.isReceivingRedstonePower(member)) return true;
        }
        return false;
    }

    private void damageEntities() {
        if (!(world instanceof ServerWorld server) || LaserConfig.get().solarDamagePerTick <= 0) return;
        var hit = new HashSet<UUID>();
        double intensity = flux / (double) LaserConfig.get().solarPeakFlux;
        for (var ray : LaserBeamNetwork.path(this, 1).segments()) {
            double radius = LaserEmitterBlockEntity.BEAM_HIT_RADIUS * width * Math.sqrt(ray.power());
            float damage = (float) (LaserConfig.get().solarDamagePerTick * intensity * ray.power());
            if (damage <= 0) continue;
            var source = LaserDamage.source(server, ray.start());
            for (LivingEntity target : server.getEntitiesByClass(LivingEntity.class, new Box(ray.start(), ray.end()).expand(radius + .35),
                    entity -> entity.isAlive() && !entity.isSpectator())) {
                var bounds = target.getBoundingBox().expand(radius);
                if ((bounds.contains(ray.start()) || bounds.raycast(ray.start(), ray.end()).isPresent()) && hit.add(target.getUuid()))
                    LaserDamage.hit(target, source, ray.axis(), damage, intensity * ray.power());
            }
        }
    }

    public void stop() {
        flux = 0; panels = 0; powerTick = Long.MIN_VALUE; nextExposure = Long.MIN_VALUE; status = Status.INCOMPLETE;
        LaserBeamNetwork.invalidate(world);
        sync();
    }
    public void toggle() { enabled = !enabled; solarTick(); shareSettings(); sync(); }
    public void cycleRedstone() { redstoneMode = redstoneMode.next(); solarTick(); shareSettings(); sync(); }
    @Override public World beamWorld() { return world; }
    @Override public BlockPos beamPosition() { return pos; }
    @Override public Vec3d beamDirection() { return Vec3d.of(outputSide().getVector()); }
    @Override public Vec3d beamOrigin() {
        if (small()) return Vec3d.ofCenter(pos).add(beamDirection().multiply(.499));
        return Vec3d.of(SolarStructure.origin(this)).add(1.5, .5, 1.5).add(beamDirection().multiply(2.499));
    }
    @Override public BlockPos beamExitBlock() { return small() ? pos : BlockPos.ofFloored(beamOrigin()); }
    @Override public int beamRgb() { return BEAM_RGB; }
    @Override public int getBeamRange() { return range; }
    @Override public float getBeamWidthScale() { return width; }
    @Override public long getTicks() { return world == null ? 0 : world.getTime(); }
    @Override public boolean isBeamActive() {
        if (world == null || !operationalStructure() || !enabled || flux <= 0) return false;
        // Receivers may tick first. Retain the previous tick's route, never its spendable allocation.
        return world.isClient || (powerTick == world.getTime() || powerTick == world.getTime() - 1)
                && LaserConfig.technicalMode() && SolarExposure.hasSun(world)
                && (redstoneMode == LaserRedstoneMode.IGNORE || redstoneMode.allows(receivesRedstone()));
    }
    @Override public boolean isLightEmissionEnabled() { return true; }
    @Override public boolean showsScorchMarks() { return true; }
    @Override public long luminousFlux() { return flux; }
    @Override public long opticalBudget() { return world != null && !world.isClient && powerTick == world.getTime() && isBeamActive() ? flux : 0; }
    public int transferBudget() { return LuminousFlux.toEnergyRate(opticalBudget(), LaserConfig.get().lumensPerEnergyUnit, 1); }

    public void initializeOwner(PlayerEntity player) {
        if (player.isSpectator()) return;
        if (owner == null) owner = player.getUuid();
        if (owner.equals(player.getUuid())) { ownerName = player.getGameProfile().getName(); shareSettings(); sync(); }
    }
    public String ownerName() { return ownerName; }
    public boolean isPrivate() { return privateAccess; }
    public boolean allowsJoining(SolarConcentratorBlockEntity controller) {
        return !privateAccess || owner != null && owner.equals(controller.owner);
    }
    public boolean canManageSecurity(PlayerEntity player) { return player.getUuid().equals(owner) || player.hasPermissionLevel(2); }
    @Override public boolean canAccess(PlayerEntity player) {
        if (controllerPos() != null && !pos.equals(controllerPos())) return controller() != null && controller().canAccess(player);
        return !privateAccess || canManageSecurity(player);
    }
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null && player.getWorld() == world && !isRemoved() && world.getBlockEntity(pos) == this && operationalStructure()
                && player.isAlive() && !player.isSpectator() && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64 && canAccess(player);
    }
    public boolean togglePrivacy(PlayerEntity player) {
        if (!canPlayerUse(player) || !canManageSecurity(player)) return false;
        privateAccess = !privateAccess; shareSettings(); sync(); return true;
    }
    public void shareSettings() {
        if (small() || world == null || world.isClient || !pos.equals(controllerPos())) return;
        // Any resonator may become the controller after reassembly; ownership must not reset with tick order.
        for (var cell : SolarStructure.RESONATORS) {
            BlockPos member = SolarStructure.origin(this).add(cell.offset());
            if (!member.equals(pos) && world.isChunkLoaded(member) && world.getBlockEntity(member) instanceof SolarConcentratorBlockEntity port
                    && pos.equals(port.controllerPos())) {
                port.owner = owner; port.ownerName = ownerName; port.privateAccess = privateAccess;
                port.enabled = enabled; port.redstoneMode = redstoneMode; port.outputSide = outputSide(); port.sync();
            }
        }
    }
    @Override public Text getDisplayName() { return Text.translatable(small() ? "block.justifylasers.small_solar_concentrator" : "gui.justifylasers.solar.title"); }
    @Override public BlockPos screenPosition() { return pos; }
    @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) { buf.writeBlockPos(pos); }
    @Override public ScreenHandler createMenu(int id, PlayerInventory inventory, PlayerEntity player) {
        return canPlayerUse(player) ? new SolarConcentratorScreenHandler(id, inventory, this) : null;
    }

    @Override protected void writeLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        super.writeLaserNbt(nbt, inventory);
        nbt.putLong("SolarFlux", flux); nbt.putLong("SolarPanels", panels);
        nbt.putInt("SolarStatus", status.ordinal()); nbt.putInt("SolarRange", range); nbt.putFloat("SolarWidth", width);
        nbt.putBoolean("Enabled", enabled); nbt.putInt("RedstoneMode", redstoneMode.ordinal());
        nbt.putBoolean("PrivateAccess", privateAccess);
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putString("OwnerName", ownerName);
        nbt.putInt("SolarOutput", outputSide().getId());
    }
    @Override protected void readLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        super.readLaserNbt(nbt, inventory);
        flux = LuminousFlux.clamp(nbt.getLong("SolarFlux"));
        panels = nbt.getLong("SolarPanels") & ((1L << SolarExposure.PANELS.size()) - 1);
        range = MathHelper.clamp(nbt.getInt("SolarRange"), 1, 512);
        width = nbt.contains("SolarWidth") ? MathHelper.clamp(nbt.getFloat("SolarWidth"), .1F, 10) : 3;
        if (!Float.isFinite(width)) width = 3;
        status = Status.values()[MathHelper.clamp(nbt.getInt("SolarStatus"), 0, Status.values().length - 1)];
        enabled = !nbt.contains("Enabled") || nbt.getBoolean("Enabled");
        redstoneMode = LaserRedstoneMode.byIndex(nbt.getInt("RedstoneMode"));
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        ownerName = nbt.getString("OwnerName");
        if (ownerName.length() > 16) ownerName = ownerName.substring(0, 16);
        privateAccess = owner != null && nbt.getBoolean("PrivateAccess");
        outputSide = nbt.contains("SolarOutput") ? Direction.byId(nbt.getInt("SolarOutput")) : null;
        if (outputSide != null && !outputSide.getAxis().isHorizontal()) outputSide = null;
        powerTick = Long.MIN_VALUE; damageTick = Long.MIN_VALUE; nextExposure = Long.MIN_VALUE;
        LaserBeamNetwork.invalidate(world);
    }
}
