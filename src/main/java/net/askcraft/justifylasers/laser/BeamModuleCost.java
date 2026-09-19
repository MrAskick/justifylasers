package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserEnergyCost;
import net.askcraft.justifylasers.energy.LaserModule;

/** The same optical price applies inside an emitter and at a world-mounted module. */
public final class BeamModuleCost {
    public static long lumens(LaserModule module, int strength, int knockback, int frequency,
                              int miningSpeed, boolean ignition, int upgradeCount, int units) {
        var config = LaserConfig.get();
        var rates = config.rates();
        boolean entity = module.isEntityMode();
        boolean healing = module == LaserModule.ENTITY_HEAL;
        long rate = LaserEnergyCost.perTick(rates, module == LaserModule.BLOCK_DESTRUCTION, miningSpeed,
                entity || module == LaserModule.IGNITION,
                healing ? .1 : entity ? LaserDamage.damageForStep(strength) : 0,
                healing ? 1 : LaserEntityMode.of(module).movesEntities() ? 20 : frequency,
                module == LaserModule.ENTITY_DAMAGE ? LaserDamage.knockbackForStep(knockback) : 0,
                module == LaserModule.IGNITION || module == LaserModule.ENTITY_DAMAGE && ignition,
                units) - rates.basePerTick();
        rate += upgradeCount;
        if (module == LaserModule.BLOCK_DESTRUCTION && ignition) rate += (long) Math.ceil(rates.ignitionPerHit());
        return LuminousFlux.clamp(Math.max(1, rate) * 2L * config.lumensPerEnergyUnit);
    }

    private BeamModuleCost() { }
}
