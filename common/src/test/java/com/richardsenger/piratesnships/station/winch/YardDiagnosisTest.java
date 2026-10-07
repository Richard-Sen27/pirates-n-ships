package com.richardsenger.piratesnships.station.winch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.sailing.sail.YardLinker;
import com.richardsenger.piratesnships.sailing.sail.YardLookup;
import com.richardsenger.piratesnships.sailing.sail.YardLookup.Cell;
import com.richardsenger.piratesnships.sailing.sail.YardRow;
import com.richardsenger.piratesnships.sailing.sail.YardRules;
import com.richardsenger.piratesnships.station.winch.YardDiagnosis.Verdict;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Q5: the explanation of rule F5a agrees with the rule and names the first obstacle. */
class YardDiagnosisTest {

    private static final YardRules R = YardRules.DEFAULTS; // gap 2..8, length <= 15

    private static final class World implements YardLookup {
        final Map<List<Integer>, Cell> cells = new HashMap<>();

        @Override
        public Cell at(int x, int y, int z) {
            return cells.getOrDefault(List.of(x, y, z), Cell.AIR);
        }

        World set(int x, int y, int z, Cell c) {
            cells.put(List.of(x, y, z), c);
            return this;
        }

        World yardX(int x0, int y, int length) {
            for (int i = 0; i < length; i++) set(x0 + i, y, 0, Cell.YARD_X);
            return this;
        }

        World mast(int x, int y0, int y1) {
            for (int y = y0; y <= y1; y++) set(x, y, 0, Cell.MAST);
            return this;
        }
    }

    private static YardRow upper(World w, int x, int y) {
        YardRow r = YardLinker.row(w, x, y, 0, R);
        assertNotNull(r, "no yard at " + x + "," + y);
        return r;
    }

    /** The diagnosis says "heads a sail" exactly when the rule links a sail. */
    private static void agrees(World w, YardRow upper, YardDiagnosis.Finding f) {
        assertEquals(YardLinker.sailHeadedBy(w, upper, R) != null, f.headsSail(), "diagnosis and rule disagree: " + f);
    }

    @Test
    void twoYardsSixApartOnALogMastHeadASail() {
        World w = new World().yardX(-4, 17, 9).yardX(-4, 11, 9).mast(0, 12, 16).mast(0, 9, 10);
        YardRow up = upper(w, 0, 17);
        YardDiagnosis.Finding f = YardDiagnosis.explain(w, up, R);
        assertEquals(Verdict.HEADS_SAIL, f.verdict());
        assertEquals(6, f.distance());
        agrees(w, up, f);
        assertEquals(Verdict.NOTHING_BELOW, YardDiagnosis.explain(w, upper(w, 0, 11), R).verdict());
    }

    @Test
    void aPlankInTheMastBlocksTheSail() {
        World w = new World().yardX(-4, 17, 9).yardX(-4, 11, 9).mast(0, 12, 16).set(0, 14, 0, Cell.OTHER);
        YardRow up = upper(w, 0, 17);
        YardDiagnosis.Finding f = YardDiagnosis.explain(w, up, R);
        assertEquals(Verdict.BLOCKED, f.verdict());
        assertEquals(3, f.distance());
        assertEquals(14, f.y());
        agrees(w, up, f);
    }

    @Test
    void aGapWiderThanTheLargestFindsNothing() {
        World w = new World().yardX(-4, 20, 9).yardX(-4, 11, 9).mast(0, 12, 19);
        YardRow up = upper(w, 0, 20);
        YardDiagnosis.Finding f = YardDiagnosis.explain(w, up, R);
        assertEquals(Verdict.NOTHING_BELOW, f.verdict());
        agrees(w, up, f);
    }

    @Test
    void yardsTooCloseOtherAxisOffMiddleAndTooLong() {
        World close = new World().yardX(-1, 12, 3).yardX(-1, 11, 3);
        YardRow up = upper(close, 0, 12);
        assertEquals(Verdict.TOO_CLOSE, YardDiagnosis.explain(close, up, R).verdict());
        agrees(close, up, YardDiagnosis.explain(close, up, R));

        World axis = new World().yardX(-1, 15, 3);
        for (int z = -1; z <= 1; z++) axis.set(0, 11, z, Cell.YARD_Z);
        up = upper(axis, 0, 15);
        assertEquals(Verdict.OTHER_AXIS, YardDiagnosis.explain(axis, up, R).verdict());
        agrees(axis, up, YardDiagnosis.explain(axis, up, R));

        World off = new World().yardX(-1, 15, 3).yardX(-1, 11, 5); // lower middle at x=1
        up = upper(off, 0, 15);
        YardDiagnosis.Finding f = YardDiagnosis.explain(off, up, R);
        assertEquals(Verdict.OFF_MIDDLE, f.verdict());
        agrees(off, up, f);

        World longLower = new World().yardX(-1, 15, 3).yardX(-8, 11, 17);
        up = upper(longLower, 0, 15);
        assertEquals(Verdict.LOWER_TOO_LONG, YardDiagnosis.explain(longLower, up, R).verdict());
        assertEquals(17, YardDiagnosis.runLength(longLower, 0, 11, 0, 64));
        assertEquals(0, YardDiagnosis.runLength(longLower, 0, 13, 0, 64));
        assertTrue(YardDiagnosis.runLength(longLower, 0, 11, 0, 10) <= 10, "the cap holds");
    }
}
