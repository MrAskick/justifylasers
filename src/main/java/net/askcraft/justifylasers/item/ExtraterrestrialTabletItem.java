package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.energy.LaserEnergyHost;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.screen.TabletScreenHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class ExtraterrestrialTabletItem extends Item implements net.askcraft.justifylasers.energy.RechargeableItem {
    public static final int CAPACITY = 50_000, TRANSFER = 256, WRITE_COST = 1_000, SCREEN_COST = 1;

    public ExtraterrestrialTabletItem(Settings settings) { super(settings); }
    @Override public int energyCapacity() { return CAPACITY; }
    @Override public int energyTransfer() { return TRANSFER; }

    public int readEnergy(ItemStack stack) { return MathHelper.clamp(GameVersion.itemData(stack).getInt("TabletEnergy"), 0, CAPACITY); }
    public void writeEnergy(ItemStack stack, int amount) {
        var data = GameVersion.itemData(stack);
        data.putInt("TabletEnergy", MathHelper.clamp(amount, 0, CAPACITY));
        GameVersion.setItemData(stack, data);
    }

    public static int charge(ItemStack stack) {
        return stack.getItem() instanceof ExtraterrestrialTabletItem tablet && stack.getCount() == 1 ? tablet.readEnergy(stack) : 0;
    }
    public static void setCharge(ItemStack stack, int amount) {
        if (stack.getItem() instanceof ExtraterrestrialTabletItem tablet && stack.getCount() == 1) tablet.writeEnergy(stack, amount);
    }
    public static int receive(ItemStack stack, int amount, boolean simulate) {
        if (amount <= 0 || !(stack.getItem() instanceof ExtraterrestrialTabletItem) || stack.getCount() != 1) return 0;
        int accepted = Math.min(Math.min(amount, TRANSFER), CAPACITY - charge(stack));
        if (!simulate && accepted > 0) setCharge(stack, charge(stack) + accepted);
        return accepted;
    }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (!world.isClient && !player.isSpectator())
            Platform.openScreen(player, new TabletScreenHandler.Factory(hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot : 40));
        return TypedActionResult.success(player.getStackInHand(hand), world.isClient);
    }

    @Override public ActionResult useOnBlock(ItemUsageContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isSneaking() || player.isSpectator()) return ActionResult.PASS;
        var block = context.getWorld().getBlockEntity(context.getBlockPos());
        if (!(block instanceof LaserEnergyHost host)) return ActionResult.PASS;
        if (block instanceof IndustrialMachineBlockEntity machine && !machine.canAccess(player)
                || block instanceof LaserEmitterBlockEntity emitter && !emitter.canAccess(player)) return ActionResult.FAIL;
        if (!context.getWorld().isClient) {
            int amount = Math.min(CAPACITY - charge(context.getStack()), host.energy().stored());
            if (amount > 0 && host.energy().consume(amount)) {
                setCharge(context.getStack(), charge(context.getStack()) + amount);
                block.markDirty(); player.getInventory().markDirty();
            }
            player.sendMessage(Text.translatable("message.justifylasers.tablet.charge", charge(context.getStack()) * 100 / CAPACITY), true);
        }
        return ActionResult.SUCCESS;
    }

    @Override public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient && entity instanceof PlayerEntity player
                && player.currentScreenHandler instanceof TabletScreenHandler screen
                && screen.tablet() == stack && screen.canUse(player) && charge(stack) > 0) {
            setCharge(stack, charge(stack) - SCREEN_COST); player.getInventory().markDirty();
        }
    }
    @Override public boolean isItemBarVisible(ItemStack stack) { return true; }
    @Override public int getItemBarStep(ItemStack stack) { return Math.round(charge(stack) * 13F / CAPACITY); }
    @Override public int getItemBarColor(ItemStack stack) { return charge(stack) > WRITE_COST ? 0x34DDF2 : 0xDA794E; }
}
