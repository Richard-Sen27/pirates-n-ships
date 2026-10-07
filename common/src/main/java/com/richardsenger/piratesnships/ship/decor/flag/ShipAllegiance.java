package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.ship.ShipData;
import com.richardsenger.piratesnships.ship.ShipRegistry;
import com.richardsenger.piratesnships.ship.assembly.ShipRejoin;
import com.richardsenger.piratesnships.ship.assembly.ShipSplits;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The flag a whole ship shows (FL2, docs/design.md §4.7 "In the world"), kept in {@link ShipData#flag()}: the winning
 * flagpole by {@link FlagSelection}. The record is the cache; it is refreshed
 * <ul>
 *   <li>when a flagpole on the ship hoists, strikes, raises, is taken down, broken or set by command
 *       ({@link FlagpoleEvents} calls {@link #onChange} before the other listeners, so they already see the new
 *       allegiance),</li>
 *   <li>on assembly ({@code ShipAssembler}), and for the keeper after a split or a rejoin (its poles may have gone with
 *       a wreck or come with the absorbed piece; {@link #register}).</li>
 * </ul>
 * A wreck ({@link ShipSplits#isWreck}) flies nothing, whatever its poles show. Placing an empty pole changes nothing,
 * so placement needs no refresh. Readers ({@link #of}) never scan the ship: NPC AI asks this every few ticks.
 */
public final class ShipAllegiance {

    private ShipAllegiance() {
    }

    /** Subscribes the split and rejoin refreshes. Called once from the decor module's {@code registerEvents()}. */
    public static void register() {
        ShipSplits.onSplit(e -> refresh(e.level(), e.keeper()));
        ShipRejoin.onRejoin(e -> refresh(e.level(), e.keeper()));
    }

    /** What the ship with {@code shipId} shows (cached; {@link FlagReading#NO_FLAG} for an unknown ship). */
    public static FlagReading of(ServerLevel level, UUID shipId) {
        return ShipRegistry.get(level.getServer()).find(shipId).map(ShipData::flag).orElse(FlagReading.NO_FLAG);
    }

    /** {@link #of} for the ship's body. */
    public static FlagReading of(ShipBody ship) {
        return of(ship.level(), ship.id());
    }

    /** A flagpole changed: refresh the ship it stands on, if any. */
    static void onChange(FlagpoleEvents.FlagChange change) {
        ShipBody ship = SableShips.containing(change.level(), change.pos());
        if (ship != null) refresh(ship);
    }

    /** Re-reads the ship's flagpoles and stores the winning reading. Returns what the ship shows now. */
    public static FlagReading refresh(ServerLevel level, UUID shipId) {
        ShipBody ship = SableShips.byId(level, shipId);
        return ship == null ? of(level, shipId) : refresh(ship);
    }

    /** Re-reads the ship's flagpoles and stores the winning reading. Returns what the ship shows now. */
    public static FlagReading refresh(ShipBody ship) {
        ShipRegistry registry = ShipRegistry.get(ship.level().getServer());
        Optional<ShipData> data = registry.find(ship.id());
        if (data.isEmpty()) return FlagReading.NO_FLAG;
        FlagReading now = ShipSplits.isWreck(ship) ? FlagReading.NO_FLAG : read(ship);
        if (!now.equals(data.get().flag())) registry.put(data.get().withFlag(now));
        return now;
    }

    /** The winning reading of the ship's poles, without storing it. */
    public static FlagReading read(ShipBody ship) {
        List<FlagSelection.PoleReading> readings = new ArrayList<>();
        for (FlagpoleBlockEntity be : poles(ship)) {
            readings.add(new FlagSelection.PoleReading(be.getBlockPos(), be.reading()));
        }
        return FlagSelection.pick(readings);
    }

    /**
     * Every flagpole in the ship's plot. Walks the block entities of the plot's chunks, not every block: the server
     * chunk cache hands out plot chunks for plot chunk positions (Sable {@code mixin/plot/ServerChunkCacheMixin#getChunkNow},
     * l.63), so this is plain vanilla chunk access.
     */
    public static List<FlagpoleBlockEntity> poles(ShipBody ship) {
        List<FlagpoleBlockEntity> out = new ArrayList<>();
        ServerLevel level = ship.level();
        BlockPos[] b = ship.plotBounds();
        for (int cx = b[0].getX() >> 4; cx <= b[1].getX() >> 4; cx++) {
            for (int cz = b[0].getZ() >> 4; cz <= b[1].getZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof FlagpoleBlockEntity pole && !pole.isRemoved() && ship.plotContains(pole.getBlockPos())) {
                        out.add(pole);
                    }
                }
            }
        }
        return out;
    }

    /** The ship a flagpole stands on, or null on land. */
    public static @Nullable ShipBody shipOf(FlagpoleBlockEntity pole) {
        return SableShips.containing(pole);
    }
}
