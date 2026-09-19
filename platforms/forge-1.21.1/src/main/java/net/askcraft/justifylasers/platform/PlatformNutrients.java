package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.industry.NutrientFluid;
import net.askcraft.justifylasers.industry.ProcessFluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.EnumMap;
import java.util.Map;

public final class PlatformNutrients {
    public static final Map<ProcessFluid, FluidType> TYPES = new EnumMap<>(ProcessFluid.class);

    public static void initialize() {
        for (var kind : ProcessFluid.values()) if (kind != ProcessFluid.WATER) {
            TYPES.put(kind, new FluidType(FluidType.Properties.create().density(1000).viscosity(1000)
                    .canSwim(true).canDrown(true).canPushEntity(true).canExtinguish(true).supportsBoating(true)
                    .motionScale(.014).fallDistanceModifier(.5F)) {
                @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions() {
                        @Override public net.minecraft.util.Identifier getStillTexture() { return GameVersion.id("minecraft", "block/water_still"); }
                        @Override public net.minecraft.util.Identifier getFlowingTexture() { return GameVersion.id("minecraft", "block/water_flow"); }
                        @Override public int getTintColor() { return 0xFF000000 | kind.rgb(); }
                    });
                }
            });
        }
        Platform.onRegister(ForgeRegistries.Keys.FLUID_TYPES, () ->
                TYPES.forEach((kind, type) -> ForgeRegistries.FLUID_TYPES.get().register(JustifyLasers.id(kind.fluidId()), type)));
    }
    public static NutrientFluid create(ProcessFluid kind, boolean flowing) {
        return new NutrientFluid(kind, flowing) {
            @Override public FluidType getFluidType() { return TYPES.get(kind); }
        };
    }
    private PlatformNutrients() { }
}
