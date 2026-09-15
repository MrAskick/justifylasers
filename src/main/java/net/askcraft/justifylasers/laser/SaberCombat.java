package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.network.SaberStatePacket;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class SaberCombat {
    public enum Contact { NONE, HIT, BLOCK, PARRY, BREAK, CLASH }
    // Client and server entities share numeric IDs in an integrated game and compare equal.
    private static final Map<PlayerEntity, EnumMap<Hand, Fighter>> SERVER_FIGHTERS = new WeakHashMap<>();
    private static final Map<PlayerEntity, EnumMap<Hand, Fighter>> CLIENT_FIGHTERS = new WeakHashMap<>();

    private static final class Fighter {
        final World world;
        final Hand hand;
        SaberState state;
        ItemStack weapon;
        final Set<UUID> hit = new HashSet<>();
        Vec3d position, movement = Vec3d.ZERO;
        long lastAttack = Long.MIN_VALUE, guardStarted, parryReady, counterUntil, staggerImmune, regenAfter, nextToggle;
        long tick = Long.MIN_VALUE;
        boolean riposte;

        Fighter(PlayerEntity player, Hand hand, boolean staff) {
            this.hand = hand;
            world = player.getWorld();
            state = SaberState.idle(staff, world.getTime());
            weapon = player.getStackInHand(hand);
            position = player.getPos();
        }
    }

    private static Fighter fighter(PlayerEntity player, Hand hand) {
        var fighters = player.getWorld().isClient ? CLIENT_FIGHTERS : SERVER_FIGHTERS;
        var hands = fighters.computeIfAbsent(player, ignored -> new EnumMap<>(Hand.class));
        Fighter fighter = hands.get(hand);
        if (fighter == null || fighter.world != player.getWorld()) {
            fighter = new Fighter(player, hand, player.getStackInHand(hand).getItem() instanceof LaserSaberItem item && item.isStaff());
            hands.put(hand, fighter);
        }
        return fighter;
    }

    public static SaberState state(PlayerEntity player) { return state(player, Hand.MAIN_HAND); }
    public static SaberState state(PlayerEntity player, Hand hand) { return fighter(player, hand).state; }
    public static void acceptState(PlayerEntity player, SaberState state) { acceptState(player, Hand.MAIN_HAND, state); }
    public static void acceptState(PlayerEntity player, Hand hand, SaberState state) { fighter(player, hand).state = state; }
    public static void clearClientState() { CLIENT_FIGHTERS.clear(); }

    public static boolean toggle(PlayerEntity player) { return toggle(player, Hand.MAIN_HAND); }

    public static boolean toggle(PlayerEntity player, Hand hand) {
        if (player.getWorld().isClient || !eligible(player) || !(player.getStackInHand(hand).getItem() instanceof LaserSaberItem)) return false;
        Fighter fighter = fighter(player, hand);
        long now = player.getWorld().getTime();
        if (now < fighter.nextToggle) return false;
        fighter.nextToggle = now + 6;
        player.stopUsingItem();
        LaserSaberItem.setActive(player.getStackInHand(hand), !LaserSaberItem.active(player.getStackInHand(hand)));
        transition(player, fighter, SaberState.Action.IDLE, now, 0);
        return true;
    }

    public static boolean guard(PlayerEntity player) { return guard(player, Hand.MAIN_HAND); }

    public static boolean guard(PlayerEntity player, Hand hand) {
        if (player.getWorld().isClient || !eligible(player) || !LaserSaberItem.active(player.getStackInHand(hand))) return false;
        Fighter fighter = fighter(player, hand);
        SaberState state = fighter.state;
        long now = player.getWorld().getTime();
        if (state.guarding()) return true;
        if (busy(state, now) || state.stamina() < LaserConfig.get().saberParryCost) return false;
        spend(fighter, LaserConfig.get().saberParryCost, now);
        fighter.guardStarted = now >= fighter.parryReady ? now : now - 100;
        fighter.parryReady = now + 10;
        transition(player, fighter, SaberState.Action.GUARD, now, 0);
        return true;
    }

    public static boolean swing(PlayerEntity player, Hand hand) {
        if (player.getWorld().isClient || !eligible(player)
                || !LaserSaberItem.active(player.getStackInHand(hand))) return false;
        Fighter fighter = fighter(player, hand);
        long now = player.getWorld().getTime();
        if (busy(fighter.state, now)) return false;
        LaserSaberItem saber = (LaserSaberItem) player.getStackInHand(hand).getItem();
        var config = LaserConfig.get();
        float cost = saber.isStaff() ? config.staffAttackCost : config.saberAttackCost;
        if (fighter.state.stamina() < cost || player.getItemCooldownManager().isCoolingDown(saber)) return false;
        player.stopUsingItem();
        int stage = fighter.lastAttack != Long.MIN_VALUE && fighter.state.staff() == saber.isStaff()
                && now - fighter.lastAttack <= fighter.state.duration() + 8
                ? (fighter.state.stage() + 1) % (saber.isStaff() ? 3 : 2) : 0;
        var forward = Vec3d.fromPolar(0, player.getYaw());
        Vec3d movement = player.getPos().subtract(fighter.position);
        if (movement.lengthSquared() < .0001) movement = fighter.movement;
        SaberCut cut = SaberCut.select(movement.dotProduct(forward), movement.dotProduct(SaberPose.right(player.getYaw())), stage, saber.isStaff());
        spend(fighter, cost, now);
        fighter.riposte = now < fighter.counterUntil;
        fighter.counterUntil = 0;
        SaberState timings = SaberState.idle(saber.isStaff(), now);
        fighter.state = new SaberState(SaberState.Action.ATTACK, cut, stage, now,
                Math.max(1, timings.windup() - (fighter.riposte ? 1 : 0)), timings.active(), timings.recovery(),
                fighter.state.stamina(), config.saberStamina, fighter.state.sequence() + 1, saber.isStaff());
        fighter.lastAttack = now;
        fighter.weapon = player.getStackInHand(hand);
        fighter.hit.clear();
        broadcast(player, fighter, Contact.NONE, player.getEyePos());
        player.resetLastAttackedTicks();
        return true;
    }

    private static boolean eligible(PlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && player.currentScreenHandler == player.playerScreenHandler;
    }

    private static boolean busy(SaberState state, long now) {
        return state.action() == SaberState.Action.ATTACK && state.elapsed(now) < state.duration()
                || (state.action() == SaberState.Action.RECOIL || state.action() == SaberState.Action.BROKEN)
                && state.elapsed(now) < state.recovery();
    }

    public static void tick(ServerWorld world) {
        SERVER_FIGHTERS.keySet().removeIf(player -> player.isRemoved());
        for (var player : world.getPlayers()) tick(player);
    }

    public static void tick(PlayerEntity player) {
        if (player.getWorld().isClient) return;
        for (Hand hand : Hand.values()) {
            var hands = SERVER_FIGHTERS.get(player);
            if (player.getStackInHand(hand).getItem() instanceof LaserSaberItem || hands != null && hands.containsKey(hand)) tick(player, hand);
        }
    }

    private static void tick(PlayerEntity player, Hand hand) {
        Fighter fighter = fighter(player, hand);
        long now = player.getWorld().getTime();
        if (fighter.tick == now) return;
        fighter.tick = now;
        fighter.movement = player.getPos().subtract(fighter.position);
        fighter.position = player.getPos();
        boolean held = LaserSaberItem.active(player.getStackInHand(hand)) && eligible(player);
        if (!held || fighter.weapon != player.getStackInHand(hand) || fighter.movement.lengthSquared() > 16) {
            fighter.weapon = player.getStackInHand(hand);
            if (fighter.state.action() != SaberState.Action.IDLE) transition(player, fighter, SaberState.Action.IDLE, now, 0);
        }
        if (fighter.state.guarding()) {
            if (!held || !player.isUsingItem() || player.getActiveHand() != hand) transition(player, fighter, SaberState.Action.IDLE, now, 0);
            else {
                spend(fighter, LaserConfig.get().saberGuardDrain, now);
                if (fighter.state.stamina() <= 0) breakGuard(player, fighter, now);
                else if (fighter.state.action() == SaberState.Action.PARRY && fighter.state.elapsed(now) >= 4)
                    transition(player, fighter, SaberState.Action.GUARD, now, 0);
            }
        }
        if (held && fighter.state.action() == SaberState.Action.ATTACK
                && fighter.state.elapsed(now) >= fighter.state.windup()
                && fighter.state.elapsed(now) <= fighter.state.windup() + fighter.state.active()) sweep(player, fighter, now);
        if (!fighter.state.guarding() && fighter.state.action() != SaberState.Action.IDLE && !busy(fighter.state, now))
            transition(player, fighter, SaberState.Action.IDLE, now, 0);
        if (now >= fighter.regenAfter && fighter.state.action() == SaberState.Action.IDLE)
            stamina(fighter, Math.min(LaserConfig.get().saberStamina, fighter.state.stamina() + LaserConfig.get().saberStaminaRegen));
        if (held && now % 4 == 0) broadcast(player, fighter, Contact.NONE, player.getEyePos());
    }

    public static SaberPose.Local local(PlayerEntity player, double time) { return local(player, Hand.MAIN_HAND, time); }

    public static SaberPose.Local local(PlayerEntity player, Hand hand, double time) {
        return SaberPose.combat(state(player, hand), WeaponHands.arm(player, hand) == Arm.RIGHT ? 1 : -1, time, LaserSaberItem.active(player.getStackInHand(hand)));
    }

    public static SaberGeometry.Segment blade(PlayerEntity player, SaberState state, double time, int end) { return blade(player, Hand.MAIN_HAND, state, time, end); }

    public static SaberGeometry.Segment blade(PlayerEntity player, Hand hand, SaberState state, double time, int end) {
        var item = (LaserSaberItem) player.getStackInHand(hand).getItem();
        var local = SaberPose.combat(state, WeaponHands.arm(player, hand) == Arm.RIGHT ? 1 : -1, time, true);
        var frame = SaberPose.frame(player, hand, 1, false, local);
        Vec3d start = frame.hilt().add(frame.axis().multiply(end * item.hiltEnd()));
        var obstruction = LaserBeamTrace.traceFrom(player.getWorld(), frame.eye(), start.subtract(frame.eye()).normalize(), frame.eye().distanceTo(start));
        if (obstruction.hasBlockHit()) return new SaberGeometry.Segment(obstruction.end(), obstruction.end());
        var ray = LaserBeamTrace.traceFrom(player.getWorld(), start, frame.axis().multiply(end), item.bladeLength());
        return new SaberGeometry.Segment(ray.start(), ray.end());
    }

    private static void sweep(PlayerEntity player, Fighter fighter, long now) {
        var saber = (LaserSaberItem) player.getStackInHand(fighter.hand).getItem();
        if (fighter.hit.size() >= (saber.isStaff() ? 4 : 2)) return;
        SaberState attack = fighter.state;
        int end = attack.leadingEnd();
        // Sub-tick poses sweep the actual rendered blade, including the returning end of a staff.
        for (int sample = 0; sample <= 8 && fighter.state.action() == SaberState.Action.ATTACK; sample++) {
            double time = Math.max(attack.started() + attack.windup(), now - 1 + sample / 8.0);
            var segment = blade(player, fighter.hand, attack, time, end);
            if (segment.start().squaredDistanceTo(segment.end()) < .0001) continue;
            // A blade can intercept another blade well before either reaches a player's body.
            for (var other : player.getWorld().getPlayers()) {
                if (other == player || !other.isAlive() || other.isSpectator() || !player.shouldDamagePlayer(other)
                        || other.squaredDistanceTo(player) > 36 || fighter.hit.contains(other.getUuid())) continue;
                if (intercept(player, fighter, other, segment, time, now)) return;
            }
            Box bounds = new Box(segment.start(), segment.end()).expand(.14);
            var targets = player.getWorld().getEntitiesByClass(LivingEntity.class, bounds,
                    target -> target != player && target.isAlive() && !target.isSpectator() && !fighter.hit.contains(target.getUuid()));
            targets.sort(java.util.Comparator.comparingDouble(target -> target.getPos().squaredDistanceTo(segment.start())));
            for (LivingEntity target : targets) {
                if (target instanceof PlayerEntity other && !player.shouldDamagePlayer(other)) continue;
                var box = target.getBoundingBox().expand(.09);
                var hit = box.raycast(segment.start(), segment.end());
                if (hit.isEmpty() && !box.contains(segment.start())) continue;
                Vec3d contact = hit.orElse(segment.start());
                if (target instanceof PlayerEntity other) {
                    Fighter defense = guarding(other);
                    if (defense != null && defend(player, fighter, other, defense, contact, now)) return;
                }
                fighter.hit.add(target.getUuid());
                float damage = saber.damage() + (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) - 1;
                if (fighter.riposte) damage *= 1.15F;
                if (fighter.hit.size() > 1) damage *= .75F;
                var source = new DamageSource(player.getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(LaserDamage.TYPE), player);
                if (LaserDamage.hit(target, source, target.getPos().subtract(player.getPos()), Math.max(0, damage), 4)) {
                    target.takeKnockback(.14, player.getX() - target.getX(), player.getZ() - target.getZ());
                    player.onAttacking(target);
                    broadcast(player, fighter, Contact.HIT, contact);
                }
                if (fighter.hit.size() >= (saber.isStaff() ? 4 : 2)) return;
                // Bodies occlude one another along each individual blade sample.
                break;
            }
        }
    }

    private static boolean intercept(PlayerEntity attacker, Fighter attack, PlayerEntity other,
                                     SaberGeometry.Segment segment, double time, long now) {
        for (Hand hand : Hand.values()) {
            if (!LaserSaberItem.active(other.getStackInHand(hand))) continue;
            Fighter defender = fighter(other, hand);
            if (!defender.state.guarding() && !defender.state.activeAt(now)) continue;
            for (int end : defender.state.staff() ? new int[]{1, -1} : new int[]{1}) {
                Vec3d contact = SaberGeometry.contact(segment, blade(other, hand, defender.state, time, end), .20);
                if (contact != null && defend(attacker, attack, other, defender, contact, now)) return true;
            }
        }
        return false;
    }

    private static Fighter guarding(PlayerEntity player) {
        for (Hand hand : Hand.values()) if (LaserSaberItem.active(player.getStackInHand(hand))) {
            Fighter defense = fighter(player, hand);
            if (defense.state.guarding()) return defense;
        }
        return null;
    }

    private static boolean defend(PlayerEntity attacker, Fighter attack, PlayerEntity defender, Fighter defense, Vec3d point, long now) {
        var config = LaserConfig.get();
        if (!SaberGeometry.faces(defender.getRotationVec(1), attacker.getEyePos().subtract(defender.getEyePos()), config.saberGuardAngle)) return false;
        if (defense.state.guarding()) {
            Contact contact = guardContact(defender, defense, point, now, attack.state.staff() ? 1.25F : 1);
            if (contact == Contact.BREAK) return false;
            attack.hit.add(defender.getUuid());
            recoil(attacker, attack, now, contact == Contact.PARRY ? 5 : 3);
            attacker.takeKnockback(.10, defender.getX() - attacker.getX(), defender.getZ() - attacker.getZ());
            return true;
        }
        if (defense.state.activeAt(now)) {
            attack.hit.add(defender.getUuid());
            defense.hit.add(attacker.getUuid());
            spend(attack, 5, now); spend(defense, 5, now);
            recoil(attacker, attack, now, 3); recoil(defender, defense, now, 3);
            broadcast(attacker, attack, Contact.CLASH, point);
            broadcast(defender, defense, Contact.CLASH, point);
            return true;
        }
        return false;
    }

    public static boolean guardMelee(PlayerEntity player, DamageSource source, float amount) {
        if (player.getWorld().isClient || amount <= 0 || player.isInvulnerableTo(source) || !eligible(player)
                || !player.isUsingItem()
                || source.isOf(LaserDamage.TYPE) || source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_SHIELD)
                || !(source.getSource() instanceof LivingEntity attacker) || attacker == player) return false;
        if (attacker instanceof PlayerEntity other && !other.shouldDamagePlayer(player)) return false;
        Fighter defense = guarding(player);
        if (defense == null || !SaberGeometry.faces(player.getRotationVec(1),
                attacker.getEyePos().subtract(player.getEyePos()), LaserConfig.get().saberGuardAngle)) return false;
        Vec3d contact = player.getEyePos().lerp(attacker.getEyePos(), .4);
        Contact result = guardContact(player, defense, contact, player.getWorld().getTime(), 1);
        if (result == Contact.BREAK) return false;
        attacker.takeKnockback(result == Contact.PARRY ? .18 : .08, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
        return true;
    }

    private static Contact guardContact(PlayerEntity player, Fighter defense, Vec3d point, long now, float pressure) {
        var config = LaserConfig.get();
        boolean parry = now - defense.guardStarted <= config.saberParryWindowTicks;
        float cost = parry ? config.saberParryCost : config.saberBlockCost * pressure;
        if (defense.state.stamina() < cost) {
            breakGuard(player, defense, now);
            broadcast(player, defense, Contact.BREAK, point);
            return Contact.BREAK;
        }
        spend(defense, cost, now);
        if (parry) {
            defense.guardStarted = now - 100;
            defense.counterUntil = now + 12;
            stamina(defense, Math.min(config.saberStamina, defense.state.stamina() + 6));
            transition(player, defense, SaberState.Action.PARRY, now, 4);
        }
        Contact contact = parry ? Contact.PARRY : Contact.BLOCK;
        broadcast(player, defense, contact, point);
        return contact;
    }

    private static void recoil(PlayerEntity player, Fighter fighter, long now, int duration) {
        if (now < fighter.staggerImmune) {
            // Still stop the intercepted cut, but don't restart a stun lock.
            transition(player, fighter, SaberState.Action.RECOIL, now, 1);
            return;
        }
        fighter.staggerImmune = now + LaserConfig.get().saberStaggerImmunityTicks;
        transition(player, fighter, SaberState.Action.RECOIL, now, duration);
    }

    private static void breakGuard(PlayerEntity player, Fighter fighter, long now) {
        stamina(fighter, 0);
        player.stopUsingItem();
        fighter.guardStarted = now - 100;
        fighter.regenAfter = now + LaserConfig.get().saberGuardBreakTicks;
        transition(player, fighter, SaberState.Action.BROKEN, now, LaserConfig.get().saberGuardBreakTicks);
    }

    private static void spend(Fighter fighter, float amount, long now) { stamina(fighter, Math.max(0, fighter.state.stamina() - amount)); fighter.regenAfter = now + 10; }
    private static void stamina(Fighter fighter, float value) {
        var s = fighter.state;
        fighter.state = new SaberState(s.action(), s.cut(), s.stage(), s.started(), s.windup(), s.active(), s.recovery(), value, s.capacity(), s.sequence(), s.staff());
    }
    private static void transition(PlayerEntity player, Fighter fighter, SaberState.Action action, long now, int duration) {
        var s = fighter.state;
        fighter.state = new SaberState(action, s.cut(), s.stage(), now, s.windup(), s.active(), duration > 0 ? duration : s.recovery(), s.stamina(), s.capacity(), s.sequence(), s.staff());
        broadcast(player, fighter, Contact.NONE, player.getEyePos());
    }
    private static void broadcast(PlayerEntity player, Fighter fighter, Contact contact, Vec3d position) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;
        var packet = new SaberStatePacket(player.getId(), world.getTime(), fighter.state, contact, position,
                LaserColor.byIndex(GameVersion.cubeColor(player.getStackInHand(fighter.hand))).rgb(), fighter.hand);
        for (var viewer : world.getPlayers()) if (viewer.squaredDistanceTo(player) <= 128 * 128) Platform.sendSaberState(viewer, packet);
    }

    /** Diagnostic query; damage is exclusively applied by the server's active-phase tick. */
    public static Set<LivingEntity> targets(PlayerEntity player, LaserSaberItem saber) { return targets(player, saber, state(player).stage()); }
    public static Set<LivingEntity> targets(PlayerEntity player, LaserSaberItem saber, int stage) { return targets(player, Hand.MAIN_HAND, saber, stage); }
    public static Set<LivingEntity> targets(PlayerEntity player, Hand hand, LaserSaberItem saber, int stage) {
        Set<LivingEntity> result = new LinkedHashSet<>();
        var idle = SaberState.idle(saber.isStaff(), 0);
        var attack = new SaberState(SaberState.Action.ATTACK, SaberCut.select(0, 0, stage, saber.isStaff()), stage, 0,
                idle.windup(), idle.active(), idle.recovery(), idle.stamina(), idle.capacity(), 0, saber.isStaff());
        for (int sample = 0; sample <= 32; sample++) {
            var blade = blade(player, hand, attack, attack.windup() + attack.active() * sample / 32.0, attack.leadingEnd());
            for (var target : player.getWorld().getEntitiesByClass(LivingEntity.class, new Box(blade.start(), blade.end()).expand(.1), target -> target != player))
                if (target.getBoundingBox().expand(.09).raycast(blade.start(), blade.end()).isPresent()) result.add(target);
        }
        return result;
    }

    private SaberCombat() { }
}
