package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.config.LaserConfig;

public enum MachineKind {
    FUEL_GENERATOR("fuel_generator", 0),
    ELECTRIC_SMELTER("electric_smelter", 2),
    CRYSTAL_GROWER("crystal_growth_chamber", 2),
    ASSEMBLY_CHAMBER("assembly_chamber", 4);

    private final String id;
    private final int inputs;

    MachineKind(String id, int inputs) { this.id = id; this.inputs = inputs; }
    public String id() { return id; }
    public boolean multiblock() { return this == CRYSTAL_GROWER || this == ASSEMBLY_CHAMBER; }
    public int inputs() { return this == FUEL_GENERATOR ? 1 : inputs; }
    public int duration() {
        return switch (this) {
            case FUEL_GENERATOR -> 0;
            case ELECTRIC_SMELTER -> LaserConfig.get().alloySmeltingTicks;
            case CRYSTAL_GROWER -> LaserConfig.get().crystalGrowthTicks;
            case ASSEMBLY_CHAMBER -> LaserConfig.get().laserAssemblyTicks;
        };
    }
    public int rate() {
        return switch (this) {
            case FUEL_GENERATOR -> LaserConfig.get().generatorPerTick;
            case ELECTRIC_SMELTER -> LaserConfig.get().smelterPerTick;
            case CRYSTAL_GROWER -> LaserConfig.get().crystalGrowerPerTick;
            case ASSEMBLY_CHAMBER -> LaserConfig.get().assemblyPerTick;
        };
    }
}
