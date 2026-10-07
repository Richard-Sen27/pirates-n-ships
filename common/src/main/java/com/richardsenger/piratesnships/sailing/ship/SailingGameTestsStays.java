package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CleatBlock;
import com.richardsenger.piratesnships.sailing.block.CleatBlockEntity;
import com.richardsenger.piratesnships.sailing.block.SailWinchBlock;
import com.richardsenger.piratesnships.sailing.block.SailingBlocks;
import com.richardsenger.piratesnships.sailing.force.ForceBreakdown;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.force.SailTypes;
import com.richardsenger.piratesnships.sailing.item.RopeItem;
import com.richardsenger.piratesnships.sailing.sail.TriangleCloth;
import com.richardsenger.piratesnships.sailing.sail.TriangularSailContent;
import com.richardsenger.piratesnships.sailing.sail.TriangularSails;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.jetbrains.annotations.Nullable;

/**
 * F5b GameTests: triangular (fore-and-aft) sails from a rope stay and cleats (docs/design.md §5.2, rule F5b), on the
 * test hull of {@link SailingGameTestsShips#foreAndAftHull} and on land. The beam reach tests are in
 * {@link SailingGameTestsShips}.
 */
public final class SailingGameTestsStays {

    private SailingGameTestsStays() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SailingGameTestsStays.class);
    }

    private static @Nullable TriangleCloth cloth(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getBlockEntity(pos) instanceof CleatBlockEntity be ? be.cloth() : null;
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    /**
     * On an assembled ship, a mast with the head and clew cleats and a stay to a tack cleat on the bowsprit is one
     * fore-and-aft sail of area 12.5 headed by the head cleat; the winch sets its trim; breaking the clew dissolves the
     * sail but keeps the stay, a new clew brings it back with the head's trim, and breaking the tack takes the stay down.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 200)
    public static void triangularSailOnAssembledShip(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        BlockPos helm = SailingGameTestsShips.foreAndAftHull(h, 17, 17, SailTrim.FURLED);
        h.setBlock(new BlockPos(18, 9, 20), SailingBlocks.SAIL_WINCH.get());
        Fixture f = SailingGameTestsShips.assemble(h, helm);
        SailingRuntime rt = f.runtime();
        h.assertTrue(rt.sailCount() == 1, "expected one sail, got " + rt.sailCount() + " at " + rt.sailPositions());
        BlockPos head = rt.sailPositions().get(0);
        h.assertTrue(h.getLevel().getBlockState(head).getBlock() instanceof CleatBlock, "the sail's position is not a cleat");
        h.assertTrue(rt.typeAt(head) == SailTypes.FORE_AND_AFT, "type " + rt.typeAt(head));
        h.assertTrue(rt.areaAt(head) == 12.5, "area " + rt.areaAt(head) + ", expected 12.5 (drop 5, tack 5 forward)");
        TriangleCloth expected = new TriangleCloth(0, -5, 5, 5);
        h.assertTrue(expected.equals(cloth(h, head)), "head cloth " + cloth(h, head) + ", expected " + expected);
        BlockPos clew = head.below(5);
        BlockPos tack = head.offset(0, -5, 5);
        h.assertTrue(cloth(h, clew) == null && cloth(h, tack) == null, "a cleat other than the head has cloth");
        BlockPos winch = f.ship().plotBlocks().stream()
                .filter(p -> h.getLevel().getBlockState(p).is(SailingBlocks.SAIL_WINCH.get())).findFirst().orElseThrow();
        SailWinchBlock.use(h.getLevel(), winch);
        h.assertTrue(rt.trimAt(head) == SailTrim.HALF && h.getLevel().getBlockState(head).getValue(CleatBlock.TRIM) == SailTrim.HALF,
                "the winch did not set half sail: " + rt.trimAt(head));
        h.runAfterDelay(20, () -> {
            ForceBreakdown fb = rt.lastBreakdown();
            h.assertTrue(fb != null && fb.contributions().stream().anyMatch(c -> c.source().startsWith("sail[") && c.source().endsWith("fore_and_aft")),
                    "the triangular sail is not evaluated");
            h.getLevel().destroyBlock(clew, false);
            h.assertTrue(rt.sailCount() == 0 && rt.unfurledCount() == 0, "the sail survived its clew: " + rt.sailCount());
            h.assertTrue(cloth(h, head) == null, "the head still has cloth without a clew");
            h.assertTrue(tack.equals(TriangularSails.partner(h.getLevel(), head)), "breaking the clew took the stay down");
        });
        h.runAfterDelay(30, () -> {
            h.getLevel().setBlock(clew, SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.NORTH), Block.UPDATE_ALL);
            h.assertTrue(rt.sailCount() == 1 && rt.areaAt(head) == 12.5, "the new clew did not bring the sail back");
            h.assertTrue(rt.trimAt(head) == SailTrim.HALF, "the sail lost the head's trim: " + rt.trimAt(head));
            h.assertTrue(expected.equals(cloth(h, head)), "no cloth after the new clew: " + cloth(h, head));
            h.getLevel().destroyBlock(tack, false);
            h.assertTrue(rt.sailCount() == 0, "the sail survived its tack");
            h.assertTrue(TriangularSails.partner(h.getLevel(), head) == null
                            && h.getLevel().getBlockEntity(head) instanceof CleatBlockEntity be && !be.hasRopes(),
                    "the head kept the stay after the tack was broken");
            h.assertTrue(cloth(h, head) == null, "the head still has cloth without a stay");
            h.succeed();
        });
    }

    /**
     * On land: the rope remembers a first cleat and rigs a stay on a second one (one rope used up), the head gets its
     * cloth at once, the head cycles the trim when clicked; a stone put between head and clew is noticed by the periodic
     * check. With rope lines off (RP1), the rope refuses cleats too far apart, too flat, or the same cleat twice.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 60, batch = "pirates_n_ships_config_sailing_stay_refresh")
    public static void ropeRigsStaysAndRefusesBadOnes(GameTestHelper h) {
        ConfigOverrides.during(h, SailingConfig.YARD_REFRESH_TICKS, 5);
        ConfigOverrides.during(h, SailingConfig.STAY_MAX_LENGTH, 16);
        ConfigOverrides.during(h, SailingConfig.STAY_MIN_DROP, 2);
        ConfigOverrides.during(h, SailingConfig.ROPE_LINES, false); // the F5b rule as it was: a flat rope is refused (RP1)
        for (int x = 1; x <= 22; x++) for (int z = 1; z <= 22; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        for (int y = 2; y <= 7; y++) h.setBlock(new BlockPos(5, y, 5), Blocks.OAK_LOG);
        BlockPos headRel = new BlockPos(5, 7, 6); // on the mast's south face
        BlockPos clewRel = new BlockPos(5, 2, 6);
        BlockPos tackRel = new BlockPos(5, 2, 11);
        h.setBlock(headRel, SailingGameTestsShips.cleat(AttachFace.WALL, Direction.SOUTH));
        h.setBlock(clewRel, SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.NORTH));
        h.setBlock(tackRel, SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.NORTH));
        BlockPos head = h.absolutePos(headRel), tack = h.absolutePos(tackRel);
        ItemStack rope = new ItemStack(TriangularSailContent.ROPE.get(), 3);

        h.assertTrue(key(RopeItem.use(h.getLevel(), rope, head, null)).equals(RopeItem.KEY_TIED), "first use did not tie");
        h.assertTrue(key(RopeItem.use(h.getLevel(), rope, head, null)).equals(RopeItem.KEY_SAME), "the same cleat twice was not refused");
        Component rigged = RopeItem.use(h.getLevel(), rope, tack, null);
        h.assertTrue(key(rigged).equals(RopeItem.KEY_RIGGED), "no stay: " + rigged.getString());
        h.assertTrue(rope.getCount() == 2 && !rope.has(TriangularSailContent.ROPE_START.get()), "the rope was not used up: " + rope);
        h.assertTrue(tack.equals(TriangularSails.partner(h.getLevel(), head)) && head.equals(TriangularSails.partner(h.getLevel(), tack)),
                "the cleats do not agree on the stay");
        TriangleCloth expected = new TriangleCloth(0, -5, 5, 5); // area 12.5
        h.assertTrue(expected.equals(cloth(h, head)), "cloth on land " + cloth(h, head));
        h.assertTrue(TriangularSails.cycle(h.getLevel(), head) == SailTrim.HALF, "clicking the head did not hoist");
        h.assertTrue(TriangularSails.cycle(h.getLevel(), tack) == null, "the tack heads a sail");

        // too far: 16.1 blocks from the clew; too flat: 1 block of height
        BlockPos farRel = new BlockPos(5, 4, 22);
        BlockPos flatRel = new BlockPos(12, 3, 11);
        h.setBlock(farRel.below(), Blocks.STONE);
        h.setBlock(farRel, SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.NORTH));
        h.setBlock(flatRel.below(), Blocks.STONE);
        h.setBlock(flatRel, SailingGameTestsShips.cleat(AttachFace.FLOOR, Direction.NORTH));
        h.assertTrue(key(RopeItem.use(h.getLevel(), rope, h.absolutePos(clewRel), null)).equals(RopeItem.KEY_TIED), "no tie at the clew");
        Component far = RopeItem.use(h.getLevel(), rope, h.absolutePos(farRel), null);
        h.assertTrue(key(far).equals(RopeItem.KEY_TOO_LONG), "a stay of 18 blocks was not refused: " + far.getString());
        Component flat = RopeItem.use(h.getLevel(), rope, h.absolutePos(flatRel), null);
        h.assertTrue(key(flat).equals(RopeItem.KEY_TOO_FLAT), "a flat stay was not refused: " + flat.getString());
        h.assertTrue(rope.getCount() == 2, "a refused stay used up rope");
        h.assertTrue(tack.equals(TriangularSails.partner(h.getLevel(), head)), "a refused stay changed the first one");

        h.setBlock(headRel.below(2), Blocks.STONE);
        h.runAfterDelay(10, () -> {
            h.assertTrue(cloth(h, head) == null, "a stone between head and clew did not take the cloth away");
            h.setBlock(headRel.below(2), Blocks.AIR);
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(expected.equals(cloth(h, head)), "the cloth did not come back: " + cloth(h, head));
            h.succeed();
        });
    }
}
