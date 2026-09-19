package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.config.LaserConfig;

public final class BeamAttenuation {
    public static double retention(double distance) {
        return retention(distance, false);
    }
    public static double retention(double distance, boolean client) {
        return Math.pow(1 - LaserConfig.beamLoss(client), Math.max(0, distance));
    }
    private BeamAttenuation() { }
}
