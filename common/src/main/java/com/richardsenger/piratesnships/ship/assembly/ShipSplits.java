package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.SableSplits;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Rules for a ship that Sable splits (RS1): when a ship loses the only block that joins two parts, Sable cuts the loose
 * part off into its own body ({@link SableSplits}). Here every piece gets its role ({@link SplitRules}):
 * <ul>
 *   <li>The <b>keeper</b> (the piece with the helm, else the larger) stays the ship: its {@link ShipData} record (name,
 *       owner, crew list, flag) and Sable name. Ship ids are sub-level UUIDs, so if Sable left the keeper's blocks in the
 *       new body, the identity <b>moves</b> to that body's id ({@link SplitEvent#keeper()} differs from
 *       {@link SplitEvent#parent()}). {@link ShipAssembler} hands the helm to Sable first, which makes the helm Sable's
 *       heat-map root, so on a ship assembled in this session the helm side stays in the original body and keeps the id;
 *       after a reload Sable's root is wherever its chunk scan starts, so a move is possible.</li>
 *   <li>Every other piece becomes a <b>wreck</b>: its own record without a name, {@code wreck = true}, the
 *       {@code origin} of the line, the original's name as {@code wreck_of}. Sails on a wreck give no force (checked in
 *       {@code SailingRuntimes}); crew at its stations are released where they stand (on its deck,
 *       {@code CrewStations} via {@link #relocate}); grappling hooks follow the block they bit into
 *       ({@code GrappleService}).</li>
 *   <li>A loose piece smaller than {@code assembly.split.wreck_min_blocks} breaks up into items one tick later
 *       (each block destroyed with drops, as a cannonball does), then its body is removed.</li>
 * </ul>
 *
 * <p>Persisted in the ship pointer of the sub-level's user data ({@link ShipAssembler#USER_DATA_KEY}):
 * {@code origin} (UUID, absent on a ship that never split: its origin is its own id), {@code wreck} (boolean) and
 * {@code wreck_of} (the name the line had). RS2 (rejoining) reads them through {@link #lineage}.
 */
public final class ShipSplits {

    public static final String TAG_ORIGIN = "origin";
    public static final String TAG_WRECK = "wreck";
    public static final String TAG_WRECK_OF = "wreck_of";

    /** How long {@link #relocate} remembers a split, in ticks (crew and other late readers catch up in that time). */
    static final int RELOCATION_MEMORY_TICKS = 6000;

    /**
     * A ship's place in its line.
     *
     * @param origin the first ship of the line (the ship's own id if it never split off anything's piece)
     * @param wreck  true for a loose piece that is not the ship any more
     * @param wreckOf the name the line had when this piece broke off (empty if unnamed or not a wreck)
     */
    public record Lineage(UUID origin, boolean wreck, String wreckOf) { }

    /** Where a station, hook or other plot position of a split ship is now. */
    public record Relocation(UUID ship, BlockPos pos, boolean keeper, boolean dropped) {
        public boolean moved(UUID oldShip, BlockPos oldPos) {
            return !ship.equals(oldShip) || !pos.equals(oldPos);
        }
    }

    /**
     * One processed split.
     *
     * @param parent  the ship that split (the original sub-level, which still exists unless it was dropped)
     * @param keeper  the piece that is the ship now (== parent unless the identity moved)
     * @param pieces  the pieces Sable cut off the parent (not the parent's remainder)
     * @param wrecks  pieces (the parent included) that became wrecks
     * @param dropped pieces (the parent included) that break up into items
     */
    public record SplitEvent(ServerLevel level, UUID parent, UUID keeper, List<SableSplits.Piece> pieces, Set<UUID> wrecks,
                             Set<UUID> dropped, Map<Long, SableSplits.Piece> movedIndex) {

        /** Where {@code plotPos} of {@code ship} is after this split ({@code ship} must be the parent). */
        public Relocation relocate(BlockPos plotPos) {
            SableSplits.Piece p = movedIndex.get(plotPos.asLong());
            UUID id = p == null ? parent : p.id();
            BlockPos pos = p == null ? plotPos.immutable() : p.moved(plotPos);
            return new Relocation(id, pos, id.equals(keeper), dropped.contains(id));
        }
    }

    @FunctionalInterface
    public interface SplitListener {
        void onSplit(SplitEvent event);
    }

    private record Remembered(SplitEvent event, long until) { }

    private record Drop(UUID ship, long due) { }

    private static final List<SplitListener> LISTENERS = new ArrayList<>();
    private static final Map<ServerLevel, Map<UUID, Remembered>> RECENT = new IdentityHashMap<>();
    private static final Map<ServerLevel, List<Drop>> DROPS = new IdentityHashMap<>();
    private static final Map<UUID, Boolean> WRECK_CACHE = new HashMap<>();
    private static final Map<UUID, Long> WRECK_LAST_SEEN = new HashMap<>();
    /** True while we hold Sable's splitting off because {@code assembly.split.enabled} is false. */
    private static boolean sableSplittingHeldOff;

    private ShipSplits() {
    }

    /** Hooks into Sable and the level tick. Called once from {@code AssemblyModule#registerEvents}. */
    static void register() {
        SableSplits.onSplit(ShipSplits::process);
        SableShips.onShipRemoved((level, id, destroyed) -> {
            WRECK_CACHE.remove(id);
            WRECK_LAST_SEEN.remove(id);
        });
    }

    /** Listens to every processed split (server thread). */
    public static synchronized void onSplit(SplitListener listener) {
        LISTENERS.add(listener);
    }

    // ---------------------------------------------------------------- queries (RS2, sailing, crew)

    /** The line of {@code ship}: origin, wreck flag and the name of the line. */
    public static Lineage lineage(ShipBody ship) {
        CompoundTag pointer = ship.userData(ShipAssembler.USER_DATA_KEY);
        UUID origin = pointer.hasUUID(TAG_ORIGIN) ? pointer.getUUID(TAG_ORIGIN) : ship.id();
        return new Lineage(origin, pointer.getBoolean(TAG_WRECK), pointer.getString(TAG_WRECK_OF));
    }

    /** True for a loose piece that is not a ship any more (cached; cleared on removal and when a split changes it). */
    public static boolean isWreck(ShipBody ship) {
        Boolean cached = WRECK_CACHE.get(ship.id());
        if (cached == null) {
            cached = ship.userData(ShipAssembler.USER_DATA_KEY).getBoolean(TAG_WRECK);
            WRECK_CACHE.put(ship.id(), cached);
        }
        return cached;
    }

    /**
     * Where {@code plotPos} on {@code ship} is now if {@code ship} split during the last
     * {@link #RELOCATION_MEMORY_TICKS} ticks, else null (nothing happened to it).
     */
    public static @Nullable Relocation relocate(ServerLevel level, UUID ship, BlockPos plotPos) {
        Map<UUID, Remembered> m = RECENT.get(level);
        Remembered r = m == null ? null : m.get(ship);
        return r == null ? null : r.event().relocate(plotPos);
    }

    // ---------------------------------------------------------------- processing

    /**
     * Applies the split rules to one batch from Sable: {@code pieces} were cut off {@code parentId}. Public for the
     * GameTests, which also feed it batches of real bodies to cover the identity move deterministically.
     */
    public static @Nullable SplitEvent process(ServerLevel level, UUID parentId, List<SableSplits.Piece> pieces) {
        ShipRegistry registry = ShipRegistry.get(level.getServer());
        Optional<ShipData> parentData = registry.find(parentId);
        if (parentData.isEmpty() || pieces.isEmpty()) {
            return null; // not one of our ships (a plain Sable sub-level), or nothing to do
        }
        ShipBody parent = SableShips.byId(level, parentId);
        List<SplitRules.Piece> rulePieces = new ArrayList<>();
        if (parent != null) {
            List<BlockPos> blocks = parent.plotBlocks();
            if (!blocks.isEmpty()) {
                rulePieces.add(new SplitRules.Piece(parentId, blocks.size(), helms(level, blocks), true));
            }
        }
        Map<Long, SableSplits.Piece> movedIndex = new HashMap<>();
        for (SableSplits.Piece p : pieces) {
            List<BlockPos> now = new ArrayList<>(p.oldPlotBlocks().size());
            for (BlockPos old : p.oldPlotBlocks()) {
                now.add(p.moved(old));
                movedIndex.put(old.asLong(), p);
            }
            rulePieces.add(new SplitRules.Piece(p.id(), p.oldPlotBlocks().size(), helms(level, now), false));
        }
        UUID keeper = SplitRules.keeper(rulePieces);

        CompoundTag parentPointer = parent != null ? parent.userData(ShipAssembler.USER_DATA_KEY) : new CompoundTag();
        UUID origin = SplitRules.origin(parentPointer.hasUUID(TAG_ORIGIN) ? parentPointer.getUUID(TAG_ORIGIN) : null, parentId);
        boolean lineIsWreck = parentPointer.getBoolean(TAG_WRECK);
        ShipData data = parentData.get();
        String lineName = lineIsWreck ? parentPointer.getString(TAG_WRECK_OF) : data.name();
        int minBlocks = AssemblyConfig.WRECK_MIN_BLOCKS.get();

        if (!keeper.equals(parentId)) {
            // The identity moves to the new body: its id becomes the ship's id. The parent gets a wreck record below,
            // or none if it is dropped or has no blocks left.
            registry.remove(parentId);
            registry.put(new ShipData(keeper, data.name(), data.owner(), data.crew(), data.flag(), data.dimension()));
            ShipBody body = SableShips.byId(level, keeper);
            if (body != null) {
                writePointer(body, keeper, origin, lineIsWreck, lineIsWreck ? lineName : "");
                body.setName(sableName(lineIsWreck, data.name(), lineName));
            }
        }
        Set<UUID> wrecks = new LinkedHashSet<>();
        Set<UUID> dropped = new LinkedHashSet<>();
        for (SplitRules.Piece p : rulePieces) {
            if (p.id().equals(keeper)) {
                continue;
            }
            ShipBody body = SableShips.byId(level, p.id());
            if (body == null) {
                continue;
            }
            if (SplitRules.drops(p, false, minBlocks)) {
                dropped.add(p.id());
                registry.remove(p.id());
                DROPS.computeIfAbsent(level, l -> new ArrayList<>()).add(new Drop(p.id(), level.getGameTime() + 1));
                continue;
            }
            wrecks.add(p.id());
            registry.put(new ShipData(p.id(), "", data.owner(), List.of(), "", data.dimension()));
            writePointer(body, p.id(), origin, true, lineName);
            body.setName(sableName(true, "", lineName));
        }
        WRECK_CACHE.remove(parentId);
        for (SableSplits.Piece p : pieces) {
            WRECK_CACHE.remove(p.id());
        }

        SplitEvent event = new SplitEvent(level, parentId, keeper, List.copyOf(pieces), Set.copyOf(wrecks), Set.copyOf(dropped),
                Map.copyOf(movedIndex));
        RECENT.computeIfAbsent(level, l -> new HashMap<>()).put(parentId, new Remembered(event, level.getGameTime() + RELOCATION_MEMORY_TICKS));
        Constants.LOG.debug("Ship {} split: keeper {}, wrecks {}, dropped {}", parentId, keeper, wrecks, dropped);
        for (SplitListener l : List.copyOf(LISTENERS)) {
            l.onSplit(event);
        }
        return event;
    }

    private static int helms(ServerLevel level, List<BlockPos> plotBlocks) {
        int n = 0;
        for (BlockPos p : plotBlocks) {
            if (level.getBlockState(p).getBlock() instanceof HelmBlock) {
                n++;
            }
        }
        return n;
    }

    private static void writePointer(ShipBody body, UUID id, UUID origin, boolean wreck, String wreckOf) {
        CompoundTag pointer = body.userData(ShipAssembler.USER_DATA_KEY);
        pointer.putUUID("ship", id);
        if (!pointer.contains("version")) {
            pointer.putInt("version", 1);
        }
        pointer.putUUID(TAG_ORIGIN, origin);
        pointer.putBoolean(TAG_WRECK, wreck);
        if (wreck && !wreckOf.isEmpty()) {
            pointer.putString(TAG_WRECK_OF, wreckOf);
        } else {
            pointer.remove(TAG_WRECK_OF);
        }
        body.setUserData(ShipAssembler.USER_DATA_KEY, pointer);
        WRECK_CACHE.put(id, wreck);
    }

    /** The name Sable shows for a piece in its own commands. */
    private static @Nullable String sableName(boolean wreck, String name, String lineName) {
        if (wreck) {
            return lineName.isEmpty() ? "Wreck" : "Wreck of " + lineName;
        }
        return name.isEmpty() ? null : name;
    }

    // ---------------------------------------------------------------- ticking

    /** Level tick end: tiny pieces break up, forgotten wrecks go, old relocations expire. */
    static void onLevelTick(ServerLevel level) {
        long now = level.getGameTime();
        List<Drop> drops = DROPS.get(level);
        if (drops != null && !drops.isEmpty()) {
            List<Drop> due = new ArrayList<>();
            drops.removeIf(d -> d.due() <= now && due.add(d));
            for (Drop d : due) {
                breakUp(level, d.ship());
            }
        }
        Map<UUID, Remembered> recent = RECENT.get(level);
        if (recent != null && now % 100 == 0) {
            recent.values().removeIf(r -> r.until() <= now);
        }
        int persist = AssemblyConfig.WRECK_PERSIST_TICKS.get();
        if (persist > 0 && now % 20 == 0) {
            expireWrecks(level, now, persist);
        }
    }

    /** Server tick end: holds Sable's splitting off while {@code assembly.split.enabled} is false. */
    static void onServerTick() {
        syncSableSplitting();
    }

    /** Mirrors {@code assembly.split.enabled} into Sable's {@code sub_level_splitting} (only ever switching it off). */
    public static void syncSableSplitting() {
        boolean want = AssemblyConfig.SPLIT_ENABLED.get();
        if (!want && SableSplits.sableSplitting()) {
            if (SableSplits.setSableSplitting(false)) {
                sableSplittingHeldOff = true;
            }
        } else if (want && sableSplittingHeldOff) {
            SableSplits.setSableSplitting(true);
            sableSplittingHeldOff = false;
        }
    }

    static void onServerStopped() {
        if (sableSplittingHeldOff) {
            SableSplits.setSableSplitting(true); // the config value lives on in memory into the next world
            sableSplittingHeldOff = false;
        }
        RECENT.clear();
        DROPS.clear();
        WRECK_CACHE.clear();
        WRECK_LAST_SEEN.clear();
    }

    /** Destroys every block of a tiny piece with drops (Sable kicks the items to the blocks' world positions). */
    private static void breakUp(ServerLevel level, UUID id) {
        ShipBody body = SableShips.byId(level, id);
        if (body == null) {
            return;
        }
        for (BlockPos p : body.plotBlocks()) {
            level.destroyBlock(p, true);
        }
        if (!body.isRemoved()) {
            SableShips.remove(body);
        }
        ShipRegistry.get(level.getServer()).remove(id);
    }

    private static void expireWrecks(ServerLevel level, long now, int persist) {
        Set<UUID> seen = new HashSet<>();
        for (ShipBody ship : SableShips.all(level)) {
            if (ship.isRemoved() || !isWreck(ship)) {
                continue;
            }
            seen.add(ship.id());
            Long last = WRECK_LAST_SEEN.get(ship.id());
            if (last == null || !ship.trackingPlayers().isEmpty()) {
                WRECK_LAST_SEEN.put(ship.id(), now);
            } else if (now - last >= persist) {
                // TODO(RS2 or later): let it sink first (design.md §4.5); removal frees the plot, its blocks are lost.
                WRECK_LAST_SEEN.remove(ship.id());
                ShipRegistry.get(level.getServer()).remove(ship.id());
                SableShips.remove(ship);
            }
        }
        WRECK_LAST_SEEN.keySet().removeIf(id -> !seen.contains(id) && SableShips.byId(level, id) == null);
    }
}
