package net.askcraft.justifylasers.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.client.render.LaserEmitterPreviewRenderer;
import net.askcraft.justifylasers.client.screen.TechGui.Icon;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.laser.LaserDamage;
import net.askcraft.justifylasers.laser.LaserMining;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class PoweredLaserEmitterScreen extends HandledScreen<LaserEmitterScreenHandler> {
    private static final Identifier BACKGROUND = JustifyLasers.id("textures/gui/powered_emitter.png");
    private static final int TEXT = 0xFFD6EDF4;
    private static final int MUTED = 0xFF7EA7B6;
    private static final int ACCENT = 0xFF5DDFEC;
    private static final String[] SLOT_NAMES = {"crystal", "silk", "drops", "scorch", "ignite", "mining", "damage", "range", "thickness"};
    private final List<TechButton> buttons = new ArrayList<>();
    private final List<SettingSlider> sliders = new ArrayList<>();
    private Page page = Page.MAIN;
    private boolean rotating;
    private float previewYaw = -30;

    private enum Page { MAIN, MODULES, MINING, DAMAGE, SECURITY, REDSTONE }

    public PoweredLaserEmitterScreen(LaserEmitterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 320;
        backgroundHeight = 234;
    }

    @Override
    protected void init() {
        sliders.forEach(SettingSlider::submit);
        buttons.clear();
        sliders.clear();
        super.init();
        handler.setInventoryVisible(page == Page.MODULES);
        button(8, 31, 41, 28, page == Page.MAIN ? Icon.MODULES : Icon.BACK,
                () -> label(page == Page.MAIN ? "modules" : "back"), () -> true,
                () -> open(page == Page.MAIN ? Page.MODULES : page == Page.MINING || page == Page.DAMAGE ? Page.MODULES : Page.MAIN));
        button(8, 64, 41, 28, Icon.LIGHT, () -> label("block_light"), () -> true,
                () -> send(LaserEmitterScreenHandler.BUTTON_MINECRAFT_LIGHTING))
                .indicator = handler::isMinecraftLightingEnabled;
        buttons.get(buttons.size() - 1).setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.minecraft_lighting.tooltip")));
        button(8, 97, 41, 28, Icon.GLOW, () -> label("shader_glow"), () -> true,
                () -> send(LaserEmitterScreenHandler.BUTTON_LIGHT_EMISSION)).indicator = handler::isLightEmissionEnabled;
        buttons.get(buttons.size() - 1).setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.light_emission.tooltip")));
        button(272, 31, 40, 44, Icon.SHIELD, () -> label("security"), () -> true, () -> open(Page.SECURITY))
                .selected = () -> page == Page.SECURITY;
        button(272, 81, 40, 44, Icon.REDSTONE, () -> label("redstone"), () -> true, () -> open(Page.REDSTONE))
                .selected = () -> page == Page.REDSTONE;

        switch (page) {
            case MAIN -> button(65, 108, 78, 14, Icon.POWER, () -> toggle("switch", handler.isEnabled()), () -> true,
                    () -> send(LaserEmitterScreenHandler.BUTTON_ENABLED)).selected = handler::isEnabled;
            case MODULES -> {
                moduleSettings(178, LaserModule.BLOCK_DESTRUCTION, Page.MINING);
                moduleSettings(232, LaserModule.ENTITY_DAMAGE, Page.DAMAGE);
            }
            case MINING -> {
                slider(64, 49, 126, 0, LaserMining.MAX_SPEED_STEP, handler::getMiningSpeedStep,
                        LaserEmitterScreenHandler.MINING_SPEED_BUTTON_BASE, step -> Text.translatable("gui.justifylasers.mining_speed",
                                step == 0 ? label("minimum") : step == LaserMining.MAX_SPEED_STEP ? label("maximum") : Text.literal(step + "%")), "mining_speed");
                button(198, 49, 56, 16, null, () -> toggle("mining", handler.breaksBlocks()), () -> true,
                        () -> send(LaserEmitterScreenHandler.BUTTON_BREAK_BLOCKS));
                moduleToggle(64, 70, 60, "silk", LaserModule.SILK_TOUCH, handler::hasSilkTouch, LaserEmitterScreenHandler.BUTTON_SILK_TOUCH, "silk_touch");
                moduleToggle(129, 70, 60, "drops", LaserModule.BLOCK_DROPS, handler::dropsBlocks, LaserEmitterScreenHandler.BUTTON_DROP_BLOCKS, "drop_blocks");
                moduleToggle(194, 70, 60, "scorch", LaserModule.SCORCH_MARKS, handler::showsScorchMarks, LaserEmitterScreenHandler.BUTTON_SCORCH_MARKS, "scorch_marks");
                button(64, 96, 76, 16, Icon.RESET, () -> label("reset"), () -> true, () -> {
                    sliders.forEach(SettingSlider::submit);
                    send(LaserEmitterScreenHandler.BUTTON_RESET_MINING_SETTINGS);
                    sliders.forEach(SettingSlider::forgetPending);
                });
            }
            case DAMAGE -> {
                slider(64, 49, 126, LaserDamage.MIN_DAMAGE_STEP, LaserDamage.MAX_DAMAGE_STEP, handler::getDamageStep,
                        LaserEmitterScreenHandler.DAMAGE_BUTTON_BASE,
                        step -> Text.translatable("gui.justifylasers.damage_per_hit", decimal(LaserDamage.damageForStep(step))), "damage_per_hit");
                slider(64, 70, 126, 0, LaserDamage.MAX_KNOCKBACK_STEP, handler::getKnockbackStep,
                        LaserEmitterScreenHandler.KNOCKBACK_BUTTON_BASE,
                        step -> Text.translatable("gui.justifylasers.knockback", decimal(LaserDamage.knockbackForStep(step)) + "×"), "knockback");
                slider(64, 91, 126, 1, LaserDamage.MAX_HITS_PER_SECOND, handler::getHitsPerSecond,
                        LaserEmitterScreenHandler.HIT_RATE_BUTTON_BASE,
                        step -> Text.translatable("gui.justifylasers.hits_per_second", step), "hits_per_second");
                button(198, 49, 56, 16, null, () -> toggle("damage", handler.damagesEntities()), () -> true,
                        () -> send(LaserEmitterScreenHandler.BUTTON_DAMAGE_ENTITIES));
                moduleToggle(198, 70, 56, "ignite", LaserModule.IGNITION, handler::ignitesEntities,
                        LaserEmitterScreenHandler.BUTTON_IGNITE_ENTITIES, "ignite_entities");
                button(198, 91, 56, 16, Icon.RESET, () -> label("reset"), () -> true, () -> {
                    sliders.forEach(SettingSlider::submit);
                    send(LaserEmitterScreenHandler.BUTTON_RESET_DAMAGE_SETTINGS);
                    sliders.forEach(SettingSlider::forgetPending);
                });
            }
            case SECURITY -> button(65, 73, 189, 18, Icon.SHIELD,
                    () -> Text.translatable("gui.justifylasers.powered.access", label(handler.isPrivate() ? "private" : "public")),
                    handler::canManageSecurity, () -> send(LaserEmitterScreenHandler.BUTTON_SECURITY));
            case REDSTONE -> button(65, 54, 189, 20, Icon.REDSTONE,
                    () -> Text.translatable(handler.getRedstoneMode().translationKey()), () -> true,
                    () -> send(LaserEmitterScreenHandler.BUTTON_REDSTONE));
        }
        updateControls();
    }

    private void moduleSettings(int left, LaserModule module, Page target) {
        TechButton button = button(left, 95, 18, 18, Icon.SETTINGS, Text::empty, () -> handler.hasModule(module), () -> open(target));
        button.setTooltip(Tooltip.of(Text.translatable(target == Page.MINING ? "gui.justifylasers.mining_settings" : "gui.justifylasers.damage_settings")));
    }

    private void moduleToggle(int left, int top, int width, String text, LaserModule module, BooleanSupplier state, int id, String tooltip) {
        TechButton button = button(left, top, width, 16, null, () -> toggle(text, state.getAsBoolean()), () -> handler.hasModule(module), () -> send(id));
        button.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers." + tooltip + ".tooltip")));
    }

    private TechButton button(int left, int top, int width, int height, Icon icon, Supplier<Text> text, BooleanSupplier available, Runnable action) {
        TechButton button = new TechButton(x + left, y + top, width, height, icon, text, available, action);
        buttons.add(addDrawableChild(button));
        return button;
    }

    private void slider(int left, int top, int width, int min, int max, IntSupplier value, int base, IntFunction<Text> label, String tooltip) {
        SettingSlider slider = new SettingSlider(x + left, y + top, width, min, max, value, base, label);
        slider.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers." + tooltip + ".tooltip")));
        sliders.add(addDrawableChild(slider));
    }

    private void open(Page target) {
        sliders.forEach(SettingSlider::submit);
        page = target;
        rotating = false;
        setDragging(false);
        setFocused(null);
        clearAndInit();
    }

    private void send(int id) {
        if (client != null && client.interactionManager != null) ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId, id));
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        if (page == Page.MINING && !handler.hasModule(LaserModule.BLOCK_DESTRUCTION)
                || page == Page.DAMAGE && !handler.hasModule(LaserModule.ENTITY_DAMAGE)) {
            open(Page.MODULES);
        }
        sliders.forEach(SettingSlider::tick);
        updateControls();
    }

    private void updateControls() {
        for (TechButton button : buttons) {
            button.setMessage(button.label.get());
            button.active = button.available.getAsBoolean();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!RenderVersion.SCREEN_RENDERS_BACKGROUND) renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        if (page == Page.MODULES) {
            for (int index = 0; index < SLOT_NAMES.length; index++) {
                var slot = handler.getSlot(index);
                if (inside(mouseX, mouseY, slot.x - 2, slot.y - 2, 20, 20) && !slot.hasStack()) {
                    Text hint = Text.translatable("gui.justifylasers.powered.slot_hint." + SLOT_NAMES[index], Platform.ENERGY_UNIT);
                    context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(hint, 220), mouseX, mouseY);
                }
            }
        }
        if (page == Page.MAIN && inside(mouseX, mouseY, 152, 68, 104, 25)) {
            context.drawTooltip(textRenderer, Text.translatable("gui.justifylasers.energy_amount",
                    handler.getEnergy(), handler.getEnergyCapacity(), Platform.ENERGY_UNIT), mouseX, mouseY);
        }
        if (page == Page.MAIN && inside(mouseX, mouseY, 63, 44, 84, 59)) {
            context.drawTooltip(textRenderer, Text.translatable("gui.justifylasers.powered.preview_hint",
                    handler.getBeamRange(), decimal(handler.getBeamWidthScale())), mouseX, mouseY);
        }
    }

    // Screen.render invokes the four-argument hook in 1.21; 1.20 draws it explicitly.
    public void renderBackground(DrawContext context) {
        context.fill(0, 0, width, height, 0x28050810);
    }

    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        drawBackground(context, delta, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            context.drawTexture(BACKGROUND, x, y, backgroundWidth, backgroundHeight, 0, 0, 1, 1, 1, 1);
        } finally {
            RenderSystem.disableBlend();
        }
        panel(context, x + 56, y + 31, 208, 94, false);
        for (var slot : handler.slots) {
            if (slot.isEnabled()) slot(context, x + slot.x, y + slot.y, slot.id < SLOT_NAMES.length && slot.hasStack());
        }
        if (page == Page.MAIN) {
            context.fill(x + 149, y + 40, x + 150, y + 115, 0xFF185063);
            context.fill(x + 154, y + 82, x + 254, y + 89, 0x70020D15);
            int capacity = handler.getEnergyCapacity();
            int fill = capacity <= 0 ? 0 : (int) (98L * handler.getEnergy() / capacity);
            context.fillGradient(x + 155, y + 83, x + 155 + fill, y + 88, ACCENT, 0xFF168CA9);
            LaserEmitterPreviewRenderer.render(context, handler, x, y, previewYaw);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        text(context, Text.translatable("block.justifylasers.laser_emitter"), 160, 14, 186, TEXT, true, 1);
        if (page == Page.MAIN) {
            text(context, Text.translatable(handler.getColor().translationKey()), 105, 36, 78, 0xFF000000 | handler.getColor().rgb(), true, 0.8F);
            text(context, label("consumption"), 154, 42, 100, MUTED, false, 0.8F);
            text(context, Text.literal(compact(handler.getEnergyCost()) + " " + Platform.ENERGY_UNIT + "/t"), 154, 53, 100, ACCENT, false, 1);
            text(context, Text.literal(compact(handler.getEnergy()) + " / " + compact(handler.getEnergyCapacity()) + " " + Platform.ENERGY_UNIT), 154, 71, 100, TEXT, false, 0.75F);
            text(context, label("status"), 154, 96, 100, MUTED, false, 0.8F);
            text(context, label("status." + status()), 154, 108, 100, handler.isActive() ? ACCENT : TEXT, false, 0.8F);
            return;
        }
        String titleKey = switch (page) {
            case MODULES -> "modules";
            case MINING -> "mining_settings";
            case DAMAGE -> "damage_settings";
            case SECURITY -> "security";
            case REDSTONE -> "redstone_title";
            default -> "";
        };
        text(context, label(titleKey), 64, 35, 191, ACCENT, false, 0.85F);
        switch (page) {
            case MODULES -> {
                for (int index = 0; index < SLOT_NAMES.length; index++) {
                    var slot = handler.getSlot(index);
                    text(context, label("slot." + SLOT_NAMES[index]), slot.x + 8, slot.y - 10, 40, TEXT, true, 0.7F);
                }
                text(context, Text.translatable("gui.justifylasers.powered.module_stats", handler.getBeamRange(), decimal(handler.getBeamWidthScale())),
                        160, 116, 191, MUTED, true, 0.65F);
            }
            case MINING -> {
                text(context, Text.translatable("gui.justifylasers.powered.stone_time",
                        String.format(Locale.ROOT, "%.2f", LaserMining.ticksToBreak(1.5F, sliders.get(0).step()) / 20.0)), 147, 101, 107, MUTED, false, 0.7F);
                cost(context);
            }
            case DAMAGE -> {
                text(context, Text.literal(decimal(LaserDamage.damageForStep(sliders.get(0).step()) * sliders.get(2).step()) + " HP/s"),
                        64, 115, 80, MUTED, false, 0.7F);
                cost(context);
            }
            case SECURITY -> {
                text(context, label("owner"), 65, 51, 60, MUTED, false, 0.8F);
                text(context, handler.getOwnerName().isEmpty() ? label("unowned") : Text.literal(handler.getOwnerName()), 121, 51, 132, TEXT, false, 0.9F);
                text(context, label(handler.canManageSecurity() ? "security_hint" : "owner_only"), 65, 98, 189, MUTED, false, 0.7F);
                text(context, label("security_scope"), 65, 110, 189, MUTED, false, 0.65F);
            }
            case REDSTONE -> {
                text(context, Text.translatable("gui.justifylasers.powered.signal", label(handler.hasRedstoneSignal() ? "detected" : "absent")),
                        65, 83, 189, handler.hasRedstoneSignal() ? ACCENT : TEXT, false, 0.85F);
                text(context, label("redstone_hint"), 65, 101, 189, MUTED, false, 0.7F);
                text(context, label("redstone_energy"), 65, 113, 189, MUTED, false, 0.65F);
            }
            default -> { }
        }
    }

    private void cost(DrawContext context) {
        text(context, Text.literal(compact(handler.getEnergyCost()) + " " + Platform.ENERGY_UNIT + "/t"), 174, 115, 80, ACCENT, false, 0.7F);
    }

    private String status() {
        if (!handler.isTechnicalMode()) return "disabled";
        if (!handler.isEnabled()) return "off";
        if (!handler.hasCrystal()) return "no_crystal";
        if (!handler.getRedstoneMode().allows(handler.hasRedstoneSignal())) return "redstone";
        return handler.isActive() ? "active" : handler.getEnergy() < handler.getEnergyCost() ? "no_power" : "ready";
    }

    private static Text label(String key) {
        return Text.translatable("gui.justifylasers.powered." + key);
    }

    private static Text toggle(String key, boolean enabled) {
        return Text.translatable("gui.justifylasers.powered.toggle", label(key), Text.translatable("gui.justifylasers." + (enabled ? "on" : "off")));
    }

    private static String compact(int number) {
        if (number >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", number / 1_000_000.0);
        if (number >= 10_000) return String.format(Locale.ROOT, "%.1fk", number / 1000.0);
        return Integer.toString(number);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void text(DrawContext context, Text text, int left, int top, int maxWidth, int color, boolean centered, float maxScale) {
        float scale = Math.min(maxScale, maxWidth / (float) Math.max(1, textRenderer.getWidth(text)));
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(left, top, 0);
        matrices.scale(scale, scale, 1);
        context.drawText(textRenderer, text, centered ? -textRenderer.getWidth(text) / 2 : 0, 0, color, false);
        matrices.pop();
    }

    private static void panel(DrawContext context, int left, int top, int width, int height, boolean selected) {
        int border = selected ? ACCENT : 0xFF25718A;
        context.fill(left + 2, top, left + width - 2, top + 2, border);
        context.fill(left + 2, top + height - 2, left + width - 2, top + height, border);
        context.fill(left, top + 2, left + 1, top + height - 2, border);
        context.fill(left + width - 1, top + 2, left + width, top + height - 2, border);
        context.fillGradient(left + 1, top + 2, left + width - 1, top + height - 2, selected ? 0x60124B62 : 0x38092C3C, 0x38041723);
        context.fill(left + 3, top + 1, left + width - 3, top + 2, selected ? 0xFFCBF9FF : 0xFF3A90A5);
    }

    private static void slot(DrawContext context, int left, int top, boolean installed) {
        panel(context, left - 2, top - 2, 20, 20, installed);
        context.fill(left, top, left + 16, top + 16, 0x18092536);
        context.fill(left - 2, top - 2, left + 3, top - 1, 0xFF8EE7F5);
        context.fill(left + 14, top + 17, left + 18, top + 18, 0xFF8EE7F5);
    }

    private boolean inside(double mouseX, double mouseY, int left, int top, int width, int height) {
        return mouseX >= x + left && mouseX < x + left + width && mouseY >= y + top && mouseY < y + top + height;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && page != Page.MAIN) {
            open(page == Page.MINING || page == Page.DAMAGE ? Page.MODULES : Page.MAIN);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && page == Page.MAIN && inside(mouseX, mouseY, 61, 44, 86, 59)) {
            rotating = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (rotating && button == 0) {
            previewYaw = MathHelper.clamp(previewYaw + (float) dx, -65, 30);
            return true;
        }
        if (button == 0 && getFocused() instanceof SettingSlider slider && slider.dragging) return slider.mouseDragged(mouseX, mouseY, button, dx, dy);
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (rotating) { rotating = false; return true; }
        // HandledScreen routes releases to slots, so sliders need their own release path.
        if (button == 0 && getFocused() instanceof SettingSlider slider && slider.dragging) {
            slider.onRelease(mouseX, mouseY);
            setDragging(false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        sliders.forEach(SettingSlider::submit);
        super.removed();
    }

    private final class TechButton extends ButtonWidget {
        private final Icon icon;
        private final Supplier<Text> label;
        private final BooleanSupplier available;
        private BooleanSupplier indicator;
        private BooleanSupplier selected = () -> false;
        private long pressedUntil;

        TechButton(int x, int y, int width, int height, Icon icon, Supplier<Text> label, BooleanSupplier available, Runnable action) {
            super(x, y, width, height, label.get(), button -> action.run(), DEFAULT_NARRATION_SUPPLIER);
            this.icon = icon;
            this.label = label;
            this.available = available;
        }

        @Override
        public void onPress() {
            pressedUntil = Util.getMeasuringTimeMs() + 120;
            super.onPress();
        }

        // 1.20 names this hook renderButton; 1.21 names it renderWidget.
        public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            renderWidget(context, mouseX, mouseY, delta);
        }

        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean pressed = Util.getMeasuringTimeMs() < pressedUntil;
            TechGui.State state = TechGui.State.of(active, pressed, isHovered(), isFocused(), selected.getAsBoolean());
            TechGui.button(context, getX(), getY(), getWidth(), getHeight(), state);
            int color = active ? TEXT : 0xFF6D8A98;
            boolean stacked = getHeight() >= 28;
            boolean tall = getHeight() >= 40;
            String[] lines = getMessage().getString().split("\n");
            boolean multiline = lines.length > 1;
            int shift = pressed && active && !stacked ? 1 : 0;
            int iconSize = stacked ? tall ? 20 : multiline ? 10 : 14 : Math.min(11, getHeight() - 6);
            int textTop = stacked ? tall ? multiline ? 26 : 29 : multiline ? 13 : 17 : (getHeight() - 6) / 2;
            float textScale = stacked ? !tall && multiline ? 0.6F : 0.7F : 0.75F;
            boolean iconOnly = getMessage().getString().isEmpty();
            if (icon != null) icon.draw(context, getX() + (stacked || iconOnly ? (getWidth() - iconSize) / 2.0F : 6),
                    getY() + (stacked ? tall ? 5 : 3 : (getHeight() - iconSize) / 2.0F) + shift, iconSize, state);
            for (int index = 0; index < lines.length; index++) {
                int offset = !stacked && icon != null && !iconOnly ? (iconSize + 5) / 2 : 0;
                text(context, Text.literal(lines[index]), getX() + getWidth() / 2 + offset,
                        getY() + textTop + index * (tall ? 7 : 6) + shift,
                        getWidth() - (stacked ? tall ? 10 : 12 : 8) - offset * 2, color, true, textScale);
            }
            if (indicator != null && stacked) TechGui.indicator(context, getX() + getWidth() - 11, getY() + 8, indicator.getAsBoolean(), active);
        }
    }

    private final class SettingSlider extends SliderWidget {
        private final int min;
        private final int max;
        private final IntSupplier serverValue;
        private final int base;
        private final IntFunction<Text> label;
        private int lastSent;
        private int pending = -1;
        private int acknowledgementTicks;
        private int countdown;
        private boolean dragging;

        SettingSlider(int x, int y, int width, int min, int max, IntSupplier value, int base, IntFunction<Text> label) {
            super(x, y, width, 16, Text.empty(), (MathHelper.clamp(value.getAsInt(), min, max) - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.serverValue = value;
            this.base = base;
            this.label = label;
            lastSent = step();
            updateMessage();
        }

        int step() { return MathHelper.clamp(min + (int) Math.round(value * (max - min)), min, max); }
        @Override protected void updateMessage() { if (label != null) setMessage(label.apply(step())); }
        @Override protected void applyValue() { updateMessage(); countdown = 2; }
        @Override public void onClick(double x, double y) { dragging = true; super.onClick(x, y); }
        @Override public void onRelease(double x, double y) { super.onRelease(x, y); dragging = false; submit(); }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
                value = (MathHelper.clamp(step() + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1), min, max) - min) / (double) (max - min);
                updateMessage();
                submit();
                return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }

        void submit() {
            countdown = 0;
            if (step() == lastSent) return;
            pending = lastSent = step();
            acknowledgementTicks = 40;
            send(base + step());
        }

        void forgetPending() { pending = -1; dragging = false; }

        void tick() {
            if (countdown > 0 && --countdown == 0) submit();
            if (dragging || countdown > 0) return;
            int current = MathHelper.clamp(serverValue.getAsInt(), min, max);
            if (pending >= 0 && current != pending && --acknowledgementTicks > 0) return;
            pending = -1;
            lastSent = current;
            value = (current - min) / (double) (max - min);
            updateMessage();
        }

        public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            renderWidget(context, mouseX, mouseY, delta);
        }

        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            TechGui.slider(context, getX(), getY(), getWidth(), getHeight(), value,
                    TechGui.State.of(active, dragging, isHovered(), isFocused(), false));
            text(context, getMessage(), getX() + getWidth() / 2, getY() + 3, getWidth() - 12,
                    active ? TEXT : MUTED, true, 0.75F);
        }
    }
}
