package com.richardsenger.piratesnships.mob.village;

import com.richardsenger.piratesnships.trade.stall.StallKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Who lives and works in which piece of the seafarer village (LIFE1), pure like {@code HarborMasterPosts}: per piece
 * name its residents (role, workstations, stall), its beds and its seats, all in the piece's local coordinates (y 0 the
 * foundation row, so a ground floor stands at y 1; pieces face north, -z) and turned with the piece when placed
 * ({@link VillagePlan}). The coordinates are read off the BuildSpec sources in {@code art/structures/village/} (a work
 * post is the free block in front of the bench, rack, crate or counter, facing it).
 *
 * <table>
 *   <caption>Residents per piece</caption>
 *   <tr><th>piece</th><th>role</th><th>stations</th></tr>
 *   <tr><td>{@code house_small}, {@code house_tall}</td><td>villager</td><td>the crafting bench</td></tr>
 *   <tr><td>{@code house_garden}</td><td>villager</td><td>the crafting bench, the kitchen garden</td></tr>
 *   <tr><td>{@code house_fisher}</td><td>fishwife</td><td>the drying rack in the yard (then the fish market's counter)</td></tr>
 *   <tr><td>{@code net_loft}</td><td>net mender</td><td>the two nets drying under the loft</td></tr>
 *   <tr><td>{@code warehouse}</td><td>dockhand</td><td>the crates inside (then the pier's cargo)</td></tr>
 *   <tr><td>{@code shipwright}</td><td>shipwright</td><td>the bench, the grindstone</td></tr>
 *   <tr><td>{@code slipway}</td><td>shipwright</td><td>the bench, the hull on the slip</td></tr>
 *   <tr><td>{@code tavern}, {@code inn}</td><td>innkeeper</td><td>behind the bar</td></tr>
 *   <tr><td>{@code lighthouse}</td><td>lamplighter</td><td>the foot of the tower's ladder (and the lamp posts)</td></tr>
 *   <tr><td>{@code fish_market}</td><td>vendor (fish)</td><td>behind the counter</td></tr>
 *   <tr><td>{@code street_square_market}</td><td>two vendors (provisions, chandlery)</td><td>behind each stall's counter</td></tr>
 * </table>
 * The chapel and the well have no resident; the dock head keeps its harbor master ({@code mob.harbor}).
 */
public final class ResidentPosts {

    /** A place to stand (or sit): the block and the way the villager faces there. */
    public record Post(BlockPos pos, Direction facing) {
    }

    /** A bed: its foot block and its {@code facing} (from the foot to the head). */
    public record Bed(BlockPos foot, Direction facing) {
        public BlockPos head() {
            return foot.relative(facing);
        }
    }

    /**
     * One resident of a piece: its role, its workstations in schedule order, the stall it keeps ({@code null} for
     * none) and whether it sleeps in its own piece's bed of the same index (a house, the tavern) or in the nearest free
     * bed of the village.
     */
    public record Slot(VillageRole role, List<Post> work, @Nullable StallKind stall, boolean ownBed) {
    }

    public static final String HOUSE_SMALL = "house_small";
    public static final String HOUSE_TALL = "house_tall";
    public static final String HOUSE_GARDEN = "house_garden";
    public static final String HOUSE_FISHER = "house_fisher";
    public static final String NET_LOFT = "net_loft";
    public static final String WAREHOUSE = "warehouse";
    public static final String SHIPWRIGHT = "shipwright";
    public static final String SLIPWAY = "slipway";
    public static final String TAVERN = "tavern";
    public static final String INN = "inn";
    public static final String LIGHTHOUSE = "lighthouse";
    public static final String FISH_MARKET = "fish_market";
    public static final String MARKET_SQUARE = "street_square_market";
    public static final String WELL_SQUARE = "street_square_well";
    public static final String WELL = "well";
    public static final String PIER = "pier";

    private static Post post(int x, int y, int z, Direction facing) {
        return new Post(new BlockPos(x, y, z), facing);
    }

    private static Bed bed(int x, int y, int z, Direction facing) {
        return new Bed(new BlockPos(x, y, z), facing);
    }

    private static Slot slot(VillageRole role, @Nullable StallKind stall, boolean ownBed, Post... work) {
        return new Slot(role, List.of(work), stall, ownBed);
    }

    private static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

    /** The residents of each piece. */
    public static final Map<String, List<Slot>> RESIDENTS = Map.ofEntries(
            Map.entry(HOUSE_SMALL, List.of(slot(VillageRole.VILLAGER, null, true, post(2, 1, 2, W)))),
            Map.entry(HOUSE_TALL, List.of(slot(VillageRole.VILLAGER, null, true, post(2, 1, 2, W)))),
            Map.entry(HOUSE_GARDEN, List.of(slot(VillageRole.VILLAGER, null, true, post(4, 1, 2, E), post(4, 1, 11, N)))),
            Map.entry(HOUSE_FISHER, List.of(slot(VillageRole.FISHWIFE, null, true, post(9, 1, 4, E)))),
            Map.entry(NET_LOFT, List.of(slot(VillageRole.NET_MENDER, null, false, post(3, 1, 4, E), post(2, 1, 3, N)))),
            Map.entry(WAREHOUSE, List.of(slot(VillageRole.DOCKHAND, null, false, post(3, 1, 2, W), post(8, 1, 7, E)))),
            Map.entry(SHIPWRIGHT, List.of(slot(VillageRole.SHIPWRIGHT, StallKind.SHIPWRIGHT, false, post(2, 1, 7, S), post(8, 1, 5, S)))),
            Map.entry(SLIPWAY, List.of(slot(VillageRole.SHIPWRIGHT, StallKind.SHIPWRIGHT, false, post(8, 1, 11, E), post(4, 1, 6, E)))),
            Map.entry(TAVERN, List.of(slot(VillageRole.INNKEEPER, null, true, post(6, 1, 7, S)))),
            Map.entry(INN, List.of(slot(VillageRole.INNKEEPER, null, true, post(4, 1, 6, N)))),
            Map.entry(LIGHTHOUSE, List.of(slot(VillageRole.LAMPLIGHTER, null, false, post(3, 1, 2, S)))),
            Map.entry(FISH_MARKET, List.of(slot(VillageRole.VENDOR, StallKind.FISH, false, post(4, 1, 3, N)))),
            Map.entry(MARKET_SQUARE, List.of(
                    slot(VillageRole.VENDOR, StallKind.PROVISIONS, false, post(4, 1, 7, E)),
                    slot(VillageRole.VENDOR, StallKind.CHANDLERY, false, post(10, 1, 8, W)))));

    /** The beds of each piece (foot, foot-to-head facing). */
    public static final Map<String, List<Bed>> BEDS = Map.of(
            HOUSE_SMALL, List.of(bed(5, 1, 5, S)),
            HOUSE_TALL, List.of(bed(3, 5, 5, S), bed(5, 5, 5, S)),
            HOUSE_GARDEN, List.of(bed(5, 1, 4, S)),
            HOUSE_FISHER, List.of(bed(1, 1, 4, S)),
            TAVERN, List.of(bed(9, 6, 3, N), bed(9, 6, 7, S)),
            INN, List.of(bed(4, 5, 5, S), bed(6, 5, 5, S)));

    /**
     * The seats of each piece (a stair seat, and the way its sitter faces: away from the stair's back): the tavern's and
     * the inn's chairs at their tables, the benches of the squares and the well, the chair of each house.
     */
    public static final Map<String, List<Post>> SEATS = Map.ofEntries(
            Map.entry(TAVERN, List.of(post(2, 1, 3, E), post(4, 1, 3, W), post(6, 1, 3, E), post(8, 1, 3, W),
                    post(5, 1, 5, S), post(8, 1, 5, S), post(1, 1, 8, S))),
            Map.entry(INN, List.of(post(6, 1, 2, N), post(3, 1, 3, W), post(7, 1, 3, W))),
            Map.entry(HOUSE_SMALL, List.of(post(2, 1, 4, W))),
            Map.entry(HOUSE_TALL, List.of(post(2, 1, 4, W))),
            Map.entry(HOUSE_GARDEN, List.of(post(4, 1, 3, E))),
            Map.entry(HOUSE_FISHER, List.of(post(2, 1, 3, W))),
            Map.entry(WELL_SQUARE, List.of(post(7, 1, 5, S), post(4, 1, 8, E), post(10, 1, 8, W))),
            Map.entry(MARKET_SQUARE, List.of(post(10, 1, 12, N), post(11, 1, 12, N))),
            Map.entry(WELL, List.of(post(0, 1, 3, E))));

    /** The fishwife's place at the fish market's counter, beside its vendor. */
    public static final Post FISH_MARKET_HELPER = post(2, 1, 3, N);

    /** The dockhand's place on the pier: on the deck at its landward end, at the barrels waiting to be loaded. */
    public static final List<Post> PIER_POSTS = List.of(post(2, 6, 16, W), post(4, 6, 16, E));

    /**
     * The free 2×2 spot of the market square for the market notice board (NB2): x 3..4, z 11..12, south of the
     * provisions stall; paved and open above. LIFE1 places nothing there.
     */
    public static final BlockPos NOTICE_BOARD_SPOT_MIN = new BlockPos(3, 1, 11);
    public static final BlockPos NOTICE_BOARD_SPOT_MAX = new BlockPos(4, 3, 12);

    private ResidentPosts() {
    }

    /** The residents of piece {@code name} (none for a piece nobody lives in). */
    public static List<Slot> residentsOf(String name) {
        return RESIDENTS.getOrDefault(name, List.of());
    }

    /** The roles of the residents of piece {@code name}, in slot order: the building picks the outfit. */
    public static List<VillageRole> rolesOf(String name) {
        return residentsOf(name).stream().map(Slot::role).toList();
    }

    public static List<Bed> bedsOf(String name) {
        return BEDS.getOrDefault(name, List.of());
    }

    public static List<Post> seatsOf(String name) {
        return SEATS.getOrDefault(name, List.of());
    }
}
