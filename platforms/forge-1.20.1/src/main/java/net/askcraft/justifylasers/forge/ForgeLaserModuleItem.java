package net.askcraft.justifylasers.forge;

import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.item.LaserModuleItem;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public final class ForgeLaserModuleItem extends LaserModuleItem {
    public ForgeLaserModuleItem(Settings settings, LaserModule module, int rangePerItem) {
        super(settings, module, rangePerItem);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ClientPlatform.partItemExtension());
    }
}
