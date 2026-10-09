package com.richardsenger.piratesnships.station.lookout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import org.junit.jupiter.api.Test;

/** The lookout's bearings in points and its called distances (CN1). */
class BearingsTest {

    @Test
    void compassIsClockwiseFromNorth() {
        assertEquals(0.0, Bearings.compass(0, -10), 1e-9);
        assertEquals(90.0, Bearings.compass(10, 0), 1e-9);
        assertEquals(180.0, Bearings.compass(0, 10), 1e-9);
        assertEquals(270.0, Bearings.compass(-10, 0), 1e-9);
        assertEquals(45.0, Bearings.compass(5, -5), 1e-9);
    }

    @Test
    void relativeIsPositiveToStarboardAndWraps() {
        assertEquals(22.5, Bearings.relative(0, 22.5), 1e-9);
        assertEquals(-22.5, Bearings.relative(0, 337.5), 1e-9);
        assertEquals(20.0, Bearings.relative(350, 10), 1e-9);
        assertEquals(-20.0, Bearings.relative(10, 350), 1e-9);
        assertEquals(180.0, Bearings.relative(90, 270), 1e-9);
    }

    @Test
    void pointsRoundToElevenAndAQuarterDegrees() {
        assertEquals(new Bearings.Relative(0, Bearings.Side.STARBOARD), Bearings.points(5.0));
        assertEquals(new Bearings.Relative(0, Bearings.Side.STARBOARD), Bearings.points(-5.0));
        assertEquals(new Bearings.Relative(1, Bearings.Side.STARBOARD), Bearings.points(11.25));
        assertEquals(new Bearings.Relative(2, Bearings.Side.STARBOARD), Bearings.points(22.5));
        assertEquals(new Bearings.Relative(2, Bearings.Side.PORT), Bearings.points(-20.0));
        assertEquals(new Bearings.Relative(8, Bearings.Side.PORT), Bearings.points(-90.0));
        assertEquals(new Bearings.Relative(16, Bearings.Side.STARBOARD), Bearings.points(180.0));
        assertEquals(new Bearings.Relative(16, Bearings.Side.STARBOARD), Bearings.points(-175.0));
    }

    @Test
    void sectorsAndCountsNameTheClassicPhrases() {
        assertEquals(Bearings.Sector.AHEAD, Bearings.points(0).sector());
        Bearings.Relative bow = Bearings.points(22.5);
        assertEquals(Bearings.Sector.BOW, bow.sector());
        assertEquals(2, bow.count());
        assertEquals(Bearings.Sector.BEAM, Bearings.points(-90).sector());
        Bearings.Relative abaft = Bearings.points(-123.75); // 11 points
        assertEquals(Bearings.Sector.ABAFT_BEAM, abaft.sector());
        assertEquals(3, abaft.count());
        assertEquals(Bearings.Side.PORT, abaft.side());
        assertEquals(Bearings.Sector.ASTERN, Bearings.points(180).sector());
        assertEquals(LookoutLang.KEY_BOW, LookoutLang.bearingKey(bow));
        assertEquals(LookoutLang.KEY_ABAFT, LookoutLang.bearingKey(abaft));
        assertEquals(LookoutLang.KEY_ASTERN, LookoutLang.bearingKey(Bearings.points(180)));
    }

    /** "Two points off the starboard bow": a ship heading north sees something north-north-east of it. */
    @Test
    void offsetFromAHeadingGivesItsPoints() {
        // heading north, target at bearing 22.5 (NNE)
        double a = Math.toRadians(22.5);
        assertEquals(new Bearings.Relative(2, Bearings.Side.STARBOARD), Bearings.of(0, 100 * Math.sin(a), -100 * Math.cos(a)));
        // heading south (180), target south-east (135): four points off the port bow
        assertEquals(new Bearings.Relative(4, Bearings.Side.PORT), Bearings.of(180, 50, 50));
        // heading east (90), target due west: dead astern
        assertEquals(Bearings.Sector.ASTERN, Bearings.of(90, -40, 0).sector());
        // heading west (270), target due north: on the starboard beam
        assertEquals(new Bearings.Relative(8, Bearings.Side.STARBOARD), Bearings.of(270, 0, -40));
    }

    @Test
    void distancesAreCalledInTensFromTwenty() {
        assertEquals(1, Bearings.calledDistance(0.2));
        assertEquals(7, Bearings.calledDistance(7.4));
        assertEquals(19, Bearings.calledDistance(19.4));
        assertEquals(20, Bearings.calledDistance(19.6));
        assertEquals(140, Bearings.calledDistance(137.0));
        assertEquals(140, Bearings.calledDistance(144.9));
        assertEquals(150, Bearings.calledDistance(145.0));
    }

    @Test
    void shipsAreDescribedByWreckAndFlyingFlag() {
        assertEquals(LookoutLang.KEY_WHAT_MERCHANT, LookoutLang.whatKey(FlagKind.MERCHANT, true, false));
        assertEquals(LookoutLang.KEY_WHAT_NAVY, LookoutLang.whatKey(FlagKind.NAVY, true, false));
        assertEquals(LookoutLang.KEY_WHAT_PIRATE, LookoutLang.whatKey(FlagKind.JOLLY_ROGER, true, false));
        assertEquals(LookoutLang.KEY_WHAT_SHIP, LookoutLang.whatKey(FlagKind.CUSTOM, true, false));
        assertEquals(LookoutLang.KEY_WHAT_SHIP, LookoutLang.whatKey(FlagKind.JOLLY_ROGER, false, false), "a struck flag tells nothing");
        assertEquals(LookoutLang.KEY_WHAT_WRECK, LookoutLang.whatKey(FlagKind.MERCHANT, true, true));
    }
}
