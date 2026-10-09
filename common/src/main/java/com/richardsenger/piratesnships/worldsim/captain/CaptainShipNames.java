package com.richardsenger.piratesnships.worldsim.captain;

/**
 * The name of a pirate captain's ship (TPL2, BOS2's open point "renaming the ship after the captain"), pure. A
 * captain's voyage sails the ship its pirate name would have had ({@code Materializer.name}: "Sea Wolf"), made his:
 * <ol>
 *   <li>his full name in the possessive and the ship's name: "Black-Tooth Bartholomew Crowe's Sea Wolf" (a name
 *       ending in s takes a bare apostrophe: "Mad Silas' Night Shark");</li>
 *   <li>longer than {@link #MAX_LENGTH} (what a nameplate holds): his surname (the last word of his name) instead,
 *       "Crowe's Widow's Revenge";</li>
 *   <li>still longer: cut to {@link #MAX_LENGTH}.</li>
 * </ol>
 * A blank captain name leaves the ship's own name.
 */
public final class CaptainShipNames {

    /** Longest name, as a nameplate keeps it ({@code NameplateText.MAX_LENGTH}). */
    public static final int MAX_LENGTH = 50;

    private CaptainShipNames() {
    }

    /** The ship {@code ship} named after the captain {@code captain} (see the class comment). */
    public static String of(String captain, String ship) {
        String c = captain == null ? "" : captain.strip();
        String s = ship == null ? "" : ship.strip();
        if (c.isEmpty()) return cut(s);
        String full = possessive(c) + (s.isEmpty() ? "" : " " + s);
        if (full.length() <= MAX_LENGTH) return full;
        String surname = c.substring(c.lastIndexOf(' ') + 1);
        return cut(possessive(surname) + (s.isEmpty() ? "" : " " + s));
    }

    /** "Crowe's", "Silas'". */
    static String possessive(String name) {
        return name.endsWith("s") || name.endsWith("S") ? name + "'" : name + "'s";
    }

    private static String cut(String s) {
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH).strip() : s;
    }
}
