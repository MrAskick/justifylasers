package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.IndustrialMachineBlock;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.sound.BlockSoundGroup;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModIndustry {
    public static final Map<MachineKind, IndustrialMachineBlock> MACHINES = new EnumMap<>(MachineKind.class);
    public static final Map<String, Block> ORES = new LinkedHashMap<>();
    public static final java.util.List<String> COMPONENT_IDS = java.util.List.of("optical_resonator", "reinforced_laser_housing",
            "energy_core", "focusing_lens_assembly", "beam_controller");
    public static final Map<String, net.askcraft.justifylasers.block.LaserComponentBlock> COMPONENT_BLOCKS = new LinkedHashMap<>();
    public static net.askcraft.justifylasers.block.LaserComponentBlock SOLAR_ABSORBER, LASER_ABSORBING_GLASS, SMALL_SOLAR_CONCENTRATOR;
    public static Item RAW_WOLFRAMITE, RAW_PHOTONIC_CRYSTAL, HEAT_RESISTANT_ALLOY, LASER_CHASSIS, OPTICAL_ASSEMBLY;
    public static Item WOLFRAMITE_INGOT, PHOTONITE_CRYSTAL, CRYSTAL_MOUNT;
    public static Item BLANK_SCHEMATIC, EXTRATERRESTRIAL_TABLET, ELECTRIC_MOTOR;
    public static final Map<String, net.askcraft.justifylasers.item.LaserComponentItem> COMPONENTS = new LinkedHashMap<>();
    public static final Map<String, net.askcraft.justifylasers.item.AssemblyBlueprintItem> BLUEPRINTS = new LinkedHashMap<>();

    public static void initializeBlocks() {
        for (String id : COMPONENT_IDS) componentBlock(id, false);
        SOLAR_ABSORBER = componentBlock("solar_absorber", false);
        LASER_ABSORBING_GLASS = componentBlock("laser_absorbing_glass", true);
        SMALL_SOLAR_CONCENTRATOR = componentBlock("small_solar_concentrator", false);
        for (String id : new String[]{"wolframite_ore", "deepslate_wolframite_ore", "photonic_crystal_ore", "deepslate_photonic_crystal_ore"})
            ORES.put(id, Platform.register(Registries.BLOCK, JustifyLasers.id(id), new Block(AbstractBlock.Settings.create()
                    .mapColor(MapColor.STONE_GRAY).requiresTool().strength(id.startsWith("deepslate") ? 4.5F : 3F, 3)
                    .sounds(id.startsWith("deepslate") ? BlockSoundGroup.DEEPSLATE : BlockSoundGroup.STONE))));
        for (MachineKind kind : MachineKind.values())
            MACHINES.put(kind, Platform.register(Registries.BLOCK, JustifyLasers.id(kind.id()), new IndustrialMachineBlock(
                    AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).requiresTool().strength(4, 10)
                            .sounds(BlockSoundGroup.METAL).nonOpaque(), kind)));
    }

    public static void initializeItems() {
        for (var block : new Block[]{SOLAR_ABSORBER, LASER_ABSORBING_GLASS, SMALL_SOLAR_CONCENTRATOR})
            Platform.register(Registries.ITEM, Registries.BLOCK.getId(block), new BlockItem(block, new Item.Settings()));
        ORES.forEach((id, block) -> Platform.register(Registries.ITEM, JustifyLasers.id(id), new BlockItem(block, new Item.Settings())));
        MACHINES.forEach((kind, block) -> Platform.register(Registries.ITEM, JustifyLasers.id(kind.id()), new BlockItem(block, new Item.Settings())));
        RAW_WOLFRAMITE = Platform.register(Registries.ITEM, JustifyLasers.id("raw_wolframite"), Platform.partItem(new Item.Settings(), "raw_wolframite"));
        ELECTRIC_MOTOR = Platform.register(Registries.ITEM, JustifyLasers.id("electric_motor"), Platform.partItem(new Item.Settings(), "electric_motor"));
        RAW_PHOTONIC_CRYSTAL = Platform.register(Registries.ITEM, JustifyLasers.id("raw_photonic_crystal"), Platform.partItem(new Item.Settings(), "raw_photonic_crystal"));
        HEAT_RESISTANT_ALLOY = item("heat_resistant_alloy");
        LASER_CHASSIS = Platform.register(Registries.ITEM, JustifyLasers.id("laser_chassis"),
                new net.askcraft.justifylasers.item.LaserComponentItem(new Item.Settings(), "reinforced_laser_housing"));
        OPTICAL_ASSEMBLY = Platform.register(Registries.ITEM, JustifyLasers.id("optical_assembly"),
                new net.askcraft.justifylasers.item.LaserComponentItem(new Item.Settings(), "focusing_lens_assembly"));
        WOLFRAMITE_INGOT = item("wolframite_ingot");
        PHOTONITE_CRYSTAL = item("photonite_crystal");
        CRYSTAL_MOUNT = Platform.register(Registries.ITEM, JustifyLasers.id("crystal_mount"),
                Platform.partItem(new Item.Settings(), "crystal_mount"));
        for (String component : COMPONENT_IDS)
            COMPONENTS.put(component, Platform.register(Registries.ITEM, JustifyLasers.id(component),
                    new net.askcraft.justifylasers.item.LaserComponentItem(new Item.Settings(), component)));
        BLANK_SCHEMATIC = item("blank_schematic");
        EXTRATERRESTRIAL_TABLET = Platform.register(Registries.ITEM, JustifyLasers.id("extraterrestrial_tablet"),
                Platform.tabletItem(new Item.Settings().maxCount(1)));
        for (String device : new String[]{"powered_laser_emitter", "laser_gun", "laser_saber", "light_staff", "laser_turret",
                "refocusing_cube", "laser_receiver", "laser_mirror", "beam_splitter", "energy_receiver", "configurator",
                "block_destruction_module", "entity_damage_module", "entity_heal_module", "entity_lift_module", "entity_lower_module", "block_drops_module", "block_collection_module", "silk_touch_module", "scorch_marks_module",
                "ignition_module", "range_module", "advanced_range_module", "thickness_module", "advanced_thickness_module", "target_filter_module",
                "optical_resonator", "reinforced_laser_housing", "energy_core", "focusing_lens_assembly", "beam_controller",
                "solar_absorber", "extraterrestrial_tablet", "beam_combiner", "small_solar_concentrator", "light_bridge", "corner_light_bridge",
                "chemical_synthesizer", "laser_cutter", "spectrum_module"})
            BLUEPRINTS.put(device, Platform.register(Registries.ITEM, JustifyLasers.id(device + "_blueprint"),
                    new net.askcraft.justifylasers.item.AssemblyBlueprintItem(new Item.Settings().maxCount(1), device)));
    }

    private static Item item(String id) { return Platform.register(Registries.ITEM, JustifyLasers.id(id), new Item(new Item.Settings())); }
    private static net.askcraft.justifylasers.block.LaserComponentBlock componentBlock(String id, boolean glass) {
        var block = Platform.register(Registries.BLOCK, JustifyLasers.id(id), new net.askcraft.justifylasers.block.LaserComponentBlock(
                AbstractBlock.Settings.create().mapColor(glass ? MapColor.CLEAR : MapColor.IRON_GRAY)
                        .strength(glass ? 2.5F : 4F, 12).requiresTool().sounds(glass ? BlockSoundGroup.GLASS : BlockSoundGroup.METAL).nonOpaque(), id));
        COMPONENT_BLOCKS.put(id, block);
        return block;
    }
    private ModIndustry() { }
}
