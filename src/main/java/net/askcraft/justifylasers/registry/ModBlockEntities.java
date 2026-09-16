package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserReceiverBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.minecraft.block.Block;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;

public final class ModBlockEntities {
    public static BlockEntityType<net.askcraft.justifylasers.block.entity.LaserComponentBlockEntity> LASER_COMPONENT;
    public static BlockEntityType<LaserEmitterBlockEntity> LASER_EMITTER;
    public static BlockEntityType<net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity> INDUSTRIAL_MACHINE;

    public static BlockEntityType<LaserReceiverBlockEntity> LASER_RECEIVER;
    public static BlockEntityType<LaserPartBlockEntity> LASER_PART;
    public static BlockEntityType<LaserOpticBlockEntity> LASER_OPTIC;
    public static BlockEntityType<net.askcraft.justifylasers.block.entity.LaserTurretBlockEntity> LASER_TURRET;

    private ModBlockEntities() {
    }

    public static void initialize() {
        LASER_COMPONENT = Platform.register(Registries.BLOCK_ENTITY_TYPE, JustifyLasers.id("laser_component"),
                Platform.blockEntityType((pos, state) -> state.isOf(ModIndustry.COMPONENT_BLOCKS.get("optical_resonator"))
                        || state.isOf(ModIndustry.SMALL_SOLAR_CONCENTRATOR)
                        ? new net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity(pos, state)
                        : new net.askcraft.justifylasers.block.entity.LaserComponentBlockEntity(pos, state),
                        ModIndustry.COMPONENT_BLOCKS.values().toArray(Block[]::new)));
        INDUSTRIAL_MACHINE = Platform.register(Registries.BLOCK_ENTITY_TYPE, JustifyLasers.id("industrial_machine"),
                Platform.blockEntityType(net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity::new, ModIndustry.MACHINES.values().toArray(Block[]::new)));
        LASER_TURRET = Platform.register(Registries.BLOCK_ENTITY_TYPE, JustifyLasers.id("laser_turret"),
                Platform.blockEntityType(net.askcraft.justifylasers.block.entity.LaserTurretBlockEntity::new, ModBlocks.LASER_TURRET));
        LASER_OPTIC = Platform.register(Registries.BLOCK_ENTITY_TYPE, JustifyLasers.id("laser_optic"),
                Platform.blockEntityType(LaserOpticBlockEntity::new, ModBlocks.LASER_MIRROR, ModBlocks.BEAM_SPLITTER, ModBlocks.BEAM_COMBINER, ModBlocks.ENERGY_RECEIVER));
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
