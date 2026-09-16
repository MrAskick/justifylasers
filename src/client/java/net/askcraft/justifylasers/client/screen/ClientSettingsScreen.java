package net.askcraft.justifylasers.client.screen;

import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public final class ClientSettingsScreen extends Screen {
    private final Screen parent;
    private int tab, left, top, panelWidth, rowWidth;
    private boolean saveFailed;

    public ClientSettingsScreen(Screen parent) {
        super(Text.translatable("gui.justifylasers.client.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(420, width - 20);
        left = (width - panelWidth) / 2;
        top = Math.max(4, (height - 244) / 2);
        rowWidth = panelWidth - 24;
        String[] tabs = {"optics", "effects", "audio"};
        for (int i = 0; i < tabs.length; i++) {
            int selected = i;
            addDrawableChild(new SettingButton(left + 12 + i * (rowWidth / 3), top + 31, rowWidth / 3 - 2,
                    label(tabs[i]), b -> { tab = selected; rebuild(); }, () -> tab == selected));
        }
        ClientSettings settings = ClientSettings.get();
        if (tab == 0) {
            toggle(0, "cube_lenses", () -> settings.cubeLenses, value -> settings.cubeLenses = value);
            slider(1, "magnification", settings.cubeMagnification, 1, 3, 0.1, value -> settings.cubeMagnification = value);
            toggle(2, "scope_lens", () -> settings.scopeLens, value -> settings.scopeLens = value);
            slider(3, "lens_distance", settings.cubeLensDistance, 8, 128, 8, value -> settings.cubeLensDistance = (int) value);
            slider(4, "lens_limit", settings.maxLensCubes, 1, 64, 1, value -> settings.maxLensCubes = (int) value);
        } else if (tab == 1) {
            toggle(0, "scorch_marks", () -> settings.scorchMarks, value -> settings.scorchMarks = value);
            slider(1, "scorch_distance", settings.scorchDistance, 16, 128, 8, value -> settings.scorchDistance = (int) value);
            toggle(2, "weapon_sway", () -> settings.weaponSway, value -> settings.weaponSway = value);
            toggle(3, "saber_sparks", () -> settings.saberSparks, value -> settings.saberSparks = value);
            toggle(4, "solar_shafts", () -> settings.solarLightShafts, value -> settings.solarLightShafts = value);
        } else {
            slider(0, "sound_volume", settings.soundVolume * 100, 0, 100, 5, value -> settings.soundVolume = value / 100);
            slider(1, "sound_limit", settings.maxSoundSources, 1, 64, 1, value -> settings.maxSoundSources = (int) value);
        }
        addDrawableChild(new SettingButton(left + 12, top + 210, rowWidth / 2 - 3,
                label("reset"), b -> { ClientSettings.reset(); rebuild(); }, () -> false));
        addDrawableChild(new SettingButton(left + 15 + rowWidth / 2, top + 210, rowWidth / 2 - 3,
                Text.translatable("gui.done"), b -> close(), () -> false));
    }

    private void rebuild() { clearChildren(); init(); }
    private static Text label(String name) { return Text.translatable("gui.justifylasers.client." + name); }

    private void toggle(int row, String name, BooleanSupplier value, Consumer<Boolean> change) {
        var button = new SettingButton(left + 12, top + 61 + row * 26, rowWidth, toggleText(name, value.getAsBoolean()), b -> {
            change.accept(!value.getAsBoolean());
            b.setMessage(toggleText(name, value.getAsBoolean()));
        }, value);
        button.setTooltip(Tooltip.of(label(name + ".tooltip")));
        addDrawableChild(button);
    }

    private static Text toggleText(String name, boolean enabled) {
        return label(name).copy().append(": ").append(Text.translatable(enabled ? "options.on" : "options.off"));
    }

    private void slider(int row, String name, double initial, double min, double max, double step, DoubleConsumer change) {
        var slider = new SliderWidget(left + 12, top + 61 + row * 26, rowWidth, 20, Text.empty(), (initial - min) / (max - min)) {
            private double selected() { return Math.max(min, Math.min(max, min + Math.round(value * (max - min) / step) * step)); }
            @Override protected void updateMessage() {
                setMessage(label(name).copy().append(": " + (step < 1 ? String.format(Locale.ROOT, "%.1f×", selected()) : Integer.toString((int) selected()))));
            }
            @Override protected void applyValue() { change.accept(selected()); updateMessage(); }
            public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
                renderWidget(context, mouseX, mouseY, delta);
            }
            public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                TechGui.slider(context, getX(), getY(), getWidth(), getHeight(), value,
                        TechGui.State.of(active, false, isHovered(), isFocused(), false));
                context.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + getWidth() / 2, getY() + 5, 0xE7F4FC);
            }
            { updateMessage(); }
        };
        slider.setTooltip(Tooltip.of(label(name + ".tooltip")));
        addDrawableChild(slider);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!RenderVersion.SCREEN_RENDERS_BACKGROUND) renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    // In 1.21 Screen.render draws this before widgets; drawing the panel earlier would blur it again.
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x78050810);
        context.fill(left, top, left + panelWidth, top + 240, 0xE9161C24);
        context.fill(left, top, left + panelWidth, top + 2, 0xFF52C9DF);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 12, 0xE7F4FC);
        context.drawCenteredTextWithShadow(textRenderer, label(saveFailed ? "save_failed" : "local_only"),
                width / 2, top + 196, saveFailed ? 0xFF7878 : 0x91A2B0);
    }

    @Override public void close() {
        if (ClientSettings.save() || saveFailed) client.setScreen(parent);
        else saveFailed = true;
    }

    @Override public boolean shouldPause() { return false; }

    private final class SettingButton extends ButtonWidget {
        private final BooleanSupplier selected;
        private long pressedUntil;

        SettingButton(int x, int y, int width, Text label, PressAction action, BooleanSupplier selected) {
            super(x, y, width, 20, label, action, DEFAULT_NARRATION_SUPPLIER);
            this.selected = selected;
        }

        @Override public void onPress() {
            pressedUntil = Util.getMeasuringTimeMs() + 120;
            super.onPress();
        }

        // The widget render hook was renamed in 1.21.
        public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            renderWidget(context, mouseX, mouseY, delta);
        }

        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean pressed = Util.getMeasuringTimeMs() < pressedUntil;
            TechGui.button(context, getX(), getY(), getWidth(), getHeight(),
                    TechGui.State.of(active, pressed, isHovered(), isFocused(), selected.getAsBoolean()));
            context.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + getWidth() / 2,
                    getY() + 6 + (pressed ? 1 : 0), active ? 0xE7F4FC : 0x6D8A98);
        }
    }
}
