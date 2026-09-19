package net.askcraft.justifylasers.laser;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class LaserTargetFilter {
    public static final int HOSTILE = 1, PASSIVE = 2, PLAYERS = 4, EXCLUDE_OWNER = 8;
    public static final int MAX_EXCLUSIONS = 32;
    public static final int WHITELIST = 16;
    public static final int MAX_TYPES = 1024;
    private int flags = HOSTILE | PASSIVE | PLAYERS;
    private final Map<UUID, String> excluded = new LinkedHashMap<>();
    private final Set<Identifier> types = new LinkedHashSet<>();

    public int flags() { return flags; }
    public void setFlags(int value) { flags = value & 31; }
    public Map<UUID, String> exclusions() { return Collections.unmodifiableMap(excluded); }
    public Set<Identifier> types() { return Collections.unmodifiableSet(types); }
    public boolean whitelist() { return (flags & WHITELIST) != 0; }
    public void toggleMode() { flags ^= WHITELIST; }

    public boolean toggleType(String name) {
        Identifier id = Identifier.tryParse(name);
        if (id == null || !Registries.ENTITY_TYPE.containsId(id)) return false;
        if (types.remove(id)) return true;
        return types.size() < MAX_TYPES && types.add(id);
    }

    public LaserTargetFilter copy() {
        var result = new LaserTargetFilter();
        result.flags = flags; result.excluded.putAll(excluded); result.types.addAll(types);
        return result;
    }

    @Override public boolean equals(Object other) {
        return other instanceof LaserTargetFilter filter && flags == filter.flags
                && excluded.equals(filter.excluded) && types.equals(filter.types);
    }

    @Override public int hashCode() { return Objects.hash(flags, excluded, types); }

    public boolean allows(LivingEntity entity, @Nullable UUID owner) {
        boolean listed = types.contains(Registries.ENTITY_TYPE.getId(entity.getType()));
        if (whitelist() != listed) return false;
        if (entity instanceof PlayerEntity) {
            return (flags & PLAYERS) != 0 && ((flags & EXCLUDE_OWNER) == 0 || !entity.getUuid().equals(owner))
                    && !excluded.containsKey(entity.getUuid());
        }
        int category = entity.getType().getSpawnGroup() == SpawnGroup.MONSTER ? HOSTILE : PASSIVE;
        return (flags & category) != 0;
    }

    public boolean removeName(String name) {
        return excluded.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(name));
    }

    public boolean exclude(UUID id, String name) {
        if (!name.matches("[A-Za-z0-9_]{1,16}") || !excluded.containsKey(id) && excluded.size() >= MAX_EXCLUSIONS) return false;
        excluded.put(id, name);
        return true;
    }

    public NbtCompound write() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Flags", flags);
        NbtList players = new NbtList();
        excluded.forEach((id, name) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Id", id);
            entry.putString("Name", name);
            players.add(entry);
        });
        nbt.put("Excluded", players);
        NbtList entities = new NbtList();
        types.forEach(id -> entities.add(net.minecraft.nbt.NbtString.of(id.toString())));
        nbt.put("EntityTypes", entities);
        return nbt;
    }

    public void read(NbtCompound nbt) {
        flags = nbt.contains("Flags") ? nbt.getInt("Flags") & 31 : HOSTILE | PASSIVE | PLAYERS;
        excluded.clear();
        NbtList players = nbt.getList("Excluded", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < Math.min(players.size(), MAX_EXCLUSIONS); i++) {
            NbtCompound entry = players.getCompound(i);
            if (entry.containsUuid("Id")) exclude(entry.getUuid("Id"), entry.getString("Name"));
        }
        types.clear();
        NbtList entities = nbt.getList("EntityTypes", NbtElement.STRING_TYPE);
        for (int i = 0; i < Math.min(entities.size(), MAX_TYPES); i++) {
            Identifier id = Identifier.tryParse(entities.getString(i));
            if (id != null) types.add(id); // Retain unloaded mod IDs when a server's mod set changes.
        }
    }
}
