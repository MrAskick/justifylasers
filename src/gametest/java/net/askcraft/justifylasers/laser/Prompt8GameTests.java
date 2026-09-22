package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.energy.AmplifierTier;
import net.askcraft.justifylasers.industry.ChamberStructure;
import net.askcraft.justifylasers.industry.CrystalGrowth;
import net.askcraft.justifylasers.industry.CrystalSeed;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.item.AssemblyBlueprintItem;
import net.askcraft.justifylasers.item.GrowthSeedItem;
import net.askcraft.justifylasers.item.LaserAmplifierItem;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModNutrients;
import net.askcraft.justifylasers.screen.IndustrialMachineScreenHandler;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Prompt8GameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void allSchematicsUseOneItemAndClearInEitherCraftingGrid(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld();
        for (var definition : ModIndustry.BLUEPRINTS.values()) {
            var card = definition.getDefaultStack();
            c.assertTrue(card.isOf(ModIndustry.ASSEMBLY_BLUEPRINT) && AssemblyBlueprintItem.recipe(card).equals(definition.recipe()), "One ID, distinct recipe data");
            c.assertFalse(AssemblyBlueprintItem.output(card, c.getWorld()).isEmpty(), "Every plan has an output icon");
            var legacy = new ItemStack(definition);
            var data = GameVersion.itemData(legacy); data.putString("ThirdPartyNote", "keep"); GameVersion.setItemData(legacy, data);
            var migrated = AssemblyBlueprintItem.migrate(legacy);
            c.assertTrue(migrated.isOf(ModIndustry.ASSEMBLY_BLUEPRINT) && AssemblyBlueprintItem.recipe(migrated).equals(definition.recipe())
                    && GameVersion.itemData(migrated).getString("ThirdPartyNote").equals("keep"), "Legacy plan migration preserves metadata");
            for (int width : new int[]{2, 3}) {
                var grid = new CraftingInventory(player.playerScreenHandler, width, width);
                grid.setStack(width * width - 1, card.copy());
                c.assertTrue(craft(c, grid).isOf(ModIndustry.BLANK_SCHEMATIC), "Plan erases in any crafting slot");
                grid.setStack(0, new ItemStack(Items.DIRT));
                c.assertTrue(craft(c, grid).isEmpty(), "Clearing cannot destroy unrelated ingredients");
                grid.setStack(0, card.copy());
                c.assertTrue(craft(c, grid).isEmpty(), "Only one configured card per clearing operation");
            }
        }
        var empty = new CraftingInventory(player.playerScreenHandler, 2, 2);
        empty.setStack(0, new ItemStack(ModIndustry.BLANK_SCHEMATIC));
        c.assertTrue(craft(c, empty).isEmpty(), "Blank schematic is not its own crafting loop");
        player.discard(); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyFreshGrowthSeedRequiresRawPhotonicMaterial(TestContext c) {
        var player = c.createMockCreativeServerPlayerInWorld();
        var grid = new CraftingInventory(player.playerScreenHandler, 2, 2);
        var grower = machine(c, MachineKind.CRYSTAL_GROWER);
        for (var crystal : CrystalGrowth.values()) {
            grid.clear(); grid.setStack(3, new ItemStack(crystal.natural()));
            c.assertTrue(GrowthSeedItem.craftingType(grid.size(), grid::getStack) == null, "A gem alone cannot replenish a seed");
            c.assertFalse(grower.isValid(0, grid.getStack(3)), "Natural and recycled gems are rejected equally");
            grid.setStack(0, new ItemStack(ModIndustry.RAW_PHOTONIC_CRYSTAL));
            var seed = craft(c, grid);
            c.assertTrue(seed.isOf(ModNutrients.GROWTH_SEED) && GrowthSeedItem.type(seed) == crystal && crystal.stage(seed) == 0, "Correct typed seed from shapeless recipe");
            c.assertTrue(grower.isValid(0, seed), "Typed seed can enter the grower");
            grid.setStack(1, new ItemStack(Items.DIRT));
            c.assertTrue(craft(c, grid).isEmpty(), "Extra ingredients do not disappear");
            grid.setStack(1, ItemStack.EMPTY); grid.setStack(0, crystal.seed(1));
            c.assertTrue(GrowthSeedItem.craftingType(grid.size(), grid::getStack) == null, "Worn seeds cannot replace raw material or reset themselves");
        }
        player.discard(); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cutterExtractionWorksOnEveryMemberAndFaceWithExactVanillaFilters(TestContext c) {
        var cutter = machine(c, MachineKind.LASER_CUTTER);
        for (var pos : ChamberStructure.positions(cutter.getPos())) for (var side : Direction.values()) {
            var port = ItemStorage.SIDED.find(c.getWorld(), pos, side);
            c.assertTrue(port != null, "Native Fabric item port exists on " + pos + "/" + side);
            cutter.setStack(0, new ItemStack(CrystalGrowth.DIAMOND.grown(), 2));
            // Existing alpha.37 output, not just a freshly generated alpha.38 gem.
            cutter.setStack(IndustrialMachineBlockEntity.OUTPUT, CrystalSeed.syntheticResult(new ItemStack(Items.DIAMOND, 3)));
            var diamond = ItemVariant.of(Items.DIAMOND);
            c.assertTrue(diamond.matches(cutter.getStack(IndustrialMachineBlockEntity.OUTPUT)), "Legacy origin tag no longer prevents an exact filter match");
            try (var tx = Transaction.openOuter()) {
                c.assertTrue(port.extract(diamond, 2, tx) == 2, "Output extraction succeeds");
                c.assertTrue(port.extract(ItemVariant.of(CrystalGrowth.DIAMOND.grown()), 1, tx) == 0, "Automation cannot steal recipe inputs");
            }
            c.assertTrue(cutter.getStack(IndustrialMachineBlockEntity.OUTPUT).getCount() == 3, "Simulated extraction rolls back");
            try (var tx = Transaction.openOuter()) {
                c.assertTrue(port.extract(diamond, 2, tx) == 2, "Committed output extraction succeeds"); tx.commit();
            }
            c.assertTrue(cutter.getStack(IndustrialMachineBlockEntity.OUTPUT).getCount() == 1, "Shared inventory is decremented once");
            cutter.clear();
        }
        var owner = c.createMockCreativeServerPlayerInWorld(); cutter.initializeOwner(owner);
        var port = ItemStorage.SIDED.find(c.getWorld(), cutter.getPos().add(1, 1, 1), Direction.SOUTH);
        cutter.setStack(IndustrialMachineBlockEntity.OUTPUT, new ItemStack(Items.DIAMOND));
        cutter.togglePrivacy(owner);
        try (var tx = Transaction.openOuter()) { c.assertTrue(port.extract(ItemVariant.of(Items.DIAMOND), 1, tx) == 0, "Privacy still protects existing ports"); }
        cutter.togglePrivacy(owner);
        c.getWorld().removeBlock(cutter.getPos().up(), false);
        try (var tx = Transaction.openOuter()) { c.assertTrue(port.extract(ItemVariant.of(Items.DIAMOND), 1, tx) == 0, "A stale port cannot extract from a dismantled chamber"); }
        owner.discard(); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cutterTagMigrationKeepsUnrelatedMetadata(TestContext c) {
        var product = CrystalSeed.syntheticResult(new ItemStack(Items.EMERALD, 7));
        var data = GameVersion.itemData(product); data.putString("OtherMod", "retained"); GameVersion.setItemData(product, data);
        CrystalSeed.cleanProduct(product);
        c.assertTrue(product.getCount() == 7 && !CrystalSeed.synthetic(product)
                && GameVersion.itemData(product).getString("OtherMod").equals("retained"), "Only retired origin fields are removed");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oldInProgressNaturalSeedMigratesAndMinimumFluxFollowsRecipe(TestContext c) {
        var grower = machine(c, MachineKind.CRYSTAL_GROWER);
        var player = c.createMockCreativeServerPlayerInWorld();
        for (var crystal : CrystalGrowth.values()) {
            grower.clear(); grower.setStack(0, crystal.seed(0));
            grower.fillFluid(crystal.nutrient(), 1000, false);
            var menu = new IndustrialMachineScreenHandler(1, player.getInventory(), grower);
            c.assertTrue(menu.minimumFlux() == crystal.minimum(), "Menu minimum belongs to the selected crystal");
            grower.clear(); grower.setStack(IndustrialMachineBlockEntity.ACTIVE_SEED, new ItemStack(crystal.natural()));
            var old = grower.createNbt(); old.remove("SeedSystemVersion"); grower.readNbt(old);
            c.assertTrue(grower.getStack(IndustrialMachineBlockEntity.ACTIVE_SEED).isOf(ModNutrients.GROWTH_SEED)
                    && crystal.stage(grower.getStack(IndustrialMachineBlockEntity.ACTIVE_SEED)) == 0, "Pre-update paid seed remains usable");
        }
        player.discard(); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void amplifierStacksAreTierSensitiveAndRetiredTiersMigrate(TestContext c) {
        for (int tier = 1; tier <= AmplifierTier.MAX; tier++) {
            c.assertTrue(LaserAmplifierItem.stack(tier).getMaxCount() == 64, "Amplifiers stack to 64");
            if (tier < AmplifierTier.MAX) c.assertFalse(GameVersion.canStack(LaserAmplifierItem.stack(tier), LaserAmplifierItem.stack(tier + 1)), "Different tiers cannot stack");
        }
        var old = LaserAmplifierItem.stack(6);
        var data = GameVersion.itemData(old); data.putInt("AmplifierTier", 15); GameVersion.setItemData(old, data);
        old.getItem().inventoryTick(old, c.getWorld(), c.createMockSurvivalPlayer(), 0, true);
        c.assertTrue(GameVersion.canStack(old, LaserAmplifierItem.stack(6)), "Retired amplifier becomes a normally stackable tier VI");
        c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void amplifierSlotAcceptsSingleItemsAndWholeStacksWithoutMixingTiers(TestContext c) {
        var pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
        c.getWorld().setBlockState(pos, ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
        var emitter = (LaserEmitterBlockEntity)c.getWorld().getBlockEntity(pos);
        var player = c.createMockSurvivalPlayer(); player.setPosition(Vec3d.ofCenter(pos));
        var menu = new LaserEmitterScreenHandler(1, player.getInventory(), emitter);
        int slot = LaserEmitterBlockEntity.AMPLIFIER_SLOT;
        var stack = LaserAmplifierItem.stack(2); stack.setCount(64); menu.setCursorStack(stack);
        menu.onSlotClick(slot, 1, SlotActionType.PICKUP, player);
        c.assertTrue(emitter.getStack(slot).getCount() == 1 && menu.getCursorStack().getCount() == 63, "Right-click inserts one amplifier");
        menu.onSlotClick(slot, 0, SlotActionType.PICKUP, player);
        c.assertTrue(emitter.getStack(slot).getCount() == 64 && menu.getCursorStack().isEmpty(), "Left-click inserts the rest of the stack");
        menu.onSlotClick(slot, 0, SlotActionType.PICKUP, player);
        c.assertTrue(emitter.getStack(slot).isEmpty() && menu.getCursorStack().getCount() == 64, "Whole stack can be picked up again");
        player.getInventory().setStack(9, menu.getCursorStack()); menu.setCursorStack(ItemStack.EMPTY);
        menu.quickMove(player, LaserEmitterBlockEntity.INVENTORY_SIZE);
        c.assertTrue(emitter.getStack(slot).getCount() == 64 && player.getInventory().getStack(9).isEmpty(), "Shift-click installs the whole stack");
        emitter.removeStack(slot, 32);
        var extra = LaserAmplifierItem.stack(2); extra.setCount(48); player.getInventory().setStack(9, extra);
        menu.quickMove(player, LaserEmitterBlockEntity.INVENTORY_SIZE);
        c.assertTrue(emitter.getStack(slot).getCount() == 64 && player.getInventory().getStack(9).getCount() == 16, "Shift-click leaves excess items with the player");
        player.getInventory().setStack(10, LaserAmplifierItem.stack(3));
        c.assertTrue(menu.quickMove(player, LaserEmitterBlockEntity.INVENTORY_SIZE + 1).isEmpty()
                && player.getInventory().getStack(10).getCount() == 1, "Another tier cannot merge or disappear");
        emitter.readNbt(emitter.createNbt());
        c.assertTrue(emitter.getStack(slot).getCount() == 64 && LaserAmplifierItem.tier(emitter.getStack(slot)) == 2, "Count and tier survive saving");
        c.assertFalse(menu.quickMove(player, slot).isEmpty(), "Shift-click recovers the installed stack");
        c.assertTrue(emitter.getStack(slot).isEmpty(), "Removing the stack clears the amplifier slot");
        int recovered = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            var owned = player.getInventory().getStack(i);
            if (LaserAmplifierItem.tier(owned) == 2) recovered += owned.getCount();
        }
        c.assertTrue(recovered == 80, "Returned stack and transfer remainder are both preserved");
        c.getWorld().removeBlock(pos, false); player.discard(); c.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void photoniteGrowthRequiresPurpleLightInTheLoadedRecipe(TestContext c) {
        var grower = machine(c, MachineKind.CRYSTAL_GROWER);
        var crystal = CrystalGrowth.PHOTONITE;
        grower.setStack(0, crystal.seed(0)); grower.fillFluid(crystal.nutrient(), 1000, false);
        grower.receiveLight(crystal.reference(), 0xFFD45A, crystal.reference(), 0xFFD45A);
        IndustrialMachineBlockEntity.tick(c.getWorld(), grower.getPos(), grower.getCachedState(), grower);
        c.assertTrue(grower.status() == IndustrialMachineBlockEntity.Status.WRONG_SPECTRUM
                && grower.progress() == 0 && grower.water() == 1000, "Old golden spectrum cannot pay for photonite growth");
        grower.readNbt(grower.createNbt());
        grower.receiveLight(crystal.reference(), 0xBC09F5, crystal.reference(), 0xBC09F5);
        IndustrialMachineBlockEntity.tick(c.getWorld(), grower.getPos(), grower.getCachedState(), grower);
        c.assertTrue(grower.status() == IndustrialMachineBlockEntity.Status.WORKING && grower.progress() == 1, "Purple light grows photonite at the reference rate");
        c.complete();
    }

    private static ItemStack craft(TestContext c, CraftingInventory grid) {
        return c.getWorld().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, grid, c.getWorld())
                .map(recipe -> recipe.craft(grid, c.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }
    private static IndustrialMachineBlockEntity machine(TestContext c, MachineKind kind) {
        var origin = c.getAbsolutePos(new BlockPos(2, 2, 2));
        for (var pos : ChamberStructure.positions(origin)) c.getWorld().setBlockState(pos, ModIndustry.MACHINES.get(kind).getDefaultState());
        var machine = (IndustrialMachineBlockEntity)c.getWorld().getBlockEntity(origin);
        c.assertTrue(ChamberStructure.form(machine), "Chamber forms"); return machine;
    }
}
