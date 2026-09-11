package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.client.screen.PoweredLaserEmitterScreen;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class LaserClientSmoke {
    private static Preview preview;
    private static int ticks;
    private static int stage;
    private static boolean reloading;

    public static void tick(MinecraftClient client) {
        if (Boolean.getBoolean("justifylasers.smokeDecorations")) {
            PartWorldSmoke.tick(client);
            return;
        }
        if (Boolean.getBoolean("justifylasers.smokeModules")) {
            ModuleClientSmoke.tick(client);
            return;
        }
        if (Boolean.getBoolean("justifylasers.smokeCrystals")) {
            CrystalClientSmoke.tick(client);
            return;
        }
        if (Boolean.getBoolean("justifylasers.smokeWorld")) {
            LaserWorldSmoke.INSTANCE.tick(client);
            return;
        }
        if (client.getOverlay() != null || reloading || ++ticks < 24) return;
        ticks = 0;
        if (preview == null) {
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1100, 760);
            client.options.pauseOnLostFocus = false;
            client.options.getGuiScale().setValue(2);
            client.onResolutionChanged();
            language(client, "en_us");
            return;
        }
        switch (stage++) {
            case 0 -> { capture(client, "en-powered-main"); preview.energy(); }
            case 1 -> { capture(client, "en-energy"); preview.settings(0); }
            case 2 -> { capture(client, "en-mining"); preview.back(); preview.settings(1); }
            case 3 -> { capture(client, "en-damage"); language(client, "ru_ru"); }
            case 4 -> { capture(client, "ru-powered-main"); preview.energy(); }
            case 5 -> {
                capture(client, "ru-energy");
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 854, 480);
                client.onResolutionChanged();
            }
            case 6 -> { capture(client, "ru-energy-compact"); preview.back(); }
            case 7 -> { capture(client, "ru-main-compact"); preview.energy(); preview.settings(1); }
            case 8 -> { capture(client, "ru-damage-compact"); preview.named("security"); }
            case 9 -> { capture(client, "ru-security-compact"); preview.named("redstone"); }
            case 10 -> { capture(client, "ru-redstone-compact"); preview.back(); preview.hoverModules(); }
            case 11 -> { capture(client, "ru-button-hover"); preview.focusModules(); }
            case 12 -> { capture(client, "ru-button-keyboard-focus"); preview.enterModules(); }
            case 13 -> { capture(client, "ru-keyboard-modules"); preview.removeCapabilityModules(); }
            case 14 -> {
                capture(client, "ru-disabled-module-settings");
                preview.checkDisabledSettings();
                preview.named("security");
                preview.handler.setProperty(LaserEmitterBlockEntity.PROPERTY_COUNT, 0);
                preview.init();
            }
            case 15 -> {
                capture(client, "ru-readonly-security");
                preview.checkReadonlySecurity();
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 900);
                client.options.getGuiScale().setValue(3);
                client.onResolutionChanged();
                language(client, "en_us");
            }
            case 16 -> { capture(client, "en-powered-main-large"); preview.energy(); }
            case 17 -> capture(client, "en-energy-large");
            default -> {
                LoggerFactory.getLogger("justifylasers-client-smoke").info("CLIENT_SMOKE_PASSED");
                client.scheduleStop();
            }
        }
    }

    private static void language(MinecraftClient client, String language) {
        reloading = true;
        client.getLanguageManager().setLanguage(language);
        client.options.language = language;
        client.reloadResources().thenRun(() -> client.execute(() -> {
            preview = new Preview();
            client.setScreen(preview);
            reloading = false;
            ticks = 0;
        }));
    }

    private static void capture(MinecraftClient client, String name) {
        preview.checkLayout();
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name + ".png", client.getFramebuffer(),
                text -> LoggerFactory.getLogger("justifylasers-client-smoke").info(text.getString()));
    }

    private static final class Preview extends Screen {
        private final PoweredLaserEmitterScreen menu;
        private final LaserEmitterScreenHandler handler;
        private int pointerX;
        private int pointerY;

        private Preview() {
            super(Text.literal("Laser client smoke"));
            PlayerInventory inventory = new PlayerInventory(null);
            handler = new LaserEmitterScreenHandler(1, inventory, BlockPos.ORIGIN, true);
            int[] defaults = {1, 0, 0, 1, 1, 1, 100, 1, 0, 5, 10, 20, 1, 65, 100, 1, 1, 1,
                    1, 1, 33920, 15, 33920, 30, 2614, 0, 255, 1, 0, 0};
            for (int i = 0; i < defaults.length; i++) handler.setProperty(i, defaults[i]);
            handler.getSlot(0).setStack(new ItemStack(ModLaserParts.CRYSTALS.get(LaserColor.RED)));
            for (LaserModule module : LaserModule.values()) handler.getSlot(module.slot()).setStack(new ItemStack(ModLaserParts.MODULES.get(module)));
            handler.getSlot(LaserModule.RANGE.slot()).setStack(new ItemStack(ModLaserParts.ADVANCED_RANGE_MODULE, 8));
            handler.getSlot(LaserModule.THICKNESS.slot()).setStack(new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS), 32));
            String owner = "MrAskick";
            for (int index = 0; index < owner.length(); index++) handler.setProperty(30 + index, owner.charAt(index));
            handler.setProperty(LaserEmitterBlockEntity.PROPERTY_COUNT, 1);
            menu = new PoweredLaserEmitterScreen(handler, inventory, Text.translatable("block.justifylasers.powered_laser_emitter"));
        }

        @Override
        protected void init() { menu.init(client, width, height); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) { menu.render(context, pointerX, pointerY, delta); }

        private List<ClickableWidget> controls() {
            return menu.children().stream().filter(ClickableWidget.class::isInstance).map(ClickableWidget.class::cast)
                    .filter(widget -> widget.visible).toList();
        }

        private void energy() {
            named("modules");
            if (handler.slots.stream().filter(slot -> slot.isEnabled()).count() != 45) throw new AssertionError("Missing module/player inventory slots");
        }

        private void named(String name) {
            click(namedButton(name));
        }

        private ClickableWidget namedButton(String name) {
            return controls().stream().filter(widget -> widget.getMessage().getString().equals(
                    Text.translatable("gui.justifylasers.powered." + name).getString())).findFirst().orElseThrow();
        }

        private void hoverModules() {
            var button = namedButton("modules");
            pointerX = button.getX() + button.getWidth() / 2;
            pointerY = button.getY() + button.getHeight() / 2;
        }

        private void focusModules() {
            pointerX = pointerY = 0;
            menu.setFocused(namedButton("modules"));
        }

        private void enterModules() {
            if (!menu.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0)) throw new AssertionError("Keyboard activation was not handled");
            if (handler.slots.stream().filter(slot -> slot.isEnabled()).count() != 45)
                throw new AssertionError("Keyboard activation did not open the module page");
        }

        private void removeCapabilityModules() {
            handler.setProperty(26, 255 & ~(1 << LaserModule.BLOCK_DESTRUCTION.ordinal()) & ~(1 << LaserModule.ENTITY_DAMAGE.ordinal()));
            handler.getSlot(LaserModule.BLOCK_DESTRUCTION.slot()).setStack(ItemStack.EMPTY);
            handler.getSlot(LaserModule.ENTITY_DAMAGE.slot()).setStack(ItemStack.EMPTY);
            init();
        }

        private void checkDisabledSettings() {
            var buttons = controls().stream().filter(widget -> widget.getMessage().getString().isEmpty()).toList();
            if (buttons.size() != 2) throw new AssertionError("Missing disabled settings buttons");
            buttons.forEach(this::checkDisabled);
        }

        private void checkReadonlySecurity() {
            var buttons = controls().stream().filter(widget -> !widget.active).toList();
            if (buttons.size() != 1) throw new AssertionError("Non-owner security control must be disabled");
            checkDisabled(buttons.get(0));
        }

        private void checkDisabled(ClickableWidget widget) {
            if (widget.active || widget.mouseClicked(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, 0))
                throw new AssertionError("Disabled button accepts clicks");
            if (widget.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0)) throw new AssertionError("Disabled button accepts keyboard activation");
        }

        private void settings(int index) {
            var buttons = controls().stream().filter(ButtonWidget.class::isInstance)
                    .filter(widget -> widget.getMessage().getString().isEmpty())
                    .sorted(java.util.Comparator.comparingInt(ClickableWidget::getX)).toList();
            if (buttons.size() != 2) throw new AssertionError("Missing settings buttons");
            click(buttons.get(index));
        }

        private void click(ClickableWidget widget) {
            if (!widget.mouseClicked(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, 0))
                throw new AssertionError("Button did not accept a click inside its visible bounds");
        }

        private void back() { menu.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); }

        private void checkLayout() {
            var controls = controls();
            for (var widget : controls) {
                if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > width
                        || widget.getY() + widget.getHeight() > height) throw new AssertionError("Widget outside screen");
            }
            long playerSlots = handler.slots.stream().filter(slot -> slot.id >= 9 && slot.isEnabled()).count();
            if (playerSlots != 36) throw new AssertionError("Player inventory must stay visible on every page");
            var slots = handler.slots.stream().filter(slot -> slot.id >= 9).toList();
            for (var slot : slots) {
                if (slot.x - 2 < 40 || slot.x + 18 > 280 || slot.y - 2 < 132 || slot.y + 18 > 217)
                    throw new AssertionError("Inventory slot crosses the inner GUI frame: " + slot.id);
            }
            for (int a = 0; a < slots.size(); a++) for (int b = a + 1; b < slots.size(); b++) {
                var first = slots.get(a); var second = slots.get(b);
                if (Math.abs(first.x - second.x) < 20 && Math.abs(first.y - second.y) < 20)
                    throw new AssertionError("Overlapping inventory slot frames");
            }
            for (int a = 0; a < controls.size(); a++) for (int b = a + 1; b < controls.size(); b++) {
                var first = controls.get(a); var second = controls.get(b);
                if (first.getX() < second.getX() + second.getWidth() && first.getX() + first.getWidth() > second.getX()
                        && first.getY() < second.getY() + second.getHeight() && first.getY() + first.getHeight() > second.getY())
                    throw new AssertionError("Overlapping controls");
            }
        }
    }
}
