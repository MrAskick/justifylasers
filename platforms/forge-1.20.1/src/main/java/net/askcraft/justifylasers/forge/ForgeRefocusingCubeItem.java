package net.askcraft.justifylasers.forge;

import net.askcraft.justifylasers.item.RefocusingCubeItem;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

public final class ForgeRefocusingCubeItem extends RefocusingCubeItem {
    public ForgeRefocusingCubeItem(Settings settings) {
        super(settings);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ClientPlatform.cubeItemExtension());
    }
}
