package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class SaberFencingGameTests implements FabricGameTest {
    private static boolean previousPvp;
    @net.minecraft.test.BeforeBatch(batchId = "saber_fencing")
    public void enablePvp(net.minecraft.server.world.ServerWorld world) {
        previousPvp = world.getServer().isPvpEnabled(); world.getServer().setPvpEnabled(true);
    }
    @net.minecraft.test.AfterBatch(batchId = "saber_fencing")
    public void restorePvp(net.minecraft.server.world.ServerWorld world) { world.getServer().setPvpEnabled(previousPvp); }
    private ServerPlayerEntity fighter(TestContext context, double z, float yaw) {
        var world = context.getWorld();
        var player = new ServerPlayerEntity(world.getServer(), world, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-fencer"));
        world.getServer().getPlayerManager().onPlayerConnect(new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND), player);
        player.changeGameMode(GameMode.SURVIVAL); player.setNoGravity(true);
        player.setPosition(context.getAbsolute(new Vec3d(3.5, 2, z)));
        player.setYaw(yaw); player.setPitch(0);
        // Mock connections do not receive normal player ticks; expire login protection before combat.
        for (int i = 0; i < 80; i++) player.tick();
        player.setHeadYaw(yaw); player.prevHeadYaw = yaw; player.bodyYaw = yaw; player.prevBodyYaw = yaw;
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModBlocks.LASER_SABER));
        LaserSaberItem.setActive(player.getMainHandStack(), true);
        context.runAtEveryTick(() -> player.setVelocity(Vec3d.ZERO));
        return player;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "saber_fencing")
    public void frontalGuardStopsTheCutAndConsumesStamina(TestContext context) {
        var attacker = fighter(context, 2.5, 0); var defender = fighter(context, 4.3, 180);
        defender.getMainHandStack().use(context.getWorld(), defender, Hand.MAIN_HAND);
        context.runAtTick(10, () -> context.assertTrue(SaberCombat.swing(attacker, Hand.MAIN_HAND), "Attack starts after guard's parry window"));
        context.runAtTick(24, () -> {
            context.assertTrue(defender.getHealth() == 20, "Frontal guard prevents damage; health=" + defender.getHealth());
            context.assertTrue(SaberCombat.state(defender).stamina() < 78, "Normal block costs stamina in addition to guard upkeep: " + SaberCombat.state(defender) + ", attacker=" + SaberCombat.state(attacker));
            attacker.discard(); defender.discard(); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "saber_fencing")
    public void guardAlsoStopsNormalMeleeButNotEnvironmentalDamage(TestContext context) {
        var attacker = fighter(context, 2.5, 0); var defender = fighter(context, 4.3, 180);
        attacker.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        defender.getMainHandStack().use(context.getWorld(), defender, Hand.MAIN_HAND);
        context.runAtTick(10, () -> {
            context.assertTrue(!defender.damage(context.getWorld().getDamageSources().playerAttack(attacker), 5), "Frontal vanilla melee is blocked");
            context.assertTrue(defender.getHealth() == 20 && SaberCombat.state(defender).stamina() < 80, "Ordinary melee spends guard stamina");
            context.assertTrue(defender.damage(context.getWorld().getDamageSources().fall(), 3), "Guard is not environmental immunity");
            attacker.discard(); defender.discard(); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "saber_fencing")
    public void normalMeleeFromBehindBypassesGuard(TestContext context) {
        var attacker = fighter(context, 2.5, 0); var defender = fighter(context, 4.3, 0);
        attacker.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        defender.getMainHandStack().use(context.getWorld(), defender, Hand.MAIN_HAND);
        context.assertTrue(defender.damage(context.getWorld().getDamageSources().playerAttack(attacker), 5) && defender.getHealth() < 20,
                "Normal melee cannot be blocked from behind");
        attacker.discard(); defender.discard(); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "saber_fencing")
    public void rearAttackBypassesGuard(TestContext context) {
        var attacker = fighter(context, 2.5, 0); var defender = fighter(context, 4.3, 0);
        defender.getMainHandStack().use(context.getWorld(), defender, Hand.MAIN_HAND);
        SaberCombat.swing(attacker, Hand.MAIN_HAND);
        context.runAtTick(14, () -> {
            context.assertTrue(defender.getHealth() < 20, "A guard cannot protect the back; health=" + defender.getHealth());
            attacker.discard(); defender.discard(); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "saber_fencing")
    public void timedParryDeflectsAndEnablesAnEarlyCounter(TestContext context) {
        var attacker = fighter(context, 2.5, 0); var defender = fighter(context, 4.3, 180);
        SaberCombat.swing(attacker, Hand.MAIN_HAND);
        context.runAtTick(2, () -> defender.getMainHandStack().use(context.getWorld(), defender, Hand.MAIN_HAND));
        boolean[] parried = {false};
        context.runAtEveryTick(() -> {
            if (SaberCombat.state(defender).action() == SaberState.Action.PARRY) parried[0] = true;
        });
        context.runAtTick(8, () -> {
            context.assertTrue(parried[0] && defender.getHealth() == 20, "Well-timed guard must parry; attacker=" + SaberCombat.state(attacker) + ", defender=" + SaberCombat.state(defender));
            context.assertTrue(SaberCombat.swing(defender, Hand.MAIN_HAND), "Parry allows an immediate counterattack");
            context.assertTrue(SaberCombat.state(defender).windup() < net.askcraft.justifylasers.config.LaserConfig.get().saberWindupTicks, "Counter has a shorter windup");
        });
        context.runAtTick(20, () -> { attacker.discard(); defender.discard(); context.complete(); });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "saber_fencing", tickLimit = 220)
    public void holdingGuardEventuallyBreaksAndThenRecovers(TestContext context) {
        var player = fighter(context, 2.5, 0);
        player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
        boolean[] broken = {false};
        context.runAtEveryTick(() -> { if (SaberCombat.state(player).action() == SaberState.Action.BROKEN) broken[0] = true; });
        context.runAtTick(180, () -> {
            context.assertTrue(broken[0] && !player.isUsingItem(), "Infinite guard is impossible");
            context.assertTrue(SaberCombat.state(player).action() == SaberState.Action.IDLE && SaberCombat.state(player).stamina() > 0, "Guard break expires and stamina recovers");
            player.discard(); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "saber_fencing")
    public void switchingToEmptySlotCancelsAnActiveAttack(TestContext context) {
        var attacker = fighter(context, 2.5, 0); var defender = fighter(context, 4.3, 180);
        defender.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        SaberCombat.swing(attacker, Hand.MAIN_HAND);
        context.runAtTick(1, () -> attacker.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY));
        context.runAtTick(14, () -> {
            context.assertTrue(SaberCombat.state(attacker).action() == SaberState.Action.IDLE && defender.getHealth() == 20, "Removed weapon cannot finish an invisible attack");
            attacker.discard(); defender.discard(); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "saber_fencing")
    public void depletedGuardCanBeBrokenThrough(TestContext context) {
        var attacker = fighter(context,2.5,0); var defender = fighter(context,4.3,180);
        defender.getMainHandStack().use(context.getWorld(),defender,Hand.MAIN_HAND);
        var state = SaberCombat.state(defender);
        SaberCombat.acceptState(defender,new SaberState(state.action(),state.cut(),state.stage(),state.started(),state.windup(),state.active(),state.recovery(),1,state.capacity(),state.sequence(),false));
        SaberCombat.swing(attacker,Hand.MAIN_HAND);
        context.runAtTick(14,() -> {
            context.assertTrue(defender.getHealth() < 20 && !defender.isUsingItem(),"Exhaustion creates a real opening");
            attacker.discard(); defender.discard(); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "saber_fencing")
    public void intersectingActiveBladesStopBothCuts(TestContext context) {
        var a = fighter(context,2.5,0); var b = fighter(context,4.3,180);
        SaberCombat.swing(a,Hand.MAIN_HAND); SaberCombat.swing(b,Hand.MAIN_HAND);
        boolean[] clash = {false};
        context.runAtEveryTick(() -> {
            if (SaberCombat.state(a).action() == SaberState.Action.RECOIL && SaberCombat.state(b).action() == SaberState.Action.RECOIL) clash[0] = true;
        });
        context.runAtTick(14,() -> {
            context.assertTrue(clash[0] && a.getHealth() == 20 && b.getHealth() == 20,"Blade contact must stop both attacks, not exchange body damage: " + a.getHealth() + "/" + b.getHealth());
            a.discard(); b.discard(); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "saber_fencing")
    public void movementAndStaffSelectDistinctServerCuts(TestContext context) {
        var player = fighter(context, 2.5, 0);
        SaberCombat.tick(player); player.setPosition(player.getPos().add(SaberPose.right(0).multiply(.1)).add(0,0,.1));
        context.assertTrue(SaberCombat.swing(player, Hand.MAIN_HAND), "Directional swing accepted");
        context.assertTrue(SaberCombat.state(player).cut() == SaberCut.DIAGONAL_RIGHT, "Server derives a diagonal cut from movement");
        player.discard(); context.complete();
    }
}
