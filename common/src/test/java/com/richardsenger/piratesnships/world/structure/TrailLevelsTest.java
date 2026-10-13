package com.richardsenger.piratesnships.world.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** STR8: the rows {@link TrailLevels} gives a port's trail pieces and what hangs from them. */
class TrailLevelsTest {

    private static TrailLevels.Node start(int row) {
        return new TrailLevels.Node(TrailLevels.Kind.START, -1, row, row);
    }

    private static TrailLevels.Node trail(int parent, int planned, int natural) {
        return new TrailLevels.Node(TrailLevels.Kind.TRAIL, parent, planned, natural);
    }

    private static TrailLevels.Node other(int parent, int row) {
        return new TrailLevels.Node(TrailLevels.Kind.OTHER, parent, row, row);
    }

    @Test
    void aClimbingTrailStepsAtMostOneBlockPerPiece() {
        // the ground rises 2 per piece inland of a dock head at row 64
        List<TrailLevels.Node> nodes = List.of(start(64), trail(0, 64, 66), trail(1, 66, 68), trail(2, 68, 69), trail(3, 69, 69));
        TrailLevels.Result r = TrailLevels.solve(nodes);
        int[] rows = new int[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) rows[i] = r.levelled(nodes.get(i), i);
        assertArrayEquals(new int[] {64, 65, 66, 67, 68}, rows);
        for (int i = 1; i < rows.length; i++) assertTrue(Math.abs(rows[i] - rows[i - 1]) <= TrailLevels.MAX_STEP);
        assertEquals(1, r.delta()[1], "planned at the joint's surface (64), laid at 65");
    }

    @Test
    void aBumpyFlatTrailLiesLevel() {
        List<TrailLevels.Node> nodes = List.of(start(64), trail(0, 63, 64), trail(1, 65, 64), trail(2, 62, 64), trail(3, 64, 64));
        TrailLevels.Result r = TrailLevels.solve(nodes);
        for (int i = 1; i < nodes.size(); i++) assertEquals(64, r.levelled(nodes.get(i), i), "piece " + i);
    }

    @Test
    void aTrailFarOffItsGroundIsDroppedWithWhatHangsFromIt() {
        // a cliff: the third piece's ground is 7 above the second piece's row
        List<TrailLevels.Node> nodes = List.of(start(64), trail(0, 64, 64), trail(1, 64, 71), other(2, 71), trail(2, 71, 72),
                other(1, 64));
        TrailLevels.Result r = TrailLevels.solve(nodes);
        assertArrayEquals(new boolean[] {false, false, true, true, true, false}, r.dropped());
    }

    @Test
    void str9KeepsATrailUpToTheStepLimitAsARamp() {
        // a hillside: the ground under each piece rises 3 rows, the trail one per piece, so it falls behind by 2 a piece
        List<TrailLevels.Node> nodes = List.of(start(64), trail(0, 64, 66), trail(1, 66, 69), trail(2, 69, 72), trail(3, 72, 75),
                trail(4, 75, 78), other(5, 78));
        TrailLevels.Result str8 = TrailLevels.solve(nodes);
        assertArrayEquals(new boolean[] {false, false, false, true, true, true, true}, str8.dropped(), "STR8: dropped 5 rows off");
        TrailLevels.Result str9 = TrailLevels.solve(nodes, TrailLevels.DEFAULT_MAX_STEP_ROWS);
        assertArrayEquals(new boolean[] {false, false, false, false, false, true, true}, str9.dropped(),
                "STR9: kept up to 8 rows off (the fifth piece lies 9 under its ground)");
        int[] rows = new int[5];
        for (int i = 0; i < 5; i++) rows[i] = str9.levelled(nodes.get(i), i);
        assertArrayEquals(new int[] {64, 65, 66, 67, 68}, rows, "each piece one row above the one before it");
    }

    @Test
    void aBuildingSitsOnItsTrailsRow() {
        // the trail is laid one above its joint's surface; the hut's connector row (its joint's surface) was 66
        List<TrailLevels.Node> nodes = List.of(start(64), trail(0, 64, 65), other(1, 66), other(2, 70));
        TrailLevels.Result r = TrailLevels.solve(nodes);
        assertEquals(65, r.levelled(nodes.get(1), 1));
        assertEquals(-1, r.delta()[2], "the hut moves onto the trail's row");
        assertEquals(-1, r.delta()[3], "what hangs from the hut moves with it");
        assertFalse(r.dropped()[2]);
    }

    @Test
    void piecesHangingFromTheStartOrTheChainDoNotMove() {
        List<TrailLevels.Node> nodes = List.of(start(64), other(0, 59), other(1, 64), trail(2, 70, 70));
        TrailLevels.Result r = TrailLevels.solve(nodes);
        assertArrayEquals(new int[] {0, 0, 0, 0}, r.delta(), "the pier, a wall and a trail off a wall keep their rows");
    }
}
