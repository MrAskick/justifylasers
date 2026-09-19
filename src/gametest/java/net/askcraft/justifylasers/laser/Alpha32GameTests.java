package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LightBridgeBlock;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.askcraft.justifylasers.energy.RechargeableItem;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.item.LaserConfiguratorItem;
import net.askcraft.justifylasers.network.ConfiguratorModePacket;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Alpha32GameTests implements FabricGameTest {
    private static final BlockPos BRIDGE = new BlockPos(3, 3, 3);

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "alpha32", tickLimit = 30)
    public void cornerConnectsFloorAndWallAndSharesOneOpticalBudget(TestContext context) {
        context.setBlockState(BRIDGE, ModBlocks.CORNER_LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING, Direction.NORTH));
        context.setBlockState(BRIDGE.east(), ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING, Direction.NORTH));
        context.setBlockState(BRIDGE.up(), ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING, Direction.NORTH).with(LightBridgeBlock.MOUNT, Direction.EAST));
        context.setBlockState(BRIDGE.south(2), ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.NORTH));
        for (var offset : new BlockPos[]{BRIDGE, BRIDGE.east(), BRIDGE.up()}) context.setBlockState(offset.north(2), Blocks.STONE);
        context.runAtTick(5, () -> {
            var world = context.getWorld();
            var origin = context.getAbsolutePos(BRIDGE);
            var sections = LightBridgeNetwork.fields(world).stream().filter(field -> field.origin().equals(origin)).toList();
            context.assertTrue(sections.size() == 2 && sections.stream().allMatch(field -> field.active() && field.connections() == 3), "Both corner legs join their neighbors: " + sections);
            var floor = LightBridgeNetwork.at(world, origin.east());
            var wall = LightBridgeNetwork.at(world, origin.up());
            context.assertTrue(floor.active() && wall.active() && floor.length() == wall.length() && floor.length() == sections.get(0).length(), "A single input powers all joined sheets equally");
            long budget = ((net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity) context.getBlockEntity(BRIDGE.south(2))).opticalBudget();
            context.assertTrue(floor.lumens() + wall.lumens() + sections.stream().mapToLong(field -> field.lumens()).sum() <= budget, "A corner does not multiply power");
            for (var section : sections) {
                var p = section.point(.7, 1, .47);
                context.assertFalse(LightBridgeNetwork.collisions(world, new Box(p.add(-.01, -.01, -.01), p.add(.01, .01, .01))).isEmpty(), "Both sheets are solid, even with the same source position");
            }
            context.removeBlock(BRIDGE);
            context.runAtTick(8, () -> {
                context.assertFalse(LightBridgeNetwork.at(world, origin.east()).active(), "Removing elbow disconnects the floor");
                context.assertFalse(LightBridgeNetwork.at(world, origin.up()).active(), "Removing elbow disconnects the wall");
                context.removeBlock(BRIDGE.south(2)); context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "alpha32", tickLimit = 30)
    public void diagonalMovementUsesThinSurfaceInsteadOfFullBoundingBox(TestContext context) {
        context.setBlockState(BRIDGE, ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING, Direction.EAST).with(LightBridgeBlock.ROTATION, 1));
        context.setBlockState(BRIDGE.west(2), ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        context.setBlockState(BRIDGE.east(4), Blocks.STONE);
        context.runAtTick(5, () -> {
            var world = context.getWorld();
            var field = LightBridgeNetwork.at(world, context.getAbsolutePos(BRIDGE));
            context.assertTrue(field != null && field.active() && field.rotation() == 1, "45-degree field is live");
            var p = field.point(.5, 1, .5).add(field.normal().multiply(.18));
            context.assertTrue(LightBridgeNetwork.collisions(world, new Box(p.add(-.02, -.02, -.02), p.add(.02, .02, .02))).isEmpty(), "Air beside diagonal remains empty");
            var pig = EntityType.PIG.create(world);
            var center = field.point(.5, .9, .5);
            pig.setPosition(center.add(0, 1, 0));
            pig.move(MovementType.SELF, new Vec3d(0, -2, 0));
            context.assertTrue(pig.isOnGround(), "Entity lands on angled field");
            double y = pig.getY();
            for (int i = 0; i < 12; i++) {
                pig.move(MovementType.SELF, new Vec3d(.1, -.08, 0));
                context.assertTrue(Math.abs(pig.getY() - y) < 1e-7, "Walking parallel to a diagonal field has no height jitter");
            }
            context.removeBlock(BRIDGE.west(2)); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "alpha32")
    public void configuratorValidatesPacketsConsumesEnergyAndNeverChargesFailedActions(TestContext context) {
        var colors = new java.util.HashSet<Integer>();
        for (int i = 0; i < LaserConfiguratorItem.MODE_COUNT; i++) context.assertTrue(colors.add(LaserConfiguratorItem.color(i)), "Modes have distinct indicator colors");
        var player = context.createMockSurvivalPlayer();
        context.setBlockState(BRIDGE, ModBlocks.LIGHT_BRIDGE);
        var pos = context.getAbsolutePos(BRIDGE);
        player.setPosition(Vec3d.ofCenter(pos.up()));
        var stack = new ItemStack(ModBlocks.CONFIGURATOR);
        var tool = (LaserConfiguratorItem) stack.getItem();
        player.setStackInHand(Hand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        var use = new ItemUsageContext(player, Hand.MAIN_HAND, hit);
        new ConfiguratorModePacket(Hand.MAIN_HAND, player.getInventory().selectedSlot, 4).apply(player);
        context.assertTrue(tool.useOnBlock(use) == ActionResult.FAIL && context.getBlockState(BRIDGE).get(LightBridgeBlock.ROTATION) == 0, "Empty tool cannot edit a block");
        context.assertTrue(RechargeableItem.receive(stack, 200, true) == 200 && tool.readEnergy(stack) == 0, "Simulated charging is non-mutating");
        RechargeableItem.receive(stack, 200, false);
        for (int angle = 1; angle <= 8; angle++) {
            context.assertTrue(tool.useOnBlock(use) == ActionResult.SUCCESS, "Paid roll succeeds");
            context.assertTrue(context.getBlockState(BRIDGE).get(LightBridgeBlock.ROTATION) == angle % 8, "Each click rolls exactly 45 degrees");
            var ray = LaserBeamTrace.traceFrom(context.getWorld(), Vec3d.ofCenter(pos.south(2)), new Vec3d(0, 0, -1), 3);
            context.assertTrue(pos.equals(ray.hitBlock()), "Grid-centered input remains connected after roll " + angle);
        }
        context.assertTrue(tool.readEnergy(stack) == 40, "Eight actions consume 160 FE");
        new ConfiguratorModePacket(Hand.MAIN_HAND, player.getInventory().selectedSlot, 90).apply(player);
        new ConfiguratorModePacket(Hand.MAIN_HAND, (player.getInventory().selectedSlot + 1) % 9, 1).apply(player);
        context.assertTrue(LaserConfiguratorItem.mode(stack) == 4, "Invalid mode and stale hotbar packets are ignored");
        context.setBlockState(BRIDGE, Blocks.STONE);
        context.assertTrue(tool.useOnBlock(use) == ActionResult.PASS && tool.readEnergy(stack) == 40, "Unsupported blocks cost nothing");
        tool.use(context.getWorld(), player, Hand.MAIN_HAND);
        context.assertTrue(LaserConfiguratorItem.mode(stack) == 4 && tool.readEnergy(stack) == 40, "Air click does not cycle or spend energy");
        var saved = stack.copy();
        context.assertTrue(tool.readEnergy(saved) == 40 && LaserConfiguratorItem.mode(saved) == 4, "Charge and selected mode survive stack copying");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "alpha32")
    public void generatorChargesConfiguratorAndManualChargingRespectsPrivacy(TestContext context) {
        context.setBlockState(BRIDGE, ModIndustry.MACHINES.get(MachineKind.FUEL_GENERATOR));
        var generator = (IndustrialMachineBlockEntity) context.getBlockEntity(BRIDGE);
        generator.energy().restore(4096);
        var stack = new ItemStack(ModBlocks.CONFIGURATOR);
        context.assertTrue(generator.isValid(IndustrialMachineBlockEntity.WATER_INPUT, stack), "Generator accepts configurator in charging slot");
        generator.setStack(IndustrialMachineBlockEntity.WATER_INPUT, stack);
        IndustrialMachineBlockEntity.tick(generator.getWorld(), generator.getPos(), generator.getCachedState(), generator);
        context.assertTrue(RechargeableItem.stored(stack) == 256 && generator.energy().stored() == 3840, "Charging conserves FE");
        generator.removeStack(IndustrialMachineBlockEntity.WATER_INPUT);
        var owner = context.createMockSurvivalPlayer();
        var guest = context.createMockSurvivalPlayer(); guest.setUuid(java.util.UUID.randomUUID());
        generator.initializeOwner(owner); generator.togglePrivacy(owner);
        guest.setStackInHand(Hand.MAIN_HAND, stack);
        LaserConfiguratorItem.select(stack, 5);
        var hit = new BlockHitResult(Vec3d.ofCenter(generator.getPos()), Direction.UP, generator.getPos(), false);
        context.assertTrue(stack.getItem().useOnBlock(new ItemUsageContext(guest, Hand.MAIN_HAND, hit)) == ActionResult.FAIL, "Guest cannot drain a private machine");
        owner.setStackInHand(Hand.MAIN_HAND, stack);
        context.assertTrue(stack.getItem().useOnBlock(new ItemUsageContext(owner, Hand.MAIN_HAND, hit)) == ActionResult.SUCCESS, "Owner may charge manually");
        context.assertTrue(RechargeableItem.stored(stack) == 4096 && generator.energy().stored() == 0, "Manual charging transfers, rather than creates, energy");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "alpha32")
    public void rotatedAndMirroredStructuresKeepAValidBridgeBasis(TestContext context) {
        for (var block : new net.minecraft.block.Block[]{ModBlocks.LIGHT_BRIDGE, ModBlocks.CORNER_LIGHT_BRIDGE})
            for (Direction facing : Direction.values()) for (Direction mount : Direction.values()) for (int angle = 0; angle < 8; angle++) {
                var state = block.getDefaultState().with(LightBridgeBlock.FACING, facing).with(LightBridgeBlock.MOUNT, mount).with(LightBridgeBlock.ROTATION, angle);
                for (BlockRotation turn : BlockRotation.values()) {
                    var rotated = state.rotate(turn);
                    context.assertTrue(rotated.get(LightBridgeBlock.FACING) == turn.rotate(facing), "Structure rotation keeps projection axis");
                }
                for (BlockMirror mirror : BlockMirror.values()) {
                    var mirrored = state.mirror(mirror);
                    context.assertTrue(mirrored.get(LightBridgeBlock.FACING) == mirror.apply(facing), "Structure mirror keeps projection axis");
                }
            }
        context.complete();
    }
}
