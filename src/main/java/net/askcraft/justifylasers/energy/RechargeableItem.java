package net.askcraft.justifylasers.energy;

import net.minecraft.item.ItemStack;

public interface RechargeableItem {
    int energyCapacity();
    int energyTransfer();
    int readEnergy(ItemStack stack);
    void writeEnergy(ItemStack stack, int amount);

    static boolean accepts(ItemStack stack) { return stack.getCount() == 1 && stack.getItem() instanceof RechargeableItem; }
    static int stored(ItemStack stack) { return accepts(stack) ? ((RechargeableItem) stack.getItem()).readEnergy(stack) : 0; }
    static int capacity(ItemStack stack) { return accepts(stack) ? ((RechargeableItem) stack.getItem()).energyCapacity() : 0; }
    static int receive(ItemStack stack, int amount, boolean simulate) {
        if (!accepts(stack) || amount <= 0) return 0;
        var item = (RechargeableItem) stack.getItem();
        int stored = item.readEnergy(stack);
        int accepted = Math.min(Math.min(amount, item.energyTransfer()), item.energyCapacity() - stored);
        if (!simulate && accepted > 0) item.writeEnergy(stack, stored + accepted);
        return accepted;
    }
}
