package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.block.IndustrialMachineBlock;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserEnergyBuffer;
import net.askcraft.justifylasers.energy.LaserEnergyHost;
import net.askcraft.justifylasers.industry.IndustryRecipes;
import net.askcraft.justifylasers.industry.GeneratorFuel;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.industry.ProcessFluid;
import net.askcraft.justifylasers.industry.CrystalGrowth;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserRedstoneMode;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.platform.InventoryNbt;
import net.askcraft.justifylasers.platform.LaserBlockEntity;
import net.askcraft.justifylasers.platform.LaserScreenFactory;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.platform.PlatformEnergyStorage;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.screen.IndustrialMachineScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class IndustrialMachineBlockEntity extends LaserBlockEntity implements SidedInventory, LaserScreenFactory, LaserEnergyHost, net.askcraft.justifylasers.laser.LaserLightSink {
    public static final int ACTIVE_SEED = 2, OUTPUT = 4, BLUEPRINT = 5, WATER_INPUT = 6, BUCKET_OUTPUT = 7, SLOT_COUNT = 8;
    public enum Status { IDLE, WORKING, NO_POWER, OUTPUT_FULL, DISABLED, NO_FUEL, CALIBRATING, UNFORMED, NO_WATER, NO_BLUEPRINT, REDSTONE, WRONG_SPECTRUM, LOW_FLUX, WRONG_FLUID, MISSING_INPUT }
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);
    private final LaserEnergyBuffer energy = new LaserEnergyBuffer(() -> LaserConfig.get().machineCapacity,
            () -> Math.max(LaserConfig.get().machineTransfer, LaserConfig.get().generatorPerTick), this::markDirty);
    private final PlatformEnergyStorage energyPort = new PlatformEnergyStorage(this);
    private boolean enabled = true;
    private int progress, fuel, fuelTotal, calibration, color = LaserColor.WHITE.ordinal();
    private Status status = Status.IDLE;
    private int lastSyncedEnergy = -1;
    private int syncedDuration;
    private int syncedRate, water, waterSpent;
    private ProcessFluid fluid = ProcessFluid.WATER, product = ProcessFluid.WATER;
    private int productAmount;
    private long spectralFlux;
    private net.askcraft.justifylasers.laser.LightMixture spectralLight = new net.askcraft.justifylasers.laser.LightMixture();
    private int spectrum = 0xFFFFFF;
    private boolean advancedGrowth;
    private String recipeKey = "";
    private long receivedFlux, lightTick = Long.MIN_VALUE;
    private net.askcraft.justifylasers.laser.LightMixture inputLight = new net.askcraft.justifylasers.laser.LightMixture();
    private int lightRgb = 0xFFFFFF;
    private long growthRemainder;
    private int temperature = 200;
    private GeneratorFuel burningFuel = GeneratorFuel.WOOD;
    private int generatedRate;
    private long heatRemainder;
    private BlockPos origin;
    private java.util.UUID owner;
    private String ownerName = "";
    private boolean privateAccess;
    private LaserRedstoneMode redstoneMode = LaserRedstoneMode.IGNORE;

    public IndustrialMachineBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.INDUSTRIAL_MACHINE, pos, state); }
    public MachineKind kind() { return ((IndustrialMachineBlock) getCachedState().getBlock()).kind(); }
    @Override public LaserEnergyBuffer energy() { var controller = controller(); return controller != null && controller != this ? controller.energy : energy; }
    public PlatformEnergyStorage energyPort() { return energyPort; }
    @Override public boolean acceptsEnergy() { return kind() != MachineKind.FUEL_GENERATOR && kind() != MachineKind.CRYSTAL_GROWER && formed(); }
    @Override public boolean exportsEnergy() { return kind() == MachineKind.FUEL_GENERATOR; }
    public int progress() { return progress; }
    public int fuel() { return fuel; }
    public int fuelTotal() { return fuelTotal; }
    public int temperature() { return temperature / 10; }
    public int efficiency() { return status == Status.WORKING ? burningFuel.efficiencyAt(temperature) : 0; }
    public int fuelMaxTemperature() { return burningFuel.maxTemperature() / 10; }
    public int generatedRate() { return generatedRate; }
    public long lightFlux() {
        var master = controller();
        if (master != null && master != this) return master.lightFlux();
        return world != null && (world.isClient || lightTick == world.getTime() || lightTick == world.getTime() - 1) ? receivedFlux : 0;
    }
    public int lightRgb() {
        var master = controller();
        return master != null && master != this ? master.lightRgb() : lightRgb;
    }
    public long spectralFlux() { var master = controller(); return master != null && master != this ? master.spectralFlux() : lightFlux() == 0 ? 0 : spectralFlux; }
    public int spectrum() { var master = controller(); return master != null && master != this ? master.spectrum() : spectrum; }
    @Override public boolean acceptsLaser(Direction side, Vec3d point) {
        if (!kind().opticalInput() || !formed() || side == null || !side.getAxis().isHorizontal()) return false;
        Vec3d local = point.subtract(Vec3d.of(origin));
        double plane = side.getDirection() == Direction.AxisDirection.POSITIVE ? 2 : 0;
        double depth = side.getAxis() == Direction.Axis.X ? local.x : local.z;
        double across = side.getAxis() == Direction.Axis.X ? local.z : local.x;
        return Math.abs(depth - plane) < .08 && Math.min(Math.abs(across - .5), Math.abs(across - 1.5)) < .27 && Math.abs(local.y - .5) < .25;
    }
    @Override public void receiveLight(long lumens, int rgb) {
        receiveLight(lumens, rgb, 0, rgb);
    }
    @Override public void receiveLight(long lumens, int rgb, long tunedLumens, int tunedRgb) {
        if (world == null || world.isClient || !kind().opticalInput() || !formed()) return;
        var master = controller();
        if (master != this) { if (master != null) master.receiveLight(lumens, rgb, tunedLumens, tunedRgb); return; }
        if (lightTick != world.getTime()) {
            receivedFlux = 0; lightTick = world.getTime();
            inputLight = new net.askcraft.justifylasers.laser.LightMixture();
            spectralLight = new net.askcraft.justifylasers.laser.LightMixture(); spectralFlux = 0;
        }
        long accepted = net.askcraft.justifylasers.laser.LuminousFlux.clamp(lumens);
        receivedFlux = net.askcraft.justifylasers.laser.LuminousFlux.clamp(receivedFlux + accepted);
        inputLight.add(rgb, accepted);
        lightRgb = inputLight.rgb();
        long tuned = Math.min(accepted, net.askcraft.justifylasers.laser.LuminousFlux.clamp(tunedLumens));
        spectralFlux = net.askcraft.justifylasers.laser.LuminousFlux.clamp(spectralFlux + tuned);
        spectralLight.add(tunedRgb, tuned); spectrum = spectralLight.rgb();
    }
    public int calibration() { return calibration; }
    public boolean enabled() { return enabled; }
    public Status status() { return status; }
    public int color() { return color; }
    public int duration() { return syncedDuration > 0 ? syncedDuration : kind().duration(); }
    public int rate() { return kind() == MachineKind.FUEL_GENERATOR ? generatedRate : syncedRate > 0 ? syncedRate : kind().rate(); }
    public BlockPos origin() { return origin; }
    public net.minecraft.util.math.Box getRenderBoundingBox() {
        if (!kind().multiblock() || origin == null) return new net.minecraft.util.math.Box(pos).expand(.03);
        return new net.minecraft.util.math.Box(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + 2, origin.getY() + 2, origin.getZ() + 2).expand(calibration > 0 ? 1.6 : .03);
    }
    public boolean formed() { return !kind().multiblock() || origin != null && net.askcraft.justifylasers.industry.ChamberStructure.complete(world, origin, kind()); }
    public boolean isController() { return !kind().multiblock() || pos.equals(origin); }
    public IndustrialMachineBlockEntity controller() {
        if (!kind().multiblock() || pos.equals(origin)) return this;
        if (origin == null || world == null || !world.isChunkLoaded(origin)) return null;
        return world.getBlockEntity(origin) instanceof IndustrialMachineBlockEntity machine && machine.kind() == kind() && origin.equals(machine.origin) ? machine : null;
    }
    public void setOrigin(BlockPos origin) { this.origin = origin == null ? null : origin.toImmutable(); sync(); }
    public boolean hasLocalContents() { return items.stream().anyMatch(stack -> !stack.isEmpty()) || energy.stored() > 0 || water > 0 || productAmount > 0; }
    public net.minecraft.inventory.Inventory localInventory() { return new net.minecraft.inventory.SimpleInventory(items.toArray(ItemStack[]::new)); }
    public int water() { var controller = controller(); return controller != null && controller != this ? controller.water : water; }
    public int tankCapacity() { return kind() == MachineKind.CHEMICAL_SYNTHESIZER ? 8_000 : LaserConfig.get().crystalTankCapacity; }
    public int tankCount() { return kind() == MachineKind.CHEMICAL_SYNTHESIZER ? 2 : kind().fluidTank() ? 1 : 0; }
    public ProcessFluid fluid(int tank) { var master = controller(); return master != null && master != this ? master.fluid(tank) : tank == 0 ? fluid : product; }
    public int fluidAmount(int tank) { var master = controller(); return master != null && master != this ? master.fluidAmount(tank) : tank == 0 ? water : productAmount; }
    public record Fluids(ProcessFluid input, int amount, ProcessFluid product, int productAmount) { }
    public Fluids fluids() { return new Fluids(fluid(0), fluidAmount(0), fluid(1), fluidAmount(1)); }
    public void restoreFluids(Fluids state) {
        var master = controller();
        if (master != null && master != this) { master.restoreFluids(state); return; }
        fluid = state.input; water = MathHelper.clamp(state.amount, 0, tankCapacity());
        product = state.product; productAmount = MathHelper.clamp(state.productAmount, 0, tankCapacity());
    }
    public boolean acceptsFluid(ProcessFluid value) { return kind().fluidTank() && (kind() != MachineKind.CHEMICAL_SYNTHESIZER || value == ProcessFluid.WATER); }
    public int fillFluid(ProcessFluid value, int amount, boolean simulate) {
        if (amount <= 0 || value == null || !acceptsFluid(value) || !formed()) return 0;
        var master = controller();
        if (master != this) return master == null ? 0 : master.fillFluid(value, amount, simulate);
        if (water > 0 && fluid != value) return 0;
        int accepted = Math.min(amount, tankCapacity() - water);
        if (!simulate && accepted > 0) { fluid = value; water += accepted; sync(); }
        return accepted;
    }
    public int drainFluid(int tank, int amount, boolean simulate) {
        if (tank < 0 || tank >= tankCount() || amount <= 0 || !formed()) return 0;
        var master = controller();
        if (master != this) return master == null ? 0 : master.drainFluid(tank, amount, simulate);
        int drained = Math.min(amount, fluidAmount(tank));
        if (!simulate && drained > 0) { if (tank == 0) water -= drained; else productAmount -= drained; sync(); }
        return drained;
    }
    public void restoreWater(int value) { var controller = controller(); if (controller != null && controller != this) controller.restoreWater(value); else water = MathHelper.clamp(value, 0, tankCapacity()); }
    public int fillWater(int amount, boolean simulate) {
        return fillFluid(ProcessFluid.WATER, amount, simulate);
    }
    public int drainWater(int amount, boolean simulate) {
        return fluid(0) == ProcessFluid.WATER ? drainFluid(0, amount, simulate) : 0;
    }
    public float completion(float delta) {
        double fraction = kind() == MachineKind.CRYSTAL_GROWER ? growthRemainder / (double)Math.max(1, rate()) : 0;
        double speed = kind() == MachineKind.CRYSTAL_GROWER ? growthFlux() / (double)Math.max(1, rate()) : 1;
        return (float)MathHelper.clamp((progress + fraction + (status == Status.WORKING ? delta * speed : 0)) / Math.max(1, duration()), 0, 1);
    }
    public void toggle() { enabled = !enabled; sync(); }

    public java.util.UUID owner() { return owner; }
    public String ownerName() { var master = controller(); return master != null && master != this ? master.ownerName : ownerName; }
    public boolean isPrivate() { var master = controller(); return master != null && master != this ? master.privateAccess : privateAccess; }
    public LaserRedstoneMode redstoneMode() { return redstoneMode; }
    public boolean canManageSecurity(PlayerEntity player) {
        var master = controller();
        return player.hasPermissionLevel(2) || player.getUuid().equals(master == null ? owner : master.owner);
    }
    public boolean canAccess(PlayerEntity player) { return !isPrivate() || canManageSecurity(player); }
    public void initializeOwner(PlayerEntity player) {
        var master = controller();
        if (master != null && master != this) { master.initializeOwner(player); return; }
        if (owner == null) { owner = player.getUuid(); ownerName = player.getGameProfile().getName(); syncAccess(); }
    }
    public void copyAccess(IndustrialMachineBlockEntity source) {
        owner = source.owner; ownerName = source.ownerName; privateAccess = source.privateAccess; redstoneMode = source.redstoneMode;
        sync();
    }
    private void syncAccess() {
        sync();
        if (origin != null && world != null && !world.isClient)
            for (BlockPos member : net.askcraft.justifylasers.industry.ChamberStructure.positions(origin))
                if (!member.equals(pos) && world.isChunkLoaded(member) && world.getBlockEntity(member) instanceof IndustrialMachineBlockEntity part
                        && origin.equals(part.origin)) part.copyAccess(this);
    }
    public boolean togglePrivacy(PlayerEntity player) {
        if (!canManageSecurity(player)) return false;
        privateAccess = !privateAccess; syncAccess(); return true;
    }
    public void cycleRedstone() { redstoneMode = redstoneMode.next(); syncAccess(); }
    public boolean redstoneAllowsWork() {
        if (redstoneMode == LaserRedstoneMode.IGNORE) return true;
        boolean powered = origin == null ? world.isReceivingRedstonePower(pos)
                : net.askcraft.justifylasers.industry.ChamberStructure.positions(origin).stream().anyMatch(world::isReceivingRedstonePower);
        return redstoneMode.allows(powered);
    }

    public static void tick(World world, BlockPos pos, BlockState state, IndustrialMachineBlockEntity machine) {
        if (world.isClient) {
            if (machine.status == Status.WORKING && machine.kind() != MachineKind.FUEL_GENERATOR) {
                if (machine.kind() == MachineKind.CRYSTAL_GROWER) {
                    long work = machine.nextGrowthWork();
                    machine.progress = (int)(work / Math.max(1, machine.rate()));
                    machine.growthRemainder = work % Math.max(1, machine.rate());
                } else machine.progress = Math.min(machine.duration(), machine.progress + 1);
            }
            if (machine.calibration > 0) machine.calibration--;
            return;
        }
        if (machine.kind().multiblock()) {
            if (machine.origin != null && !machine.formed()) {
                // A partially unloaded structure must not force-load chunks or consume ingredients.
                boolean loaded = net.askcraft.justifylasers.industry.ChamberStructure.positions(machine.origin).stream().allMatch(world::isChunkLoaded);
                if (loaded) net.askcraft.justifylasers.industry.ChamberStructure.dismantle(machine);
            }
            if (!machine.formed()) {
                if (machine.status != Status.UNFORMED) { machine.status = Status.UNFORMED; machine.sync(); }
                return;
            }
            if (!machine.isController()) return;
        }
        machine.transferBuckets();
        Status previous = machine.status;
        int before = machine.progress;
        int beforeTemperature = machine.temperature;
        if (machine.kind() == MachineKind.FUEL_GENERATOR) {
            machine.generate();
            machine.chargeTablet();
            if (machine.status != Status.WORKING) {
                int previousTemperature = machine.temperature;
                machine.temperature = machine.burningFuel.approach(machine.temperature, LaserConfig.get().generatorHeatPerTick, false);
                if (previousTemperature != machine.temperature) machine.markDirty();
            }
            Platform.exportEnergy(machine);
        } else machine.process();
        boolean animating = machine.status == Status.WORKING || machine.calibration > 0;
        boolean periodic = world.getTime() % 10 == Math.floorMod(pos.asLong(), 10)
                && (animating || machine.kind().opticalInput() || machine.temperature > 200 || machine.energy.stored() != machine.lastSyncedEnergy);
        boolean cooled = beforeTemperature > GeneratorFuel.AMBIENT && machine.temperature == GeneratorFuel.AMBIENT;
        if (previous != machine.status || before > 0 && machine.progress == 0 || cooled || periodic) machine.sync();
    }

    private void generate() {
        generatedRate = 0;
        if (!enabled || !LaserConfig.technicalMode()) { status = Status.DISABLED; return; }
        if (!redstoneAllowsWork()) { status = Status.REDSTONE; return; }
        if (energy.stored() >= energy.capacity()) { status = Status.OUTPUT_FULL; return; }
        GeneratorFuel profile = fuel > 0 ? burningFuel : GeneratorFuel.of(items.get(0));
        int nextTemperature = profile.approach(temperature, LaserConfig.get().generatorHeatPerTick, true);
        long produced = heatRemainder + profile.outputNumerator(nextTemperature, kind().rate());
        int output = (int) (produced / GeneratorFuel.OUTPUT_DIVISOR);
        if (energy.capacity() - energy.stored() < output) { status = Status.OUTPUT_FULL; return; }
        if (fuel <= 0) {
            ItemStack stack = items.get(0);
            int duration = IndustryRecipes.fuelTicks(stack);
            if (duration <= 0) { status = Status.NO_FUEL; return; }
            ItemStack remainder = stack.getItem().hasRecipeRemainder() ? new ItemStack(stack.getItem().getRecipeRemainder()) : ItemStack.EMPTY;
            if (!remainder.isEmpty() && !canOutput(remainder)) { status = Status.OUTPUT_FULL; return; }
            stack.decrement(1);
            if (!remainder.isEmpty()) addOutput(remainder);
            fuel = fuelTotal = duration;
            burningFuel = profile;
        }
        temperature = nextTemperature;
        heatRemainder = produced % GeneratorFuel.OUTPUT_DIVISOR;
        generatedRate = energy.receive(output, false);
        fuel--;
        status = Status.WORKING;
        markDirty();
    }

    private void chargeTablet() {
        ItemStack tablet = items.get(WATER_INPUT);
        if (!net.askcraft.justifylasers.energy.RechargeableItem.accepts(tablet)) return;
        int accepted = net.askcraft.justifylasers.energy.RechargeableItem.receive(tablet, Math.min(256, energy.stored()), true);
        if (accepted > 0 && energy.consume(accepted)) {
            net.askcraft.justifylasers.energy.RechargeableItem.receive(tablet, accepted, false);
            markDirty();
        }
    }

    private void process() {
        if (!enabled || !LaserConfig.technicalMode()) { status = Status.DISABLED; return; }
        if (!redstoneAllowsWork()) { status = Status.REDSTONE; return; }
        if (calibration > 0) {
            calibration--;
            status = Status.CALIBRATING;
            markDirty();
            return;
        }
        var recipe = recipe();
        if (recipe == null || !recipe.matches(this) || kind() == MachineKind.ASSEMBLY_CHAMBER
                && (!(items.get(BLUEPRINT).getItem() instanceof net.askcraft.justifylasers.item.AssemblyBlueprintItem blueprint) || !blueprint.recipe().equals(recipe.blueprint()))) {
            status = hasPendingWork() ? Status.MISSING_INPUT : kind() == MachineKind.ASSEMBLY_CHAMBER && !(items.get(BLUEPRINT).getItem() instanceof net.askcraft.justifylasers.item.AssemblyBlueprintItem) ? Status.NO_BLUEPRINT : Status.IDLE;
            return;
        }
        int inputColor = recipe.color(this);
        CrystalGrowth crystal = recipe.process().crystal();
        ItemStack seed = items.get(ACTIVE_SEED).isEmpty() ? items.get(0) : items.get(ACTIVE_SEED);
        String signature = recipe.key() + ":" + inputColor + (crystal == null ? "" : ":" + crystal.stage(seed));
        // Paid work belongs to this recipe and seed condition, even while an input is absent.
        if (hasPendingWork() && !recipeKey.equals(signature)) { status = Status.MISSING_INPUT; return; }
        recipeKey = signature;
        color = inputColor;
        if (kind() == MachineKind.CRYSTAL_GROWER && rate() != recipe.rate())
            growthRemainder = growthRemainder * recipe.rate() / Math.max(1, rate());
        syncedDuration = recipe.duration(); syncedRate = recipe.rate();
        advancedGrowth = crystal != null;
        ItemStack result = recipe.output(this);
        if (crystal != null) result.setCount(3);
        ProcessFluid outputFluid = recipe.process().outputFluid();
        if (outputFluid != null ? productAmount > 0 && product != outputFluid || productAmount + recipe.waterCost() > tankCapacity()
                : !canOutput(result)) { status = Status.OUTPUT_FULL; return; }
        boolean growing = kind() == MachineKind.CRYSTAL_GROWER;
        if (growing && recipe.process().spectrum() >= 0 && (spectralFlux() == 0 || !CrystalGrowth.matchesSpectrum(spectrum, recipe.process().spectrum()))) {
            status = Status.WRONG_SPECTRUM; return;
        }
        if (growing && growthFlux() < recipe.process().minimumFlux()
                || kind() == MachineKind.LASER_CUTTER && lightFlux() < recipe.process().cuttingFlux()) { status = Status.LOW_FLUX; return; }
        long nextWork = growing ? nextGrowthWork() : progress + 1L;
        long totalWork = growing ? (long)recipe.duration() * recipe.rate() : recipe.duration();
        int waterDue = Math.max(0, (int)((double)recipe.waterCost() * nextWork / totalWork) - waterSpent);
        if (recipe.waterCost() > 0) {
            if (water > 0 && fluid != recipe.process().inputFluid()) { status = Status.WRONG_FLUID; return; }
            if (water < waterDue || progress == 0 && growthRemainder == 0 && water < recipe.waterCost()) { status = Status.NO_WATER; return; }
        }
        // LM is a current optical flux, not FE stored in the electrical buffer.
        if (growing ? growthFlux() <= 0 : !energy.consume(recipe.rate())) { status = Status.NO_POWER; return; }
        if (crystal != null && items.get(ACTIVE_SEED).isEmpty()) items.set(ACTIVE_SEED, items.get(0).split(1));
        water -= waterDue; waterSpent += waterDue;
        status = Status.WORKING;
        progress = growing ? (int)(nextWork / recipe.rate()) : progress + 1;
        growthRemainder = growing ? nextWork % recipe.rate() : 0;
        if (progress >= recipe.duration()) {
            if (crystal == null) {
                for (int slot = 0; slot < recipe.inputs().size(); slot++) items.get(slot).decrement(recipe.counts().get(slot));
            } else {
                result.setCount(CrystalGrowth.yield(crystal.stage(items.get(ACTIVE_SEED)), world.random.nextInt(1000)));
                items.set(ACTIVE_SEED, crystal.degraded(items.get(ACTIVE_SEED), world.random.nextInt(1000)));
            }
            if (outputFluid == null) addOutput(result);
            else { product = outputFluid; productAmount += recipe.waterCost(); }
            progress = 0; growthRemainder = 0; waterSpent = 0; recipeKey = "";
            if (kind() == MachineKind.ASSEMBLY_CHAMBER) {
                calibration = 30;
                status = Status.CALIBRATING;
                world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, .45F, 1.5F);
            }
        }
        markDirty();
    }

    private long nextGrowthWork() {
        long reference = Math.max(1, rate());
        // Integer light-work preserves even a 1 lm input across ticks and save/load.
        // One completed batch per tick is the normal machine throughput limit.
        return Math.min((long)duration() * reference, progress * reference + growthRemainder + growthFlux());
    }
    private long growthFlux() { return advancedGrowth ? spectralFlux() : lightFlux(); }
    private boolean hasPendingWork() { return progress > 0 || growthRemainder > 0 || waterSpent > 0; }

    public net.askcraft.justifylasers.industry.MachineRecipeData recipe() {
        if (world == null) return null;
        String blueprint = items.get(BLUEPRINT).getItem() instanceof net.askcraft.justifylasers.item.AssemblyBlueprintItem item ? item.recipe() : "";
        var recipes = net.askcraft.justifylasers.industry.IndustryRecipe.all(world).stream().filter(recipe -> recipe.kind() == kind()
                && (hasPendingWork() ? recipeKey.startsWith(recipe.key() + ":")
                : kind() != MachineKind.ASSEMBLY_CHAMBER || !blueprint.isEmpty() && recipe.blueprint().equals(blueprint))).toList();
        if (hasPendingWork()) return recipes.isEmpty() ? null : recipes.get(0);
        return recipes.stream().filter(recipe -> recipe.matches(this)).findFirst().orElse(recipes.isEmpty() ? null : recipes.get(0));
    }
    private void transferBuckets() {
        if (!kind().fluidTank()) return;
        ItemStack input = items.get(WATER_INPUT), output = items.get(BUCKET_OUTPUT);
        boolean draining = input.isOf(net.minecraft.item.Items.BUCKET);
        int tank = kind() == MachineKind.CHEMICAL_SYNTHESIZER ? 1 : 0;
        ProcessFluid value = draining ? fluid(tank) : java.util.Arrays.stream(ProcessFluid.values()).filter(type -> input.isOf(type.bucket())).findFirst().orElse(null);
        if (value == null) return;
        ItemStack remainder = new ItemStack(draining ? value.bucket() : net.minecraft.item.Items.BUCKET);
        if (!output.isEmpty() && (!GameVersion.canStack(output, remainder) || output.getCount() >= output.getMaxCount())) return;
        if (draining ? drainFluid(tank, 1000, true) != 1000 : fillFluid(value, 1000, true) != 1000) return;
        if (draining) drainFluid(tank, 1000, false); else fillFluid(value, 1000, false);
        input.decrement(1);
        if (output.isEmpty()) items.set(BUCKET_OUTPUT, remainder); else output.increment(1);
        sync();
    }

    private boolean canOutput(ItemStack result) {
        ItemStack output = items.get(OUTPUT);
        return output.isEmpty() || GameVersion.canStack(output, result) && output.getCount() + result.getCount() <= Math.min(getMaxCountPerStack(), output.getMaxCount());
    }
    private void addOutput(ItemStack result) {
        if (result.isEmpty()) return;
        if (items.get(OUTPUT).isEmpty()) items.set(OUTPUT, result.copy());
        else items.get(OUTPUT).increment(result.getCount());
    }
    public void sync() {
        markDirty();
        if (world != null && !world.isClient) {
            lastSyncedEnergy = energy.stored();
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override protected void writeLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        inventory.write(nbt, items);
        nbt.putInt("Energy", energy.stored()); nbt.putInt("Progress", progress);
        nbt.putLong("GrowthRemainder", growthRemainder);
        nbt.putInt("Duration", duration());
        nbt.putInt("Rate", rate()); nbt.putInt("Water", water); nbt.putInt("WaterSpent", waterSpent); nbt.putString("Recipe", recipeKey);
        nbt.putString("ProcessFluid", fluid.id()); nbt.putString("ProductFluid", product.id()); nbt.putInt("ProductAmount", productAmount);
        nbt.putBoolean("AdvancedGrowth", advancedGrowth); nbt.putLong("SpectralFlux", spectralFlux()); nbt.putInt("Spectrum", spectrum);
        if (origin != null) nbt.putLong("ChamberOrigin", origin.asLong());
        nbt.putInt("Fuel", fuel); nbt.putInt("FuelTotal", fuelTotal); nbt.putInt("Calibration", calibration);
        nbt.putInt("FuelBalanceRevision", 31);
        nbt.putLong("LightFlux", lightFlux()); nbt.putInt("Temperature", temperature);
        nbt.putInt("LightRgb", lightRgb());
        nbt.putInt("FuelMaxTemperature", burningFuel.maxTemperature()); nbt.putInt("FuelEfficiency", burningFuel.efficiency());
        nbt.putLong("HeatRemainder", heatRemainder); nbt.putInt("GeneratedRate", generatedRate);
        nbt.putInt("Color", color); nbt.putInt("Status", status.ordinal()); nbt.putBoolean("Enabled", enabled);
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putString("OwnerName", ownerName); nbt.putBoolean("PrivateAccess", privateAccess);
        nbt.putInt("RedstoneMode", redstoneMode.ordinal());
    }
    @Override protected void readLaserNbt(NbtCompound nbt, InventoryNbt inventory) {
        items.clear(); inventory.read(nbt, items);
        for (int slot = 0; slot < items.size(); slot++) items.set(slot, net.askcraft.justifylasers.industry.CrystalSeed.migrate(items.get(slot)));
        energy.restore(nbt.getInt("Energy"));
        syncedDuration = MathHelper.clamp(nbt.contains("Duration") ? nbt.getInt("Duration") : kind().duration(), 1, 72_000);
        syncedRate = Math.max(0, nbt.getInt("Rate"));
        water = MathHelper.clamp(nbt.getInt("Water"), 0, tankCapacity()); waterSpent = MathHelper.clamp(nbt.getInt("WaterSpent"), 0, 64_000);
        fluid = java.util.Arrays.stream(ProcessFluid.values()).filter(value -> value.id().equals(nbt.getString("ProcessFluid"))).findFirst().orElse(ProcessFluid.WATER);
        product = java.util.Arrays.stream(ProcessFluid.values()).filter(value -> value.id().equals(nbt.getString("ProductFluid"))).findFirst().orElse(ProcessFluid.WATER);
        productAmount = MathHelper.clamp(nbt.getInt("ProductAmount"), 0, tankCapacity());
        advancedGrowth = nbt.getBoolean("AdvancedGrowth"); spectralFlux = net.askcraft.justifylasers.laser.LuminousFlux.clamp(nbt.getLong("SpectralFlux")); spectrum = nbt.getInt("Spectrum") & 0xFFFFFF;
        recipeKey = nbt.getString("Recipe"); origin = nbt.contains("ChamberOrigin") ? BlockPos.fromLong(nbt.getLong("ChamberOrigin")) : null;
        progress = MathHelper.clamp(nbt.getInt("Progress"), 0, 72_000);
        growthRemainder = Math.max(0, Math.min(Math.max(1, rate()) - 1L, nbt.getLong("GrowthRemainder")));
        fuel = MathHelper.clamp(nbt.getInt("Fuel"), 0, 1_000_000); fuelTotal = MathHelper.clamp(nbt.getInt("FuelTotal"), 0, 1_000_000);
        calibration = MathHelper.clamp(nbt.getInt("Calibration"), 0, 30);
        receivedFlux = net.askcraft.justifylasers.laser.LuminousFlux.clamp(nbt.getLong("LightFlux"));
        lightTick = Long.MIN_VALUE;
        lightRgb = nbt.contains("LightRgb") ? nbt.getInt("LightRgb") & 0xFFFFFF : 0xFFFFFF;
        temperature = MathHelper.clamp(nbt.contains("Temperature") ? nbt.getInt("Temperature") : GeneratorFuel.AMBIENT, GeneratorFuel.AMBIENT, GeneratorFuel.MAX_TEMPERATURE);
        burningFuel = nbt.contains("FuelMaxTemperature") ? new GeneratorFuel(
                MathHelper.clamp(nbt.getInt("FuelMaxTemperature"), GeneratorFuel.AMBIENT + 1, GeneratorFuel.MAX_TEMPERATURE),
                MathHelper.clamp(nbt.getInt("FuelEfficiency"), 1, GeneratorFuel.MAX_EFFICIENCY)) : GeneratorFuel.WOOD;
        heatRemainder = Math.max(0, Math.min(GeneratorFuel.OUTPUT_DIVISOR - 1, nbt.getLong("HeatRemainder")));
        if (nbt.getInt("FuelBalanceRevision") < 31 && burningFuel.equals(GeneratorFuel.LAVA)) {
            fuel /= 3;
            fuelTotal /= 3;
        }
        generatedRate = MathHelper.clamp(nbt.getInt("GeneratedRate"), 0, LaserConfig.get().generatorPerTick);
        color = LaserColor.byIndex(nbt.getInt("Color")).ordinal();
        status = Status.values()[MathHelper.clamp(nbt.getInt("Status"), 0, Status.values().length - 1)];
        enabled = !nbt.contains("Enabled") || nbt.getBoolean("Enabled");
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        ownerName = nbt.getString("OwnerName"); privateAccess = owner != null && nbt.getBoolean("PrivateAccess");
        redstoneMode = LaserRedstoneMode.byIndex(nbt.getInt("RedstoneMode"));
    }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
    @Override public BlockPos screenPosition() { return pos; }
    @Override public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buffer) { buffer.writeBlockPos(pos); }
    @Override public Text getDisplayName() { return Text.translatable("block.justifylasers." + kind().id()); }
    @Override public ScreenHandler createMenu(int id, PlayerInventory inventory, PlayerEntity player) {
        return canPlayerUse(player) ? new IndustrialMachineScreenHandler(id, inventory, this) : null;
    }
    @Override public int size() { return items.size(); }
    @Override public boolean isEmpty() { for (int i = 0; i < size(); i++) if (!getStack(i).isEmpty()) return false; return true; }
    @Override public ItemStack getStack(int slot) { var controller = controller(); return controller != null && controller != this ? controller.getStack(slot) : items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) { var controller = controller(); if (controller != null && controller != this) return controller.removeStack(slot, amount); var stack = Inventories.splitStack(items, slot, amount); sync(); return stack; }
    @Override public ItemStack removeStack(int slot) { var controller = controller(); if (controller != null && controller != this) return controller.removeStack(slot); var stack = Inventories.removeStack(items, slot); sync(); return stack; }
    @Override public void setStack(int slot, ItemStack stack) { var controller = controller(); if (controller != null && controller != this) { controller.setStack(slot, stack); return; } stack = net.askcraft.justifylasers.industry.CrystalSeed.migrate(stack); items.set(slot, stack); stack.setCount(Math.min(slot == BLUEPRINT ? 1 : getMaxCountPerStack(), stack.getCount())); sync(); }
    @Override public void clear() {
        var controller = controller();
        if (controller != null && controller != this) { controller.clear(); return; }
        items.clear(); progress = 0; growthRemainder = 0; waterSpent = 0; recipeKey = ""; sync();
    }
    @Override public void markDirty() {
        super.markDirty();
        var controller = controller();
        if (controller != null && controller != this) controller.markDirty();
    }
    @Override public boolean isValid(int slot, ItemStack stack) {
        var controller = controller(); if (controller != null && controller != this) return controller.isValid(slot, stack);
        if (slot == BLUEPRINT) return kind() == MachineKind.ASSEMBLY_CHAMBER && stack.getItem() instanceof net.askcraft.justifylasers.item.AssemblyBlueprintItem;
        if (slot == WATER_INPUT) return kind().fluidTank() ? stack.isOf(net.minecraft.item.Items.BUCKET)
                || java.util.Arrays.stream(ProcessFluid.values()).anyMatch(value -> acceptsFluid(value) && stack.isOf(value.bucket()))
                : kind() == MachineKind.FUEL_GENERATOR && net.askcraft.justifylasers.energy.RechargeableItem.accepts(stack);
        if (slot == OUTPUT || slot == BUCKET_OUTPUT || slot < 0 || slot >= kind().inputs()) return false;
        if (kind() == MachineKind.FUEL_GENERATOR) return IndustryRecipes.accepts(kind(), slot, stack);
        if (world == null) return false;
        var selected = recipe();
        return net.askcraft.justifylasers.industry.IndustryRecipe.all(world).stream().filter(recipe -> recipe.kind() == kind()
                        && (kind() != MachineKind.ASSEMBLY_CHAMBER || selected != null && selected.key().equals(recipe.key())))
                .anyMatch(recipe -> slot < recipe.inputs().size() && recipe.inputs().get(slot).test(stack)
                        && (recipe.process().crystal() == null || recipe.process().crystal().stage(stack) >= 0));
    }
    @Override public int[] getAvailableSlots(Direction side) {
        if (isPrivate() || kind().multiblock() && !formed()) return new int[0];
        return java.util.stream.IntStream.concat(java.util.stream.IntStream.range(0, kind().inputs()),
                kind().fluidTank() ? java.util.stream.IntStream.of(OUTPUT, WATER_INPUT, BUCKET_OUTPUT)
                        : kind() == MachineKind.FUEL_GENERATOR ? java.util.stream.IntStream.of(OUTPUT, WATER_INPUT) : java.util.stream.IntStream.of(OUTPUT)).toArray();
    }
    @Override public boolean canInsert(int slot, ItemStack stack, Direction side) { return !isPrivate() && (!kind().multiblock() || formed()) && slot != BLUEPRINT && isValid(slot, stack); }
    @Override public boolean canExtract(int slot, ItemStack stack, Direction side) { return !isPrivate() && (!kind().multiblock() || formed()) && (slot == OUTPUT || slot == BUCKET_OUTPUT
            || slot == WATER_INPUT && kind() == MachineKind.FUEL_GENERATOR && net.askcraft.justifylasers.energy.RechargeableItem.accepts(stack)
                    && net.askcraft.justifylasers.energy.RechargeableItem.stored(stack) >= net.askcraft.justifylasers.energy.RechargeableItem.capacity(stack)); }
    @Override public boolean canPlayerUse(PlayerEntity player) {
        return world != null && world.getBlockEntity(pos) == this && !player.isSpectator() && canAccess(player)
                && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64;
    }
}
