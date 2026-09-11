package net.askcraft.justifylasers.fabric;

import com.mojang.serialization.Codec;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.config.LaserConfig;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.minecraft.registry.RegistryWrapper;

public record EnergyModeCondition(boolean enabled) implements ResourceCondition {
    public static final ResourceConditionType<EnergyModeCondition> TYPE = ResourceConditionType.create(
            JustifyLasers.id("energy_mode"), Codec.BOOL.fieldOf("enabled").xmap(EnergyModeCondition::new, EnergyModeCondition::enabled));

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean test(RegistryWrapper.WrapperLookup registries) {
        return enabled == LaserConfig.technicalMode();
    }
}
