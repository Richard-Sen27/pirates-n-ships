package com.richardsenger.piratesnships.almanac.ponder;

import static com.richardsenger.piratesnships.almanac.ponder.SceneKit.say;

import com.richardsenger.piratesnships.almanac.ponder.ShipSceneText.Identity;
import com.richardsenger.piratesnships.rpg.career.CareerTitles;
import com.richardsenger.piratesnships.ship.decor.NameBoardBlock;
import java.util.List;
import java.util.Optional;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

/**
 * "Her name and figurehead" (PND4, docs/almanac.md "Scenes"; naming at the helm of ship/assembly, the name boards of
 * ship/decor NAME2, the figureheads and the captain's title of rpg/career HON1; design.md §4.8, §15), on the schematic
 * {@code assets/pirates_n_ships/ponder/ship_identity.nbt} ({@code art/structures/ponder/ship_identity.py}: a small ship,
 * bow north, her helm aft at [5, 4, 8], three name boards on her port side at x 2, y 3, z 4..6 facing west, a mermaid
 * figurehead on the bow at [5, 3, 1]).
 *
 * <p>The boards show what the server syncs to them ({@code NameplateBlockEntity}: the name, the board's index in its
 * row and the master's row length; {@link SceneSync}), and their {@code left}/{@code right} joins are set the way
 * {@code NameBoardRow} links a row: the viewer's left of a board facing west is north, so the master is the board at
 * z 4 and the name is drawn once, centred on the whole row and scaled to it. The title is {@link CareerTitles#shipName}
 * for a navy captain, the name {@code CareerShipTitles} stores when a captain names his ship. About 33 seconds.
 */
final class ShipIdentityScene {

    static final BlockPos HELM = new BlockPos(5, 4, 8);
    /** The boards, master (the viewer's left) first. */
    static final List<BlockPos> BOARDS = List.of(new BlockPos(2, 3, 4), new BlockPos(2, 3, 5), new BlockPos(2, 3, 6));
    static final BlockPos FIGUREHEAD = new BlockPos(5, 3, 1);
    static final String NAME = "Black Gull";
    /** The name a navy captain who names her stores (HON1). */
    static final String TITLED = CareerTitles.shipName(Optional.of(CareerTitles.Title.CAPTAIN), NAME);

    private ShipIdentityScene() {
    }

    static void program(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(ShipSceneText.SHIP_IDENTITY, Identity.TITLE);
        scene.configureBasePlate(0, 0, 11);
        scene.showBasePlate();
        scene.idle(10);

        Selection later = util.select().position(FIGUREHEAD);
        for (BlockPos b : BOARDS) {
            later = later.add(util.select().position(b));
        }
        scene.world().showSection(util.select().layersFrom(1).substract(later), Direction.DOWN);
        scene.idle(20);

        // named at the helm
        Vec3 helmTop = util.vector().topOf(HELM);
        scene.overlay().showControls(helmTop, Pointing.DOWN, 30).rightClick().withItem(nameTag(NAME));
        scene.idle(10);
        say(scene.overlay().showText(65), Identity.NAME)
                .pointAt(helmTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        // one name board on her side
        BlockPos master = BOARDS.get(0);
        scene.overlay().showControls(boardFace(master), Pointing.RIGHT, 15).rightClick().withItem(SceneKit.item("nameplate"));
        scene.idle(8);
        scene.world().showSection(util.select().position(master), Direction.EAST);
        row(scene, 1, NAME);
        scene.idle(10);
        say(scene.overlay().showText(60), Identity.BOARD)
                .pointAt(boardFace(master))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // two more: the row joins and the name grows across it
        for (int i = 1; i < BOARDS.size(); i++) {
            BlockPos b = BOARDS.get(i);
            scene.overlay().showControls(boardFace(b), Pointing.RIGHT, 12).rightClick().withItem(SceneKit.item("nameplate"));
            scene.idle(8);
            scene.world().showSection(util.select().position(b), Direction.EAST);
            row(scene, i + 1, NAME);
            scene.idle(14);
        }
        say(scene.overlay().showText(60), Identity.ROW)
                .pointAt(boardFace(BOARDS.get(1)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);
        say(scene.overlay().showText(55), Identity.READ)
                .pointAt(boardFace(BOARDS.get(2)))
                .placeNearTarget();
        scene.idle(60);

        // the figurehead on the bow
        Vec3 bow = util.vector().of(FIGUREHEAD.getX() + 0.5, FIGUREHEAD.getY() + 0.5, FIGUREHEAD.getZ() + 1.0);
        scene.overlay().showControls(bow, Pointing.DOWN, 15).rightClick().withItem(SceneKit.item("figurehead_mermaid"));
        scene.idle(8);
        scene.world().showSection(util.select().position(FIGUREHEAD), Direction.SOUTH);
        scene.idle(10);
        say(scene.overlay().showText(60), Identity.FIGUREHEAD)
                .pointAt(util.vector().centerOf(FIGUREHEAD))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // a captain's title goes in front
        scene.overlay().showControls(helmTop, Pointing.DOWN, 25).rightClick().withItem(nameTag(NAME));
        scene.idle(10);
        row(scene, BOARDS.size(), TITLED);
        say(scene.overlay().showText(65), Identity.RANK)
                .colored(PonderPalette.GREEN)
                .pointAt(boardFace(BOARDS.get(1)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);
        say(scene.overlay().showText(55), Identity.RECIPE)
                .independent(10);
        scene.idle(60);
        scene.markAsFinished();
    }

    /** A name tag renamed {@code name}, as the anvil leaves it. */
    private static ItemStack nameTag(String name) {
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return tag;
    }

    /** The outer face of a board facing west (its plank stands 2 px off the hull). */
    static Vec3 boardFace(BlockPos board) {
        return new Vec3(board.getX() + 0.85, board.getY() + 0.5, board.getZ() + 0.5);
    }

    /**
     * The first {@code length} boards as one row showing {@code name}: each board's joins ({@code left} toward the
     * master, {@code right} toward the next), its index from the master, the master's length, and the name on every
     * board as the server's refresh gives it.
     */
    private static void row(SceneBuilder scene, int length, String name) {
        for (int i = 0; i < length; i++) {
            BlockPos b = BOARDS.get(i);
            boolean left = i > 0;
            boolean right = i < length - 1;
            scene.world().modifyBlock(b, s -> join(join(s, NameBoardBlock.LEFT, left), NameBoardBlock.RIGHT, right), false);
            int index = i;
            SceneKit.once(scene, s -> SceneKit.editBlockEntity(s, b, tag -> {
                tag.putString(SceneSync.key(SceneSync.NAME_TEXT, "text"), name);
                tag.putInt(SceneSync.key(SceneSync.NAME_ROW_INDEX, "row_index"), index);
                tag.putInt(SceneSync.key(SceneSync.NAME_ROW_LENGTH, "row_length"), index == 0 ? length : 1);
            }));
        }
    }

    private static BlockState join(BlockState s, BooleanProperty side, boolean value) {
        return s.hasProperty(side) ? s.setValue(side, value) : s;
    }
}
