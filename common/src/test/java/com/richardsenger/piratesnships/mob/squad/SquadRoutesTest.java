package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.world.outpost.GarrisonPosts;
import com.richardsenger.piratesnships.world.outpost.GarrisonPosts.PlacedPiece;
import com.richardsenger.piratesnships.world.structure.StandAloneOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The squad's patrol route (MOB2): built from the placed pieces in the decided order (court, land gate, sea gate,
 * quay, the east walls, the west walls), turned with the gate, and every spot is a place to stand in its committed
 * ST3 piece.
 */
class SquadRoutesTest {

    private static Path root;

    @BeforeAll
    static void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        root = StandAloneOps.root();
    }

    // the layout of GarrisonPostsTest: gate at (100, 64, 200), sea to the north
    private static final PlacedPiece GATE = new PlacedPiece(GarrisonPosts.FORT_GATE, new BlockPos(100, 64, 200), Rotation.NONE);
    private static final PlacedPiece QUAY = new PlacedPiece("quay", new BlockPos(104, 59, 182), Rotation.NONE);
    private static final PlacedPiece EAST_WALL = new PlacedPiece("wall", new BlockPos(115, 64, 200), Rotation.NONE);
    private static final PlacedPiece EAST_WALL_2 = new PlacedPiece("wall", new BlockPos(122, 64, 200), Rotation.NONE);
    private static final PlacedPiece WEST_WALL = new PlacedPiece("wall", new BlockPos(93, 64, 200), Rotation.NONE);
    private static final PlacedPiece EAST_TOWER = new PlacedPiece("wall_tower", new BlockPos(129, 64, 200), Rotation.NONE);
    private static final PlacedPiece WEST_TOWER = new PlacedPiece("wall_tower", new BlockPos(86, 64, 200), Rotation.NONE);
    private static final PlacedPiece BARRACKS = new PlacedPiece("barracks", new BlockPos(109, 64, 215), Rotation.NONE);

    @Test
    void theRouteGoesCourtGatesQuayThenTheEastAndTheWestWalls() {
        // jigsaw order with the farther east wall before the nearer one: the route still walks outwards
        List<BlockPos> route = SquadRoutes.route(List.of(GATE, QUAY, EAST_WALL_2, WEST_WALL, EAST_WALL, BARRACKS, EAST_TOWER, WEST_TOWER));
        assertEquals(List.of(
                new BlockPos(109, 65, 207),  // the court, a few steps from the officer
                new BlockPos(107, 65, 212),  // the land gate
                new BlockPos(107, 65, 203),  // the sea gate
                new BlockPos(107, 65, 189),  // the quay's deck: (3, 6, 7) of the quay, level with the court
                new BlockPos(111, 69, 203),  // up the stairs onto the gate's east walkway
                new BlockPos(116, 69, 204),  // the nearer east wall
                new BlockPos(123, 69, 204),  // the farther east wall
                new BlockPos(103, 69, 203),  // back along the sea-side walkway to the west
                new BlockPos(98, 69, 204)    // the west wall, at its end toward the gate
        ), route);
        assertEquals(new BlockPos(107, 64, 207), SquadRoutes.anchor(List.of(GATE, QUAY)), "the outpost's anchor: the court centre");
        assertTrue(route.size() >= 3, "a route of at least three waypoints");
        assertEquals(route.size(), new HashSet<>(route).size(), "every waypoint once");
    }

    @Test
    void theRouteTurnsWithTheGate() {
        // sea to the east: the gate turned clockwise; local (x, z) goes to (-z, x) from the origin
        PlacedPiece gate = new PlacedPiece(GarrisonPosts.FORT_GATE, new BlockPos(100, 64, 200), Rotation.CLOCKWISE_90);
        // the gate's east (its local +x) is south now: a wall south of the gate is on the east run
        PlacedPiece south = new PlacedPiece("wall", new BlockPos(100, 64, 215), Rotation.CLOCKWISE_90);
        PlacedPiece north = new PlacedPiece("wall", new BlockPos(100, 64, 193), Rotation.CLOCKWISE_90);
        List<BlockPos> route = SquadRoutes.route(List.of(gate, north, south));
        assertEquals(new BlockPos(100 - 7, 65, 200 + 9), route.get(0), "the court");
        assertEquals(new BlockPos(100 - 12, 65, 200 + 7), route.get(1), "the land gate, to the west (inland)");
        assertEquals(new BlockPos(100 - 3, 65, 200 + 7), route.get(2), "the sea gate, to the east");
        assertEquals(new BlockPos(100 - 3, 69, 200 + 11), route.get(3), "the east walkway is south now");
        assertEquals(GarrisonPosts.toWorld(south, SquadRoutes.WALL_WALK_EAST_RUN), route.get(4), "then the southern wall");
        assertEquals(new BlockPos(100 - 3, 69, 200 + 3), route.get(5), "the west walkway is north now");
        assertEquals(GarrisonPosts.toWorld(north, SquadRoutes.WALL_WALK_WEST_RUN), route.get(6), "then the northern wall");
        assertEquals(7, route.size(), "no quay, no waypoint on it");
    }

    @Test
    void noGateNoRoute() {
        assertEquals(List.of(), SquadRoutes.route(List.of(QUAY, EAST_WALL)));
        assertNull(SquadRoutes.anchor(List.of(QUAY, EAST_WALL)));
    }

    @Test
    void everySpotIsAPlaceToStandInItsPiece() throws IOException {
        Map<String, List<BlockPos>> spots = Map.of(
                GarrisonPosts.FORT_GATE, List.of(SquadRoutes.COURT, SquadRoutes.LAND_GATE, SquadRoutes.SEA_GATE,
                        SquadRoutes.EAST_WALK, SquadRoutes.WEST_WALK),
                "wall", List.of(SquadRoutes.WALL_WALK_EAST_RUN, SquadRoutes.WALL_WALK_WEST_RUN),
                "quay", List.of(SquadRoutes.QUAY_DECK));
        for (Map.Entry<String, List<BlockPos>> e : spots.entrySet()) {
            Map<BlockPos, String> names = new HashMap<>();
            Map<BlockPos, BlockState> blocks = template(e.getKey(), names);
            for (BlockPos p : e.getValue()) {
                String where = e.getKey() + " " + p.toShortString();
                for (BlockPos room : List.of(p, p.above())) {
                    String name = names.getOrDefault(room, "minecraft:air");
                    assertEquals("minecraft:air", name, where + ": " + room.toShortString() + " is free");
                }
                BlockState below = blocks.get(p.below());
                assertTrue(below != null && below.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP),
                        where + " stands on a sturdy block, found " + names.get(p.below()));
            }
        }
    }

    @Test
    void theCourtLiesBesideTheOfficersPost() {
        GarrisonPosts.Assignment officer = GarrisonPosts.plan(List.of(GATE), 0, 1).get(0);
        BlockPos court = SquadRoutes.route(List.of(GATE)).get(0);
        int steps = officer.pos().distManhattan(court);
        // a few steps off: near, yet farther than the arrival radius, so the officer visibly sets off
        assertTrue(steps >= 2 && steps <= 4, "the patrol starts a few steps from the officer's post, got " + steps);
        assertTrue(Math.sqrt(officer.pos().distSqr(court)) > Squad.ARRIVE, "the officer has to walk to the first waypoint");
    }

    private static Map<BlockPos, BlockState> template(String name, Map<BlockPos, String> names) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(root.resolve("common/src/main/resources/data/pirates_n_ships/structure/navy_outpost/"
                + name + ".nbt"), NbtAccounter.unlimitedHeap());
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (Tag t : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag b = (CompoundTag) t;
            ListTag p = b.getList("pos", Tag.TAG_INT);
            BlockPos pos = new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2));
            CompoundTag state = palette.getCompound(b.getInt("state"));
            names.put(pos, state.getString("Name"));
            blocks.put(pos, NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), state));
        }
        return blocks;
    }
}
