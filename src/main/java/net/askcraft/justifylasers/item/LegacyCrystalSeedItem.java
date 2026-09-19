package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.industry.CrystalGrowth;
import net.askcraft.justifylasers.industry.CrystalSeed;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** Load-only aliases for alpha.35 saves; no recipes, creative entries or new outputs use these items. */
public final class LegacyCrystalSeedItem extends Item {
    private final CrystalGrowth crystal;
    private final int stage;
    public LegacyCrystalSeedItem(Settings settings, CrystalGrowth crystal, int stage) { super(settings); this.crystal = crystal; this.stage = stage; }
    public CrystalGrowth crystal() { return crystal; }
    public int stage() { return stage; }
    @Override public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof PlayerEntity player)) return;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i) == stack) {
            inventory.setStack(i, CrystalSeed.migrate(stack)); inventory.markDirty(); break;
        }
    }
}
