package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
    public static final BlockEntityType<LaserEmitterBlockEntity> LASER_EMITTER = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            JustifyLasers.id("laser_emitter"),
            FabricBlockEntityTypeBuilder.create(LaserEmitterBlockEntity::new, ModBlocks.LASER_EMITTER).build()
    );

    private ModBlockEntities() {
    }

    public static void initialize() {
    }
}
