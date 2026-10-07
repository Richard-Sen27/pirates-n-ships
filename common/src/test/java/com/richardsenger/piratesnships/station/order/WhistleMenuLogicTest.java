package com.richardsenger.piratesnships.station.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.station.pump.PumpOrder;
import com.richardsenger.piratesnships.station.winch.SailOrder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The pure parts of the whistle's radial menu: sector math, the entry list and the order payload codec. */
class WhistleMenuLogicTest {

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ------------------------------------------------------------------ sector math

    @Test
    void anglesAreClockwiseFromUp() {
        assertEquals(0, RadialLayout.angleOf(0, -10), 1e-9);
        assertEquals(Math.PI / 2, RadialLayout.angleOf(10, 0), 1e-9);
        assertEquals(Math.PI, RadialLayout.angleOf(0, 10), 1e-9);
        assertEquals(3 * Math.PI / 2, RadialLayout.angleOf(-10, 0), 1e-9);
        double a = 1.234;
        assertEquals(a, RadialLayout.angleOf(RadialLayout.x(a, 5), RadialLayout.y(a, 5)), 1e-9);
    }

    @Test
    void fourEntriesSitTopRightBottomLeft() {
        RadialLayout l = new RadialLayout(4, 20, 50);
        assertEquals(0, l.sectorAt(0, -30));
        assertEquals(1, l.sectorAt(30, 0));
        assertEquals(2, l.sectorAt(0, 30));
        assertEquals(3, l.sectorAt(-30, 0));
        // sector 0 is centered at the top: slightly left of up is still sector 0
        assertEquals(0, l.sectorAt(-5, -30));
        assertEquals(3, l.sectorAt(-30, -29)); // just past the diagonal, towards the left
        assertEquals(0, l.sectorAt(-29, -30));
    }

