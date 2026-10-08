package com.richardsenger.piratesnships.worldsim.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Raid state of the world (WS5), saved with the overworld's data ({@code data/pirates_n_ships_raids.dat}): per
 * settlement the day of its last raid ({@code last_raid_day}, the cooldown) and the minutes a player has been there,
 * and the raids under way with the phase of each raiding ship. Server thread only.
 */
public final class RaidData extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_raids";

    /** Where one raiding ship is in its raid. */
    public enum Phase implements StringRepresentable {
        /** Sailing in toward the berth. */
        APPROACH,
        /** Anchored off the berth, its fighters ashore. */
        LANDED,
        /** Withdrawn, lost or turned away: nothing more to do for this raid. */
        DONE;

        public static final Codec<Phase> CODEC = StringRepresentable.fromEnum(Phase::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One raiding ship: its phase and the game time its fighters went ashore. */
    public record ShipRaid(Phase phase, long landedAt) {
        public static final Codec<ShipRaid> CODEC = RecordCodecBuilder.create(i -> i.group(
                Phase.CODEC.fieldOf("phase").forGetter(ShipRaid::phase),
                Codec.LONG.optionalFieldOf("landed_at", 0L).forGetter(ShipRaid::landedAt)
        ).apply(i, ShipRaid::new));

        public static final ShipRaid APPROACHING = new ShipRaid(Phase.APPROACH, 0L);
    }

    /**
     * A raid under way on {@code port}: the berth the raiders make for ({@code target}), the settlement's box
     * ({@code area}, where its bells hang and its raiders are looked for), when it started, its ships by voyage id,
     * whether any of them fought ({@code fought}) and whether raiders held the shore to the end ({@code succeeded}).
     */
    public record ActiveRaid(ResourceLocation port, ResourceKey<Level> dimension, BlockPos target, BoundingBox area,
                             long started, Map<UUID, ShipRaid> ships, boolean fought, boolean succeeded) {

        public static final Codec<ActiveRaid> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("port").forGetter(ActiveRaid::port),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ActiveRaid::dimension),
                BlockPos.CODEC.fieldOf("target").forGetter(ActiveRaid::target),
                BoundingBox.CODEC.fieldOf("area").forGetter(ActiveRaid::area),
                Codec.LONG.fieldOf("started").forGetter(ActiveRaid::started),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, ShipRaid.CODEC).fieldOf("ships").forGetter(ActiveRaid::ships),
                Codec.BOOL.optionalFieldOf("fought", false).forGetter(ActiveRaid::fought),
                Codec.BOOL.optionalFieldOf("succeeded", false).forGetter(ActiveRaid::succeeded)
        ).apply(i, ActiveRaid::new));

        public ActiveRaid {
            ships = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(ships));
        }

        public static ActiveRaid start(ResourceLocation port, ResourceKey<Level> dimension, BlockPos target, BoundingBox area,
                                       long now, List<UUID> voyages) {
            Map<UUID, ShipRaid> ships = new LinkedHashMap<>();
            for (UUID v : voyages) ships.put(v, ShipRaid.APPROACHING);
            return new ActiveRaid(port, dimension, target, area, now, ships, false, false);
        }

        public ActiveRaid with(UUID voyage, ShipRaid ship) {
            Map<UUID, ShipRaid> next = new LinkedHashMap<>(ships);
            next.put(voyage, ship);
            return new ActiveRaid(port, dimension, target, area, started, next, fought, succeeded);
        }

        public ActiveRaid withResult(boolean newFought, boolean newSucceeded) {
            return new ActiveRaid(port, dimension, target, area, started, ships, fought || newFought, succeeded || newSucceeded);
        }

        /** Whether every ship of the raid is done. */
        public boolean done() {
            return ships.values().stream().allMatch(s -> s.phase() == Phase.DONE);
        }
    }

    private static final Codec<Map<ResourceLocation, Long>> DAYS = Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG);
    private static final Codec<Map<ResourceLocation, Integer>> MINUTES = Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT);
    private static final Codec<List<ActiveRaid>> RAIDS = ActiveRaid.CODEC.listOf();

    // Same choice as PortRegistry: vanilla calls the fixer unconditionally, so it must not be null on Fabric.
    private static final SavedData.Factory<RaidData> FACTORY =
            new SavedData.Factory<>(RaidData::new, RaidData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<ResourceLocation, Long> lastRaidDay = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> minutes = new LinkedHashMap<>();
    private final Map<ResourceLocation, ActiveRaid> raids = new LinkedHashMap<>();

    public static RaidData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /** The day of the settlement's last raid, {@link RaidRules#NO_DAY} if never. */
    public long lastRaidDay(ResourceLocation port) {
        return lastRaidDay.getOrDefault(port, RaidRules.NO_DAY);
    }

    public void setLastRaidDay(ResourceLocation port, long day) {
        Long old = lastRaidDay.put(port, day);
        if (old == null || old != day) setDirty();
    }

    /** Minutes a player has been at the settlement without a break. */
    public int minutes(ResourceLocation port) {
        return minutes.getOrDefault(port, 0);
    }

    public void setMinutes(ResourceLocation port, int value) {
        Integer old = value <= 0 ? minutes.remove(port) : minutes.put(port, value);
        if (old == null ? value > 0 : old != value) setDirty();
    }

    /** Settlements with a presence count. */
    public Collection<ResourceLocation> counted() {
        return List.copyOf(minutes.keySet());
    }

    public Optional<ActiveRaid> raid(ResourceLocation port) {
        return Optional.ofNullable(raids.get(port));
    }

    /** The raid voyage {@code voyage} sails in. */
    public Optional<ActiveRaid> raidOf(UUID voyage) {
        for (ActiveRaid r : raids.values()) {
            if (r.ships().containsKey(voyage)) return Optional.of(r);
        }
        return Optional.empty();
    }

    public List<ActiveRaid> raids() {
        return List.copyOf(raids.values());
    }

    public void put(ActiveRaid raid) {
        raids.put(raid.port(), raid);
        setDirty();
    }

    public void remove(ResourceLocation port) {
        if (raids.remove(port) != null) setDirty();
    }

    /** Forgets everything about a settlement (GameTests). */
    public void forget(ResourceLocation port) {
        lastRaidDay.remove(port);
        minutes.remove(port);
        raids.remove(port);
        setDirty();
    }

    public static RaidData load(CompoundTag tag, HolderLookup.Provider registries) {
        RaidData d = new RaidData();
        if (tag.contains("last_raid_day", Tag.TAG_COMPOUND)) {
            DAYS.parse(NbtOps.INSTANCE, tag.get("last_raid_day")).resultOrPartial(e -> Constants.LOG.error("Raid days: {}", e))
                    .ifPresent(d.lastRaidDay::putAll);
        }
        if (tag.contains("minutes", Tag.TAG_COMPOUND)) {
            MINUTES.parse(NbtOps.INSTANCE, tag.get("minutes")).resultOrPartial(e -> Constants.LOG.error("Raid minutes: {}", e))
                    .ifPresent(d.minutes::putAll);
        }
        if (tag.contains("raids", Tag.TAG_LIST)) {
            RAIDS.parse(NbtOps.INSTANCE, tag.get("raids")).resultOrPartial(e -> Constants.LOG.error("Raids under way: {}", e))
                    .ifPresent(l -> l.forEach(r -> d.raids.put(r.port(), r)));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        DAYS.encodeStart(NbtOps.INSTANCE, lastRaidDay).resultOrPartial(e -> Constants.LOG.error("Raid days: {}", e))
                .ifPresent(t -> tag.put("last_raid_day", t));
        MINUTES.encodeStart(NbtOps.INSTANCE, minutes).resultOrPartial(e -> Constants.LOG.error("Raid minutes: {}", e))
                .ifPresent(t -> tag.put("minutes", t));
        RAIDS.encodeStart(NbtOps.INSTANCE, List.copyOf(raids.values())).resultOrPartial(e -> Constants.LOG.error("Raids: {}", e))
                .ifPresent(t -> tag.put("raids", t));
        return tag;
    }
}
