package net.askcraft.justifylasers.client.screen;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.laser.LaserColor;
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
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public class LaserEmitterScreen extends HandledScreen<LaserEmitterScreenHandler> {
    private ButtonWidget enabledButton;
    private ButtonWidget redstoneButton;
    private ButtonWidget colorButton;
    private SettingSlider thicknessSlider;
    private SettingSlider rangeSlider;
    private SettingSlider damageSlider;
    private SettingSlider knockbackSlider;
    private SettingSlider hitRateSlider;
    private SettingSlider miningSpeedSlider;
    private ButtonWidget lightEmissionButton;
    private ButtonWidget minecraftLightingButton;
    private ButtonWidget breakBlocksButton;
    private ButtonWidget damageEntitiesButton;
    private ButtonWidget igniteEntitiesButton;
    private ButtonWidget silkTouchButton;
    private ButtonWidget dropBlocksButton;
    private ButtonWidget scorchMarksButton;
    private ButtonWidget energySettingsButton;
    private List<ClickableWidget> mainControls = List.of();
    private List<ClickableWidget> damageControls = List.of();
    private List<ClickableWidget> miningControls = List.of();
    private List<ClickableWidget> energyControls = List.of();
    private List<SettingSlider> sliders = List.of();
    private Page page = Page.MAIN;
    private boolean poweredLayout;

    private enum Page {
        MAIN, DAMAGE, MINING, ENERGY
    }

    public LaserEmitterScreen(LaserEmitterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 276;
        backgroundHeight = 308;
        titleX = 18;
        titleY = 13;
    }

    @Override
    protected void init() {
        sliders.forEach(SettingSlider::submitValue);
        int widthStep = thicknessSlider == null ? handler.getBeamWidthStep() : thicknessSlider.getStep();
        int range = rangeSlider == null ? handler.getBeamRange() : rangeSlider.getStep();
        int damageStep = damageSlider == null ? handler.getDamageStep() : damageSlider.getStep();
        int knockbackStep = knockbackSlider == null ? handler.getKnockbackStep() : knockbackSlider.getStep();
        int hitRate = hitRateSlider == null ? handler.getHitsPerSecond() : hitRateSlider.getStep();
        int miningSpeed = miningSpeedSlider == null ? handler.getMiningSpeedStep() : miningSpeedSlider.getStep();
        backgroundHeight = Math.min(308, height - 12);
        super.init();

        int buttonX = x + 22;
        int buttonWidth = backgroundWidth - 44;
        poweredLayout = handler.isPoweredEmitter();
        int rowStep = Math.min(22, (backgroundHeight - (poweredLayout ? 100 : 84)) / 9);
        int buttonHeight = rowStep - 2;
        enabledButton = addDrawableChild(makeButton(buttonX, y + 48, buttonWidth, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_ENABLED));
        redstoneButton = addDrawableChild(makeButton(buttonX, y + 48 + rowStep, buttonWidth, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_REDSTONE));
        colorButton = addDrawableChild(makeButton(buttonX, y + 48 + rowStep * 2, buttonWidth, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_COLOR));
        thicknessSlider = addDrawableChild(new SettingSlider(
                buttonX, y + 48 + rowStep * 3, buttonWidth, buttonHeight,
                0, LaserEmitterBlockEntity.BEAM_WIDTH_STEPS, widthStep,
                handler::getBeamWidthStep, LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_BASE,
                step -> valueLabel("gui.justifylasers.beam_width",
                        String.format(Locale.ROOT, "%.2f×", LaserEmitterBlockEntity.beamWidthScale(step)))
        ));
        rangeSlider = addDrawableChild(new SettingSlider(
                buttonX, y + 48 + rowStep * 4, buttonWidth, buttonHeight,
                LaserEmitterBlockEntity.MIN_RANGE, LaserEmitterBlockEntity.MAX_RANGE, range,
                handler::getBeamRange, LaserEmitterScreenHandler.RANGE_BUTTON_BASE,
                step -> valueLabel("gui.justifylasers.beam_range", Integer.toString(step))
        ));
        rangeSlider.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.beam_range.tooltip")));
        lightEmissionButton = addDrawableChild(makeButton(buttonX, y + 48 + rowStep * 5, buttonWidth, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_LIGHT_EMISSION));
        lightEmissionButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.light_emission.tooltip")));
        minecraftLightingButton = addDrawableChild(makeButton(buttonX, y + 48 + rowStep * 6, buttonWidth, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_MINECRAFT_LIGHTING));
        minecraftLightingButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.minecraft_lighting.tooltip")));
        breakBlocksButton = addDrawableChild(makeButton(buttonX, y + 48 + rowStep * 7, buttonWidth - 24, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_BREAK_BLOCKS));
        ButtonWidget miningSettingsButton = addDrawableChild(ButtonWidget.builder(
                        Text.literal("..."), button -> setPage(Page.MINING))
                .dimensions(buttonX + buttonWidth - 20, y + 48 + rowStep * 7, 20, buttonHeight)
                .tooltip(Tooltip.of(Text.translatable("gui.justifylasers.mining_settings")))
                .build());
        damageEntitiesButton = addDrawableChild(makeButton(buttonX, y + 48 + rowStep * 8, buttonWidth - 24, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_DAMAGE_ENTITIES));
        ButtonWidget damageSettingsButton = addDrawableChild(ButtonWidget.builder(
                        Text.literal("..."), button -> setPage(Page.DAMAGE))
                .dimensions(buttonX + buttonWidth - 20, y + 48 + rowStep * 8, 20, buttonHeight)
                .tooltip(Tooltip.of(Text.translatable("gui.justifylasers.damage_settings")))
                .build());

        ButtonWidget closeButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.justifylasers.close"),
                        button -> close())
                .dimensions(x + backgroundWidth - 72, y + backgroundHeight - 26, 54, 18)
                .build());
        energySettingsButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.justifylasers.energy_settings"),
                        button -> setPage(Page.ENERGY))
                .dimensions(buttonX, y + backgroundHeight - 48, buttonWidth, 18).build());
        ButtonWidget energyBackButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.justifylasers.back"),
                        button -> setPage(Page.MAIN))
                .dimensions(x + backgroundWidth - 72, y + backgroundHeight - 26, 54, 18).build());

        int damageRowStep = backgroundHeight < 270 ? 30 : 36;
        damageSlider = addDrawableChild(new SettingSlider(
                buttonX, y + 48, buttonWidth, 20,
                LaserDamage.MIN_DAMAGE_STEP, LaserDamage.MAX_DAMAGE_STEP, damageStep,
                handler::getDamageStep, LaserEmitterScreenHandler.DAMAGE_BUTTON_BASE,
                step -> valueLabel("gui.justifylasers.damage_per_hit",
                        String.format(Locale.ROOT, "%.1f", LaserDamage.damageForStep(step)))
        ));
        damageSlider.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.damage_per_hit.tooltip")));
        knockbackSlider = addDrawableChild(new SettingSlider(
                buttonX, y + 48 + damageRowStep, buttonWidth, 20,
                0, LaserDamage.MAX_KNOCKBACK_STEP, knockbackStep,
                handler::getKnockbackStep, LaserEmitterScreenHandler.KNOCKBACK_BUTTON_BASE,
                step -> valueLabel("gui.justifylasers.knockback",
                        String.format(Locale.ROOT, "%.1f×", LaserDamage.knockbackForStep(step)))
        ));
        knockbackSlider.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.knockback.tooltip")));
        hitRateSlider = addDrawableChild(new SettingSlider(
                buttonX, y + 48 + damageRowStep * 2, buttonWidth, 20,
                1, LaserDamage.MAX_HITS_PER_SECOND, hitRate,
                handler::getHitsPerSecond, LaserEmitterScreenHandler.HIT_RATE_BUTTON_BASE,
                step -> valueLabel("gui.justifylasers.hits_per_second", Integer.toString(step))
        ));
        hitRateSlider.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.hits_per_second.tooltip")));
        igniteEntitiesButton = addDrawableChild(makeButton(buttonX, y + 48 + damageRowStep * 3, buttonWidth, 20,
                LaserEmitterScreenHandler.BUTTON_IGNITE_ENTITIES));
        igniteEntitiesButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.ignite_entities.tooltip")));
        ButtonWidget resetButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.justifylasers.damage_defaults"), button -> {
                            damageSlider.setStepAndSubmit(LaserDamage.DEFAULT_DAMAGE_STEP);
                            knockbackSlider.setStepAndSubmit(LaserDamage.DEFAULT_KNOCKBACK_STEP);
                            hitRateSlider.setStepAndSubmit(LaserDamage.MAX_HITS_PER_SECOND);
                            sendButton(LaserEmitterScreenHandler.BUTTON_RESET_DAMAGE_SETTINGS);
                        })
                .dimensions(buttonX, y + backgroundHeight - 26, 116, 18)
                .build());
        ButtonWidget backButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.justifylasers.back"), button -> setPage(Page.MAIN))
                .dimensions(x + backgroundWidth - 72, y + backgroundHeight - 26, 54, 18)
                .build());

        miningSpeedSlider = addDrawableChild(new SettingSlider(
                buttonX, y + 48, buttonWidth, 20,
                0, LaserMining.MAX_SPEED_STEP, miningSpeed,
                handler::getMiningSpeedStep, LaserEmitterScreenHandler.MINING_SPEED_BUTTON_BASE,
                step -> Text.translatable("gui.justifylasers.mining_speed", (step == 0
                        ? Text.translatable("gui.justifylasers.mining_speed.min")
                        : step == LaserMining.MAX_SPEED_STEP ? Text.translatable("gui.justifylasers.mining_speed.max")
                        : Text.literal(step + "%")).formatted(Formatting.AQUA))
        ));
        miningSpeedSlider.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.mining_speed.tooltip")));
        silkTouchButton = addDrawableChild(makeButton(buttonX, y + 48 + damageRowStep, buttonWidth, 20,
                LaserEmitterScreenHandler.BUTTON_SILK_TOUCH));
        silkTouchButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.silk_touch.tooltip")));
        dropBlocksButton = addDrawableChild(makeButton(buttonX, y + 48 + damageRowStep * 2, buttonWidth, 20,
                LaserEmitterScreenHandler.BUTTON_DROP_BLOCKS));
        dropBlocksButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.drop_blocks.tooltip")));
        scorchMarksButton = addDrawableChild(makeButton(buttonX, y + 48 + damageRowStep * 3, buttonWidth, 20,
                LaserEmitterScreenHandler.BUTTON_SCORCH_MARKS));
        scorchMarksButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.scorch_marks.tooltip")));
        ButtonWidget miningResetButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.justifylasers.mining_defaults"), button -> {
                            miningSpeedSlider.setStepAndSubmit(LaserMining.DEFAULT_SPEED_STEP);
                            sendButton(LaserEmitterScreenHandler.BUTTON_RESET_MINING_SETTINGS);
                        })
                .dimensions(buttonX, y + backgroundHeight - 26, 116, 18)
                .build());
        ButtonWidget miningBackButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.justifylasers.back"), button -> setPage(Page.MAIN))
                .dimensions(x + backgroundWidth - 72, y + backgroundHeight - 26, 54, 18)
                .build());

        mainControls = List.of(enabledButton, redstoneButton, colorButton, thicknessSlider, rangeSlider,
                lightEmissionButton, minecraftLightingButton, breakBlocksButton, miningSettingsButton,
                damageEntitiesButton, damageSettingsButton, closeButton, energySettingsButton);
        damageControls = List.of(damageSlider, knockbackSlider, hitRateSlider, igniteEntitiesButton, resetButton, backButton);
        miningControls = List.of(miningSpeedSlider, silkTouchButton, dropBlocksButton, scorchMarksButton, miningResetButton, miningBackButton);
        energyControls = List.of(energyBackButton);
        sliders = List.of(thicknessSlider, rangeSlider, damageSlider, knockbackSlider, hitRateSlider, miningSpeedSlider);
        setPage(page);

        updateButtonMessages();
    }

    private void setPage(Page page) {
        this.page = page;
        for (SettingSlider slider : sliders) {
            slider.dragging = false;
            slider.submitValue();
        }
        setDragging(false);
        setFocused(null);
        setControlsVisible(mainControls, page == Page.MAIN);
        setControlsVisible(damageControls, page == Page.DAMAGE);
        setControlsVisible(miningControls, page == Page.MINING);
        setControlsVisible(energyControls, page == Page.ENERGY);
        handler.setInventoryVisible(page == Page.ENERGY);
        updateButtonMessages();
    }

    private static void setControlsVisible(List<ClickableWidget> controls, boolean visible) {
        for (ClickableWidget widget : controls) {
            widget.visible = visible;
            widget.active = visible;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && page != Page.MAIN) {
            setPage(Page.MAIN);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && getFocused() instanceof SettingSlider slider && slider.dragging) {
            return slider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // HandledScreen consumes release events for slots instead of forwarding them to widgets.
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && getFocused() instanceof SettingSlider slider && slider.dragging) {
            slider.onRelease(mouseX, mouseY);
            setDragging(false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private ButtonWidget makeButton(int buttonX, int buttonY, int width, int height, int id) {
        return ButtonWidget.builder(Text.empty(), button -> sendButton(id))
                .dimensions(buttonX, buttonY, width, height)
                .build();
    }

    private void sendButton(int id) {
        if (client != null && client.interactionManager != null) {
            client.interactionManager.clickButton(handler.syncId, id);
        }
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        if (poweredLayout != handler.isPoweredEmitter()) {
            clearAndInit();
        }
        sliders.forEach(SettingSlider::tick);
        updateButtonMessages();
    }

    private void updateButtonMessages() {
        if (enabledButton == null) {
            return;
        }

        enabledButton.setMessage(toggleLabel("gui.justifylasers.enabled", handler.isEnabled()));
        redstoneButton.setMessage(Text.translatable(
                "gui.justifylasers.redstone",
                Text.translatable(handler.getRedstoneMode().translationKey())
        ));

        MutableText colorName = Text.translatable(handler.getColor().translationKey());
        MutableText colorDot = Text.literal("● ").styled(style -> style.withColor(handler.getColor().rgb()));
        colorButton.setMessage(Text.translatable("gui.justifylasers.color", colorDot.append(colorName)));

        lightEmissionButton.setMessage(toggleLabel(
                "gui.justifylasers.light_emission",
                handler.isLightEmissionEnabled()
        ));
        minecraftLightingButton.setMessage(toggleLabel(
                "gui.justifylasers.minecraft_lighting",
                handler.isMinecraftLightingEnabled()
        ));
        breakBlocksButton.setMessage(toggleLabel("gui.justifylasers.break_blocks", handler.breaksBlocks()));
        damageEntitiesButton.setMessage(toggleLabel("gui.justifylasers.damage_entities", handler.damagesEntities()));
        igniteEntitiesButton.setMessage(toggleLabel("gui.justifylasers.ignite_entities", handler.ignitesEntities()));
        silkTouchButton.setMessage(toggleLabel("gui.justifylasers.silk_touch", handler.hasSilkTouch()));
        dropBlocksButton.setMessage(toggleLabel("gui.justifylasers.drop_blocks", handler.dropsBlocks()));
        scorchMarksButton.setMessage(toggleLabel("gui.justifylasers.scorch_marks", handler.showsScorchMarks()));
        energySettingsButton.visible = page == Page.MAIN && handler.isPoweredEmitter();
        energySettingsButton.active = energySettingsButton.visible;
        colorButton.active = page == Page.MAIN && !handler.isPoweredEmitter();
        redstoneButton.active = page == Page.MAIN && !handler.isPoweredEmitter();
        if (handler.isPoweredEmitter()) {
            colorButton.setMessage(Text.translatable("gui.justifylasers.crystal_color", colorName));
            redstoneButton.setMessage(Text.translatable("gui.justifylasers.power_source"));
        }
        updateModuleButton(silkTouchButton, Page.MINING, LaserModule.SILK_TOUCH, "silk_touch");
        updateModuleButton(dropBlocksButton, Page.MINING, LaserModule.BLOCK_DROPS, "drop_blocks");
        updateModuleButton(scorchMarksButton, Page.MINING, LaserModule.SCORCH_MARKS, "scorch_marks");
        updateModuleButton(igniteEntitiesButton, Page.DAMAGE, LaserModule.IGNITION, "ignite_entities");
    }

    private void updateModuleButton(ButtonWidget button, Page targetPage, LaserModule module, String setting) {
        boolean available = handler.hasModule(module);
        button.active = page == targetPage && available;
        button.setTooltip(Tooltip.of(available
                ? Text.translatable("gui.justifylasers." + setting + ".tooltip")
                : Text.translatable("gui.justifylasers.module_required",
                        Text.translatable("item.justifylasers." + module.id()))));
    }

    private Text toggleLabel(String key, boolean value) {
        Text state = Text.translatable(value ? "gui.justifylasers.on" : "gui.justifylasers.off")
                .formatted(value ? Formatting.GREEN : Formatting.RED);
        return Text.translatable(key, state);
    }

    private static Text valueLabel(String key, String value) {
        return Text.translatable(key, Text.literal(value).formatted(Formatting.AQUA));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderVersion.screenBackground(this, context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = x;
        int top = y;
        int right = left + backgroundWidth;
        int bottom = top + backgroundHeight;
        LaserColor laserColor = handler.getColor();
        int rgb = laserColor.rgb();

        context.fill(left, top, right, bottom, 0xF20B0F15);
        context.fillGradient(left + 2, top + 2, right - 2, bottom - 2, 0xF2252B35, 0xF211151C);
        context.fill(left + 2, top + 2, right - 2, top + 4, withAlpha(rgb, 0xD8));
        context.fill(left + 2, bottom - 4, right - 2, bottom - 2, 0xFF05070A);
        context.fill(left + 10, top + 38, right - 10, top + 39, 0xFF47515F);

        if (page == Page.ENERGY) {
            context.fill(left + 22, top + 44, right - 22, top + 51, 0xFF04070B);
            int capacity = handler.getEnergyCapacity();
            int barWidth = capacity <= 0 ? 0 : (int) ((backgroundWidth - 44L) * handler.getEnergy() / capacity);
            context.fill(left + 22, top + 44, left + 22 + barWidth, top + 51, withAlpha(rgb, 255));
            for (var slot : handler.slots) {
                context.fill(left + slot.x - 1, top + slot.y - 1, left + slot.x + 17, top + slot.y + 17, 0xFF536171);
                context.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, 0xFF080D15);
            }
            return;
        }

        // Keep all controls reachable on small windows / high GUI scales.
        if (page != Page.MAIN || backgroundHeight < 304 || handler.isPoweredEmitter()) {
            return;
        }

        int previewY = bottom - 41;
        float widthScale = thicknessSlider == null
                ? handler.getBeamWidthScale()
                : LaserEmitterBlockEntity.beamWidthScale(thicknessSlider.getStep());
        float previewWidth = MathHelper.sqrt(widthScale);
        int haloRadius = MathHelper.clamp(Math.round(2.0F + previewWidth * 3.0F), 2, 13);
        int colorRadius = Math.max(1, Math.round(haloRadius * 0.52F));
        int coreRadius = Math.max(1, Math.round(previewWidth * 0.48F));

        context.fill(
                left + 22, previewY - haloRadius,
                right - 22, previewY + haloRadius + 1,
                withAlpha(rgb, 0x2A)
        );
        context.fill(
                left + 22, previewY - colorRadius,
                right - 22, previewY + colorRadius + 1,
                withAlpha(rgb, 0x86)
        );
        context.fill(
                left + 22, previewY - coreRadius,
                right - 22, previewY + coreRadius + 1,
                0xFFFFFFFF
        );

        context.fill(left + 17, previewY - 7, left + 31, previewY + 8, 0xFF07090C);
        context.fill(left + 20, previewY - 4, left + 28, previewY + 5, withAlpha(rgb, 0xB8));
        context.fill(left + 22, previewY - 2, left + 26, previewY + 3, 0xFFFFFFFF);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        if (page == Page.ENERGY) {
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.justifylasers.energy_settings"), titleX, titleY, 0xFFF3F7FF);
            String status = !handler.isTechnicalMode() ? "disabled" : !handler.hasCrystal() ? "no_crystal"
                    : handler.getEnergy() < handler.getEnergyCost() && !handler.isActive() ? "no_power" : handler.isEnabled() ? "ready" : "off";
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.justifylasers.energy_status." + status), 18, 28, 0xFFF3F7FF);
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.justifylasers.energy_amount",
                    handler.getEnergy(), handler.getEnergyCapacity(), Platform.ENERGY_UNIT), 22, 55, 0xFFF3F7FF);
            String[] labels = {"crystal", "silk", "drops", "scorch", "ignite"};
            for (int slot = 0; slot < labels.length; slot++) {
                Text label = Text.translatable("gui.justifylasers.slot." + labels[slot]);
                context.drawText(textRenderer, label, 44 + slot * 42 - textRenderer.getWidth(label) / 2, 71, 0xFFB6C5D9, false);
            }
            if (backgroundHeight >= 270) {
                context.drawText(textRenderer, Text.translatable("gui.justifylasers.module_hint"), 22, 207, 0xFF98A4B5, false);
            }
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.justifylasers.energy_cost", handler.getEnergyCost(),
                            Platform.ENERGY_UNIT),
                    22, backgroundHeight - 20, 0xFFF3F7FF);
            return;
        }
        if (page == Page.MINING) {
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.justifylasers.mining_settings"),
                    titleX, titleY, 0xFFF3F7FF);
            context.drawTextWithShadow(textRenderer,
                    toggleLabel("gui.justifylasers.break_blocks", handler.breaksBlocks()), 18, 28, 0xFFFFFFFF);
            int summaryY = Math.min(200, backgroundHeight - 62);
            context.drawTextWithShadow(textRenderer, valueLabel("gui.justifylasers.mining_example",
                    String.format(Locale.ROOT, "%.2f", LaserMining.ticksToBreak(1.5F, miningSpeedSlider.getStep()) / 20.0D)),
                    22, summaryY, 0xFFF3F7FF);
            context.drawText(textRenderer, Text.translatable("gui.justifylasers.mining_hardness_hint"),
                    22, summaryY + 12, 0xFF98A4B5, false);
            return;
        }
        if (page == Page.DAMAGE) {
            context.drawTextWithShadow(textRenderer, Text.translatable("gui.justifylasers.damage_settings"),
                    titleX, titleY, 0xFFF3F7FF);
            context.drawTextWithShadow(textRenderer,
                    toggleLabel("gui.justifylasers.damage_entities", handler.damagesEntities()), 18, 28, 0xFFFFFFFF);
            float damagePerSecond = LaserDamage.damageForStep(damageSlider.getStep()) * hitRateSlider.getStep();
            int summaryY = Math.min(200, backgroundHeight - 62);
            context.drawTextWithShadow(textRenderer, valueLabel("gui.justifylasers.damage_per_second",
                    String.format(Locale.ROOT, "%.1f", damagePerSecond)), 22, summaryY, 0xFFF3F7FF);
            context.drawText(textRenderer, Text.translatable("gui.justifylasers.damage_reduction_hint"),
                    22, summaryY + 12, 0xFF98A4B5, false);
            return;
        }
        context.drawTextWithShadow(textRenderer, title, titleX, titleY, 0xFFF3F7FF);

        Text status = Text.translatable(
                handler.isActive() ? "gui.justifylasers.status.active" : "gui.justifylasers.status.inactive"
        ).formatted(handler.isActive() ? Formatting.GREEN : Formatting.GRAY);
        context.drawTextWithShadow(textRenderer, status, 18, 28, 0xFFFFFFFF);

        context.drawText(
                textRenderer,
                Text.translatable("gui.justifylasers.range_hint", rangeSlider.getStep()),
                18,
                backgroundHeight - 20,
                0xFF98A4B5,
                false
        );
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    @Override
    public void removed() {
        sliders.forEach(SettingSlider::submitValue);
        super.removed();
    }

    private final class SettingSlider extends SliderWidget {
        private final int minStep;
        private final int maxStep;
        private final IntSupplier serverValue;
        private final int buttonBase;
        private final IntFunction<Text> label;
        private int lastSubmittedStep;
        private int pendingServerStep = -1;
        private int acknowledgementTicks;
        private int submitCountdown = -1;
        private boolean dragging;
        private boolean ready;

        private SettingSlider(int x, int y, int width, int height, int minStep, int maxStep,
                              int initialStep, IntSupplier serverValue, int buttonBase, IntFunction<Text> label) {
            super(
                    x,
                    y,
                    width,
                    height,
                    Text.empty(),
                    (MathHelper.clamp(initialStep, minStep, maxStep) - minStep) / (double) (maxStep - minStep)
            );
            this.minStep = minStep;
            this.maxStep = maxStep;
            this.serverValue = serverValue;
            this.buttonBase = buttonBase;
            this.label = label;
            lastSubmittedStep = MathHelper.clamp(initialStep, minStep, maxStep);
            updateMessage();
            ready = true;
        }

        private int getStep() {
            return MathHelper.clamp(minStep + (int) Math.round(value * (maxStep - minStep)), minStep, maxStep);
        }

        @Override
        protected void updateMessage() {
            setMessage(label.apply(getStep()));
        }

        @Override
        protected void applyValue() {
            updateMessage();
            if (ready) {
                // Debounce updates and still submit if the input path skips onRelease.
                submitCountdown = 2;
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            dragging = true;
            super.onClick(mouseX, mouseY);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
            dragging = true;
            super.onDrag(mouseX, mouseY, deltaX, deltaY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            dragging = false;
            submitValue();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
                setStepAndSubmit(getStep() + (keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1));
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private void setStepAndSubmit(int step) {
            dragging = false;
            value = (MathHelper.clamp(step, minStep, maxStep) - minStep) / (double) (maxStep - minStep);
            updateMessage();
            submitValue();
        }

        private void submitValue() {
            submitCountdown = -1;
            int step = getStep();
            if (step == lastSubmittedStep || client == null || client.interactionManager == null) {
                return;
            }
            lastSubmittedStep = step;
            pendingServerStep = step;
            acknowledgementTicks = 40;
            ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId, buttonBase + step));
        }

        private void tick() {
            if (submitCountdown > 0 && --submitCountdown == 0) {
                submitValue();
            }

            int serverStep = MathHelper.clamp(serverValue.getAsInt(), minStep, maxStep);
            if (dragging) {
                return;
            }
            if (pendingServerStep >= 0) {
                if (serverStep != pendingServerStep && --acknowledgementTicks > 0) {
                    return;
                }
                pendingServerStep = -1;
            }
            if (getStep() != serverStep) {
                value = (serverStep - minStep) / (double) (maxStep - minStep);
                updateMessage();
            }
            lastSubmittedStep = serverStep;
        }
    }
}
