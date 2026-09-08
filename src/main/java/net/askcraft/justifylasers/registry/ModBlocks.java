package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    public static final Block LASER_EMITTER = Registry.register(
            Registries.BLOCK,
            JustifyLasers.id("laser_emitter"),
            new LaserEmitterBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.IRON_GRAY)
                    .strength(4.5F, 12.0F)
                    .requiresTool()
                    .sounds(BlockSoundGroup.METAL)
                    .nonOpaque()
                    .luminance(state -> state.get(LaserEmitterBlock.MINECRAFT_LIGHTING) ? 15 : 0))
    );

    public static final Item LASER_EMITTER_ITEM = Registry.register(
            Registries.ITEM,
            JustifyLasers.id("laser_emitter"),
            new BlockItem(LASER_EMITTER, new FabricItemSettings())
    );

    public static final Block LASER_RECEIVER = Registry.register(
            Registries.BLOCK, JustifyLasers.id("laser_receiver"),
            new LaserReceiverBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.IRON_GRAY).strength(4.5F, 12.0F).requiresTool()
                    .sounds(BlockSoundGroup.METAL).nonOpaque())
    );

    public static final Item LASER_RECEIVER_ITEM = Registry.register(
            Registries.ITEM, JustifyLasers.id("laser_receiver"),
            new BlockItem(LASER_RECEIVER, new FabricItemSettings())
    );

    private ModBlocks() {
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> {
            entries.add(LASER_EMITTER_ITEM);
            entries.add(LASER_RECEIVER_ITEM);
        });
    }
}
