package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.energy.LaserEnergyHost;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class PlatformEnergyStorage implements IEnergyStorage {
    private final LaserEnergyHost host;
    private final java.util.function.BooleanSupplier available;

    public PlatformEnergyStorage(LaserEnergyHost host) {
        this(host, () -> true);
    }

    public PlatformEnergyStorage(LaserEnergyHost host, java.util.function.BooleanSupplier available) {
        this.host = host;
        this.available = available;
    }

    @Override
    public int receiveEnergy(int requested, boolean simulate) {
        return available.getAsBoolean() && host.acceptsEnergy() ? host.energy().receive(requested, simulate) : 0;
    }

    @Override
    public int extractEnergy(int requested, boolean simulate) {
        return available.getAsBoolean() && host.exportsEnergy() ? host.energy().extract(requested, simulate) : 0;
    }

    @Override
    public int getEnergyStored() {
        return host.energy().stored();
    }

    @Override
    public int getMaxEnergyStored() {
        return host.energy().capacity();
    }

    @Override
    public boolean canExtract() {
        return available.getAsBoolean() && host.exportsEnergy();
    }

    @Override
    public boolean canReceive() {
        return available.getAsBoolean() && host.acceptsEnergy();
    }
}
