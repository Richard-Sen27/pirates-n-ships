package com.richardsenger.piratesnships.ship.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.crew.hiring.HiringRules;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import com.richardsenger.piratesnships.ship.hull.net.ShipStatusPayload;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The pure rules of the ship screen (HGUI1): who may open it and act, how the HUD's hull strip, the anchor and the flag
 * read on the screen, how close a crew member is to deserting, and which buttons are active. No world access; the
 * server uses the same functions to validate what the client's buttons ask for.
 */
public final class ShipScreenRules {

    /** A compartment counts as flooded from this water fraction on (a puddle is not a flood). */
    public static final float FLOODED_FRACTION = 0.05f;

    /** What the anchor does, as the screen says it. */
    public enum Anchor {
        /** The ship has no sailing runtime (no anchor known). */
        UNKNOWN,
        /** Stowed at the hawse. */
        STOWED,
        /** Running out. */
        DROPPING,
        /** Out and holding, but the ship still has way on or the anchor drags. */
        DOWN,
        /** Holding, and the ship lies still: anchored. */
        ANCHORED,
        /** Being heaved in. */
        RAISING
    }

    /** What the flag tells others (§4.7). */
    public enum Allegiance { NONE, MERCHANT, NAVY, PIRATE, CUSTOM, STRUCK }

    /** Where a crew member is. */
    public enum CrewState { STATION, RESTING, FREE }

    /** How close a crew member is to deserting (CR2: below {@code desert_below} for {@code desert_days} dawns). */
    public enum Desertion {
        /** Content, or desertion off. */
        NONE,
        /** Morale below the line: this dawn counts toward desertion. */
        LOW,
        /** One more low dawn and it deserts. */
        LEAVING
    }

