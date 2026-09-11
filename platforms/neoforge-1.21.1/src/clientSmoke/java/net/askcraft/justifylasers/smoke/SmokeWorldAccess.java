package net.askcraft.justifylasers.smoke;

import net.minecraft.client.MinecraftClient;

public final class SmokeWorldAccess {
    public static void start(MinecraftClient client) {
        client.createIntegratedServerLoader().start("cube-smoke", () -> client.scheduleStop());
    }
}
