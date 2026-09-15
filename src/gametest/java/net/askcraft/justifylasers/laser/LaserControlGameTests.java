package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.item.LaserConfiguratorItem;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

import java.util.UUID;

public class LaserControlGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void configuratorCyclesOnlyTheClickedPortInBothDirections(TestContext context) {
        BlockPos relative = new BlockPos(3, 3, 3);
        var player = context.createMockCreativeServerPlayerInWorld();
        ItemStack tool = new ItemStack(ModBlocks.CONFIGURATOR);
        player.setStackInHand(Hand.MAIN_HAND, tool);
        for (var block : new LaserOpticBlock[]{ModBlocks.BEAM_SPLITTER, ModBlocks.ENERGY_RECEIVER}) {
            context.setBlockState(relative, block.getDefaultState().with(LaserOpticBlock.FACING, Direction.WEST));
            var optic = (LaserOpticBlockEntity) context.getBlockEntity(relative);
            optic.energy().restore(1234);
            player.setPosition(Vec3d.ofCenter(optic.getPos().north(2)));
            for (Direction side : Direction.values()) {
                OpticPortMode original = optic.portMode(side);
                var otherPorts = new java.util.EnumMap<Direction, OpticPortMode>(Direction.class);
                for (Direction other : Direction.values()) otherPorts.put(other, optic.portMode(other));
                for (boolean reverse : new boolean[]{false, true}) {
                    player.setSneaking(reverse);
                    for (int step = 0; step < 3; step++) {
                        OpticPortMode expected = optic.portMode(side).cycle(reverse);
                        var hit = new BlockHitResult(Vec3d.ofCenter(optic.getPos()).add(Vec3d.of(side.getVector()).multiply(0.5)),
                                side, optic.getPos(), false);
                        context.assertTrue(tool.getItem().useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(),
                                "Configurator accepts " + side + " port interaction");
                        context.assertTrue(optic.portMode(side) == expected, "Clicked port cycles in the requested direction");
                        for (Direction other : Direction.values()) if (other != side)
                            context.assertTrue(optic.portMode(other) == otherPorts.get(other), "Other ports remain unchanged");
                        context.assertTrue(context.getWorld().getBlockEntity(optic.getPos()) == optic && optic.energy().stored() == 1234
                                && optic.facing() == Direction.WEST, "Port editing preserves the entity, orientation and buffer");
                    }
                    context.assertTrue(optic.portMode(side) == original, "Three clicks complete the mode cycle");
                }
            }
            context.removeBlock(relative);
        }
        player.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void configuratorItemRotatesInPlaceAndCyclesItsSettingsClipboard(TestContext context) {
        var world = context.getWorld();
        BlockPos relative = new BlockPos(3, 3, 3);
        context.setBlockState(relative, ModBlocks.POWERED_LASER_EMITTER);
        var emitter = (LaserEmitterBlockEntity) context.getBlockEntity(relative);
        var player = context.createMockCreativeServerPlayerInWorld();
        player.setPosition(Vec3d.ofCenter(emitter.getPos().north(2)));
        emitter.initializeOwner(player);
        emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.BLUE)));
        emitter.energy().restore(1234);
        ItemStack tool = new ItemStack(ModBlocks.CONFIGURATOR);
        player.setStackInHand(Hand.MAIN_HAND, tool);
        var item = (LaserConfiguratorItem) tool.getItem();
        for (Direction side : Direction.values()) {
            var hit = new BlockHitResult(Vec3d.ofCenter(emitter.getPos()), side, emitter.getPos(), false);
            context.assertTrue(item.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(), "Rotate toward " + side);
            context.assertTrue(emitter.getCachedState().get(LaserEmitterBlock.FACING) == side, "Clicked face sets orientation");
            context.assertTrue(world.getBlockEntity(emitter.getPos()) == emitter && emitter.energy().stored() == 1234
                    && emitter.crystal().color() == LaserColor.BLUE && emitter.canManageSecurity(player),
                    "Rotation preserves the existing block entity, energy, crystal and owner");
        }
        item.use(world, player, Hand.MAIN_HAND);
        context.assertTrue(LaserConfiguratorItem.mode(tool) == 1, "Air use selects copy mode");
        var hit = new BlockHitResult(Vec3d.ofCenter(emitter.getPos()), Direction.UP, emitter.getPos(), false);
        context.assertTrue(item.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(), "Copy succeeds through item interaction");
        NbtCompound data = GameVersion.itemData(tool);
        context.assertTrue(data.contains("Settings") && data.getLong("Preview") == emitter.getPos().asLong()
                && !data.getCompound("Settings").contains("Energy"), "Clipboard contains only settings and preview coordinates");
        for (int mode : new int[]{2, 3, 0}) {
            item.use(world, player, Hand.MAIN_HAND);
            context.assertTrue(LaserConfiguratorItem.mode(tool) == mode && GameVersion.itemData(tool).contains("Settings"),
                    "Changing mode keeps copied settings");
        }
        player.discard();
        context.removeBlock(relative);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void filterCategoriesOwnerAndUuidExclusionsSurviveSaveLoad(TestContext context) {
        var hostile = context.spawnMob(EntityType.ZOMBIE, new Vec3d(1, 2, 1));
        var passive = context.spawnMob(EntityType.VILLAGER, new Vec3d(2, 2, 1));
        var owner = context.createMockSurvivalPlayer();
        var guest = context.createMockSurvivalPlayer();
        guest.setUuid(UUID.randomUUID());
        LaserTargetFilter filter = new LaserTargetFilter();
        context.assertTrue(filter.allows(hostile, null) && filter.allows(passive, null) && filter.allows(owner, null), "Default filter preserves original targeting");
        filter.setFlags(LaserTargetFilter.HOSTILE);
        context.assertTrue(filter.allows(hostile, null) && !filter.allows(passive, null) && !filter.allows(owner, null), "Categories are independent");
        filter.setFlags(15);
        context.assertTrue(!filter.allows(owner, owner.getUuid()) && filter.allows(guest, owner.getUuid()), "Owner exclusion is UUID-based");
        context.assertTrue(filter.exclude(guest.getUuid(), "Guest"), "Named player can be excluded");
        context.assertFalse(filter.allows(guest, null), "Excluded UUID stays safe independently of profile name");
        var restored = new LaserTargetFilter();
        restored.read(filter.write());
        context.assertTrue(restored.flags() == 15 && !restored.allows(guest, null), "Filter state persists");
        context.assertTrue(restored.removeName("gUeSt") && restored.allows(guest, null), "Names can be removed case-insensitively and offline");
        for (int i = 0; i < LaserTargetFilter.MAX_EXCLUSIONS; i++) context.assertTrue(restored.exclude(UUID.randomUUID(), "Player" + i), "Accept entry " + i);
        context.assertFalse(restored.exclude(UUID.randomUUID(), "Overflow"), "Exclusion limit enforced");
        restored.read(new NbtCompound());
        context.assertTrue(restored.flags() == 7 && restored.exclusions().isEmpty(), "Old saves retain unfiltered defaults");
        hostile.discard();
        passive.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void filteringBlocksDamageAndIgnitionWithoutIncreasingDamage(TestContext context) {
        BlockPos pos = new BlockPos(1, 3, 3);
        context.setBlockState(pos, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        var emitter = (LaserEmitterBlockEntity) context.getBlockEntity(pos);
        emitter.getPropertyDelegate().set(4, 1);
        emitter.getPropertyDelegate().set(12, 1);
        emitter.targetFilter().setFlags(0);
        var target = context.spawnMob(EntityType.VILLAGER, new Vec3d(3, 2.5, 3.5));
        target.setAiDisabled(true);
        target.setNoGravity(true);
        tick(emitter);
        context.assertTrue(target.getHealth() == 20 && !target.isOnFire(), "Rejected target takes neither damage nor ignition");
        emitter.targetFilter().setFlags(LaserTargetFilter.PASSIVE);
        tick(emitter);
        context.assertTrue(target.getHealth() == 19.5F && target.isOnFire(), "Accepted target takes exactly the existing damage");
        target.discard();
        context.removeBlock(pos);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void copyPasteKeepsInventoryEnergyOwnershipAndModuleGates(TestContext context) {
        BlockPos sourcePos = new BlockPos(1, 3, 3), targetPos = new BlockPos(4, 3, 3);
        context.setBlockState(sourcePos, ModBlocks.POWERED_LASER_EMITTER);
        context.setBlockState(targetPos, ModBlocks.POWERED_LASER_EMITTER);
        var source = (LaserEmitterBlockEntity) context.getBlockEntity(sourcePos);
        var target = (LaserEmitterBlockEntity) context.getBlockEntity(targetPos);
        var owner = context.createMockSurvivalPlayer();
        var guest = context.createMockSurvivalPlayer();
        guest.setUuid(UUID.randomUUID());
        owner.setPosition(Vec3d.ofCenter(target.getPos()));
        guest.setPosition(owner.getPos());
        source.initializeOwner(guest);
        target.initializeOwner(owner);
        source.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
        target.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.BLUE)));
        source.setStack(LaserModule.ENTITY_DAMAGE.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.ENTITY_DAMAGE)));
        source.setStack(LaserModule.TARGET_FILTER.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER)));
        source.getPropertyDelegate().set(9, 137);
        source.getPropertyDelegate().set(4, 1);
        source.getPropertyDelegate().set(1, LaserRedstoneMode.HIGH.ordinal());
        source.targetFilter().setFlags(1);
        source.energy().restore(1000);
        target.energy().restore(5000);
        NbtCompound settings = source.copySettings();
        context.assertFalse(settings.contains("Energy") || settings.contains("Owner") || settings.contains("Items"), "Only explicit settings leave the source");
        settings.putInt("Energy", Integer.MAX_VALUE);
        settings.putUuid("Owner", guest.getUuid());
        context.assertTrue(target.pasteSettings(owner, settings), "Owner can paste settings");
        context.assertTrue(target.energy().stored() == 5000 && target.canManageSecurity(owner), "Unknown payload fields cannot replace energy or owner");
        context.assertTrue(target.crystal().color() == LaserColor.BLUE && !target.damagesEntities() && target.targetFilter().flags() == 7,
                "Crystal and missing module restrictions are preserved");
        context.assertTrue(target.getPropertyDelegate().get(1) == LaserRedstoneMode.HIGH.ordinal(), "Available settings are copied");
        target.setStack(LaserModule.ENTITY_DAMAGE.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.ENTITY_DAMAGE)));
        target.setStack(LaserModule.TARGET_FILTER.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER)));
        target.pasteSettings(owner, settings);
        context.assertTrue(target.getPropertyDelegate().get(9) == 137 && target.targetFilter().flags() == 1, "Installed modules unlock their copied settings");
        target.togglePrivacy(owner);
        context.assertFalse(target.pasteSettings(guest, settings), "Private access cannot be bypassed by the configurator");
        target.removeStack(LaserModule.TARGET_FILTER.slot());
        context.assertFalse(target.allowsSetting(14) || target.allowsSetting(18), "Removed filter rejects stale control packets");
        var preview = LaserBeamPath.preview(target, 1);
        context.assertTrue(!preview.segments().isEmpty() && !target.isBeamActive() && target.energy().stored() == 5000,
                "Inactive preview traces without activation or payment");
        context.removeBlock(sourcePos);
        context.removeBlock(targetPos);
        context.complete();
    }

    private static void tick(LaserEmitterBlockEntity emitter) {
        LaserEmitterBlockEntity.serverTick(emitter.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
    }
}
