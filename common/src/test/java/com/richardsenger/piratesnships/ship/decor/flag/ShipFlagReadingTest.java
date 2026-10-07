package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagSelection.PoleReading;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** FL2: what a ship with several poles shows (the value kept in {@code ShipData.flag}) and its string form. */
class ShipFlagReadingTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PoleReading pole(int x, int y, int z, FlagReading r) {
        return new PoleReading(new BlockPos(x, y, z), r);
    }

    @Test
    void theMastheadFlagWins() {
        var ship = List.of(
                pole(0, 10, 0, FlagReading.flying(FlagKind.MERCHANT)),
                pole(2, 14, 0, FlagReading.flying(FlagKind.JOLLY_ROGER)),
                pole(4, 12, 0, FlagReading.flying(FlagKind.NAVY)));
        assertEquals(FlagReading.flying(FlagKind.JOLLY_ROGER), FlagSelection.pick(ship));
    }

    @Test
    void strikingTheWinningPoleSurrendersTheShip() {
        var ship = List.of(
                pole(0, 14, 0, FlagReading.struck(FlagKind.JOLLY_ROGER)),
                pole(2, 10, 0, FlagReading.flying(FlagKind.MERCHANT)));
        FlagReading shown = FlagSelection.pick(ship);
        assertEquals(FlagReading.struck(FlagKind.JOLLY_ROGER), shown);
        assertEquals(FlagKind.NONE, shown.shown(), "a struck ship shows no flag");
    }

    @Test
    void polesWithoutFlagsShowNothing() {
        assertEquals(FlagReading.NO_FLAG, FlagSelection.pick(List.of(pole(0, 20, 0, FlagReading.NO_FLAG), pole(1, 5, 1, FlagReading.NO_FLAG))));
        assertEquals(FlagReading.NO_FLAG, FlagSelection.pick(List.of()));
        assertEquals(FlagReading.flying(FlagKind.CUSTOM),
                FlagSelection.pick(List.of(pole(0, 20, 0, FlagReading.NO_FLAG), pole(1, 5, 1, FlagReading.flying(FlagKind.CUSTOM)))),
                "an empty pole higher up does not hide a flag");
    }

    @Test
    void readingsRoundTripThroughTheirStringForm() {
        for (FlagKind k : FlagKind.values()) {
            for (FlagReading r : List.of(FlagReading.flying(k), FlagReading.struck(k))) {
                assertEquals(r, FlagReading.fromId(r.id()), r.toString());
                var json = FlagReading.STRING_CODEC.encodeStart(JsonOps.INSTANCE, r).getOrThrow();
                assertEquals(r, FlagReading.STRING_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            }
        }
        assertEquals("", FlagReading.NO_FLAG.id());
        assertEquals("jolly_roger", FlagReading.flying(FlagKind.JOLLY_ROGER).id());
        assertEquals("struck:navy", FlagReading.struck(FlagKind.NAVY).id());
    }

    @Test
    void unknownStringsReadAsNoFlag() {
        assertEquals(FlagReading.NO_FLAG, FlagReading.fromId(""));
        assertEquals(FlagReading.NO_FLAG, FlagReading.fromId("pirates"), "a pre-FL2 placeholder");
        assertEquals(FlagReading.NO_FLAG, FlagReading.fromId("none"));
        assertEquals(FlagReading.NO_FLAG, FlagReading.fromId("struck:"));
    }
}
