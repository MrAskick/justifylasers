package net.askcraft.justifylasers.client.screen;

import net.askcraft.justifylasers.screen.LaserTurretScreenHandler;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public final class LaserTurretScreen extends HandledScreen<LaserTurretScreenHandler> {
    private TextFieldWidget excludedPlayer;
    public LaserTurretScreen(LaserTurretScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title); backgroundWidth = 216; backgroundHeight = 238; playerInventoryTitleY = 145;
    }

    @Override protected void init() {
        super.init();
        addDrawableChild(new SettingButton(x + 112, y + 25, 94, 20, 0, "power"));
        addDrawableChild(new SettingButton(x + 112, y + 49, 94, 20, 5, "color"));
        for (int index = 0; index < 4; index++) addDrawableChild(new SettingButton(x + 8 + (index % 2) * 102,
                y + 70 + (index / 2) * 20, 98, 18, index + 1, new String[]{"hostile", "passive", "players", "owner"}[index]));
        excludedPlayer = addDrawableChild(new TextFieldWidget(textRenderer, x + 10, y + 122, 110, 18,
                Text.translatable("gui.justifylasers.turret.player_name")));
        excludedPlayer.setMaxLength(16);
        addDrawableChild(new SettingButton(x + 126, y + 120, 80, 20, 6, "exclude"));
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderVersion.screenBackground(this, context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta); drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.fillGradient(x, y, x + backgroundWidth, y + backgroundHeight, 0xF01B2933, 0xF0081119);
        context.fill(x + 6, y + 18, x + 210, y + 19, 0xFF44B8C9);
        for (var slot : handler.slots) {
            int sx = x + slot.x, sy = y + slot.y;
            context.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF45606B);
            context.fill(sx, sy, sx + 16, sy + 16, 0xFF101B23);
        }
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.justifylasers.turret.gun"), x + 34, y + 25, 0xCBDDE7);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.justifylasers.turret.filter"), x + 74, y + 25, 0xCBDDE7);
        context.drawText(textRenderer, Text.translatable("gui.justifylasers.turret." + (handler.firing() ? "firing" : "standby")), x + 13, y + 58, 0x6ADADB, false);
        context.drawText(textRenderer, Text.translatable("gui.justifylasers.turret.owned_by", handler.ownerName()), x + 10, y + 110, 0x9CB4C3, false);
        excludedPlayer.setEditable(handler.hasFilter());
    }

    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, 8, 7, 0xE4EFF5, false);
        context.drawText(textRenderer, playerInventoryTitle, 8, playerInventoryTitleY, 0x9CB4C3, false);
    }

    private final class SettingButton extends ButtonWidget {
        private final int id;
        private SettingButton(int x, int y, int width, int height, int id, String key) {
            super(x, y, width, height, Text.translatable("gui.justifylasers.turret." + key),
                    button -> {
                        if (id == 6) ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId, 6, excludedPlayer.getText()));
                        else client.interactionManager.clickButton(handler.syncId, id);
                    }, DEFAULT_NARRATION_SUPPLIER);
            this.id = id;
        }
        public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) { renderWidget(context, mouseX, mouseY, delta); }
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            active = id == 0 || id == 5 || handler.hasFilter();
            if (id == 6) setTooltip(Tooltip.of(Text.translatable("gui.justifylasers.turret.exclude_hint", handler.exclusionCount())));
            boolean selected = id == 0 ? handler.enabled() : id <= 4 && (handler.flags() & (1 << (id - 1))) != 0;
            TechGui.button(context, getX(), getY(), width, height, TechGui.State.of(active, false, isHovered(), isFocused(), selected));
            float scale = Math.min(1, (width - 12F) / Math.max(1, textRenderer.getWidth(getMessage())));
            context.getMatrices().push();
            context.getMatrices().translate(getX() + width / 2F, getY() + (height - 8 * scale) / 2F, 0);
            context.getMatrices().scale(scale, scale, 1);
            context.drawCenteredTextWithShadow(textRenderer, getMessage(), 0, 0, active ? 0xD7EDF5 : 0x57727F);
            context.getMatrices().pop();
        }
    }
}
