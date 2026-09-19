package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.client.screen.ConfiguratorRadialScreen;
import net.askcraft.justifylasers.item.LaserConfiguratorItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public final class ConfiguratorControls {
    private static boolean wasHeld;
    private ConfiguratorControls() { }

    public static boolean selectionHeld(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        var key = InputUtil.fromTranslationKey(ClientSettingsKey.CONFIGURATOR.getBoundKeyTranslationKey());
        if (key.getCode() < 0) return false;
        if (key.getCategory() == InputUtil.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
        if (key.getCategory() == InputUtil.Type.SCANCODE) {
            for (int code = GLFW.GLFW_KEY_SPACE; code <= GLFW.GLFW_KEY_LAST; code++)
                if (GLFW.glfwGetKeyScancode(code) == key.getCode() && InputUtil.isKeyPressed(window, code)) return true;
            return false;
        }
        return InputUtil.isKeyPressed(window, key.getCode());
    }

    public static void tick(MinecraftClient client) {
        boolean held = selectionHeld(client);
        while (ClientSettingsKey.CONFIGURATOR.wasPressed()) { }
        if (held && !wasHeld && client.currentScreen == null && client.player != null && client.player.isAlive() && !client.player.isSpectator()) {
            Hand hand = client.player.getMainHandStack().getItem() instanceof LaserConfiguratorItem ? Hand.MAIN_HAND : Hand.OFF_HAND;
            if (client.player.getStackInHand(hand).getItem() instanceof LaserConfiguratorItem)
                client.setScreen(new ConfiguratorRadialScreen(hand));
        }
        wasHeld = held;
    }
}
