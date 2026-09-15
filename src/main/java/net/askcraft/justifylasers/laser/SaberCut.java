package net.askcraft.justifylasers.laser;

public enum SaberCut {
    OVERHEAD, RISING, LEFT, RIGHT, DIAGONAL_LEFT, DIAGONAL_RIGHT, RETURNING;

    public static SaberCut select(double forward, double sideways, int stage, boolean staff) {
        if (staff && stage == 2) return RETURNING;
        if (Math.abs(sideways) > .025) {
            if (forward > .025) return sideways > 0 ? DIAGONAL_RIGHT : DIAGONAL_LEFT;
            return sideways > 0 ? RIGHT : LEFT;
        }
        return forward < -.025 || stage % 2 == 1 ? RISING : OVERHEAD;
    }
}
