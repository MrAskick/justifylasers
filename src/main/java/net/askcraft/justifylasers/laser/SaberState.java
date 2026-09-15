package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.config.LaserConfig;

/** Immutable, replicated combat state used by both blade collision and animation. */
public record SaberState(Action action, SaberCut cut, int stage, long started, int windup, int active,
                         int recovery, float stamina, float capacity, int sequence, boolean staff) {
    public enum Action { IDLE, ATTACK, GUARD, RECOIL, PARRY, BROKEN }

    public static SaberState idle(boolean staff, long time) {
        var config = LaserConfig.get();
        return new SaberState(Action.IDLE, SaberCut.OVERHEAD, 0, time,
                staff ? config.staffWindupTicks : config.saberWindupTicks,
                staff ? config.staffActiveTicks : config.saberActiveTicks,
                staff ? config.staffRecoveryTicks : config.saberRecoveryTicks,
                config.saberStamina, config.saberStamina, 0, staff);
    }

    public double elapsed(double time) { return Math.max(0, time - started); }
    public int duration() { return windup + active + recovery; }
    public boolean activeAt(double time) {
        double elapsed = elapsed(time);
        return action == Action.ATTACK && elapsed >= windup && elapsed < windup + active;
    }
    public boolean guarding() { return action == Action.GUARD || action == Action.PARRY; }
    public int leadingEnd() { return staff && cut == SaberCut.RETURNING ? -1 : 1; }
}
