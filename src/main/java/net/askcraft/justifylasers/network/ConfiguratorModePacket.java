package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.item.LaserConfiguratorItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public record ConfiguratorModePacket(Hand hand, int slot, int mode) {
    public static final Identifier ID = JustifyLasers.id("configurator_mode");
    public ConfiguratorModePacket(PacketByteBuf buffer) { this(buffer.readEnumConstant(Hand.class), buffer.readByte(), buffer.readByte()); }
    public void write(PacketByteBuf buffer) { buffer.writeEnumConstant(hand); buffer.writeByte(slot); buffer.writeByte(mode); }
    public void apply(PlayerEntity player) {
        if (!player.isAlive() || player.isSpectator() || mode < 0 || mode >= LaserConfiguratorItem.MODE_COUNT
                || slot != (hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot : 40)) return;
        var stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof LaserConfiguratorItem)) return;
        LaserConfiguratorItem.select(stack, mode);
        player.getInventory().markDirty();
        player.sendMessage(Text.translatable("message.justifylasers.configurator_mode",
                Text.translatable("item.justifylasers.configurator.mode." + mode)), true);
    }
}
