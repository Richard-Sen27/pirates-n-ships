package com.richardsenger.piratesnships.law.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NB2: the market notice board's part arithmetic. */
class MarketBoardPartTest {

    private static final BlockPos ANCHOR = new BlockPos(10, 64, -3);

    @Test
    void theViewersRightIsCounterClockwiseFromTheNotices() {
        // notices north: the viewer stands north looking south, their right hand points west
        assertEquals(Direction.WEST, MarketBoardPart.right(Direction.NORTH));
        assertEquals(Direction.EAST, MarketBoardPart.right(Direction.SOUTH));
        assertEquals(Direction.NORTH, MarketBoardPart.right(Direction.EAST));
        assertEquals(Direction.SOUTH, MarketBoardPart.right(Direction.WEST));
    }

    @Test
    void cellsRunRightAndUpFromTheAnchor() {
        List<BlockPos> cells = MarketBoardPart.cells(ANCHOR, Direction.NORTH);
        assertEquals(List.of(ANCHOR, ANCHOR.west(), ANCHOR.above(), ANCHOR.west().above()), cells);
        List<BlockPos> east = MarketBoardPart.cells(ANCHOR, Direction.EAST);
        assertEquals(List.of(ANCHOR, ANCHOR.north(), ANCHOR.above(), ANCHOR.north().above()), east);
    }

    @Test
    void everyPartFindsTheAnchorAndEveryOtherPart() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            List<BlockPos> cells = MarketBoardPart.cells(ANCHOR, facing);
            assertEquals(4, new HashSet<>(cells).size(), "four distinct cells facing " + facing);
            for (MarketBoardPart from : MarketBoardPart.values()) {
                BlockPos at = cells.get(from.ordinal());
                assertEquals(ANCHOR, from.anchor(at, facing), from + " facing " + facing);
                for (MarketBoardPart to : MarketBoardPart.values()) {
                    assertEquals(cells.get(to.ordinal()), from.posOf(to, at, facing), from + " -> " + to + " facing " + facing);
                }
            }
        }
    }

    @Test
    void partnersLieOnTheSidesThatLeadIntoTheBoard() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            List<BlockPos> cells = MarketBoardPart.cells(ANCHOR, facing);
            for (MarketBoardPart p : MarketBoardPart.values()) {
                BlockPos at = cells.get(p.ordinal());
                Set<MarketBoardPart> partners = new HashSet<>();
                for (Direction side : Direction.values()) {
                    MarketBoardPart partner = p.partnerOn(side, facing);
                    if (partner == null) {
                        assertTrue(!cells.contains(at.relative(side)), p + " " + side + " leads out of the board");
                        continue;
                    }
                    partners.add(partner);
                    assertEquals(cells.get(partner.ordinal()), at.relative(side), p + " " + side + " facing " + facing);
                }
                assertEquals(Set.of(p.sidewaysPartner(), p.verticalPartner()), partners, p + " has two partners");
            }
        }
    }

    @Test
    void columnsAndRows() {
        assertEquals(MarketBoardPart.BOTTOM_RIGHT, MarketBoardPart.BOTTOM_LEFT.sidewaysPartner());
        assertEquals(MarketBoardPart.TOP_LEFT, MarketBoardPart.BOTTOM_LEFT.verticalPartner());
        assertEquals(MarketBoardPart.BOTTOM_LEFT, MarketBoardPart.TOP_RIGHT.sidewaysPartner().verticalPartner());
        assertEquals(MarketBoardPart.TOP_RIGHT, MarketBoardPart.at(1, 1));
        assertTrue(MarketBoardPart.BOTTOM_LEFT.isAnchor());
        assertEquals(1, java.util.Arrays.stream(MarketBoardPart.values()).filter(MarketBoardPart::isAnchor).count());
        assertNull(MarketBoardPart.TOP_LEFT.partnerOn(Direction.UP, Direction.NORTH));
        assertEquals("bottom_left", MarketBoardPart.BOTTOM_LEFT.getSerializedName());
    }
}
