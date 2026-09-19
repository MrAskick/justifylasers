package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/** Properties of this segment only. Redirects retain them; a world module changes downstream segments. */
public record BeamBehavior(EntityEffect entity, MiningEffect mining, @Nullable LaserTargetFilter filter,
                           @Nullable UUID owner, float widthMultiplier, boolean scorch,
                           @Nullable BlockPos collector, List<EntityEffect> extraEffects, boolean spectral) {
    public BeamBehavior(EntityEffect entity, MiningEffect mining, LaserTargetFilter filter, UUID owner,
                        float widthMultiplier, boolean scorch, BlockPos collector) {
        this(entity, mining, filter, owner, widthMultiplier, scorch, collector, List.of());
    }
    public BeamBehavior(EntityEffect entity, MiningEffect mining, LaserTargetFilter filter, UUID owner,
                        float widthMultiplier, boolean scorch, BlockPos collector, List<EntityEffect> extraEffects) {
        this(entity, mining, filter, owner, widthMultiplier, scorch, collector, extraEffects, false);
    }
    public BeamBehavior withSpectrum(boolean value) {
        return new BeamBehavior(entity, mining, filter, owner, widthMultiplier, scorch, collector, extraEffects, value);
    }
    public BeamBehavior { extraEffects = List.copyOf(extraEffects); }
    public record EntityEffect(LaserEntityMode mode, boolean enabled, int strength, int knockback, int frequency, boolean ignite, double intensity) {
        public EntityEffect(LaserEntityMode mode, boolean enabled, int strength, int knockback, int frequency, boolean ignite) {
            this(mode, enabled, strength, knockback, frequency, ignite, 1);
        }
        public boolean active() { return enabled && mode != LaserEntityMode.NONE && mode != LaserEntityMode.MINING; }
    }

    public record MiningEffect(boolean enabled, int speed, boolean silk, boolean drops, boolean collect, boolean smelt, BlockPos origin) {
        public MiningEffect(boolean enabled, int speed, boolean silk, boolean drops, boolean collect, boolean smelt) {
            this(enabled, speed, silk, drops, collect, smelt, null);
        }
        public MiningEffect(boolean enabled, int speed, boolean silk, boolean drops, boolean collect) {
            this(enabled, speed, silk, drops, collect, false);
        }
        public MiningEffect at(BlockPos pos) { return new MiningEffect(enabled, speed, silk, drops, collect, smelt, pos.toImmutable()); }
    }

    public static final BeamBehavior NONE = new BeamBehavior(
            new EntityEffect(LaserEntityMode.NONE, false, LaserDamage.DEFAULT_DAMAGE_STEP, LaserDamage.DEFAULT_KNOCKBACK_STEP, 20, false),
            new MiningEffect(false, 0, false, false, false), null, null, 1, true, null);

    public List<EntityEffect> entities() {
        var result = new ArrayList<EntityEffect>();
        if (entity.active()) result.add(entity);
        for (var effect : extraEffects) if (effect.active()) result.add(effect);
        return List.copyOf(result);
    }

    public BeamBehavior plusEntity(EntityEffect value) {
        if (!entity.active()) return withEntity(value);
        var effects = new ArrayList<>(extraEffects); effects.add(value);
        return new BeamBehavior(entity, mining, filter, owner, widthMultiplier, scorch, collector, effects, spectral);
    }

    public BeamBehavior igniting() {
        return new BeamBehavior(ignite(entity), new MiningEffect(mining.enabled(), mining.speed(), mining.silk(), mining.drops(), mining.collect(), true, mining.origin()),
                filter, owner, widthMultiplier, scorch, collector, extraEffects.stream().map(BeamBehavior::ignite).toList(), spectral);
    }

    private static EntityEffect ignite(EntityEffect value) {
        return new EntityEffect(value.mode(), value.enabled(), value.strength(), value.knockback(), value.frequency(), true, value.intensity());
    }

    public BeamBehavior withEntity(EntityEffect value) {
        return new BeamBehavior(value, mining, filter, owner, widthMultiplier, scorch, collector, extraEffects, spectral);
    }
    public BeamBehavior withMining(MiningEffect value, BlockPos destination) {
        if (value.origin() == null && mining.origin() != null) value = value.at(mining.origin());
        return new BeamBehavior(entity, value, filter, owner, widthMultiplier, scorch, destination, extraEffects, spectral);
    }
    public BeamBehavior withFilter(LaserTargetFilter value, UUID owner) {
        return new BeamBehavior(entity, mining, value, owner, widthMultiplier, scorch, collector, extraEffects, spectral);
    }
    public BeamBehavior withWidth(float value) {
        return new BeamBehavior(entity, mining, filter, owner, value, scorch, collector, extraEffects, spectral);
    }
    public BeamBehavior withScorch(boolean value) {
        return new BeamBehavior(entity, mining, filter, owner, widthMultiplier, value, collector, extraEffects, spectral);
    }
}
