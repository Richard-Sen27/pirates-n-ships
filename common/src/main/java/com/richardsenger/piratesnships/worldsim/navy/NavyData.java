package com.richardsenger.piratesnships.worldsim.navy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The chases of navy patrols (WS4b), saved with the overworld ({@code data/pirates_n_ships_navy.dat}): per patrol voyage
 * in pursuit, the route it left ({@code home}, {@code homeProgress}) and the chase's timers. The quarry itself is the
 * voyage's {@code pursuit}. Server thread only; {@link Hunting} keeps it in step with the voyages.
 */
public final class NavyData extends SavedData {

    /**
     * One chase.
     *
     * @param voyage           the patrol's voyage
     * @param target           the quarry's ship id
     * @param home             the route the patrol left
     * @param homeProgress     how far along it the patrol was
     * @param started          the tick the chase began
     * @param lastContact      the last tick the patrol was within {@code contact_distance}
     * @param surrenderedUntil the tick the shadowing of a surrendered quarry ends, 0 while it flies its colours
     */
    public record Pursuit(UUID voyage, UUID target, List<Lane.Point> home, double homeProgress, long started, long lastContact,
                          long surrenderedUntil) {

        public static final Codec<Pursuit> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("voyage").forGetter(Pursuit::voyage),
                UUIDUtil.CODEC.fieldOf("target").forGetter(Pursuit::target),
                Lane.Point.CODEC.listOf().fieldOf("home").forGetter(Pursuit::home),
                Codec.DOUBLE.fieldOf("home_progress").forGetter(Pursuit::homeProgress),
                Codec.LONG.fieldOf("started").forGetter(Pursuit::started),
                Codec.LONG.fieldOf("last_contact").forGetter(Pursuit::lastContact),
                Codec.LONG.optionalFieldOf("surrendered_until", 0L).forGetter(Pursuit::surrenderedUntil)
        ).apply(i, Pursuit::new));

        public Pursuit {
            home = List.copyOf(home);
        }

        public Pursuit withTimers(long newLastContact, long newSurrenderedUntil) {
            return new Pursuit(voyage, target, home, homeProgress, started, newLastContact, newSurrenderedUntil);
        }
    }

    public static final String FILE_ID = Constants.MOD_ID + "_navy";
    public static final Codec<List<Pursuit>> LIST_CODEC = Pursuit.CODEC.listOf();

    private static final SavedData.Factory<NavyData> FACTORY =
            new SavedData.Factory<>(NavyData::new, NavyData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Pursuit> pursuits = new LinkedHashMap<>();

    public static NavyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public Optional<Pursuit> get(UUID voyage) {
        return Optional.ofNullable(pursuits.get(voyage));
    }

    public List<Pursuit> all() {
        return List.copyOf(pursuits.values());
    }

    public void put(Pursuit pursuit) {
        if (!pursuit.equals(pursuits.put(pursuit.voyage(), pursuit))) setDirty();
    }

    public Optional<Pursuit> remove(UUID voyage) {
        Pursuit p = pursuits.remove(voyage);
        if (p != null) setDirty();
        return Optional.ofNullable(p);
    }

    public static NavyData load(CompoundTag tag, HolderLookup.Provider registries) {
        NavyData data = new NavyData();
        if (tag.contains("pursuits")) {
            LIST_CODEC.parse(NbtOps.INSTANCE, tag.get("pursuits"))
                    .resultOrPartial(e -> Constants.LOG.error("Could not read the navy pursuits: {}", e))
                    .ifPresent(list -> list.forEach(p -> data.pursuits.put(p.voyage(), p)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        LIST_CODEC.encodeStart(NbtOps.INSTANCE, new ArrayList<>(pursuits.values()))
                .resultOrPartial(e -> Constants.LOG.error("Could not save the navy pursuits: {}", e))
                .ifPresent(t -> tag.put("pursuits", t));
        return tag;
    }
}
