package net.askcraft.justifylasers.laser;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public final class LaserMining {
    public static final int MAX_SPEED_STEP = 100;
    public static final int DEFAULT_SPEED_STEP = 0;

    public static int clampSpeedStep(int step) {
        return MathHelper.clamp(step, 0, MAX_SPEED_STEP);
    }

    public static int ticksToBreak(float hardness, int speedStep) {
        double original = MathHelper.clamp(18.0F + hardness * 28.0F, 12.0F, 600.0F);
        // Interpolate mining time logarithmically: the old hardness curve at 0, one tick at 100.
        return (int) Math.ceil(Math.pow(original, 1.0D - clampSpeedStep(speedStep) / (double) MAX_SPEED_STEP));
    }

    public static boolean breakBlock(ServerWorld world, BlockPos pos, boolean drops, boolean silkTouch) {
        if (drops && !silkTouch) {
            return world.breakBlock(pos, true, null);
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!drops) {
            // Container removal scatters its contents even when World.breakBlock's drop flag is false.
            Inventory inventory = blockEntity instanceof Inventory contents ? contents : null;
            List<ItemStack> saved = new ArrayList<>();
            if (inventory != null) {
                for (int slot = 0; slot < inventory.size(); slot++) saved.add(inventory.getStack(slot).copy());
                inventory.clear();
            }
            boolean broken = world.breakBlock(pos, false, null);
            if (!broken && inventory != null && world.getBlockEntity(pos) == blockEntity) {
                for (int slot = 0; slot < saved.size(); slot++) inventory.setStack(slot, saved.get(slot));
            }
            return broken;
        }

        BlockState state = world.getBlockState(pos);
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        net.askcraft.justifylasers.platform.GameVersion.applySilkTouch(tool, world);
        // Resolve loot before removal so shulker boxes and other block-entity loot retain their data.
        List<ItemStack> loot = Block.getDroppedStacks(state, world, pos, blockEntity, null, tool);
        if (!world.breakBlock(pos, false, null)) {
            return false;
        }
        loot.forEach(stack -> Block.dropStack(world, pos, stack));
        state.onStacksDropped(world, pos, tool, true);
        return true;
    }

    private LaserMining() {
    }
}