    @Test
    void everyAngleHitsTheSectorItIsCenteredIn() {
        for (int n = 1; n <= 12; n++) {
            RadialLayout l = new RadialLayout(n, 10, 40);
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < n; i++) {
                double c = l.centerAngle(i);
                assertEquals(i, l.sectorAt(RadialLayout.x(c, 25), RadialLayout.y(c, 25)), "n=" + n + " i=" + i);
                // just inside both edges
                assertEquals(i, l.sectorOfAngle(l.startAngle(i) + 1e-6), "n=" + n + " start " + i);
                assertEquals(i, l.sectorOfAngle(l.startAngle(i) + l.span() - 1e-6), "n=" + n + " end " + i);
            }
            for (int k = 0; k < 720; k++) {
                int s = l.sectorOfAngle(k * Math.PI / 360);
                assertTrue(s >= 0 && s < n);
                seen.add(s);
            }
            assertEquals(n, seen.size(), "every sector reachable for n=" + n);
        }
    }

    @Test
    void deadZoneInTheCenterSelectsNothing() {
        RadialLayout l = new RadialLayout(4, 20, 50);
        assertEquals(-1, l.sectorAt(0, 0));
        assertEquals(-1, l.sectorAt(10, -10));
        assertEquals(-1, l.sectorAt(0, -19.9));
        assertEquals(0, l.sectorAt(0, -20));
        // beyond the ring the direction still counts
        assertEquals(1, l.sectorAt(500, 0));
    }

    @Test
    void negativeAndLargeAnglesWrap() {
        RadialLayout l = new RadialLayout(4, 20, 50);
        assertEquals(3, l.sectorOfAngle(-Math.PI / 2));
        assertEquals(1, l.sectorOfAngle(2 * RadialLayout.TAU + Math.PI / 2));
    }

    @Test
    void windowLayoutFitsEveryGuiScale() {
        // a 1920×1080 window at GUI scale 1..4, and a small 854×480 one
        int[][] windows = {{1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {854, 480}, {427, 240}, {214, 120}};
        for (int[] w : windows) {
            RadialLayout l = RadialLayout.forWindow(4, w[0], w[1], 1.0);
            assertTrue(l.outer() <= Math.min(w[0], w[1]) / 2.0, "ring leaves the window " + w[0] + "x" + w[1]);
            assertTrue(l.outer() <= RadialLayout.MAX_OUTER, "ring too big at " + w[0] + "x" + w[1]);
            assertTrue(l.inner() > 0 && l.inner() < l.outer());
            assertTrue(l.outer() - l.inner() >= 16 || l.outer() < RadialLayout.MIN_OUTER, "no room for an icon at " + w[0] + "x" + w[1]);
        }
        assertEquals(RadialLayout.MAX_OUTER, RadialLayout.forWindow(4, 1920, 1080, 1.0).outer(), 1e-9);
        assertEquals(270 * RadialLayout.OUTER_SHARE, RadialLayout.forWindow(4, 480, 270, 1.0).outer(), 1e-9);
    }

    @Test
    void layoutRejectsNonsense() {
        assertThrows(IllegalArgumentException.class, () -> new RadialLayout(0, 10, 20));
        assertThrows(IllegalArgumentException.class, () -> new RadialLayout(3, 20, 20));
    }

    // ------------------------------------------------------------------ entries

    @Test
    void entriesAreTheSailOrdersThenPumpThenRelease() {
        List<WhistleOrder> e = WhistleOrder.entries();
        assertEquals(List.of(WhistleOrder.HOIST, WhistleOrder.REEF, WhistleOrder.FURL, WhistleOrder.PUMP, WhistleOrder.RELEASE), e);
        assertEquals(SailOrder.HOIST, WhistleOrder.HOIST.sail());
        assertEquals(SailOrder.REEF, WhistleOrder.REEF.sail());
        assertEquals(SailOrder.FURL, WhistleOrder.FURL.sail());
        assertNull(WhistleOrder.PUMP.sail());
        assertEquals(PumpOrder.PUMP, WhistleOrder.PUMP.order());
        assertNull(WhistleOrder.RELEASE.sail());
        assertNull(WhistleOrder.RELEASE.order());
        for (WhistleOrder o : e) {
            if (o.sail() != null) assertEquals(o.sail(), o.order());
        }
        // every crew order has an entry
        for (CrewOrder c : CrewOrder.all()) {
            assertTrue(e.stream().anyMatch(o -> o.order() == c), "no entry for " + c);
        }
        assertEquals(Constants.id("bilge_pump"), WhistleOrder.PUMP.icon());
    }

    @Test
    void fiveEntriesSplitTheWheelEvenly() {
        RadialLayout l = RadialLayout.forWindow(WhistleOrder.entries().size(), 480, 270, 1.0);
        assertEquals(5, l.count());
        for (int i = 0; i < l.count(); i++) {
            double a = l.centerAngle(i);
            assertEquals(i, l.sectorAt(RadialLayout.x(a, l.iconRadius()), RadialLayout.y(a, l.iconRadius())), "sector " + i);
        }
    }

    @Test
    void entriesHaveUniqueIdsKeysAndIcons() {
        Set<String> ids = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (WhistleOrder o : WhistleOrder.entries()) {
            assertTrue(ids.add(o.id()), "duplicate id " + o.id());
            assertTrue(keys.add(o.nameKey()) && keys.add(o.descriptionKey()), "duplicate key of " + o);
            assertTrue(o.id().length() <= WhistleOrderPayload.MAX_LENGTH);
            assertTrue(o.icon() != null);
            assertEquals(o, WhistleOrder.byId(o.id()).orElseThrow());
        }
        assertTrue(WhistleOrder.byId("fire_at_will").isEmpty());
        assertTrue(WhistleOrder.byId("HOIST").isEmpty());
    }

    // ------------------------------------------------------------------ payload

    @Test
    void payloadRoundTrip() {
        for (WhistleOrder o : WhistleOrder.entries()) {
            assertEquals(new WhistleOrderPayload(o.id()), roundTrip(new WhistleOrderPayload(o)));
        }
        // an unknown id survives the wire, so the server can see and ignore it
        assertEquals(new WhistleOrderPayload("fire_at_will"), roundTrip(new WhistleOrderPayload("fire_at_will")));
    }

    @Test
    void payloadRefusesOverlongIds() {
        ByteBuf buf = Unpooled.buffer();
        assertThrows(RuntimeException.class, () -> WhistleOrderPayload.CODEC.encode(buf, new WhistleOrderPayload("x".repeat(65))));
    }

    private static WhistleOrderPayload roundTrip(WhistleOrderPayload p) {
        ByteBuf buf = Unpooled.buffer();
        WhistleOrderPayload.CODEC.encode(buf, p);
        WhistleOrderPayload back = WhistleOrderPayload.CODEC.decode(buf);
        assertEquals(0, buf.readableBytes());
        return back;
    }
}
