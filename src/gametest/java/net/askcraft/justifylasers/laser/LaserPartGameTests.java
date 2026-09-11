package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserPartBlock;
import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.item.LaserPartItem;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class LaserPartGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void allNineteenPartsPlaceAndReturnExactlyOneUsableItem(TestContext context) {
        var world = context.getWorld();
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        BlockPos support = context.getAbsolutePos(new BlockPos(3, 2, 3));
        BlockPos target = support.up();
        player.setPosition(Vec3d.ofCenter(support.north(2)));
        player.setYaw(0);
        world.setBlockState(support, Blocks.STONE.getDefaultState());
        int tested = 0;
        for (var item : ModLaserParts.items()) {
            context.assertTrue(item instanceof LaserPartItem, "Part remains a typed module/crystal block item");
            ItemStack stack = new ItemStack(item, Math.min(2, item.getMaxCount()));
            int original = stack.getCount();
            player.setStackInHand(Hand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3d.ofCenter(support).add(0, 0.5, 0), Direction.UP, support, false);
            context.assertTrue(stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(), "Place " + item);
            context.assertTrue(stack.getCount() == original - 1, "Consume exactly one " + item);
            var block = ((LaserPartItem) item).getBlock();
            context.assertTrue(world.getBlockState(target).isOf(block), "Correct decoration block");
            context.assertTrue(world.getBlockState(target).get(LaserPartBlock.FACING) == Direction.NORTH, "Face the placing player");
            context.assertTrue(world.getBlockEntity(target) instanceof LaserPartBlockEntity, "Non-ticking display entity is present");
            context.assertTrue(world.getBlockState(target).getLuminance() == 0, "Shader emission must not add vanilla block light");
            context.assertTrue(block.asItem() == item, "Pick block returns the original module/crystal");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            context.assertTrue(player.interactionManager.tryBreakBlock(target), "Survival mining succeeds");
            var drops = world.getEntitiesByClass(ItemEntity.class, new Box(target).expand(0.5), entity -> entity.getStack().isOf(item));
            context.assertTrue(drops.size() == 1 && drops.get(0).getStack().getCount() == 1, "No loss or duplication when mined: " + item);
            drops.forEach(ItemEntity::discard);
            tested++;
        }
        context.assertTrue(tested == 19, "Every decoration covered");
        player.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativePlacementAndOccupiedSpaceDoNotConsumeParts(TestContext context) {
        var world = context.getWorld();
        var player = context.createMockCreativeServerPlayerInWorld();
        BlockPos support = context.getAbsolutePos(new BlockPos(3, 2, 3));
        player.setPosition(Vec3d.ofCenter(support.north(2)));
        world.setBlockState(support, Blocks.STONE.getDefaultState());
        var item = ModLaserParts.CRYSTALS.get(LaserColor.CYAN);
        ItemStack stack = new ItemStack(item);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3d.ofCenter(support).add(0, 0.5, 0), Direction.UP, support, false);
        context.assertTrue(stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(), "Creative placement");
        context.assertTrue(stack.getCount() == 1, "Creative placement keeps the item");
        context.assertTrue(player.interactionManager.tryBreakBlock(support.up()), "Creative removal");
        context.assertTrue(world.getEntitiesByClass(ItemEntity.class, new Box(support.up()).expand(0.5), entity -> true).isEmpty(), "Creative removal has no loot");
        player.changeGameMode(GameMode.SURVIVAL);
        world.setBlockState(support.up(), Blocks.BEDROCK.getDefaultState());
        context.assertFalse(stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit)).isAccepted(), "Cannot overwrite occupied block");
        context.assertTrue(stack.getCount() == 1 && world.getBlockState(support.up()).isOf(Blocks.BEDROCK), "Failed placement preserves inventory and world");
        player.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void decorationRotationAndCollisionStayInsideOneBlock(TestContext context) {
        for (var block : ModLaserParts.DECORATIONS.values()) {
            var initial = block.getDefaultState();
            var state = initial;
            for (int i = 0; i < 4; i++) {
                var shape = state.getCollisionShape(context.getWorld(), BlockPos.ORIGIN);
                context.assertFalse(shape.isEmpty(), "Solid decoration");
                var bounds = shape.getBoundingBox();
                context.assertTrue(bounds.minX >= 0 && bounds.minY >= 0 && bounds.minZ >= 0
                        && bounds.maxX <= 1 && bounds.maxY <= 1 && bounds.maxZ <= 1, "Collision fits block");
                state = state.rotate(BlockRotation.CLOCKWISE_90);
            }
            context.assertTrue(state == initial, "Four turns restore block state");
        }
        context.complete();
    }
}
