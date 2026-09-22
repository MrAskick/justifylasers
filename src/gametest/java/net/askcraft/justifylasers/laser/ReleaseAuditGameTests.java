package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.industry.ChamberStructure;
import net.askcraft.justifylasers.industry.SolarStructure;
import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.askcraft.justifylasers.item.LaserAmplifierItem;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.printing.EncoderDraft;
import net.askcraft.justifylasers.printing.PrintExamples;
import net.askcraft.justifylasers.printing.VoxelGrid;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.screen.ModelEncoderScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Regressions found during the 2.0 release audit; always run with the main suite. */
public class ReleaseAuditGameTests implements FabricGameTest {
    private static final BlockPos SOURCE = new BlockPos(1, 4, 3);
    private static final BlockPos TARGET = new BlockPos(4, 4, 3);

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "release_performance")
    public void mirrorCollisionCacheTracksAimAndMountButNotLight(TestContext context) {
        context.setBlockState(TARGET, ModBlocks.LASER_MIRROR.getDefaultState());
        var mirror = (net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity) context.getBlockEntity(TARGET);
        var support = mirror.supportShape();
        var shape = mirror.mirrorShape();
        for (int i = 0; i < 100; i++) {
            context.assertTrue(support == mirror.supportShape(), "Unchanged supports must reuse the exact shape");
            context.assertTrue(shape == mirror.mirrorShape(), "Unchanged outlines must reuse the exact shape");
        }
        mirror.aim(OpticalGeometry.normal(37, 19));
        context.assertTrue(support != mirror.supportShape() && shape != mirror.mirrorShape(), "Aiming invalidates both cached shapes");
        support = mirror.supportShape();
        context.setBlockState(TARGET, mirror.getCachedState().with(net.askcraft.justifylasers.block.LaserOpticBlock.LIT, true));
        context.assertTrue(support == mirror.supportShape(), "Light changes do not change collision geometry");
        var mount = mirror.facing() == Direction.UP ? Direction.DOWN : Direction.UP;
        context.setBlockState(TARGET, mirror.getCachedState().with(net.askcraft.justifylasers.block.LaserOpticBlock.FACING, mount));
        context.assertTrue(support != mirror.supportShape(), "Changing the mounting side invalidates geometry");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "release_security")
    public void laserCannotDestroyAnotherPlayersPrivateEmitter(TestContext context) {
        context.setBlockState(TARGET, ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
        var target = (LaserEmitterBlockEntity) context.getBlockEntity(TARGET);
        var owner = context.createMockSurvivalPlayer();
        var stranger = context.createMockSurvivalPlayer();
        stranger.setUuid(UUID.randomUUID());
        target.initializeOwner(owner);
        context.assertTrue(target.togglePrivacy(owner) && !target.canAccess(stranger), "Fixture is privately owned");
        fire(context);
        context.assertTrue(context.getBlockEntity(TARGET) == target, "A mining beam must not bypass private emitter ownership");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "release_security")
    public void laserCannotDestroyAnotherPlayersPrivateEncoder(TestContext context) {
        context.setBlockState(TARGET, ModIndustry.MACHINES.get(MachineKind.MODEL_ENCODER).getDefaultState());
        var target = (IndustrialMachineBlockEntity) context.getBlockEntity(TARGET);
        var owner = context.createMockSurvivalPlayer();
        var stranger = context.createMockSurvivalPlayer();
        stranger.setUuid(UUID.randomUUID());
        target.initializeOwner(owner);
        context.assertTrue(target.togglePrivacy(owner) && !target.canAccess(stranger), "Fixture is privately owned");
        fire(context);
        context.assertTrue(context.getBlockEntity(TARGET) == target, "A mining beam must not bypass private machine ownership");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "release_security")
    public void draftUploadsCannotRepeatedlyAllocateMaximumBuffersInOneTick(TestContext context) {
        context.setBlockState(TARGET, ModIndustry.MACHINES.get(MachineKind.MODEL_ENCODER).getDefaultState());
        var machine = (IndustrialMachineBlockEntity) context.getBlockEntity(TARGET);
        var player = context.createMockSurvivalPlayer();
        player.setPosition(Vec3d.ofCenter(machine.getPos()));
        machine.initializeOwner(player);
        var menu = new ModelEncoderScreenHandler(91, player.getInventory(), machine);
        player.currentScreenHandler = menu;
        try {
            String length = Integer.toString(EncoderDraft.MAX_JSON);
            var request = new LaserSettingsPacket(91, ModelEncoderScreenHandler.DRAFT_BEGIN, length);
            context.assertTrue(request.apply(player), "The initial draft upload is valid");
            context.assertFalse(request.apply(player), "Repeated same-tick DRAFT_BEGIN must be rate limited like BEGIN");
        } finally {
            menu.onClosed(player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "release_security")
    public void protectedInventoriesSurviveAllMiningModesAndPublicMiningStillWorks(TestContext context) {
        context.setBlockState(TARGET, ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
        var target = (LaserEmitterBlockEntity) context.getBlockEntity(TARGET);
        var owner = context.createMockSurvivalPlayer();
        target.initializeOwner(owner);
        context.assertTrue(target.togglePrivacy(owner), "Private fixture");
        target.setStack(LaserEmitterBlockEntity.STORAGE_START, new ItemStack(Items.DIAMOND, 7));
        var pos = target.getPos();
        for (int flags = 0; flags < 8; flags++) {
            context.assertFalse(MiningCollection.breakBlock(context.getWorld(), pos, (flags & 1) != 0, (flags & 2) != 0,
                    (flags & 4) != 0, stack -> { throw new AssertionError("Protected inventory leaked a drop"); }), "Private blocks reject all automatic mining modes");
            context.assertTrue(context.getWorld().getBlockEntity(pos) == target
                    && target.getStack(LaserEmitterBlockEntity.STORAGE_START).getCount() == 7, "No removal or inventory clearing before the permission check");
        }
        context.assertTrue(target.canAccess(owner), "Manual owner access remains available");
        target.togglePrivacy(owner);
        context.assertTrue(LaserMining.breakBlock(context.getWorld(), pos, false, false), "Public block remains mineable");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "release_security")
    public void privateMachineProtectsEveryMultiblockPart(TestContext context) {
        var origin = context.getAbsolutePos(new BlockPos(2, 2, 2));
        for (var pos : ChamberStructure.positions(origin)) context.getWorld().setBlockState(pos, ModIndustry.MACHINES.get(MachineKind.ASSEMBLY_CHAMBER).getDefaultState());
        var controller = (IndustrialMachineBlockEntity) context.getWorld().getBlockEntity(origin);
        context.assertTrue(ChamberStructure.form(controller), "Chamber assembled");
        var owner = context.createMockSurvivalPlayer();
        controller.initializeOwner(owner);
        controller.togglePrivacy(owner);
        for (var pos : ChamberStructure.positions(origin))
            context.assertFalse(LaserMining.breakBlock(context.getWorld(), pos, false, false), "All eight private parts protected: " + pos);
        context.assertTrue(controller.formed(), "Rejected mining leaves the structure formed");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "release_security")
    public void privateSolarControllerProtectsTheEntireConcentrator(TestContext context) {
        var origin = context.getAbsolutePos(new BlockPos(2, 1, 2));
        for (var cell : SolarStructure.PARTS) context.getWorld().setBlockState(origin.add(cell.offset()), ModIndustry.COMPONENT_BLOCKS.get(cell.component()).getDefaultState());
        var controller = (SolarConcentratorBlockEntity) context.getWorld().getBlockEntity(origin.add(SolarStructure.RESONATORS.get(0).offset()));
        context.assertTrue(SolarStructure.form(controller), "Solar structure assembled");
        var owner = context.createMockSurvivalPlayer();
        owner.setPosition(Vec3d.ofCenter(controller.getPos()));
        controller.initializeOwner(owner);
        context.assertTrue(controller.togglePrivacy(owner), "Private solar structure");
        for (var cell : SolarStructure.PARTS)
            context.assertFalse(LaserMining.breakBlock(context.getWorld(), origin.add(cell.offset()), true, false), "Solar member protected: " + cell);
        context.assertTrue(controller.operationalStructure(), "Rejected mining leaves the concentrator assembled");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "release_security")
    public void rejectedBeginDoesNotReplaceTheActiveDraftAndCooldownRecovers(TestContext context) {
        context.setBlockState(TARGET, ModIndustry.MACHINES.get(MachineKind.MODEL_ENCODER).getDefaultState());
        var machine = (IndustrialMachineBlockEntity) context.getBlockEntity(TARGET);
        var player = context.createMockSurvivalPlayer();
        player.setPosition(Vec3d.ofCenter(machine.getPos()));
        machine.initializeOwner(player);
        var menu = new ModelEncoderScreenHandler(93, player.getInventory(), machine);
        String json = new EncoderDraft(null, "Kept draft", true, VoxelGrid.Material.color(0xFFFFFF), 0, 1, 1, false).json();
        context.assertTrue(menu.receive(player, ModelEncoderScreenHandler.DRAFT_BEGIN, Integer.toString(json.length())), "First draft accepted");
        context.assertFalse(menu.receive(player, ModelEncoderScreenHandler.BEGIN, "40000"), "An active draft cannot be replaced by a card upload");
        context.assertFalse(menu.receive(player, ModelEncoderScreenHandler.DRAFT_BEGIN, "50000"), "Repeated draft begin rejected");
        context.assertTrue(menu.receive(player, ModelEncoderScreenHandler.CHUNK, "0:" + json), "Original upload still accepts its data");
        context.assertTrue(menu.receive(player, ModelEncoderScreenHandler.DRAFT_COMMIT, ""), "Original draft commits after rejected replacement");
        context.assertTrue(machine.encoderDraft().name().equals("Kept draft"), "The correct draft survived");
        context.assertFalse(menu.receive(player, ModelEncoderScreenHandler.DRAFT_BEGIN, Integer.toString(json.length())), "Completed drafts are also rate limited");
        context.runAtTick(ModelEncoderScreenHandler.UPLOAD_INTERVAL + 1, () -> {
            context.assertTrue(menu.receive(player, ModelEncoderScreenHandler.DRAFT_BEGIN, Integer.toString(json.length())), "A later autosave is allowed");
            context.assertTrue(menu.receive(player, ModelEncoderScreenHandler.CHUNK, "0:" + json), "Later draft chunk");
            context.assertTrue(menu.receive(player, ModelEncoderScreenHandler.DRAFT_COMMIT, ""), "Later draft commits");
            menu.onClosed(player);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "release_security")
    public void autosavingDoesNotThrottleAnImmediateCardWrite(TestContext context) {
        context.setBlockState(TARGET, ModIndustry.MACHINES.get(MachineKind.MODEL_ENCODER).getDefaultState());
        var machine = (IndustrialMachineBlockEntity) context.getBlockEntity(TARGET);
        var player = context.createMockSurvivalPlayer();
        player.setPosition(Vec3d.ofCenter(machine.getPos()));
        machine.initializeOwner(player);
        machine.energy().restore(10000);
        machine.setStack(0, new ItemStack(ModIndustry.BLANK_SCHEMATIC));
        var menu = new ModelEncoderScreenHandler(94, player.getInventory(), machine);
        var model = PrintExamples.pedestal();
        String draft = new EncoderDraft(model, model.name(), true, VoxelGrid.Material.color(0xFFFFFF), 0, 1, 1, false).json();
        context.assertTrue(menu.receive(player, ModelEncoderScreenHandler.DRAFT_BEGIN, Integer.toString(draft.length())), "Autosave starts");
        sendChunks(context, menu, player, draft);
        context.assertTrue(menu.receive(player, ModelEncoderScreenHandler.DRAFT_COMMIT, ""), "Autosave commits");
        context.assertTrue(menu.receive(player, ModelEncoderScreenHandler.BEGIN, Integer.toString(model.json().length())), "Card write has its own bounded budget");
        sendChunks(context, menu, player, model.json());
        context.assertTrue(menu.receive(player, ModelEncoderScreenHandler.COMMIT, ""), "Card write commits in the same tick as autosave");
        context.assertTrue(machine.getStack(IndustrialMachineBlockEntity.OUTPUT).isOf(ModIndustry.MODEL_SCHEMATIC), "A paid schematic was produced");
        menu.onClosed(player);
        context.complete();
    }

    private static void sendChunks(TestContext context, ModelEncoderScreenHandler menu, net.minecraft.entity.player.PlayerEntity player, String json) {
        for (int offset = 0, sequence = 0; offset < json.length(); offset += ModelEncoderScreenHandler.CHUNK_SIZE, sequence++) {
            String chunk = json.substring(offset, Math.min(json.length(), offset + ModelEncoderScreenHandler.CHUNK_SIZE));
            context.assertTrue(menu.receive(player, ModelEncoderScreenHandler.CHUNK, sequence + ":" + chunk), "Upload chunk " + sequence);
        }
    }

    private static void fire(TestContext context) {
        context.setBlockState(new BlockPos(6, 4, 3), Blocks.BEDROCK);
        context.setBlockState(SOURCE, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        var source = (LaserEmitterBlockEntity) context.getBlockEntity(SOURCE);
        var attacker = context.createMockSurvivalPlayer();
        attacker.setUuid(UUID.randomUUID());
        source.initializeOwner(attacker);
        source.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
        source.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE));
        source.setStack(LaserModule.BLOCK_DESTRUCTION.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_DESTRUCTION)));
        var amplifier = LaserAmplifierItem.stack(4);
        amplifier.setCount(64);
        source.setStack(LaserEmitterBlockEntity.AMPLIFIER_SLOT, amplifier);
        source.energy().restore(Integer.MAX_VALUE);
        source.getPropertyDelegate().set(3, 1);
        source.getPropertyDelegate().set(13, 8);
        source.getPropertyDelegate().set(14, 100);
        try {
            LaserEmitterBlockEntity.serverTick(source.getWorld(), source.getPos(), source.getCachedState(), source);
            context.assertTrue(source.isBeamActive(), "The attack uses a paid, survival-type emitter");
        } finally {
            context.removeBlock(SOURCE);
        }
    }
}
