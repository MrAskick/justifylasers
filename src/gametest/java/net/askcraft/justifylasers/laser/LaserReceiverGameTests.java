package net.askcraft.justifylasers.laser;

import io.netty.buffer.Unpooled;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserReceiverBlockEntity;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.screen.LaserReceiverScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class LaserReceiverGameTests implements FabricGameTest {
    private static final BlockPos RECEIVER = new BlockPos(4, 4, 4);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyOrientationHasOneInputAndFiveOutputs(TestContext context) {
        for (Direction front : Direction.values()) {
            Fixture fixture = fixture(context, front);
            fixture.receiver.getPropertyDelegate().set(1, 9);
            fixture.tick(context);
            BlockState state = fixture.receiver.getCachedState();
            context.assertTrue(state.get(LaserReceiverBlock.POWER) == 9, "Front input must work when facing " + front);
            for (Direction side : Direction.values()) {
                int expected = side == front ? 0 : 9;
                context.assertTrue(context.getWorld().getReceivedRedstonePower(fixture.receiver.getPos().offset(side)) == expected,
                        "Neighbor power on " + side + " for receiver facing " + front);
                context.assertTrue(state.getStrongRedstonePower(context.getWorld(), fixture.receiver.getPos(), side.getOpposite()) == expected,
                        "Strong power on " + side + " for receiver facing " + front);
            }
            context.removeBlock(RECEIVER.offset(front, 2));
            context.removeBlock(RECEIVER);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sidesAndRearCannotActivateReceiver(TestContext context) {
        Fixture fixture = fixture(context, Direction.NORTH);
        context.removeBlock(RECEIVER.north(2));
        for (Direction side : Direction.values()) {
            if (side == Direction.NORTH) {
                continue;
            }
            BlockPos emitterPos = RECEIVER.offset(side, 2);
            LaserEmitterBlockEntity emitter = placeEmitter(context, emitterPos, side.getOpposite());
            tickEmitter(context, emitter);
            tickReceiver(context, fixture.receiver);
            context.assertTrue(fixture.receiver.getCachedState().get(LaserReceiverBlock.POWER) == 0,
                    "A beam arriving from " + side + " must not activate the front input");
            context.removeBlock(emitterPos);
        }
        finish(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void obstructionDisablingAndRemovalClearInput(TestContext context) {
        Fixture fixture = fixture(context, Direction.NORTH);
        fixture.tick(context);
        power(context, fixture.receiver, 15);
        context.setBlockState(RECEIVER.north(), Blocks.OBSIDIAN);
        fixture.tick(context);
        power(context, fixture.receiver, 0);
        context.removeBlock(RECEIVER.north());
        fixture.tick(context);
        power(context, fixture.receiver, 15);
        fixture.emitter.getPropertyDelegate().set(0, 0);
        fixture.tick(context);
        power(context, fixture.receiver, 0);
        fixture.emitter.getPropertyDelegate().set(0, 1);
        fixture.tick(context);
        power(context, fixture.receiver, 15);
        context.removeBlock(RECEIVER.north(2));
        tickReceiver(context, fixture.receiver);
        power(context, fixture.receiver, 0);
        finish(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void emitterMustActuallyPointAtReceiver(TestContext context) {
        Fixture fixture = fixture(context, Direction.NORTH);
        BlockState turned = fixture.emitter.getCachedState().with(LaserEmitterBlock.FACING, Direction.NORTH);
        context.setBlockState(RECEIVER.north(2), turned);
        fixture.tick(context);
        power(context, fixture.receiver, 0);
        finish(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inversionAndMasterSwitchFollowTheirTruthTable(TestContext context) {
        Fixture fixture = fixture(context, Direction.NORTH);
        fixture.receiver.getPropertyDelegate().set(1, 6);
        for (boolean enabled : new boolean[]{false, true}) {
            for (boolean inverted : new boolean[]{false, true}) {
                for (boolean beam : new boolean[]{false, true}) {
                    fixture.receiver.getPropertyDelegate().set(0, enabled ? 1 : 0);
                    fixture.receiver.getPropertyDelegate().set(2, inverted ? 1 : 0);
                    fixture.emitter.getPropertyDelegate().set(0, beam ? 1 : 0);
                    fixture.tick(context);
                    power(context, fixture.receiver, enabled && (beam != inverted) ? 6 : 0);
                }
            }
        }
        finish(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void colorsAndFilterAreIndependentOfShaderEmission(TestContext context) {
        Fixture fixture = fixture(context, Direction.NORTH);
        for (LaserColor color : LaserColor.values()) {
            fixture.emitter.getPropertyDelegate().set(2, color.ordinal());
            fixture.receiver.getPropertyDelegate().set(3, color.ordinal() + 1);
            fixture.tick(context);
            power(context, fixture.receiver, 15);
            context.assertTrue(fixture.receiver.getCachedState().get(LaserReceiverBlock.COLOR) == color, "Model must follow " + color);
            context.assertTrue(fixture.receiver.getCachedState().get(LaserReceiverBlock.LIT), "Received beam must light the material");
            fixture.receiver.getPropertyDelegate().set(3, color.next().ordinal() + 1);
            fixture.tick(context);
            power(context, fixture.receiver, 0);
            context.assertTrue(fixture.receiver.getCachedState().get(LaserReceiverBlock.COLOR) == color, "Rejected colors must still be displayed");
        }
        fixture.receiver.getPropertyDelegate().set(3, 0);
        fixture.receiver.getPropertyDelegate().set(7, 0);
        fixture.tick(context);
        power(context, fixture.receiver, 15);
        context.assertFalse(fixture.receiver.getCachedState().get(LaserReceiverBlock.LIT), "Shader emission toggle must work independently");
        finish(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void wireGetsSelectedStrengthAndTurnsOffOnRemoval(TestContext context) {
        Fixture fixture = fixture(context, Direction.NORTH);
        BlockPos wirePos = RECEIVER.south();
        context.setBlockState(wirePos.down(), Blocks.STONE);
        context.setBlockState(wirePos, Blocks.REDSTONE_WIRE);
        for (int strength = 0; strength <= 15; strength++) {
            fixture.receiver.getPropertyDelegate().set(1, strength);
            fixture.tick(context);
            context.assertTrue(context.getBlockState(wirePos).get(RedstoneWireBlock.POWER) == strength,
                    "Wire must receive strength " + strength);
        }
        context.removeBlock(RECEIVER);
        context.assertTrue(context.getBlockState(wirePos).get(RedstoneWireBlock.POWER) == 0, "Removing the receiver must depower the wire");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void strongPowerReachesLampThroughSolidBlock(TestContext context) {
        Fixture fixture = fixture(context, Direction.NORTH);
        context.setBlockState(RECEIVER.south(), Blocks.STONE);
        context.setBlockState(RECEIVER.south(2), Blocks.REDSTONE_LAMP);
        fixture.tick(context);
        context.assertTrue(context.getBlockState(RECEIVER.south(2)).get(RedstoneLampBlock.LIT), "Receiver must power through a solid block");
        fixture.receiver.handleButton(LaserReceiverScreenHandler.BUTTON_ENABLED);
        context.runAtTick(6, () -> {
            context.assertFalse(context.getBlockState(RECEIVER.south(2)).get(RedstoneLampBlock.LIT), "Lamp must turn off after receiver is disabled");
            finish(context);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inputFaceSurvivesDestructiveBeam(TestContext context) {
        Fixture fixture = fixture(context, Direction.NORTH);
        fixture.emitter.getPropertyDelegate().set(3, 1);
        for (int tick = 0; tick < 650; tick++) {
            fixture.tick(context);
        }
        context.assertTrue(context.getBlockState(RECEIVER).isOf(ModBlocks.LASER_RECEIVER), "Optical input must withstand a mining laser");
        power(context, fixture.receiver, 15);
        fixture.emitter.getPropertyDelegate().set(3, 0);
        finish(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void receiverSettingsSaveAndSynchronize(TestContext context) {
        Fixture fixture = fixture(context, Direction.NORTH);
        PlayerEntity player = context.createMockSurvivalPlayer();
        player.setPosition(Vec3d.ofCenter(fixture.receiver.getPos()));
        LaserReceiverScreenHandler serverMenu = new LaserReceiverScreenHandler(22, player.getInventory(), fixture.receiver);
        PacketByteBuf data = new PacketByteBuf(Unpooled.buffer());
        LaserReceiverScreenHandler clientMenu;
        try {
            data.writeBlockPos(fixture.receiver.getPos());
            clientMenu = new LaserReceiverScreenHandler(22, player.getInventory(), data);
        } finally {
            data.release();
        }
        serverMenu.addListener(new ScreenHandlerListener() {
            @Override
            public void onSlotUpdate(ScreenHandler handler, int slotId, ItemStack stack) {
            }

            @Override
            public void onPropertyUpdate(ScreenHandler handler, int property, int value) {
                clientMenu.setProperty(property, value);
            }
        });
        for (int count = 0; count < 6; count++) {
            context.assertTrue(serverMenu.onButtonClick(player, LaserReceiverScreenHandler.BUTTON_SIGNAL_DOWN), "Signal decrement");
        }
        serverMenu.onButtonClick(player, LaserReceiverScreenHandler.BUTTON_INVERTED);
        serverMenu.onButtonClick(player, LaserReceiverScreenHandler.BUTTON_COLOR_FILTER);
        serverMenu.onButtonClick(player, LaserReceiverScreenHandler.BUTTON_LIGHT_EMISSION);
        context.assertTrue(clientMenu.getSignalStrength() == 9, "Signal must synchronize");
        context.assertTrue(clientMenu.isInverted(), "Inversion must synchronize");
        context.assertTrue(clientMenu.getColorFilter() == 1, "Filter must synchronize");
        context.assertFalse(clientMenu.isLightEmissionEnabled(), "Emission must synchronize");
        NbtCompound saved = fixture.receiver.createNbt();
        LaserReceiverBlockEntity restored = new LaserReceiverBlockEntity(RECEIVER, ModBlocks.LASER_RECEIVER.getDefaultState());
        restored.readNbt(saved);
        context.assertTrue(saved.equals(restored.createNbt()), "Settings must survive saving");
        context.assertFalse(serverMenu.onButtonClick(player, 6), "Unknown button must be rejected");
        player.setPosition(player.getPos().add(20, 0, 0));
        context.assertFalse(serverMenu.onButtonClick(player, 0), "Out-of-reach interaction must be rejected");
        context.assertTrue(context.getWorld().getRecipeManager().get(JustifyLasers.id("laser_receiver")).isPresent(), "Receiver recipe must load");
        finish(context);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void receiverSettingsClampInvalidSavedValues(TestContext context) {
        LaserReceiverBlockEntity receiver = new LaserReceiverBlockEntity(RECEIVER, ModBlocks.LASER_RECEIVER.getDefaultState());
        receiver.readNbt(new NbtCompound());
        context.assertTrue(receiver.getPropertyDelegate().get(0) == 1, "Default master switch");
        context.assertTrue(receiver.getPropertyDelegate().get(1) == 15, "Default signal");
        context.assertTrue(receiver.getPropertyDelegate().get(3) == 0, "Default filter");
        NbtCompound saved = new NbtCompound();
        saved.putInt("SignalStrength", Integer.MAX_VALUE);
        saved.putInt("ColorFilter", -1);
        receiver.readNbt(saved);
        context.assertTrue(receiver.getPropertyDelegate().get(1) == 15, "Maximum signal must be clamped");
        context.assertTrue(receiver.getPropertyDelegate().get(3) == 0, "Negative filter must be clamped");
        saved.putInt("SignalStrength", -1);
        saved.putInt("ColorFilter", Integer.MAX_VALUE);
        receiver.readNbt(saved);
        context.assertTrue(receiver.getPropertyDelegate().get(1) == 0, "Negative signal must be clamped");
        context.assertTrue(receiver.getPropertyDelegate().get(3) == 9, "Maximum filter must be clamped");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void receiverUsesTheFullBeamRangeButNotBeyondIt(TestContext context) {
        BlockPos receiverPos = context.getAbsolutePos(RECEIVER).up(16);
        context.getWorld().setBlockState(receiverPos,
                ModBlocks.LASER_RECEIVER.getDefaultState().with(LaserReceiverBlock.FACING, Direction.UP), Block.NOTIFY_ALL);
        LaserReceiverBlockEntity receiver = (LaserReceiverBlockEntity) context.getWorld().getBlockEntity(receiverPos);
        for (int distance : new int[]{63, 64, 65}) {
            BlockPos emitterPos = receiverPos.up(distance);
            context.assertTrue(context.getWorld().isChunkLoaded(emitterPos), "Range test must stay in its loaded chunk");
            context.getWorld().setBlockState(emitterPos,
                    ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.DOWN), Block.NOTIFY_ALL);
            LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) context.getWorld().getBlockEntity(emitterPos);
            tickEmitter(context, emitter);
            tickReceiver(context, receiver);
            power(context, receiver, distance <= 64 ? 15 : 0);
            context.getWorld().removeBlock(emitterPos, false);
        }
        context.getWorld().removeBlock(receiverPos, false);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void receptionIsContinuousRegardlessOfHitRate(TestContext context) {
        Fixture fixture = fixture(context, Direction.NORTH);
        fixture.emitter.getPropertyDelegate().set(11, 1);
        context.runAtEveryTick(() -> {
            if (context.getTick() >= 3 && context.getTick() <= 22) {
                power(context, fixture.receiver, 15);
            }
        });
        context.runAtTick(24, () -> fixture.emitter.getPropertyDelegate().set(0, 0));
        context.runAtTick(26, () -> {
            power(context, fixture.receiver, 0);
            finish(context);
        });
    }

    private static Fixture fixture(TestContext context, Direction front) {
        for (Direction direction : Direction.values()) {
            context.setBlockState(RECEIVER.offset(direction, 3), Blocks.OBSIDIAN);
        }
        context.setBlockState(RECEIVER, ModBlocks.LASER_RECEIVER.getDefaultState().with(LaserReceiverBlock.FACING, front));
        LaserReceiverBlockEntity receiver = (LaserReceiverBlockEntity) context.getBlockEntity(RECEIVER);
        LaserEmitterBlockEntity emitter = placeEmitter(context, RECEIVER.offset(front, 2), front.getOpposite());
        return new Fixture(emitter, receiver);
    }

    private static LaserEmitterBlockEntity placeEmitter(TestContext context, BlockPos pos, Direction facing) {
        context.setBlockState(pos, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, facing));
        return (LaserEmitterBlockEntity) context.getBlockEntity(pos);
    }

    private static void tickEmitter(TestContext context, LaserEmitterBlockEntity emitter) {
        LaserEmitterBlockEntity.serverTick(context.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
    }

    private static void tickReceiver(TestContext context, LaserReceiverBlockEntity receiver) {
        LaserReceiverBlockEntity.serverTick(context.getWorld(), receiver.getPos(), receiver.getCachedState(), receiver);
    }

    private static void power(TestContext context, LaserReceiverBlockEntity receiver, int expected) {
        int actual = receiver.getCachedState().get(LaserReceiverBlock.POWER);
        context.assertTrue(actual == expected, "Expected redstone " + expected + ", got " + actual);
    }

    private static void finish(TestContext context) {
        context.removeBlock(RECEIVER);
        context.complete();
    }

    private record Fixture(LaserEmitterBlockEntity emitter, LaserReceiverBlockEntity receiver) {
        private void tick(TestContext context) {
            tickEmitter(context, emitter);
            tickReceiver(context, receiver);
        }
    }
}
