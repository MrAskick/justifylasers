package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class LaserScorchGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void geometryFitsAllSixFacesWithOutwardWindingAndSoftEdges(TestContext context) {
        BlockPos relative = new BlockPos(4, 4, 4);
        context.setBlockState(relative, Blocks.IRON_BLOCK);
        BlockPos absolute = context.getAbsolutePos(relative);
        for (Direction face : Direction.values()) {
            Vec3d normal = Vec3d.of(face.getVector());
            Vec3d point = Vec3d.ofCenter(absolute).add(normal.multiply(0.5D));
            List<ScorchGeometry.Patch> patches = ScorchGeometry.create(context.getWorld(), point, point, face, 0.2D);
            context.assertTrue(patches.size() == 1, "A dot must stay on its own " + face + " face");
            ScorchGeometry.Patch patch = patches.get(0);
            context.assertTrue(patch.position().equals(absolute), "Geometry retains its supporting block");
            for (List<ScorchGeometry.Vertex> vertices : List.of(patch.soot(), patch.heat(), patch.emission())) {
                context.assertFalse(vertices.isEmpty(), "Each material has geometry");
                context.assertTrue(vertices.size() % 4 == 0, "Complete quads");
                for (ScorchGeometry.Vertex vertex : vertices) {
                    Vec3d p = vertex.position();
                    close(context, point.subtract(Vec3d.of(absolute)).dotProduct(normal) + ScorchGeometry.SURFACE_OFFSET,
                            p.dotProduct(normal), "Vertices lie just outside the physical face");
                    for (Direction.Axis axis : Direction.Axis.values()) {
                        if (axis != face.getAxis()) {
                            double coordinate = p.getComponentAlongAxis(axis);
                            context.assertTrue(coordinate >= 0 && coordinate <= 1, "No floating edge geometry");
                        }
                    }
                }
                for (int i = 0; i < vertices.size(); i += 4) {
                    Vec3d a = vertices.get(i).position();
                    Vec3d b = vertices.get(i + 1).position();
                    Vec3d c = vertices.get(i + 2).position();
                    context.assertTrue(b.subtract(a).crossProduct(c.subtract(a)).dotProduct(normal) > 0,
                            "Face triangles point out, including mirrored axes");
                }
            }
            context.assertTrue(patch.soot().stream().anyMatch(v -> v.alpha() == 0), "Soot has transparent outer edges");
            context.assertTrue(patch.heat().stream().anyMatch(v -> v.alpha() == 0), "Hot edges fade to transparent");
            context.assertTrue(patch.heat().stream().anyMatch(v -> v.rgb() == 0xFFF4BD), "White-hot center remains");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aTrailClipsAtBlockEdgesAndDoesNotBridgeAir(TestContext context) {
        context.setBlockState(new BlockPos(2, 4, 4), Blocks.STONE);
        context.setBlockState(new BlockPos(4, 4, 4), Blocks.STONE);
        Vec3d a = context.getAbsolute(new Vec3d(2.5D, 4.5D, 4));
        Vec3d b = context.getAbsolute(new Vec3d(4.9D, 4.5D, 4));
        List<ScorchGeometry.Patch> patches = ScorchGeometry.create(context.getWorld(), a, b, Direction.NORTH, 0.3D);
        context.assertTrue(patches.size() == 2, "A hole must not receive a decal");
        for (ScorchGeometry.Patch patch : patches) {
            for (ScorchGeometry.Vertex vertex : patch.soot()) {
                context.assertTrue(vertex.position().x >= -1.0E-6D && vertex.position().x <= 1.000001D,
                        "The trail is clipped at both edges of each supporting block");
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void slabsUseTheirActualSurfaceInsteadOfAFullCube(TestContext context) {
        BlockPos relative = new BlockPos(4, 4, 4);
        context.setBlockState(relative, Blocks.STONE_SLAB);
        Vec3d top = context.getAbsolute(new Vec3d(4.5D, 4.5D, 4.5D));
        List<ScorchGeometry.Patch> patches = ScorchGeometry.create(context.getWorld(), top, top, Direction.UP, 0.2D);
        context.assertTrue(patches.size() == 1, "The half-height top receives the mark");
        for (ScorchGeometry.Vertex vertex : patches.get(0).soot()) {
            close(context, 0.5D + ScorchGeometry.SURFACE_OFFSET, vertex.position().y, "Slab surface height");
        }
        Vec3d empty = top.add(0, 0.5D, 0);
        context.assertTrue(ScorchGeometry.create(context.getWorld(), empty, empty, Direction.UP, 0.2D).isEmpty(),
                "No full-block-height ghost surface");
        Vec3d side = context.getAbsolute(new Vec3d(4.5D, 4.45D, 4));
        for (ScorchGeometry.Patch patch : ScorchGeometry.create(context.getWorld(), side, side, Direction.NORTH, 0.2D)) {
            context.assertTrue(patch.soot().stream().allMatch(v -> v.position().y <= 0.500001D),
                    "Side scorch must not extend above a slab");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void receiverInputIsProtectedFromScorchMarks(TestContext context) {
        context.setBlockState(new BlockPos(4, 4, 4), ModBlocks.LASER_RECEIVER.getDefaultState()
                .with(LaserReceiverBlock.FACING, Direction.NORTH));
        Vec3d a = context.getAbsolute(new Vec3d(4.5D, 4.5D, 2));
        LaserBeamTrace trace = LaserBeamTrace.traceFrom(context.getWorld(), a, Direction.SOUTH, 4);
        context.assertTrue(trace.hasBlockHit(), "Fixture hits the receiver lens");
        context.assertTrue(ScorchGeometry.create(context.getWorld(), trace.end(), trace.end(), trace.hitSide(), 0.2D).isEmpty(),
                "A functioning receiver lens must not become charred");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stationaryContactRefreshesOneMarkWithoutChangingBlocks(TestContext context) {
        BlockPos target = context.getAbsolutePos(new BlockPos(4, 4, 4));
        context.setBlockState(new BlockPos(4, 4, 4), Blocks.IRON_BLOCK);
        LaserEmitterBlockEntity emitter = emitter(context);
        LaserScorchMarks marks = new LaserScorchMarks();
        for (int tick = 0; tick < 100; tick++) marks.update(context.getWorld(), tick);
        List<LaserScorchMarks.Mark> matching = marksAt(marks, target);
        context.assertTrue(matching.size() == 1, "A stationary beam must retain exactly one mark, got " + matching.size());
        close(context, 0, matching.get(0).age(99), "Continuous contact remains incandescent");
        context.assertTrue(context.getWorld().getBlockState(target).isOf(Blocks.IRON_BLOCK), "Scorch is cosmetic");
        context.assertTrue(emitter.getPropertyDelegate().get(3) == 0, "Marks also work with block destruction disabled");
        context.removeBlock(new BlockPos(1, 4, 4));
        marks.clear();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void marksCoolExpireAndHonorEmissionWithoutWorldLighting(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context);
        context.setBlockState(new BlockPos(4, 4, 4), Blocks.STONE);
        BlockPos target = context.getAbsolutePos(new BlockPos(4, 4, 4));
        emitter.getPropertyDelegate().set(7, 0);
        emitter.getPropertyDelegate().set(8, 0);
        LaserScorchMarks marks = new LaserScorchMarks();
        marks.update(context.getWorld(), 0);
        context.assertFalse(marksAt(marks, target).get(0).emitsLight(), "Emission follows the emitter, not physical lighting");
        emitter.getPropertyDelegate().set(7, 1);
        marks.update(context.getWorld(), 1);
        context.assertTrue(marksAt(marks, target).get(0).emitsLight(), "Stationary contacts update their emission flag");
        context.removeBlock(new BlockPos(1, 4, 4));
        marks.update(context.getWorld(), 1 + LaserScorchMarks.COOLING_TICKS);
        List<LaserScorchMarks.Mark> cold = marksAt(marks, target);
        context.assertTrue(cold.size() == 1, "A cold scar remains after the beam is removed");
        close(context, 0, LaserScorchMarks.heat(cold.get(0).age(1 + LaserScorchMarks.COOLING_TICKS)), "It has cooled");
        marks.update(context.getWorld(), LaserScorchMarks.LIFETIME_TICKS);
        context.assertTrue(marksAt(marks, target).size() == 1, "Lifetime is counted from the last contact");
        marks.update(context.getWorld(), 1 + LaserScorchMarks.LIFETIME_TICKS);
        context.assertTrue(marksAt(marks, target).isEmpty(), "Old marks are retired");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void changedBlocksAndWorldExitRemoveTheirMarks(TestContext context) {
        BlockPos relative = new BlockPos(4, 4, 4);
        BlockPos target = context.getAbsolutePos(relative);
        context.setBlockState(relative, Blocks.STONE);
        emitter(context);
        LaserScorchMarks marks = new LaserScorchMarks();
        marks.update(context.getWorld(), 0);
        context.removeBlock(new BlockPos(1, 4, 4));
        context.setBlockState(relative, Blocks.IRON_BLOCK);
        marks.update(context.getWorld(), 1);
        context.assertTrue(marksAt(marks, target).isEmpty(), "Replacing the material removes the old scorch");
        emitter(context);
        marks.update(context.getWorld(), 2);
        context.assertFalse(marksAt(marks, target).isEmpty(), "Fresh contact can mark the new material");
        context.removeBlock(relative);
        LaserBeamNetwork.invalidate(context.getWorld());
        marks.update(context.getWorld(), 3);
        context.assertTrue(marksAt(marks, target).isEmpty(), "Broken blocks leave no floating marks");
        marks.tick(null);
        context.assertTrue(marks.marks().isEmpty(), "Leaving a world clears the cache");
        context.assertFalse(marks.belongsTo(context.getWorld()), "No stale world reference");
        context.removeBlock(new BlockPos(1, 4, 4));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void refocusingProducesAContinuousSurfaceTrail(TestContext context) {
        emitter(context);
        RefocusingCubeEntity cube = cube(context);
        for (int x = 2; x <= 6; x++) context.setBlockState(new BlockPos(x, 4, 6), Blocks.IRON_BLOCK);
        LaserScorchMarks marks = new LaserScorchMarks();
        for (int tick = 0; tick < 10; tick++) {
            cube.orient(-20 + tick * 4, 0);
            marks.update(context.getWorld(), tick);
        }
        BlockPos wall = context.getAbsolutePos(new BlockPos(4, 4, 6));
        List<LaserScorchMarks.Mark> trail = marksAt(marks, wall);
        context.assertTrue(trail.size() > 2, "Moving output leaves a trail, not a stationary dot");
        context.assertTrue(trail.stream().allMatch(mark -> mark.patch().face() == Direction.NORTH), "Marks follow the output hit face");
        context.assertTrue(marksAt(marks, context.getAbsolutePos(new BlockPos(4, 4, 4))).isEmpty(),
                "The cube itself must not be treated as a damaged block");
        context.removeBlock(new BlockPos(1, 4, 4));
        cube.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inactiveOrDistantContactsNeverDrawAConnectingStripe(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context);
        RefocusingCubeEntity cube = cube(context);
        for (int x = 0; x <= 8; x++) context.setBlockState(new BlockPos(x, 4, 8), Blocks.STONE);
        LaserScorchMarks marks = new LaserScorchMarks();
        cube.orient(-42, 0);
        marks.update(context.getWorld(), 0);
        cube.orient(42, 0);
        marks.update(context.getWorld(), 1);
        BlockPos middle = context.getAbsolutePos(new BlockPos(4, 4, 8));
        context.assertTrue(marksAt(marks, middle).isEmpty(), "A discontinuous sweep cannot scar the space between hits");
        emitter.handleButton(0);
        marks.update(context.getWorld(), 2);
        cube.orient(5, 0);
        emitter.handleButton(0);
        marks.update(context.getWorld(), 3);
        for (LaserScorchMarks.Mark mark : marksAt(marks, middle)) {
            double min = mark.patch().soot().stream().mapToDouble(v -> v.position().x).min().orElseThrow();
            double max = mark.patch().soot().stream().mapToDouble(v -> v.position().x).max().orElseThrow();
            context.assertTrue(max - min < 0.23D, "Turning a beam back on starts a new dot");
        }
        context.removeBlock(new BlockPos(1, 4, 4));
        cube.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void repeatedSweepsKeepTheCacheBounded(TestContext context) {
        emitter(context);
        RefocusingCubeEntity cube = cube(context);
        for (int x = 2; x <= 6; x++) context.setBlockState(new BlockPos(x, 4, 6), Blocks.STONE);
        LaserScorchMarks marks = new LaserScorchMarks();
        for (int tick = 0; tick < 500; tick++) {
            cube.orient(tick % 2 == 0 ? -20 : 20, 0);
            marks.update(context.getWorld(), tick);
        }
        int vertices = marks.marks().stream().mapToInt(mark -> mark.patch().soot().size()
                + mark.patch().heat().size() + mark.patch().emission().size()).sum();
        context.assertTrue(marks.marks().size() <= LaserScorchMarks.MAX_PATCHES, "Bounded patch count");
        context.assertTrue(vertices <= LaserScorchMarks.MAX_VERTICES, "Bounded geometry memory");
        context.assertTrue(marks.marks().stream().anyMatch(mark -> mark.age(499) == 0), "Recent impacts survive eviction");
        context.removeBlock(new BlockPos(1, 4, 4));
        cube.discard();
        context.complete();
    }

    private static LaserEmitterBlockEntity emitter(TestContext context) {
        BlockPos position = new BlockPos(1, 4, 4);
        context.setBlockState(position, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) context.getBlockEntity(position);
        emitter.getPropertyDelegate().set(3, 0);
        emitter.getPropertyDelegate().set(4, 0);
        LaserEmitterBlockEntity.serverTick(context.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
        return emitter;
    }

    private static RefocusingCubeEntity cube(TestContext context) {
        RefocusingCubeEntity cube = new RefocusingCubeEntity(ModEntities.REFOCUSING_CUBE, context.getWorld());
        cube.orient(0, 0);
        cube.setPosition(context.getAbsolute(new Vec3d(4.5D, 4.05D, 4.5D)));
        cube.setNoGravity(true);
        context.getWorld().spawnEntity(cube);
        return cube;
    }

    private static List<LaserScorchMarks.Mark> marksAt(LaserScorchMarks marks, BlockPos position) {
        return marks.marks().stream().filter(mark -> mark.patch().position().equals(position)).toList();
    }

    private static void close(TestContext context, double expected, double actual, String message) {
        context.assertTrue(Math.abs(expected - actual) < 0.00001D, message + ": expected " + expected + ", got " + actual);
    }
}
