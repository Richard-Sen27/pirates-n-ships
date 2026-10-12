package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.ChartMarker;
import com.richardsenger.piratesnships.chart.data.MarkerIcon;
import com.richardsenger.piratesnships.chart.data.MarkerList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** CHART2's markers list: nearest first, stable ties, distance in blocks and the compass point (north is -z). */
class MarkerListTest {

    private static ChartMarker m(int id, int x, int z, String name) {
        return new ChartMarker(id, x, z, MarkerIcon.X, name);
    }

    @Test
    void nearestFirstWithStableTies() {
        List<ChartMarker> markers = List.of(m(1, 1000, 0, "far"), m(2, 0, -100, "bravo"), m(3, 100, 0, "Alpha"),
                m(4, -100, 0, "alpha"), m(5, 10, 10, "near"));
        List<MarkerList.Entry> e = MarkerList.entries(markers, 0.5, 0.5);
        assertEquals(List.of(5, 3, 4, 2, 1), e.stream().map(x -> x.marker().id()).toList(),
                "near, then the three at 100 blocks by name (case-insensitive, then id), then far");
        assertEquals(1000, e.get(4).distance());
        assertEquals(14, e.get(0).distance());
    }

    @Test
    void compassPoints() {
        double x = 0.5;
        double z = 0.5;
        assertEquals(MarkerList.Point.N, MarkerList.entries(List.of(m(1, 0, -50, "")), x, z).get(0).point());
        assertEquals(MarkerList.Point.E, MarkerList.entries(List.of(m(1, 50, 0, "")), x, z).get(0).point());
        assertEquals(MarkerList.Point.S, MarkerList.entries(List.of(m(1, 0, 50, "")), x, z).get(0).point());
        assertEquals(MarkerList.Point.W, MarkerList.entries(List.of(m(1, -50, 0, "")), x, z).get(0).point());
        assertEquals(MarkerList.Point.NE, MarkerList.entries(List.of(m(1, 40, -40, "")), x, z).get(0).point());
        assertEquals(MarkerList.Point.SW, MarkerList.entries(List.of(m(1, -40, 40, "")), x, z).get(0).point());
        assertEquals(MarkerList.Point.NW, MarkerList.entries(List.of(m(1, -40, -40, "")), x, z).get(0).point());
        assertEquals(MarkerList.Point.SE, MarkerList.entries(List.of(m(1, 40, 40, "")), x, z).get(0).point());
        MarkerList.Entry here = MarkerList.entries(List.of(m(1, 0, 0, "")), x, z).get(0);
        assertEquals(0, here.distance());
        assertNull(here.point(), "standing on it: no point");
    }

    @Test
    void anEmptyChartHasAnEmptyList() {
        assertEquals(List.of(), MarkerList.entries(List.of(), 0, 0));
    }
}
