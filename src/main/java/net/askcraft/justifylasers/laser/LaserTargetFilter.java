package net.askcraft.justifylasers.laser;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class LaserTargetFilter {
    public static final int HOSTILE = 1, PASSIVE = 2, PLAYERS = 4, EXCLUDE_OWNER = 8;
    public static final int MAX_EXCLUSIONS = 32;
    private int flags = HOSTILE | PASSIVE | PLAYERS;
    private final Map<UUID, String> excluded = new LinkedHashMap<>();

    public int flags() { return flags; }
    public void setFlags(int value) { flags = value & 15; }
    public Map<UUID, String> exclusions() { return Collections.unmodifiableMap(excluded); }

    public boolean allows(LivingEntity entity, @Nullable UUID owner) {
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
        return nbt;
    }

    public void read(NbtCompound nbt) {
        flags = nbt.contains("Flags") ? nbt.getInt("Flags") & 15 : HOSTILE | PASSIVE | PLAYERS;
        excluded.clear();
        NbtList players = nbt.getList("Excluded", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < Math.min(players.size(), MAX_EXCLUSIONS); i++) {
            NbtCompound entry = players.getCompound(i);
            if (entry.containsUuid("Id")) exclude(entry.getUuid("Id"), entry.getString("Name"));
        }
    }
}
