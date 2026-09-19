package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record LaserSettingsPacket(int syncId, int buttonId, String argument) {
    public static final Identifier ID = JustifyLasers.id("emitter_setting");
    public LaserSettingsPacket(int syncId, int buttonId) { this(syncId, buttonId, ""); }
    public LaserSettingsPacket(PacketByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readString(256));
    }

    public void write(PacketByteBuf buf) {
        // Vanilla ButtonClickC2SPacket truncates button IDs to a signed byte in 1.20.1.
        buf.writeVarInt(syncId);
        buf.writeVarInt(buttonId);
        buf.writeString(argument, 256);
    }

    public boolean apply(PlayerEntity player) {
        if (!player.isAlive() || player.isSpectator() || player.currentScreenHandler.syncId != syncId) return false;
        if (player.currentScreenHandler instanceof net.askcraft.justifylasers.screen.LaserModuleScreenHandler module)
            return module.setting(player, buttonId, argument);
        if (player.isAlive() && !player.isSpectator()
                && player.currentScreenHandler instanceof net.askcraft.justifylasers.screen.LaserTurretScreenHandler turret
                && turret.syncId == syncId) return buttonId == 6 ? turret.toggleExcludedPlayer(player, argument)
                        : (buttonId == LaserEmitterScreenHandler.BUTTON_FILTER_MODE || buttonId == LaserEmitterScreenHandler.BUTTON_FILTER_TYPE)
                        && turret.editEntityFilter(player, argument, buttonId == LaserEmitterScreenHandler.BUTTON_FILTER_MODE);
        return player.isAlive() && !player.isSpectator()
                && player.currentScreenHandler instanceof LaserEmitterScreenHandler handler
                && handler.syncId == syncId
                && (buttonId == LaserEmitterScreenHandler.BUTTON_SPECTRUM ? handler.setSpectrum(player, argument) : buttonId == LaserEmitterScreenHandler.BUTTON_FILTER_PLAYER
                        ? handler.toggleExcludedPlayer(player, argument) : buttonId == LaserEmitterScreenHandler.BUTTON_FILTER_TYPE
                        ? handler.toggleEntityType(player, argument) : handler.onButtonClick(player, buttonId));
    }
}
