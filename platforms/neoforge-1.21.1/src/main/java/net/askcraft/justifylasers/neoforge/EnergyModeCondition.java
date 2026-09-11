package net.askcraft.justifylasers.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.askcraft.justifylasers.config.LaserConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

public record EnergyModeCondition(boolean enabled) implements ICondition {
    public static final MapCodec<EnergyModeCondition> CODEC =
            Codec.BOOL.fieldOf("enabled").xmap(EnergyModeCondition::new, EnergyModeCondition::enabled);

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(IContext context) {
        return enabled == LaserConfig.technicalMode();
    }
}
