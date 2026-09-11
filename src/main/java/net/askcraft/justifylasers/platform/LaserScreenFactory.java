package net.askcraft.justifylasers.platform;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public interface LaserScreenFactory extends NamedScreenHandlerFactory {
    BlockPos getPos();
    void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buffer);

    @FunctionalInterface
    interface MenuFactory<T extends ScreenHandler> {
        T create(int syncId, PlayerInventory inventory, BlockPos pos);
    }
}
