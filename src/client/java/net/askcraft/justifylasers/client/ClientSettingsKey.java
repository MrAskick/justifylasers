package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.client.screen.ClientSettingsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class ClientSettingsKey {
    public static final KeyBinding SABER_TOGGLE = new KeyBinding("key.justifylasers.saber_toggle",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.justifylasers");
    public static final KeyBinding OPEN = new KeyBinding("key.justifylasers.client_settings",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.justifylasers");

    public static void tick(MinecraftClient client) {
        while (SABER_TOGGLE.wasPressed()) if (client.currentScreen == null && SaberControls.held(client))
            net.askcraft.justifylasers.platform.ClientPlatform.sendSaberToggle(new net.askcraft.justifylasers.network.SaberTogglePacket());
        while (OPEN.wasPressed()) {
            if (client.currentScreen == null && client.player != null) client.setScreen(new ClientSettingsScreen(null));
        }
    }

    private ClientSettingsKey() { }
}
