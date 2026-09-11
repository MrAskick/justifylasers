package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserReceiverBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.minecraft.block.Block;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;

public final class ModBlockEntities {
    public static BlockEntityType<LaserEmitterBlockEntity> LASER_EMITTER;

    public static BlockEntityType<LaserReceiverBlockEntity> LASER_RECEIVER;
    public static BlockEntityType<LaserPartBlockEntity> LASER_PART;

    private ModBlockEntities() {
    }

    public static void initialize() {
        LASER_PART = Platform.register(Registries.BLOCK_ENTITY_TYPE, JustifyLasers.id("laser_part"),
                Platform.blockEntityType(LaserPartBlockEntity::new, ModLaserParts.DECORATIONS.values().toArray(Block[]::new)));
        LASER_EMITTER = Platform.register(
                Registries.BLOCK_ENTITY_TYPE,
                JustifyLasers.id("laser_emitter"),
                Platform.blockEntityType(LaserEmitterBlockEntity::new, ModBlocks.LASER_EMITTER, ModBlocks.POWERED_LASER_EMITTER)
        );

        LASER_RECEIVER = Platform.register(
                Registries.BLOCK_ENTITY_TYPE, JustifyLasers.id("laser_receiver"),
                Platform.blockEntityType(LaserReceiverBlockEntity::new, ModBlocks.LASER_RECEIVER)
        );
    }
}
