package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserReceiverBlockEntity;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class LaserRangeGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedRangeKeepsLegacyDefaultsAndClampsInvalidValues(TestContext context) {
        LaserEmitterBlockEntity emitter = new LaserEmitterBlockEntity(BlockPos.ORIGIN, ModBlocks.LASER_EMITTER.getDefaultState());
        context.assertTrue(emitter.getBeamRange() == 64, "New emitters retain the previous default");
        for (int range : new int[]{1, 64, 128, 256, 512}) {
            emitter.getPropertyDelegate().set(13, range);
            NbtCompound saved = emitter.createNbt();
            emitter.getPropertyDelegate().set(13, 17);
            emitter.readNbt(saved);
            context.assertTrue(emitter.getBeamRange() == range, "Saved range " + range);
        }
        for (int invalid : new int[]{Integer.MIN_VALUE, 0, 513, Integer.MAX_VALUE}) {
            NbtCompound saved = emitter.createNbt();
            saved.putInt("BeamRange", invalid);
            emitter.readNbt(saved);
            context.assertTrue(emitter.getBeamRange() == (invalid < 1 ? 1 : 512), "Clamped NBT range");
        }
        emitter.readNbt(new NbtCompound());
        context.assertTrue(emitter.getBeamRange() == 64, "A pre-range save must restore 64, not a stale value");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void entireSliderControlsActualPathIncluding512(TestContext context) {
        BlockPos position = new BlockPos(3, 2, 3);
        LaserEmitterBlockEntity emitter = emitter(context, position, Direction.UP);
        for (int range = 1; range <= 512; range++) {
            emitter.handleButton(LaserEmitterScreenHandler.RANGE_BUTTON_BASE + range);
            tick(emitter);
            LaserBeamPath path = LaserBeamNetwork.path(emitter, 1);
            close(context, range, path.last().length(), "Actual beam range " + range);
            context.assertFalse(path.last().hasBlockHit(), "Empty sky must not be a collision");
        }
        context.removeBlock(position);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void configuredRangeIsSharedThroughARefocusingCube(TestContext context) {
        BlockPos position = new BlockPos(2, 4, 4);
        LaserEmitterBlockEntity emitter = emitter(context, position, Direction.EAST);
        RefocusingCubeEntity cube = new RefocusingCubeEntity(ModEntities.REFOCUSING_CUBE, context.getWorld());
        cube.orient(0, -90);
        cube.setPosition(context.getAbsolute(new Vec3d(4.5D, 4.05D, 4.5D)));
        cube.setNoGravity(true);
        context.getWorld().spawnEntity(cube);
        for (int range : new int[]{3, 5, 64, 128, 256, 512}) {
            emitter.handleButton(LaserEmitterScreenHandler.RANGE_BUTTON_BASE + range);
            tick(emitter);
            LaserBeamPath path = LaserBeamNetwork.path(emitter, 1);
            context.assertTrue(path.segments().size() == 2, "Cube redirects a configured beam");
            double total = path.segments().stream().mapToDouble(LaserBeamTrace::length).sum();
            total += path.segments().get(0).end().distanceTo(cube.opticalFrame(1).center());
            total += cube.opticalFrame(1).center().distanceTo(cube.opticalFrame(1).output());
            close(context, range, total, "Refocusing must not multiply range");
        }
        emitter.handleButton(LaserEmitterScreenHandler.RANGE_BUTTON_MIN);
        tick(emitter);
        context.assertTrue(LaserBeamNetwork.path(emitter, 1).segments().size() == 1, "Shortening range disconnects a distant cube immediately");
        context.removeBlock(position);
        cube.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shorteningRangeStopsDamageAndReception(TestContext context) {
        BlockPos position = new BlockPos(1, 2, 2);
        BlockPos receiverPosition = new BlockPos(6, 2, 2);
        LaserEmitterBlockEntity emitter = emitter(context, position, Direction.EAST);
        context.setBlockState(receiverPosition, ModBlocks.LASER_RECEIVER.getDefaultState().with(LaserReceiverBlock.FACING, Direction.WEST));
        LaserReceiverBlockEntity receiver = (LaserReceiverBlockEntity) context.getBlockEntity(receiverPosition);
        var target = context.spawnMob(EntityType.VILLAGER, new Vec3d(4.5D, 2, 2.5D));
        target.setAiDisabled(true);
        target.setNoGravity(true);
        emitter.getPropertyDelegate().set(4, 1);
        emitter.getPropertyDelegate().set(10, 0);
        for (int range : new int[]{1, 7, 1}) {
            float health = target.getHealth();
            emitter.handleButton(LaserEmitterScreenHandler.RANGE_BUTTON_BASE + range);
            tick(emitter);
            LaserReceiverBlockEntity.serverTick(context.getWorld(), receiver.getPos(), receiver.getCachedState(), receiver);
            close(context, range == 7 ? health - 0.5D : health, target.getHealth(), "Range limits actual damage");
            context.assertTrue(receiver.getCachedState().get(LaserReceiverBlock.POWER) == (range == 7 ? 15 : 0),
                    "Range changes update receiver power");
        }
        context.removeBlock(position);
        context.removeBlock(receiverPosition);
        target.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unloadedChunksStopBeamsWithoutBeingLoaded(TestContext context) {
        Vec3d start = context.getAbsolute(new Vec3d(4.5D, 20, 4.5D));
        BlockPos missing = BlockPos.ofFloored(start);
        for (int distance = 0; distance <= 512 && context.getWorld().isChunkLoaded(missing); distance++) {
            missing = BlockPos.ofFloored(start.add(distance, 0, 0));
        }
        context.assertFalse(context.getWorld().isChunkLoaded(missing), "Fixture must cross a loaded-chunk boundary");
        LaserBeamTrace trace = LaserBeamTrace.traceFrom(context.getWorld(), start, Direction.EAST, 512);
        close(context, missing.getX(), trace.end().x, "Beam stops at the first unknown chunk");
        context.assertFalse(trace.hasBlockHit(), "An unloaded boundary must not be mined or receive impact effects");
        context.assertFalse(context.getWorld().isChunkLoaded(missing), "Tracing must not load the missing chunk");
        context.complete();
    }

    private static LaserEmitterBlockEntity emitter(TestContext context, BlockPos pos, Direction direction) {
        context.setBlockState(pos, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, direction));
        return (LaserEmitterBlockEntity) context.getBlockEntity(pos);
    }

    private static void tick(LaserEmitterBlockEntity emitter) {
        LaserEmitterBlockEntity.serverTick(emitter.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
    }

    private static void close(TestContext context, double expected, double actual, String message) {
        context.assertTrue(Math.abs(expected - actual) < 0.0001D, message + ": expected " + expected + ", got " + actual);
    }
}
