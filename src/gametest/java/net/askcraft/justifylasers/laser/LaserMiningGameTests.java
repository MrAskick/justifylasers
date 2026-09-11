package net.askcraft.justifylasers.laser;

import io.netty.buffer.Unpooled;
import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.util.List;

public class LaserMiningGameTests implements FabricGameTest {
    private static final BlockPos SOURCE = new BlockPos(1, 4, 3);
    private static final BlockPos TARGET = new BlockPos(4, 4, 3);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void defaultsAndLegacyNbtKeepExistingMiningAndScorchBehavior(TestContext context) {
        LaserEmitterBlockEntity emitter = new LaserEmitterBlockEntity(SOURCE, ModBlocks.LASER_EMITTER.getDefaultState());
        for (int index = 14; index < 18; index++) emitter.getPropertyDelegate().set(index, 1);
        emitter.getPropertyDelegate().set(16, 0);
        emitter.getPropertyDelegate().set(17, 0);
        emitter.readNbt(new NbtCompound());
        context.assertTrue(emitter.getPropertyDelegate().get(14) == 0, "Legacy mining uses the original speed");
        context.assertTrue(emitter.getPropertyDelegate().get(15) == 0, "Silk Touch defaults off");
        context.assertTrue(emitter.getPropertyDelegate().get(16) == 1, "Legacy block drops remain on");
        context.assertTrue(emitter.showsScorchMarks(), "Legacy scorch marks remain on");
        for (int invalid : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            NbtCompound nbt = emitter.createNbt();
            nbt.putInt("MiningSpeedStep", invalid);
            emitter.readNbt(nbt);
            context.assertTrue(emitter.getPropertyDelegate().get(14) == (invalid < 0 ? 0 : 100), "Clamp saved speed");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void settingsSaveSynchronizeAndResetWithoutChangingOtherControls(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context, Blocks.STONE, 0);
        var player = context.createMockSurvivalPlayer();
        player.setPosition(Vec3d.ofCenter(emitter.getPos()));
        LaserEmitterScreenHandler serverMenu = new LaserEmitterScreenHandler(42, player.getInventory(), emitter);
        player.currentScreenHandler = serverMenu;
        PacketByteBuf data = new PacketByteBuf(Unpooled.buffer());
        LaserEmitterScreenHandler clientMenu;
        try {
            data.writeBlockPos(emitter.getPos());
            clientMenu = new LaserEmitterScreenHandler(42, player.getInventory(), data.readBlockPos());
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
        for (int id : new int[]{6000, 6057, 6100, 9, 10, 11}) {
            PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
            try {
                new LaserSettingsPacket(42, id).write(buffer);
                context.assertTrue(new LaserSettingsPacket(buffer).apply(player), "Valid mining control " + id);
            } finally {
                buffer.release();
            }
        }
        context.assertTrue(clientMenu.getMiningSpeedStep() == 100 && clientMenu.hasSilkTouch()
                && !clientMenu.dropsBlocks() && !clientMenu.showsScorchMarks(), "All mining properties reach the client menu");
        LaserEmitterBlockEntity restored = new LaserEmitterBlockEntity(SOURCE, ModBlocks.LASER_EMITTER.getDefaultState());
        restored.readNbt(emitter.createNbt());
        for (int index = 14; index < 18; index++) {
            context.assertTrue(restored.getPropertyDelegate().get(index) == emitter.getPropertyDelegate().get(index), "Saved setting " + index);
        }
        NbtCompound before = emitter.createNbt();
        for (int id : new int[]{13, 5999, 6101, Integer.MAX_VALUE}) {
            context.assertFalse(new LaserSettingsPacket(42, id).apply(player), "Reject invalid mining packet");
        }
        context.assertFalse(new LaserSettingsPacket(41, 6050).apply(player), "Reject stale menu ID");
        context.assertTrue(before.equals(emitter.createNbt()), "Invalid packets cannot change mining settings");
        emitter.getPropertyDelegate().set(9, 37);
        context.assertTrue(new LaserSettingsPacket(42, 12).apply(player), "Reset mining settings");
        context.assertTrue(clientMenu.getMiningSpeedStep() == 0 && !clientMenu.hasSilkTouch()
                && clientMenu.dropsBlocks() && clientMenu.showsScorchMarks(), "Reset restores four mining defaults");
        context.assertTrue(emitter.getPropertyDelegate().get(9) == 37 && clientMenu.breaksBlocks(), "Reset preserves damage and mining toggle");
        context.assertTrue(restored.getPropertyDelegate().get(14) == 100, "Settings stay per emitter");
        context.removeBlock(SOURCE);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void minimumAndIntermediateSpeedsMatchActualMiningTime(TestContext context) {
        for (Block block : new Block[]{Blocks.STONE, Blocks.DIRT, Blocks.OBSIDIAN}) {
            for (int speed : new int[]{0, 25, 50, 75}) {
                LaserEmitterBlockEntity emitter = emitter(context, block, speed);
                int expected = LaserMining.ticksToBreak(context.getWorld().getBlockState(context.getAbsolutePos(TARGET))
                        .getHardness(context.getWorld(), context.getAbsolutePos(TARGET)), speed);
                for (int tick = 1; tick < expected; tick++) {
                    tick(emitter);
                    context.assertTrue(context.getWorld().getBlockState(context.getAbsolutePos(TARGET)).isOf(block), "No premature mining at speed " + speed);
                }
                tick(emitter);
                context.assertTrue(context.getWorld().isAir(context.getAbsolutePos(TARGET)), "Mine on the expected tick at speed " + speed);
                context.removeBlock(SOURCE);
                clearDrops(context);
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void maximumBreaksHardBlocksImmediatelyButNotBedrockOrReceiverInputs(TestContext context) {
        for (Block block : new Block[]{Blocks.STONE, Blocks.OBSIDIAN, Blocks.ANCIENT_DEBRIS, Blocks.BEDROCK}) {
            LaserEmitterBlockEntity emitter = emitter(context, block, 100);
            tick(emitter);
            context.assertTrue(context.getWorld().isAir(context.getAbsolutePos(TARGET)) == (block != Blocks.BEDROCK), "Maximum speed respects hardness protections");
            context.removeBlock(SOURCE);
            clearDrops(context);
        }
        LaserEmitterBlockEntity emitter = emitter(context, Blocks.STONE, 100);
        context.setBlockState(TARGET, ModBlocks.LASER_RECEIVER.getDefaultState().with(LaserReceiverBlock.FACING, Direction.WEST));
        tick(emitter);
        context.assertTrue(context.getWorld().getBlockState(context.getAbsolutePos(TARGET)).isOf(ModBlocks.LASER_RECEIVER), "Receiver input survives instant mining");
        context.removeBlock(SOURCE);
        context.removeBlock(TARGET);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void maximumIsLimitedToOneBlockPerRealWorldTick(TestContext context) {
        BlockPos source = new BlockPos(3, 2, 3);
        for (int y = 3; y <= 10; y++) context.setBlockState(new BlockPos(3, y, 3), Blocks.OBSIDIAN);
        context.setBlockState(new BlockPos(3, 11, 3), Blocks.BEDROCK);
        context.setBlockState(source, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.UP));
        LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) context.getBlockEntity(source);
        emitter.getPropertyDelegate().set(3, 1);
        emitter.getPropertyDelegate().set(14, 100);
        emitter.getPropertyDelegate().set(16, 0);
        tick(emitter);
        for (int repeat = 0; repeat < 20; repeat++) tick(emitter);
        context.assertTrue(context.getWorld().isAir(context.getAbsolutePos(new BlockPos(3, 3, 3))), "First block breaks immediately");
        context.assertTrue(context.getWorld().getBlockState(context.getAbsolutePos(new BlockPos(3, 4, 3))).isOf(Blocks.OBSIDIAN),
                "Repeated ticker calls in the same world tick cannot mine a second block");
        context.runAtTick(12, () -> {
            for (int y = 3; y <= 10; y++) {
                context.assertTrue(context.getWorld().isAir(context.getAbsolutePos(new BlockPos(3, y, 3))), "Maximum continues mining each real tick");
            }
            context.assertTrue(context.getWorld().getBlockState(context.getAbsolutePos(new BlockPos(3, 11, 3))).isOf(Blocks.BEDROCK), "Bedrock stops the column");
            context.removeBlock(source);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void silkTouchUsesVanillaStoneGlassAndOreLoot(TestContext context) {
        Block[] blocks = {Blocks.STONE, Blocks.GLASS, Blocks.DIAMOND_ORE};
        Item[] normal = {Items.COBBLESTONE, Items.AIR, Items.DIAMOND};
        for (int index = 0; index < blocks.length; index++) {
            for (boolean silk : new boolean[]{false, true}) {
                LaserEmitterBlockEntity emitter = emitter(context, blocks[index], 100);
                emitter.getPropertyDelegate().set(15, silk ? 1 : 0);
                tick(emitter);
                Item expected = silk ? blocks[index].asItem() : normal[index];
                context.assertTrue(itemCount(context, expected) == (expected == Items.AIR ? 0 : 1), "Correct " + (silk ? "Silk Touch" : "normal") + " loot");
                context.assertTrue(drops(context).stream().allMatch(item -> item.getStack().isOf(expected)), "No duplicate or unrelated block drops");
                if (silk) context.assertTrue(experience(context) == 0, "Silk Touch ore gives no XP");
                context.removeBlock(SOURCE);
                clearDrops(context);
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void disablingDropsRemovesLootXpAndContainerContents(TestContext context) {
        for (Block block : new Block[]{Blocks.DIAMOND_ORE, Blocks.CHEST, Blocks.SHULKER_BOX}) {
            LaserEmitterBlockEntity emitter = emitter(context, block, 100);
            if (context.getBlockEntity(TARGET) instanceof Inventory inventory) inventory.setStack(0, new ItemStack(Items.DIAMOND, 32));
            emitter.getPropertyDelegate().set(15, 1);
            emitter.getPropertyDelegate().set(16, 0);
            tick(emitter);
            context.assertTrue(context.getWorld().isAir(context.getAbsolutePos(TARGET)), "No-drops mining still removes the block");
            context.assertTrue(drops(context).isEmpty() && experience(context) == 0, "No items, inventory contents, or XP when drops are off");
            context.removeBlock(SOURCE);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void silkTouchPreservesContainerContentsWithoutDuplication(TestContext context) {
        for (Block block : new Block[]{Blocks.CHEST, Blocks.SHULKER_BOX}) {
            LaserEmitterBlockEntity emitter = emitter(context, block, 100);
            Inventory inventory = (Inventory) context.getBlockEntity(TARGET);
            inventory.setStack(0, new ItemStack(Items.DIAMOND, 32));
            emitter.getPropertyDelegate().set(15, 1);
            tick(emitter);
            context.assertTrue(itemCount(context, block.asItem()) == 1, "One container item");
            if (block == Blocks.CHEST) {
                context.assertTrue(itemCount(context, Items.DIAMOND) == 32, "Chest contents spill once");
            } else {
                ItemStack box = drops(context).stream().filter(item -> item.getStack().isOf(Items.SHULKER_BOX)).findFirst().orElseThrow().getStack();
                var savedItems = box.getNbt().getCompound("BlockEntityTag").getList("Items", NbtElement.COMPOUND_TYPE);
                context.assertTrue(ItemStack.fromNbt(savedItems.getCompound(0)).getCount() == 32, "Shulker contents remain in its NBT");
                context.assertTrue(itemCount(context, Items.DIAMOND) == 0, "Shulker contents are not also dropped loose");
            }
            context.removeBlock(SOURCE);
            clearDrops(context);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dropGameRuleStillAppliesToSilkTouch(TestContext context) {
        var rule = context.getWorld().getGameRules().get(GameRules.DO_TILE_DROPS);
        boolean original = rule.get();
        try {
            rule.set(false, context.getWorld().getServer());
            LaserEmitterBlockEntity emitter = emitter(context, Blocks.STONE, 100);
            emitter.getPropertyDelegate().set(15, 1);
            tick(emitter);
            context.assertTrue(drops(context).isEmpty(), "Silk Touch does not bypass doTileDrops");
            context.removeBlock(SOURCE);
        } finally {
            rule.set(original, context.getWorld().getServer());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void destructionSwitchAndCubeRoutingKeepMiningSettings(TestContext context) {
        LaserEmitterBlockEntity emitter = emitter(context, Blocks.STONE, 100);
        emitter.getPropertyDelegate().set(3, 0);
        tick(emitter);
        context.assertTrue(context.getWorld().getBlockState(context.getAbsolutePos(TARGET)).isOf(Blocks.STONE), "Maximum speed cannot enable block destruction");
        context.removeBlock(TARGET);
        RefocusingCubeEntity cube = new RefocusingCubeEntity(ModEntities.REFOCUSING_CUBE, context.getWorld());
        cube.setNoGravity(true);
        cube.orient(0, 0);
        cube.setPosition(context.getAbsolute(new Vec3d(3.5D, 4.05D, 3.5D)));
        context.getWorld().spawnEntity(cube);
        context.setBlockState(new BlockPos(3, 4, 6), Blocks.GLASS);
        emitter.getPropertyDelegate().set(3, 1);
        emitter.getPropertyDelegate().set(15, 1);
        tick(emitter);
        BlockPos redirectedTarget = context.getAbsolutePos(new BlockPos(3, 4, 6));
        context.assertTrue(context.getWorld().isAir(redirectedTarget), "Refocused beam uses the maximum mining speed");
        context.assertTrue(context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(redirectedTarget).expand(1),
                item -> item.getStack().isOf(Items.GLASS)).size() == 1, "Refocused beam also uses Silk Touch");
        context.removeBlock(SOURCE);
        cube.discard();
        context.complete();
    }

    private static LaserEmitterBlockEntity emitter(TestContext context, Block target, int speed) {
        context.setBlockState(TARGET, target);
        context.setBlockState(new BlockPos(6, 4, 3), Blocks.BEDROCK);
        context.setBlockState(SOURCE, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) context.getBlockEntity(SOURCE);
        emitter.getPropertyDelegate().set(3, 1);
        emitter.getPropertyDelegate().set(13, 8);
        emitter.getPropertyDelegate().set(14, speed);
        return emitter;
    }

    private static void tick(LaserEmitterBlockEntity emitter) {
        LaserEmitterBlockEntity.serverTick(emitter.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
    }

    private static Box dropArea(TestContext context) {
        return new Box(context.getAbsolutePos(TARGET)).expand(1);
    }

    private static List<ItemEntity> drops(TestContext context) {
        return context.getWorld().getEntitiesByClass(ItemEntity.class, dropArea(context), item -> true);
    }

    private static int itemCount(TestContext context, Item item) {
        return drops(context).stream().filter(entity -> entity.getStack().isOf(item)).mapToInt(entity -> entity.getStack().getCount()).sum();
    }

    private static int experience(TestContext context) {
        return context.getWorld().getEntitiesByClass(ExperienceOrbEntity.class, dropArea(context), orb -> true).size();
    }

    private static void clearDrops(TestContext context) {
        drops(context).forEach(ItemEntity::discard);
        context.getWorld().getEntitiesByClass(ExperienceOrbEntity.class, dropArea(context), orb -> true).forEach(ExperienceOrbEntity::discard);
    }
}
