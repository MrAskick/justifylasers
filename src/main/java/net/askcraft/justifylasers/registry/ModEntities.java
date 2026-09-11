package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

public final class ModEntities {
    public static EntityType<RefocusingCubeEntity> REFOCUSING_CUBE;

    public static Item REFOCUSING_CUBE_ITEM;

    public static void initialize() {
        REFOCUSING_CUBE = Platform.register(
                Registries.ENTITY_TYPE, JustifyLasers.id("refocusing_cube"),
                Platform.cubeEntityType());
    }

    public static void initializeItems() {
        REFOCUSING_CUBE_ITEM = Platform.register(Registries.ITEM,
                JustifyLasers.id("refocusing_cube"), Platform.cubeItem(new Item.Settings().maxCount(1)));
        Platform.addToRedstoneTab(REFOCUSING_CUBE_ITEM);
    }

    private ModEntities() {
    }
}