    /**
     * The hull as the HUD's strip knows it (HUD1).
     *
     * @param compartments compartments (strip cells)
     * @param flooded      compartments with at least {@link #FLOODED_FRACTION} water
     * @param breaches     open breaches
     * @param pumping      compartments a pump drains right now
     * @param water        water over the whole hull volume, 0..1
     */
    public record Hull(int compartments, int flooded, int breaches, int pumping, float water) {
        public static final Hull NONE = new Hull(0, 0, 0, 0, 0f);
        public static final Codec<Hull> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("compartments").forGetter(Hull::compartments),
                Codec.INT.fieldOf("flooded").forGetter(Hull::flooded),
                Codec.INT.fieldOf("breaches").forGetter(Hull::breaches),
                Codec.INT.fieldOf("pumping").forGetter(Hull::pumping),
                Codec.FLOAT.fieldOf("water").forGetter(Hull::water)
        ).apply(i, Hull::new));
    }

    private ShipScreenRules() {
    }

    /**
     * An enum as its lower-case name. Lives here, not in {@link ShipScreenView}, so the view's nested codecs do not
     * initialise the view class while it initialises them.
     */
    static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.xmap(s -> Enum.valueOf(type, s.toUpperCase(Locale.ROOT)), e -> e.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ who may

    /** Whether {@code viewer} may open the screen and give ship-wide orders: the owner, or anyone on an ownerless ship. */
    public static boolean mayManage(UUID viewer, Optional<UUID> owner) {
        return owner.isEmpty() || owner.get().equals(viewer);
    }

    /** Whether {@code viewer} may release or dismiss a crew member: the whistle's dismissal rule (hirer, owner, ownerless). */
    public static boolean mayCommand(UUID viewer, Optional<UUID> hiredBy, Optional<UUID> owner) {
        return HiringRules.mayDismiss(viewer, hiredBy, owner);
    }

    /** Whether a player {@code distance} blocks from the helm is in reach ({@code ship_screen.reach}). */
    public static boolean inReach(double distance, double reach) {
        return distance <= reach;
    }

    // ------------------------------------------------------------------ reading the ship

    /** The hull summary of the HUD's strip cells. */
    public static Hull hull(List<ShipStatusPayload.Cell> cells) {
        int flooded = 0, breaches = 0, pumping = 0;
        double water = 0, volume = 0;
        for (ShipStatusPayload.Cell c : cells) {
            if (c.fraction() >= FLOODED_FRACTION) flooded++;
            breaches += c.breaches();
            if (c.pumping()) pumping++;
            water += Math.max(0f, Math.min(c.volume(), c.water()));
            volume += Math.max(0, c.volume());
        }
        return new Hull(cells.size(), flooded, breaches, pumping, volume <= 0 ? 0f : (float) (water / volume));
    }

    /**
     * The anchor's state: {@code phase} of the ship's anchor (null: stowed, no anchor out), {@code anchored} the
     * runtime's "anchored" predicate (holding and at rest). {@code known} false: the ship has no sailing runtime.
     */
    public static Anchor anchor(boolean known, AnchorState.@Nullable Phase phase, boolean anchored) {
        if (!known) return Anchor.UNKNOWN;
        if (phase == null || phase == AnchorState.Phase.RAISED) return Anchor.STOWED;
        return switch (phase) {
            case DROPPING -> Anchor.DROPPING;
            case HOLDING -> anchored ? Anchor.ANCHORED : Anchor.DOWN;
            case RAISING -> Anchor.RAISING;
            case RAISED -> Anchor.STOWED;
        };
    }

    /** What the flag tells others: the kind while it flies, {@link Allegiance#STRUCK} while struck. */
    public static Allegiance allegiance(FlagReading flag) {
        if (flag.isStruck()) return Allegiance.STRUCK;
        FlagKind shown = flag.shown();
        return switch (shown) {
            case NONE -> Allegiance.NONE;
            case MERCHANT -> Allegiance.MERCHANT;
            case NAVY -> Allegiance.NAVY;
            case JOLLY_ROGER -> Allegiance.PIRATE;
            case CUSTOM -> Allegiance.CUSTOM;
        };
    }

    /**
     * How close a crew member with {@code morale} and {@code lowDays} low dawns behind it is to deserting, with
     * desertion {@code enabled} below {@code desertBelow} after {@code desertDays} dawns (CR2's {@code UpkeepDay}).
     */
    public static Desertion desertion(boolean enabled, int desertBelow, int desertDays, int morale, int lowDays) {
        if (!enabled || morale >= desertBelow) return Desertion.NONE;
        return lowDays + 1 >= desertDays ? Desertion.LEAVING : Desertion.LOW;
    }

    /**
     * The name a rename stores before the title rule: trimmed, control characters and formatting codes removed, at
     * most {@link ShipScreenView#MAX_NAME} characters; empty when nothing is left.
     */
    public static Optional<String> cleanName(String typed) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (c == '§') {
                i++; // a formatting code: drop it with its letter
            } else if (c >= ' ' && c != 127) {
                b.append(c);
            }
        }
        String s = b.toString().strip();
        if (s.length() > ShipScreenView.MAX_NAME) s = s.substring(0, ShipScreenView.MAX_NAME).strip();
        return s.isEmpty() ? Optional.empty() : Optional.of(s);
    }

    // ------------------------------------------------------------------ buttons

    /** Crew members manning no station (they may be resting): who a "Man" button can send. */
    public static List<ShipScreenView.CrewLine> freeCrew(ShipScreenView view) {
        return view.crew().stream().filter(c -> c.station().isEmpty() && c.mayCommand()).toList();
    }

    /** Rename: allowed to manage, a usable name that differs from the current one. */
    public static boolean canRename(ShipScreenView view, String typed) {
        Optional<String> n = cleanName(typed);
        return view.toggles().mayManage() && n.isPresent() && !n.get().equals(view.header().bareName());
    }

    /** The whistle's ship-wide orders: allowed to manage and stations on. */
    public static boolean canOrder(ShipScreenView view) {
        return view.toggles().mayManage() && view.toggles().stations();
    }

    /** Release from its station: it mans one, stations on, the viewer may command it. */
    public static boolean canRelease(ShipScreenView view, ShipScreenView.CrewLine c) {
        return view.toggles().stations() && c.station().isPresent() && c.mayCommand();
    }

    /** Dismiss: hiring on (it owns dismissal), the viewer may command it. */
    public static boolean canDismiss(ShipScreenView view, ShipScreenView.CrewLine c) {
        return view.toggles().hiring() && c.mayCommand();
    }

    /** "Man this": stations on, the station free, a free crew member to send. */
    public static boolean canMan(ShipScreenView view, ShipScreenView.StationLine s) {
        return view.toggles().stations() && s.occupant().isEmpty() && !freeCrew(view).isEmpty();
    }

    /** Release the crew member at a station: stations on, manned by crew (not a player) the viewer may command. */
    public static boolean canReleaseAt(ShipScreenView view, ShipScreenView.StationLine s) {
        if (!view.toggles().stations() || s.occupant().isEmpty() || s.playerOccupant()) return false;
        UUID id = s.occupant().get();
        return view.crew().stream().anyMatch(c -> c.id().equals(id) && c.mayCommand());
    }

    /** Disassemble: allowed to manage. The server checks speed, tilt and room as the helm always did. */
    public static boolean canDisassemble(ShipScreenView view) {
        return view.toggles().mayManage();
    }
}
