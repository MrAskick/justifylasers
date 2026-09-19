package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.industry.NutrientFluid;
import net.askcraft.justifylasers.industry.ProcessFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.EnumMap;
import java.util.Map;

public final class PlatformNutrients {
    public static final Map<ProcessFluid, FluidType> TYPES = new EnumMap<>(ProcessFluid.class);

    public static void initialize() {
        for (var kind : ProcessFluid.values()) if (kind != ProcessFluid.WATER) {
            TYPES.put(kind, new FluidType(FluidType.Properties.create().density(1000).viscosity(1000)
                    .canSwim(true).canDrown(true).canPushEntity(true).canExtinguish(true).supportsBoating(true)
                    .motionScale(.014).fallDistanceModifier(.5F)));
        }
        Platform.onRegister(NeoForgeRegistries.Keys.FLUID_TYPES, () ->
                TYPES.forEach((kind, type) -> Platform.register(NeoForgeRegistries.FLUID_TYPES, JustifyLasers.id(kind.fluidId()), type)));
    }
    public static NutrientFluid create(ProcessFluid kind, boolean flowing) {
        return new NutrientFluid(kind, flowing) {
            @Override public FluidType getFluidType() { return TYPES.get(kind); }
        };
    }
    private PlatformNutrients() { }
}
