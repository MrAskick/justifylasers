package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;

public class Alpha31GameTests implements FabricGameTest {
    private static final BlockPos EMITTER = new BlockPos(1, 3, 3), MODULE = EMITTER.east(2);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void serverDistanceSettingsRoundTripWithoutChangingLocalConfig(TestContext context) {
        var buffer = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        boolean originalMode = LaserConfig.technicalMode();
        boolean enabled = LaserConfig.get().beamAttenuation;
        double rate = LaserConfig.get().beamLossPerBlock;
        try {
            var policy = new net.askcraft.justifylasers.network.LaserPolicyPacket(true, true, .02);
            policy.write(buffer);
            var received = new net.askcraft.justifylasers.network.LaserPolicyPacket(buffer);
            context.assertTrue(received.equals(policy), "Optical server policy survives packet serialization");
            received.apply();
            context.assertTrue(Math.abs(BeamAttenuation.retention(32, true) - Math.pow(.98, 32)) < 1e-12,
                    "Client uses the connected server's attenuation");
            context.assertTrue(LaserConfig.get().beamAttenuation == enabled && LaserConfig.get().beamLossPerBlock == rate,
                    "Server policy does not overwrite the local config or integrated server settings");
            LaserConfig.resetServerMode();
            context.assertTrue(BeamAttenuation.retention(32, true) == BeamAttenuation.retention(32),
                    "Disconnect restores the local policy");
            context.complete();
        } finally {
            buffer.release();
            LaserConfig.resetServerMode();
            LaserConfig.applyServerMode(originalMode);
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void distanceLossUsesTheWholeRouteAndDefaultsToNoLoss(TestContext context) {
        var config = LaserConfig.get();
        boolean enabled = config.beamAttenuation;
        double loss = config.beamLossPerBlock;
        var source = new Source(context.getWorld(), context.getAbsolutePos(EMITTER), BeamBehavior.NONE, 6_000_000, 240_000);
        try {
            config.beamLossPerBlock = .01;
            config.beamAttenuation = false;
            context.setBlockState(EMITTER.east(6), Blocks.BEDROCK);
            context.assertTrue(Math.abs(trace(source).last().power() - .96) < 1e-10, "Disabled attenuation preserves paid power");
            config.beamAttenuation = true;
            var distant = trace(source).last();
            context.assertTrue(Math.abs(distant.power() - .96 * Math.pow(.99, distant.length())) < 1e-10,
                    "Module payment and distance loss both apply, without double charging");
            context.setBlockState(EMITTER.east(2), Blocks.BEDROCK);
            context.assertTrue(trace(source).last().power() > distant.power(), "A nearer consumer receives more light");
            context.setBlockState(EMITTER.east(2), ModLaserParts.DECORATIONS.get("red_crystal").getDefaultState());
            var recolored = trace(source);
            context.assertTrue(recolored.segments().size() == 2, "The crystal splits the optical route into two segments");
            context.assertTrue(Math.abs(recolored.last().power() - distant.power()) < 1e-5,
                    "An optical pass-through does not reset distance attenuation");
            context.complete();
        } finally {
            config.beamAttenuation = enabled;
            config.beamLossPerBlock = loss;
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void miningHasIdenticalInstalledAndPlacedOpticalPricesAndWork(TestContext context) {
        context.setBlockState(EMITTER, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        var emitter = (LaserEmitterBlockEntity) context.getBlockEntity(EMITTER);
        emitter.setStack(6, new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_DESTRUCTION)));
        emitter.getPropertyDelegate().set(3, 1);
        context.setBlockState(EMITTER.east(6), Blocks.BEDROCK);
        for (boolean upgraded : new boolean[]{false, true}) for (int speed : new int[]{0, 25, 50, 100}) {
            context.removeBlock(MODULE);
            emitter.getPropertyDelegate().set(14, speed);
            var upgrades = new LaserModule[]{LaserModule.BLOCK_DROPS, LaserModule.SILK_TOUCH, LaserModule.IGNITION, LaserModule.BLOCK_COLLECTION};
            for (var upgrade : upgrades) emitter.setStack(upgrade.slot(), upgraded ? new ItemStack(ModLaserParts.MODULES.get(upgrade)) : ItemStack.EMPTY);
            var installed = trace(new Source(context.getWorld(), emitter.getPos(), emitter.beamBehavior(), 6_000_000, emitter.moduleFluxCost()));
            context.setBlockState(MODULE, ModLaserParts.DECORATIONS.get("block_destruction_module").getDefaultState());
            var module = (LaserPartBlockEntity) context.getBlockEntity(MODULE);
            var nbt = module.createNbt(); nbt.putInt("MiningSpeed", speed); module.readNbt(nbt);
            int[] slots = {9, 10, 11, 12};
            if (upgraded) for (int i = 0; i < slots.length; i++) module.setStack(slots[i], new ItemStack(ModLaserParts.MODULES.get(upgrades[i])));
            var placed = trace(new Source(context.getWorld(), emitter.getPos(), BeamBehavior.NONE, 6_000_000, 0));
            context.assertTrue(emitter.moduleFluxCost() == module.operatingFlux(), "Identical LM cost, speed=" + speed + ", upgrades=" + upgraded);
            context.assertTrue(Math.abs(installed.last().power() - placed.last().power()) < 1e-10, "Identical paid output flux");
            context.assertTrue(installed.last().workPower() == placed.last().workPower() && placed.last().workPower() == 1,
                    "Paid mining is not slowed a second time by its remaining flux");
            context.assertTrue(installed.last().behavior().mining().speed() == placed.last().behavior().mining().speed(), "Identical configured mining speed");
        }
        context.removeBlock(EMITTER); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void insufficientInstalledMiningFluxCannotMineForFree(TestContext context) {
        context.setBlockState(EMITTER, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        var emitter = (LaserEmitterBlockEntity) context.getBlockEntity(EMITTER);
        emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
        emitter.setStack(7, new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 4));
        emitter.setStack(6, new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_DESTRUCTION)));
        emitter.getPropertyDelegate().set(3, 1); emitter.getPropertyDelegate().set(14, 100);
        emitter.energy().restore(1_000_000);
        context.setBlockState(EMITTER.east(4), Blocks.STONE);
        LaserEmitterBlockEntity.serverTick(context.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
        context.assertTrue(LaserBeamNetwork.path(emitter, 1).segments().isEmpty(), "Not enough LM, despite adequate FE");
        context.assertTrue(context.getBlockState(EMITTER.east(4)).isOf(Blocks.STONE), "Unfunded mining cannot break blocks");
        context.removeBlock(EMITTER); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 50)
    public void healingIsFixedPerSecondAcrossOverlappingSources(TestContext context) {
        var mob = context.spawnMob(EntityType.VILLAGER, new Vec3d(4.5, 3, 3.5));
        mob.setAiDisabled(true); mob.setNoGravity(true); mob.setHealth(10);
        var behavior = BeamBehavior.NONE.withEntity(new BeamBehavior.EntityEffect(LaserEntityMode.HEAL, true, 1000, 0, 20, false));
        var source = new Source(context.getWorld(), context.getAbsolutePos(EMITTER), behavior, 6_000_000, 0);
        var effects = new LaserBeamEffects();
        for (int tick = 1; tick <= 40; tick++) {
            int elapsed = tick;
            context.runAtTick(tick, () -> {
                effects.tick(source, trace(source));
                new LaserBeamEffects().tick(source, trace(source));
                context.assertTrue(Math.abs(mob.getHealth() - (elapsed <= 20 ? 10.1 : 10.2)) < .0001,
                        "Fixed healing rate and no duplicate source contribution at tick " + elapsed);
                if (elapsed == 40) { mob.discard(); context.complete(); }
            });
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void healingRejectsTuningAndThicknessTierTwoIsEightUpgrades(TestContext context) {
        context.setBlockState(MODULE, ModLaserParts.DECORATIONS.get("entity_heal_module").getDefaultState());
        var healing = (LaserPartBlockEntity) context.getBlockEntity(MODULE);
        var player = context.createMockSurvivalPlayer(); player.setPosition(Vec3d.ofCenter(healing.getPos()));
        context.assertFalse(healing.setting(player, 2100, "") || healing.setting(player, 4020, ""), "No hidden healing tuning packets");
        context.assertTrue(healing.operatingFlux() == 4000, "Fixed healing price is doubled");
        context.setBlockState(EMITTER, ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
        var emitter = (LaserEmitterBlockEntity) context.getBlockEntity(EMITTER);
        emitter.setStack(8, new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS), 8));
        float width = emitter.getBeamWidthScale(); int cost = emitter.energyCost();
        emitter.setStack(8, new ItemStack(ModLaserParts.ADVANCED_THICKNESS_MODULE));
        context.assertTrue(width == emitter.getBeamWidthScale() && cost == emitter.energyCost(), "Tier II equals eight tier I upgrades");
        emitter.setStack(8, new ItemStack(ModLaserParts.ADVANCED_THICKNESS_MODULE, 64));
        context.assertTrue(emitter.getBeamWidthScale() == 10 && emitter.energyCost() == LaserConfig.get().basePerTick + 512, "Clamped geometry still charges installed tiers");
        context.removeBlock(EMITTER); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void lavaBurnsForOneThirdAndOnlyLavaChanges(TestContext context) {
        context.assertTrue(net.askcraft.justifylasers.industry.IndustryRecipes.fuelTicks(new ItemStack(Items.LAVA_BUCKET)) == 20_000 / 3, "Lava duration reduced threefold");
        context.assertTrue(net.askcraft.justifylasers.industry.IndustryRecipes.fuelTicks(new ItemStack(Items.COAL)) == 1600, "Coal duration preserved");
        var kind = net.askcraft.justifylasers.industry.MachineKind.FUEL_GENERATOR;
        context.setBlockState(EMITTER, net.askcraft.justifylasers.registry.ModIndustry.MACHINES.get(kind).getDefaultState());
        var generator = (net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity) context.getBlockEntity(EMITTER);
        var nbt = generator.createNbt(); nbt.remove("FuelBalanceRevision"); nbt.putInt("Fuel", 12_000); nbt.putInt("FuelTotal", 20_000);
        nbt.putInt("FuelMaxTemperature", 12_000); nbt.putInt("FuelEfficiency", 95);
        generator.readNbt(nbt);
        context.assertTrue(generator.fuel() == 4000 && generator.fuelTotal() == 20_000 / 3, "Loaded lava remainder migrates once");
        generator.readNbt(generator.createNbt());
        context.assertTrue(generator.fuel() == 4000, "No repeated migration");
        context.removeBlock(EMITTER); context.complete();
    }

    private static LaserBeamPath trace(Source source) { return LaserBeamPath.trace(source.world, source, 1, new HashMap<>(), new HashMap<>(), new HashMap<>()); }
    private record Source(World world, BlockPos pos, BeamBehavior behavior, long flux, long cost) implements LaserBeamSource {
        public World beamWorld() { return world; } public BlockPos beamPosition() { return pos; }
        public Vec3d beamOrigin() { return Vec3d.ofCenter(pos).add(.499, 0, 0); } public Vec3d beamDirection() { return new Vec3d(1, 0, 0); }
        public int beamRgb() { return 0xFFFFFF; } public int getBeamRange() { return 10; } public float getBeamWidthScale() { return 1; }
        public long getTicks() { return world.getTime(); } public boolean isBeamActive() { return true; }
        public boolean isLightEmissionEnabled() { return false; } public boolean showsScorchMarks() { return false; }
        public long luminousFlux() { return flux; } public long opticalBudget() { return flux; } public long moduleFluxCost() { return cost; }
        public BeamBehavior beamBehavior() { return behavior; }
    }
}
