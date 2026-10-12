package com.richardsenger.piratesnships.chart.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server-side checks for marker edits (work package MAP1), pure. A request either changes the chart and returns the
 * new data, or is refused with a reason (the translation key suffix {@code message.pirates_n_ships.chart.refused.<reason>}).
 */
public final class MarkerRules {

    /** Longest marker name, in UTF-16 chars (the screen's field limit too). */
    public static final int MAX_NAME_LENGTH = 32;
    /** Markers must lie within the vanilla world border's maximum extent. */
    public static final int MAX_COORDINATE = 30_000_000;
    /**
     * Most markers one chart holds even with {@code chart.max_markers} 0 ("no limit", CHART2): the whole list travels in
     * one payload, and 4096 markers of the longest names stay well under a payload's 1 MiB.
     */
    public static final int HARD_LIMIT = 4096;

    public enum Refusal {
        TOO_MANY, NAME_TOO_LONG, BAD_NAME, OUT_OF_WORLD, UNKNOWN_MARKER, DISABLED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The new data, or the refusal. */
    public record Outcome(ChartData data, Refusal refusal, int markerId) {
        public boolean ok() {
            return refusal == null;
        }

        static Outcome refused(ChartData data, Refusal r) {
            return new Outcome(data, r, -1);
        }
    }

    private MarkerRules() {
    }

    /** Why a (stripped) name is refused: too long, or control or formatting characters; {@code null} if it is fine. */
    public static Refusal checkName(String name) {
        if (name.length() > MAX_NAME_LENGTH) return Refusal.NAME_TOO_LONG;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c) || c == '§') return Refusal.BAD_NAME;
        }
        return null;
    }

    private static Refusal checkPosition(int x, int z) {
        return x > MAX_COORDINATE || x < -MAX_COORDINATE || z > MAX_COORDINATE || z < -MAX_COORDINATE ? Refusal.OUT_OF_WORLD : null;
    }

    /** The markers a chart may hold for {@code chart.max_markers}: 0 (or less) means no limit but {@link #HARD_LIMIT}. */
    public static int limit(int maxMarkers) {
        return maxMarkers <= 0 ? HARD_LIMIT : Math.min(maxMarkers, HARD_LIMIT);
    }

    /** Places a new marker; {@code maxMarkers} is {@code chart.max_markers} (0 = no limit, see {@link #limit}). */
    public static Outcome add(ChartData data, int x, int z, MarkerIcon icon, String name, int maxMarkers) {
        String n = name.strip();
        if (data.markers().size() >= limit(maxMarkers)) return Outcome.refused(data, Refusal.TOO_MANY);
        Refusal r = checkName(n);
        if (r == null) r = checkPosition(x, z);
        if (r != null) return Outcome.refused(data, r);
        int id = Math.max(1, data.nextMarkerId());
        List<ChartMarker> list = new ArrayList<>(data.markers());
        list.add(new ChartMarker(id, x, z, icon, n));
        return new Outcome(data.withMarkers(list, id + 1), null, id);
    }

    /** Renames, re-icons or moves the marker {@code id}. */
    public static Outcome edit(ChartData data, int id, int x, int z, MarkerIcon icon, String name) {
        String n = name.strip();
        if (data.marker(id).isEmpty()) return Outcome.refused(data, Refusal.UNKNOWN_MARKER);
        Refusal r = checkName(n);
        if (r == null) r = checkPosition(x, z);
        if (r != null) return Outcome.refused(data, r);
        List<ChartMarker> list = new ArrayList<>(data.markers().size());
        for (ChartMarker m : data.markers()) list.add(m.id() == id ? new ChartMarker(id, x, z, icon, n) : m);
        return new Outcome(data.withMarkers(list, data.nextMarkerId()), null, id);
    }

    public static Outcome remove(ChartData data, int id) {
        if (data.marker(id).isEmpty()) return Outcome.refused(data, Refusal.UNKNOWN_MARKER);
        List<ChartMarker> list = data.markers().stream().filter(m -> m.id() != id).toList();
        return new Outcome(data.withMarkers(list, data.nextMarkerId()), null, id);
    }
}
