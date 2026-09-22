package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.config.LaserConfig;

public enum MachineKind {
    FUEL_GENERATOR("fuel_generator", 0),
    CRYSTAL_GROWER("crystal_growth_chamber", 2),
    ASSEMBLY_CHAMBER("assembly_chamber", 4),
    CHEMICAL_SYNTHESIZER("chemical_synthesizer", 3),
    LASER_CUTTER("laser_cutter", 1),
    PHOTOPOLYMER_PRINTER("photopolymer_printer", 1),
    MODEL_ENCODER("model_encoder", 1);

    private final String id;
    private final int inputs;

    MachineKind(String id, int inputs) { this.id = id; this.inputs = inputs; }
    public String id() { return id; }
    public boolean multiblock() { return this == CRYSTAL_GROWER || this == ASSEMBLY_CHAMBER || this == LASER_CUTTER || this == PHOTOPOLYMER_PRINTER; }
    public boolean fluidTank() { return this == CRYSTAL_GROWER || this == CHEMICAL_SYNTHESIZER || this == PHOTOPOLYMER_PRINTER; }
    public boolean opticalInput() { return this == CRYSTAL_GROWER || this == LASER_CUTTER || this == PHOTOPOLYMER_PRINTER; }
    public boolean programmable() { return this == PHOTOPOLYMER_PRINTER || this == MODEL_ENCODER; }
    public int inputs() { return this == FUEL_GENERATOR ? 1 : inputs; }
    public int duration() {
        return switch (this) {
            case FUEL_GENERATOR -> 0;
            case CRYSTAL_GROWER -> LaserConfig.get().crystalGrowthTicks;
            case ASSEMBLY_CHAMBER -> LaserConfig.get().laserAssemblyTicks;
            case CHEMICAL_SYNTHESIZER -> 200;
            case LASER_CUTTER -> 160;
            case PHOTOPOLYMER_PRINTER -> 200;
            case MODEL_ENCODER -> 1;
        };
    }
    public int rate() {
        return switch (this) {
            case FUEL_GENERATOR -> LaserConfig.get().generatorPerTick;
            case CRYSTAL_GROWER -> LaserConfig.get().crystalGrowthFlux;
            case ASSEMBLY_CHAMBER -> LaserConfig.get().assemblyPerTick;
            case CHEMICAL_SYNTHESIZER -> 32;
            case LASER_CUTTER -> 64;
            case PHOTOPOLYMER_PRINTER -> 16;
            case MODEL_ENCODER -> 0;
        };
    }
}
