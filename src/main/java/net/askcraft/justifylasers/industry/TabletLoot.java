package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class TabletLoot {
    public static final String POOL_NAME = "justifylasers_tablet_schematic";
    public static final Map<String, Float> CHESTS = Map.of(
            "chests/simple_dungeon", .20F,
            "chests/shipwreck_treasure", .25F,
            "chests/abandoned_mineshaft", .15F,
            "chests/desert_pyramid", .15F,
            "chests/jungle_temple", .20F,
            "chests/stronghold_library", .35F,
            "chests/ancient_city", .25F);

    @Nullable
    public static LootPool.Builder pool(Identifier table) {
        Float chance = table.getNamespace().equals("minecraft") ? CHESTS.get(table.getPath()) : null;
        if (chance == null) return null;
        return LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
                .conditionally(RandomChanceLootCondition.builder(chance))
                .with(ItemEntry.builder(ModIndustry.BLUEPRINTS.get("extraterrestrial_tablet")));
    }

    private TabletLoot() { }
}
