package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.LaserPartBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserModule;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import team.reborn.energy.api.EnergyStorage;

import java.util.List;

public class LaserOpticsGameTests implements FabricGameTest {
    private static final BlockPos SOURCE = new BlockPos(1, 3, 3);
    private static final BlockPos OPTIC = new BlockPos(4, 3, 3);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void crystalsRecolorOnlyTheirOutgoingSegmentAndDoNotTunnelThroughWalls(TestContext context) {
        var emitter = emitter(context);
        var crystal = ModLaserParts.DECORATIONS.get("blue_crystal");
        context.setBlockState(OPTIC, crystal.getDefaultState());
        BlockPos wall = OPTIC.east();
        context.setBlockState(wall, Blocks.STONE);
        for (boolean mixing : new boolean[]{false, true}) {
            context.setBlockState(OPTIC, crystal.getDefaultState().with(LaserPartBlock.MIXING, mixing));
            tick(emitter);
            var path = LaserBeamNetwork.path(emitter, 1);
            context.assertTrue(path.segments().size() == 2, "Crystal splits the route at its surface");
            context.assertTrue(path.segments().get(0).rgb() == LaserColor.RED.rgb(), "Input remains red");
            int expected = mixing ? OpticalGeometry.mix(LaserColor.RED.rgb(), LaserColor.BLUE.rgb()) : LaserColor.BLUE.rgb();
            context.assertTrue(path.last().rgb() == expected, "Output uses crystal mixing mode");
            context.assertTrue(context.getAbsolutePos(wall).equals(path.last().hitBlock()), "Adjacent wall still stops the ray");
        }
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mirrorUsesItsActualNormalAndRespectsRemainingRange(TestContext context) {
        var emitter = emitter(context);
        context.setBlockState(OPTIC, ModBlocks.LASER_MIRROR.getDefaultState().with(LaserOpticBlock.FACING, Direction.UP));
        var mirror = (LaserOpticBlockEntity) context.getBlockEntity(OPTIC);
        mirror.aim(new Vec3d(1, 0, -1));
        context.setBlockState(OPTIC.south(3), Blocks.STONE);
        tick(emitter);
        ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(context.getWorld());
        var path = LaserBeamNetwork.path(emitter, 1);
        context.assertTrue(path.segments().size() == 2, "Mirror has input and reflected output");
        context.assertTrue(path.last().axis().dotProduct(new Vec3d(0, 0, 1)) > 0.9999, "45-degree mirror turns east into south");
        context.assertTrue(path.last().hitBlock().equals(context.getAbsolutePos(OPTIC.south(3))), "Reflection hits the south wall");
        context.assertTrue(path.segments().stream().mapToDouble(LaserBeamTrace::length).sum() <= emitter.getBeamRange(), "No extra range");
        context.assertTrue(mirror.getCachedState().get(LaserOpticBlock.LIT) && mirror.rgb() == LaserColor.RED.rgb(),
                "Mirror frame follows the reflected beam color");
        var saved = mirror.createNbt();
        mirror.aim(new Vec3d(1, 0, 0));
        mirror.readNbt(saved);
        context.assertTrue(mirror.normal().squaredDistanceTo(new Vec3d(1, 0, -1).normalize()) < 1e-8, "Normal survives save/load");
        context.removeBlock(SOURCE);
        ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(context.getWorld());
        context.assertTrue(!mirror.getCachedState().get(LaserOpticBlock.LIT) && !mirror.emitsShaderLight(),
                "Mirror frame turns off when its beam disappears");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void splitterMakesThreeWeakerOutputsAndNeverMultipliesPower(TestContext context) {
        var emitter = emitter(context);
        context.setBlockState(OPTIC, ModBlocks.BEAM_SPLITTER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
        threeOutputs(context);
        context.setBlockState(OPTIC.east(2), Blocks.STONE);
        context.setBlockState(OPTIC.north(2), Blocks.STONE);
        context.setBlockState(OPTIC.south(2), Blocks.STONE);
        tick(emitter);
        var path = LaserBeamNetwork.path(emitter, 1);
        var outputs = path.segments().stream().filter(LaserBeamTrace::hasBlockHit).toList();
        context.assertTrue(outputs.size() == 3, "Exactly three terminal outputs");
        context.assertTrue(Math.abs(outputs.stream().mapToDouble(LaserBeamTrace::power).sum() - 1) < 1e-9, "Power conserved");
        context.assertTrue(outputs.stream().allMatch(ray -> Math.abs(ray.power() - 1.0 / 3) < 1e-9), "Even power split");
        context.assertTrue(emitter.transferBudget() == 0, "Creative source cannot generate FE");
        context.removeBlock(SOURCE);
        context.complete();
    }

    private static LaserEmitterBlockEntity emitter(TestContext context) {
        context.setBlockState(SOURCE, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        var emitter = (LaserEmitterBlockEntity) context.getBlockEntity(SOURCE);
        emitter.getPropertyDelegate().set(13, 10);
        return emitter;
    }

    private static void threeOutputs(TestContext context) {
        var splitter = (LaserOpticBlockEntity) context.getBlockEntity(OPTIC);
        splitter.setPortMode(Direction.UP, OpticPortMode.DISABLED);
        splitter.setPortMode(Direction.DOWN, OpticPortMode.DISABLED);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sixPortsIncludeVerticalOutputsAndConservePower(TestContext context) {
        var emitter = emitter(context);
        context.setBlockState(OPTIC, ModBlocks.BEAM_SPLITTER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
        var splitter = (LaserOpticBlockEntity) context.getBlockEntity(OPTIC);
        context.assertTrue(splitter.outputPorts().size() == 5, "New splitter starts with five outputs");
        for (Direction side : splitter.outputPorts()) context.setBlockState(OPTIC.offset(side, 2), Blocks.STONE);
        tick(emitter);
        var outputs = LaserBeamNetwork.path(emitter, 1).segments().stream().filter(LaserBeamTrace::hasBlockHit).toList();
        context.assertTrue(outputs.size() == 5, "All five exits reach a wall, including top and bottom");
        context.assertTrue(outputs.stream().allMatch(ray -> Math.abs(ray.power() - 0.2) < 1e-9), "Five equal shares, no extra power");
        splitter.setPortMode(Direction.UP, OpticPortMode.DISABLED);
        splitter.setPortMode(Direction.DOWN, OpticPortMode.DISABLED);
        tick(emitter);
        outputs = LaserBeamNetwork.path(emitter, 1).segments().stream().filter(LaserBeamTrace::hasBlockHit).toList();
        context.assertTrue(outputs.size() == 3 && outputs.stream().allMatch(ray -> Math.abs(ray.power() - 1.0 / 3) < 1e-9),
                "Disabling outputs immediately redistributes power");
        for (Direction side : Direction.values()) splitter.setPortMode(side, OpticPortMode.INPUT);
        tick(emitter);
        context.assertTrue(LaserBeamNetwork.path(emitter, 1).segments().size() == 1, "An input-only splitter absorbs without inventing an exit");
        splitter.setPortMode(Direction.WEST, OpticPortMode.DISABLED);
        tick(emitter);
        context.assertTrue(context.getAbsolutePos(OPTIC).equals(LaserBeamNetwork.path(emitter, 1).last().hitBlock()),
                "Disabled input blocks the beam");
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anyFaceCanBeAnInputAndRouteToAnyOtherFace(TestContext context) {
        BlockPos center = new BlockPos(4, 4, 4);
        context.setBlockState(center, ModBlocks.BEAM_SPLITTER);
        var splitter = (LaserOpticBlockEntity) context.getBlockEntity(center);
        for (Direction input : Direction.values()) {
            for (Direction output : Direction.values()) {
                if (input == output) continue;
                for (Direction side : Direction.values()) splitter.setPortMode(side, OpticPortMode.DISABLED);
                splitter.setPortMode(input, OpticPortMode.INPUT);
                splitter.setPortMode(output, OpticPortMode.OUTPUT);
                BlockPos sourcePos = center.offset(input, 3), wall = center.offset(output, 2);
                context.setBlockState(sourcePos, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, input.getOpposite()));
                context.setBlockState(wall, Blocks.STONE);
                var emitter = (LaserEmitterBlockEntity) context.getBlockEntity(sourcePos);
                emitter.getPropertyDelegate().set(13, 10);
                tick(emitter);
                var path = LaserBeamNetwork.path(emitter, 1);
                context.assertTrue(path.segments().size() == 2 && context.getAbsolutePos(wall).equals(path.last().hitBlock()),
                        "Remapped route " + input + " -> " + output);
                context.assertTrue(path.last().power() == 1, "Single output preserves the input's power");
                context.removeBlock(sourcePos);
                context.removeBlock(wall);
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void portModesSurviveSaveLoadAndOldSplittersKeepTheirLayout(TestContext context) {
        context.setBlockState(OPTIC, ModBlocks.BEAM_SPLITTER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
        var splitter = (LaserOpticBlockEntity) context.getBlockEntity(OPTIC);
        splitter.setPortMode(Direction.UP, OpticPortMode.INPUT);
        splitter.setPortMode(Direction.EAST, OpticPortMode.DISABLED);
        var saved = splitter.createNbt();
        splitter.setPortMode(Direction.UP, OpticPortMode.OUTPUT);
        splitter.readNbt(saved);
        context.assertTrue(splitter.portMode(Direction.UP) == OpticPortMode.INPUT && splitter.portMode(Direction.EAST) == OpticPortMode.DISABLED,
                "All modes survive serialization");
        saved.remove("Ports");
        splitter.readNbt(saved);
        context.assertTrue(splitter.portMode(Direction.WEST) == OpticPortMode.INPUT && splitter.outputPorts().size() == 3
                && splitter.portMode(Direction.UP) == OpticPortMode.DISABLED && splitter.portMode(Direction.DOWN) == OpticPortMode.DISABLED,
                "Alpha.7 horizontal splitters retain their previous layout");
        saved.putIntArray("Ports", new int[]{-1, 99});
        splitter.readNbt(saved);
        for (Direction side : Direction.values()) context.assertTrue(splitter.portMode(side) == OpticPortMode.DISABLED,
                "Invalid or missing mode fails closed: " + side);
        context.setBlockState(OPTIC, ModBlocks.BEAM_SPLITTER.getDefaultState().with(LaserOpticBlock.FACING, Direction.UP));
        splitter = (LaserOpticBlockEntity) context.getBlockEntity(OPTIC);
        saved.remove("Ports");
        splitter.readNbt(saved);
        context.assertTrue(splitter.portMode(Direction.UP) == OpticPortMode.INPUT && splitter.outputPorts().size() == 3
                && splitter.portMode(Direction.NORTH) == OpticPortMode.DISABLED && splitter.portMode(Direction.SOUTH) == OpticPortMode.DISABLED,
                "Alpha.7 vertical splitters retain their previous layout");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "optics_energy")
    public void disabledReceiverPortsRejectEvenCachedCableConnections(TestContext context) {
        boolean previous = LaserConfig.technicalMode();
        try {
            LaserConfig.applyServerMode(true);
            context.setBlockState(OPTIC, ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
            var receiver = (LaserOpticBlockEntity) context.getBlockEntity(OPTIC);
            receiver.energy().restore(1000);
            for (Direction side : Direction.values()) {
                receiver.setPortMode(side, OpticPortMode.OUTPUT);
                var cached = EnergyStorage.SIDED.find(context.getWorld(), receiver.getPos(), side);
                context.assertTrue(cached != null && cached.supportsExtraction(), "Enabled output exposes native storage on " + side);
                for (OpticPortMode mode : new OpticPortMode[]{OpticPortMode.DISABLED, OpticPortMode.INPUT}) {
                    receiver.setPortMode(side, mode);
                    context.assertTrue(EnergyStorage.SIDED.find(context.getWorld(), receiver.getPos(), side) == null,
                            "Closed or optical side has no native FE port");
                    try (var transaction = Transaction.openOuter()) {
                        context.assertTrue(cached.extract(100, transaction) == 0 && !cached.supportsExtraction(), "Cached cable cannot bypass " + mode);
                        transaction.commit();
                    }
                    context.assertTrue(receiver.energy().stored() == 1000, "Buffer remains unchanged");
                }
                receiver.setPortMode(side, OpticPortMode.OUTPUT);
                context.assertTrue(cached.supportsExtraction(), "Reopening re-enables the same side-bound storage");
            }
            context.assertTrue(EnergyStorage.SIDED.find(context.getWorld(), receiver.getPos(), null) == null, "Unsided lookup cannot bypass port rules");
            context.complete();
        } finally { LaserConfig.applyServerMode(previous); }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 30)
    public void receiverColorAndEmissionFollowTheBeamNotItsStoredEnergy(TestContext context) {
        var emitter = emitter(context);
        context.setBlockState(OPTIC, ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
        var receiver = (LaserOpticBlockEntity) context.getBlockEntity(OPTIC);
        receiver.energy().restore(1234);
        context.runAtTick(3, () -> {
            context.assertTrue(receiver.rgb() == LaserColor.RED.rgb() && receiver.emitsShaderLight(), "Creative beam lights the input without producing energy");
            context.assertTrue(receiver.energy().stored() == 1234, "Color tracking cannot generate FE");
            receiver.setPortMode(Direction.WEST, OpticPortMode.DISABLED);
        });
        context.runAtTick(6, () -> {
            context.assertTrue(receiver.rgb() == LaserOpticBlockEntity.IDLE_COLOR && !receiver.emitsShaderLight(), "Disabled input is gray and unlit despite a full buffer");
            receiver.setPortMode(Direction.WEST, OpticPortMode.INPUT);
            emitter.getPropertyDelegate().set(2, LaserColor.BLUE.ordinal());
        });
        context.runAtTick(9, () -> {
            context.assertTrue(receiver.rgb() == LaserColor.BLUE.rgb() && receiver.emitsShaderLight(), "New color propagates after reopening input");
            emitter.getPropertyDelegate().set(0, 0);
        });
        context.runAtTick(12, () -> {
            context.assertTrue(receiver.rgb() == LaserOpticBlockEntity.IDLE_COLOR && !receiver.emitsShaderLight(), "Stopped beam clears all emission");
            context.removeBlock(SOURCE);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void splitDamageAndBurningTimeAreSharedBetweenOutputs(TestContext context) {
        var emitter = emitter(context);
        emitter.getPropertyDelegate().set(9, 30);
        emitter.getPropertyDelegate().set(12, 1);
        emitter.getPropertyDelegate().set(4, 1);
        context.setBlockState(OPTIC, ModBlocks.BEAM_SPLITTER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
        threeOutputs(context);
        var targets = List.of(
                context.spawnMob(EntityType.VILLAGER, new Vec3d(6.5, 2.5, 3.5)),
                context.spawnMob(EntityType.VILLAGER, new Vec3d(4.5, 2.5, 1.5)),
                context.spawnMob(EntityType.VILLAGER, new Vec3d(4.5, 2.5, 5.5)));
        targets.forEach(target -> { target.setAiDisabled(true); target.setNoGravity(true); });
        tick(emitter);
        float lost = 0;
        int fireTicks = 0;
        double totalPush = 0;
        for (var target : targets) {
            context.assertTrue(Math.abs(target.getHealth() - 19) < 0.001, "Each output deals one third of the configured 3 HP");
            lost += 20 - target.getHealth();
            fireTicks += target.getFireTicks();
            totalPush += target.getVelocity().length();
            target.discard();
        }
        context.assertTrue(Math.abs(lost - 3) < 0.001 && fireTicks <= 80, "Damage and new burning time are not multiplied");
        context.assertTrue(Math.abs(totalPush - 0.025) < 1e-8, "The default knockback impulse is shared, not tripled");
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void splitMiningSharesHeatAndKeepsTheOneBlockPerWorldTickLimit(TestContext context) {
        var emitter = emitter(context);
        emitter.getPropertyDelegate().set(3, 1);
        emitter.getPropertyDelegate().set(14, LaserMining.MAX_SPEED_STEP);
        context.setBlockState(OPTIC, ModBlocks.BEAM_SPLITTER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
        threeOutputs(context);
        var targets = List.of(OPTIC.east(2), OPTIC.north(2), OPTIC.south(2));
        targets.forEach(pos -> context.setBlockState(pos, Blocks.STONE));
        for (int i = 0; i < 2; i++) {
            tick(emitter);
            context.assertTrue(targets.stream().allMatch(pos -> context.getBlockState(pos).isOf(Blocks.STONE)),
                    "One-third branches cannot mine a full block in fewer than three ticks");
        }
        tick(emitter);
        context.assertTrue(targets.stream().filter(pos -> context.getBlockState(pos).isAir()).count() == 1,
                "Only one branch may finish mining in a world tick");
        tick(emitter);
        context.assertTrue(targets.stream().filter(pos -> context.getBlockState(pos).isAir()).count() == 1,
                "Repeated accelerated calls cannot bypass the shared mining limit");
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "optics_energy", tickLimit = 40)
    public void paidTransmissionLosesEnergyStopsAtObstaclesAndCannotUseCreativePower(TestContext context) {
        boolean previousMode = LaserConfig.technicalMode();
        LaserConfig.applyServerMode(true);
        var source = powered(context);
        context.setBlockState(OPTIC, ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
        var receiver = (LaserOpticBlockEntity) context.getBlockEntity(OPTIC);
        int[] frozen = {0};
        context.runAtTick(3, () -> {
            int paid = 100_000 - source.energy().stored();
            context.assertTrue(receiver.energy().stored() > 0 && receiver.energy().stored() <= paid * LaserConfig.get().energyTransmissionEfficiency,
                    "Only paid source energy is recovered, with loss");
            context.assertTrue(EnergyStorage.SIDED.find(context.getWorld(), receiver.getPos(), Direction.WEST) == null,
                    "The optical input face is not an energy cable port");
            var port = EnergyStorage.SIDED.find(context.getWorld(), receiver.getPos(), Direction.EAST);
            context.assertTrue(port != null && port.supportsExtraction() && !port.supportsInsertion(), "Receiver port exports, never imports");
            int before = receiver.energy().stored();
            try (var transaction = Transaction.openOuter()) {
                context.assertTrue(port.extract(10, transaction) == 10, "Energy is extractable");
            }
            context.assertTrue(receiver.energy().stored() == before, "Aborted extraction restores the buffer");
            context.setBlockState(OPTIC.west(), Blocks.OBSIDIAN);
            frozen[0] = before;
        });
        context.runAtTick(6, () -> {
            context.assertTrue(receiver.energy().stored() == frozen[0], "A solid obstacle prevents any new transfer");
            context.setBlockState(OPTIC.west(), Blocks.AIR);
            context.setBlockState(SOURCE, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
            var creative = (LaserEmitterBlockEntity) context.getBlockEntity(SOURCE);
            creative.getPropertyDelegate().set(13, 10);
        });
        context.runAtTick(9, () -> {
            context.assertTrue(receiver.energy().stored() == frozen[0], "Creative emitters cannot generate energy");
            var splitSource = powered(context);
            context.setBlockState(OPTIC, ModBlocks.BEAM_SPLITTER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
            context.setBlockState(OPTIC.east(2), ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
            context.setBlockState(OPTIC.north(2), ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, Direction.SOUTH));
            context.setBlockState(OPTIC.south(2), ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, Direction.NORTH));
            context.runAtTick(12, () -> {
                try {
                    var receivers = List.of(OPTIC.east(2), OPTIC.north(2), OPTIC.south(2)).stream()
                            .map(pos -> (LaserOpticBlockEntity) context.getBlockEntity(pos)).toList();
                    int total = receivers.stream().mapToInt(optic -> optic.energy().stored()).sum();
                    int paid = 100_000 - splitSource.energy().stored();
                    context.assertTrue(receivers.stream().allMatch(optic -> optic.energy().stored() > 0), "All three receivers receive energy");
                    context.assertTrue(total <= paid * LaserConfig.get().energyTransmissionEfficiency, "Splitting cannot multiply recovered FE");
                    ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(context.getWorld());
                    int once = receivers.stream().mapToInt(optic -> optic.energy().stored()).sum();
                    ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(context.getWorld());
                    context.assertTrue(receivers.stream().mapToInt(optic -> optic.energy().stored()).sum() == once, "No duplicate transfer in the same world tick");
                    var sinkPos = OPTIC.east(3);
                    context.setBlockState(sinkPos, ModBlocks.POWERED_LASER_EMITTER);
                    var sink = (LaserEmitterBlockEntity) context.getBlockEntity(sinkPos);
                    var output = receivers.get(0);
                    int stored = output.energy().stored();
                    LaserOpticBlockEntity.serverTick(context.getWorld(), output.getPos(), output.getCachedState(), output);
                    context.assertTrue(sink.energy().stored() == stored && output.energy().stored() == 0, "Output port transfers to a compatible adjacent machine");
                    context.removeBlock(SOURCE);
                    context.complete();
                } finally { LaserConfig.applyServerMode(previousMode); }
            });
        });
    }

    private static LaserEmitterBlockEntity powered(TestContext context) {
        context.setBlockState(SOURCE, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        var emitter = (LaserEmitterBlockEntity) context.getBlockEntity(SOURCE);
        emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
        emitter.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 2));
        emitter.energy().restore(100_000);
        return emitter;
    }

    private static void tick(LaserEmitterBlockEntity emitter) {
        LaserEmitterBlockEntity.serverTick(emitter.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
    }
}
