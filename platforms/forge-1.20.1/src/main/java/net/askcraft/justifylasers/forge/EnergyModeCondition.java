package net.askcraft.justifylasers.forge;

import com.google.gson.JsonObject;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.config.LaserConfig;
import net.minecraft.util.Identifier;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

public record EnergyModeCondition(boolean enabled) implements ICondition {
    private static final Identifier ID = JustifyLasers.id("energy_mode");

    @Override
    public Identifier getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return enabled == LaserConfig.technicalMode();
    }

    public static final class Serializer implements IConditionSerializer<EnergyModeCondition> {
        @Override
        public void write(JsonObject json, EnergyModeCondition condition) {
            json.addProperty("enabled", condition.enabled());
        }

        @Override
        public EnergyModeCondition read(JsonObject json) {
            return new EnergyModeCondition(json.get("enabled").getAsBoolean());
        }

        @Override
        public Identifier getID() {
            return ID;
        }
    }
}
