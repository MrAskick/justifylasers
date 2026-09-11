package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.item.LaserEmitterItem;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    public static Block LASER_EMITTER;

    public static Item LASER_EMITTER_ITEM;

    public static Block POWERED_LASER_EMITTER;

    public static Item POWERED_LASER_EMITTER_ITEM;

    public static Block LASER_RECEIVER;

    public static Item LASER_RECEIVER_ITEM;

    private ModBlocks() {
    }

    public static void initialize() {
        LASER_EMITTER = Platform.register(
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

        POWERED_LASER_EMITTER = Platform.register(
                Registries.BLOCK, JustifyLasers.id("powered_laser_emitter"),
                new LaserEmitterBlock(AbstractBlock.Settings.create()
                        .mapColor(MapColor.IRON_GRAY).strength(4.5F, 12.0F).requiresTool()
                        .sounds(BlockSoundGroup.METAL).nonOpaque()
                        .luminance(state -> state.get(LaserEmitterBlock.MINECRAFT_LIGHTING) ? 15 : 0)));

        LASER_RECEIVER = Platform.register(
                Registries.BLOCK, JustifyLasers.id("laser_receiver"),
                new LaserReceiverBlock(AbstractBlock.Settings.create()
                        .mapColor(MapColor.IRON_GRAY).strength(4.5F, 12.0F).requiresTool()
                        .sounds(BlockSoundGroup.METAL).nonOpaque())
        );
    }

    public static void initializeItems() {
        LASER_EMITTER_ITEM = Platform.register(
                Registries.ITEM,
                JustifyLasers.id("laser_emitter"),
                new LaserEmitterItem(LASER_EMITTER, new Item.Settings(), false)
        );

        POWERED_LASER_EMITTER_ITEM = Platform.register(
                Registries.ITEM, JustifyLasers.id("powered_laser_emitter"),
                new LaserEmitterItem(POWERED_LASER_EMITTER, new Item.Settings(), true));

        LASER_RECEIVER_ITEM = Platform.register(
                Registries.ITEM, JustifyLasers.id("laser_receiver"),
                new BlockItem(LASER_RECEIVER, new Item.Settings())
        );
        Platform.addToRedstoneTab(LASER_EMITTER_ITEM, LASER_RECEIVER_ITEM);
    }
}
