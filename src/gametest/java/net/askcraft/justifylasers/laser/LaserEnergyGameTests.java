package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import team.reborn.energy.api.EnergyStorage;

public class LaserEnergyGameTests implements FabricGameTest {
    private static final BlockPos SOURCE = new BlockPos(1, 4, 3);
    private static final BlockPos TARGET = new BlockPos(4, 4, 3);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void crystalPowerAndTechnicalModeAreAllRequired(TestContext context) {
        boolean initialMode = LaserConfig.technicalMode();
        try {
            for (int condition = 0; condition < 3; condition++) {
                LaserConfig.applyServerMode(condition != 0);
                LaserEmitterBlockEntity emitter = create(context);
                if (condition != 1) installCrystal(emitter, LaserColor.RED);
                if (condition != 2) emitter.energy().restore(10000);
                emitter.getPropertyDelegate().set(3, 1);
                emitter.getPropertyDelegate().set(14, 100);
                tick(emitter);
                context.assertFalse(emitter.isBeamActive(), "Unpowered/incomplete emitter cannot emit");
                context.assertTrue(context.getWorld().getBlockState(context.getAbsolutePos(TARGET)).isOf(Blocks.STONE), "No mining without requirements");
                context.removeBlock(SOURCE);
            }
        } finally {
            LaserConfig.applyServerMode(initialMode);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void onePaidTickWorksAndNeverPaysTwiceInTheSameTick(TestContext context) {
        boolean initialMode = LaserConfig.technicalMode();
        LaserConfig.applyServerMode(true);
        LaserEmitterBlockEntity emitter = create(context);
        installCrystal(emitter, LaserColor.CYAN);
        emitter.getPropertyDelegate().set(1, LaserRedstoneMode.IGNORE.ordinal());
        emitter.energy().restore(emitter.energyCost());
        tick(emitter);
        context.assertTrue(emitter.isBeamActive(), "The final paid tick must still render");
        context.assertTrue(emitter.energy().stored() == 0, "Charge one configured tick");
        context.assertTrue(emitter.getColor() == LaserColor.CYAN, "Crystal selects beam color");
        emitter.energy().restore(1000);
        tick(emitter);
        context.assertTrue(emitter.energy().stored() == 1000, "Never charge twice during one world tick");
        emitter.energy().restore(0);
        context.runAtTick(3, () -> {
            try {
                tick(emitter);
                context.assertFalse(emitter.isBeamActive(), "Beam must stop when no further tick can be paid");
                context.removeBlock(SOURCE);
                context.complete();
            } finally {
                LaserConfig.applyServerMode(initialMode);
            }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void modulesGateSettingsAndRuntimeEffects(TestContext context) {
        boolean initialMode = LaserConfig.technicalMode();
        try {
            LaserConfig.applyServerMode(true);
            LaserEmitterBlockEntity emitter = create(context);
            var player = context.createMockSurvivalPlayer();
            player.setPosition(Vec3d.ofCenter(emitter.getPos()));
            var menu = new LaserEmitterScreenHandler(42, player.getInventory(), emitter);
            player.currentScreenHandler = menu;
            for (int id : new int[]{2, 3, 4, 7, 8, 9, 10, 11, 12, 2005, 3001, 4020, 6000, 5008, 1100}) {
                context.assertFalse(new LaserSettingsPacket(42, id).apply(player), "Reject unavailable control " + id);
            }
            emitter.getPropertyDelegate().set(12, 1);
            emitter.getPropertyDelegate().set(15, 1);
            context.assertFalse(emitter.ignitesEntities() || emitter.hasSilkTouch() || emitter.dropsBlocks() || emitter.showsScorchMarks(), "Saved toggles cannot bypass module requirements");
            for (LaserModule module : LaserModule.values()) emitter.setStack(module.slot(), new ItemStack(ModLaserParts.MODULES.get(module)));
            emitter.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE));
            context.assertTrue(emitter.ignitesEntities() && emitter.hasSilkTouch() && emitter.dropsBlocks() && emitter.showsScorchMarks(), "Installed modules unlock configured effects");
            installCrystal(emitter, LaserColor.RED);
            emitter.energy().restore(100000);
            emitter.getPropertyDelegate().set(3, 1);
            emitter.getPropertyDelegate().set(14, 100);
            tick(emitter);
            context.assertTrue(context.getWorld().isAir(context.getAbsolutePos(TARGET)), "Powered mining works");
            context.assertTrue(context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(context.getAbsolutePos(TARGET)).expand(1),
                    item -> item.getStack().isOf(Items.STONE)).size() == 1, "Collection and Silk Touch modules affect actual loot");
            emitter.removeStack(LaserModule.SCORCH_MARKS.slot());
            context.assertFalse(emitter.showsScorchMarks(), "Removing module disables its effect immediately");
            context.removeBlock(SOURCE);
        } finally {
            LaserConfig.applyServerMode(initialMode);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void storedEnergyCrystalsAndModulesSurviveNbt(TestContext context) {
        LaserEmitterBlockEntity emitter = create(context);
        emitter.energy().restore(1_800_001);
        installCrystal(emitter, LaserColor.VIOLET);
        for (LaserModule module : LaserModule.values()) emitter.setStack(module.slot(), new ItemStack(ModLaserParts.MODULES.get(module)));
        NbtCompound saved = emitter.createNbt();
        LaserEmitterBlockEntity restored = new LaserEmitterBlockEntity(SOURCE, ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
        restored.readNbt(saved);
        context.assertTrue(restored.energy().stored() == 1_800_001 && restored.crystal().color() == LaserColor.VIOLET, "Energy and crystal persist");
        for (LaserModule module : LaserModule.values()) context.assertTrue(restored.hasModule(module), "Module persists " + module);
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fabricEnergyTransactionsRollBackWithoutDuplication(TestContext context) {
        boolean initialMode = LaserConfig.technicalMode();
        try {
            LaserConfig.applyServerMode(true);
            LaserEmitterBlockEntity emitter = create(context);
            EnergyStorage port = EnergyStorage.SIDED.find(context.getWorld(), emitter.getPos(), Direction.UP);
            context.assertTrue(port != null, "A real sided energy port is registered");
            try (Transaction transaction = Transaction.openOuter()) {
                port.insert(1000, transaction);
            }
            context.assertTrue(emitter.energy().stored() == 0, "Aborted transfer rolls back");
            try (Transaction outer = Transaction.openOuter()) {
                port.insert(100, outer);
                try (Transaction inner = outer.openNested()) {
                    port.insert(200, inner);
                    inner.commit();
                }
            }
            context.assertTrue(emitter.energy().stored() == 0, "Committed nested transfer still rolls back with parent");
            try (Transaction transaction = Transaction.openOuter()) {
                context.assertTrue(port.insert(321, transaction) == 321 && port.extract(321, transaction) == 0, "Port only receives");
                transaction.commit();
            }
            context.assertTrue(emitter.energy().stored() == 321, "Committed transfer persists exactly once");
            context.removeBlock(SOURCE);
        } finally {
            LaserConfig.applyServerMode(initialMode);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shiftClickMovesPartsWithoutLossOrDuplication(TestContext context) {
        LaserEmitterBlockEntity emitter = create(context);
        var player = context.createMockSurvivalPlayer();
        player.setPosition(Vec3d.ofCenter(emitter.getPos()));
        var menu = new LaserEmitterScreenHandler(42, player.getInventory(), emitter);
        player.getInventory().setStack(9, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
        player.getInventory().setStack(10, new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.BLUE)));
        context.assertFalse(menu.quickMove(player, LaserEmitterBlockEntity.MODULE_SLOT_COUNT).isEmpty(), "Shift-click inserts crystal");
        context.assertTrue(emitter.crystal().color() == LaserColor.RED && player.getInventory().getStack(9).isEmpty(), "One crystal moved");
        context.assertTrue(menu.quickMove(player, LaserEmitterBlockEntity.MODULE_SLOT_COUNT + 1).isEmpty() && !player.getInventory().getStack(10).isEmpty(), "Occupied slot cannot eat another crystal");
        context.assertFalse(menu.quickMove(player, 0).isEmpty(), "Crystal can be recovered");
        context.assertTrue(emitter.crystal() == null, "Module slot is now empty");
        int crystals = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).getItem() instanceof net.askcraft.justifylasers.item.LaserCrystalItem) crystals += player.getInventory().getStack(slot).getCount();
        }
        context.assertTrue(crystals == 2, "Both crystals survive without duplication");
        context.removeBlock(SOURCE);
        context.complete();
    }

    private static LaserEmitterBlockEntity create(TestContext context) {
        context.setBlockState(TARGET, Blocks.STONE);
        context.setBlockState(SOURCE, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) context.getBlockEntity(SOURCE);
        emitter.getPropertyDelegate().set(13, 8);
        return emitter;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void onlyRangeAndThicknessModulesIncreaseOpticalOutput(TestContext context) {
        boolean mode = LaserConfig.technicalMode();
        try {
            LaserConfig.applyServerMode(true);
            for (int upgrades : new int[]{0, 1, 64}) {
                int opticalRate = LaserConfig.get().basePerTick + upgrades * 9;
                long flux = LuminousFlux.fromEnergyRate(opticalRate, LaserConfig.get().lumensPerEnergyUnit);
                int previousCost = 0;
                for (boolean functions : new boolean[]{false, true}) {
                    var emitter = create(context);
                    installCrystal(emitter, LaserColor.RED);
                    if (upgrades > 0) {
                        emitter.setStack(LaserModule.RANGE.slot(), new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, upgrades));
                        emitter.setStack(LaserModule.THICKNESS.slot(), new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS), upgrades));
                    }
                    if (functions) {
                        for (var module : LaserModule.values()) if (module != LaserModule.RANGE && module != LaserModule.THICKNESS)
                            emitter.setStack(module.slot(), new ItemStack(ModLaserParts.MODULES.get(module)));
                        for (int property : new int[]{3, 4, 12, 15, 16, 17}) emitter.getPropertyDelegate().set(property, 1);
                        emitter.getPropertyDelegate().set(9, 200);
                        emitter.getPropertyDelegate().set(10, 100);
                        emitter.getPropertyDelegate().set(11, 20);
                        emitter.getPropertyDelegate().set(14, 100);
                    }
                    emitter.energy().restore(100_000);
                    tick(emitter);
                    context.assertTrue(emitter.isBeamActive() && emitter.opticalBudget() == flux, "Only optical modules set the output");
                    context.assertTrue(emitter.transferBudget() == opticalRate, "FE-equivalent output excludes paid effects");
                    context.assertTrue(100_000 - emitter.energy().stored() == emitter.energyCost(), "All work is still paid for");
                    if (functions) context.assertTrue(emitter.energyCost() > previousCost, "Mining, damage and ignition still increase consumption");
                    previousCost = emitter.energyCost();
                    var clientMenu = new LaserEmitterScreenHandler(1, context.createMockSurvivalPlayer().getInventory(), emitter.getPos(), true);
                    for (int index = 48; index < 52; index++) clientMenu.setProperty(index, (short) emitter.getPropertyDelegate().get(index));
                    context.assertTrue(clientMenu.luminousFlux() == flux, "Flux survives vanilla's signed 16-bit menu packets");
                    context.removeBlock(SOURCE);
                }
            }
        } finally { context.removeBlock(SOURCE); LaserConfig.applyServerMode(mode); }
        context.complete();
    }

    private static void installCrystal(LaserEmitterBlockEntity emitter, LaserColor color) {
        emitter.setStack(0, new ItemStack(ModLaserParts.CRYSTALS.get(color)));
    }

    private static void tick(LaserEmitterBlockEntity emitter) {
        LaserEmitterBlockEntity.serverTick(emitter.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
    }
}
