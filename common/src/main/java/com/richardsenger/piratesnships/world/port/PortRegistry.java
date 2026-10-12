package com.richardsenger.piratesnships.world.port;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The server's port registry (design.md §10.4): every generated port with its position, box, climate and berths.
 * Saved with the overworld's data ({@code data/pirates_n_ships_ports.dat}) for all dimensions. Server thread only;
 * world generation hands new ports over through {@link PortService#report}.
 *
 * <p>Names (NAME3): {@link #add} gives every new port its proper name from the world seed ({@link PortNames}, unique
 * among the registered ports); a port saved before NAME3 gets one the first time the registry is fetched after a load,
 * in the saved order, by the same rule, and the registry is saved with the names from then on.
 */
public final class PortRegistry extends SavedData {

    public static final String FILE_ID = Constants.MOD_ID + "_ports";

    // Same choice as TradeData: vanilla calls the fixer unconditionally, so it must not be null on Fabric.
    private static final SavedData.Factory<PortRegistry> FACTORY =
            new SavedData.Factory<>(PortRegistry::new, PortRegistry::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private PortIndex index = PortIndex.EMPTY;
    /** The world seed names are drawn from; set on the first {@link #get}. */
    private long seed;
    private boolean seeded;

    public static PortRegistry get(MinecraftServer server) {
        PortRegistry r = server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
        if (!r.seeded) r.seed(server.overworld().getSeed());
        return r;
    }

    /** Sets the seed names come from and names every port that has none yet (ports saved before NAME3). */
    public void seed(long worldSeed) {
        this.seed = worldSeed;
        this.seeded = true;
        if (nameUnnamed()) setDirty();
    }

    /**
     * Names every unnamed port, and renames a later port that repeats an earlier one's name (the first keeps it);
     * named ports keep their names, unnamed ones are named in the saved order. True if any changed.
     */
    boolean nameUnnamed() {
        List<Port> all = index.all();
        Set<String> taken = new HashSet<>();
        Set<ResourceLocation> keep = new HashSet<>();
        for (Port p : all) if (p.named() && taken.add(PortNames.key(p.name()))) keep.add(p.id());
        if (keep.size() == all.size()) return false;
        List<Port> out = new ArrayList<>();
        for (Port p : all) {
            Port named = p;
            if (!keep.contains(p.id())) {
                named = p.withName(PortNames.name(seed, p.kind(), p.centre().getX(), p.centre().getZ(), taken::contains));
                taken.add(PortNames.key(named.name()));
            }
            out.add(named);
        }
        index = PortIndex.of(out);
        return true;
    }

    /** {@code port} with its proper name: its own if it has one no other port uses, else the next from the rule. */
    private Port named(Port port) {
        Set<String> taken = new HashSet<>();
        for (Port p : index.all()) if (!p.id().equals(port.id()) && p.named()) taken.add(PortNames.key(p.name()));
        if (port.named() && !taken.contains(PortNames.key(port.name()))) return port;
        return port.withName(PortNames.name(seed, port.kind(), port.centre().getX(), port.centre().getZ(), taken::contains));
    }

    /** The registered port named {@code name} (case-insensitive). */
    public Optional<Port> byName(String name) {
        String key = PortNames.key(name);
        return index.all().stream().filter(p -> p.named() && PortNames.key(p.name()).equals(key)).findFirst();
    }

    public PortIndex index() {
        return index;
    }

    /** Adds {@code port} with its proper name ({@link #named}); false if a port with that id is already known. */
    public boolean add(Port port) {
        if (index.byId(port.id()).isPresent()) return false;
        PortIndex next = index.with(named(port));
        if (next == index) return false;
        index = next;
        setDirty();
        return true;
    }

    /** Replaces the known port with {@code port}'s id (e.g. its shipwright orders changed); false if unknown. */
    public boolean update(Port port) {
        Optional<Port> old = index.byId(port.id());
        if (old.isEmpty()) return false;
        // an update never drops the name; a new name must still be unique
        Port next0 = port.named() ? named(port) : port.withName(old.get().name());
        PortIndex next = index.replace(next0);
        if (next == index) return false;
        index = next;
        setDirty();
        return true;
    }

    public boolean remove(ResourceLocation id) {
        PortIndex next = index.without(id);
        if (next == index) return false;
        index = next;
        setDirty();
        return true;
    }

    public static PortRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        PortRegistry r = new PortRegistry();
        if (tag.contains("ports", Tag.TAG_LIST)) {
            r.index = PortIndex.CODEC.parse(NbtOps.INSTANCE, tag.get("ports"))
                    .resultOrPartial(e -> Constants.LOG.error("Could not read the port registry: {}", e))
                    .orElse(PortIndex.EMPTY);
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        PortIndex.CODEC.encodeStart(NbtOps.INSTANCE, index)
                .resultOrPartial(e -> Constants.LOG.error("Could not save the port registry: {}", e))
                .ifPresent(t -> tag.put("ports", t));
        return tag;
    }
}
