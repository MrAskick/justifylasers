package net.askcraft.justifylasers.smoke;

import net.minecraft.client.MinecraftClient;
import net.neoforged.fml.common.Mod;

@Mod("justifylasers_client_smoke")
public class SmokeEntrypoint {
    public SmokeEntrypoint() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
                    LaserClientSmoke.tick(MinecraftClient.getInstance());
                });
    }
}
