package net.askcraft.justifylasers.forge;

import net.askcraft.justifylasers.item.LaserPartItem;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public final class ForgeLaserPartItem extends LaserPartItem {
    public ForgeLaserPartItem(Settings settings, String partId) {
        super(settings, partId);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ClientPlatform.partItemExtension());
    }
}
