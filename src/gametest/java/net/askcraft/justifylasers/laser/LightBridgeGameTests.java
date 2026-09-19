package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LightBridgeBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LightBridgeBlockEntity;
import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.askcraft.justifylasers.bridge.LightBridgeSpan;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class LightBridgeGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void floorAndWallPlacementIsIndependentOfCameraPitchAndCopiesNeighbours(TestContext context) {
        var player=context.createMockCreativeServerPlayerInWorld();
        BlockPos support=context.getAbsolutePos(new BlockPos(3,3,3));
        try {
            context.getWorld().setBlockState(support,Blocks.STONE.getDefaultState());
            for(var block:new net.askcraft.justifylasers.block.LightBridgeBlock[]{(net.askcraft.justifylasers.block.LightBridgeBlock)ModBlocks.LIGHT_BRIDGE,(net.askcraft.justifylasers.block.LightBridgeBlock)ModBlocks.CORNER_LIGHT_BRIDGE}) {
                player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new net.minecraft.item.ItemStack(block));
                for(Direction mount:Direction.values()) for(float yaw:new float[]{0,90,180,270}) for(float pitch:new float[]{-85,0,85}) {
                    player.setYaw(yaw); player.setPitch(pitch); player.setSneaking(false);
                    var hit=new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(support).add(Vec3d.of(mount.getVector()).multiply(.5)),mount,support,false);
                    var placement=new net.minecraft.item.ItemPlacementContext(new net.minecraft.item.ItemUsageContext(player,net.minecraft.util.Hand.MAIN_HAND,hit));
                    var state=block.getPlacementState(placement);
                    var expected=mount.getAxis().isHorizontal()?mount:player.getHorizontalFacing();
                    context.assertTrue(state.get(net.askcraft.justifylasers.block.LightBridgeBlock.FACING)==expected,"Pitch never changes floor/ceiling projection; walls point out");
                }
                for(int rotation=0;rotation<8;rotation++) {
                    var neighbour=ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING,Direction.EAST)
                            .with(LightBridgeBlock.MOUNT,Direction.DOWN).with(LightBridgeBlock.ROLLED,true).with(LightBridgeBlock.ROTATION,rotation);
                    context.getWorld().setBlockState(support,neighbour);
                    var hit=new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(support).add(0,.5,0),Direction.UP,support,false);
                    player.setSneaking(false);
                    var state=block.getPlacementState(new net.minecraft.item.ItemPlacementContext(new net.minecraft.item.ItemUsageContext(player,net.minecraft.util.Hand.MAIN_HAND,hit)));
                    context.assertTrue(state.get(LightBridgeBlock.FACING)==Direction.EAST && state.get(LightBridgeBlock.MOUNT)==Direction.DOWN
                            && state.get(LightBridgeBlock.ROLLED) && state.get(LightBridgeBlock.ROTATION)==rotation,"Attached section inherits every orientation property");
                    player.setSneaking(true);
                    state=block.getPlacementState(new net.minecraft.item.ItemPlacementContext(new net.minecraft.item.ItemUsageContext(player,net.minecraft.util.Hand.MAIN_HAND,hit)));
                    context.assertTrue(state.get(LightBridgeBlock.FACING)==Direction.UP,"Sneaking places independently, perpendicular to the clicked floor");
                }
                context.getWorld().setBlockState(support,Blocks.STONE.getDefaultState());
            }
        } finally { player.discard(); }
        context.complete();
    }
    private static final BlockPos EMITTER = new BlockPos(1, 3, 3), BRIDGE = new BlockPos(3, 3, 3);
    private static final double HEIGHT = net.askcraft.justifylasers.bridge.BridgeOrientation.HALF_HEIGHT;
    private static final double LEAD = 1 - 2 * net.askcraft.justifylasers.bridge.BridgeOrientation.HALF_DEPTH;

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "light_bridge", tickLimit = 25)
    public void opticalSinkIsWalkableWithoutAnyBlocksOrEntitiesAndSwitchesOff(TestContext context) {
        var bridge = bridge(context, BRIDGE);
        var source = source(context, EMITTER);
        context.setBlockState(BRIDGE.east(6), Blocks.STONE);
        context.runAtTick(4, () -> {
            var span = field(context);
            context.assertTrue(span != null && Math.abs(span.length() - (5 + LEAD)) < 1e-6 && span.rgb() == source.beamRgb(), "Receives beam color and stops at wall");
            var pos = context.getAbsolutePos(BRIDGE.east(2));
            context.assertTrue(context.getWorld().getBlockState(pos).isAir(), "No invisible blocks are placed");
            Box body = new Box(pos.getX()+.2,pos.getY()+HEIGHT-.05,pos.getZ()+.2,pos.getX()+.8,pos.getY()+HEIGHT+.15,pos.getZ()+.8);
            context.assertFalse(context.getWorld().isSpaceEmpty(body), "Ordinary world collision queries see the virtual surface");
            var pig = EntityType.PIG.create(context.getWorld());
            pig.setPosition(pos.getX()+.5, pos.getY()+1.2, pos.getZ()+.5);
            pig.move(MovementType.SELF, new Vec3d(0,-2,0));
            context.assertTrue(Math.abs(pig.getY() - (pos.getY()+HEIGHT)) < 1e-6 && pig.isOnGround(), "Entity movement lands precisely on the rendered surface");
            pig.move(MovementType.SELF,new Vec3d(.5,-.08,0));
            context.assertTrue(Math.abs(pig.getY() - (pos.getY()+HEIGHT)) < 1e-6, "Walking across the field has no seams");
            context.assertTrue(context.getWorld().getOtherEntities(null,span.bounds()).isEmpty(), "No support entities are spawned");
            context.removeBlock(EMITTER);
            context.runAtTick(7, () -> {
                context.assertTrue(!field(context).active() && context.getWorld().isSpaceEmpty(body), "Loss of power removes both surface and collision");
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "light_bridge", tickLimit = 25)
    public void sectionsShareTheBudgetWithAThreeBlockLimit(TestContext context) {
        var source=source(context, EMITTER);
        for (int i=0;i<4;i++) bridge(context,BRIDGE.south(i));
        for (int i=0;i<4;i++) context.setBlockState(BRIDGE.south(i).east(6),Blocks.STONE);
        context.runAtTick(4, () -> {
            var span=field(context);
            context.assertTrue(span.width()==3 && span.active(), "Three adjacent sections form one field");
            context.assertTrue(span.lumens()==source.opticalBudget(), "Widening cannot duplicate the incoming optical budget");
            var fourth=LightBridgeNetwork.at(context.getWorld(),context.getAbsolutePos(BRIDGE.south(3)));
            context.assertTrue(fourth.width()==1 && !fourth.active(), "Fourth section cannot borrow power across the three-wide group limit");
            context.removeBlock(BRIDGE.south(1));
            context.runAtTick(7, () -> {
                context.assertTrue(field(context).width()==1, "Removing a section splits the multiblock immediately");
                context.assertTrue(!LightBridgeNetwork.at(context.getWorld(),context.getAbsolutePos(BRIDGE.south(2))).active(), "Disconnected sections do not retain optical power");
                context.removeBlock(EMITTER); context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "light_bridge", tickLimit = 25)
    public void wholeWidthChecksActualBlockShapesAndRebuildsAfterMining(TestContext context) {
        source(context,EMITTER);
        bridge(context,BRIDGE); bridge(context,BRIDGE.south());
        context.setBlockState(BRIDGE.east(2),Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
        context.setBlockState(BRIDGE.south().east(4),Blocks.STONE);
        context.setBlockState(BRIDGE.east(6),Blocks.STONE);
        context.runAtTick(4, () -> {
            context.assertTrue(Math.abs(field(context).length()-(3+LEAD))<1e-6, "Top slab above the field passes; obstacle in the other lane stops the whole width");
            context.setBlockState(BRIDGE.east(2),Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.BOTTOM));
            context.runAtTick(7, () -> {
                context.assertTrue(Math.abs(field(context).length()-(1+LEAD))<1e-6,"Bottom slab intersects the thin field");
                context.removeBlock(BRIDGE.east(2)); context.removeBlock(BRIDGE.south().east(4));
                context.runAtTick(10, () -> {
                    context.assertTrue(Math.abs(field(context).length()-(5+LEAD))<1e-6,"Mining an obstacle restores the available length");
                    context.removeBlock(EMITTER); context.complete();
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "light_bridge")
    public void fiveInputsAndNoSavedGhostPower(TestContext context) {
        var bridge=bridge(context,BRIDGE);
        for(var side:Direction.values()) context.assertTrue(bridge.acceptsLaser(side,Vec3d.ofCenter(bridge.getPos()))==(side!=Direction.EAST),"Only the projecting face rejects laser input");
        bridge.receiveLight(80_000,0xFF0000);
        var restored=new LightBridgeBlockEntity(bridge.getPos(),bridge.getCachedState());
        restored.readNbt(bridge.createNbt());
        context.assertTrue(restored.input(context.getWorld().getTime())==0,"Saved NBT cannot restore a bridge without a live source");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "light_bridge", tickLimit = 20)
    public void breakingEmitterRemovesItsFieldImmediately(TestContext context) {
        source(context,EMITTER); bridge(context,BRIDGE);
        context.setBlockState(BRIDGE.east(6),Blocks.STONE);
        context.runAtTick(4, () -> {
            Box field=field(context).bounds();
            context.removeBlock(BRIDGE);
            context.assertTrue(LightBridgeNetwork.collisions(context.getWorld(),field).isEmpty(),"No stale collision remains after breaking the hardware");
            context.removeBlock(EMITTER); context.complete();
        });
    }

    private static LightBridgeSpan field(TestContext context) { return LightBridgeNetwork.at(context.getWorld(),context.getAbsolutePos(BRIDGE)); }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "light_bridge_solar", tickLimit = 90)
    public void directSolarPowerCreatesABridgeAndNightRemovesIt(TestContext context) {
        var world=context.getWorld();
        long time=world.getTimeOfDay();
        world.setTimeOfDay(6000); world.setWeather(0,0,false,false);
        context.setBlockState(EMITTER,net.askcraft.justifylasers.registry.ModIndustry.SMALL_SOLAR_CONCENTRATOR.getDefaultState()
                .with(net.askcraft.justifylasers.block.LaserComponentBlock.FACING,Direction.EAST));
        var source=(net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity)context.getBlockEntity(EMITTER);
        bridge(context,BRIDGE); context.setBlockState(BRIDGE.east(6),Blocks.STONE);
        context.runAtTick(30, () -> {
            context.assertTrue(field(context).active() && field(context).lumens()>15_000,"Small collector directly powers hard light without FE conversion");
            context.assertTrue(field(context).rgb()==source.beamRgb(),"Solar bridge retains the collector's warm white color");
            world.setTimeOfDay(18000);
            context.runAtTick(60, () -> {
                try { context.assertTrue(!field(context).active(),"Night removes the solar bridge");context.complete(); }
                finally { context.removeBlock(EMITTER);world.setTimeOfDay(time); }
            });
        });
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "light_bridge", tickLimit = 20)
    public void sneakingStopsAtTheEdgeAndJumpingIsNotBlocked(TestContext context) {
        source(context,EMITTER); bridge(context,BRIDGE);
        context.setBlockState(BRIDGE.east(6),Blocks.STONE);
        context.runAtTick(4, () -> {
            var box=field(context).bounds();
            var player=context.createMockSurvivalPlayer();
            player.setPosition(box.minX+2,box.maxY,box.maxZ-.1);
            player.setOnGround(true); player.setSneaking(true);
            player.move(MovementType.SELF,new Vec3d(0,-.08,2));
            context.assertTrue(player.getZ()<box.maxZ+.31 && Math.abs(player.getY()-box.maxY)<1e-6,"Sneaking keeps support at the bridge's side edge");
            player.setSneaking(false);
            player.move(MovementType.SELF,new Vec3d(0,.8,0));
            context.assertTrue(player.getY()>box.maxY+.7,"Jumping up from the bridge remains normal");
            context.removeBlock(EMITTER); context.complete();
        });
    }
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "light_bridge", tickLimit = 85)
    public void mountedFieldsGroupAndMeetObstaclesInAllSixDirections(TestContext context) {
        Direction[] mounts = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};
        var origin = new BlockPos(4, 6, 4);
        for (boolean rolled : new boolean[]{false, true}) for (Direction facing : Direction.values()) {
            Direction mount = mounts[facing.ordinal()];
            var frame = new net.askcraft.justifylasers.bridge.BridgeOrientation(facing, mount, rolled);
            int start = 1 + ((rolled ? 6 : 0) + facing.ordinal()) * 6;
            context.runAtTick(start, () -> {
                var state = ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING, facing).with(LightBridgeBlock.MOUNT, mount).with(LightBridgeBlock.ROLLED, rolled);
                context.setBlockState(origin, state); context.setBlockState(origin.offset(frame.across()), state);
                var source = source(context, origin.offset(facing.getOpposite(), 2));
                context.getWorld().setBlockState(source.getPos(), source.getCachedState().with(LaserEmitterBlock.FACING, facing));
                for (int lane = 0; lane < 2; lane++) context.setBlockState(origin.offset(frame.across(), lane).offset(facing, 4), Blocks.STONE);
            });
            context.runAtTick(start + 4, () -> {
                var span = LightBridgeNetwork.at(context.getWorld(), context.getAbsolutePos(origin));
                context.assertTrue(span != null && span.width() == 2 && span.facing() == facing && span.mount() == mount && span.rolled() == rolled,
                        "Grouping retains the full orientation: " + facing + "/" + mount + "/rolled=" + rolled);
                context.assertTrue(Math.abs(span.length() - 3 - frame.leadIn()) < 1e-6, "Field reaches the next block without a gap on " + facing);
                var body = span.bounds();
                context.assertFalse(context.getWorld().isSpaceEmpty(body.contract(.001)), "Mounted light retains collision: " + facing);
                context.removeBlock(origin.offset(facing.getOpposite(), 2));
                for (int lane = 0; lane < 2; lane++) {
                    context.removeBlock(origin.offset(frame.across(), lane));
                    context.removeBlock(origin.offset(frame.across(), lane).offset(facing, 4));
                }
                if (rolled && facing == Direction.EAST) context.complete();
            });
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bridge_login", tickLimit = 70)
    public void loginCheckpointProvidesTemporaryCollisionAndCannotKeepAnUnpoweredBridge(TestContext context) {
        bridge(context, BRIDGE); source(context, EMITTER);
        var player = context.createMockCreativeServerPlayerInWorld();
        context.runAtTick(4, () -> {
            var field = field(context);
            Vec3d spot = field.bounds().getCenter();
            player.setPosition(spot.x, field.bounds().maxY, spot.z);
            var saved = player.writeNbt(new net.minecraft.nbt.NbtCompound());
            context.assertTrue(saved.contains(net.askcraft.justifylasers.bridge.BridgeJoinSupport.KEY), "Player save contains an actual supported bridge checkpoint");
            LightBridgeNetwork.removeFieldsAt(context.getWorld(), context.getAbsolutePos(BRIDGE));
            context.removeBlock(EMITTER);
            player.readNbt(saved);
            player.onSpawn();
            Box feet = player.getBoundingBox().offset(0, -.02, 0);
            context.assertFalse(LightBridgeNetwork.collisions(context.getWorld(), feet).isEmpty(), "Login restores collision before the first player tick");
            player.tick();
            context.runAtTick(48, () -> {
                context.assertTrue(LightBridgeNetwork.collisions(context.getWorld(), feet).isEmpty(), "Temporary login support expires without power");
                player.discard(); context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bridge_login", tickLimit = 20)
    public void bridgeCheckpointDoesNotFollowTeleportOrDimensionChanges(TestContext context) {
        var player = context.createMockCreativeServerPlayerInWorld();
        player.setPosition(Vec3d.ofCenter(context.getAbsolutePos(BRIDGE)));
        context.assertTrue(net.askcraft.justifylasers.bridge.BridgeJoinSupport.checkpoint(player).isEmpty(), "Ordinary air does not create a checkpoint");
        var saved = new net.minecraft.nbt.NbtCompound();
        saved.putLong("Emitter", context.getAbsolutePos(BRIDGE).asLong());
        saved.putDouble("X", player.getX()); saved.putDouble("Y", player.getY()); saved.putDouble("Z", player.getZ());
        saved.putString("Dimension", "minecraft:the_nether");
        net.askcraft.justifylasers.bridge.BridgeJoinSupport.restore(player, saved);
        context.assertTrue(LightBridgeNetwork.collisions(context.getWorld(), player.getBoundingBox().offset(0, -.02, 0)).isEmpty(), "No cross-dimension landing surface");
        player.discard(); context.complete();
    }

    private static LightBridgeBlockEntity bridge(TestContext context,BlockPos pos) {
        context.setBlockState(pos,ModBlocks.LIGHT_BRIDGE.getDefaultState().with(LightBridgeBlock.FACING,Direction.EAST));
        return (LightBridgeBlockEntity)context.getBlockEntity(pos);
    }
    private static LaserEmitterBlockEntity source(TestContext context,BlockPos pos) {
        context.setBlockState(pos,ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING,Direction.EAST));
        var source=(LaserEmitterBlockEntity)context.getBlockEntity(pos);
        source.setStack(0,new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.CYAN)));
        source.setStack(LaserModule.RANGE.slot(),new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE));
        source.energy().restore(100_000);
        return source;
    }
}
