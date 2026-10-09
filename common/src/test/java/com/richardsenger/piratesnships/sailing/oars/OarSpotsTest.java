package com.richardsenger.piratesnships.sailing.oars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.sailing.oars.OarRules.Side;
import com.richardsenger.piratesnships.sailing.oars.OarSpots.Deck;
import com.richardsenger.piratesnships.sailing.oars.OarSpots.Hand;
import com.richardsenger.piratesnships.sailing.oars.OarSpots.Spot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** OAR1: the spots at the rail and how the rowers are spread over them. */
class OarSpotsTest {

    /**
     * A plank box deck: floor y = 0 over x 0..4, z 0..9 (bow +z, so port is +x), the deck cells at y = 1. Extra blocks
     * by cell: railings and bulwarks.
     */
    private static final class Box implements Deck {
        final int maxX;
        final int maxZ;
        final Set<BlockPos> railings = new HashSet<>();
        final Set<BlockPos> bulwarks = new HashSet<>();
        final Set<BlockPos> clutter = new HashSet<>();

        Box(int maxX, int maxZ) {
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        boolean floor(BlockPos p) {
            return p.getY() == 0 && p.getX() >= 0 && p.getX() <= maxX && p.getZ() >= 0 && p.getZ() <= maxZ;
        }

        boolean solid(BlockPos p) {
            return floor(p) || bulwarks.contains(p) || clutter.contains(p);
        }

        @Override
        public boolean standable(BlockPos cell) {
            return !solid(cell) && !railings.contains(cell) && !solid(cell.above()) && floor(cell.below());
        }

        @Override
        public boolean open(BlockPos cell) {
            return !solid(cell) && !railings.contains(cell) && !solid(cell.below());
        }

        @Override
        public boolean railing(BlockPos cell) {
            return railings.contains(cell);
        }

        @Override
        public boolean bulwark(BlockPos cell) {
            return bulwarks.contains(cell) && !solid(cell.above());
        }

        List<BlockPos> deckCells() {
            List<BlockPos> out = new ArrayList<>();
            for (int x = 0; x <= maxX; x++) {
                for (int z = 0; z <= maxZ; z++) out.add(new BlockPos(x, 1, z));
            }
            return out;
        }
    }

    private static final Direction PORT = OarSpots.port(0, 1);

    @Test
    void portIsLeftOfTheBow() {
        assertEquals(Direction.EAST, OarSpots.port(0, 1), "bow south (+z): port is east (+x)");
        assertEquals(Direction.WEST, OarSpots.port(0, -1), "bow north: port is west");
        assertEquals(Direction.NORTH, OarSpots.port(1, 0), "bow east: port is north");
        assertEquals(Direction.SOUTH, OarSpots.port(-1, 0), "bow west: port is south");
        assertEquals(Side.PORT, OarSpots.sideOf(Direction.EAST, Direction.EAST));
        assertEquals(Side.STARBOARD, OarSpots.sideOf(Direction.WEST, Direction.EAST));
        assertEquals(Side.NONE, OarSpots.sideOf(Direction.SOUTH, Direction.EAST), "the bow is no rowing side");
    }

    @Test
    void spotsRunAlongBothRailsAndNeverAtBowOrStern() {
        Box box = new Box(4, 9);
        List<Spot> spots = OarSpots.find(box, box.deckCells(), PORT);
        assertEquals(20, spots.size(), "ten cells along each side");
        for (Spot s : spots) {
            if (s.side() == Side.PORT) {
                assertEquals(4, s.cell().getX(), "port spots on the +x edge");
                assertEquals(Direction.EAST, s.outboard());
            } else {
                assertEquals(Side.STARBOARD, s.side());
                assertEquals(0, s.cell().getX(), "starboard spots on the −x edge");
                assertEquals(Direction.WEST, s.outboard());
            }
        }
        assertEquals(spots, OarSpots.find(box, box.deckCells().reversed(), PORT), "independent of the cell order");
    }

    @Test
    void aRailingCountsAsTheRailAndTheRowerStandsInboardOfIt() {
        Box box = new Box(4, 9);
        for (int z = 0; z <= 9; z++) box.railings.add(new BlockPos(4, 1, z)); // a railing along the port edge cells
        List<Spot> spots = OarSpots.find(box, box.deckCells(), PORT);
        List<Spot> port = spots.stream().filter(s -> s.side() == Side.PORT).toList();
        assertEquals(10, port.size());
        for (Spot s : port) assertEquals(3, s.cell().getX(), "inboard of the railing");
        assertNotNull(OarSpots.spotAt(box, new BlockPos(3, 1, 5), Direction.EAST, PORT));
        assertNull(OarSpots.spotAt(box, new BlockPos(4, 1, 5), Direction.EAST, PORT), "the railing's own cell is no place to stand");
    }

