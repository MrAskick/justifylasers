package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public class PoweredEmitterGameTests implements FabricGameTest {
    private static final BlockPos SOURCE = new BlockPos(1, 3, 3);
    private static final BlockPos TARGET = new BlockPos(4, 3, 3);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rangeAndThicknessComeOnlyFromStackedModules(TestContext context) {
        var emitter = create(context);
        var settings = emitter.getPropertyDelegate();
        settings.set(13, 512);
        settings.set(6, 200);
        context.assertTrue(emitter.getBeamRange() == 1 && emitter.getBeamWidthStep() == 0, "Saved/manual sliders cannot bypass modules");
        for (int count = 1; count <= 64; count++) {
            emitter.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.RANGE), count));
            context.assertTrue(emitter.getBeamRange() == 1 + count, "Tier I adds one block per module");
            emitter.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, count));
            context.assertTrue(emitter.getBeamRange() == Math.min(512, 1 + 8 * count), "Tier II adds eight blocks per module");
            emitter.setStack(LaserModule.THICKNESS.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS), count));
            context.assertTrue(emitter.getBeamWidthStep() == Math.round(200 * count / 64.0F), "Stacked thickness has deterministic steps");
        }
        context.assertTrue(emitter.getBeamWidthScale() == 10 && emitter.getBeamRange() == 512, "Full stacks reach both maxima");
        emitter.removeStack(LaserModule.RANGE.slot());
        emitter.removeStack(LaserModule.THICKNESS.slot());
        context.assertTrue(emitter.getBeamRange() == 1 && emitter.getBeamWidthScale() == 0.1F, "Removing upgrades restores minima");
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void geometryModuleEnergyTracksEveryItemAndSurvivesSaving(TestContext context) {
        var emitter = create(context);
        int base = LaserConfig.get().basePerTick;
        for (int count = 0; count <= 64; count++) {
            emitter.setStack(7, new ItemStack(ModLaserParts.MODULES.get(LaserModule.RANGE), count));
            emitter.setStack(8, new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS), count));
            context.assertTrue(emitter.energyCost() == base + 2 * count, "Range I and thickness each add one energy unit per item");
            emitter.setStack(7, new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, count));
            context.assertTrue(emitter.energyCost() == base + 9 * count, "Range II adds eight energy units per item, independently of the range cap");
        }
        context.assertTrue(emitter.getBeamRange() == 512 && emitter.energyCost() == base + 576, "Full stacks charge all 512 + 64 upgrade units");
        var player = context.createMockSurvivalPlayer();
        var menu = new LaserEmitterScreenHandler(7, player.getInventory(), emitter);
        context.assertTrue(menu.getEnergyCost() == base + 576, "Menu reports the same cost as the server");
        var restored = new LaserEmitterBlockEntity(SOURCE, ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
        restored.readNbt(emitter.createNbt());
        context.assertTrue(restored.energyCost() == base + 576, "Saved module stacks retain their energy cost");
        emitter.getStack(7).decrement(1);
        emitter.getStack(8).decrement(1);
        emitter.markDirty();
        context.assertTrue(emitter.energyCost() == base + 567 && menu.getEnergyCost() == base + 567, "In-place stack changes immediately update consumption");
        emitter.removeStack(7);
        emitter.removeStack(8);
        context.assertTrue(emitter.energyCost() == base, "Removing both upgrades removes their cost");
        var creative = new LaserEmitterBlockEntity(SOURCE, ModBlocks.LASER_EMITTER.getDefaultState());
        creative.getPropertyDelegate().set(13, 512);
        creative.getPropertyDelegate().set(6, 200);
        context.assertTrue(creative.energyCost() == 0, "Creative emitter still consumes no energy");
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void upgradeEnergyIsPaidOnceAndOnlyWhenAllowedToOperate(TestContext context) {
        boolean initialMode = LaserConfig.technicalMode();
        try {
            LaserConfig.applyServerMode(true);
            for (int condition = 0; condition < 5; condition++) {
                var emitter = create(context);
                if (condition != 4) emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
                emitter.setStack(7, new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 64));
                emitter.setStack(8, new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS), 64));
                int stored = emitter.energyCost() - (condition == 1 ? 1 : 0);
                emitter.energy().restore(stored);
                if (condition == 2) emitter.getPropertyDelegate().set(0, 0);
                if (condition == 3) emitter.getPropertyDelegate().set(1, LaserRedstoneMode.HIGH.ordinal());
                tick(emitter);
                context.assertTrue(emitter.isBeamActive() == (condition == 0), "Upgraded beam respects activation requirements: " + condition);
                context.assertTrue(emitter.energy().stored() == (condition == 0 ? 0 : stored), "Charge the complete module cost or nothing: " + condition);
                tick(emitter);
                context.assertTrue(emitter.energy().stored() == (condition == 0 ? 0 : stored), "No duplicate payment in the same tick");
                context.removeBlock(SOURCE);
            }
        } finally { LaserConfig.applyServerMode(initialMode); }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void moduleStacksMergeWithoutMixingTiersOrLosingItems(TestContext context) {
        var emitter = create(context);
        var player = context.createMockSurvivalPlayer();
        player.setPosition(Vec3d.ofCenter(emitter.getPos()));
        var menu = new LaserEmitterScreenHandler(7, player.getInventory(), emitter);
        context.assertTrue(menu.getType() == ModScreenHandlers.POWERED_LASER_EMITTER, "Powered emitter has a separate GUI type");
        emitter.setStack(7, new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 48));
        player.getInventory().setStack(9, new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 32));
        menu.quickMove(player, 9);
        context.assertTrue(emitter.getStack(7).getCount() == 64 && player.getInventory().getStack(9).getCount() == 16, "Shift-click respects stack64 and leaves remainder");
        context.assertTrue(emitter.getBeamRange() == 512, "Merging updates range");
        emitter.removeStack(7, 32);
        player.getInventory().setStack(10, new ItemStack(ModLaserParts.MODULES.get(LaserModule.RANGE), 32));
        context.assertTrue(menu.quickMove(player, 10).isEmpty() && emitter.getStack(7).getCount() == 32, "Two tiers cannot merge");
        context.assertFalse(menu.quickMove(player, 7).isEmpty(), "Installed stack can be recovered");
        context.assertTrue(emitter.getBeamRange() == 1 && emitter.getStack(7).isEmpty(), "Recovered stack removes its upgrade");
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inPlaceUpgradeMergesInvalidateTheCachedPath(TestContext context) {
        boolean mode = LaserConfig.technicalMode();
        try {
            LaserConfig.applyServerMode(true);
            var emitter = create(context);
            emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
            emitter.setStack(7, new ItemStack(ModLaserParts.MODULES.get(LaserModule.RANGE)));
            emitter.setStack(8, new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS), 32));
            emitter.energy().restore(10000);
            tick(emitter);
            var before = LaserBeamNetwork.path(emitter, 1.0F);
            context.assertFalse(before.last().hasBlockHit(), "Minimum upgrade cannot reach the target");
            emitter.getStack(7).increment(2);
            emitter.getStack(8).increment(1);
            emitter.markDirty();
            var after = LaserBeamNetwork.path(emitter, 1.0F);
            context.assertTrue(after.last().hasBlockHit() && after.last().hitBlock().equals(context.getAbsolutePos(TARGET)), "In-place merge updates the route in the same tick");
            context.assertTrue(emitter.getBeamWidthStep() == 103, "In-place merge updates thickness");
            context.assertFalse(emitter.isBeamActive(), "A higher-cost stack waits until its new cost is paid");
            context.removeBlock(SOURCE);
        } finally { LaserConfig.applyServerMode(mode); }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void missingCapabilityModulesPreventRealDamageAndMining(TestContext context) {
        boolean mode = LaserConfig.technicalMode();
        try {
            LaserConfig.applyServerMode(true);
            var emitter = create(context);
            emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
            emitter.setStack(7, new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE));
            emitter.energy().restore(100000);
            emitter.getPropertyDelegate().set(3, 1);
            emitter.getPropertyDelegate().set(4, 1);
            emitter.getPropertyDelegate().set(14, 100);
            var mob = context.spawnMob(EntityType.VILLAGER, new Vec3d(3, 2.5, 3.5));
            mob.setAiDisabled(true);
            mob.setNoGravity(true);
            tick(emitter);
            context.assertTrue(mob.getHealth() == 20 && context.getWorld().getBlockState(context.getAbsolutePos(TARGET)).isOf(Blocks.STONE), "Raw toggles cannot bypass missing modules");
            context.assertTrue(emitter.energyCost() == LaserConfig.get().basePerTick + 8, "Charge the installed range module, not locked capabilities");
            emitter.setStack(5, new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_DESTRUCTION)));
            emitter.setStack(6, new ItemStack(ModLaserParts.MODULES.get(LaserModule.ENTITY_DAMAGE)));
            int cost = emitter.energyCost();
            context.assertTrue(cost > LaserConfig.get().basePerTick, "Enabled capabilities increase consumption");
            emitter.removeStack(5);
            emitter.removeStack(6);
            context.assertFalse(emitter.breaksBlocks() || emitter.damagesEntities(), "Removing capability modules gates effects immediately");
            mob.discard();
            context.removeBlock(SOURCE);
        } finally { LaserConfig.applyServerMode(mode); }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void redstoneModesGateTheBeamBeforeCharging(TestContext context) {
        boolean initialMode = LaserConfig.technicalMode();
        try {
            LaserConfig.applyServerMode(true);
            for (LaserRedstoneMode mode : LaserRedstoneMode.values()) {
                for (boolean signal : new boolean[]{false, true}) {
                    var emitter = create(context);
                    context.setBlockState(SOURCE.down(), signal ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
                    emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.CYAN)));
                    emitter.energy().restore(10000);
                    emitter.getPropertyDelegate().set(1, mode.ordinal());
                    tick(emitter);
                    boolean allowed = mode.allows(signal);
                    context.assertTrue(emitter.isBeamActive() == allowed, "Redstone activation " + mode + "/" + signal);
                    context.assertTrue(emitter.energy().stored() == 10000 - (allowed ? emitter.energyCost() : 0), "No charge when blocked by redstone");
                    context.removeBlock(SOURCE);
                }
            }
        } finally { LaserConfig.applyServerMode(initialMode); }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void privacyIsEnforcedForAlreadyOpenMenusAndAutomation(TestContext context) {
        var emitter = create(context);
        var owner = context.createMockSurvivalPlayer();
        var guest = context.createMockSurvivalPlayer();
        guest.setUuid(UUID.randomUUID());
        owner.setPosition(Vec3d.ofCenter(emitter.getPos()));
        guest.setPosition(owner.getPos());
        emitter.initializeOwner(owner);
        emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
        var ownerMenu = new LaserEmitterScreenHandler(8, owner.getInventory(), emitter);
        var guestMenu = new LaserEmitterScreenHandler(9, guest.getInventory(), emitter);
        owner.currentScreenHandler = ownerMenu;
        guest.currentScreenHandler = guestMenu;
        context.assertTrue(guestMenu.canUse(guest), "Public is the default");
        context.assertFalse(new LaserSettingsPacket(9, 13).apply(guest), "Guest cannot claim security control");
        context.assertTrue(new LaserSettingsPacket(8, 13).apply(owner), "Owner can switch to private");
        context.assertFalse(guestMenu.canUse(guest) || new LaserSettingsPacket(9, 0).apply(guest), "Closing access revokes old menu immediately");
        guestMenu.onSlotClick(0, 0, SlotActionType.PICKUP, guest);
        context.assertTrue(!emitter.getStack(0).isEmpty() && guestMenu.getCursorStack().isEmpty(), "Forged slot clicks cannot steal parts");
        context.assertTrue(guestMenu.quickMove(guest, 0).isEmpty(), "Forged shift-click cannot steal parts");
        context.assertTrue(emitter.getAvailableSlots(Direction.DOWN).length == 0 && !emitter.canExtract(0, emitter.getStack(0), Direction.DOWN), "Hoppers cannot bypass private access");
        context.assertTrue(emitter.createMenu(10, guest.getInventory(), guest) == null, "Direct factory access is checked");
        context.assertTrue(new LaserSettingsPacket(8, 13).apply(owner) && guestMenu.canUse(guest), "Owner can restore public access");
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ownershipAndStackedUpgradesSurviveSaving(TestContext context) {
        var emitter = create(context);
        var owner = context.createMockSurvivalPlayer();
        emitter.initializeOwner(owner);
        emitter.togglePrivacy(owner);
        emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.BLUE)));
        emitter.setStack(7, new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 64));
        emitter.setStack(8, new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS), 64));
        emitter.getPropertyDelegate().set(1, LaserRedstoneMode.LOW.ordinal());
        NbtCompound nbt = emitter.createNbt();
        var restored = new LaserEmitterBlockEntity(SOURCE, ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
        restored.readNbt(nbt);
        context.assertTrue(restored.isPrivate() && restored.canManageSecurity(owner) && restored.ownerName().equals(emitter.ownerName()), "Owner and access persist");
        context.assertTrue(restored.getBeamRange() == 512 && restored.getBeamWidthScale() == 10, "Full upgrade stacks persist");
        context.assertTrue(restored.getPropertyDelegate().get(1) == LaserRedstoneMode.LOW.ordinal(), "Redstone mode persists");
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacyFiveSlotInventoryKeepsItsItems(TestContext context) {
        var emitter = create(context);
        emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.VIOLET)));
        for (LaserModule module : new LaserModule[]{LaserModule.SILK_TOUCH, LaserModule.BLOCK_DROPS, LaserModule.SCORCH_MARKS, LaserModule.IGNITION}) {
            emitter.setStack(module.slot(), new ItemStack(ModLaserParts.MODULES.get(module)));
        }
        NbtCompound oldSave = emitter.createNbt();
        oldSave.remove("Owner");
        oldSave.remove("OwnerName");
        oldSave.remove("PrivateAccess");
        var restored = new LaserEmitterBlockEntity(SOURCE, ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
        restored.readNbt(oldSave);
        for (int slot = 0; slot < 5; slot++) context.assertTrue(restored.getStack(slot).getItem() == emitter.getStack(slot).getItem(), "Old slot " + slot + " stays intact");
        context.assertFalse(restored.isPrivate(), "Legacy ownerless emitter remains accessible");
        context.assertTrue(restored.getBeamRange() == 1 && restored.getBeamWidthScale() == 0.1F, "New empty upgrade slots use minimum settings");
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void removingModulesDisablesForgedSettingsAndCostsScale(TestContext context) {
        var emitter = create(context);
        emitter.setStack(5, new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_DESTRUCTION)));
        emitter.setStack(6, new ItemStack(ModLaserParts.MODULES.get(LaserModule.ENTITY_DAMAGE)));
        emitter.getPropertyDelegate().set(3, 1);
        emitter.getPropertyDelegate().set(4, 1);
        emitter.getPropertyDelegate().set(9, 1);
        emitter.getPropertyDelegate().set(10, 0);
        emitter.getPropertyDelegate().set(11, 1);
        int previous = emitter.energyCost();
        for (int[] setting : new int[][]{{14, 100}, {9, 200}, {11, 20}, {10, 100}}) {
            emitter.getPropertyDelegate().set(setting[0], setting[1]);
            int cost = emitter.energyCost();
            context.assertTrue(cost > previous, "Higher settings must increase energy cost");
            previous = cost;
        }
        emitter.removeStack(5);
        emitter.removeStack(6);
        for (int id : new int[]{3, 4, 8, 12, 2005, 3000, 4020, 6100, 5001, 1200}) {
            context.assertFalse(emitter.allowsSetting(id), "Removed modules cannot be bypassed via control " + id);
        }
        context.removeBlock(SOURCE);
        context.complete();
    }

    private static LaserEmitterBlockEntity create(TestContext context) {
        context.setBlockState(SOURCE, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        context.setBlockState(TARGET, Blocks.STONE);
        return (LaserEmitterBlockEntity) context.getBlockEntity(SOURCE);
    }

    private static void tick(LaserEmitterBlockEntity emitter) {
        LaserEmitterBlockEntity.serverTick(emitter.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
    }
}
