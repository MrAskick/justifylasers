package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.registry.ModEntities;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public class RefocusingCubeItem extends Item {
    public RefocusingCubeItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (player == null || player.isSpectator()) {
            return ActionResult.FAIL;
        }
        BlockPos pos = context.getBlockPos().offset(context.getSide());
        RefocusingCubeEntity cube = new RefocusingCubeEntity(ModEntities.REFOCUSING_CUBE, world);
        cube.orient(Math.round(player.getYaw() / 90.0F) * 90.0F, 0);
        cube.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (!world.isSpaceEmpty(cube, cube.getBoundingBox())) {
            return ActionResult.FAIL;
        }
        if (!world.isClient) {
            ItemStack stack = context.getStack();
            if (stack.contains(DataComponentTypes.CUSTOM_DATA)) {
                cube.setBeamInput(new LaserBeamNetwork.CubeInput(LaserColor.byIndex(stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getInt("Color")), true));
                cube.setBeamInput(null);
            }
            if (stack.contains(DataComponentTypes.CUSTOM_NAME)) {
                cube.setCustomName(stack.getName());
            }
            if (!world.spawnEntity(cube)) {
                return ActionResult.FAIL;
            }
            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.justifylasers.refocusing_cube.input").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.justifylasers.refocusing_cube.carry").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.justifylasers.refocusing_cube.rotate").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("item.justifylasers.refocusing_cube.collect").formatted(Formatting.DARK_GRAY));
    }
}
