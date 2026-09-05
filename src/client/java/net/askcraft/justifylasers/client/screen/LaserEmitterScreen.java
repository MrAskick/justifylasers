package net.askcraft.justifylasers.client.screen;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

public class LaserEmitterScreen extends HandledScreen<LaserEmitterScreenHandler> {
    private ButtonWidget enabledButton;
    private ButtonWidget redstoneButton;
    private ButtonWidget colorButton;
    private BeamThicknessSlider thicknessSlider;
    private ButtonWidget lightEmissionButton;
    private ButtonWidget minecraftLightingButton;
    private ButtonWidget breakBlocksButton;
    private ButtonWidget damageEntitiesButton;

    public LaserEmitterScreen(LaserEmitterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 276;
        backgroundHeight = 286;
        titleX = 18;
        titleY = 13;
    }

    @Override
    protected void init() {
        backgroundHeight = Math.min(286, height - 12);
        super.init();

        int buttonX = x + 22;
        int buttonWidth = backgroundWidth - 44;
        int rowStep = backgroundHeight < 270 ? 18 : 22;
        int buttonHeight = rowStep - 2;
        enabledButton = addDrawableChild(makeButton(buttonX, y + 48, buttonWidth, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_ENABLED));
        redstoneButton = addDrawableChild(makeButton(buttonX, y + 48 + rowStep, buttonWidth, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_REDSTONE));
        colorButton = addDrawableChild(makeButton(buttonX, y + 48 + rowStep * 2, buttonWidth, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_COLOR));
        thicknessSlider = addDrawableChild(new BeamThicknessSlider(
                buttonX,
                y + 48 + rowStep * 3,
                buttonWidth,
                buttonHeight,
                handler.getBeamWidthStep()
        ));
        lightEmissionButton = addDrawableChild(makeButton(buttonX, y + 48 + rowStep * 4, buttonWidth, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_LIGHT_EMISSION));
        lightEmissionButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.light_emission.tooltip")));
        minecraftLightingButton = addDrawableChild(makeButton(buttonX, y + 48 + rowStep * 5, buttonWidth, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_MINECRAFT_LIGHTING));
        minecraftLightingButton.setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.minecraft_lighting.tooltip")));
        breakBlocksButton = addDrawableChild(makeButton(buttonX, y + 48 + rowStep * 6, buttonWidth, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_BREAK_BLOCKS));
        damageEntitiesButton = addDrawableChild(makeButton(buttonX, y + 48 + rowStep * 7, buttonWidth, buttonHeight,
                LaserEmitterScreenHandler.BUTTON_DAMAGE_ENTITIES));

        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.justifylasers.close"),
                        button -> close())
                .dimensions(x + backgroundWidth - 72, y + backgroundHeight - 26, 54, 18)
                .build());

        updateButtonMessages();
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
        if (thicknessSlider != null) {
            thicknessSlider.tick(handler.getBeamWidthStep());
        }
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
    }

    private Text toggleLabel(String key, boolean value) {
        Text state = Text.translatable(value ? "gui.justifylasers.on" : "gui.justifylasers.off")
                .formatted(value ? Formatting.GREEN : Formatting.RED);
        return Text.translatable(key, state);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
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

        // Keep all controls reachable on small windows / high GUI scales.
        if (backgroundHeight < 282) {
            return;
        }

        int previewY = bottom - 41;
        float widthScale = thicknessSlider == null
                ? handler.getBeamWidthScale()
                : thicknessSlider.getBeamWidthScale();
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
        context.drawTextWithShadow(textRenderer, title, titleX, titleY, 0xFFF3F7FF);

        Text status = Text.translatable(
                handler.isActive() ? "gui.justifylasers.status.active" : "gui.justifylasers.status.inactive"
        ).formatted(handler.isActive() ? Formatting.GREEN : Formatting.GRAY);
        context.drawTextWithShadow(textRenderer, status, 18, 28, 0xFFFFFFFF);

        context.drawText(
                textRenderer,
                Text.translatable("gui.justifylasers.range_hint"),
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
        if (thicknessSlider != null) {
            thicknessSlider.submitValue();
        }
        super.removed();
    }

    private final class BeamThicknessSlider extends SliderWidget {
        private int lastSubmittedStep;
        private int pendingServerStep = -1;
        private int submitCountdown = -1;
        private boolean dragging;
        private boolean ready;

        private BeamThicknessSlider(int x, int y, int width, int height, int initialStep) {
            super(
                    x,
                    y,
                    width,
                    height,
                    Text.empty(),
                    initialStep / (double) LaserEmitterBlockEntity.BEAM_WIDTH_STEPS
            );
            lastSubmittedStep = LaserEmitterBlockEntity.clampBeamWidthStep(initialStep);
            updateMessage();
            ready = true;
        }

        private int getBeamWidthStep() {
            return LaserEmitterBlockEntity.clampBeamWidthStep(
                    (int) Math.round(value * LaserEmitterBlockEntity.BEAM_WIDTH_STEPS)
            );
        }

        private float getBeamWidthScale() {
            return LaserEmitterBlockEntity.beamWidthScale(getBeamWidthStep());
        }

        @Override
        protected void updateMessage() {
            String multiplier = String.format(Locale.ROOT, "%.2f×", getBeamWidthScale());
            setMessage(Text.translatable(
                    "gui.justifylasers.beam_width",
                    Text.literal(multiplier).formatted(Formatting.AQUA)
            ));
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
            int previousStep = getBeamWidthStep();
            boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
            if (getBeamWidthStep() != previousStep) {
                submitValue();
            }
            return handled;
        }

        private void submitValue() {
            submitCountdown = -1;
            int step = getBeamWidthStep();
            if (step == lastSubmittedStep || client == null || client.interactionManager == null) {
                return;
            }
            lastSubmittedStep = step;
            pendingServerStep = step;
            client.interactionManager.clickButton(
                    handler.syncId,
                    LaserEmitterScreenHandler.BEAM_WIDTH_BUTTON_BASE + step
            );
        }

        private void tick(int serverStep) {
            if (submitCountdown > 0 && --submitCountdown == 0) {
                submitValue();
            }

            serverStep = LaserEmitterBlockEntity.clampBeamWidthStep(serverStep);
            if (dragging) {
                return;
            }
            if (pendingServerStep >= 0) {
                if (serverStep != pendingServerStep) {
                    return;
                }
                pendingServerStep = -1;
            }
            if (getBeamWidthStep() != serverStep) {
                value = serverStep / (double) LaserEmitterBlockEntity.BEAM_WIDTH_STEPS;
                updateMessage();
            }
            lastSubmittedStep = serverStep;
        }
    }
}
