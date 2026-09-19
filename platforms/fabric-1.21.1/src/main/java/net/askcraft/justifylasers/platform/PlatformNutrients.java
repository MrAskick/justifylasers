package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.industry.NutrientFluid;
import net.askcraft.justifylasers.industry.ProcessFluid;

public final class PlatformNutrients {
    public static void initialize() { }
    public static NutrientFluid create(ProcessFluid kind, boolean flowing) { return new NutrientFluid(kind, flowing); }
    private PlatformNutrients() { }
}
