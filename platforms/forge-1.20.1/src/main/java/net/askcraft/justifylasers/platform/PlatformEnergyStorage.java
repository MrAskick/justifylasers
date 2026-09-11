package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.minecraftforge.energy.IEnergyStorage;

public final class PlatformEnergyStorage implements IEnergyStorage {
    private final LaserEmitterBlockEntity emitter;

    public PlatformEnergyStorage(LaserEmitterBlockEntity emitter) {
        this.emitter = emitter;
    }

    @Override
    public int receiveEnergy(int requested, boolean simulate) {
        return emitter.acceptsEnergy() ? emitter.energy().receive(requested, simulate) : 0;
    }

    @Override
    public int extractEnergy(int requested, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return emitter.energy().stored();
    }

    @Override
    public int getMaxEnergyStored() {
        return emitter.energy().capacity();
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return emitter.acceptsEnergy();
    }
}
