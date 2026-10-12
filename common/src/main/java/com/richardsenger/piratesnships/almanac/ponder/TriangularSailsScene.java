package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.ShipSceneText.Triangular;
import com.richardsenger.piratesnships.sailing.block.BoomBlock;
import com.richardsenger.piratesnships.sailing.block.BoomBlockEntity;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.block.CleatBlockEntity;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.sail.BoomRules;
import com.richardsenger.piratesnships.sailing.sail.StayRules;
import com.richardsenger.piratesnships.sailing.sail.TriangleCloth;
import com.richardsenger.piratesnships.sailing.sail.TriangularSail;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "Triangular sails and the boom" (SAIL5, docs/almanac.md "Scenes"; sailing/sail {@code StayLinker}, the cleat, the
 * boom and {@code BoomRules}), on the schematic {@code assets/pirates_n_ships/ponder/triangular_sails.nbt}
 * ({@code art/structures/ponder/triangular_sails.py}: bow north, an oak mast at x 4, z 6; by the yard rule the head
 * cleat [4, 7, 5] and the clew cleat [4, 2, 5] on its north face one block forward of it, the tack cleat [4, 1, 1] on
 * the deck forward). The stay's rope ends are written into both cleats' block entities and the head's cloth comes from
 * the real rule ({@link TriangularSails#sailHeadedAt}), so {@code StayClothRenderer} draws the stay and the cloth as in
 * the game. Then the clew cleat becomes a boom facing north (a forward boom along the stay's foot): its arm reaches the
 * tack's 4 blocks, and its {@link BoomBlock#SHEET} steps through what the crew does by {@link BoomRules} for each wind
 * (the boom points forward, so a positive sheet swings its end east, to starboard): 45° to starboard on the port beam,
 * across to 45° to port on the starboard beam, 60° on the starboard quarter, and the gybe to 60° to starboard when the
 * wind crosses the stern to the port quarter. A boom held out bellies the cloth to its side, so the look does not hang
 * on the player's own wind. White arrows show the wind. About 36 seconds.
 */
final class TriangularSailsScene {

    /** The rig's column: one block forward (north) of the mast at z 6, the yard rule (PND3). */
    static final int RIG_Z = 5;

    private TriangularSailsScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ShipSceneText.TRIANGULAR_SAILS, Triangular.TITLE);
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos head = util.grid().at(4, 7, RIG_Z);
        BlockPos clew = util.grid().at(4, 2, RIG_Z);
        BlockPos tack = util.grid().at(4, 1, 1);
        Selection mast = util.select().fromTo(4, 1, 6, 4, 8, 6);

        scene.world().showSection(mast, Direction.DOWN);
        scene.idle(10);
        say(scene.overlay().showText(40), Triangular.RIG)
                .pointAt(util.vector().centerOf(4, 5, 6))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(45);

        scene.world().showSection(util.select().position(head).add(util.select().position(clew)), Direction.SOUTH);
        scene.idle(10);
        say(scene.overlay().showText(35), Triangular.HEAD)
                .pointAt(util.vector().blockSurface(head, Direction.NORTH))
                .placeNearTarget();
        scene.idle(40);

        scene.world().showSection(util.select().position(tack), Direction.DOWN);
        scene.idle(10);
        say(scene.overlay().showText(35), Triangular.TACK)
                .pointAt(util.vector().topOf(tack))
                .placeNearTarget();
        scene.idle(40);

        // the rope: head, then tack; the cleats keep both ends and the head works out its sail
        scene.overlay().showControls(util.vector().blockSurface(head, Direction.NORTH), Pointing.RIGHT, 20).rightClick()
                .withItem(SceneKit.item("rope"));
        scene.idle(22);
        scene.overlay().showControls(util.vector().topOf(tack), Pointing.DOWN, 20).rightClick().withItem(SceneKit.item("rope"));
        scene.idle(10);
        scene.world().modifyBlockEntity(tack, CleatBlockEntity.class, be -> be.addRope(head));
        scene.world().modifyBlockEntity(head, CleatBlockEntity.class, be -> {
            be.addRope(tack);
            be.setCloth(cloth(be.getLevel(), head, false));
        });
        say(scene.overlay().showText(40), Triangular.ROPE)
                .pointAt(util.vector().of(4.5, 4.5, 3.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(45);

        scene.world().modifyBlock(head, s -> s.setValue(CleatBlock.TRIM, SailTrim.FULL), false);
        scene.idle(10);
        SceneKit.focus(scene, util.vector().of(4.5, 3.5, 4.0));
        say(scene.overlay().showText(40), Triangular.CLOTH)
                .pointAt(util.vector().of(4.5, 3.5, 4.0))
                .placeNearTarget();
        scene.idle(45);

        scene.overlay().showControls(util.vector().blockSurface(head, Direction.NORTH), Pointing.RIGHT, 30).rightClick();
        say(scene.overlay().showText(35), Triangular.TRIM)
                .pointAt(util.vector().blockSurface(head, Direction.NORTH))
                .placeNearTarget();
        scene.idle(40);

        // a reach: the wind from the west, on the port beam
        wind(scene, util, 1, 0, 60);
        say(scene.overlay().showText(45), Triangular.REACH)
                .pointAt(util.vector().of(1.5, 5.5, 3.5))
                .placeNearTarget();
        scene.idle(50);

        // the boom in the clew's place
        SceneKit.setLater(scene, util.select().position(clew),
                () -> SceneKit.block("boom").setValue(BoomBlock.FACING, Direction.NORTH), true);
        scene.world().modifyBlockEntity(clew, BoomBlockEntity.class, be -> be.setArm(new int[] {0, tack.getZ() - clew.getZ()}));
        scene.world().modifyBlockEntity(head, CleatBlockEntity.class, be -> be.setCloth(cloth(be.getLevel(), head, true)));
        scene.idle(8);
        SceneKit.focus(scene, clew);
        say(scene.overlay().showText(40), Triangular.BOOM)
                .pointAt(util.vector().blockSurface(clew, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(45);

        // the port beam: the crew sheets it out 45° to starboard (east), a step at a time
        wind(scene, util, 1, 0, 70);
        for (int step = BoomRules.CENTRE + 1; step <= BoomRules.CENTRE + 3; step++) {
            sheet(scene, clew, step);
            scene.idle(6);
        }
        say(scene.overlay().showText(40), Triangular.SHEET)
                .pointAt(util.vector().of(6.0, 2.5, 2.5))
                .placeNearTarget();
        scene.idle(45);

        // the starboard beam: the boom swings across to port (west)
        wind(scene, util, -1, 0, 60);
        scene.idle(5);
        sheet(scene, clew, BoomRules.CENTRE - 3);
        SceneKit.focus(scene, util.vector().of(2.0, 2.5, 2.5));
        say(scene.overlay().showText(40), Triangular.OTHER_TACK)
                .pointAt(util.vector().of(2.0, 2.5, 2.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(45);

        // the starboard quarter (from the south-east): all the way out to port
        wind(scene, util, -0.7, -0.7, 55);
        scene.idle(5);
        sheet(scene, clew, 0);
        say(scene.overlay().showText(40), Triangular.RUN)
                .pointAt(util.vector().of(1.0, 2.5, 3.0))
                .placeNearTarget();
        scene.idle(45);

        // the wind crosses the stern to the port quarter (from the south-west): the gybe, all the way to starboard
        wind(scene, util, 0.7, -0.7, 60);
        scene.idle(8);
        sheet(scene, clew, BoomRules.STEPS - 1);
        SceneKit.focus(scene, util.vector().of(7.0, 2.5, 3.0));
        say(scene.overlay().showText(45), Triangular.GYBE)
                .colored(PonderPalette.RED)
                .pointAt(util.vector().of(7.0, 2.5, 3.0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(50);

        // by hand
        scene.overlay().showControls(util.vector().blockSurface(clew, Direction.NORTH), Pointing.RIGHT, 20).rightClick();
        scene.idle(6);
        sheet(scene, clew, BoomRules.STEPS - 2);
        scene.idle(18);
        scene.overlay().showControls(util.vector().blockSurface(clew, Direction.NORTH), Pointing.RIGHT, 20).rightClick()
                .whileSneaking();
        scene.idle(6);
        sheet(scene, clew, BoomRules.CENTRE);
        say(scene.overlay().showText(35), Triangular.HAND)
                .pointAt(util.vector().blockSurface(clew, Direction.NORTH))
                .placeNearTarget();
        scene.idle(40);

        say(scene.overlay().showText(40), Triangular.RANGE)
                .pointAt(util.vector().of(4.5, 4.5, 3.5))
                .placeNearTarget();
        scene.idle(45);
        scene.markAsFinished();
    }

    /** The boom at {@code boom} takes the sheet {@code step}. */
    private static void sheet(SceneBuilder scene, BlockPos boom, int step) {
        scene.world().modifyBlock(boom, s -> s.hasProperty(BoomBlock.SHEET) ? s.setValue(BoomBlock.SHEET, step) : s, false);
    }

    /**
     * The head's cloth from the triangular sail rule in the scene's world; the cloth this scene expects (drop 5, the
     * tack 6 down and 4 forward) if the rule finds none.
     */
    private static TriangleCloth cloth(Level level, BlockPos head, boolean boomed) {
        TriangularSail s = level == null ? null : TriangularSails.sailHeadedAt(level, head, StayRules.DEFAULTS);
        return s != null && s.boomed() == boomed ? s.geometry() : new TriangleCloth(0, -6, -4, 5, boomed);
    }

    /**
     * Three white arrows across the scene for the wind blowing toward {@code (dx, dz)}: streaks over the deck at mast
     * height, beside the rig.
     */
    private static void wind(SceneBuilder scene, SceneBuildingUtil util, double dx, double dz, int ticks) {
        double len = Math.hypot(dx, dz);
        double ux = dx / len, uz = dz / len;
        double px = -uz, pz = ux;
        for (int k = -1; k <= 1; k++) {
            double cx = 4.5 + px * 2.5 * k - ux * 1.5, cz = 4.5 + pz * 2.5 * k - uz * 1.5;
            Vec3 from = util.vector().of(cx - ux * 1.2, 6.5 - 0.4 * k, cz - uz * 1.2);
            Vec3 to = util.vector().of(cx + ux * 1.2, 6.5 - 0.4 * k, cz + uz * 1.2);
            SceneKit.arrow(scene, PonderPalette.WHITE, from, to, ticks);
        }
    }
}
