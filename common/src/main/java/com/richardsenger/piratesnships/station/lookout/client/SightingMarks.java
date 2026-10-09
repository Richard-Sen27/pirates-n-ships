package com.richardsenger.piratesnships.station.lookout.client;

import com.richardsenger.piratesnships.ship.hull.client.ShipHudMarks;
import com.richardsenger.piratesnships.station.lookout.SightingPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client store of the lookout's calls (LKO2). No client classes, so the common payload registration can name its
 * handler and JUnit can test the rules. Counted in client ticks.
 *
 * <ul>
 *   <li><b>Marks:</b> every call marks its compass bearing on the ship HUD's rose for the payload's
 *   {@code freshTicks}; a new call of the same sighting replaces its mark. A mark is fully opaque for the first two
 *   thirds of its time and fades out over the last third ({@link #alpha}).</li>
 *   <li><b>Cues:</b> each call is one action-bar line and one hail; calls arriving together (one scan) are shown one
 *   after the other, {@link #CUE_GAP_TICKS} apart, nearest first as the server sends them, at most
 *   {@link #MAX_PENDING} waiting.</li>
 * </ul>
 */
public final class SightingMarks {

    /** Ticks between two cues of one burst: the action bar shows a line for about three seconds. */
    public static final int CUE_GAP_TICKS = 50;
    /** Calls waiting for the action bar; older ones beyond are dropped (their marks stay). */
    public static final int MAX_PENDING = 4;
    /** Share of a mark's time it fades out over, at the end. */
    public static final double FADE_SHARE = 1.0 / 3.0;
    /** Farthest a hail is played from the listener: a nest farther off is heard from this far, in its direction. */
    public static final double HAIL_REACH = 12.0;

    private record Entry(SightingPayload payload, long at) {
    }

    private static final Map<String, Entry> MARKS = new LinkedHashMap<>();
    private static final Deque<SightingPayload> PENDING = new ArrayDeque<>();
    private static long ticks;
    private static long lastCue = Long.MIN_VALUE / 2;

    private SightingMarks() {
    }

    /** Payload handler (client main thread). */
    public static synchronized void accept(SightingPayload p) {
        MARKS.put(p.ship() + ":" + p.key(), new Entry(p, ticks));
        PENDING.addLast(p);
        while (PENDING.size() > MAX_PENDING) PENDING.removeFirst();
    }

    /** Client tick end: advances time and drops marks that have faded. */
    public static synchronized void tick() {
        ticks++;
        MARKS.values().removeIf(e -> ticks - e.at() >= Math.max(1, e.payload().freshTicks()));
    }

    /** The next call to show on the action bar and hail, or null while none waits or the last one is too recent. */
    public static synchronized @Nullable SightingPayload nextCue() {
        if (PENDING.isEmpty() || ticks - lastCue < CUE_GAP_TICKS) return null;
        lastCue = ticks;
        return PENDING.removeFirst();
    }

    /** The marks on the rose of the ship {@code ship} right now. */
    public static synchronized List<ShipHudMarks.Mark> marks(UUID ship) {
        List<ShipHudMarks.Mark> out = new ArrayList<>();
        for (Entry e : MARKS.values()) {
            if (!e.payload().ship().equals(ship)) continue;
            float a = alpha(ticks - e.at(), e.payload().freshTicks());
            if (a > 0f) out.add(new ShipHudMarks.Mark(e.payload().compass(), e.payload().what().tint(), a));
        }
        return out;
    }

    /** Opacity of a mark {@code age} ticks old that lasts {@code fresh} ticks: 1, then down to 0 over the last third. */
    public static float alpha(long age, int fresh) {
        if (fresh <= 0 || age >= fresh) return 0f;
        if (age <= 0) return 1f;
        double fadeFrom = fresh * (1.0 - FADE_SHARE);
        if (age <= fadeFrom) return 1f;
        return (float) ((fresh - age) / (fresh - fadeFrom));
    }

    /** Where to play the hail for a listener at {@code listener}: at the nest, or {@link #HAIL_REACH} toward it. */
    public static Vec3 hailAt(Vec3 listener, Vec3 nest) {
        Vec3 d = nest.subtract(listener);
        double len = d.length();
        if (len <= HAIL_REACH) return nest;
        return listener.add(d.scale(HAIL_REACH / len));
    }

    public static synchronized void reset() {
        MARKS.clear();
        PENDING.clear();
        ticks = 0;
        lastCue = Long.MIN_VALUE / 2;
    }
}
