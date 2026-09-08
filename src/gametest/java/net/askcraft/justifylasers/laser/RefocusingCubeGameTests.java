package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserReceiverBlockEntity;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;

public class RefocusingCubeGameTests implements FabricGameTest {
    private static final BlockPos CENTER = new BlockPos(4, 4, 4);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fiveInputsRedirectButOutputFaceOnlyBlocks(TestContext context) {
        for (Direction side : Direction.values()) {
            cap(context);
            RefocusingCubeEntity cube = cube(context, 4.5D, 4.05D, 4.5D, 0, 0);
            LaserEmitterBlockEntity emitter = emitter(context, CENTER.offset(side, 2), side.getOpposite());
            tick(emitter);
            LaserBeamPath path = LaserBeamNetwork.path(emitter, 1);
            context.assertTrue(path.segments().size() == (side == Direction.SOUTH ? 1 : 2), "Input face " + side);
            context.assertTrue(path.segments().get(0).end().distanceTo(cube.opticalFrame(1).center()) < 0.46D, "Incoming beam ends at cube");
            if (side != Direction.SOUTH) {
                context.assertTrue(path.last().axis().dotProduct(new Vec3d(0, 0, 1)) > 0.99999D, "Single south output");
            }
            context.getWorld().breakBlock(emitter.getPos(), false);
            cube.discard();
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void freeRotationProducesAnAngledBeam(TestContext context) {
        cap(context);
        RefocusingCubeEntity cube = cube(context, 4.5D, 4.05D, 4.5D, 37, -20);
        LaserEmitterBlockEntity emitter = emitter(context, CENTER.west(2), Direction.EAST);
        tick(emitter);
        LaserBeamPath path = LaserBeamNetwork.path(emitter, 1);
        context.assertTrue(path.segments().size() == 2, "Angled cube must redirect");
        context.assertTrue(path.last().axis().dotProduct(Vec3d.fromPolar(-20, 37)) > 0.99999D, "Output follows full yaw and pitch");
        cube.orient(-90, 0);
        tick(emitter);
        context.assertTrue(LaserBeamNetwork.path(emitter, 1).last().axis().x > 0.99999D, "Rotation updates routing immediately");
        finish(context, emitter, cube);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void redirectedBeamActivatesReceiverAndRetainsColor(TestContext context) {
        cap(context);
        RefocusingCubeEntity cube = cube(context, 4.5D, 4.05D, 4.5D, 0, 0);
        LaserEmitterBlockEntity emitter = emitter(context, CENTER.west(2), Direction.EAST);
        BlockPos receiverPos = CENTER.south(2);
        context.setBlockState(receiverPos, ModBlocks.LASER_RECEIVER.getDefaultState().with(LaserReceiverBlock.FACING, Direction.NORTH));
        LaserReceiverBlockEntity receiver = (LaserReceiverBlockEntity) context.getBlockEntity(receiverPos);
        for (LaserColor color : LaserColor.values()) {
            emitter.getPropertyDelegate().set(2, color.ordinal());
            tick(emitter);
            LaserReceiverBlockEntity.serverTick(context.getWorld(), receiver.getPos(), receiver.getCachedState(), receiver);
            context.assertTrue(receiver.getPropertyDelegate().get(5) == color.ordinal(), "Receiver color " + color);
            context.assertTrue(receiver.getPropertyDelegate().get(6) == 15, "Refocused signal");
        }
        emitter.getPropertyDelegate().set(0, 0);
        tick(emitter);
        LaserReceiverBlockEntity.serverTick(context.getWorld(), receiver.getPos(), receiver.getCachedState(), receiver);
        context.assertTrue(receiver.getPropertyDelegate().get(6) == 0, "Disabled source clears refocused signal");
        context.removeBlock(receiverPos);
        finish(context, emitter, cube);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void damageAndIgnitionFollowOutputNotOriginalDirection(TestContext context) {
        cap(context);
        RefocusingCubeEntity cube = cube(context, 4.5D, 4.05D, 4.5D, 0, 0);
        LaserEmitterBlockEntity emitter = emitter(context, CENTER.west(2), Direction.EAST);
        emitter.getPropertyDelegate().set(4, 1);
        emitter.getPropertyDelegate().set(12, 1);
        VillagerEntity output = target(context, new Vec3d(4.5D, 4, 5.8D));
        VillagerEntity behind = target(context, new Vec3d(5.8D, 4, 4.5D));
        tick(emitter);
        context.assertTrue(output.getHealth() == 19.5F && output.isOnFire(), "Refocused hit must retain damage and ignition");
        context.assertTrue(output.getVelocity().z > 0 && Math.abs(output.getVelocity().x) < 0.001D, "Knockback follows output");
        context.assertTrue(behind.getHealth() == 20 && !behind.isOnFire(), "Cube stops the original ray");
        finish(context, emitter, cube);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void redirectedMiningBreaksBlocksButProtectsReceiver(TestContext context) {
        cap(context);
        RefocusingCubeEntity cube = cube(context, 4.5D, 4.05D, 4.5D, 0, 0);
        LaserEmitterBlockEntity emitter = emitter(context, CENTER.west(2), Direction.EAST);
        emitter.getPropertyDelegate().set(3, 1);
        BlockPos target = CENTER.south(2);
        context.setBlockState(target, Blocks.STONE);
        for (int tick = 0; tick < 80; tick++) {
            tick(emitter);
        }
        context.assertTrue(context.getBlockState(target).isAir(), "Mining must follow output");
        context.setBlockState(target, ModBlocks.LASER_RECEIVER.getDefaultState().with(LaserReceiverBlock.FACING, Direction.NORTH));
        for (int tick = 0; tick < 650; tick++) {
            tick(emitter);
        }
        context.assertTrue(context.getBlockState(target).isOf(ModBlocks.LASER_RECEIVER), "Receiver front survives redirected mining");
        context.removeBlock(target);
        finish(context, emitter, cube);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chainsAndClosedLoopsTerminate(TestContext context) {
        cap(context);
        RefocusingCubeEntity a = cube(context, 4.5D, 4.05D, 4.5D, 0, 0);
        RefocusingCubeEntity b = cube(context, 4.5D, 4.05D, 6.5D, -90, 0);
        RefocusingCubeEntity c = cube(context, 6.5D, 4.05D, 6.5D, 180, 0);
        RefocusingCubeEntity d = cube(context, 6.5D, 4.05D, 4.5D, 90, 0);
        LaserEmitterBlockEntity emitter = emitter(context, CENTER.west(2), Direction.EAST);
        tick(emitter);
        LaserBeamPath path = LaserBeamNetwork.path(emitter, 1);
        context.assertTrue(path.segments().size() == 5, "Four-cube loop must stop when it reaches the first cube again");
        context.assertTrue(path.last().end().distanceTo(a.opticalFrame(1).center()) < 0.46D, "Loop closes at the first cube, without recursing");
        for (RefocusingCubeEntity cube : List.of(b, c, d)) {
            cube.discard();
        }
        finish(context, emitter, a);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rangeIsSharedAcrossAllSegments(TestContext context) {
        cap(context);
        context.removeBlock(CENTER.up(3));
        RefocusingCubeEntity cube = cube(context, 4.5D, 4.05D, 4.5D, 0, -90);
        LaserEmitterBlockEntity emitter = emitter(context, CENTER.west(2), Direction.EAST);
        tick(emitter);
        LaserBeamPath path = LaserBeamNetwork.path(emitter, 1);
        context.assertTrue(path.segments().size() == 2 && !path.last().hasBlockHit(), "Unobstructed upward output");
        double inside = path.segments().get(0).end().distanceTo(cube.opticalFrame(1).center())
                + cube.opticalFrame(1).center().distanceTo(path.last().start());
        double total = path.segments().stream().mapToDouble(LaserBeamTrace::length).sum() + inside;
        context.assertTrue(Math.abs(total - 64) < 0.0001D, "Cube must not multiply the 64-block range: " + total);
        finish(context, emitter, cube);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void simultaneousInputsProduceOneStableOutput(TestContext context) {
        cap(context);
        RefocusingCubeEntity cube = cube(context, 4.5D, 4.05D, 4.5D, 0, 0);
        LaserEmitterBlockEntity a = emitter(context, CENTER.west(2), Direction.EAST);
        LaserEmitterBlockEntity b = emitter(context, CENTER.north(2), Direction.SOUTH);
        b.getPropertyDelegate().set(2, LaserColor.BLUE.ordinal());
        tick(a);
        tick(b);
        int firstA = LaserBeamNetwork.path(a, 1).segments().size();
        int firstB = LaserBeamNetwork.path(b, 1).segments().size();
        context.assertTrue(firstA + firstB == 3, "Exactly one redirected output for two inputs");
        tick(b);
        tick(a);
        context.assertTrue(LaserBeamNetwork.path(a, 1).segments().size() == firstA, "Ownership must not flicker with tick order");
        context.assertTrue(LaserBeamNetwork.path(b, 1).segments().size() == firstB, "Stable losing source");
        LaserEmitterBlockEntity winner = firstA == 2 ? a : b;
        LaserEmitterBlockEntity other = winner == a ? b : a;
        winner.getPropertyDelegate().set(0, 0);
        tick(winner);
        context.assertTrue(LaserBeamNetwork.path(other, 1).segments().size() == 2, "Remaining source takes over immediately");
        context.getWorld().breakBlock(b.getPos(), false);
        finish(context, a, cube);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void movingCubeOutOfBeamRestoresStraightPath(TestContext context) {
        cap(context);
        RefocusingCubeEntity cube = cube(context, 4.5D, 4.05D, 4.5D, 0, 0);
        LaserEmitterBlockEntity emitter = emitter(context, CENTER.west(2), Direction.EAST);
        tick(emitter);
        context.assertTrue(LaserBeamNetwork.path(emitter, 1).segments().size() == 2, "Initial refocus");
        cube.setPosition(cube.getPos().add(0, 0, 1.2D));
        tick(emitter);
        context.assertTrue(LaserBeamNetwork.path(emitter, 1).segments().size() == 1, "Moved cube must not leave a ghost output");
        context.assertTrue(LaserBeamNetwork.path(emitter, 1).last().axis().x > 0.99999D, "Straight path restored");
        finish(context, emitter, cube);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gravityAndFastMovementRespectSolidWalls(TestContext context) {
        floor(context);
        RefocusingCubeEntity cube = cube(context, 4.5D, 5, 4.5D, 0, 0);
        cube.setNoGravity(false);
        for (int tick = 0; tick < 40; tick++) {
            cube.tick();
        }
        context.assertTrue(Math.abs(cube.getY() - context.getAbsolutePos(new BlockPos(0, 2, 0)).getY()) < 0.001D, "Cube rests on floor");
        context.setBlockState(6, 2, 4, Blocks.OBSIDIAN);
        cube.move(MovementType.SELF, new Vec3d(20, 0, 0));
        context.assertTrue(cube.getBoundingBox().maxX <= context.getAbsolutePos(new BlockPos(6, 2, 4)).getX() + 0.0001D,
                "Fast cube must not tunnel through a one-block wall");
        cube.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void entitiesCanStandOnCubeAndCannotWalkThroughIt(TestContext context) {
        floor(context);
        RefocusingCubeEntity cube = cube(context, 4.5D, 2, 4.5D, 0, 0);
        VillagerEntity walker = target(context, new Vec3d(3, 2, 4.5D));
        walker.move(MovementType.SELF, new Vec3d(3, 0, 0));
        context.assertTrue(walker.getBoundingBox().maxX <= cube.getBoundingBox().minX + 0.0001D, "Solid collision stops a mob");
        walker.setPosition(context.getAbsolute(new Vec3d(4.5D, 5, 4.5D)));
        walker.move(MovementType.SELF, new Vec3d(0, -4, 0));
        context.assertTrue(Math.abs(walker.getY() - cube.getBoundingBox().maxY) < 0.0001D, "Mob can stand on cube");
        cube.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void carryRemainsPhysicalAndStillRefocuses(TestContext context) {
        floor(context);
        PlayerEntity player = player(context, new Vec3d(4.5D, 2, 2.5D));
        player.setYaw(0);
        player.setPitch(0);
        RefocusingCubeEntity cube = cube(context, 4.5D, 2, 4.5D, 0, 0);
        cube.interact(player, Hand.MAIN_HAND);
        context.assertTrue(cube.holder() == player, "Right-click picks up world entity");
        for (int tick = 0; tick < 10; tick++) {
            cube.tick();
        }
        LaserEmitterBlockEntity emitter = emitter(context, new BlockPos(2, 3, 4), Direction.EAST);
        tick(emitter);
        context.assertTrue(LaserBeamNetwork.path(emitter, 1).segments().size() == 2, "Carried cube still intercepts the beam");
        for (int y = 2; y < 6; y++) {
            for (int z = 2; z < 6; z++) {
                context.setBlockState(5, y, z, Blocks.OBSIDIAN);
            }
        }
        player.setYaw(-90);
        for (int tick = 0; tick < 15; tick++) {
            cube.tick();
        }
        context.assertTrue(cube.getBoundingBox().maxX <= context.getAbsolutePos(new BlockPos(5, 2, 4)).getX() + 0.0001D,
                "Carrying must not pull cube through wall");
        cube.interact(player, Hand.MAIN_HAND);
        context.assertFalse(cube.isHeld(), "Second right-click releases");
        player.discard();
        finish(context, emitter, cube);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void kicksAndBodyPushesMoveCubeWithoutRemovingIt(TestContext context) {
        floor(context);
        PlayerEntity player = player(context, new Vec3d(4.5D, 2, 2.5D));
        player.setYaw(0);
        RefocusingCubeEntity cube = cube(context, 4.5D, 2, 4.5D, 0, 0);
        cube.pushAwayFrom(player);
        context.assertTrue(cube.getVelocity().z > 0, "Body push moves cube away");
        cube.damage(context.getWorld().getDamageSources().playerAttack(player), 1);
        double ordinary = cube.getVelocity().horizontalLength();
        context.assertTrue(ordinary > 0.3D && !cube.isRemoved(), "Attack kicks rather than destroys");
        player.setSprinting(true);
        cube.damage(context.getWorld().getDamageSources().playerAttack(player), 1);
        context.assertTrue(cube.getVelocity().horizontalLength() > ordinary, "Sprint kick is stronger");
        player.discard();
        cube.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heldCubeIsExclusiveAndDropsWhenCarrierDisappears(TestContext context) {
        PlayerEntity a = player(context, new Vec3d(4.5D, 2, 2.5D));
        PlayerEntity b = player(context, new Vec3d(3, 2, 4.5D));
        RefocusingCubeEntity cube = cube(context, 4.5D, 2, 4.5D, 0, 0);
        cube.interact(a, Hand.MAIN_HAND);
        cube.interact(b, Hand.MAIN_HAND);
        context.assertTrue(cube.holder() == a, "Another player cannot steal held cube");
        RefocusingCubeEntity second = cube(context, 4.5D, 2, 5.5D, 0, 0);
        second.interact(a, Hand.MAIN_HAND);
        context.assertFalse(second.isHeld(), "Only one cube can be carried per player");
        a.discard();
        cube.tick();
        context.assertFalse(cube.isHeld(), "Missing player must release cube");
        b.discard();
        cube.discard();
        second.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedCubesKeepColorAndOrientationButNotAHolder(TestContext context) {
        PlayerEntity player = player(context, new Vec3d(4.5D, 2, 2.5D));
        RefocusingCubeEntity cube = cube(context, 4.5D, 2, 4.5D, 37, -20);
        cube.setBeamInput(new LaserBeamNetwork.CubeInput(LaserColor.MAGENTA, true));
        cube.interact(player, Hand.MAIN_HAND);
        NbtCompound saved = cube.writeNbt(new NbtCompound());
        RefocusingCubeEntity loaded = new RefocusingCubeEntity(ModEntities.REFOCUSING_CUBE, context.getWorld());
        loaded.readNbt(saved);
        context.assertTrue(loaded.getColor() == LaserColor.MAGENTA && loaded.getYaw() == 37 && loaded.getPitch() == -20, "Saved optical state");
        context.assertFalse(loaded.isHeld() || loaded.isLit(), "Reload must not restore stale holder or light");
        context.assertTrue(loaded.asItemStack().getNbt().getInt("Color") == LaserColor.MAGENTA.ordinal(), "Collected item retains color");
        player.discard();
        cube.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placingAndCollectingDoesNotDuplicateTheItem(TestContext context) {
        floor(context);
        PlayerEntity player = player(context, new Vec3d(4.5D, 2, 2.5D));
        ItemStack item = new ItemStack(ModEntities.REFOCUSING_CUBE_ITEM);
        item.getOrCreateNbt().putInt("Color", LaserColor.BLUE.ordinal());
        player.setStackInHand(Hand.MAIN_HAND, item);
        BlockPos support = context.getAbsolutePos(new BlockPos(4, 1, 4));
        var hit = new BlockHitResult(Vec3d.ofCenter(support).add(0, 0.5D, 0), Direction.UP, support, false);
        context.assertTrue(item.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(), "Placement succeeds");
        context.assertTrue(item.isEmpty(), "Survival placement consumes one item");
        var cubes = context.getWorld().getEntitiesByClass(RefocusingCubeEntity.class,
                new net.minecraft.util.math.Box(support.up()).expand(0.2D), cube -> true);
        context.assertTrue(cubes.size() == 1, "Placement creates exactly one physical entity");
        RefocusingCubeEntity cube = cubes.get(0);
        context.assertTrue(cube.getColor() == LaserColor.BLUE, "Placed cube retains saved item color");
        player.setSneaking(true);
        var source = context.getWorld().getDamageSources().playerAttack(player);
        cube.damage(source, 1);
        cube.damage(source, 1);
        context.assertTrue(cube.isRemoved(), "Collection removes world entity");
        context.assertTrue(player.getInventory().count(ModEntities.REFOCUSING_CUBE_ITEM) == 1, "Repeated attacks cannot duplicate cube");
        context.assertTrue(context.getWorld().getRecipeManager().get(new net.minecraft.util.Identifier("justifylasers", "refocusing_cube")).isPresent(), "Crafting recipe loads");
        player.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void projectilesCannotCollectCubesRemotely(TestContext context) {
        PlayerEntity player = player(context, new Vec3d(4.5D, 2, 2.5D));
        player.setSneaking(true);
        RefocusingCubeEntity cube = cube(context, 4.5D, 2, 4.5D, 0, 0);
        var arrow = EntityType.ARROW.create(context.getWorld());
        cube.damage(context.getWorld().getDamageSources().arrow(arrow, player), 1);
        context.assertFalse(cube.isRemoved(), "Ranged damage must not use the melee collection action");
        context.assertTrue(player.getInventory().count(ModEntities.REFOCUSING_CUBE_ITEM) == 0, "No remotely collected item");
        player.discard();
        cube.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void droppedCubesSettleOnTheNearestFaceWithoutChangingHeading(TestContext context) {
        floor(context);
        for (float yaw : new float[]{0, 37, 89, 145}) {
            for (float pitch : new float[]{-85, -46, -44, -20, 20, 44, 46, 85}) {
                RefocusingCubeEntity cube = cube(context, 4.5D, 4, 4.5D, yaw, pitch);
                cube.setNoGravity(false);
                float previousPitch = pitch;
                for (int tick = 0; tick < 60; tick++) {
                    cube.tick();
                    context.assertTrue(Math.abs(cube.getPitch() - previousPitch) <= 6.01F, "Settling must be smooth, not an instant snap");
                    context.assertTrue(context.getWorld().isSpaceEmpty(cube, cube.getBoundingBox()), "Settling must not clip through blocks");
                    previousPitch = cube.getPitch();
                }
                context.assertTrue(cube.getPitch() == Math.round(pitch / 90.0F) * 90.0F, "Cube must not remain balanced on an edge: " + pitch);
                context.assertTrue(cube.getYaw() == yaw, "Settling preserves the chosen horizontal heading");
                context.assertTrue(Math.abs(cube.getY() - context.getAbsolutePos(new BlockPos(4, 2, 4)).getY()) < 0.0001D, "Flat face rests on the floor");
                context.assertTrue(cube.getVelocity().lengthSquared() < 1.0E-8D, "A settled cube must not accumulate fall velocity");
                cube.discard();
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void releasedHeldCubeSettlesButCarriedAimStaysFree(TestContext context) {
        floor(context);
        PlayerEntity player = player(context, new Vec3d(4.5D, 2, 2.5D));
        player.setYaw(0);
        player.setPitch(-28);
        RefocusingCubeEntity cube = cube(context, 4.5D, 2, 4.5D, 0, 0);
        cube.setNoGravity(false);
        cube.interact(player, Hand.MAIN_HAND);
        for (int tick = 0; tick < 20; tick++) {
            cube.tick();
        }
        context.assertTrue(cube.isHeld() && cube.getPitch() == -28, "Held cube follows view without auto-leveling");
        Vec3d expectedCenter = player.getEyePos().add(player.getRotationVec(1).multiply(1.8D)).add(0, -0.15D, 0);
        context.assertTrue(cube.opticalFrame(1).center().distanceTo(expectedCenter) < 0.0001D, "Carrying targets the center, not the rotating AABB bottom");
        cube.interact(player, Hand.MAIN_HAND);
        for (int tick = 0; tick < 60; tick++) {
            cube.tick();
        }
        context.assertFalse(cube.isHeld(), "Right-click still releases");
        context.assertTrue(cube.getPitch() == 0 && cube.isOnGround(), "Released cube lands flat");
        player.discard();
        cube.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tiltedCubeSettlesBesideAWallWithoutPenetrationOrJitter(TestContext context) {
        floor(context);
        for (int y = 2; y < 5; y++) {
            for (int z = 2; z < 7; z++) {
                context.setBlockState(5, y, z, Blocks.OBSIDIAN);
            }
        }
        RefocusingCubeEntity cube = cube(context, 4.5D, 2, 4.5D, 0, 38);
        cube.setNoGravity(false);
        for (int tick = 0; tick < 80; tick++) {
            cube.tick();
            context.assertTrue(context.getWorld().isSpaceEmpty(cube, cube.getBoundingBox()), "Wall/floor collision stays valid while settling");
        }
        context.assertTrue(cube.getPitch() == 0, "Wall must not leave the cube balanced on an edge");
        Vec3d rest = cube.getPos();
        for (int tick = 0; tick < 40; tick++) {
            cube.tick();
        }
        context.assertTrue(cube.getPos().equals(rest), "Resting cube must not drift or jitter");
        cube.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void droppedCubeRestsOnSlabCollisionShape(TestContext context) {
        for (int x = 1; x < 8; x++) {
            for (int z = 1; z < 8; z++) {
                context.setBlockState(x, 1, z, Blocks.STONE_SLAB);
            }
        }
        RefocusingCubeEntity cube = cube(context, 4.5D, 3, 4.5D, 27, -33);
        cube.setNoGravity(false);
        for (int tick = 0; tick < 60; tick++) {
            cube.tick();
        }
        double floor = context.getAbsolutePos(new BlockPos(4, 1, 4)).getY() + 0.5D;
        context.assertTrue(Math.abs(cube.getY() - floor) < 0.0001D && cube.getPitch() == 0, "Settling respects non-full-block support shapes");
        cube.discard();
        context.complete();
    }

    private static void cap(TestContext context) {
        for (Direction side : Direction.values()) {
            context.setBlockState(CENTER.offset(side, 3), Blocks.OBSIDIAN);
        }
    }

    private static void floor(TestContext context) {
        for (int x = 1; x < 8; x++) {
            for (int z = 1; z < 8; z++) {
                context.setBlockState(x, 1, z, Blocks.STONE);
            }
        }
    }

    private static RefocusingCubeEntity cube(TestContext context, double x, double y, double z, float yaw, float pitch) {
        RefocusingCubeEntity cube = new RefocusingCubeEntity(ModEntities.REFOCUSING_CUBE, context.getWorld());
        cube.orient(yaw, pitch);
        cube.prevYaw = yaw;
        cube.prevPitch = pitch;
        cube.setPosition(context.getAbsolute(new Vec3d(x, y, z)));
        cube.setNoGravity(true);
        context.getWorld().spawnEntity(cube);
        return cube;
    }

    private static LaserEmitterBlockEntity emitter(TestContext context, BlockPos pos, Direction direction) {
        context.setBlockState(pos, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, direction));
        return (LaserEmitterBlockEntity) context.getBlockEntity(pos);
    }

    private static VillagerEntity target(TestContext context, Vec3d pos) {
        VillagerEntity target = context.spawnMob(EntityType.VILLAGER, pos);
        target.setAiDisabled(true);
        target.setNoGravity(true);
        return target;
    }

    private static PlayerEntity player(TestContext context, Vec3d pos) {
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        player.setPosition(context.getAbsolute(pos));
        return player;
    }

    private static void tick(LaserEmitterBlockEntity emitter) {
        LaserEmitterBlockEntity.serverTick(emitter.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
    }

    private static void finish(TestContext context, LaserEmitterBlockEntity emitter, RefocusingCubeEntity cube) {
        context.getWorld().breakBlock(emitter.getPos(), false);
        cube.discard();
        context.complete();
    }
}
