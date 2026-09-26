package net.askcraft.justifylasers.addon.lasers;
import net.askcraft.justifylasers.api.JustifyClientModule;
import net.askcraft.justifylasers.addon.lasers.platform.LaserClientPlatform;
public final class LasersClientModule implements JustifyClientModule {
    public String id() { return "justifylasers"; }
    public void initialize() {
        LaserClientPlatform.initialize();
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(net.askcraft.justifylasers.registry.ModLaserParts.AMPLIFIER,
                net.askcraft.justifylasers.JustifyLasers.id("amplifier_tier"),
                (stack, world, entity, seed) -> net.askcraft.justifylasers.item.LaserAmplifierItem.tier(stack) / 10F);
    }
    public void loaderEvent(Object event) { LaserClientPlatform.event(event); }
}
