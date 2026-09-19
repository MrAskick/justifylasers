package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.industry.CrystalGrowth;
import net.askcraft.justifylasers.industry.NutrientFluid;
import net.askcraft.justifylasers.industry.ProcessFluid;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.platform.PlatformNutrients;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModNutrients {
    public static final Map<ProcessFluid, NutrientFluid> STILL = new EnumMap<>(ProcessFluid.class), FLOWING = new EnumMap<>(ProcessFluid.class);
    public static final Map<ProcessFluid, FluidBlock> BLOCKS = new EnumMap<>(ProcessFluid.class);
    public static final Map<ProcessFluid, Item> BUCKETS = new EnumMap<>(ProcessFluid.class);
    public static final Map<CrystalGrowth, Item> GROWN = new EnumMap<>(CrystalGrowth.class);
    public static final Map<CrystalGrowth, List<Item>> SEEDS = new EnumMap<>(CrystalGrowth.class);

    public static void initialize() {
        PlatformNutrients.initialize();
        Platform.onRegister(RegistryKeys.FLUID, () -> {
            for (var kind : ProcessFluid.values()) if (kind != ProcessFluid.WATER) {
                STILL.put(kind, PlatformNutrients.create(kind, false));
                FLOWING.put(kind, PlatformNutrients.create(kind, true));
            }
            STILL.forEach((kind, fluid) -> Platform.register(Registries.FLUID, JustifyLasers.id(kind.fluidId()), fluid));
            FLOWING.forEach((kind, fluid) -> Platform.register(Registries.FLUID, JustifyLasers.id("flowing_" + kind.fluidId()), fluid));
        });
    }

    public static void blocks() {
        STILL.forEach((kind, fluid) -> BLOCKS.put(kind, Platform.register(Registries.BLOCK, JustifyLasers.id(kind.fluidId()),
                new FluidBlock(fluid, AbstractBlock.Settings.copy(Blocks.WATER)) { })));
    }

    public static void items() {
        STILL.forEach((kind, fluid) -> BUCKETS.put(kind, Platform.register(Registries.ITEM, JustifyLasers.id(kind.fluidId() + "_bucket"),
                new BucketItem(fluid, new Item.Settings().recipeRemainder(Items.BUCKET).maxCount(1)))));
        for (var kind : CrystalGrowth.values()) {
            GROWN.put(kind, item("grown_" + kind.id() + "_crystal"));
            SEEDS.put(kind, List.of(legacy("damaged_" + kind.id() + "_seed", kind, 1), legacy("cracked_" + kind.id() + "_seed", kind, 2), legacy(kind.id() + "_seed_fragment", kind, 3)));
        }
    }
    private static Item item(String id) { return Platform.register(Registries.ITEM, JustifyLasers.id(id), Platform.partItem(new Item.Settings(), id)); }
    private static Item legacy(String id, CrystalGrowth crystal, int stage) {
        return Platform.register(Registries.ITEM, JustifyLasers.id(id), new net.askcraft.justifylasers.item.LegacyCrystalSeedItem(new Item.Settings(), crystal, stage));
    }
    private ModNutrients() { }
}
