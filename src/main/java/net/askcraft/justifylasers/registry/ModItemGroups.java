package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroups {
    public static final ItemGroup JUSTIFY_LASERS = Registry.register(
            Registries.ITEM_GROUP,
            JustifyLasers.id("justify_lasers"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.justifylasers"))
                    .icon(ModBlocks.LASER_EMITTER_ITEM::getDefaultStack)
                    .entries((displayContext, entries) -> {
                        entries.add(ModBlocks.LASER_EMITTER_ITEM);
                        entries.add(ModBlocks.LASER_RECEIVER_ITEM);
                        entries.add(ModEntities.REFOCUSING_CUBE_ITEM);
                    })
                    .build()
    );

    public static void initialize() {
        // Registers the group after its items have been initialized.
    }

    private ModItemGroups() {
    }
}
