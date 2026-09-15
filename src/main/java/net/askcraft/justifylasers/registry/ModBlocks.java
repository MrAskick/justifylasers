package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
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
    public static LaserOpticBlock LASER_MIRROR;
    public static LaserOpticBlock BEAM_SPLITTER;
    public static LaserOpticBlock ENERGY_RECEIVER;
    public static Item CONFIGURATOR;
    public static Block LASER_TURRET;
    public static Item LASER_GUN;
    public static Item LASER_SABER;
    public static Item LIGHT_STAFF;

    private ModBlocks() {
    }

    public static void initialize() {
        LASER_TURRET = Platform.register(Registries.BLOCK, JustifyLasers.id("laser_turret"),
                new net.askcraft.justifylasers.block.LaserTurretBlock(AbstractBlock.Settings.create()
                        .mapColor(MapColor.IRON_GRAY).strength(4, 12).requiresTool().sounds(BlockSoundGroup.METAL).nonOpaque()));
        LASER_MIRROR = optic("laser_mirror", LaserOpticBlock.Kind.MIRROR);
        BEAM_SPLITTER = optic("beam_splitter", LaserOpticBlock.Kind.SPLITTER);
        ENERGY_RECEIVER = optic("energy_receiver", LaserOpticBlock.Kind.ENERGY_RECEIVER);
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
        LASER_SABER = Platform.register(Registries.ITEM, JustifyLasers.id("laser_saber"),
                new net.askcraft.justifylasers.item.LaserSaberItem(new Item.Settings().maxCount(1), false));
        LIGHT_STAFF = Platform.register(Registries.ITEM, JustifyLasers.id("light_staff"),
                new net.askcraft.justifylasers.item.LaserSaberItem(new Item.Settings().maxCount(1), true));
        LASER_GUN = Platform.register(Registries.ITEM, JustifyLasers.id("laser_gun"), new net.askcraft.justifylasers.item.LaserGunItem(new Item.Settings().maxCount(1)));
        Platform.register(Registries.ITEM, JustifyLasers.id("laser_turret"), new BlockItem(LASER_TURRET, new Item.Settings()));
        CONFIGURATOR = Platform.register(Registries.ITEM, JustifyLasers.id("configurator"),
                new net.askcraft.justifylasers.item.LaserConfiguratorItem(new Item.Settings().maxCount(1)));
        for (LaserOpticBlock block : new LaserOpticBlock[]{LASER_MIRROR, BEAM_SPLITTER, ENERGY_RECEIVER}) {
            Platform.register(Registries.ITEM, Registries.BLOCK.getId(block), new BlockItem(block, new Item.Settings()));
        }
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

    private static LaserOpticBlock optic(String id, LaserOpticBlock.Kind kind) {
        return Platform.register(Registries.BLOCK, JustifyLasers.id(id), new LaserOpticBlock(
                AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(3, 10).requiresTool().dynamicBounds()
                        .sounds(BlockSoundGroup.METAL).nonOpaque(), kind));
    }
}
