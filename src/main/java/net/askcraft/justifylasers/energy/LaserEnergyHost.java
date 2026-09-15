package net.askcraft.justifylasers.energy;

public interface LaserEnergyHost {
    LaserEnergyBuffer energy();
    boolean acceptsEnergy();
    default boolean exportsEnergy() { return false; }
}
