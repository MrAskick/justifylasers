package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class BeamCombinerGameTests implements FabricGameTest {
    private static final BlockPos CENTER = new BlockPos(4, 3, 3), A = new BlockPos(1, 3, 3), B = new BlockPos(4, 3, 1);

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "combiner_energy", tickLimit = 20)
    public void touchingChainSumsNewInputsAndDeliversFluxThroughATurn(TestContext context) {
        var red = powered(context, A, Direction.EAST, LaserColor.RED);
        var blue = powered(context, B, Direction.SOUTH, LaserColor.BLUE);
        optic(context, CENTER, ModBlocks.BEAM_COMBINER, Direction.EAST);
        optic(context, CENTER.east(), ModBlocks.BEAM_COMBINER, Direction.SOUTH);
        var greenPos = CENTER.east(3);
        var green = powered(context, greenPos, Direction.WEST, LaserColor.GREEN);
        var sink = optic(context, CENTER.east().south(), ModBlocks.ENERGY_RECEIVER, Direction.NORTH);
        context.runAtTick(4, () -> {
            long expected = LuminousFlux.share(red.luminousFlux(), .95 * .95)
                    + LuminousFlux.share(blue.luminousFlux(), .95 * .95) + LuminousFlux.share(green.luminousFlux(), .95);
            context.assertTrue(sink.lastFlux() == expected, "Touching combiners sum every contribution with one loss per traversed combiner: "
                    + sink.lastFlux() + " / " + expected + "; red=" + LaserBeamNetwork.path(red, 1).last()
                    + "; blue=" + LaserBeamNetwork.path(blue, 1).last() + "; green=" + LaserBeamNetwork.path(green, 1).last());
            context.assertTrue(sink.lastInput() == LuminousFlux.toEnergyRate(expected, LaserConfig.get().lumensPerEnergyUnit,
                    LaserConfig.get().energyTransmissionEfficiency), "The adjacent receiver converts the summed flux, not separate rounded shares");
            context.removeBlock(A); context.removeBlock(B); context.removeBlock(greenPos); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "combiner_energy", tickLimit = 25)
    public void touchingCombinersAcceptTheSharedFaceInAllSixDirections(TestContext context) {
        var first = optic(context, CENTER, ModBlocks.BEAM_COMBINER, Direction.EAST);
        context.runAtTick(3, () -> {
            for (Direction facing : Direction.values()) {
                var active = facing == Direction.WEST ? powered(context, B, Direction.SOUTH, LaserColor.RED)
                        : powered(context, A, Direction.EAST, LaserColor.RED);
                first.setPortMode(facing, OpticPortMode.OUTPUT);
                var second = optic(context, CENTER.offset(facing), ModBlocks.BEAM_COMBINER, facing);
                var sink = optic(context, CENTER.offset(facing, 2), ModBlocks.ENERGY_RECEIVER, facing.getOpposite());
                tick(active);
                var ray = LaserBeamNetwork.path(active, 1).last();
                context.assertTrue(sink.getPos().equals(ray.hitBlock()), "Touching chain reaches sink toward " + facing + ": " + ray);
                context.assertTrue(Math.abs(ray.power() - .95 * .95) < 1e-9, "Both losses apply exactly once");
                second.setPortMode(facing.getOpposite(), OpticPortMode.DISABLED);
                tick(active);
                context.assertTrue(second.getPos().equals(LaserBeamNetwork.path(active, 1).last().hitBlock()), "Disabled adjacent input still stops light");
                context.removeBlock(CENTER.offset(facing)); context.removeBlock(CENTER.offset(facing, 2));
                context.removeBlock(A); context.removeBlock(B);
            }
            context.removeBlock(A); context.removeBlock(B); context.complete();
        });
    }


    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oneOutputInvariantAndDisabledFacesSurviveNbt(TestContext context) {
        var combiner = optic(context, CENTER, ModBlocks.BEAM_COMBINER, Direction.EAST);
        context.assertTrue(combiner.outputPorts().equals(List.of(Direction.EAST)), "New combiner has one output");
        for (Direction side : Direction.values()) if (side != Direction.EAST)
            context.assertTrue(combiner.portMode(side) == OpticPortMode.INPUT, "Other five faces are inputs");
        combiner.setPortMode(Direction.UP, OpticPortMode.OUTPUT);
        context.assertTrue(combiner.outputPorts().equals(List.of(Direction.UP)) && combiner.portMode(Direction.EAST) == OpticPortMode.INPUT,
                "Reassignment cannot duplicate the output");
        combiner.setPortMode(Direction.WEST, OpticPortMode.DISABLED);
        var restored = new LaserOpticBlockEntity(combiner.getPos(), combiner.getCachedState()); restored.readNbt(combiner.createNbt());
        context.assertTrue(restored.outputPorts().equals(List.of(Direction.UP)) && restored.portMode(Direction.WEST) == OpticPortMode.DISABLED, "Ports survive reload");
        var malformed = combiner.createNbt(); malformed.putIntArray("Ports", new int[]{1, 1, 1, 1, 1, 1});
        restored.readNbt(malformed);
        context.assertTrue(restored.outputPorts().size() <= 1, "Malformed NBT cannot create multiple outputs");
        var emitter = powered(context, A, Direction.EAST, LaserColor.RED);
        tick(emitter);
        context.assertTrue(LaserBeamNetwork.path(emitter, 1).last().hitBlock().equals(context.getAbsolutePos(CENTER)), "Disabled face absorbs");
        combiner.setPortMode(Direction.WEST, OpticPortMode.INPUT); combiner.setPortMode(Direction.UP, OpticPortMode.DISABLED);
        tick(emitter);
        context.assertTrue(LaserBeamNetwork.path(emitter, 1).segments().size() == 1, "No output means no forwarded beam");
        context.removeBlock(A); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "combiner_energy", tickLimit = 40)
    public void twoSourcesSumFluxOnceWithLossAndStopWithoutGhostEnergy(TestContext context) {
        var first = powered(context, A, Direction.EAST, LaserColor.RED);
        var second = powered(context, B, Direction.SOUTH, LaserColor.BLUE);
        var combiner = optic(context, CENTER, ModBlocks.BEAM_COMBINER, Direction.EAST);
        var target = CENTER.east(3);
        var receiver = optic(context, target, ModBlocks.ENERGY_RECEIVER, Direction.WEST);
        context.runAtTick(4, () -> {
            long incoming = first.luminousFlux() + second.luminousFlux();
            long expected = LuminousFlux.share(first.luminousFlux(), combiner.combiningEfficiency()) + LuminousFlux.share(second.luminousFlux(), combiner.combiningEfficiency());
            context.assertTrue(receiver.lastFlux() == expected && expected < incoming, "Both budgets merge with one loss: " + receiver.lastFlux() + " / " + expected);
            int recovered = LuminousFlux.toEnergyRate(expected, LaserConfig.get().lumensPerEnergyUnit, LaserConfig.get().energyTransmissionEfficiency);
            context.assertTrue(receiver.lastInput() == recovered, "FE is converted after summation");
            context.assertTrue((combiner.rgb() >> 16 & 255) > 180 && (combiner.rgb() & 255) > 180, "Combiner mixes red and blue");
            var mixed = new LightMixture(); mixed.add(first.beamRgb(), first.luminousFlux()); mixed.add(second.beamRgb(), second.luminousFlux());
            context.assertTrue(LaserBeamNetwork.receivedColor(context.getWorld(), receiver.getPos(), Direction.WEST) == LaserColor.nearest(mixed.rgb()),
                    "Redstone receivers detect the actual mixed color, not the first source");
            for (var source : List.of(first, second)) {
                var ray = LaserBeamNetwork.path(source, 1).last();
                context.assertTrue(context.getAbsolutePos(target).equals(ray.hitBlock()), "Each contribution reaches the same terminal");
                context.assertTrue(Math.abs(ray.power() - .95) < 1e-9, "Exactly 5 percent loss per combiner");
                context.assertTrue(ray.combinedBy() != null, "Combined output is rendered as one beam");
            }
            ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(context.getWorld());
            int once = receiver.energy().stored();
            ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(context.getWorld());
            context.assertTrue(once == receiver.energy().stored(), "No repeated delivery in the same tick");
            context.removeBlock(A); context.removeBlock(B);
            context.runAtTick(7, () -> {
                int stopped = receiver.energy().stored();
                context.assertTrue(receiver.lastFlux() == 0 && !combiner.getCachedState().get(LaserOpticBlock.LIT), "No cached flux or lit combiner after disconnect");
                context.runAtTick(10, () -> {
                    context.assertTrue(receiver.energy().stored() == stopped, "No ghost energy");
                    context.complete();
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "combiner_energy", tickLimit = 30)
    public void mergedContributionsTravelTogetherThroughARefocusingCube(TestContext context) {
        var first = powered(context, A, Direction.EAST, LaserColor.RED);
        var second = powered(context, B, Direction.SOUTH, LaserColor.BLUE);
        optic(context, CENTER, ModBlocks.BEAM_COMBINER, Direction.EAST);
        var cube = new RefocusingCubeEntity(ModEntities.REFOCUSING_CUBE, context.getWorld());
        cube.orient(0, 0); cube.prevYaw = 0; cube.prevPitch = 0;
        cube.setPosition(context.getAbsolute(new Vec3d(6.5, 3.05, 3.5))); cube.setNoGravity(true); context.getWorld().spawnEntity(cube);
        var target = new BlockPos(6, 3, 6);
        var receiver = optic(context, target, ModBlocks.ENERGY_RECEIVER, Direction.NORTH);
        context.runAtTick(4, () -> {
            for (var source : List.of(first, second)) context.assertTrue(context.getAbsolutePos(target).equals(LaserBeamNetwork.path(source, 1).last().hitBlock()),
                    "Cube must pass all contributions of the merged beam");
            long expected = LuminousFlux.share(first.luminousFlux(), .95) + LuminousFlux.share(second.luminousFlux(), .95);
            context.assertTrue(receiver.lastFlux() == expected, "Cube preserves the whole combined flux");
            context.removeBlock(A); context.removeBlock(B); cube.discard(); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void closedOpticalLoopTerminatesAndCannotRenewRange(TestContext context) {
        var source = powered(context, A, Direction.EAST, LaserColor.RED);
        var entrance = optic(context, new BlockPos(3, 3, 3), ModBlocks.BEAM_SPLITTER, Direction.WEST);
        for (Direction side : Direction.values()) entrance.setPortMode(side, OpticPortMode.DISABLED);
        entrance.setPortMode(Direction.WEST, OpticPortMode.INPUT); entrance.setPortMode(Direction.SOUTH, OpticPortMode.INPUT);
        entrance.setPortMode(Direction.EAST, OpticPortMode.OUTPUT);
        optic(context, new BlockPos(5, 3, 3), ModBlocks.BEAM_COMBINER, Direction.SOUTH);
        optic(context, new BlockPos(5, 3, 5), ModBlocks.BEAM_COMBINER, Direction.WEST);
        optic(context, new BlockPos(3, 3, 5), ModBlocks.BEAM_COMBINER, Direction.NORTH);
        tick(source);
        var path = LaserBeamNetwork.path(source, 1);
        context.assertTrue(path.segments().size() == 5, "Loop terminates at the first repeated optic, not the segment cap: " + path.segments().size());
        context.assertTrue(path.segments().stream().mapToDouble(LaserBeamTrace::length).sum() < source.getBeamRange(), "No reset of source range");
        context.assertTrue(path.segments().stream().noneMatch(LaserBeamTrace::hasBlockHit), "A closed loop cannot invent a terminal energy sink");
        context.removeBlock(A); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void verticalOutputAndCrystalRecoloringPreserveContribution(TestContext context) {
        var emitter = powered(context, A, Direction.EAST, LaserColor.RED);
        var combiner = optic(context, CENTER, ModBlocks.BEAM_COMBINER, Direction.UP);
        context.setBlockState(CENTER.up(2), ModLaserParts.DECORATIONS.get("green_crystal"));
        context.setBlockState(CENTER.up(3), Blocks.STONE);
        tick(emitter);
        var path = LaserBeamNetwork.path(emitter, 1);
        context.assertTrue(path.last().rgb() == LaserColor.GREEN.rgb() && path.last().power() == combiner.combiningEfficiency(), "Crystal changes color without restoring lost power");
        context.assertTrue(path.last().hitBlock().equals(context.getAbsolutePos(CENTER.up(3))), "Vertical output cannot tunnel through the next wall");
        context.removeBlock(A); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "combiner_energy", tickLimit = 30)
    public void splitBranchesRejoinWithoutLosingHalfTheDamageOrDoublingEnergy(TestContext context) {
        var source = powered(context, A, Direction.EAST, LaserColor.RED);
        var splitter = optic(context, CENTER, ModBlocks.BEAM_SPLITTER, Direction.WEST);
        for (Direction side : Direction.values()) splitter.setPortMode(side, OpticPortMode.DISABLED);
        splitter.setPortMode(Direction.WEST, OpticPortMode.INPUT);
        splitter.setPortMode(Direction.NORTH, OpticPortMode.OUTPUT); splitter.setPortMode(Direction.SOUTH, OpticPortMode.OUTPUT);
        for (int z : new int[]{1, 5}) {
            optic(context, new BlockPos(4, 3, z), ModBlocks.LASER_MIRROR, Direction.UP).aim(new Vec3d(1, 0, z == 1 ? 1 : -1));
            optic(context, new BlockPos(6, 3, z), ModBlocks.LASER_MIRROR, Direction.UP).aim(new Vec3d(1, 0, z == 1 ? -1 : 1));
        }
        optic(context, new BlockPos(6, 3, 3), ModBlocks.BEAM_COMBINER, Direction.EAST);
        var receiver = optic(context, new BlockPos(8, 3, 3), ModBlocks.ENERGY_RECEIVER, Direction.WEST);
        context.runAtTick(4, () -> {
            var terminals = LaserBeamNetwork.path(source, 1).segments().stream().filter(LaserBeamTrace::hasBlockHit).toList();
            context.assertTrue(terminals.size() == 1 && Math.abs(terminals.get(0).power() - .95) < 1e-9,
                    "Rejoined damage/mining uses one summed fraction: " + terminals);
            context.assertTrue(receiver.lastFlux() == LuminousFlux.share(source.luminousFlux(), .95), "One paid budget, minus combiner loss");
            context.removeBlock(A); context.complete();
        });
    }

    private static LaserOpticBlockEntity optic(TestContext context, BlockPos pos, LaserOpticBlock block, Direction facing) {
        context.setBlockState(pos, block.getDefaultState().with(LaserOpticBlock.FACING, facing));
        return (LaserOpticBlockEntity) context.getBlockEntity(pos);
    }
    private static LaserEmitterBlockEntity powered(TestContext context, BlockPos pos, Direction facing, LaserColor color) {
        context.setBlockState(pos, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, facing));
        var source = (LaserEmitterBlockEntity) context.getBlockEntity(pos);
        source.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(color)));
        source.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 2));
        source.energy().restore(100_000);
        return source;
    }
    private static void tick(LaserEmitterBlockEntity source) {
        LaserEmitterBlockEntity.serverTick(source.getWorld(), source.getPos(), source.getCachedState(), source);
    }
}
