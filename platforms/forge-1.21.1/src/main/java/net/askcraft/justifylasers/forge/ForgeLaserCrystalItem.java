package net.askcraft.justifylasers.forge;

import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public final class ForgeLaserCrystalItem extends LaserCrystalItem {
    public ForgeLaserCrystalItem(Settings settings, LaserColor color) {
        super(settings, color);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ClientPlatform.partItemExtension());
    }
}
