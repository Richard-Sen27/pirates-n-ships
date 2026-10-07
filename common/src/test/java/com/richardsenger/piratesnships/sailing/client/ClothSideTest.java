package com.richardsenger.piratesnships.sailing.client;

import com.richardsenger.piratesnships.sailing.ship.BowFrame;
import com.richardsenger.piratesnships.sailing.wind.WindOverride;
import com.richardsenger.piratesnships.sailing.wind.WindSample;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClothSideTest {

    private static final double EPS = 1.0e-9;

    /** {@code /pirates wind set 270}: wind from the west, i.e. blowing toward the east. */
    private static WindSample fromWest() {
        return new WindOverride.Entry(270.0, 6.0, WindOverride.FOREVER).sample();
    }

    private static void assertDir(double x, double z, Vector3d v) {
        assertEquals(x, v.x, 1.0e-6, "x of " + v);
        assertEquals(z, v.z, 1.0e-6, "z of " + v);
    }

    @Test
    void windFromTheWestBlowsTowardTheEast() {
        WindSample w = fromWest();
        assertEquals(1.0, w.dirX(), EPS);
        assertEquals(0.0, w.dirZ(), EPS);
        assertEquals(270.0, w.fromDegrees(), EPS);
    }

    /** The spec case: wind from 270°, a yard along z on a ship whose bow points east (assembled that way): belly east. */
    @Test
    void yardAlongZOnAnEastboundShipBelliesEastInAWestWind() {
        WindSample w = fromWest();
        // Assembled with the bow along plot +X: the pose is the identity, the yard runs along plot (and world) z
        Vector3d out = ClothSide.squareSailOut(false);
        int side = ClothSide.side(-1, out, new Quaterniond(), w.dirX(), w.dirZ());
        assertDir(1, 0, ClothSide.bellyWorld(side, out, new Quaterniond()));
    }

    /** Same, but the ship was assembled with its bow along plot +Z and has turned to the east since. */
    @Test
    void yardOnATurnedShipBelliesEastInAWestWind() {
        WindSample w = fromWest();
        // Bow plot +Z (BowFrame.SOUTH), turned so the bow points east: plot → world maps +Z onto +X
        Quaterniond plotToWorld = new Quaterniond().rotateY(Math.PI / 2);
        Vector3d bow = BowFrame.SOUTH.toPlot(new Vector3d(0, 0, 1), new Vector3d());
        assertDir(1, 0, plotToWorld.transform(bow, new Vector3d()));
        // The yard runs across the ship: plot x, world z
        Vector3d out = ClothSide.squareSailOut(true);
        int side = ClothSide.side(-1, out, plotToWorld, w.dirX(), w.dirZ());
        assertDir(1, 0, ClothSide.bellyWorld(side, out, plotToWorld));
        // Turned the other way round (bow west, sailing into the wind) the cloth still bellies east, over the bow
        Quaterniond west = new Quaterniond().rotateY(-Math.PI / 2);
        side = ClothSide.side(1, out, west, w.dirX(), w.dirZ());
        assertDir(1, 0, ClothSide.bellyWorld(side, out, west));
    }

    @Test
    void onLandTheClothFollowsTheWorldWind() {
        Vector3d out = ClothSide.squareSailOut(true); // yard along x, out along z
        WindSample north = new WindOverride.Entry(0.0, 6.0, WindOverride.FOREVER).sample(); // from the north: toward +Z
        int side = ClothSide.side(-1, out, null, north.dirX(), north.dirZ());
        assertDir(0, 1, ClothSide.bellyWorld(side, out, null));
        WindSample south = new WindOverride.Entry(180.0, 6.0, WindOverride.FOREVER).sample();
        side = ClothSide.side(side, out, null, south.dirX(), south.dirZ());
        assertDir(0, -1, ClothSide.bellyWorld(side, out, null));
    }

    @Test
    void windAlongTheYardKeepsTheSide() {
        Vector3d out = ClothSide.squareSailOut(true);
        // Wind almost along the yard: |cos| = 0.1 < SWITCH
        double a = Math.acos(0.1);
        assertEquals(1, ClothSide.side(1, out, null, Math.sin(a), -Math.cos(a)));
        assertEquals(-1, ClothSide.side(-1, out, null, Math.sin(a), Math.cos(a)));
        // A clear crossing flips it
        assertEquals(-1, ClothSide.side(1, out, null, 0.0, -1.0));
    }

    @Test
    void stayClothBelliesDownwind() {
        // A stay from the head down to a tack 4 blocks east: the renderer's normal is (tackZ, 0, -tackX) = (0, 0, -4)
        Vector3d normal = new Vector3d(0, 0, -1);
        WindSample north = new WindOverride.Entry(0.0, 6.0, WindOverride.FOREVER).sample(); // toward +Z
        int side = ClothSide.side(1, normal, null, north.dirX(), north.dirZ());
        assertDir(0, 1, ClothSide.bellyWorld(side, normal, null));
    }
}