    @Test
    void aBulwarkWithRoomAboveCountsButAWallDoesNot() {
        Box box = new Box(4, 9);
        box.bulwarks.add(new BlockPos(4, 1, 2)); // one block of bulwark on the port edge
        assertNotNull(OarSpots.spotAt(box, new BlockPos(3, 1, 2), Direction.EAST, PORT), "inboard of a one-block bulwark");
        box.bulwarks.add(new BlockPos(4, 2, 2)); // a wall two blocks high: no room for the oar
        assertNull(OarSpots.spotAt(box, new BlockPos(3, 1, 2), Direction.EAST, PORT));
        assertNull(OarSpots.spotAt(box, new BlockPos(2, 1, 2), Direction.EAST, PORT), "mid-deck is no spot");
    }

    @Test
    void clutterOnTheDeckIsNoSpot() {
        Box box = new Box(4, 9);
        box.clutter.add(new BlockPos(4, 1, 3)); // a barrel at the rail
        box.clutter.add(new BlockPos(0, 2, 6)); // a low beam over the starboard rail
        List<Spot> spots = OarSpots.find(box, box.deckCells(), PORT);
        assertFalse(spots.stream().anyMatch(s -> s.cell().equals(new BlockPos(4, 1, 3))), "the barrel's cell");
        assertFalse(spots.stream().anyMatch(s -> s.cell().equals(new BlockPos(0, 1, 6))), "no head room");
        assertEquals(18, spots.size());
    }

    private static Hand hand(int x, int z) {
        return new Hand(UUID.randomUUID(), new Vec3(x + 0.5, 1, z + 0.5));
    }

    @Test
    void rowersAlternateSidesAndTakeTheNearestFreeSpot() {
        Box box = new Box(4, 9);
        List<Spot> spots = OarSpots.find(box, box.deckCells(), PORT);
        // all four stand near the port rail amidships
        List<Hand> hands = List.of(hand(3, 5), hand(3, 4), hand(3, 6), hand(3, 5));
        Map<UUID, Spot> placed = OarSpots.assign(spots, Set.of(), hands, Map.of());
        assertEquals(4, placed.size());
        List<Side> sides = hands.stream().map(h -> placed.get(h.id()).side()).toList();
        assertEquals(List.of(Side.PORT, Side.STARBOARD, Side.PORT, Side.STARBOARD), sides, "alternating, the first to its own side");
        assertEquals(new BlockPos(4, 1, 5), placed.get(hands.get(0).id()).cell(), "the nearest port spot");
        assertEquals(new BlockPos(4, 1, 6), placed.get(hands.get(2).id()).cell(), "the next free port spot near it");
        assertEquals(4, new HashSet<>(placed.values()).size(), "one rower per spot");
    }

    @Test
    void benchRowersCountForTheBalanceAndTakenSpotsAreSkipped() {
        Box box = new Box(4, 9);
        List<Spot> spots = OarSpots.find(box, box.deckCells(), PORT);
        Hand a = hand(3, 5);
        // two benches on port already: the rail rower goes to starboard, though port is nearer
        Map<UUID, Spot> placed = OarSpots.assign(spots, Set.of(), List.of(a), Map.of(Side.PORT, 2));
        assertEquals(Side.STARBOARD, placed.get(a.id()).side());
        // its nearest starboard spot is taken: the next one
        Map<UUID, Spot> again = OarSpots.assign(spots, Set.of(new BlockPos(0, 1, 5)), List.of(a), Map.of(Side.PORT, 2));
        BlockPos cell = again.get(a.id()).cell();
        assertEquals(0, cell.getX());
        assertEquals(1, Math.abs(cell.getZ() - 5), "a neighbour of the taken spot: " + cell);
    }

    @Test
    void aFullSideSendsRowersToTheOtherAndNoSpotLeavesThemOut() {
        Box box = new Box(4, 1); // two cells per side
        List<Spot> spots = OarSpots.find(box, box.deckCells(), PORT);
        assertEquals(4, spots.size());
        Set<BlockPos> portTaken = Set.of(new BlockPos(4, 1, 0), new BlockPos(4, 1, 1));
        Hand a = hand(3, 0), b = hand(3, 1), c = hand(3, 1);
        Map<UUID, Spot> placed = OarSpots.assign(spots, portTaken, List.of(a, b, c), Map.of());
        assertEquals(Side.STARBOARD, placed.get(a.id()).side());
        assertEquals(Side.STARBOARD, placed.get(b.id()).side());
        assertFalse(placed.containsKey(c.id()), "no spot left");
        assertTrue(OarSpots.assign(List.of(), Set.of(), List.of(a), Map.of()).isEmpty());
    }
}
