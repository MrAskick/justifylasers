package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.platform.GameVersion;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class LaserSaberItem extends Item {
    private final boolean staff;

    public LaserSaberItem(Settings settings, boolean staff) {
        super(settings);
        this.staff = staff;
    }

    public boolean isStaff() { return staff; }
    public int attackInterval() { return net.askcraft.justifylasers.laser.SaberState.idle(staff, 0).duration(); }
    public float damage() { return staff ? net.askcraft.justifylasers.config.LaserConfig.get().staffDamage : net.askcraft.justifylasers.config.LaserConfig.get().saberDamage; }
    public double hiltEnd() { return (staff ? 6.2 : 3.6) / 16; }
    public double bladeLength() { return staff ? 1.4 : 1.75; }

    public static boolean active(ItemStack stack) {
        return stack.getItem() instanceof LaserSaberItem && GameVersion.itemData(stack).getBoolean("SaberActive");
    }

    public static void setActive(ItemStack stack, boolean active) {
        var data = GameVersion.itemData(stack);
        data.putBoolean("SaberActive", active);
        GameVersion.setItemData(stack, data);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (net.askcraft.justifylasers.laser.WeaponHands.dual(player)) return TypedActionResult.pass(stack);
        if (world.isClient && !player.isSneaking()) player.setCurrentHand(hand);
        if (!world.isClient) {
            if (player.isSneaking()) {
                LaserColor color = LaserColor.byIndex(GameVersion.cubeColor(stack)).next();
                GameVersion.setCubeColor(stack, color.ordinal());
                player.sendMessage(Text.translatable("message.justifylasers.saber_color", Text.translatable(color.translationKey())), true);
            } else {
                if (!active(stack)) setActive(stack, true);
                if (net.askcraft.justifylasers.laser.SaberCombat.guard(player, hand)) player.setCurrentHand(hand);
            }
            if (player.isSneaking()) player.getItemCooldownManager().set(this, 5);
        }
        return TypedActionResult.consume(stack);
    }

    @Override public net.minecraft.util.UseAction getUseAction(ItemStack stack) { return net.minecraft.util.UseAction.NONE; }
    // Both supported game versions call the matching overload.
    public int getMaxUseTime(ItemStack stack) { return 72_000; }
    public int getMaxUseTime(ItemStack stack, net.minecraft.entity.LivingEntity user) { return 72_000; }

    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity player) { return false; }
}
