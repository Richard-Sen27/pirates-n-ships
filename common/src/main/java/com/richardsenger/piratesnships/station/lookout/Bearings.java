package com.richardsenger.piratesnships.station.lookout;

/**
 * Bearings as a lookout calls them (CN1): relative to the ship's heading, in points of the compass (32 points, 11.25°
 * each), on the starboard (right, clockwise) or port side. Pure.
 *
 * <ul>
 *   <li>0 points: dead ahead; 16 points: dead astern;</li>
 *   <li>1..7 points: "n points off the starboard (port) bow";</li>
 *   <li>8 points: "on the starboard (port) beam";</li>
 *   <li>9..15 points: "n points abaft the starboard (port) beam", n = points − 8.</li>
 * </ul>
 * Compass bearings are degrees clockwise from north (−z), as the HUD's heading: east (+x) is 90.
 */
public final class Bearings {

    /** One point of the compass in degrees. */
    public static final double POINT = 11.25;

    public enum Side { STARBOARD, PORT }

    public enum Sector { AHEAD, BOW, BEAM, ABAFT_BEAM, ASTERN }

    /**
     * A relative bearing in points: {@code points} 0..16 from the bow, {@code side} the side it lies on (STARBOARD for
     * dead ahead and dead astern).
     */
    public record Relative(int points, Side side) {

        public Sector sector() {
            if (points == 0) return Sector.AHEAD;
            if (points == 16) return Sector.ASTERN;
            if (points == 8) return Sector.BEAM;
            return points < 8 ? Sector.BOW : Sector.ABAFT_BEAM;
        }

        /** The count a phrase names: points off the bow, or points abaft the beam; 0 for ahead, beam and astern. */
        public int count() {
            return switch (sector()) {
                case BOW -> points;
                case ABAFT_BEAM -> points - 8;
                default -> 0;
            };
        }
    }

    private Bearings() {
    }

    /** Degrees into [0, 360). */
    public static double normalize(double degrees) {
        double d = degrees % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    /** Compass bearing of the offset (dx, dz): 0 north (−z), 90 east (+x). */
    public static double compass(double dx, double dz) {
        return normalize(Math.toDegrees(Math.atan2(dx, -dz)));
    }

    /** The bearing relative to the heading in (−180, 180]: positive to starboard (clockwise). */
    public static double relative(double heading, double bearing) {
        double r = normalize(bearing - heading);
        return r > 180.0 ? r - 360.0 : r;
    }

    /** The relative bearing {@code relativeDegrees} rounded to whole points. */
    public static Relative points(double relativeDegrees) {
        int p = (int) Math.min(16, Math.round(Math.abs(relativeDegrees) / POINT));
        Side side = relativeDegrees < 0 && p != 0 && p != 16 ? Side.PORT : Side.STARBOARD;
        return new Relative(p, side);
    }

    /** Points of the offset (dx, dz) from a ship heading {@code heading}. */
    public static Relative of(double heading, double dx, double dz) {
        return points(relative(heading, compass(dx, dz)));
    }

    /** Distance as called: whole blocks below 20, then rounded to tens ("140 blocks"); at least 1. */
    public static int calledDistance(double blocks) {
        if (!(blocks >= 1)) return 1;
        if (blocks < 20) return (int) Math.round(blocks);
        return (int) (Math.round(blocks / 10.0) * 10);
    }
}
