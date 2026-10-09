package com.richardsenger.piratesnships.ship.hull.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Marks other features put on the compass rose's rim (LKO2: the lookout's sightings), drawn by {@link ShipHud} with the
 * {@link ShipHudSheet#MARK} sprite at {@link ShipHudLayout#rimPoint}. A source answers for the ship the HUD shows. No
 * client classes, so a feature registers its source from its client init and JUnit can test it.
 */
public final class ShipHudMarks {

    /**
     * One mark: a compass bearing (degrees, 0 = north, clockwise), its tint (ARGB, alpha ignored) and its opacity 0..1.
     */
    public record Mark(double bearing, int tint, float alpha) {
    }

    private static final List<Function<UUID, List<Mark>>> SOURCES = new CopyOnWriteArrayList<>();

    private ShipHudMarks() {
    }

    /** Adds a source of marks for the ship with the given id. */
    public static void register(Function<UUID, List<Mark>> source) {
        SOURCES.add(source);
    }

    /** Every mark for {@code ship}, from every source. */
    public static List<Mark> of(UUID ship) {
        List<Mark> out = new ArrayList<>();
        for (Function<UUID, List<Mark>> s : SOURCES) {
            out.addAll(s.apply(ship));
        }
        return out;
    }
}
