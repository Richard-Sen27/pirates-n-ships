package com.richardsenger.piratesnships.worldsim.captain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * What the captains' voyages remember (BOS2), saved with the overworld ({@code data/pirates_n_ships_captain_voyages.dat}):
 * per island the day of its captain's last chance to put to sea, his stowed state while he is not in the world (at sea
 * as a record, or on his way back to his post), whether he is coming home; per voyage the chase it is on. Whether he is
 * at sea, and on which voyage, is the captain registry's ({@code CaptainEntry.voyage}). Server thread only.
 */
public final class CaptainSeaData extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_captain_voyages";

    /**
     * One island's captain at sea.
     *
     * @param lastChanceDay the day of his last chance to put to sea ({@link CaptainVoyageRules#decide})
     * @param stash         his saved entity state while no copy of him is in the world
     * @param returning     his voyage ended: he appears at his post as soon as it is loaded
     */
    public record Sea(long lastChanceDay, Optional<CompoundTag> stash, boolean returning) {

        public static final Codec<Sea> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("last_chance_day").forGetter(Sea::lastChanceDay),
                CompoundTag.CODEC.optionalFieldOf("stash").forGetter(Sea::stash),
                Codec.BOOL.optionalFieldOf("returning", false).forGetter(Sea::returning)
        ).apply(i, Sea::new));

        public Sea withStash(Optional<CompoundTag> newStash) {
            return new Sea(lastChanceDay, newStash, returning);
        }

        public Sea withReturning(boolean newReturning) {
            return new Sea(lastChanceDay, stash, newReturning);
        }

        public Sea withLastChanceDay(long day) {
            return new Sea(day, stash, returning);
        }
    }

    /**
     * The chase a captain's voyage is on: the quarry, the route it left and where on it, the timers of
     * {@code HuntRules.judge}.
     */
    public record Chase(UUID voyage, UUID target, List<Lane.Point> home, double homeProgress, long lastContact,
                        long surrenderedUntil) {

        public static final Codec<Chase> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("voyage").forGetter(Chase::voyage),
                UUIDUtil.CODEC.fieldOf("target").forGetter(Chase::target),
                Lane.Point.CODEC.listOf().fieldOf("home").forGetter(Chase::home),
                Codec.DOUBLE.fieldOf("home_progress").forGetter(Chase::homeProgress),
                Codec.LONG.fieldOf("last_contact").forGetter(Chase::lastContact),
                Codec.LONG.optionalFieldOf("surrendered_until", 0L).forGetter(Chase::surrenderedUntil)
        ).apply(i, Chase::new));

        public Chase {
            home = List.copyOf(home);
        }

        public Chase withTimers(long newLastContact, long newSurrenderedUntil) {
            return new Chase(voyage, target, home, homeProgress, newLastContact, newSurrenderedUntil);
        }
    }

    private static final Codec<Map<ResourceLocation, Sea>> SEAS_CODEC = Codec.unboundedMap(ResourceLocation.CODEC, Sea.CODEC);
    private static final Codec<List<Chase>> CHASES_CODEC = Chase.CODEC.listOf();

    // Same choice as the other saved data: vanilla calls the fixer unconditionally, so it must not be null on Fabric.
    private static final SavedData.Factory<CaptainSeaData> FACTORY =
            new SavedData.Factory<>(CaptainSeaData::new, CaptainSeaData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<ResourceLocation, Sea> seas = new TreeMap<>();
    private final Map<UUID, Chase> chases = new HashMap<>();

    public static CaptainSeaData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public Optional<Sea> sea(ResourceLocation port) {
        return Optional.ofNullable(seas.get(port));
    }

    /** The island's record, made with {@code today} as the day of the last chance when it has none yet. */
    public Sea seaOrNew(ResourceLocation port, long today) {
        Sea s = seas.get(port);
        if (s != null) return s;
        s = new Sea(today, Optional.empty(), false);
        putSea(port, s);
        return s;
    }

    public Map<ResourceLocation, Sea> seas() {
        return Map.copyOf(seas);
    }

    public void putSea(ResourceLocation port, Sea sea) {
        seas.put(port, sea);
        setDirty();
    }

    public void removeSea(ResourceLocation port) {
        if (seas.remove(port) != null) setDirty();
    }

    public Optional<Chase> chase(UUID voyage) {
        return Optional.ofNullable(chases.get(voyage));
    }

    public void putChase(Chase chase) {
        chases.put(chase.voyage(), chase);
        setDirty();
    }

    public void removeChase(UUID voyage) {
        if (chases.remove(voyage) != null) setDirty();
    }

    public static CaptainSeaData load(CompoundTag tag, HolderLookup.Provider registries) {
        CaptainSeaData d = new CaptainSeaData();
        if (tag.contains("seas", Tag.TAG_COMPOUND)) {
            SEAS_CODEC.parse(NbtOps.INSTANCE, tag.get("seas"))
                    .resultOrPartial(e -> Constants.LOG.error("Could not read the captains' voyages: {}", e))
                    .ifPresent(d.seas::putAll);
        }
        if (tag.contains("chases", Tag.TAG_LIST)) {
            CHASES_CODEC.parse(NbtOps.INSTANCE, tag.get("chases"))
                    .resultOrPartial(e -> Constants.LOG.error("Could not read the captains' chases: {}", e))
                    .ifPresent(l -> l.forEach(c -> d.chases.put(c.voyage(), c)));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        SEAS_CODEC.encodeStart(NbtOps.INSTANCE, seas)
                .resultOrPartial(e -> Constants.LOG.error("Could not save the captains' voyages: {}", e))
                .ifPresent(t -> tag.put("seas", t));
        CHASES_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(chases.values()))
                .resultOrPartial(e -> Constants.LOG.error("Could not save the captains' chases: {}", e))
                .ifPresent(t -> tag.put("chases", t));
        return tag;
    }
}
