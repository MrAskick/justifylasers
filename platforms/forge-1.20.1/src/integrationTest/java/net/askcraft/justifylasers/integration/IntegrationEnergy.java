package net.askcraft.justifylasers.integration;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.minecraft.util.math.Direction;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public final class IntegrationEnergy {
    public static net.minecraft.server.network.ServerPlayerEntity serverPlayer(net.minecraft.test.TestContext context) {
        return net.minecraftforge.common.util.FakePlayerFactory.get(context.getWorld(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "laser-test-guest"));
    }

    public static net.minecraft.entity.player.PlayerEntity player(net.minecraft.test.TestContext context) {
        return context.createMockSurvivalPlayer();
    }

    public static long receive(LaserEmitterBlockEntity emitter, int amount, boolean simulate) {
        var port = emitter.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(() -> new AssertionError("Missing energy capability"));
        return port.receiveEnergy(amount, simulate);
    }

    public static void roundTrip(LaserEmitterBlockEntity emitter) {
        var saved = emitter.createNbt();
        emitter.readNbt(saved);
    }
}
