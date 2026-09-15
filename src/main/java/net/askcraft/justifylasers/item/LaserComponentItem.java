package net.askcraft.justifylasers.item;

import net.minecraft.item.Item;

public final class LaserComponentItem extends Item {
    private final String component;

    public LaserComponentItem(Settings settings, String component) {
        super(settings);
        this.component = component;
    }

    public String component() { return component; }
    @Override public String getTranslationKey() { return "item.justifylasers." + component; }

    @Override public void inventoryTick(net.minecraft.item.ItemStack stack, net.minecraft.world.World world,
                                        net.minecraft.entity.Entity entity, int slot, boolean selected) {
        var replacement = net.askcraft.justifylasers.registry.ModIndustry.COMPONENTS.get(component);
        if (world.isClient || replacement == this || replacement == null || !(entity instanceof net.minecraft.entity.player.PlayerEntity player)) return;
        // Keep old registry IDs loadable; migrate the actual stack only when its inventory is available.
        var inventory = player.getInventory();
        for (int index = 0; index < inventory.size(); index++) if (inventory.getStack(index) == stack) {
            inventory.setStack(index, net.askcraft.justifylasers.platform.GameVersion.replaceItem(stack, replacement));
            inventory.markDirty();
            break;
        }
    }
}
