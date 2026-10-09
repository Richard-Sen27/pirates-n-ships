package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.almanac.ponder.ShipSceneText.Sails;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.block.YardBlockEntity;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.sail.YardRules;
import com.richardsenger.piratesnships.sailing.sail.YardSails;
import com.richardsenger.piratesnships.sailing.wind.ClientWind;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

/**
 * "Sails and the sail winch" (PND1, docs/almanac.md "Scenes"; sailing/block {@link YardBlock} and {@code SailWinchBlock},
 * the sail rule of design.md §5.2), on the schematic {@code assets/pirates_n_ships/ponder/sails_and_winch.nbt}
 * ({@code art/structures/ponder/sails_and_winch.py}: bow north, an oak mast at x 3, z 3, yards along x on y 2 and y 6
 * one block forward of the mast at z 2 by the yard rule (PND3), the winch at [1, 1, 4] aft and to port of the mast,
 * where the default camera from the north-west sees it past the sail).
 *
 * <p>The head's cloth comes from the real rule ({@link YardSails#geometryAt} with the default {@link YardRules}), and
 * the trim steps through the upper yard's real {@link YardBlock#TRIM} states in the winch's cycle (furled, half, full,
 * then furled and half to reef), so {@code YardClothRenderer} draws and hoists the cloth as in the game. The cloth
 * bellies with the client's real wind; the drawn wind arrow takes that wind's side along the sail's normal when one is
 * known. About 38 seconds.
 */
final class SailsAndWinchScene {

    /** The yards' column: one block forward (north, toward the bow) of the mast at z 3, the yard rule (PND3). */
    static final int YARD_Z = 2;

    private SailsAndWinchScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ShipSceneText.SAILS_AND_WINCH, Sails.TITLE);
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos head = util.grid().at(3, 6, YARD_Z);
        BlockPos winch = util.grid().at(1, 1, 4);
        Selection upper = util.select().fromTo(1, 6, YARD_Z, 5, 6, YARD_Z);
        Selection lower = util.select().fromTo(1, 2, YARD_Z, 5, 2, YARD_Z);
        Selection mast = util.select().fromTo(3, 1, 3, 3, 7, 3);

        // the cloth the head carries, from the yard rule (the server's job in the game)
        scene.world().modifyBlockEntity(head, YardBlockEntity.class, be -> {
            if (be.getLevel() != null) {
                be.setGeometry(YardSails.geometryAt(be.getLevel(), be.getBlockPos(), YardRules.DEFAULTS));
            }
        });

        scene.world().showSection(mast, Direction.DOWN);
        scene.idle(10);
        say(scene.overlay().showText(50), Sails.MAST)
                .pointAt(util.vector().centerOf(3, 4, 3))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(55);

        scene.world().showSection(upper, Direction.DOWN);
        scene.idle(10);
        say(scene.overlay().showText(50), Sails.YARDS)
                .pointAt(util.vector().topOf(head))
                .placeNearTarget();
        scene.idle(55);

        scene.world().showSection(lower, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.GREEN, "yards", upper.copy().add(lower), 60);
        say(scene.overlay().showText(50), Sails.PAIR)
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().centerOf(1, 2, YARD_Z))
                .placeNearTarget();
        scene.idle(55);

        scene.overlay().showBigLine(PonderPalette.WHITE, util.vector().of(0.6, 6.5, YARD_Z + 0.5),
                util.vector().of(0.6, 2.5, YARD_Z + 0.5), 70);
        say(scene.overlay().showText(60), Sails.AREA)
                .pointAt(util.vector().of(0.6, 4.5, YARD_Z + 0.5))
                .placeNearTarget();
        scene.idle(65);

        // the winch
        scene.world().showSection(util.select().position(winch), Direction.DOWN);
        scene.idle(10);
        say(scene.overlay().showText(50), Sails.WINCH)
                .pointAt(util.vector().topOf(winch))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(55);
        say(scene.overlay().showText(44), Sails.FURLED)
                .pointAt(util.vector().blockSurface(head, Direction.DOWN))
                .placeNearTarget();
        scene.idle(48);

        trim(scene, util, winch, upper, SailTrim.HALF);
        SceneKit.focus(scene, util.vector().of(3.5, 5.5, YARD_Z + 0.5));
        say(scene.overlay().showText(46), Sails.HALF)
                .pointAt(util.vector().of(3.5, 5.5, YARD_Z + 0.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(52);

        trim(scene, util, winch, upper, SailTrim.FULL);
        SceneKit.focus(scene, util.vector().of(3.5, 3.5, YARD_Z + 0.5));
        say(scene.overlay().showText(50), Sails.FULL)
                .pointAt(util.vector().of(3.5, 3.5, YARD_Z + 0.5))
                .placeNearTarget();
        scene.idle(55);

        // the wind
        double toward = windTowardZ(scene);
        Vec3 windFrom = util.vector().of(6.3, 4.5, YARD_Z + 0.5 - toward * 3.0);
        Vec3 windTo = util.vector().of(6.3, 4.5, YARD_Z + 0.5 - toward * 1.0);
        SceneKit.arrow(scene, PonderPalette.WHITE, windFrom, windTo, 70);
        say(scene.overlay().showText(60), Sails.WIND)
                .pointAt(windTo)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // a strong wind: reef
        SceneKit.arrow(scene, PonderPalette.RED, windFrom.add(0, 0, -toward * 1.5), windTo, 80);
        trim(scene, util, winch, upper, SailTrim.FURLED);
        trim(scene, util, winch, upper, SailTrim.HALF);
        say(scene.overlay().showText(60), Sails.REEF)
                .colored(PonderPalette.RED)
                .pointAt(util.vector().of(3.5, 5.5, YARD_Z + 0.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // one sail at its yard
        scene.overlay().showControls(util.vector().topOf(head), Pointing.DOWN, 30).rightClick();
        scene.idle(10);
        scene.world().modifyBlocks(upper, s -> s.setValue(YardBlock.TRIM, SailTrim.FULL), false);
        say(scene.overlay().showText(46), Sails.YARD)
                .pointAt(util.vector().topOf(head))
                .placeNearTarget();
        scene.idle(52);

        scene.overlay().showControls(util.vector().topOf(winch), Pointing.DOWN, 50).rightClick()
                .withItem(SceneKit.item("captains_whistle"));
        say(scene.overlay().showText(46), Sails.CREW)
                .pointAt(util.vector().topOf(winch))
                .placeNearTarget();
        scene.idle(48);
        scene.markAsFinished();
    }

    /** One use of the winch: the hand on it, then every block of the upper yard takes {@code trim}. */
    private static void trim(SceneBuilder scene, SceneBuildingUtil util, BlockPos winch, Selection upper, SailTrim trim) {
        scene.overlay().showControls(util.vector().topOf(winch), Pointing.DOWN, 20).rightClick();
        scene.idle(8);
        scene.world().modifyBlocks(upper, s -> s.setValue(YardBlock.TRIM, trim), false);
        scene.idle(4);
    }

    /**
     * The side the wind blows toward along z (the sail's normal), +1 or -1: from the client's synced wind when it
     * knows one, so the arrow agrees with the side the cloth bellies to; toward the north (-1) otherwise.
     */
    private static double windTowardZ(SceneBuilder scene) {
        PonderLevel level = scene.getScene().getWorld();
        if (!ClientWind.hasData()) {
            return -1.0;
        }
        double dirZ = ClientWind.sample(level == null ? 0.0 : level.getGameTime()).dirZ();
        return dirZ > 0 ? 1.0 : -1.0;
    }
}
