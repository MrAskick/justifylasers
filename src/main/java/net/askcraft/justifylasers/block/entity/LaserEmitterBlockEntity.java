package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserEnergyBuffer;
import net.askcraft.justifylasers.energy.LaserEnergyHost;
import net.askcraft.justifylasers.energy.LaserEnergyCost;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.item.LaserModuleItem;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.BeamBehavior;
import net.askcraft.justifylasers.laser.LaserBeamEffects;
import net.askcraft.justifylasers.laser.LaserEntityMode;
import net.askcraft.justifylasers.laser.LaserLootCollector;
import net.askcraft.justifylasers.laser.LaserBeamPath;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserDamage;
import net.askcraft.justifylasers.laser.LaserMining;
import net.askcraft.justifylasers.laser.LaserRedstoneMode;
import net.askcraft.justifylasers.laser.LaserTargetFilter;
import net.askcraft.justifylasers.laser.LuminousFlux;
import net.askcraft.justifylasers.platform.InventoryNbt;
import net.askcraft.justifylasers.platform.LaserBlockEntity;
import net.askcraft.justifylasers.platform.LaserScreenFactory;
import net.askcraft.justifylasers.platform.PlatformEnergyStorage;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
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
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class LaserEmitterBlockEntity extends LaserBlockEntity implements LaserScreenFactory, net.askcraft.justifylasers.platform.AutomatedInventory, LaserEnergyHost, LaserLootCollector,
        net.askcraft.justifylasers.laser.LaserBeamSource {
    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 512;
    public static final int DEFAULT_RANGE = 64;
    public static final double BEAM_HIT_RADIUS = 0.11D;
    public static final int BEAM_WIDTH_STEPS = 200;
    public static final int DEFAULT_BEAM_WIDTH_STEP = BEAM_WIDTH_STEPS / 2;
    public static final float MIN_BEAM_WIDTH_SCALE = 0.1F;
    public static final float MAX_BEAM_WIDTH_SCALE = 10.0F;
    public static final int PROPERTY_COUNT = 61;
    public static final int MODULE_SLOT_COUNT = 10;
    public static final int STORAGE_START = MODULE_SLOT_COUNT;
    public static final int STORAGE_SIZE = 9;
    public static final int COLLECTION_SLOT = MODULE_SLOT_COUNT + STORAGE_SIZE;
    public static final int AMPLIFIER_SLOT = COLLECTION_SLOT + 1;
    public static final int INVENTORY_SIZE = AMPLIFIER_SLOT + 1;
    private final LaserTargetFilter targetFilter = new LaserTargetFilter();

    private final DefaultedList<ItemStack> modules = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final LaserEnergyBuffer energy = new LaserEnergyBuffer(this::energyCapacity,
            () -> Math.max(LaserConfig.get().maxInput, amplifierTier() == 0 ? 0 : energyCost()), this::markDirty);
    private final PlatformEnergyStorage energyPort = new PlatformEnergyStorage(this);
    private long lastEnergyTick = Long.MIN_VALUE;
    private int paidEnergy;
    private int syncedBeamRange = MIN_RANGE;
    private int syncedBeamWidthStep;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private boolean privateAccess;

    private boolean enabled = true;
    private LaserRedstoneMode redstoneMode = LaserRedstoneMode.IGNORE;
    private LaserColor color = LaserColor.RED;
    private boolean breakBlocks;
    private boolean damageEntities;
    private boolean igniteEntities;
    private int miningSpeedStep = LaserMining.DEFAULT_SPEED_STEP;
    private boolean silkTouch;
    private boolean dropBlocks = true;
    private boolean scorchMarks = true;
    private boolean collectDrops;
    private int creativeFluxStep = net.askcraft.justifylasers.laser.CreativeFlux.DEFAULT_STEP;
    private LaserEntityMode previousEntityMode = LaserEntityMode.NONE;
    private int damageStep = LaserDamage.DEFAULT_DAMAGE_STEP;
    private int knockbackStep = LaserDamage.DEFAULT_KNOCKBACK_STEP;
    private int hitsPerSecond = LaserDamage.MAX_HITS_PER_SECOND;
    private int beamWidthStep = DEFAULT_BEAM_WIDTH_STEP;
    private int beamRange = DEFAULT_RANGE;
    private boolean lightEmission = true;
    private boolean minecraftLighting = true;
    private boolean active;

    private long ticks;
    private final LaserBeamEffects effects = new LaserBeamEffects();

    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> enabled ? 1 : 0;
                case 1 -> redstoneMode.ordinal();
                case 2 -> color.ordinal();
                case 3 -> breaksBlocks() ? 1 : 0;
                case 4 -> affectsEntities() ? 1 : 0;
                case 5 -> active ? 1 : 0;
                case 6 -> getBeamWidthStep();
                case 7 -> lightEmission ? 1 : 0;
                case 8 -> minecraftLighting ? 1 : 0;
                case 9 -> damageStep;
                case 10 -> knockbackStep;
                case 11 -> hitsPerSecond;
                case 12 -> ignitesEntities() ? 1 : 0;
                case 13 -> getBeamRange();
                case 14 -> miningSpeedStep;
                case 15 -> hasSilkTouch() ? 1 : 0;
                case 16 -> dropsBlocks() ? 1 : 0;
                case 17 -> showsScorchMarks() ? 1 : 0;
                case 18 -> isPoweredEmitter() ? 1 : 0;
                case 19 -> LaserConfig.technicalMode() ? 1 : 0;
                case 20 -> energy.stored() & 0xFFFF;
                case 21 -> energy.stored() >>> 16;
                case 22 -> energy.capacity() & 0xFFFF;
                case 23 -> energy.capacity() >>> 16;
                case 24 -> energyCost() & 0xFFFF;
                case 25 -> energyCost() >>> 16;
                case 26 -> moduleMask();
                case 27 -> opticalElement() ? 1 : 0;
                case 59 -> beamRgb() & 0xFFFF;
                case 60 -> beamRgb() >>> 16;
                case 28 -> privateAccess ? 1 : 0;
                case 29 -> world != null && world.isReceivingRedstonePower(pos) ? 1 : 0;
                case 46 -> targetFilter.flags();
                case 47 -> targetFilter.exclusions().size();
                case 48, 49, 50, 51 -> (int) (luminousFlux() >>> ((index - 48) * 16)) & 0xFFFF;
                case 52 -> entityMode().ordinal();
                case 53 -> collectsDrops() ? 1 : 0;
                case 54 -> creativeFluxStep;
                case 55, 56, 57, 58 -> (int) (moduleFluxCost() >>> ((index - 55) * 16)) & 0xFFFF;
                default -> index >= 30 && index < 46 && index - 30 < ownerName.length()
                        ? ownerName.charAt(index - 30) : 0;
            };
        }

        @Override
        public void set(int index, int value) {
            LaserBeamNetwork.invalidate(world);
            switch (index) {
                case 0 -> enabled = value != 0;
                case 1 -> redstoneMode = LaserRedstoneMode.byIndex(value);
                case 2 -> color = LaserColor.byIndex(value);
                case 3 -> breakBlocks = value != 0;
                case 4 -> damageEntities = value != 0;
                case 5 -> active = value != 0;
                case 6 -> beamWidthStep = clampBeamWidthStep(value);
                case 7 -> lightEmission = value != 0;
                case 8 -> minecraftLighting = value != 0;
                case 9 -> damageStep = LaserDamage.clampDamageStep(value);
                case 10 -> knockbackStep = LaserDamage.clampKnockbackStep(value);
                case 11 -> hitsPerSecond = LaserDamage.clampHitsPerSecond(value);
                case 12 -> igniteEntities = value != 0;
                case 13 -> beamRange = clampBeamRange(value);
                case 14 -> miningSpeedStep = LaserMining.clampSpeedStep(value);
                case 15 -> silkTouch = value != 0;
                case 16 -> dropBlocks = value != 0;
                case 17 -> scorchMarks = value != 0;
                case 53 -> collectDrops = value != 0;
                case 54 -> creativeFluxStep = net.askcraft.justifylasers.laser.CreativeFlux.clamp(value);
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    };

    public LaserEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_EMITTER, pos, state);
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        LaserBeamNetwork.registerEmitter(world, pos);
    }

    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        if (world != null) {
            LaserBeamNetwork.registerEmitter(world, pos);
        }
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, LaserEmitterBlockEntity emitter) {
        emitter.ticks++;
        emitter.payForTick();
        emitter.refreshActiveState();
        LaserBeamNetwork.beginEmitterTick(world, pos);

        if (!emitter.active) {
            emitter.clearHeating();
            return;
        }

        LaserBeamPath path = LaserBeamNetwork.path(emitter, 1.0F);

        emitter.effects.tick(emitter, path);
        for (LaserBeamTrace trace : path.segments()) {
            if (!trace.hasBlockHit()) continue;
            emitter.spawnImpactEffects(trace);
        }

    }

    public static void clientTick(World world, BlockPos pos, BlockState state, LaserEmitterBlockEntity emitter) {
        emitter.ticks++;
    }

    private void refreshActiveState() {
        if (world == null || world.isClient) {
            return;
        }

        boolean powered = world.isReceivingRedstonePower(pos);
        boolean shouldBeActive = enabled && redstoneMode.allows(powered) && (!isPoweredEmitter()
                || LaserConfig.technicalMode() && opticalElement() && lastEnergyTick == world.getTime() && paidEnergy >= energyCost());
        boolean activeChanged = active != shouldBeActive;
        active = shouldBeActive;
        boolean shouldEmitLight = shouldBeActive && lightEmission;
        boolean shouldLightWorld = shouldBeActive && minecraftLighting;

        BlockState state = getCachedState();
        BlockState updatedState = state;
        if (state.contains(LaserEmitterBlock.COLOR) && state.get(LaserEmitterBlock.COLOR) != color) {
            updatedState = updatedState.with(LaserEmitterBlock.COLOR, color);
        }
        if (state.contains(LaserEmitterBlock.LIT) && state.get(LaserEmitterBlock.LIT) != shouldBeActive) {
            updatedState = updatedState.with(LaserEmitterBlock.LIT, shouldBeActive);
        }
        if (state.contains(LaserEmitterBlock.EMITTING_LIGHT)
                && state.get(LaserEmitterBlock.EMITTING_LIGHT) != shouldEmitLight) {
            updatedState = updatedState.with(LaserEmitterBlock.EMITTING_LIGHT, shouldEmitLight);
        }
        if (state.contains(LaserEmitterBlock.MINECRAFT_LIGHTING)
                && state.get(LaserEmitterBlock.MINECRAFT_LIGHTING) != shouldLightWorld) {
            updatedState = updatedState.with(LaserEmitterBlock.MINECRAFT_LIGHTING, shouldLightWorld);
        }

        if (updatedState != state) {
            world.setBlockState(pos, updatedState, Block.NOTIFY_LISTENERS);
            LaserBeamNetwork.invalidate(world);
        }

        if (activeChanged) {
            world.updateComparators(pos, state.getBlock());
        }
    }

    private void spawnImpactEffects(LaserBeamTrace trace) {
        if (!(world instanceof ServerWorld serverWorld) || ticks % 2L != 0L) {
            return;
        }

        Vec3d axis = trace.axis();
        Vec3d impact = trace.end().subtract(axis.multiply(0.018D));
        serverWorld.spawnParticles(
                new DustParticleEffect(new org.joml.Vector3f((trace.rgb() >> 16 & 255) / 255.0F,
                        (trace.rgb() >> 8 & 255) / 255.0F, (trace.rgb() & 255) / 255.0F), 1.25F),
                impact.x, impact.y, impact.z,
                3,
                0.035D, 0.035D, 0.035D,
                0.004D
        );
        serverWorld.spawnParticles(
                ParticleTypes.ELECTRIC_SPARK,
                impact.x, impact.y, impact.z,
                1,
                0.055D, 0.055D, 0.055D,
                0.025D
        );

        if (breaksBlocks() && ticks % 4L == 0L) {
            serverWorld.spawnParticles(
                    ParticleTypes.SMOKE,
                    impact.x, impact.y, impact.z,
                    1,
                    0.025D, 0.025D, 0.025D,
                    0.008D
            );
        }
    }

    private void clearHeating() {
        effects.clear(this);
    }

    public void handleButton(int buttonId) {
        if (!allowsSetting(buttonId)) return;
        if (buttonId >= LaserEmitterScreenHandler.FILTER_BUTTON_BASE && buttonId < LaserEmitterScreenHandler.FILTER_BUTTON_BASE + 4) {
            targetFilter.setFlags(targetFilter.flags() ^ (1 << (buttonId - LaserEmitterScreenHandler.FILTER_BUTTON_BASE)));
            sync();
            return;
        }
        if (buttonId >= LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_BASE
                && buttonId <= LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_MAX) {
            beamWidthStep = clampBeamWidthStep(buttonId - LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_BASE);
        } else if (buttonId >= LaserEmitterScreenHandler.DAMAGE_BUTTON_MIN
                && buttonId <= LaserEmitterScreenHandler.DAMAGE_BUTTON_MAX) {
            damageStep = buttonId - LaserEmitterScreenHandler.DAMAGE_BUTTON_BASE;
        } else if (buttonId >= LaserEmitterScreenHandler.KNOCKBACK_BUTTON_BASE
                && buttonId <= LaserEmitterScreenHandler.KNOCKBACK_BUTTON_MAX) {
            knockbackStep = buttonId - LaserEmitterScreenHandler.KNOCKBACK_BUTTON_BASE;
        } else if (buttonId >= LaserEmitterScreenHandler.HIT_RATE_BUTTON_MIN
                && buttonId <= LaserEmitterScreenHandler.HIT_RATE_BUTTON_MAX) {
            hitsPerSecond = buttonId - LaserEmitterScreenHandler.HIT_RATE_BUTTON_BASE;
        } else if (buttonId >= LaserEmitterScreenHandler.RANGE_BUTTON_MIN
                && buttonId <= LaserEmitterScreenHandler.RANGE_BUTTON_MAX) {
            beamRange = buttonId - LaserEmitterScreenHandler.RANGE_BUTTON_BASE;
        } else if (buttonId >= LaserEmitterScreenHandler.FLUX_BUTTON_BASE && buttonId <= LaserEmitterScreenHandler.FLUX_BUTTON_MAX) {
            creativeFluxStep = buttonId - LaserEmitterScreenHandler.FLUX_BUTTON_BASE;
        } else if (buttonId >= LaserEmitterScreenHandler.MINING_SPEED_BUTTON_BASE
                && buttonId <= LaserEmitterScreenHandler.MINING_SPEED_BUTTON_MAX) {
            miningSpeedStep = buttonId - LaserEmitterScreenHandler.MINING_SPEED_BUTTON_BASE;
        } else {
            switch (buttonId) {
                case LaserEmitterScreenHandler.BUTTON_ENABLED -> enabled = !enabled;
                case LaserEmitterScreenHandler.BUTTON_REDSTONE -> redstoneMode = redstoneMode.next();
                case LaserEmitterScreenHandler.BUTTON_COLOR -> color = color.next();
                case LaserEmitterScreenHandler.BUTTON_BREAK_BLOCKS -> breakBlocks = !breakBlocks;
                case LaserEmitterScreenHandler.BUTTON_DAMAGE_ENTITIES -> damageEntities = !damageEntities;
                case LaserEmitterScreenHandler.BUTTON_LIGHT_EMISSION -> lightEmission = !lightEmission;
                case LaserEmitterScreenHandler.BUTTON_MINECRAFT_LIGHTING -> minecraftLighting = !minecraftLighting;
                case LaserEmitterScreenHandler.BUTTON_IGNITE_ENTITIES -> igniteEntities = !igniteEntities;
                case LaserEmitterScreenHandler.BUTTON_SILK_TOUCH -> silkTouch = !silkTouch;
                case LaserEmitterScreenHandler.BUTTON_DROP_BLOCKS -> dropBlocks = !dropBlocks;
                case LaserEmitterScreenHandler.BUTTON_SCORCH_MARKS -> scorchMarks = !scorchMarks;
                case LaserEmitterScreenHandler.BUTTON_COLLECT -> collectDrops = !collectDrops;
                case LaserEmitterScreenHandler.BUTTON_FILTER_MODE -> targetFilter.toggleMode();
                case LaserEmitterScreenHandler.BUTTON_RESET_MINING_SETTINGS -> {
                    miningSpeedStep = LaserMining.DEFAULT_SPEED_STEP;
                    silkTouch = false;
                    dropBlocks = true;
                    scorchMarks = true;
                }
                case LaserEmitterScreenHandler.BUTTON_RESET_DAMAGE_SETTINGS -> {
                    damageStep = LaserDamage.DEFAULT_DAMAGE_STEP;
                    knockbackStep = LaserDamage.DEFAULT_KNOCKBACK_STEP;
                    hitsPerSecond = LaserDamage.MAX_HITS_PER_SECOND;
                    igniteEntities = false;
                }
                default -> {
                    return;
                }
            }
            refreshActiveState();
        }
        sync();
    }

    private void sync() {
        syncedBeamRange = getBeamRange();
        syncedBeamWidthStep = getBeamWidthStep();
        LaserBeamNetwork.invalidate(world);
        super.markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public void markDirty() {
        // Hoppers merge stacks in place, without calling setStack.
        if (world != null && !world.isClient && isPoweredEmitter()
                && (syncedBeamRange != getBeamRange() || syncedBeamWidthStep != getBeamWidthStep())) {
            refreshActiveState();
            sync();
        } else {
            super.markDirty();
        }
    }

    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }

    public boolean isBeamActive() {
        BlockState state = getCachedState();
        return state.contains(LaserEmitterBlock.LIT) && state.get(LaserEmitterBlock.LIT);
    }

    public LaserColor getColor() {
        return color;
    }

    @Override public World beamWorld() { return world; }
    @Override public BlockPos beamPosition() { return pos; }
    @Override public int beamRgb() { return spectral() ? net.askcraft.justifylasers.laser.LaserSpectrum.color(modules.get(0)) : color.rgb(); }
    @Override public Vec3d beamDirection() { return Vec3d.of(getCachedState().get(LaserEmitterBlock.FACING).getVector()); }
    @Override public Vec3d beamOrigin() { return Vec3d.ofCenter(pos).add(beamDirection().multiply(LaserEmitterBlock.BEAM_ORIGIN_OFFSET)); }

    @Override public BeamBehavior beamBehavior() {
        return new BeamBehavior(new BeamBehavior.EntityEffect(entityMode(), affectsEntities(), damageStep, knockbackStep, hitsPerSecond, ignitesEntities()),
                new BeamBehavior.MiningEffect(breaksBlocks(), miningSpeedStep, hasSilkTouch(), dropsBlocks(), collectsDrops(),
                        isPoweredEmitter() && hasModule(LaserModule.IGNITION)).at(pos),
                hasModule(LaserModule.TARGET_FILTER) ? targetFilter.copy() : null, owner, 1, showsScorchMarks(), pos).withSpectrum(spectral());
    }

    public float getBeamWidthScale() {
        return beamWidthScale(getBeamWidthStep());
    }

    public int getBeamWidthStep() {
        if (!isPoweredEmitter()) return beamWidthStep;
        ItemStack stack = modules.get(LaserModule.THICKNESS.slot());
        return stack.getItem() instanceof LaserModuleItem item && item.module() == LaserModule.THICKNESS
                ? Math.min(BEAM_WIDTH_STEPS, Math.round(BEAM_WIDTH_STEPS * stack.getCount() * item.upgradeUnits() / 64.0F)) : 0;
    }

    public int getBeamRange() {
        if (!isPoweredEmitter()) return beamRange;
        ItemStack stack = modules.get(LaserModule.RANGE.slot());
        return stack.getItem() instanceof LaserModuleItem item && item.module() == LaserModule.RANGE
                ? clampBeamRange(MIN_RANGE + stack.getCount() * item.rangePerItem()) : MIN_RANGE;
    }

    public static int clampBeamRange(int range) {
        return MathHelper.clamp(range, MIN_RANGE, MAX_RANGE);
    }

    public boolean isLightEmissionEnabled() {
        return lightEmission;
    }

    public boolean showsScorchMarks() {
        return scorchMarks;
    }

    public boolean isPoweredEmitter() {
        return getCachedState().isOf(ModBlocks.POWERED_LASER_EMITTER);
    }

    public boolean acceptsEnergy() {
        return isPoweredEmitter() && LaserConfig.technicalMode() && world != null && !world.isClient && !isRemoved();
    }

    public LaserEnergyBuffer energy() {
        return energy;
    }

    public PlatformEnergyStorage energyPort() {
        return energyPort;
    }

    public int energyCost() {
        if (!isPoweredEmitter()) return 0;
        return LaserEnergyCost.perTick(LaserConfig.get().rates(), breaksBlocks(), miningSpeedStep, affectsEntities(),
                entityMode() == LaserEntityMode.HEAL ? .1 : LaserDamage.damageForStep(damageStep),
                entityMode() == LaserEntityMode.HEAL ? 1 : entityMode().movesEntities() ? 20 : hitsPerSecond,
                damagesEntities() ? LaserDamage.knockbackForStep(knockbackStep) : 0, ignitesEntities(), opticalModuleCost() + actionModuleCost() + amplifierEnergy());
    }

    private int amplifierTier() { return net.askcraft.justifylasers.item.LaserAmplifierItem.tier(modules.get(AMPLIFIER_SLOT)); }
    private long amplifierEnergy() { return net.askcraft.justifylasers.energy.AmplifierTier.energy(amplifierTier(), LaserConfig.get().lumensPerEnergyUnit); }
    private int energyCapacity() {
        return Math.max(LaserConfig.get().capacity, amplifierTier() == 0 ? 0 : (int)Math.min(Integer.MAX_VALUE, 2L * energyCost()));
    }

    private long emittedFlux() {
        long flux = LuminousFlux.fromEnergyRate((int)Math.min(Integer.MAX_VALUE, LaserConfig.get().basePerTick + opticalModuleCost()), LaserConfig.get().lumensPerEnergyUnit)
                + net.askcraft.justifylasers.energy.AmplifierTier.lumens(amplifierTier());
        // A custom conversion rate must not let an amplifier exceed the FE actually paid.
        return Math.min(LuminousFlux.clamp(flux), LuminousFlux.fromEnergyRate(opticalEnergyRate(), LaserConfig.get().lumensPerEnergyUnit));
    }

    @Override public long moduleFluxCost() {
        if (!isPoweredEmitter()) return 0;
        long cost = 0;
        for (LaserModule module : LaserModule.values()) {
            if (!hasModule(module) || module == LaserModule.RANGE || module == LaserModule.THICKNESS
                    || module == LaserModule.SCORCH_MARKS) continue;
            if (module == LaserModule.BLOCK_DESTRUCTION && !breaksBlocks() || module.isEntityMode() && !affectsEntities()) continue;
            cost += net.askcraft.justifylasers.laser.BeamModuleCost.lumens(module, damageStep, knockbackStep, hitsPerSecond,
                    miningSpeedStep, hasModule(LaserModule.IGNITION), 0, 1);
        }
        // Ignition's effect price is already included in Damage/Mining; its installed item costs one unit.
        if (hasModule(LaserModule.IGNITION) && (damagesEntities() || breaksBlocks())) {
            cost -= net.askcraft.justifylasers.laser.BeamModuleCost.lumens(LaserModule.IGNITION, 0, 0, hitsPerSecond, 0, true, 0, 1);
            cost += 2L * LaserConfig.get().lumensPerEnergyUnit;
        }
        return LuminousFlux.clamp(cost);
    }

    private long actionModuleCost() {
        long cost = breaksBlocks() && hasModule(LaserModule.IGNITION) ? (long) Math.ceil(LaserConfig.get().rates().ignitionPerHit()) : 0;
        for (LaserModule module : LaserModule.values()) {
            if (module != LaserModule.RANGE && module != LaserModule.THICKNESS && module != LaserModule.SCORCH_MARKS && hasModule(module)) cost++;
        }
        return cost;
    }

    private long opticalModuleCost() {
        ItemStack width = modules.get(LaserModule.THICKNESS.slot());
        long moduleCost = width.getItem() instanceof LaserModuleItem item && item.module() == LaserModule.THICKNESS
                ? (long) width.getCount() * item.upgradeUnits() : 0;
        ItemStack range = modules.get(LaserModule.RANGE.slot());
        if (range.getItem() instanceof LaserModuleItem item && item.module() == LaserModule.RANGE) {
            // Charge installed upgrades, including the last tier-II module at the range cap.
            moduleCost += (long) range.getCount() * item.rangePerItem();
        }
        return moduleCost;
    }

    public int opticalEnergyRate() {
        return isPoweredEmitter() ? (int) Math.min(Integer.MAX_VALUE, LaserConfig.get().basePerTick + opticalModuleCost() + amplifierEnergy()) : 0;
    }

    @Override public long luminousFlux() {
        if (!isBeamActive()) return 0;
        return isPoweredEmitter() ? emittedFlux()
                : net.askcraft.justifylasers.laser.CreativeFlux.lumens(creativeFluxStep);
    }

    @Override public long opticalBudget() {
        return isPoweredEmitter() ? Math.min(emittedFlux(), LuminousFlux.fromEnergyRate(transferBudget(), LaserConfig.get().lumensPerEnergyUnit)) : luminousFlux();
    }

    public int transferBudget() {
        return world != null && !world.isClient && isPoweredEmitter() && isBeamActive()
                && lastEnergyTick == world.getTime() ? Math.min(paidEnergy, opticalEnergyRate()) : 0;
    }

    public String status() {
        if (isPoweredEmitter() && !LaserConfig.technicalMode()) return "disabled";
        if (!enabled) return "off";
        if (isPoweredEmitter() && !opticalElement()) return "no_crystal";
        if (world != null && !redstoneMode.allows(world.isReceivingRedstonePower(pos))) return "redstone";
        return isBeamActive() ? "active" : energy.stored() < energyCost() ? "no_power" : "ready";
    }

    private void payForTick() {
        if (!isPoweredEmitter() || world == null || world.isClient || lastEnergyTick == world.getTime()) return;
        lastEnergyTick = world.getTime();
        paidEnergy = 0;
        LaserCrystalItem crystal = crystal();
        if (crystal != null) color = crystal.color();
        else if (spectral()) color = LaserColor.nearest(beamRgb());
        if (enabled && LaserConfig.technicalMode() && opticalElement() && redstoneMode.allows(world.isReceivingRedstonePower(pos))) {
            int cost = energyCost();
            if (energy.consume(cost)) paidEnergy = cost;
        }
    }

    @Nullable
    public LaserCrystalItem crystal() {
        return modules.get(0).getItem() instanceof LaserCrystalItem crystal ? crystal : null;
    }

    public boolean hasModule(LaserModule module) {
        return !isPoweredEmitter() ? module != LaserModule.SPECTRUM : modules.get(module.slot()).getItem() instanceof LaserModuleItem item && item.module() == module;
    }
    public boolean spectral() { return isPoweredEmitter() && hasModule(LaserModule.SPECTRUM); }
    private boolean opticalElement() { return crystal() != null || spectral(); }
    public boolean setSpectrum(PlayerEntity player, String value) {
        int rgb = net.askcraft.justifylasers.laser.LaserSpectrum.parse(value);
        if (!canPlayerUse(player) || !player.isAlive() || player.isSpectator() || !spectral() || rgb < 0) return false;
        net.askcraft.justifylasers.laser.LaserSpectrum.color(modules.get(0), rgb);
        color = LaserColor.nearest(rgb); inventoryChanged(); return true;
    }

    private int moduleMask() {
        int mask = 0;
        for (LaserModule module : LaserModule.values()) {
            if (hasModule(module)) mask |= 1 << module.ordinal();
        }
        return mask;
    }

    public boolean hasSilkTouch() {
        return isPoweredEmitter() ? hasModule(LaserModule.SILK_TOUCH) : silkTouch;
    }

    public boolean dropsBlocks() {
        return isPoweredEmitter() ? hasModule(LaserModule.BLOCK_DROPS) : dropBlocks;
    }

    public boolean ignitesEntities() {
        return isPoweredEmitter() ? hasModule(LaserModule.IGNITION) && entityMode() == LaserEntityMode.DAMAGE : igniteEntities;
    }

    public boolean breaksBlocks() {
        return breakBlocks && hasModule(LaserModule.BLOCK_DESTRUCTION);
    }

    public boolean damagesEntities() {
        return affectsEntities() && entityMode() == LaserEntityMode.DAMAGE;
    }

    public LaserEntityMode entityMode() {
        if (!isPoweredEmitter()) return LaserEntityMode.DAMAGE;
        return modules.get(LaserModule.ENTITY_DAMAGE.slot()).getItem() instanceof LaserModuleItem item
                ? LaserEntityMode.of(item.module()) : LaserEntityMode.NONE;
    }

    public boolean affectsEntities() { return damageEntities && entityMode() != LaserEntityMode.NONE && entityMode() != LaserEntityMode.MINING; }
    public boolean collectsDrops() { return isPoweredEmitter() ? hasModule(LaserModule.BLOCK_COLLECTION) : collectDrops; }

    public boolean allowsSetting(int id) {
        if (!isPoweredEmitter()) return true;
        if (id >= LaserEmitterScreenHandler.FILTER_BUTTON_BASE && id <= LaserEmitterScreenHandler.BUTTON_FILTER_TYPE) {
            return hasModule(LaserModule.TARGET_FILTER);
        }
        if (id >= LaserEmitterScreenHandler.FLUX_BUTTON_BASE && id <= LaserEmitterScreenHandler.FLUX_BUTTON_MAX) return false;
        if (id >= LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_BASE && id <= LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_MAX
                || id >= LaserEmitterScreenHandler.RANGE_BUTTON_MIN && id <= LaserEmitterScreenHandler.RANGE_BUTTON_MAX) return false;
        if (id >= LaserEmitterScreenHandler.DAMAGE_BUTTON_MIN && id <= LaserEmitterScreenHandler.DAMAGE_BUTTON_MAX
                || id >= LaserEmitterScreenHandler.KNOCKBACK_BUTTON_BASE && id <= LaserEmitterScreenHandler.KNOCKBACK_BUTTON_MAX
                || id >= LaserEmitterScreenHandler.HIT_RATE_BUTTON_MIN && id <= LaserEmitterScreenHandler.HIT_RATE_BUTTON_MAX) {
            return entityMode() != LaserEntityMode.NONE && entityMode() != LaserEntityMode.HEAL;
        }
        if (id >= LaserEmitterScreenHandler.MINING_SPEED_BUTTON_BASE && id <= LaserEmitterScreenHandler.MINING_SPEED_BUTTON_MAX) {
            return hasModule(LaserModule.BLOCK_DESTRUCTION);
        }
        return switch (id) {
            case LaserEmitterScreenHandler.BUTTON_COLOR -> false;
            case LaserEmitterScreenHandler.BUTTON_BREAK_BLOCKS, LaserEmitterScreenHandler.BUTTON_RESET_MINING_SETTINGS -> hasModule(LaserModule.BLOCK_DESTRUCTION);
            case LaserEmitterScreenHandler.BUTTON_DAMAGE_ENTITIES, LaserEmitterScreenHandler.BUTTON_RESET_DAMAGE_SETTINGS -> entityMode() != LaserEntityMode.NONE;
            case LaserEmitterScreenHandler.BUTTON_SILK_TOUCH, LaserEmitterScreenHandler.BUTTON_DROP_BLOCKS,
                    LaserEmitterScreenHandler.BUTTON_SCORCH_MARKS -> false;
            case LaserEmitterScreenHandler.BUTTON_COLLECT, LaserEmitterScreenHandler.BUTTON_IGNITE_ENTITIES -> false;
            default -> true;
        };
    }

    @Override
    public int size() {
        return isPoweredEmitter() ? INVENTORY_SIZE : 0;
    }

    @Override
    public boolean isEmpty() {
        return modules.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
        return modules.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(modules, slot, amount);
        if (!removed.isEmpty()) inventoryChanged();
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack removed = Inventories.removeStack(modules, slot);
        if (!removed.isEmpty()) inventoryChanged();
        return removed;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (!stack.isEmpty() && (!isValid(slot, stack) || stack.getCount() > stack.getMaxCount())) {
            throw new IllegalArgumentException("Invalid crystal/module stack for slot " + slot);
        }
        modules.set(slot, stack);
        inventoryChanged();
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (!isPoweredEmitter() || slot < 0 || slot >= INVENTORY_SIZE) return false;
        if (slot >= STORAGE_START && slot < STORAGE_START + STORAGE_SIZE) return true;
        if (slot == AMPLIFIER_SLOT) return net.askcraft.justifylasers.item.LaserAmplifierItem.tier(stack) > 0;
        if (slot == LaserModule.SCORCH_MARKS.slot() || slot == 5) return false;
        return slot == 0 ? stack.getItem() instanceof LaserCrystalItem || stack.getItem() instanceof LaserModuleItem item && item.module() == LaserModule.SPECTRUM
                : stack.getItem() instanceof LaserModuleItem item && item.module().slot() == slot;
    }

    @Override
    public int getMaxCountPerStack() {
        return 64;
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return isPoweredEmitter() && !privateAccess ? java.util.stream.IntStream.range(0, INVENTORY_SIZE).toArray() : new int[0];
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction side) {
        return !privateAccess && isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction side) {
        return !privateAccess && isPoweredEmitter();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null && player.getWorld() == world && !isRemoved() && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64 && canAccess(player);
    }

    public boolean canAccess(PlayerEntity player) {
        return !isPoweredEmitter() || !privateAccess || canManageSecurity(player);
    }

    public boolean canManageSecurity(PlayerEntity player) {
        return isPoweredEmitter() && (player.getUuid().equals(owner) || player.hasPermissionLevel(2));
    }

    public void initializeOwner(PlayerEntity player) {
        if (player.isSpectator()) return;
        if (owner == null) owner = player.getUuid();
        if (owner.equals(player.getUuid())) {
            ownerName = player.getGameProfile().getName();
            sync();
        }
    }

    public String ownerName() {
        return ownerName;
    }

    public LaserTargetFilter targetFilter() { return targetFilter; }

    public boolean toggleEntityType(PlayerEntity player, String id) {
        if (!canPlayerUse(player) || !player.isAlive() || player.isSpectator() || !hasModule(LaserModule.TARGET_FILTER)) return false;
        boolean changed = targetFilter.toggleType(id);
        if (changed) sync();
        return changed;
    }

    @Override public ItemStack collect(ItemStack stack) {
        if (!isPoweredEmitter()) return stack;
        ItemStack remaining = net.askcraft.justifylasers.laser.LaserStorage.insert(modules, STORAGE_START, STORAGE_START + STORAGE_SIZE, stack);
        if (remaining.getCount() != stack.getCount()) sync();
        return remaining;
    }

    private static final int[][] COPIED_SETTINGS = {
            {0, 0}, {1, 1}, {2, 2}, {3, 3}, {4, 4}, {6, 1000}, {7, 5}, {8, 6},
            {9, 2001}, {10, 3000}, {11, 4001}, {12, 7}, {13, 5001}, {14, 6000}, {15, 9}, {16, 10}, {17, 11}
    };

    public NbtCompound copySettings() {
        NbtCompound settings = new NbtCompound();
        settings.putInt("Version", 1);
        for (int[] setting : COPIED_SETTINGS) settings.putInt("Setting" + setting[0], propertyDelegate.get(setting[0]));
        settings.put("TargetFilter", targetFilter.write());
        return settings;
    }

    public boolean pasteSettings(PlayerEntity player, NbtCompound settings) {
        if (!canPlayerUse(player) || !player.canModifyBlocks() || settings.getInt("Version") != 1) return false;
        // Whitelist controls explicitly. Inventory, energy and ownership never leave the source block.
        for (int[] setting : COPIED_SETTINGS) {
            String key = "Setting" + setting[0];
            if (settings.contains(key) && allowsSetting(setting[1])) propertyDelegate.set(setting[0], settings.getInt(key));
        }
        if (hasModule(LaserModule.TARGET_FILTER)) targetFilter.read(settings.getCompound("TargetFilter"));
        refreshActiveState();
        sync();
        return true;
    }

    public boolean toggleExcludedPlayer(PlayerEntity player, String name) {
        if (!canPlayerUse(player) || !player.isAlive() || player.isSpectator() || !hasModule(LaserModule.TARGET_FILTER)
                || !name.matches("[A-Za-z0-9_]{1,16}") || player.getServer() == null) return false;
        boolean changed = targetFilter.removeName(name);
        if (!changed) {
            ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(name);
            if (target != null) changed = targetFilter.exclude(target.getUuid(), target.getGameProfile().getName());
        }
        if (changed) sync();
        else player.sendMessage(Text.translatable("message.justifylasers.filter_player_unavailable", LaserTargetFilter.MAX_EXCLUSIONS), true);
        return changed;
    }

    public boolean isPrivate() {
        return privateAccess;
    }

    public boolean togglePrivacy(PlayerEntity player) {
        if (!canManageSecurity(player)) return false;
        privateAccess = !privateAccess;
        sync();
        return true;
    }

    @Override
    public void clear() {
        modules.clear();
        inventoryChanged();
    }

    public void inventoryChanged() {
        LaserCrystalItem crystal = crystal();
        if (isPoweredEmitter() && crystal != null) color = crystal.color();
        LaserEntityMode mode = entityMode();
        if (isPoweredEmitter() && mode != previousEntityMode && mode != LaserEntityMode.NONE) damageEntities = true;
        if (isPoweredEmitter() && mode != previousEntityMode && mode == LaserEntityMode.MINING) breakBlocks = true;
        if (mode.movesEntities()) damageStep = Math.min(damageStep, 50);
        previousEntityMode = mode;
        refreshActiveState();
        sync();
    }

    public static int clampBeamWidthStep(int step) {
        return MathHelper.clamp(step, 0, BEAM_WIDTH_STEPS);
    }

    public static float beamWidthScale(int step) {
        int clampedStep = clampBeamWidthStep(step);
        double progress = clampedStep / (double) BEAM_WIDTH_STEPS;
        return (float) (MIN_BEAM_WIDTH_SCALE * Math.pow(
                MAX_BEAM_WIDTH_SCALE / MIN_BEAM_WIDTH_SCALE,
                progress
        ));
    }

    public long getTicks() {
        return ticks;
    }

    public LaserBeamTrace getBeamTrace() {
        if (world == null) {
            Vec3d center = Vec3d.ofCenter(pos);
            return new LaserBeamTrace(center, center, getCachedState().get(LaserEmitterBlock.FACING), null);
        }
        return LaserBeamNetwork.path(this, 1.0F).segments().get(0);
    }

    @Override
    public void markRemoved() {
        clearHeating();
        if (world != null) {
            LaserBeamNetwork.removeEmitter(world, pos);
        }
        super.markRemoved();
    }

    @Override
    protected void writeLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        nbt.putBoolean("Enabled", enabled);
        nbt.putInt("RedstoneMode", redstoneMode.ordinal());
        nbt.putInt("Color", color.ordinal());
        nbt.putBoolean("BreakBlocks", breakBlocks);
        nbt.putBoolean("DamageEntities", damageEntities);
        nbt.putBoolean("IgniteEntities", igniteEntities);
        nbt.putInt("MiningSpeedStep", miningSpeedStep);
        nbt.putBoolean("SilkTouch", silkTouch);
        nbt.putBoolean("DropBlocks", dropBlocks);
        nbt.putBoolean("ScorchMarks", scorchMarks);
        nbt.putBoolean("CollectDrops", collectDrops);
        nbt.putInt("CreativeFluxStep", creativeFluxStep);
        nbt.putInt("CreativeFluxScale", 2);
        nbt.putInt("DamageStep", damageStep);
        nbt.putInt("KnockbackStep", knockbackStep);
        nbt.putInt("HitsPerSecond", hitsPerSecond);
        nbt.putInt("BeamWidthStep", beamWidthStep);
        nbt.putInt("BeamRange", beamRange);
        nbt.putBoolean("LightEmission", lightEmission);
        nbt.putBoolean("MinecraftLighting", minecraftLighting);
        nbt.put("TargetFilter", targetFilter.write());
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putString("OwnerName", ownerName);
        if (isPoweredEmitter()) {
            nbt.putInt("Energy", energy.stored());
            inventory.write(nbt, modules);
            nbt.putBoolean("PrivateAccess", privateAccess);
        }
    }

    @Override
    protected void readLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        LaserBeamNetwork.invalidate(world);
        targetFilter.read(nbt.getCompound("TargetFilter"));
        modules.clear();
        inventory.read(nbt, modules);
        energy.restore(nbt.getLong("Energy"));
        // Alpha 29 stored Mining separately. Never overwrite an occupied effect slot.
        if (modules.get(6).isEmpty() && modules.get(5).getItem() instanceof LaserModuleItem item && item.module() == LaserModule.BLOCK_DESTRUCTION) {
            modules.set(6, modules.get(5));
            modules.set(5, ItemStack.EMPTY);
        }
        previousEntityMode = entityMode();
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        ownerName = nbt.getString("OwnerName");
        privateAccess = owner != null && nbt.getBoolean("PrivateAccess");
        if (nbt.contains("Enabled")) {
            enabled = nbt.getBoolean("Enabled");
        }
        redstoneMode = LaserRedstoneMode.byIndex(nbt.getInt("RedstoneMode"));
        color = LaserColor.byIndex(nbt.getInt("Color"));
        breakBlocks = nbt.getBoolean("BreakBlocks");
        if (nbt.contains("DamageEntities")) {
            damageEntities = nbt.getBoolean("DamageEntities");
        }
        igniteEntities = nbt.getBoolean("IgniteEntities");
        miningSpeedStep = LaserMining.clampSpeedStep(nbt.getInt("MiningSpeedStep"));
        silkTouch = nbt.getBoolean("SilkTouch");
        dropBlocks = !nbt.contains("DropBlocks") || nbt.getBoolean("DropBlocks");
        scorchMarks = !nbt.contains("ScorchMarks") || nbt.getBoolean("ScorchMarks");
        collectDrops = nbt.getBoolean("CollectDrops");
        creativeFluxStep = nbt.contains("CreativeFluxStep") ? net.askcraft.justifylasers.laser.CreativeFlux.clamp(nbt.getInt("CreativeFluxStep"))
                : net.askcraft.justifylasers.laser.CreativeFlux.DEFAULT_STEP;
        if (nbt.contains("CreativeFluxStep") && nbt.getInt("CreativeFluxScale") < 2)
            creativeFluxStep = net.askcraft.justifylasers.laser.CreativeFlux.migrate(nbt.getInt("CreativeFluxStep"));
        damageStep = nbt.contains("DamageStep")
                ? LaserDamage.clampDamageStep(nbt.getInt("DamageStep"))
                : LaserDamage.DEFAULT_DAMAGE_STEP;
        knockbackStep = nbt.contains("KnockbackStep")
                ? LaserDamage.clampKnockbackStep(nbt.getInt("KnockbackStep"))
                : LaserDamage.DEFAULT_KNOCKBACK_STEP;
        hitsPerSecond = nbt.contains("HitsPerSecond")
                ? LaserDamage.clampHitsPerSecond(nbt.getInt("HitsPerSecond"))
                : LaserDamage.MAX_HITS_PER_SECOND;
        beamWidthStep = nbt.contains("BeamWidthStep")
                ? clampBeamWidthStep(nbt.getInt("BeamWidthStep"))
                : DEFAULT_BEAM_WIDTH_STEP;
        beamRange = nbt.contains("BeamRange") ? clampBeamRange(nbt.getInt("BeamRange")) : DEFAULT_RANGE;
        lightEmission = !nbt.contains("LightEmission") || nbt.getBoolean("LightEmission");
        // Saves predating the lighting split used LightEmission for both settings.
        minecraftLighting = nbt.contains("MinecraftLighting")
                ? nbt.getBoolean("MinecraftLighting")
                : lightEmission;
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable(isPoweredEmitter() ? "block.justifylasers.powered_laser_emitter"
                : LaserConfig.technicalMode() ? "block.justifylasers.creative_laser_emitter" : "block.justifylasers.laser_emitter");
    }

    @Override
    public BlockPos screenPosition() { return getPos(); }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        if (!canPlayerUse(player)) return null;
        return new LaserEmitterScreenHandler(syncId, playerInventory, this);
    }
}
