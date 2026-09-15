package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.LaserWeapon;
import net.askcraft.justifylasers.laser.WeaponHands;
import net.askcraft.justifylasers.platform.GameVersion;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class LaserGunItem extends Item {
    private static final Map<PlayerEntity, EnumMap<Hand, Trigger>> TRIGGERS = new WeakHashMap<>();

    private static final class Trigger {
        final ItemStack stack;
        long received, fired = Long.MIN_VALUE;

        Trigger(ItemStack stack, long received) { this.stack = stack; this.received = received; }
    }
    public LaserGunItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (player.isSneaking()) {
            if (!world.isClient) {
                LaserColor color = LaserColor.byIndex(GameVersion.cubeColor(stack) + 1);
                GameVersion.setCubeColor(stack, color.ordinal());
                player.sendMessage(Text.translatable("message.justifylasers.gun_color", Text.translatable("gui.justifylasers.color." + color.asString())), true);
            }
            return TypedActionResult.success(stack, world.isClient);
        }
        return TypedActionResult.pass(stack);
    }

    public static void startFiring(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof LaserGunItem)) return;
        if (!player.getWorld().isClient) {
            var hands = TRIGGERS.computeIfAbsent(player, ignored -> new EnumMap<>(Hand.class));
            var trigger = hands.get(hand);
            if (trigger == null || trigger.stack != stack) {
                if (trigger != null) firingFlag(trigger.stack, false);
                trigger = new Trigger(stack, player.getWorld().getTime());
                hands.put(hand, trigger);
            }
            trigger.received = player.getWorld().getTime();
            var data = GameVersion.itemData(stack);
            data.putInt("WeaponRange", LaserConfig.get().laserGunRange);
            GameVersion.setItemData(stack, data);
        }
        firingFlag(stack, true);
        if (WeaponHands.dual(player)) {
            if (player.getActiveItem().getItem() instanceof LaserGunItem) player.stopUsingItem();
        } else if (!player.isUsingItem()) player.setCurrentHand(hand);
    }

    public static void stopFiring(PlayerEntity player, Hand hand) {
        releaseTrigger(player, hand);
        if (player.isUsingItem() && player.getActiveHand() == hand && player.getActiveItem().getItem() instanceof LaserGunItem)
            player.stopUsingItem();
    }

    private static void releaseTrigger(PlayerEntity player, Hand hand) {
        var hands = player.getWorld().isClient ? null : TRIGGERS.get(player);
        var trigger = hands == null ? null : hands.remove(hand);
        if (trigger != null) firingFlag(trigger.stack, false);
        if (hands != null && hands.isEmpty()) TRIGGERS.remove(player);
        firingFlag(player.getStackInHand(hand), false);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof PlayerEntity player && !WeaponHands.dual(player)) releaseTrigger(player, player.getActiveHand());
    }

    public static boolean isFiring(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof LaserGunItem) || !player.isAlive()) return false;
        if (player.getWorld().isClient) return GameVersion.itemData(stack).getBoolean("WeaponFiring");
        var hands = TRIGGERS.get(player);
        return hands != null && hands.containsKey(hand) && hands.get(hand).stack == stack;
    }

    private static void firingFlag(ItemStack stack, boolean firing) {
        if (!(stack.getItem() instanceof LaserGunItem)) return;
        var data = GameVersion.itemData(stack);
        if (data.getBoolean("WeaponFiring") == firing) return;
        if (firing) data.putBoolean("WeaponFiring", true);
        else data.remove("WeaponFiring");
        GameVersion.setItemData(stack, data);
    }

    public static void tick(ServerWorld world) {
        TRIGGERS.entrySet().removeIf(entry -> {
            if (!entry.getKey().isRemoved()) return false;
            entry.getValue().values().forEach(trigger -> firingFlag(trigger.stack, false));
            return true;
        });
        for (var player : world.getPlayers()) for (Hand hand : Hand.values()) tick(player, hand);
    }

    public static void tick(PlayerEntity player, Hand hand) {
        if (player.getWorld().isClient) return;
        var hands = TRIGGERS.get(player);
        var trigger = hands == null ? null : hands.get(hand);
        if (trigger == null) return;
        long now = player.getWorld().getTime();
        // Inputs expire independently: a lost release packet cannot keep either barrel alive.
        if (!player.isAlive() || player.isSpectator() || player.currentScreenHandler != player.playerScreenHandler
                || trigger.stack != player.getStackInHand(hand) || now - trigger.received > 30 || now < trigger.received) {
            stopFiring(player, hand);
            return;
        }
        if (trigger.fired == now) return;
        trigger.fired = now;
        LaserWeapon.damage(player.getWorld(), LaserWeapon.trace(player.getWorld(), player.getEyePos(), player.getRotationVec(1),
                LaserConfig.get().laserGunRange, player), player);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (user instanceof PlayerEntity player) tick(player, player.getActiveHand());
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient && entity instanceof PlayerEntity player
                && !(player.getMainHandStack() == stack && isFiring(player, Hand.MAIN_HAND))
                && !(player.getOffHandStack() == stack && isFiring(player, Hand.OFF_HAND))) firingFlag(stack, false);
    }

    public int getMaxUseTime(ItemStack stack) { return 72000; }
    public int getMaxUseTime(ItemStack stack, LivingEntity user) { return 72000; }
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.NONE; }

    public static int range(ItemStack stack) {
        var data = GameVersion.itemData(stack);
        return data.contains("WeaponRange") ? Math.max(1, Math.min(512, data.getInt("WeaponRange"))) : 64;
    }
}
