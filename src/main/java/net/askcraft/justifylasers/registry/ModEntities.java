package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.item.RefocusingCubeItem;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    public static final EntityType<RefocusingCubeEntity> REFOCUSING_CUBE = Registry.register(
            Registries.ENTITY_TYPE, JustifyLasers.id("refocusing_cube"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, RefocusingCubeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.9F, 0.9F)).fireImmune()
                    .trackRangeBlocks(LaserEmitterBlockEntity.MAX_RANGE + 32)
                    .trackedUpdateRate(1).forceTrackedVelocityUpdates(true).build());

    public static final Item REFOCUSING_CUBE_ITEM = Registry.register(Registries.ITEM,
            JustifyLasers.id("refocusing_cube"), new RefocusingCubeItem(new FabricItemSettings().maxCount(1)));

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> entries.add(REFOCUSING_CUBE_ITEM));
    }

    private ModEntities() {
    }
}
