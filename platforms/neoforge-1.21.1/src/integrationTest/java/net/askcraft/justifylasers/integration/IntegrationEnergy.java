package net.askcraft.justifylasers.integration;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.minecraft.util.math.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class IntegrationEnergy {
    public static net.minecraft.server.network.ServerPlayerEntity serverPlayer(net.minecraft.test.TestContext context) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(context.getWorld(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "laser-test-guest"));
    }

    public static net.minecraft.entity.player.PlayerEntity player(net.minecraft.test.TestContext context) {
        return context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
    }

    public static long receive(LaserEmitterBlockEntity emitter, int amount, boolean simulate) {
        var port = emitter.getWorld().getCapability(Capabilities.EnergyStorage.BLOCK, emitter.getPos(), Direction.UP);
        if (port == null) throw new AssertionError("Missing energy capability");
        return port.receiveEnergy(amount, simulate);
    }

    public static void roundTrip(LaserEmitterBlockEntity emitter) {
        var saved = emitter.createNbt(emitter.getWorld().getRegistryManager());
        emitter.readNbt(saved, emitter.getWorld().getRegistryManager());
    }
}
