package com.richardsenger.piratesnships.worldsim.lane;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The lane cache (WS2), saved with the overworld ({@code data/pirates_n_ships_lanes.dat}): one {@link Lane} per
 * unordered port pair, stored in the direction it was computed, and the game time of the last failed search per pair.
 * {@code version} is {@code world_simulation.lanes.version} at the time of saving; a different version empties the
 * cache ({@link Lanes} checks it). Server thread only.
 */
public final class LaneData extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_lanes";

    /** A failed search of the pair {@code a}–{@code b} at game time {@code tick}. */
    public record Failure(ResourceLocation a, ResourceLocation b, long tick) {
        public static final Codec<Failure> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("a").forGetter(Failure::a),
                ResourceLocation.CODEC.fieldOf("b").forGetter(Failure::b),
                Codec.LONG.fieldOf("tick").forGetter(Failure::tick)
        ).apply(i, Failure::new));
    }

    /** Everything stored, as one codec value. */
    public record Snapshot(int version, List<Lane> lanes, List<Failure> failures) {
        public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("version", 0).forGetter(Snapshot::version),
                Lane.CODEC.listOf().optionalFieldOf("lanes", List.of()).forGetter(Snapshot::lanes),
                Failure.CODEC.listOf().optionalFieldOf("failures", List.of()).forGetter(Snapshot::failures)
        ).apply(i, Snapshot::new));
    }

    private static final SavedData.Factory<LaneData> FACTORY =
            new SavedData.Factory<>(LaneData::new, LaneData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private int version = -1;
    private final Map<String, Lane> lanes = new HashMap<>();
    private final Map<String, Failure> failures = new HashMap<>();

    public static LaneData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /** The key of an unordered pair. */
    public static String key(ResourceLocation a, ResourceLocation b) {
        String x = a.toString(), y = b.toString();
        return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
    }

    public int version() {
        return version;
    }

    /** Empties the cache if it was made with another version, and stamps it with {@code current}. */
    public void checkVersion(int current) {
        if (version == current) return;
        if (!lanes.isEmpty() || !failures.isEmpty()) {
            Constants.LOG.info("Lane cache version {} != {}: dropping {} lanes", version, current, lanes.size());
        }
        lanes.clear();
        failures.clear();
        version = current;
        setDirty();
    }

    /** The cached lane between {@code a} and {@code b}, oriented from {@code a} to {@code b}. */
    public Optional<Lane> lane(ResourceLocation a, ResourceLocation b) {
        Lane l = lanes.get(key(a, b));
        if (l == null) return Optional.empty();
        return Optional.of(l.from().equals(a) ? l : l.reversed());
    }

    public void putLane(Lane lane) {
        String k = key(lane.from(), lane.to());
        lanes.put(k, lane);
        failures.remove(k);
        setDirty();
    }

    public Optional<Failure> failure(ResourceLocation a, ResourceLocation b) {
        return Optional.ofNullable(failures.get(key(a, b)));
    }

    public void putFailure(ResourceLocation a, ResourceLocation b, long tick) {
        failures.put(key(a, b), new Failure(a, b, tick));
        setDirty();
    }

    /** Forgets everything about the pair (lane and failure). */
    public void forget(ResourceLocation a, ResourceLocation b) {
        String k = key(a, b);
        if (lanes.remove(k) != null | failures.remove(k) != null) setDirty();
    }

    /** Forgets every lane touching {@code port} (e.g. a test port removed). */
    public void forgetPort(ResourceLocation port) {
        boolean changed = lanes.values().removeIf(l -> l.from().equals(port) || l.to().equals(port));
        changed |= failures.values().removeIf(f -> f.a().equals(port) || f.b().equals(port));
        if (changed) setDirty();
    }

    public List<Lane> lanes() {
        return List.copyOf(lanes.values());
    }

    public Snapshot snapshot() {
        return new Snapshot(version, new ArrayList<>(lanes.values()), new ArrayList<>(failures.values()));
    }

    void restore(Snapshot s) {
        version = s.version();
        lanes.clear();
        failures.clear();
        for (Lane l : s.lanes()) lanes.put(key(l.from(), l.to()), l);
        for (Failure f : s.failures()) failures.put(key(f.a(), f.b()), f);
    }

    public static LaneData load(CompoundTag tag, HolderLookup.Provider registries) {
        LaneData data = new LaneData();
        if (tag.contains("lanes")) {
            Snapshot.CODEC.parse(NbtOps.INSTANCE, tag.get("lanes"))
                    .resultOrPartial(e -> Constants.LOG.error("Could not read the lane cache: {}", e))
                    .ifPresent(data::restore);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Snapshot.CODEC.encodeStart(NbtOps.INSTANCE, snapshot())
                .resultOrPartial(e -> Constants.LOG.error("Could not save the lane cache: {}", e))
                .ifPresent(t -> tag.put("lanes", t));
        return tag;
    }
}
